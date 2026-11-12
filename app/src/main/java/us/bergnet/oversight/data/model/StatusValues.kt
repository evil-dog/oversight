package us.bergnet.oversight.data.model

import us.bergnet.oversight.data.model.enums.BatteryOptimizationState
import us.bergnet.oversight.data.model.enums.PermissionState
import kotlinx.serialization.Serializable

@Serializable
data class StatusValues(
    val id: Int? = null,
    val version: Int? = null,
    val permissionState: PermissionState? = null,
    val batteryOptimizationState: BatteryOptimizationState? = null,
    val isScreenOn: Boolean? = null,
    /** Whether a Home Assistant webhook is set to announce address changes to. */
    val webhookConfigured: Boolean? = null
)
