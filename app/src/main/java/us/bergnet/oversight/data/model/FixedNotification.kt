package us.bergnet.oversight.data.model

import us.bergnet.oversight.data.model.enums.FixedNotificationShape
import us.bergnet.oversight.data.model.enums.FixedNotificationSize
import us.bergnet.oversight.util.ExpirationParser
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class FixedNotification(
    val id: String = UUID.randomUUID().toString(),
    val visible: Boolean = true,
    val icon: String? = null,
    @JsonNames("message", "title")
    val text: String? = null,
    @JsonNames("textColor")
    val messageColor: String? = null,
    val iconColor: String? = null,
    val borderColor: String? = null,
    val backgroundColor: String? = null,
    val shape: FixedNotificationShape? = null,
    val size: FixedNotificationSize = FixedNotificationSize.NORMAL,
    val receivedTime: Long? = null,
    val expiration: String? = null,
    val expirationEpoch: Long? = null,
    // New fields for collapsible notifications (Phase 7)
    val showDuration: Int? = null,
    val collapseDuration: Int? = null,
    val repeatExpand: Boolean? = null
) {
    fun withReceivedTime(): FixedNotification {
        val now = System.currentTimeMillis()
        val epoch = expirationEpoch ?: expiration?.let {
            ExpirationParser.parse(it, now)
        }
        return copy(receivedTime = receivedTime ?: now, expirationEpoch = epoch)
    }

    fun isExpired(): Boolean {
        val epoch = expirationEpoch ?: return false
        return System.currentTimeMillis() > epoch
    }

    fun isEmpty(): Boolean {
        return icon.isNullOrBlank() && text.isNullOrBlank()
    }

    fun getExpirationInSeconds(): Long? {
        val epoch = expirationEpoch ?: return null
        return (epoch - System.currentTimeMillis()) / 1000
    }

    fun getExpirationText(): String? {
        val epoch = expirationEpoch ?: return null
        return SimpleDateFormat("hh:mm:ss (dd-MMM-yyyy)", Locale.getDefault()).format(Date(epoch))
    }

    fun getReceivedText(): String? {
        val time = receivedTime ?: return null
        return SimpleDateFormat("hh:mm:ss (dd-MMM-yyyy)", Locale.getDefault()).format(Date(time))
    }
}
