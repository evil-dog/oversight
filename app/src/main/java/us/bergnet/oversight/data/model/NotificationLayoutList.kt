package us.bergnet.oversight.data.model

import kotlinx.serialization.Serializable

@Serializable
data class NotificationLayoutList(
    val list: List<NotificationLayout> = NotificationLayout.ALL_DEFAULTS,
    val selected: String = "Default"
)
