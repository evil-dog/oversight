package us.bergnet.oversight.server.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import us.bergnet.oversight.data.model.*
import us.bergnet.oversight.data.store.OverlayStateStore
import us.bergnet.oversight.server.HttpServer

// Paths that have their own routes; "/{filter}" must not capture them.
private val reservedPaths = setOf(
    "info", "set", "notify", "notify_fixed", "fixed_notifications",
    "overlay_customization", "screen_on", "restart_service"
)

/** Upserts the layout in the request body, makes it active, and responds with the layout list. */
private suspend fun RoutingContext.setLayout() {
    try {
        val body = call.receiveText()
        val layout = HttpServer.json.decodeFromString<NotificationLayout>(body)
        OverlayStateStore.upsertLayout(layout, select = true)
        val json = HttpServer.json.encodeToJsonElement(
            NotificationLayoutList.serializer(),
            OverlayStateStore.layoutList.value
        )
        call.respond(ApiResponse.success("Layout set", json))
    } catch (e: Exception) {
        call.respondError(e)
    }
}

fun Routing.layoutRoutes() {
    // TvOverlay-compatible layout endpoints mounted at the root: "/" and "/{filter}".

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
        if (filter in reservedPaths) return@get
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

    post("/") { setLayout() }

    post("/{filter}") {
        if (call.parameters["filter"] in reservedPaths) return@post
        setLayout()
    }
}
