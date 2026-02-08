package us.bergnet.oversight.ui.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import us.bergnet.oversight.data.model.FixedNotification
import us.bergnet.oversight.data.model.enums.HotCorner
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

    val overlayVisibility = infoValues.overlay?.overlayVisibility ?: 0
    val clockVisibility = infoValues.overlay?.clockOverlayVisibility ?: 0
    val displayNotifications = infoValues.notifications?.displayNotifications ?: true
    val displayFixedNotifications = infoValues.notifications?.displayFixedNotifications ?: true
    val hotCornerName = infoValues.overlay?.hotCorner ?: "top_end"
    val hotCorner = HotCorner.fromRestApiName(hotCornerName)
    val notificationDuration = infoValues.notifications?.notificationDuration ?: 8

    val currentLayout = remember(layoutList, infoValues.notifications?.notificationLayoutName) {
        val name = infoValues.notifications?.notificationLayoutName ?: layoutList.selected
        layoutList.list.firstOrNull { it.name == name }
            ?: us.bergnet.oversight.data.model.NotificationLayout.DEFAULT
    }

    val isBottom = hotCorner == HotCorner.BOTTOM_START || hotCorner == HotCorner.BOTTOM_END
    val horizontalAlignment = if (hotCorner.isStart()) Alignment.Start else Alignment.End

    // Determine toast corner: per-notification override or hot corner default
    val toastCorner = currentNotification?.corner ?: hotCorner
    val toastAtHotCorner = toastCorner == hotCorner

    val activeFixed = if (displayFixedNotifications) {
        fixedNotifications.filter { !it.isExpired() && it.visible && !it.isEmpty() }
    } else {
        emptyList()
    }

    OverlayTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background dimmer (full screen)
            BackgroundDimmer(visibility = overlayVisibility)

            // Main corner: clock + fixed notifications (horizontal) + toast if at same corner
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = hotCorner.alignment
            ) {
                Column(horizontalAlignment = horizontalAlignment) {
                    if (isBottom) {
                        if (toastAtHotCorner) {
                            ToastSection(
                                displayNotifications = displayNotifications,
                                currentNotification = currentNotification,
                                currentLayout = currentLayout,
                                notificationDuration = notificationDuration,
                                corner = hotCorner
                            )
                        }
                        ClockFixedRow(
                            hotCorner = hotCorner,
                            clockVisibility = clockVisibility,
                            overlayCustomization = overlayCustomization,
                            clockTextFormat = clockTextFormat,
                            activeFixed = activeFixed
                        )
                    } else {
                        ClockFixedRow(
                            hotCorner = hotCorner,
                            clockVisibility = clockVisibility,
                            overlayCustomization = overlayCustomization,
                            clockTextFormat = clockTextFormat,
                            activeFixed = activeFixed
                        )
                        if (toastAtHotCorner) {
                            ToastSection(
                                displayNotifications = displayNotifications,
                                currentNotification = currentNotification,
                                currentLayout = currentLayout,
                                notificationDuration = notificationDuration,
                                corner = hotCorner
                            )
                        }
                    }
                }
            }

            // Toast at a custom corner (different from hot corner)
            if (!toastAtHotCorner && displayNotifications && currentNotification != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = toastCorner.alignment
                ) {
                    NotificationPopup(
                        notification = currentNotification,
                        layout = currentLayout,
                        durationSeconds = notificationDuration,
                        corner = toastCorner,
                        onDismiss = { OverlayStateStore.dismissCurrentNotification() }
                    )
                }
            }
        }
    }
}

/**
 * Tracks a fixed notification and its animation state.
 * Kept in the render list during exit animation so it can animate out.
 */
private class AnimatedFixedItem(
    var notification: FixedNotification,
    val visibleState: MutableTransitionState<Boolean>
)

@Composable
private fun ClockFixedRow(
    hotCorner: HotCorner,
    clockVisibility: Int,
    overlayCustomization: us.bergnet.oversight.data.model.OverlayCustomization,
    clockTextFormat: String?,
    activeFixed: List<FixedNotification>
) {
    val showClock = clockVisibility > 0
    val expandFrom = if (hotCorner.isStart()) Alignment.Start else Alignment.End

    // Track animated items including those animating out
    val trackedItems = remember { mutableListOf<AnimatedFixedItem>() }

    val activeIds = activeFixed.map { it.id }.toSet()

    // Clean up items whose exit animation has completed
    trackedItems.removeAll { item ->
        !item.visibleState.targetState && item.visibleState.isIdle && !item.visibleState.currentState
    }

    // Add new items or update existing
    activeFixed.forEach { notification ->
        val existing = trackedItems.firstOrNull { it.notification.id == notification.id }
        if (existing != null) {
            existing.notification = notification
            existing.visibleState.targetState = true
        } else {
            trackedItems.add(
                AnimatedFixedItem(
                    notification = notification,
                    visibleState = MutableTransitionState(false).apply { targetState = true }
                )
            )
        }
    }

    // Start exit animation for removed items
    trackedItems.forEach { item ->
        if (item.notification.id !in activeIds) {
            item.visibleState.targetState = false
        }
    }

    // Don't render row if nothing to show (and nothing animating out)
    val hasVisibleItems = trackedItems.any { item ->
        item.visibleState.currentState || item.visibleState.targetState
    }
    if (!showClock && !hasVisibleItems) return

    val renderItems = if (hotCorner.isStart()) trackedItems.toList() else trackedItems.reversed()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (hotCorner.isStart()) {
            if (showClock) {
                ClockOverlay(
                    customization = overlayCustomization.toNonNullable(),
                    clockTextFormat = clockTextFormat
                )
            }
            renderItems.forEach { item ->
                key(item.notification.id) {
                    AnimatedVisibility(
                        visibleState = item.visibleState,
                        enter = expandHorizontally(expandFrom = expandFrom) + fadeIn(),
                        exit = shrinkHorizontally(shrinkTowards = expandFrom) + fadeOut()
                    ) {
                        FixedNotificationBadge(notification = item.notification)
                    }
                }
            }
        } else {
            renderItems.forEach { item ->
                key(item.notification.id) {
                    AnimatedVisibility(
                        visibleState = item.visibleState,
                        enter = expandHorizontally(expandFrom = expandFrom) + fadeIn(),
                        exit = shrinkHorizontally(shrinkTowards = expandFrom) + fadeOut()
                    ) {
                        FixedNotificationBadge(notification = item.notification)
                    }
                }
            }
            if (showClock) {
                ClockOverlay(
                    customization = overlayCustomization.toNonNullable(),
                    clockTextFormat = clockTextFormat
                )
            }
        }
    }
}

@Composable
private fun ToastSection(
    displayNotifications: Boolean,
    currentNotification: us.bergnet.oversight.data.model.ReceivedNotification?,
    currentLayout: us.bergnet.oversight.data.model.NotificationLayout,
    notificationDuration: Int,
    corner: HotCorner
) {
    if (displayNotifications && currentNotification != null) {
        NotificationPopup(
            notification = currentNotification,
            layout = currentLayout,
            durationSeconds = notificationDuration,
            corner = corner,
            onDismiss = { OverlayStateStore.dismissCurrentNotification() }
        )
    }
}
