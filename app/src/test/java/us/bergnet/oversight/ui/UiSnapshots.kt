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
import androidx.compose.ui.test.junit4.createComposeRule
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
import us.bergnet.oversight.ui.setup.SetupScreen

/**
 * Renders UI surfaces to PNGs without a device. Shield TV resolution: 1920x1080 = 960x540dp @ xhdpi.
 *
 *   ./gradlew recordRoborazziDebug   ->  app/build/outputs/roborazzi/
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
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
        OverlayStateStore.setInfoValues(InfoValues())
        OverlayStateStore.setOverlayCustomization(OverlayCustomization.DEFAULT)
        OverlayStateStore.setClockTextFormat("'9:41 PM'") // quoted literal keeps renders stable
        OverlayStateStore.setFixedNotifications(emptyList())
        OverlayStateStore.setLayoutList(NotificationLayoutList())
        OverlayStateStore.setDeviceId("00000000-snapshot")
        OverlayStateStore.setServiceRunning(true)
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
                hasOverlayPermission = true, hasBatteryExemption = false,
                onRequestOverlayPermission = {}, onRequestBatteryExemption = {},
                onStartService = {}, onStopService = {}, onRestartService = {}, onClearFixedNotifications = {}
            )
        }
    }
}

/** Stand-in for TV content behind the transparent overlay. */
@Composable
private fun TvBackdrop(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF3A6073), Color(0xFF16222A), Color(0xFF6B4F2A)))
        )
    ) { content() }
}
