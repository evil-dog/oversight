package us.bergnet.oversight.data.model

import kotlinx.serialization.Serializable
import us.bergnet.oversight.data.model.enums.HotCorner

@Serializable
data class OverlayValues(
    val overlayVisibility: Int? = null,
    val clockOverlayVisibility: Int? = null,
    val hotCorner: String? = null
) {
    /** Returns an error message for the first out-of-range field, or null if all set fields are valid. */
    fun validate(): String? = when {
        overlayVisibility != null && overlayVisibility !in VALID_OVERLAY_VISIBILITY ->
            "overlayVisibility must be between ${VALID_OVERLAY_VISIBILITY.first} and ${VALID_OVERLAY_VISIBILITY.last}"
        clockOverlayVisibility != null && clockOverlayVisibility !in VALID_CLOCK_VISIBILITY ->
            "clockOverlayVisibility must be between ${VALID_CLOCK_VISIBILITY.first} and ${VALID_CLOCK_VISIBILITY.last}"
        hotCorner != null && HotCorner.entries.none { it.restApiName == hotCorner } ->
            "hotCorner must be one of ${HotCorner.entries.joinToString { it.restApiName }}"
        else -> null
    }

    companion object {
        val VALID_OVERLAY_VISIBILITY = 0..95
        val VALID_CLOCK_VISIBILITY = 0..100
    }
}
