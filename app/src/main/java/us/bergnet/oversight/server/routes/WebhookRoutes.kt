package us.bergnet.oversight.server.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import us.bergnet.oversight.data.model.ApiResponse
import us.bergnet.oversight.data.store.OverlayStateStore
import us.bergnet.oversight.server.HttpServer
import java.net.URI

@Serializable
data class WebhookRequest(val url: String? = null)

/**
 * Validate a webhook URL: absolute http(s) with a host. Returns the trimmed URL, or null to clear.
 * Throws IllegalArgumentException (a 400 via respondError) for anything else.
 */
internal fun validateWebhookUrl(url: String?): String? {
    val trimmed = url?.trim()
    if (trimmed.isNullOrEmpty()) return null
    val uri = try {
        URI(trimmed)
    } catch (e: Exception) {
        throw IllegalArgumentException("url is not a valid URL")
    }
    require(uri.scheme == "http" || uri.scheme == "https") { "url must start with http:// or https://" }
    require(!uri.host.isNullOrEmpty()) { "url must include a host" }
    return trimmed
}

/** Lets Home Assistant tell the device where to announce its address (see AddressAnnouncer). */
fun Routing.webhookRoutes() {
    post("/set/webhook") {
        try {
            val request = HttpServer.json.decodeFromString<WebhookRequest>(call.receiveText())
            val url = validateWebhookUrl(request.url)
            OverlayStateStore.setWebhookUrl(url)
            call.respond(
                HttpStatusCode.OK,
                ApiResponse.success(if (url != null) "Webhook set" else "Webhook cleared")
            )
        } catch (e: Exception) {
            call.respondError(e)
        }
    }
}
