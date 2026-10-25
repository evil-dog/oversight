package us.bergnet.oversight.data.model

import kotlinx.serialization.Serializable

@Serializable
data class NotificationValues(
    val displayNotifications: Boolean? = null,
    val notificationLayoutName: String? = null,
    val notificationDuration: Int? = null,
    val displayFixedNotifications: Boolean? = null,
    val fixedNotificationsVisibility: Int? = null
) {
    /** Returns an error message for the first out-of-range field, or null if all set fields are valid. */
    fun validate(): String? = when {
        notificationDuration != null && notificationDuration !in VALID_DURATION ->
            "notificationDuration must be between ${VALID_DURATION.first} and ${VALID_DURATION.last}"
        fixedNotificationsVisibility != null && fixedNotificationsVisibility !in VALID_FIXED_VISIBILITY ->
            "fixedNotificationsVisibility must be between ${VALID_FIXED_VISIBILITY.first} and ${VALID_FIXED_VISIBILITY.last}"
        else -> null
    }

    companion object {
        /** Seconds; matches the setup screen's duration slider. */
        val VALID_DURATION = 3..30
        val VALID_FIXED_VISIBILITY = 0..100
    }
}
