package us.bergnet.oversight.data.model.enums

import androidx.compose.ui.text.font.FontWeight
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FontWeightEnum(val displayString: String, private val weight: Int) {
    @SerialName("Thin")
    THIN("Thin", 100),

    @SerialName("ExtraLight")
    EXTRA_LIGHT("ExtraLight", 200),

    @SerialName("Light")
    LIGHT("Light", 300),

    @SerialName("Normal")
    NORMAL("Normal", 400),

    @SerialName("Medium")
    MEDIUM("Medium", 500),

    @SerialName("SemiBold")
    SEMI_BOLD("SemiBold", 600),

    @SerialName("Bold")
    BOLD("Bold", 700),

    @SerialName("ExtraBold")
    EXTRA_BOLD("ExtraBold", 800),

    @SerialName("Black")
    BLACK("Black", 900);

    fun toFontWeight(): FontWeight = FontWeight(weight)
}
