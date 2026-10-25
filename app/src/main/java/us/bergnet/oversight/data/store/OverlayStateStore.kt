package us.bergnet.oversight.data.store

import us.bergnet.oversight.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object OverlayStateStore {
    // Configuration
    private val _infoValues = MutableStateFlow(InfoValues())
    val infoValues: StateFlow<InfoValues> = _infoValues.asStateFlow()

    // Overlay customization (clock appearance)
    private val _overlayCustomization = MutableStateFlow(OverlayCustomization.DEFAULT)
    val overlayCustomization: StateFlow<OverlayCustomization> = _overlayCustomization.asStateFlow()

    // Clock text format override
    private val _clockTextFormat = MutableStateFlow<String?>(null)
    val clockTextFormat: StateFlow<String?> = _clockTextFormat.asStateFlow()

    // Fixed notifications
    private val _fixedNotifications = MutableStateFlow<List<FixedNotification>>(emptyList())
    val fixedNotifications: StateFlow<List<FixedNotification>> = _fixedNotifications.asStateFlow()

    // Toast notification queue
    private val _currentNotification = MutableStateFlow<ReceivedNotification?>(null)
    val currentNotification: StateFlow<ReceivedNotification?> = _currentNotification.asStateFlow()

    private val _notificationQueue = MutableStateFlow<List<ReceivedNotification>>(emptyList())
    val notificationQueue: StateFlow<List<ReceivedNotification>> = _notificationQueue.asStateFlow()

    // Notification layouts
    private val _layoutList = MutableStateFlow(NotificationLayoutList())
    val layoutList: StateFlow<NotificationLayoutList> = _layoutList.asStateFlow()

    // Screen state
    private val _isScreenOn = MutableStateFlow(true)
    val isScreenOn: StateFlow<Boolean> = _isScreenOn.asStateFlow()

    // Service state
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    // mDNS registration state, as reported by NsdManager callbacks
    private val _isMdnsRegistered = MutableStateFlow(false)
    val isMdnsRegistered: StateFlow<Boolean> = _isMdnsRegistered.asStateFlow()

    // Device info
    private val _deviceId = MutableStateFlow("")
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    // --- Accessors (defaults live in InfoValues.effective()) ---

    fun getDeviceName(): String = _infoValues.value.effective().deviceName

    fun getRemotePort(): Int = _infoValues.value.effective().remotePort

    fun isDisplayNotifications(): Boolean = _infoValues.value.effective().displayNotifications

    // --- Mutators ---

    fun updateInfoValues(new: InfoValues) {
        _infoValues.update { it.merge(new) }
        // The popup leaves composition when notifications are off, so its dismiss timer never fires;
        // without this the stranded toast (and anything queued behind it) reappears on re-enable.
        if (!isDisplayNotifications()) clearNotifications()
    }

    fun clearNotifications() {
        _notificationQueue.value = emptyList()
        _currentNotification.value = null
    }

    fun setInfoValues(values: InfoValues) {
        _infoValues.value = values
    }

    fun setOverlayCustomization(customization: OverlayCustomization) {
        _overlayCustomization.value = customization
    }

    fun setClockTextFormat(format: String?) {
        _clockTextFormat.value = format
    }

    fun setFixedNotifications(notifications: List<FixedNotification>) {
        _fixedNotifications.value = notifications
    }

    fun upsertFixedNotification(notification: FixedNotification) {
        _fixedNotifications.update { current ->
            val index = current.indexOfFirst { it.id == notification.id }
            val merged = if (index >= 0) {
                // Merge: existing values kept for null incoming fields
                current[index].mergeWith(notification)
            } else {
                notification
            }
            val withTime = merged.withReceivedTime()
            val next = current.toMutableList()
            if (index >= 0) next[index] = withTime else next.add(withTime)
            next.filter { !it.isExpired() }
        }
    }

    fun getFixedNotification(id: String): FixedNotification? =
        _fixedNotifications.value.firstOrNull { it.id == id }

    fun removeFixedNotification(id: String) {
        _fixedNotifications.update { current -> current.filter { it.id != id } }
    }

    fun removeExpiredFixedNotifications() {
        _fixedNotifications.update { current ->
            val filtered = current.filter { !it.isExpired() }
            if (filtered.size == current.size) current else filtered
        }
    }

    fun getActiveFixedNotifications(): List<FixedNotification> =
        _fixedNotifications.value.filter { !it.isExpired() && it.isVisible() && !it.isEmpty() }

    fun enqueueNotification(notification: ReceivedNotification) {
        if (notification.isEmpty()) return
        if (!isDisplayNotifications()) return

        // Atomically decide whether this replaces the current notification or appends to the queue.
        // Done as a single update on _currentNotification (the discriminator) so we don't race
        // between reading current and writing the queue.
        var enqueue = false
        _currentNotification.update { current ->
            if (current == null || (current.id == notification.id && current.source == notification.source)) {
                notification
            } else {
                enqueue = true
                current
            }
        }
        if (enqueue) {
            _notificationQueue.update { it + notification }
        }
    }

    fun dismissCurrentNotification() {
        // Pop the head of the queue (if any) into current, atomically.
        var next: ReceivedNotification? = null
        _notificationQueue.update { queue ->
            if (queue.isEmpty()) {
                queue
            } else {
                next = queue.first()
                queue.drop(1)
            }
        }
        _currentNotification.value = next
    }

    fun setScreenOn(on: Boolean) {
        _isScreenOn.value = on
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun setMdnsRegistered(registered: Boolean) {
        _isMdnsRegistered.value = registered
    }

    fun setDeviceId(id: String) {
        _deviceId.value = id
    }

    fun setLayoutList(list: NotificationLayoutList) {
        _layoutList.value = list
    }

    fun upsertLayout(layout: NotificationLayout, select: Boolean) {
        _layoutList.update { current ->
            val next = current.list.toMutableList()
            val idx = next.indexOfFirst { it.name == layout.name }
            if (idx >= 0) next[idx] = layout else next.add(layout)
            current.copy(list = next, selected = if (select) layout.name else current.selected)
        }
        // notificationLayoutName takes precedence over layoutList.selected (see NotificationLayoutList.resolve),
        // so selecting must update both or the old layout stays active.
        if (select) {
            updateInfoValues(InfoValues(notifications = NotificationValues(notificationLayoutName = layout.name)))
        }
    }

    fun getCurrentLayout(): NotificationLayout =
        _layoutList.value.resolve(_infoValues.value.effective().notificationLayoutName)
}
