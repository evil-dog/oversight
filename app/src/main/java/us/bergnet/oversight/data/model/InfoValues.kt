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
                val current = result.settings ?: SettingsValues()
                result = result.copy(settings = current.copy(remotePort = port))
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
}
