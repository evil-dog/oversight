package us.bergnet.oversight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.mikepenz.iconics.Iconics
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import us.bergnet.oversight.data.model.FixedNotification
import us.bergnet.oversight.data.model.InfoValues
import us.bergnet.oversight.data.model.NotificationLayout
import us.bergnet.oversight.data.model.NotificationLayoutList
import us.bergnet.oversight.data.model.NotificationValues
import us.bergnet.oversight.data.model.OverlayCustomization
import us.bergnet.oversight.data.model.OverlayValues
import us.bergnet.oversight.data.model.ReceivedNotification
import us.bergnet.oversight.data.model.enums.FixedNotificationShape
import us.bergnet.oversight.data.model.enums.FixedNotificationSize
import us.bergnet.oversight.data.store.OverlayStateStore
import us.bergnet.oversight.ui.overlay.OverlayContent
import us.bergnet.oversight.ui.overlay.notification.DefaultNotificationLayout
import us.bergnet.oversight.ui.overlay.notification.FixedNotificationBadge
import us.bergnet.oversight.ui.overlay.notification.IconOnlyNotificationLayout
import us.bergnet.oversight.ui.overlay.notification.NotificationPopup
import us.bergnet.oversight.data.model.enums.HotCorner
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import us.bergnet.oversight.ui.setup.SetupScreen
import us.bergnet.oversight.ui.theme.OverlayTheme

/**
 * Renders UI surfaces to PNGs without a device. Shield TV resolution: 1920x1080 = 960x540dp @ xhdpi.
 *
 *   ./gradlew recordRoborazziDebug   ->  app/build/outputs/roborazzi/
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Plain Application: OverSightApplication would load DataStore state concurrently and race with test setup
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi", application = android.app.Application::class)
class UiSnapshots {

    @get:Rule
    val compose = createComposeRule()

    private val sampleToast = ReceivedNotification(
        id = "toast-1",
        title = "Front Door",
        message = "Motion detected on the front porch camera.",
        source = "Home Assistant",
        largeIcon = "mdi:cctv",
        smallIcon = "mdi:motion-sensor",
        smallIconColor = "#FFC107"
    )

    private val sampleFixed = listOf(
        FixedNotification(id = "temp", icon = "mdi:thermometer", text = "72°F", iconColor = "#FF9800"),
        FixedNotification(id = "door", icon = "mdi:door-open", text = "Garage open", backgroundColor = "#CCB71C1C",
            shape = FixedNotificationShape.CIRCLE),
        FixedNotification(id = "pkg", icon = "mdi:package-variant", iconColor = "#4CAF50"),
    )

    @Before
    fun resetState() {
        // On device Iconics registers fonts via androidx.startup, which Robolectric doesn't run
        Iconics.init(ApplicationProvider.getApplicationContext())
        Iconics.registerFont(CommunityMaterial)
        FakeImages.install(ApplicationProvider.getApplicationContext())
        OverlayStateStore.setInfoValues(InfoValues())
        OverlayStateStore.setOverlayCustomization(OverlayCustomization.DEFAULT)
        OverlayStateStore.setClockTextFormat("'9:41 PM'") // quoted literal keeps renders stable
        OverlayStateStore.setFixedNotifications(emptyList())
        OverlayStateStore.setLayoutList(NotificationLayoutList())
        OverlayStateStore.setDeviceId("00000000-snapshot")
        OverlayStateStore.setServiceRunning(true)
        OverlayStateStore.setMdnsRegistered(true)
        while (OverlayStateStore.currentNotification.value != null) OverlayStateStore.dismissCurrentNotification()
    }

    private fun capture(name: String, advanceMs: Long = 1_000, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent(content)
        compose.mainClock.advanceTimeBy(advanceMs)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun overlay_topEnd() {
        OverlayStateStore.updateInfoValues(InfoValues(overlay = OverlayValues(clockOverlayVisibility = 100, hotCorner = "top_end")))
        OverlayStateStore.setFixedNotifications(sampleFixed.map { it.withReceivedTime() })
        OverlayStateStore.enqueueNotification(sampleToast)
        capture("overlay_top_end") { TvBackdrop { OverlayContent() } }
    }

    @Test
    fun overlay_bottomStart_dimmed() {
        OverlayStateStore.updateInfoValues(InfoValues(
            overlay = OverlayValues(clockOverlayVisibility = 100, hotCorner = "bottom_start", overlayVisibility = 40),
            notifications = NotificationValues(notificationLayoutName = "Minimalist")
        ))
        OverlayStateStore.setFixedNotifications(sampleFixed.map { it.withReceivedTime() })
        OverlayStateStore.enqueueNotification(sampleToast)
        capture("overlay_bottom_start_dimmed") { TvBackdrop { OverlayContent() } }
    }

    /** Popups with media. Image URLs choose the fake picture's shape (see FakeImages). */
    @Test
    fun popupMedia() {
        val base = sampleToast.copy(largeIcon = "mdi:cctv")
        val cases = listOf(
            base.copy(id = "landscape", title = "Landscape image", image = "https://img/landscape.jpg") to NotificationLayout.DEFAULT,
            base.copy(id = "portrait", title = "Portrait image", image = "https://img/portrait.jpg") to NotificationLayout.DEFAULT,
            base.copy(id = "square", title = "Square image", image = "https://img/square.jpg") to NotificationLayout.DEFAULT,
        )
        capture("popup_images") {
            TvBackdrop {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cases.forEach { (n, layout) ->
                        NotificationPopup(notification = n, layout = layout, durationSeconds = 8,
                            corner = HotCorner.TOP_START, onDismiss = {})
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun popupMediaVariants() {
        val base = sampleToast.copy(largeIcon = "mdi:cctv")
        val cases = listOf(
            base.copy(id = "tiny", title = "Tiny image (64px)", image = "https://img/tiny.png") to NotificationLayout.DEFAULT,
            base.copy(id = "mini", title = "Minimalist + image", image = "https://img/landscape.jpg") to NotificationLayout.MINIMALIST,
            base.copy(id = "icononly", title = "Icon only + image", image = "https://img/landscape.jpg") to NotificationLayout.ICON_ONLY,
            base.copy(id = "urlicon", title = "URL large icon", largeIcon = "https://img/square-icon.png") to NotificationLayout.DEFAULT,
        )
        capture("popup_media_variants") {
            TvBackdrop {
                FlowRow(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cases.forEach { (n, layout) ->
                        NotificationPopup(notification = n, layout = layout, durationSeconds = 8,
                            corner = HotCorner.TOP_START, onDismiss = {})
                    }
                }
            }
        }
    }

    /** Robolectric has no video decoder, so video renders as layout only: a black 16:9 box with the progress bar. */
    @Test
    fun popupVideo() {
        val base = sampleToast.copy(largeIcon = "mdi:cctv")
        val cases = listOf(
            base.copy(id = "video", title = "Video (layout only)", video = "https://cam/stream.mp4"),
            base.copy(id = "rtsp", title = "RTSP, no text layout", video = "rtsp://cam/front"),
            base.copy(id = "both", title = "Image + video", image = "https://img/landscape.jpg", video = "https://cam/stream.mp4"),
        )
        capture("popup_video") {
            TvBackdrop {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cases.forEach { n ->
                        val layout = if (n.id == "rtsp") NotificationLayout.ICON_ONLY else NotificationLayout.DEFAULT
                        NotificationPopup(notification = n, layout = layout, durationSeconds = 8,
                            corner = HotCorner.TOP_START, onDismiss = {})
                    }
                }
            }
        }
    }

    @Test
    fun overlay_imageToast() {
        OverlayStateStore.updateInfoValues(InfoValues(overlay = OverlayValues(clockOverlayVisibility = 100, hotCorner = "top_end")))
        OverlayStateStore.setFixedNotifications(sampleFixed.map { it.withReceivedTime() })
        OverlayStateStore.enqueueNotification(sampleToast.copy(id = "img", title = "Package delivered",
            message = "A package was left at the front door.", image = "https://img/portrait.jpg"))
        capture("overlay_image_toast") { TvBackdrop { OverlayContent() } }
    }

    @Test
    fun overlay_videoToast_bottomEnd() {
        OverlayStateStore.updateInfoValues(InfoValues(overlay = OverlayValues(clockOverlayVisibility = 100, hotCorner = "bottom_end")))
        OverlayStateStore.setFixedNotifications(sampleFixed.map { it.withReceivedTime() })
        OverlayStateStore.enqueueNotification(sampleToast.copy(id = "vid", video = "rtsp://cam/front"))
        capture("overlay_video_toast_bottom_end") { TvBackdrop { OverlayContent() } }
    }

    @Test
    fun toastLayouts() {
        capture("toast_layouts") {
            TvBackdrop {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DefaultNotificationLayout(sampleToast, NotificationLayout.DEFAULT)
                    DefaultNotificationLayout(sampleToast, NotificationLayout.MINIMALIST)
                    IconOnlyNotificationLayout(sampleToast, NotificationLayout.ICON_ONLY)
                    DefaultNotificationLayout(sampleToast.copy(largeIcon = null), NotificationLayout.DEFAULT)
                }
            }
        }
    }

    @Test
    fun fixedBadges() {
        capture("fixed_badges") {
            TvBackdrop {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FixedNotificationSize.entries.forEach { size ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FixedNotificationShape.entries.forEach { shape ->
                                FixedNotificationBadge(
                                    FixedNotification(id = "$size$shape", icon = "mdi:lightbulb-on", text = shape.restApiName,
                                        iconColor = "#FFEB3B", borderColor = "#FFFFFF", shape = shape, size = size)
                                )
                            }
                            FixedNotificationBadge(FixedNotification(id = "c$size", icon = "mdi:lightbulb-on", text = "collapsed", size = size), collapsed = true)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun setupScreen_about() {
        capture("setup_about") {
            SetupScreen(
                hasOverlayPermission = true, hasBatteryExemption = false, canRequestBatteryExemption = true,
                onRequestOverlayPermission = {}, onRequestBatteryExemption = {},
                onStartService = {}, onStopService = {}, onRestartService = {}, onClearFixedNotifications = {},
                appVersion = "1.2.3"
            )
        }
    }

    /** Google TV and similar: no usable exemption screen, so no button and "Not Required". */
    @Test
    fun setupScreen_about_noBatteryScreen() {
        capture("setup_about_no_battery_screen") {
            SetupScreen(
                hasOverlayPermission = true, hasBatteryExemption = false, canRequestBatteryExemption = false,
                onRequestOverlayPermission = {}, onRequestBatteryExemption = {},
                onStartService = {}, onStopService = {}, onRestartService = {}, onClearFixedNotifications = {},
                appVersion = "1.2.3"
            )
        }
    }
}

/** Stand-in for TV content behind the transparent overlay, themed like the real overlay window. */
@Composable
private fun TvBackdrop(content: @Composable () -> Unit) {
    OverlayTheme {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(Color(0xFF3A6073), Color(0xFF16222A), Color(0xFF6B4F2A)))
            )
        ) { content() }
    }
}
