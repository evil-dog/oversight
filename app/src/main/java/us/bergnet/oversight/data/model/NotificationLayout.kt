package us.bergnet.oversight.data.model

import kotlinx.serialization.Serializable

@Serializable
data class NotificationLayout(
    val name: String = "Default",
    val imageDisplay: Boolean = true,
    val imageSmall: Boolean = false,
    val titleDisplay: Boolean = true,
    val titleFormat: NotificationTextFormat = NotificationTextFormat.TITLE_DEFAULT,
    val sourceDisplay: Boolean = true,
    val sourceFormat: NotificationTextFormat = NotificationTextFormat.SOURCE_DEFAULT,
    val messageDisplay: Boolean = true,
    val messageFormat: NotificationTextFormat = NotificationTextFormat.MESSAGE_DEFAULT,
    val iconDisplay: Boolean = true,
    val iconSize: Float = 40f,
    val iconSecondaryDisplay: Boolean = true,
    val iconSecondarySize: Float = 20f,
    val maxWidth: Float = 260f,
    val backgroundColor: String = "#66000000",
    val progressBarColor: String = "#2196F3"
) {
    companion object {
        val DEFAULT = NotificationLayout()

        val MINIMALIST = NotificationLayout(
            name = "Minimalist",
            iconDisplay = false,
            sourceDisplay = false,
            titleFormat = NotificationTextFormat.TITLE_MINIMALIST,
            messageFormat = NotificationTextFormat.MESSAGE_MINIMALIST,
            maxWidth = 220f
        )

        val ICON_ONLY = NotificationLayout(
            name = "Only Icon",
            imageDisplay = false,
            titleDisplay = false,
            sourceDisplay = false,
            messageDisplay = false,
            iconDisplay = true,
            iconSecondaryDisplay = true
        )

        val ALL_DEFAULTS = listOf(DEFAULT, MINIMALIST, ICON_ONLY)
    }
}
