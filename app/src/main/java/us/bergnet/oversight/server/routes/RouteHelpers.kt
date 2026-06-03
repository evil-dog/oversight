package us.bergnet.oversight.server.routes

import android.util.Log
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.serialization.SerializationException
import us.bergnet.oversight.data.model.ApiResponse

private const val TAG = "Routes"

/**
 * Standard error response for a route handler:
 *   - SerializationException / IllegalArgumentException -> 400 with the message
 *   - anything else -> 500, logged with stack trace (the message is not echoed to the client)
 */
suspend fun ApplicationCall.respondError(e: Throwable) {
    when (e) {
        is SerializationException,
        is IllegalArgumentException -> respond(
            HttpStatusCode.BadRequest,
            ApiResponse.error("Invalid request: ${e.message}")
        )
        else -> {
            Log.e(TAG, "Unhandled error in route handler", e)
            respond(HttpStatusCode.InternalServerError, ApiResponse.error("Internal server error"))
        }
    }
}
