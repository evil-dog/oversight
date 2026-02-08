package us.bergnet.oversight.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SettingsValues(
    val deviceName: String? = null,
    val remotePort: Int? = null,
    val displayDebug: Boolean? = null,
    val pixelShift: Boolean? = null
)
