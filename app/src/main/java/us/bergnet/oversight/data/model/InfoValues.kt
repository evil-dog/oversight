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
                if (vis in OverlayValues.VALID_OVERLAY_VISIBILITY) {
                    val current = result.overlay ?: OverlayValues()
                    result = result.copy(overlay = current.copy(overlayVisibility = vis))
                }
            }
            newOverlay.clockOverlayVisibility?.let { vis ->
                if (vis in OverlayValues.VALID_CLOCK_VISIBILITY) {
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
                if (duration in NotificationValues.VALID_DURATION) {
                    val current = result.notifications ?: NotificationValues()
                    result = result.copy(notifications = current.copy(notificationDuration = duration))
                }
            }
            newNotifs.displayFixedNotifications?.let { display ->
                val current = result.notifications ?: NotificationValues()
                result = result.copy(notifications = current.copy(displayFixedNotifications = display))
            }
            newNotifs.fixedNotificationsVisibility?.let { vis ->
                if (vis in NotificationValues.VALID_FIXED_VISIBILITY) {
                    val current = result.notifications ?: NotificationValues()
                    result = result.copy(notifications = current.copy(fixedNotificationsVisibility = vis))
                }
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
        displayDebug = settings?.displayDebug ?: false,
    )

    /**
     * These values with defaults filled in, for API responses. Stored values stay nullable so partial
     * updates merge; [NotificationValues.notificationLayoutName] stays null when unset, meaning the
     * layout list's selected layout is used.
     */
    fun withDefaults(status: StatusValues? = this.status): InfoValues {
        val e = effective()
        return InfoValues(
            overlay = OverlayValues(
                overlayVisibility = e.overlayVisibility,
                clockOverlayVisibility = e.clockVisibility,
                hotCorner = e.hotCorner.restApiName,
            ),
            settings = SettingsValues(
                deviceName = e.deviceName,
                remotePort = e.remotePort,
                displayDebug = e.displayDebug,
                pixelShift = e.pixelShift,
            ),
            notifications = NotificationValues(
                displayNotifications = e.displayNotifications,
                notificationLayoutName = e.notificationLayoutName,
                notificationDuration = e.notificationDuration,
                displayFixedNotifications = e.displayFixedNotifications,
                fixedNotificationsVisibility = e.fixedNotificationsVisibility,
            ),
            status = status,
        )
    }
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
    val displayDebug: Boolean,
)
