package us.bergnet.oversight.data.model

import us.bergnet.oversight.data.model.enums.FontWeightEnum
import kotlinx.serialization.Serializable

@Serializable
data class NotificationTextFormat(
    val fontSize: Int? = null,
    val lineHeight: Int? = null,
    val fontWeight: FontWeightEnum? = null,
    val maxLines: Int = 2,
    val color: String = "#FFFFFF"
) {
    companion object {
        val SOURCE_DEFAULT = NotificationTextFormat(
            fontSize = 9,
            lineHeight = 10,
            fontWeight = null,
            maxLines = 1,
            color = "#FFFFFF"
        )

        val TITLE_DEFAULT = NotificationTextFormat(
            fontSize = 12,
            lineHeight = 13,
            fontWeight = FontWeightEnum.BOLD,
            maxLines = 2,
            color = "#FFFFFF"
        )

        val MESSAGE_DEFAULT = NotificationTextFormat(
            fontSize = 11,
            lineHeight = 13,
            fontWeight = null,
            maxLines = 4,
            color = "#FFFFFF"
        )

        val TITLE_MINIMALIST = NotificationTextFormat(
            fontSize = 11,
            lineHeight = 12,
            fontWeight = FontWeightEnum.BOLD,
            maxLines = 1,
            color = "#FFFFFF"
        )

        val MESSAGE_MINIMALIST = NotificationTextFormat(
            fontSize = 10,
            lineHeight = 12,
            fontWeight = FontWeightEnum.NORMAL,
            maxLines = 2,
            color = "#FFFFFF"
        )
    }
}
