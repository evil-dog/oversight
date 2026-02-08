package us.bergnet.oversight.data.model.enums

import kotlinx.serialization.Serializable

@Serializable
enum class BatteryOptimizationState {
    BLOCKED,
    SKIPPED,
    ENABLED,
    ON_SCREEN,
    ON_SCREEN_ADB
}
