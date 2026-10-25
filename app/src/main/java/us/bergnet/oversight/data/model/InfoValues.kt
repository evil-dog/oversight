package us.bergnet.oversight.data.model

import us.bergnet.oversight.data.model.enums.HotCorner
import kotlinx.serialization.Serializable

@Serializable
data class InfoValues(
    val overlay: OverlayValues? = null,
    val settings: SettingsValues? = null,
    val notifications: NotificationValues? = null,
    val status: StatusValues? = null
) {
    fun merge(other: InfoValues): InfoValues {
        var result = this

        // Merge overlay values
        other.overlay?.let { newOverlay ->
            newOverlay.overlayVisibility?.let { vis ->
                if (vis in 0..95) {
                    val current = result.overlay ?: OverlayValues()
                    result = result.copy(overlay = current.copy(overlayVisibility = vis))
                }
            }
            newOverlay.clockOverlayVisibility?.let { vis ->
                if (vis in 0..100) {
                    val current = result.overlay ?: OverlayValues()
                    result = result.copy(overlay = current.copy(clockOverlayVisibility = vis))
                }
            }
            newOverlay.hotCorner?.let { corner ->
                val validated = HotCorner.fromRestApiName(corner)
                val current = result.overlay ?: OverlayValues()
                result = result.copy(overlay = current.copy(hotCorner = validated.restApiName))
            }
        }

        // Merge settings values
        other.settings?.let { newSettings ->
            newSettings.deviceName?.let { name ->
                val current = result.settings ?: SettingsValues()
                result = result.copy(settings = current.copy(deviceName = name.trim()))
            }
            newSettings.remotePort?.let { port ->
                if (port in SettingsValues.VALID_PORTS) {
                    val current = result.settings ?: SettingsValues()
                    result = result.copy(settings = current.copy(remotePort = port))
                }
            }
            newSettings.displayDebug?.let { debug ->
                val current = result.settings ?: SettingsValues()
                result = result.copy(settings = current.copy(displayDebug = debug))
            }
            newSettings.pixelShift?.let { shift ->
                val current = result.settings ?: SettingsValues()
                result = result.copy(settings = current.copy(pixelShift = shift))
            }
        }

        // Merge notification values
        other.notifications?.let { newNotifs ->
            newNotifs.notificationLayoutName?.let { name ->
                val current = result.notifications ?: NotificationValues()
                result = result.copy(notifications = current.copy(notificationLayoutName = name.trim()))
            }
            newNotifs.displayNotifications?.let { display ->
                val current = result.notifications ?: NotificationValues()
                result = result.copy(notifications = current.copy(displayNotifications = display))
            }
            newNotifs.notificationDuration?.let { duration ->
                val current = result.notifications ?: NotificationValues()
                result = result.copy(notifications = current.copy(notificationDuration = duration))
            }
            newNotifs.displayFixedNotifications?.let { display ->
                val current = result.notifications ?: NotificationValues()
                result = result.copy(notifications = current.copy(displayFixedNotifications = display))
            }
            newNotifs.fixedNotificationsVisibility?.let { vis ->
                val current = result.notifications ?: NotificationValues()
                result = result.copy(notifications = current.copy(fixedNotificationsVisibility = vis))
            }
        }

        return result
    }

    /** These values with defaults applied — the single place defaults are defined. */
    fun effective() = EffectiveSettings(
        overlayVisibility = overlay?.overlayVisibility ?: 0,
        clockVisibility = overlay?.clockOverlayVisibility ?: 0,
        hotCorner = overlay?.hotCorner?.let { HotCorner.fromRestApiName(it) } ?: HotCorner.TOP_END,
        deviceName = settings?.deviceName ?: "OverSight Device",
        // Persisted values predate port validation, so re-check here rather than trusting storage
        remotePort = settings?.remotePort?.takeIf { it in SettingsValues.VALID_PORTS } ?: 5001,
        pixelShift = settings?.pixelShift ?: false,
        displayNotifications = notifications?.displayNotifications ?: true,
        notificationDuration = notifications?.notificationDuration ?: 8,
        notificationLayoutName = notifications?.notificationLayoutName,
        displayFixedNotifications = notifications?.displayFixedNotifications ?: true,
        fixedNotificationsVisibility = notifications?.fixedNotificationsVisibility ?: 100,
    )
}

data class EffectiveSettings(
    val overlayVisibility: Int,
    val clockVisibility: Int,
    val hotCorner: HotCorner,
    val deviceName: String,
    val remotePort: Int,
    val pixelShift: Boolean,
    val displayNotifications: Boolean,
    val notificationDuration: Int,
    /** Null means use the layout list's selected layout. */
    val notificationLayoutName: String?,
    val displayFixedNotifications: Boolean,
    val fixedNotificationsVisibility: Int,
)
