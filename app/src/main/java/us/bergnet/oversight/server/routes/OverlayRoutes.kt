package us.bergnet.oversight.server.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import us.bergnet.oversight.data.model.ApiResponse
import us.bergnet.oversight.data.model.OverlayCustomization
import us.bergnet.oversight.data.store.OverlayStateStore
import us.bergnet.oversight.server.HttpServer

fun Routing.overlayRoutes() {
    get("/overlay_customization") {
        val customization = OverlayStateStore.overlayCustomization.value
        val json = HttpServer.json.encodeToJsonElement(
            OverlayCustomization.serializer(),
            customization
        )
        call.respond(ApiResponse.success("Overlay customization", json))
    }

    post("/overlay_customization") {
        try {
            val body = call.receiveText()
            val customization = HttpServer.json.decodeFromString<OverlayCustomization>(body)
            OverlayStateStore.setOverlayCustomization(customization)
            val json = HttpServer.json.encodeToJsonElement(
                OverlayCustomization.serializer(),
                OverlayStateStore.overlayCustomization.value
            )
            call.respond(ApiResponse.success("Overlay customization updated", json))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ApiResponse.error("Invalid request: ${e.message}"))
        }
    }
}
