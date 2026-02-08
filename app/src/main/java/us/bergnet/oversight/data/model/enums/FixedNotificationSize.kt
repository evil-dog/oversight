package us.bergnet.oversight.data.model.enums

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FixedNotificationSize(
    val restApiName: String,
    val height: Float,
    val padding: Float,
    val imageSize: Float,
    val fontSize: Int
) {
    @SerialName("small")
    SMALL("small", 12f, 1f, 10f, 7),

    @SerialName("normal")
    NORMAL("normal", 18f, 2f, 14f, 10),

    @SerialName("big")
    BIG("big", 36f, 4f, 28f, 20);

    companion object {
        fun fromRestApiName(name: String): FixedNotificationSize =
            entries.firstOrNull { it.restApiName == name } ?: NORMAL
    }
}
