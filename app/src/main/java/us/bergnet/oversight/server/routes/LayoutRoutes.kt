package us.bergnet.oversight.server.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import us.bergnet.oversight.data.model.*
import us.bergnet.oversight.data.store.OverlayStateStore
import us.bergnet.oversight.server.HttpServer

fun Routing.layoutRoutes() {
    // These are mounted under root, so they need to be careful about path conflicts.
    // The original app uses "/" and "/{filter?}" for layout operations.
    // We prefix with /layouts to avoid conflicts.

    get("/") {
        val layoutList = OverlayStateStore.layoutList.value
        val json = HttpServer.json.encodeToJsonElement(
            NotificationLayoutList.serializer(),
            layoutList
        )
        call.respond(ApiResponse.success("Notification layouts", json))
    }

    get("/{filter}") {
        val filter = call.parameters["filter"] ?: ""
        // Avoid capturing other routes
        if (filter in listOf("notify", "notify_fixed", "fixed_notifications",
                "overlay_customization", "screen_on", "restart_service", "set")) {
            return@get
        }
        val layoutList = OverlayStateStore.layoutList.value
        val filtered = if (filter.isNotEmpty()) {
            layoutList.copy(
                list = layoutList.list.filter {
                    it.name.contains(filter, ignoreCase = true)
                }
            )
        } else {
            layoutList
        }
        val json = HttpServer.json.encodeToJsonElement(
            NotificationLayoutList.serializer(),
            filtered
        )
        call.respond(ApiResponse.success("Filtered layouts", json))
    }

    post("/") {
        try {
            val body = call.receiveText()
            val layout = HttpServer.json.decodeFromString<NotificationLayout>(body)
            val current = OverlayStateStore.layoutList.value
            val newList = current.list.toMutableList()
            val existing = newList.indexOfFirst { it.name == layout.name }
            if (existing >= 0) {
                newList[existing] = layout
            } else {
                newList.add(layout)
            }
            OverlayStateStore.setLayoutList(
                current.copy(list = newList, selected = layout.name)
            )
            OverlayStateStore.updateInfoValues(
                InfoValues(notifications = NotificationValues(notificationLayoutName = layout.name))
            )
            val json = HttpServer.json.encodeToJsonElement(
                NotificationLayoutList.serializer(),
                OverlayStateStore.layoutList.value
            )
            call.respond(ApiResponse.success("Layout set", json))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ApiResponse.error("Invalid request: ${e.message}"))
        }
    }

    post("/{filter}") {
        val filter = call.parameters["filter"] ?: ""
        if (filter in listOf("notify", "notify_fixed", "fixed_notifications",
                "overlay_customization", "screen_on", "restart_service", "set")) {
            return@post
        }
        try {
            val body = call.receiveText()
            val layout = HttpServer.json.decodeFromString<NotificationLayout>(body)
            val current = OverlayStateStore.layoutList.value
            val newList = current.list.toMutableList()
            val existing = newList.indexOfFirst { it.name == layout.name }
            if (existing >= 0) {
                newList[existing] = layout
            } else {
                newList.add(layout)
            }
            OverlayStateStore.setLayoutList(
                current.copy(list = newList, selected = layout.name)
            )
            val json = HttpServer.json.encodeToJsonElement(
                NotificationLayoutList.serializer(),
                OverlayStateStore.layoutList.value
            )
            call.respond(ApiResponse.success("Layout set", json))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ApiResponse.error("Invalid request: ${e.message}"))
        }
    }
}
