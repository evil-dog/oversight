package us.bergnet.oversight.data.model.enums

import androidx.compose.ui.Alignment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class HotCorner(val restApiName: String, val alignment: Alignment) {
    @SerialName("top_start")
    TOP_START("top_start", Alignment.TopStart),

    @SerialName("top_end")
    TOP_END("top_end", Alignment.TopEnd),

    @SerialName("bottom_start")
    BOTTOM_START("bottom_start", Alignment.BottomStart),

    @SerialName("bottom_end")
    BOTTOM_END("bottom_end", Alignment.BottomEnd);

    fun isStart(): Boolean = this == TOP_START || this == BOTTOM_START

    companion object {
        fun fromRestApiName(name: String): HotCorner =
            entries.firstOrNull { it.restApiName == name } ?: TOP_END
    }
}
