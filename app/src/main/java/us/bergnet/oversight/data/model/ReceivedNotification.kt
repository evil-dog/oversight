package us.bergnet.oversight.data.model

import us.bergnet.oversight.data.model.enums.HotCorner
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ReceivedNotification(
    val id: String = UUID.randomUUID().toString(),
    val groupId: String? = null,
    val title: String? = null,
    val message: String? = null,
    val source: String? = null,
    val image: String? = null,
    val video: String? = null,
    val smallIcon: String? = null,
    val largeIcon: String? = null,
    val appIcon: String? = null,
    val corner: HotCorner? = null,
    val deviceSourceName: String = "Unknown",
    val smallIconColor: String? = null,
    val whenTimeStamp: Long = 0L,
    val duration: Int? = null,
    val packageName: String? = null,
    val category: String? = null,
    val layout: NotificationLayout? = null
) {
    fun getDisplayTitle(): String = title ?: message ?: ""

    fun getDisplayMessage(): String? {
        if (title == null && source == null) return null
        return message
    }

    fun getDisplayIcon(): String? {
        return largeIcon ?: appIcon ?: smallIcon
    }

    fun isEmpty(): Boolean {
        return getDisplayTitle().isBlank()
                && getDisplayIcon().isNullOrBlank()
                && getDisplayMessage().isNullOrBlank()
                && image.isNullOrEmpty()
    }
}
