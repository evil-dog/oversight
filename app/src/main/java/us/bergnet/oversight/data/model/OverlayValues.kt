package us.bergnet.oversight.data.model

import kotlinx.serialization.Serializable

@Serializable
data class OverlayValues(
    val overlayVisibility: Int? = null,
    val clockOverlayVisibility: Int? = null,
    val hotCorner: String? = null
)
