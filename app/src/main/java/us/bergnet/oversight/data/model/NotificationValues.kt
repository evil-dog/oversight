package us.bergnet.oversight.data.model

import kotlinx.serialization.Serializable

@Serializable
data class NotificationValues(
    val displayNotifications: Boolean? = null,
    val notificationLayoutName: String? = null,
    val notificationDuration: Int? = null,
    val displayFixedNotifications: Boolean? = null,
    val fixedNotificationsVisibility: Int? = null
)
