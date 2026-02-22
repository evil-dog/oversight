package us.bergnet.oversight.server.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonElement
import us.bergnet.oversight.data.model.ApiResponse
import us.bergnet.oversight.data.model.FixedNotification
import us.bergnet.oversight.data.model.ReceivedNotification
import us.bergnet.oversight.data.store.OverlayStateStore
import us.bergnet.oversight.server.HttpServer
import us.bergnet.oversight.util.IconResolver

fun Routing.notifyRoutes() {
    post("/notify") {
        try {
            val body = call.receiveText()
            val notification = HttpServer.json.decodeFromString<ReceivedNotification>(body)
            if (notification.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse.error("Empty notification"))
                return@post
            }
            // Validate icon fields
            val iconError = listOfNotNull(
                notification.smallIcon?.let { icon ->
                    if (IconResolver.isMdiIcon(icon)) {
                        if (!IconResolver.isValidMdiIcon(icon)) "Invalid MDI icon name for 'smallIcon': $icon" else null
                    } else {
                        "Invalid icon value for 'smallIcon': must start with 'mdi:'"
                    }
                },
                notification.largeIcon?.let { IconResolver.validateIconField("largeIcon", it) },
                notification.appIcon?.let { IconResolver.validateIconField("appIcon", it) },
            ).firstOrNull()
            if (iconError != null) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse.error(iconError))
                return@post
            }
            OverlayStateStore.enqueueNotification(notification)
            call.respond(ApiResponse.success("Notification received"))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ApiResponse.error("Invalid request: ${e.message}"))
        }
    }

    post("/notify_fixed") {
        try {
            val body = call.receiveText()
            val notification = HttpServer.json.decodeFromString<FixedNotification>(body)
            val existing = OverlayStateStore.getFixedNotification(notification.id)
            if (existing == null && notification.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse.error("New fixed notification requires icon or text"))
                return@post
            }
            // Validate icon field
            val iconError = notification.icon?.let { IconResolver.validateIconField("icon", it) }
            if (iconError != null) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse.error(iconError))
                return@post
            }
            OverlayStateStore.upsertFixedNotification(notification)
            call.respond(ApiResponse.success("Fixed notification received"))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ApiResponse.error("Invalid request: ${e.message}"))
        }
    }

    get("/fixed_notifications") {
        val active = OverlayStateStore.getActiveFixedNotifications()
        val json = HttpServer.json.encodeToJsonElement(
            kotlinx.serialization.builtins.ListSerializer(FixedNotification.serializer()),
            active
        )
        call.respond(ApiResponse.success("Fixed notifications", json))
    }
}
