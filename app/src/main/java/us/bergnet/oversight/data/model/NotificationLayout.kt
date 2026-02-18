package us.bergnet.oversight.data.model

import kotlinx.serialization.Serializable

@Serializable
data class NotificationLayout(
    var name: String = "Default",
    var imageDisplay: Boolean = true,
    var imageSmall: Boolean = false,
    var titleDisplay: Boolean = true,
    val titleFormat: NotificationTextFormat = NotificationTextFormat.TITLE_DEFAULT,
    var sourceDisplay: Boolean = true,
    val sourceFormat: NotificationTextFormat = NotificationTextFormat.SOURCE_DEFAULT,
    var messageDisplay: Boolean = true,
    val messageFormat: NotificationTextFormat = NotificationTextFormat.MESSAGE_DEFAULT,
    var iconDisplay: Boolean = true,
    val iconSize: Float = 40f,
    var iconSecondaryDisplay: Boolean = true,
    val iconSecondarySize: Float = 20f,
    val maxWidth: Float = 260f,
    var backgroundColor: String = "#66000000",
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
