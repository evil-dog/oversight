package us.bergnet.oversight.ui.overlay

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import us.bergnet.oversight.data.store.OverlayStateStore
import us.bergnet.oversight.ui.overlay.notification.FixedNotificationBadge
import us.bergnet.oversight.ui.overlay.notification.NotificationPopup
import us.bergnet.oversight.ui.theme.OverlayTheme

@Composable
fun OverlayContent() {
    val infoValues by OverlayStateStore.infoValues.collectAsState()
    val overlayCustomization by OverlayStateStore.overlayCustomization.collectAsState()
    val clockTextFormat by OverlayStateStore.clockTextFormat.collectAsState()
    val fixedNotifications by OverlayStateStore.fixedNotifications.collectAsState()
    val currentNotification by OverlayStateStore.currentNotification.collectAsState()
    val layoutList by OverlayStateStore.layoutList.collectAsState()
    val isScreenOn by OverlayStateStore.isScreenOn.collectAsState()

    val overlayVisibility = infoValues.overlay?.overlayVisibility ?: 0
    val clockVisibility = infoValues.overlay?.clockOverlayVisibility ?: 0
    val displayNotifications = infoValues.notifications?.displayNotifications ?: true
    val displayFixedNotifications = infoValues.notifications?.displayFixedNotifications ?: true
    val hotCornerName = infoValues.overlay?.hotCorner ?: "top_end"
    val hotCorner = us.bergnet.oversight.data.model.enums.HotCorner.fromRestApiName(hotCornerName)
    val notificationDuration = infoValues.notifications?.notificationDuration ?: 8

    val currentLayout = remember(layoutList, infoValues.notifications?.notificationLayoutName) {
        val name = infoValues.notifications?.notificationLayoutName ?: layoutList.selected
        layoutList.list.firstOrNull { it.name == name }
            ?: us.bergnet.oversight.data.model.NotificationLayout.DEFAULT
    }

    OverlayTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            // Layer 1: Background dimmer
            BackgroundDimmer(visibility = overlayVisibility)

            // Layer 2: Fixed notifications (bottom of selected corner)
            if (displayFixedNotifications) {
                val activeFixed = fixedNotifications.filter { !it.isExpired() && it.visible && !it.isEmpty() }
                if (activeFixed.isNotEmpty()) {
                    val fixedAlignment = when (hotCorner) {
                        us.bergnet.oversight.data.model.enums.HotCorner.TOP_START -> Alignment.TopStart
                        us.bergnet.oversight.data.model.enums.HotCorner.TOP_END -> Alignment.TopEnd
                        us.bergnet.oversight.data.model.enums.HotCorner.BOTTOM_START -> Alignment.BottomStart
                        us.bergnet.oversight.data.model.enums.HotCorner.BOTTOM_END -> Alignment.BottomEnd
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = fixedAlignment
                    ) {
                        Column(
                            modifier = Modifier.animateContentSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = if (hotCorner.isStart()) Alignment.Start else Alignment.End
                        ) {
                            activeFixed.forEach { notification ->
                                FixedNotificationBadge(
                                    notification = notification
                                )
                            }
                        }
                    }
                }
            }

            // Layer 3: Clock overlay
            if (clockVisibility > 0) {
                val clockAlignment = when (hotCorner) {
                    us.bergnet.oversight.data.model.enums.HotCorner.TOP_START -> Alignment.TopStart
                    us.bergnet.oversight.data.model.enums.HotCorner.TOP_END -> Alignment.TopEnd
                    us.bergnet.oversight.data.model.enums.HotCorner.BOTTOM_START -> Alignment.BottomStart
                    us.bergnet.oversight.data.model.enums.HotCorner.BOTTOM_END -> Alignment.BottomEnd
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = clockAlignment
                ) {
                    ClockOverlay(
                        customization = overlayCustomization.toNonNullable()
                    , clockTextFormat = clockTextFormat
                    )
                }
            }

            // Layer 4: Toast notifications
            if (displayNotifications && currentNotification != null) {
                val toastAlignment = when (hotCorner) {
                    us.bergnet.oversight.data.model.enums.HotCorner.TOP_START -> Alignment.TopStart
                    us.bergnet.oversight.data.model.enums.HotCorner.TOP_END -> Alignment.TopEnd
                    us.bergnet.oversight.data.model.enums.HotCorner.BOTTOM_START -> Alignment.BottomStart
                    us.bergnet.oversight.data.model.enums.HotCorner.BOTTOM_END -> Alignment.BottomEnd
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = toastAlignment
                ) {
                    NotificationPopup(
                        notification = currentNotification,
                        layout = currentLayout,
                        durationSeconds = notificationDuration,
                        onDismiss = { OverlayStateStore.dismissCurrentNotification() }
                    )
                }
            }
        }
    }
}
