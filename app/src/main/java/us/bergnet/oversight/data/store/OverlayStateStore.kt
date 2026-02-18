package us.bergnet.oversight.data.store

import us.bergnet.oversight.data.model.*
import us.bergnet.oversight.data.model.enums.HotCorner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    // Device info
    private val _deviceId = MutableStateFlow("")
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    // --- Accessors ---

    fun getOverlayVisibility(): Int =
        _infoValues.value.overlay?.overlayVisibility ?: 0

    fun getClockOverlayVisibility(): Int =
        _infoValues.value.overlay?.clockOverlayVisibility ?: 0

    fun getHotCorner(): HotCorner {
        val name = _infoValues.value.overlay?.hotCorner ?: "top_end"
        return HotCorner.fromRestApiName(name)
    }

    fun getDeviceName(): String =
        _infoValues.value.settings?.deviceName ?: "OverSight Device"

    fun getRemotePort(): Int =
        _infoValues.value.settings?.remotePort ?: 5001

    fun getNotificationDuration(): Int =
        _infoValues.value.notifications?.notificationDuration ?: 8

    fun isDisplayNotifications(): Boolean =
        _infoValues.value.notifications?.displayNotifications ?: true

    fun isDisplayFixedNotifications(): Boolean =
        _infoValues.value.notifications?.displayFixedNotifications ?: true

    fun getFixedNotificationsVisibility(): Int =
        _infoValues.value.notifications?.fixedNotificationsVisibility ?: 0

    fun isPixelShift(): Boolean =
        _infoValues.value.settings?.pixelShift ?: false

    // --- Mutators ---

    fun updateInfoValues(new: InfoValues) {
        _infoValues.value = _infoValues.value.merge(new)
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
        val current = _fixedNotifications.value.toMutableList()
        val index = current.indexOfFirst { it.id == notification.id }
        val merged = if (index >= 0) {
            // Merge: existing values kept for null incoming fields
            current[index].mergeWith(notification)
        } else {
            notification
        }
        val withTime = merged.withReceivedTime()
        if (index >= 0) {
            current[index] = withTime
        } else {
            current.add(withTime)
        }
        // Remove expired
        _fixedNotifications.value = current.filter { !it.isExpired() }
    }

    fun getFixedNotification(id: String): FixedNotification? =
        _fixedNotifications.value.firstOrNull { it.id == id }

    fun removeFixedNotification(id: String) {
        _fixedNotifications.value = _fixedNotifications.value.filter { it.id != id }
    }

    fun removeExpiredFixedNotifications() {
        val current = _fixedNotifications.value
        val filtered = current.filter { !it.isExpired() }
        if (filtered.size != current.size) {
            _fixedNotifications.value = filtered
        }
    }

    fun getActiveFixedNotifications(): List<FixedNotification> =
        _fixedNotifications.value.filter { !it.isExpired() && it.visible && !it.isEmpty() }

    fun enqueueNotification(notification: ReceivedNotification) {
        if (notification.isEmpty()) return

        val current = _currentNotification.value
        if (current == null) {
            _currentNotification.value = notification
        } else if (current.id == notification.id && current.source == notification.source) {
            // Update in place for same id+source
            _currentNotification.value = notification
        } else {
            val queue = _notificationQueue.value.toMutableList()
            queue.add(notification)
            _notificationQueue.value = queue
        }
    }

    fun dismissCurrentNotification() {
        val queue = _notificationQueue.value.toMutableList()
        if (queue.isNotEmpty()) {
            _currentNotification.value = queue.removeFirst()
            _notificationQueue.value = queue
        } else {
            _currentNotification.value = null
        }
    }

    fun setScreenOn(on: Boolean) {
        _isScreenOn.value = on
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun setDeviceId(id: String) {
        _deviceId.value = id
    }

    fun setLayoutList(list: NotificationLayoutList) {
        _layoutList.value = list
    }

    fun getCurrentLayoutName(): String =
        _infoValues.value.notifications?.notificationLayoutName ?: _layoutList.value.selected

    fun getCurrentLayout(): NotificationLayout {
        val name = getCurrentLayoutName()
        return _layoutList.value.list.firstOrNull { it.name == name } ?: NotificationLayout.DEFAULT
    }
}
