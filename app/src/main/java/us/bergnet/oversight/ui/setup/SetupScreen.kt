package us.bergnet.oversight.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import us.bergnet.oversight.data.model.ButtonMapping
import us.bergnet.oversight.data.model.InfoValues
import us.bergnet.oversight.data.model.NotificationValues
import us.bergnet.oversight.data.model.OverlayValues
import us.bergnet.oversight.data.model.RemapAction
import us.bergnet.oversight.data.model.SettingsValues
import us.bergnet.oversight.data.model.enums.HotCorner
import us.bergnet.oversight.data.store.OverlayStateStore
import us.bergnet.oversight.ui.components.MdiIcon
import us.bergnet.oversight.util.NetworkUtils
import us.bergnet.oversight.ui.theme.*

internal enum class SId {
    ABOUT,
    DEVICE_NAME, REMOTE_PORT,
    HOT_CORNER, CLOCK_VISIBILITY, DIMMER, PIXEL_SHIFT,
    DISPLAY_NOTIFICATIONS, NOTIFICATION_DURATION,
    DISPLAY_FIXED, FIXED_VISIBILITY,
    REMAP_SERVICE, BUTTON_MAPPINGS,
    SERVICE_CONTROL, CLEAR_FIXED,
    // Steps of the add/edit button mapping flow (selectors, not menu items)
    MAP_PRESS, MAP_ACTION, MAP_APP, MAP_EDIT
}

/** An installed app the "Launch App" action can open. */
data class LaunchableApp(val packageName: String, val label: String)

private sealed interface DialogState {
    data class Selector(val id: SId, val title: String, val options: List<String>, val selected: Int) : DialogState
    data class Slider(val id: SId, val title: String, val value: Int, val range: IntRange, val step: Int, val suffix: String) : DialogState
    data class TextInput(val id: SId, val title: String, val value: String, val numeric: Boolean) : DialogState
    data object Capture : DialogState
}

private sealed interface PanelEntry {
    data class Section(val title: String) : PanelEntry
    data class Item(val id: SId) : PanelEntry
}

private val menuEntries = listOf(
    PanelEntry.Item(SId.ABOUT),
    PanelEntry.Section("Device"),
    PanelEntry.Item(SId.DEVICE_NAME),
    PanelEntry.Item(SId.REMOTE_PORT),
    PanelEntry.Section("Display"),
    PanelEntry.Item(SId.HOT_CORNER),
    PanelEntry.Item(SId.CLOCK_VISIBILITY),
    PanelEntry.Item(SId.DIMMER),
    PanelEntry.Item(SId.PIXEL_SHIFT),
    PanelEntry.Section("Popup Notifications"),
    PanelEntry.Item(SId.DISPLAY_NOTIFICATIONS),
    PanelEntry.Item(SId.NOTIFICATION_DURATION),
    PanelEntry.Section("Fixed Notifications"),
    PanelEntry.Item(SId.DISPLAY_FIXED),
    PanelEntry.Item(SId.FIXED_VISIBILITY),
    PanelEntry.Section("Remote Buttons"),
    PanelEntry.Item(SId.REMAP_SERVICE),
    PanelEntry.Item(SId.BUTTON_MAPPINGS),
    PanelEntry.Section("Service"),
    PanelEntry.Item(SId.SERVICE_CONTROL),
    PanelEntry.Item(SId.CLEAR_FIXED),
)

private fun menuIndexOf(id: SId) =
    menuEntries.indexOfFirst { it is PanelEntry.Item && it.id == id }.coerceAtLeast(0)

private val cornerLabels = listOf("Top Left", "Top Right", "Bottom Left", "Bottom Right")
private val cornerValues = listOf(HotCorner.TOP_START, HotCorner.TOP_END, HotCorner.BOTTOM_START, HotCorner.BOTTOM_END)

private val remapActions = RemapAction.entries

private data class SettingMeta(val title: String, val description: String, val icon: String)

private val settingMeta = mapOf(
    SId.ABOUT to SettingMeta("About", "Status, permissions, and network info", "information-outline"),
    SId.DEVICE_NAME to SettingMeta("Device Name", "The name used to identify this device on the network and via mDNS discovery.", "rename-box"),
    SId.REMOTE_PORT to SettingMeta("Remote Port", "The port number for the REST API server (1024-65535). The server restarts on the new port automatically.", "network-outline"),
    SId.HOT_CORNER to SettingMeta("Hot Corner", "Corner of the screen where the clock and fixed notifications are anchored.", "dock-window"),
    SId.CLOCK_VISIBILITY to SettingMeta("Clock Visibility", "Opacity of the clock overlay. Set to 0% to hide the clock entirely.", "eye-outline"),
    SId.DIMMER to SettingMeta("Screen Dimmer", "Dims the entire screen with a dark overlay. Useful for reducing brightness at night.", "brightness-6"),
    SId.PIXEL_SHIFT to SettingMeta("Pixel Shift", "Periodically shifts the overlay position slightly to help prevent screen burn-in.", "move-resize"),
    SId.DISPLAY_NOTIFICATIONS to SettingMeta("Display Notifications", "Show toast/popup notifications when received via the REST API.", "message-text-outline"),
    SId.NOTIFICATION_DURATION to SettingMeta("Notification Duration", "How long popup notifications are displayed before automatically dismissing.", "timer-outline"),
    SId.DISPLAY_FIXED to SettingMeta("Display Fixed", "Show persistent fixed notification badges alongside the clock overlay.", "pin-outline"),
    SId.FIXED_VISIBILITY to SettingMeta("Fixed Visibility", "Opacity of fixed notification badges. 100% is fully visible, 0% is fully transparent.", "opacity"),
    SId.REMAP_SERVICE to SettingMeta("Remap Service", "Button remapping runs as an accessibility service, which must be turned on in Accessibility settings.", "remote-tv"),
    SId.BUTTON_MAPPINGS to SettingMeta("Button Mappings", "Reassign remote buttons to other actions or apps. The Power button can't be remapped.", "gesture-tap-button"),
    SId.SERVICE_CONTROL to SettingMeta("Service Control", "Start, stop, or restart the overlay service.", "cog-outline"),
    SId.CLEAR_FIXED to SettingMeta("Clear Fixed Notifications", "Remove all fixed notification badges. Useful for clearing stale notifications.", "notification-clear-all"),
)

// ==================== MAIN ====================

@Composable
internal fun SetupScreen(
    hasOverlayPermission: Boolean,
    hasBatteryExemption: Boolean,
    /** Button remapping is turned on in Accessibility settings (the service may still be binding). */
    hasRemapPermission: Boolean,
    /** False when the device has no working exemption screen; the exemption is then shown as not required. */
    canRequestBatteryExemption: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onRestartService: () -> Unit,
    onClearFixedNotifications: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    loadLaunchableApps: () -> List<LaunchableApp>,
    appVersion: String,
    /** Menu item selected on open; snapshots use it to render other panels. */
    initialItem: SId = SId.ABOUT
) {
    val infoValues by OverlayStateStore.infoValues.collectAsState()
    val isRunning by OverlayStateStore.isServiceRunning.collectAsState()
    val mdnsRegistered by OverlayStateStore.isMdnsRegistered.collectAsState()
    val deviceId by OverlayStateStore.deviceId.collectAsState()
    val buttonMappings by OverlayStateStore.buttonMappings.collectAsState()
    val remapConnected by OverlayStateStore.isRemapServiceConnected.collectAsState()

    var selectedItem by remember { mutableStateOf(initialItem) }
    var dialog by remember { mutableStateOf<DialogState?>(null) }
    // Button mapping being added or edited; action is a placeholder until chosen
    var mappingDraft by remember { mutableStateOf<ButtonMapping?>(null) }
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    // Counters as LaunchedEffect keys — incrementing avoids the cancellation bug
    // (setting a boolean key back to false inside the effect cancels the running coroutine)
    var restoreFocusTrigger by remember { mutableIntStateOf(0) }
    var rightTransferTrigger by remember { mutableIntStateOf(0) }
    var leftActive by remember { mutableStateOf(true) }

    val rightFocus = remember { FocusRequester() }
    val leftFocusMap = remember { SId.entries.associateWith { FocusRequester() } }
    // Starts scrolled to the initial item so it can take focus
    val leftListState = rememberLazyListState(initialFirstVisibleItemIndex = menuIndexOf(initialItem))
    val scope = rememberCoroutineScope()

    /**
     * Returns focus to the selected menu item. The menu is lazy, so an item scrolled out of view
     * isn't composed and can't take focus until it's scrolled back in.
     */
    fun focusLeftMenu() {
        scope.launch {
            val index = menuIndexOf(selectedItem)
            if (leftListState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                leftListState.scrollToItem(index)
                withFrameNanos {} // let the item compose and attach before focusing it
            }
            try { leftFocusMap[selectedItem]?.requestFocus() } catch (_: Exception) {}
        }
    }

    // Clock overlap padding
    // Current values
    val settings = infoValues.effective()
    val hotCorner = settings.hotCorner
    val clockAtTop = hotCorner == HotCorner.TOP_START || hotCorner == HotCorner.TOP_END
    val topPad = if (clockAtTop && settings.clockVisibility > 0) 48.dp else 0.dp

    val deviceName = settings.deviceName
    val port = settings.remotePort
    val cornerIdx = cornerValues.indexOf(hotCorner).coerceAtLeast(0)
    val clockVisibility = settings.clockVisibility
    val dimmer = settings.overlayVisibility
    val pixelShift = settings.pixelShift
    val displayNotifs = settings.displayNotifications
    val duration = settings.notificationDuration
    val displayFixed = settings.displayFixedNotifications
    val fixedVisibility = settings.fixedNotificationsVisibility
    val ip = remember { NetworkUtils.getDeviceIpAddress() ?: "Unknown" }

    fun applyValue(id: SId, value: String) {
        when (id) {
            SId.HOT_CORNER -> value.toIntOrNull()?.let { OverlayStateStore.updateInfoValues(InfoValues(overlay = OverlayValues(hotCorner = cornerValues[it].restApiName))) }
            SId.CLOCK_VISIBILITY -> OverlayStateStore.updateInfoValues(InfoValues(overlay = OverlayValues(clockOverlayVisibility = value.toInt())))
            SId.DIMMER -> OverlayStateStore.updateInfoValues(InfoValues(overlay = OverlayValues(overlayVisibility = value.toInt())))
            SId.NOTIFICATION_DURATION -> OverlayStateStore.updateInfoValues(InfoValues(notifications = NotificationValues(notificationDuration = value.toInt())))
            SId.FIXED_VISIBILITY -> OverlayStateStore.updateInfoValues(InfoValues(notifications = NotificationValues(fixedNotificationsVisibility = value.toInt())))
            SId.DEVICE_NAME -> OverlayStateStore.updateInfoValues(InfoValues(settings = SettingsValues(deviceName = value)))
            SId.REMOTE_PORT -> value.toIntOrNull()?.let { OverlayStateStore.updateInfoValues(InfoValues(settings = SettingsValues(remotePort = it))) }
            else -> {}
        }
    }

    fun openEditor(id: SId) {
        leftActive = false
        dialog = when (id) {
            SId.HOT_CORNER -> DialogState.Selector(id, "Hot Corner", cornerLabels, cornerIdx)
            SId.CLOCK_VISIBILITY -> DialogState.Slider(id, "Clock Visibility", clockVisibility, OverlayValues.VALID_CLOCK_VISIBILITY, 5, "%")
            SId.DIMMER -> DialogState.Slider(id, "Screen Dimmer", dimmer, OverlayValues.VALID_OVERLAY_VISIBILITY, 5, "%")
            SId.NOTIFICATION_DURATION -> DialogState.Slider(id, "Duration", duration, NotificationValues.VALID_DURATION, 1, "s")
            SId.FIXED_VISIBILITY -> DialogState.Slider(id, "Fixed Visibility", fixedVisibility, NotificationValues.VALID_FIXED_VISIBILITY, 5, "%")
            SId.DEVICE_NAME -> DialogState.TextInput(id, "Device Name", deviceName, false)
            SId.REMOTE_PORT -> DialogState.TextInput(id, "Remote Port", port.toString(), true)
            else -> null
        }
    }

    val dismissDialog: () -> Unit = {
        dialog = null
        mappingDraft = null
        restoreFocusTrigger++
    }

    fun startMappingCapture() {
        leftActive = false
        dialog = DialogState.Capture
    }

    fun onKeyCaptured(keyCode: Int) {
        mappingDraft = ButtonMapping(keyCode, longPress = false, action = RemapAction.BLOCK)
        val shortKept = if (ButtonMapping.canReproduceDefault(keyCode)) "short press works as normal" else "short press does nothing"
        dialog = DialogState.Selector(
            SId.MAP_PRESS, ButtonMapping.keyName(keyCode),
            listOf("Short press", "Long press ($shortKept unless mapped)"), -1
        )
    }

    fun editMapping(mapping: ButtonMapping) {
        leftActive = false
        mappingDraft = mapping
        dialog = DialogState.Selector(SId.MAP_EDIT, mapping.title(), listOf("Change action", "Delete"), -1)
    }

    fun chooseAction(draft: ButtonMapping) {
        val existing = buttonMappings.firstOrNull { it.matches(draft.keyCode, draft.longPress) }
        dialog = DialogState.Selector(
            SId.MAP_ACTION, draft.title(), remapActions.map { it.label },
            existing?.let { remapActions.indexOf(it.action) } ?: -1
        )
    }

    /** Handles a pick in any selector; the mapping flow advances to its next step instead of closing. */
    fun onSelectorPick(id: SId, index: Int) {
        val draft = mappingDraft
        when (id) {
            SId.MAP_PRESS -> draft?.copy(longPress = index == 1)?.let { mappingDraft = it; chooseAction(it) }
            SId.MAP_EDIT -> when {
                draft == null -> dismissDialog()
                index == 0 -> chooseAction(draft)
                else -> { OverlayStateStore.removeButtonMapping(draft.keyCode, draft.longPress); dismissDialog() }
            }
            SId.MAP_ACTION -> {
                val action = remapActions[index]
                when {
                    draft == null -> dismissDialog()
                    action == RemapAction.LAUNCH_APP -> {
                        mappingDraft = draft.copy(action = action)
                        apps = loadLaunchableApps()
                        if (apps.isEmpty()) { dismissDialog(); return }
                        dialog = DialogState.Selector(SId.MAP_APP, "Launch which app?", apps.map { it.label },
                            apps.indexOfFirst { it.packageName == draft.packageName })
                    }
                    else -> {
                        OverlayStateStore.upsertButtonMapping(draft.copy(action = action, packageName = null, appLabel = null))
                        dismissDialog()
                    }
                }
            }
            SId.MAP_APP -> {
                val app = apps.getOrNull(index)
                if (draft != null && app != null) {
                    OverlayStateStore.upsertButtonMapping(draft.copy(packageName = app.packageName, appLabel = app.label))
                }
                dismissDialog()
            }
            else -> { applyValue(id, index.toString()); dismissDialog() }
        }
    }

    // Restore focus after dialog closes — counter key avoids self-cancellation
    LaunchedEffect(restoreFocusTrigger) {
        if (restoreFocusTrigger > 0) {
            delay(250)
            try { rightFocus.requestFocus() } catch (_: Exception) { focusLeftMenu() }
            delay(50)
            leftActive = true
        }
    }

    // Deferred focus transfer: Enter/OK on left menu waits for KeyUp to settle
    LaunchedEffect(rightTransferTrigger) {
        if (rightTransferTrigger > 0) {
            delay(100)
            if (selectedItem != SId.ABOUT) {
                try { rightFocus.requestFocus() } catch (_: Exception) {}
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Row(modifier = Modifier.fillMaxSize().padding(top = topPad)) {
            // Left panel
            Box(modifier = Modifier.weight(0.28f).fillMaxHeight().background(PanelBg)) {
                LeftPanel(
                    selectedItem = selectedItem,
                    listState = leftListState,
                    focusMap = leftFocusMap,
                    enabled = leftActive,
                    onItemFocused = { selectedItem = it },
                    onNavigateRight = {
                        if (selectedItem != SId.ABOUT) {
                            try { rightFocus.requestFocus() } catch (_: Exception) {}
                        }
                    },
                    onActivate = {
                        if (selectedItem != SId.ABOUT) {
                            rightTransferTrigger++
                        }
                    }
                )
            }

            // Right panel — global Back/Left interceptor returns focus to left menu
            Box(
                modifier = Modifier.weight(0.72f).fillMaxHeight()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionLeft, Key.Back, Key.Escape -> {
                                    focusLeftMenu()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                RightPanel(
                    selectedItem = selectedItem,
                    infoValues = infoValues, isRunning = isRunning, mdnsRegistered = mdnsRegistered,
                    deviceId = deviceId, ip = ip,
                    hasOverlayPermission = hasOverlayPermission, hasBatteryExemption = hasBatteryExemption,
                    canRequestBatteryExemption = canRequestBatteryExemption, hasRemapPermission = hasRemapPermission,
                    buttonMappings = buttonMappings, remapConnected = remapConnected,
                    appVersion = appVersion, rightFocus = rightFocus, enabled = leftActive,
                    onNavigateLeft = ::focusLeftMenu,
                    onToggle = { id ->
                        when (id) {
                            SId.PIXEL_SHIFT -> OverlayStateStore.updateInfoValues(InfoValues(settings = SettingsValues(pixelShift = !pixelShift)))
                            SId.DISPLAY_NOTIFICATIONS -> OverlayStateStore.updateInfoValues(InfoValues(notifications = NotificationValues(displayNotifications = !displayNotifs)))
                            SId.DISPLAY_FIXED -> OverlayStateStore.updateInfoValues(InfoValues(notifications = NotificationValues(displayFixedNotifications = !displayFixed)))
                            else -> {}
                        }
                    },
                    onEdit = ::openEditor,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onRequestBatteryExemption = onRequestBatteryExemption,
                    onStartService = onStartService, onStopService = onStopService,
                    onRestartService = onRestartService, onClearFixedNotifications = onClearFixedNotifications,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onAddMapping = ::startMappingCapture, onEditMapping = ::editMapping
                )
            }
        }

        // ---- Selector slide-in (separate backdrop + panel for clean animation) ----
        var lastSelector by remember { mutableStateOf<DialogState.Selector?>(null) }
        val isSelectorOpen = dialog is DialogState.Selector
        if (isSelectorOpen) lastSelector = dialog as DialogState.Selector

        // Backdrop fades independently
        AnimatedVisibility(visible = isSelectorOpen, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        }
        // Panel slides from right
        AnimatedVisibility(
            visible = isSelectorOpen,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(0.4f)
                        .align(Alignment.CenterEnd).background(PanelBg).padding(24.dp)
                ) {
                    lastSelector?.let { state ->
                        // Keyed so each step of a multi-step flow starts with fresh focus
                        key(state) {
                            SelectorContent(
                                state = state,
                                onSelect = { idx -> onSelectorPick(state.id, idx) },
                                onDismiss = dismissDialog
                            )
                        }
                    }
                }
            }
        }

        // ---- Centered dialogs (slider, text input, key capture) ----
        if (dialog is DialogState.Slider || dialog is DialogState.TextInput || dialog is DialogState.Capture) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.width(400.dp)
                        .background(Color(0xFF222244), RoundedCornerShape(16.dp)).padding(24.dp)
                ) {
                    when (val d = dialog) {
                        is DialogState.Slider -> SliderContent(
                            state = d,
                            onLiveUpdate = { id, v -> applyValue(id, v) },
                            onDismiss = dismissDialog
                        )
                        is DialogState.TextInput -> TextInputContent(
                            state = d,
                            onSave = { id, v -> applyValue(id, v); dismissDialog() },
                            onDismiss = dismissDialog
                        )
                        DialogState.Capture -> CaptureContent(
                            serviceConnected = remapConnected,
                            onCaptured = ::onKeyCaptured,
                            onDismiss = dismissDialog
                        )
                        else -> {}
                    }
                }
            }
        }
    }
}

// ==================== LEFT PANEL ====================

@Composable
private fun LeftPanel(
    selectedItem: SId,
    listState: LazyListState,
    focusMap: Map<SId, FocusRequester>,
    enabled: Boolean,
    onItemFocused: (SId) -> Unit,
    onNavigateRight: () -> Unit,
    onActivate: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "OverSight", color = Accent, fontSize = 22.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            menuEntries.forEach { entry ->
                when (entry) {
                    is PanelEntry.Section -> item {
                        Text(
                            text = entry.title.uppercase(), color = TextMuted, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                        )
                    }
                    is PanelEntry.Item -> item {
                        LeftMenuItem(
                            title = settingMeta[entry.id]!!.title,
                            isSelected = selectedItem == entry.id,
                            focusRequester = focusMap[entry.id],
                            enabled = enabled,
                            onFocused = { onItemFocused(entry.id) },
                            onRight = onNavigateRight,
                            onActivate = onActivate
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        try { focusMap[selectedItem]?.requestFocus() } catch (_: Exception) {}
    }
}

@Composable
private fun LeftMenuItem(
    title: String, isSelected: Boolean, focusRequester: FocusRequester?,
    enabled: Boolean, onFocused: () -> Unit, onRight: () -> Unit, onActivate: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        when {
            focused -> Surface
            isSelected -> Color(0xFF1E1E3A)
            else -> Color.Transparent
        }, label = "leftBg"
    )

    Row(
        modifier = Modifier.fillMaxWidth()
            .onFocusChanged { focused = it.isFocused; if (it.isFocused && enabled) onFocused() }
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onPreviewKeyEvent { event ->
                // Block all input when disabled (dialog open)
                if (!enabled) return@onPreviewKeyEvent true
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionRight -> { onRight(); true }
                        Key.Enter, Key.DirectionCenter -> { onActivate(); true }
                        else -> false
                    }
                } else if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    true // consume KeyUp so it doesn't propagate after deferred transfer
                } else false
            }
            .focusable()
            .background(bgColor),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(3.dp).height(44.dp).background(if (focused) Accent else Color.Transparent))
        Text(
            text = title,
            color = if (focused) TextPrimary else TextSecondary,
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

// ==================== RIGHT PANEL ====================

@Composable
private fun RightPanel(
    selectedItem: SId,
    infoValues: InfoValues,
    isRunning: Boolean, mdnsRegistered: Boolean, deviceId: String, ip: String,
    hasOverlayPermission: Boolean, hasBatteryExemption: Boolean, canRequestBatteryExemption: Boolean,
    hasRemapPermission: Boolean,
    buttonMappings: List<ButtonMapping>, remapConnected: Boolean,
    appVersion: String, rightFocus: FocusRequester, enabled: Boolean,
    onNavigateLeft: () -> Unit,
    onToggle: (SId) -> Unit, onEdit: (SId) -> Unit,
    onRequestOverlayPermission: () -> Unit, onRequestBatteryExemption: () -> Unit,
    onStartService: () -> Unit, onStopService: () -> Unit,
    onRestartService: () -> Unit, onClearFixedNotifications: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onAddMapping: () -> Unit, onEditMapping: (ButtonMapping) -> Unit
) {
    val settings = infoValues.effective()

    // Shared modifier: d-pad left AND back both return to left panel
    val backToLeft = Modifier.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown &&
            (event.key == Key.DirectionLeft || event.key == Key.Back || event.key == Key.Escape)
        ) { onNavigateLeft(); true }
        else false
    }

    when (selectedItem) {
        SId.ABOUT -> AboutDetail(
            infoValues = infoValues, isRunning = isRunning, mdnsRegistered = mdnsRegistered, deviceId = deviceId, ip = ip,
            hasOverlayPermission = hasOverlayPermission, hasBatteryExemption = hasBatteryExemption,
            canRequestBatteryExemption = canRequestBatteryExemption,
            hasRemapPermission = hasRemapPermission, remapConnected = remapConnected,
            appVersion = appVersion, rightFocus = rightFocus, enabled = enabled, backToLeft = backToLeft,
            onRequestOverlayPermission = onRequestOverlayPermission,
            onRequestBatteryExemption = onRequestBatteryExemption
        )
        SId.PIXEL_SHIFT -> ToggleDetail(
            meta = settingMeta[SId.PIXEL_SHIFT]!!,
            isOn = settings.pixelShift,
            rightFocus = rightFocus, enabled = enabled, backToLeft = backToLeft,
            onSetValue = { wantOn ->
                if (wantOn != settings.pixelShift) onToggle(SId.PIXEL_SHIFT)
            }
        )
        SId.DISPLAY_NOTIFICATIONS -> ToggleDetail(
            meta = settingMeta[SId.DISPLAY_NOTIFICATIONS]!!,
            isOn = settings.displayNotifications,
            rightFocus = rightFocus, enabled = enabled, backToLeft = backToLeft,
            onSetValue = { wantOn ->
                if (wantOn != settings.displayNotifications) onToggle(SId.DISPLAY_NOTIFICATIONS)
            }
        )
        SId.DISPLAY_FIXED -> ToggleDetail(
            meta = settingMeta[SId.DISPLAY_FIXED]!!,
            isOn = settings.displayFixedNotifications,
            rightFocus = rightFocus, enabled = enabled, backToLeft = backToLeft,
            onSetValue = { wantOn ->
                if (wantOn != settings.displayFixedNotifications) onToggle(SId.DISPLAY_FIXED)
            }
        )
        SId.SERVICE_CONTROL -> ServiceDetail(
            isRunning = isRunning, hasOverlayPermission = hasOverlayPermission,
            rightFocus = rightFocus, enabled = enabled, backToLeft = backToLeft,
            onStartService = onStartService, onStopService = onStopService, onRestartService = onRestartService
        )
        SId.REMAP_SERVICE -> RemapServiceDetail(
            connected = remapConnected,
            rightFocus = rightFocus, backToLeft = backToLeft,
            onOpenSettings = onOpenAccessibilitySettings
        )
        SId.BUTTON_MAPPINGS -> ButtonMappingsDetail(
            mappings = buttonMappings, serviceConnected = remapConnected,
            rightFocus = rightFocus, backToLeft = backToLeft,
            onAdd = onAddMapping, onEdit = onEditMapping
        )
        SId.MAP_PRESS, SId.MAP_ACTION, SId.MAP_APP, SId.MAP_EDIT -> {}
        SId.CLEAR_FIXED -> ActionDetail(
            meta = settingMeta[SId.CLEAR_FIXED]!!, buttonText = "Clear All", buttonColor = Orange,
            rightFocus = rightFocus, enabled = enabled, backToLeft = backToLeft,
            onClick = onClearFixedNotifications
        )
        else -> ValueDetail(
            meta = settingMeta[selectedItem]!!,
            currentValue = when (selectedItem) {
                SId.DEVICE_NAME -> settings.deviceName
                SId.REMOTE_PORT -> settings.remotePort.toString()
                SId.HOT_CORNER -> cornerLabels[cornerValues.indexOf(settings.hotCorner).coerceAtLeast(0)]
                SId.CLOCK_VISIBILITY -> "${settings.clockVisibility}%"
                SId.DIMMER -> "${settings.overlayVisibility}%"
                SId.NOTIFICATION_DURATION -> "${settings.notificationDuration}s"
                SId.FIXED_VISIBILITY -> "${settings.fixedNotificationsVisibility}%"
            },
            rightFocus = rightFocus, enabled = enabled, backToLeft = backToLeft,
            onEdit = { onEdit(selectedItem) }
        )
    }
}

// ==================== DETAIL VIEWS ====================

@Composable
private fun AboutDetail(
    infoValues: InfoValues,
    isRunning: Boolean, mdnsRegistered: Boolean, deviceId: String, ip: String,
    hasOverlayPermission: Boolean, hasBatteryExemption: Boolean, canRequestBatteryExemption: Boolean,
    hasRemapPermission: Boolean, remapConnected: Boolean,
    appVersion: String, rightFocus: FocusRequester, enabled: Boolean,
    backToLeft: Modifier,
    onRequestOverlayPermission: () -> Unit, onRequestBatteryExemption: () -> Unit
) {
    val settings = infoValues.effective()

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier.size(72.dp).background(Accent.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) { MdiIcon("television", tint = Accent, size = 40.dp) }
        Spacer(modifier = Modifier.height(12.dp))
        Text("OverSight", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("v$appVersion", color = TextMuted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Overlay notifications for your TV,\ncontrolled via REST API.", color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
        Text("github.com/evil-dog/oversight", color = Accent, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(20.dp))

        Column(modifier = Modifier.fillMaxWidth(0.85f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoLine("Service", if (isRunning) "Running" else "Stopped", if (isRunning) Green else Red)
            InfoLine("mDNS", if (mdnsRegistered) "Advertising" else "Inactive", if (mdnsRegistered) Green else TextMuted)
            InfoLine("IP Address", ip, TextPrimary)
            InfoLine("Port", settings.remotePort.toString(), TextPrimary)
            InfoLine("Device", settings.deviceName, TextPrimary)
            InfoLine("Device ID", deviceId, TextMuted)
            InfoLine("Overlay", if (hasOverlayPermission) "Granted" else "Required", if (hasOverlayPermission) Green else Red)
            when {
                hasBatteryExemption -> InfoLine("Battery", "Exempt", Green)
                canRequestBatteryExemption -> InfoLine("Battery", "Not Exempt", Orange)
                else -> InfoLine("Battery", "Not Required", TextMuted)
            }
            // Remapping is optional, so "off" is muted rather than an error
            InfoLine("Remap Permission", if (hasRemapPermission) "Granted" else "Not Granted",
                if (hasRemapPermission) Green else TextMuted)
            when {
                remapConnected -> InfoLine("Remap Service", "Running", Green)
                hasRemapPermission -> InfoLine("Remap Service", "Not Running", Red)
                else -> InfoLine("Remap Service", "Off", TextMuted)
            }
        }

        val showBatteryButton = !hasBatteryExemption && canRequestBatteryExemption
        if (!hasOverlayPermission || showBatteryButton) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!hasOverlayPermission) {
                    Button(
                        onClick = onRequestOverlayPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = backToLeft.focusRequester(rightFocus)
                    ) { Text("Grant Overlay", fontSize = 12.sp) }
                }
                if (showBatteryButton) {
                    Button(
                        onClick = onRequestBatteryExemption,
                        colors = ButtonDefaults.buttonColors(containerColor = Orange),
                        shape = RoundedCornerShape(8.dp),
                        modifier = backToLeft.let { if (hasOverlayPermission) it.focusRequester(rightFocus) else it }
                    ) { Text("Battery Exemption", fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun ToggleDetail(
    meta: SettingMeta, isOn: Boolean,
    rightFocus: FocusRequester, enabled: Boolean, backToLeft: Modifier,
    onSetValue: (Boolean) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        DetailHeader(meta)
        Spacer(modifier = Modifier.height(24.dp))
        // Focus goes to the button matching the CURRENT state
        ToggleOptionButton(
            label = "On", isActive = isOn, activeColor = Green,
            focusRequester = if (isOn) rightFocus else null,
            enabled = enabled, backToLeft = backToLeft,
            onClick = { onSetValue(true) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        ToggleOptionButton(
            label = "Off", isActive = !isOn, activeColor = Red,
            focusRequester = if (!isOn) rightFocus else null,
            enabled = enabled, backToLeft = backToLeft,
            onClick = { onSetValue(false) }
        )
    }
}

@Composable
private fun ToggleOptionButton(
    label: String, isActive: Boolean, activeColor: Color,
    focusRequester: FocusRequester?, enabled: Boolean, backToLeft: Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bgColor = if (isActive) activeColor.copy(alpha = 0.15f) else Color(0xFF1E1E3A)

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp),
        modifier = backToLeft
            .onFocusChanged { focused = it.isFocused }
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .let { if (focused) it.border(2.dp, Accent, RoundedCornerShape(12.dp)) else it }
            .width(220.dp).height(52.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isActive) MdiIcon("check-circle", tint = activeColor, size = 22.dp)
            else MdiIcon("circle-outline", tint = TextMuted, size = 22.dp)
            Text(label, color = if (isActive) activeColor else TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ValueDetail(
    meta: SettingMeta, currentValue: String,
    rightFocus: FocusRequester, enabled: Boolean, backToLeft: Modifier,
    onEdit: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        DetailHeader(meta)
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onEdit,
            colors = ButtonDefaults.buttonColors(containerColor = Surface),
            shape = RoundedCornerShape(12.dp),
            modifier = backToLeft
                .onFocusChanged { focused = it.isFocused }
                .focusRequester(rightFocus)
                .let { if (focused) it.border(2.dp, Accent, RoundedCornerShape(12.dp)) else it }
                .width(240.dp).height(56.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(currentValue, color = TextPrimary, fontSize = 16.sp)
                MdiIcon("pencil", tint = Accent, size = 20.dp)
            }
        }
    }
}

@Composable
private fun ServiceDetail(
    isRunning: Boolean, hasOverlayPermission: Boolean,
    rightFocus: FocusRequester, enabled: Boolean, backToLeft: Modifier,
    onStartService: () -> Unit, onStopService: () -> Unit, onRestartService: () -> Unit
) {
    val startEnabled = !isRunning && hasOverlayPermission
    val stopEnabled = isRunning
    val restartEnabled = isRunning
    // Assign rightFocus to first enabled button
    val startFocus = if (startEnabled) rightFocus else null
    val stopFocus = if (!startEnabled && stopEnabled) rightFocus else null
    val restartFocus = if (!startEnabled && !stopEnabled && restartEnabled) rightFocus else null

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        DetailHeader(settingMeta[SId.SERVICE_CONTROL]!!)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (isRunning) "Service is running" else "Service is stopped",
            color = if (isRunning) Green else Red, fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ServiceBtn("Start", Green, startEnabled, startFocus, backToLeft, onStartService)
            ServiceBtn("Stop", Red, stopEnabled, stopFocus, backToLeft, onStopService)
            ServiceBtn("Restart", Accent, restartEnabled, restartFocus, backToLeft, onRestartService)
        }
    }
}

@Composable
private fun ServiceBtn(
    text: String, color: Color, btnEnabled: Boolean,
    focusRequester: FocusRequester?, backToLeft: Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick, enabled = btnEnabled,
        colors = ButtonDefaults.buttonColors(containerColor = color, disabledContainerColor = color.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(10.dp),
        modifier = backToLeft
            .onFocusChanged { focused = it.isFocused }
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .let { if (focused) it.border(2.dp, Accent, RoundedCornerShape(10.dp)) else it }
            .width(180.dp).height(48.dp)
    ) { Text(text, fontSize = 15.sp) }
}

@Composable
private fun ActionDetail(
    meta: SettingMeta, buttonText: String, buttonColor: Color,
    rightFocus: FocusRequester, enabled: Boolean, backToLeft: Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        DetailHeader(meta)
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
            shape = RoundedCornerShape(10.dp),
            modifier = backToLeft
                .onFocusChanged { focused = it.isFocused }
                .focusRequester(rightFocus)
                .let { if (focused) it.border(2.dp, Accent, RoundedCornerShape(10.dp)) else it }
                .width(180.dp).height(48.dp)
        ) { Text(buttonText, fontSize = 15.sp) }
    }
}

@Composable
private fun RemapServiceDetail(
    connected: Boolean,
    rightFocus: FocusRequester, backToLeft: Modifier,
    onOpenSettings: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        DetailHeader(settingMeta[SId.REMAP_SERVICE]!!)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (connected) "Service is enabled" else "Service is disabled",
            color = if (connected) Green else Red, fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onOpenSettings,
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(10.dp),
            modifier = backToLeft
                .onFocusChanged { focused = it.isFocused }
                .focusRequester(rightFocus)
                .let { if (focused) it.border(2.dp, TextPrimary, RoundedCornerShape(10.dp)) else it }
                .height(48.dp)
        ) { Text("Accessibility Settings", fontSize = 15.sp) }
        if (!connected) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Turn on \"OverSight Button Remapping\". If the switch is greyed out (Android 13+), first choose " +
                    "\"Allow restricted settings\" from OverSight's App info menu.",
                color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.8f)
            )
        }
    }
}

@Composable
private fun ButtonMappingsDetail(
    mappings: List<ButtonMapping>, serviceConnected: Boolean,
    rightFocus: FocusRequester, backToLeft: Modifier,
    onAdd: () -> Unit, onEdit: (ButtonMapping) -> Unit
) {
    val sorted = mappings.sortedWith(compareBy({ ButtonMapping.keyName(it.keyCode) }, { it.longPress }))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        DetailHeader(settingMeta[SId.BUTTON_MAPPINGS]!!)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (serviceConnected) "Mappings are paused while these settings are open."
            else "The Remap Service is disabled, so mappings won't apply.",
            color = if (serviceConnected) TextMuted else Orange, fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        MappingRow(
            title = "Add Mapping", subtitle = null, icon = "plus",
            focusRequester = rightFocus, backToLeft = backToLeft, onClick = onAdd
        )
        sorted.forEach { mapping ->
            Spacer(modifier = Modifier.height(8.dp))
            val shortLost = mapping.longPress && !ButtonMapping.canReproduceDefault(mapping.keyCode) &&
                mappings.none { it.matches(mapping.keyCode, longPress = false) }
            MappingRow(
                title = "${mapping.title()}  \u2192  ${mapping.actionLabel()}",
                subtitle = if (shortLost) "Short press does nothing" else null,
                icon = if (mapping.longPress) "gesture-tap-hold" else "gesture-tap",
                focusRequester = null, backToLeft = backToLeft, onClick = { onEdit(mapping) }
            )
        }
    }
}

@Composable
private fun MappingRow(
    title: String, subtitle: String?, icon: String,
    focusRequester: FocusRequester?, backToLeft: Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Surface),
        shape = RoundedCornerShape(10.dp),
        modifier = backToLeft
            .onFocusChanged { focused = it.isFocused }
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .let { if (focused) it.border(2.dp, Accent, RoundedCornerShape(10.dp)) else it }
            .fillMaxWidth(0.8f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            MdiIcon(icon, tint = Accent, size = 20.dp)
            Column {
                Text(title, color = TextPrimary, fontSize = 15.sp)
                if (subtitle != null) Text(subtitle, color = Orange, fontSize = 12.sp)
            }
        }
    }
}

/** "Back" or "Back (long press)", identifying a mapping's trigger. */
private fun ButtonMapping.title() =
    ButtonMapping.keyName(keyCode) + if (longPress) " (long press)" else ""

@Composable
private fun DetailHeader(meta: SettingMeta) {
    Box(
        modifier = Modifier.size(72.dp).background(Accent.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center
    ) { MdiIcon(meta.icon, tint = Accent, size = 36.dp) }
    Spacer(modifier = Modifier.height(16.dp))
    Text(meta.title, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))
    Text(meta.description, color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(0.8f))
}

@Composable
private fun InfoLine(label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(value, color = valueColor, fontSize = 13.sp)
    }
}

// ==================== SELECTOR (slide-in from right) ====================

@Composable
private fun SelectorContent(
    state: DialogState.Selector,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier.fillMaxHeight()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Back || event.key == Key.Escape || event.key == Key.DirectionLeft)
                ) { onDismiss(); true }
                else false
            }
    ) {
        Text(state.title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))

        // Scrolls for long lists (actions, apps); focused rows bring themselves into view
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            state.options.forEachIndexed { index, option ->
                var focused by remember { mutableStateOf(false) }
                val isSelected = index == state.selected

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused }
                        .let { if (index == 0) it.focusRequester(firstFocus) else it }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                (event.key == Key.Enter || event.key == Key.DirectionCenter)
                            ) {
                                // A held OK must not also pick in the next step of a multi-step flow
                                if (event.nativeKeyEvent.repeatCount == 0) onSelect(index)
                                true
                            }
                            else if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) true // consume
                            else false
                        }
                        .focusable()
                        .background(if (focused) Surface else Color.Transparent, RoundedCornerShape(8.dp))
                        .let { if (focused) it.border(1.dp, Accent, RoundedCornerShape(8.dp)) else it }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MdiIcon(
                        if (isSelected) "radiobox-marked" else "radiobox-blank",
                        tint = if (isSelected) Accent else TextMuted, size = 22.dp
                    )
                    Text(option, color = TextPrimary, fontSize = 16.sp)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        try { firstFocus.requestFocus() } catch (_: Exception) {}
    }
}

// ==================== KEY CAPTURE (centered dialog) ====================

private const val CAPTURE_TIMEOUT_MS = 10_000L

/**
 * Waits for the remote button to remap. ButtonRemapService does the capturing, so buttons the
 * activity never receives (Home, vendor app buttons) can be captured too; Back is captured like
 * any other button, so the dialog times out instead.
 */
@Composable
private fun CaptureContent(
    serviceConnected: Boolean,
    onCaptured: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val focusReq = remember { FocusRequester() }

    if (serviceConnected) {
        DisposableEffect(Unit) {
            OverlayStateStore.keyCaptureListener = onCaptured
            onDispose { OverlayStateStore.keyCaptureListener = null }
        }
        LaunchedEffect(Unit) {
            delay(CAPTURE_TIMEOUT_MS)
            onDismiss()
        }
    }

    Column(
        modifier = Modifier
            .focusRequester(focusReq)
            .onPreviewKeyEvent { event ->
                // Only reached when the service isn't capturing
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Back || event.key == Key.Escape || event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) onDismiss()
                true
            }
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MdiIcon("remote-tv", tint = Accent, size = 40.dp)
        Spacer(modifier = Modifier.height(12.dp))
        if (serviceConnected) {
            Text("Press a Button", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Press the remote button you want to remap.", color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Cancels after ${CAPTURE_TIMEOUT_MS / 1000} seconds", color = TextMuted, fontSize = 12.sp)
        } else {
            Text("Remap Service Disabled", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Turn on the Remap Service before adding mappings.", color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Text("OK / Back to close", color = TextMuted, fontSize = 12.sp)
        }
    }

    LaunchedEffect(Unit) {
        try { focusReq.requestFocus() } catch (_: Exception) {}
    }
}

// ==================== SLIDER (centered, live preview) ====================

@Composable
private fun SliderContent(
    state: DialogState.Slider,
    onLiveUpdate: (SId, String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember(state.id, state.value) { mutableIntStateOf(state.value) }
    val focusReq = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .focusRequester(focusReq)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            val v = (value - state.step).coerceIn(state.range)
                            value = v; onLiveUpdate(state.id, v.toString()); true
                        }
                        Key.DirectionRight -> {
                            val v = (value + state.step).coerceIn(state.range)
                            value = v; onLiveUpdate(state.id, v.toString()); true
                        }
                        Key.Back, Key.Escape, Key.Enter, Key.DirectionCenter -> { onDismiss(); true }
                        else -> true
                    }
                } else true // consume KeyUp to prevent propagation
            }
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(state.title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        Text("$value${state.suffix}", color = Accent, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        val fraction = if (state.range.last > state.range.first)
            (value - state.range.first).toFloat() / (state.range.last - state.range.first) else 0f

        Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(Color(0xFF333355), RoundedCornerShape(4.dp))) {
            Box(modifier = Modifier.fillMaxWidth(fraction).height(8.dp).background(Accent, RoundedCornerShape(4.dp)))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${state.range.first}${state.suffix}", color = TextMuted, fontSize = 12.sp)
            Text("${state.range.last}${state.suffix}", color = TextMuted, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("\u25C0  \u25B6  adjust  \u2022  OK / Back to close", color = TextMuted, fontSize = 12.sp)
    }

    LaunchedEffect(Unit) {
        try { focusReq.requestFocus() } catch (_: Exception) {}
    }
}

// ==================== TEXT INPUT (centered dialog) ====================

@Composable
private fun TextInputContent(
    state: DialogState.TextInput,
    onSave: (SId, String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(state.id, state.value) { mutableStateOf(state.value) }
    val focusReq = remember { FocusRequester() }

    Column(
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) {
                onDismiss(); true
            } else false
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(state.title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))

        BasicTextField(
            value = text, onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focusReq),
            textStyle = TextStyle(color = TextPrimary, fontSize = 18.sp, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(Accent), singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (state.numeric) KeyboardType.Number else KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSave(state.id, text) }),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .border(2.dp, Accent, RoundedCornerShape(8.dp)).padding(16.dp),
                    contentAlignment = Alignment.Center
                ) { inner() }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333355)),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Cancel", fontSize = 14.sp) }
            Button(
                onClick = { onSave(state.id, text) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Save", fontSize = 14.sp) }
        }
    }

    LaunchedEffect(Unit) {
        try { focusReq.requestFocus() } catch (_: Exception) {}
    }
}
