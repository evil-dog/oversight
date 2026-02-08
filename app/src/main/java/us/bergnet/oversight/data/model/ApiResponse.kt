package us.bergnet.oversight.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiResponse(
    val success: Boolean,
    val message: String,
    val result: JsonElement? = null
) {
    companion object {
        fun success(message: String = "OK", result: JsonElement? = null) =
            ApiResponse(success = true, message = message, result = result)

        fun error(message: String) =
            ApiResponse(success = false, message = message)
    }
}
