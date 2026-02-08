package us.bergnet.oversight.data.model.enums

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FixedNotificationShape(val restApiName: String) {
    @SerialName("circle")
    CIRCLE("circle"),

    @SerialName("rounded")
    ROUNDED("rounded"),

    @SerialName("rectangular")
    RECTANGULAR("rectangular");

    companion object {
        fun fromRestApiName(name: String): FixedNotificationShape =
            entries.firstOrNull { it.restApiName == name } ?: ROUNDED
    }
}
