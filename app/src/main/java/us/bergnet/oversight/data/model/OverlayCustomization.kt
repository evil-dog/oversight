package us.bergnet.oversight.data.model

import us.bergnet.oversight.data.model.enums.FontWeightEnum
import kotlinx.serialization.Serializable

@Serializable
data class OverlayCustomization(
    val fontSize: Int? = null,
    val fontWeight: FontWeightEnum? = null,
    val color: String? = null,
    val displayShadow: Boolean? = null,
    val backgroundColor: String? = null
) {
    fun toNonNullable(): NonNullableOverlayCustomization = NonNullableOverlayCustomization(
        fontSize = fontSize ?: DEFAULT.fontSize!!,
        fontWeight = fontWeight ?: DEFAULT.fontWeight!!,
        color = color ?: DEFAULT.color!!,
        displayShadow = displayShadow ?: DEFAULT.displayShadow!!,
        backgroundColor = backgroundColor ?: DEFAULT.backgroundColor!!
    )

    companion object {
        val DEFAULT = OverlayCustomization(
            fontSize = 12,
            fontWeight = FontWeightEnum.NORMAL,
            color = "#FFFFFF",
            displayShadow = true,
            backgroundColor = "#000000"
        )
    }
}

@Serializable
data class NonNullableOverlayCustomization(
    val fontSize: Int,
    val fontWeight: FontWeightEnum,
    val color: String,
    val displayShadow: Boolean,
    val backgroundColor: String
)
