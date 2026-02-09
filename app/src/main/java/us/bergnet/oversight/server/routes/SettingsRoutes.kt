package us.bergnet.oversight.server.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import us.bergnet.oversight.data.model.*
import us.bergnet.oversight.data.store.OverlayStateStore
import us.bergnet.oversight.server.HttpServer

fun Routing.settingsRoutes() {
    get("/info") {
        val info = OverlayStateStore.infoValues.value
        val deviceId = OverlayStateStore.deviceId.value
        val result = HttpServer.json.encodeToJsonElement(
            InfoValues.serializer(),
            info
        )
        // Wrap result in an object that includes deviceId at the top level
        val resultObj = buildJsonObject {
            put("deviceId", deviceId)
            // Spread the InfoValues fields into the result
            result.jsonObject.forEach { (key, value) -> put(key, value) }
        }
        call.respond(ApiResponse.success("Device info", resultObj))
    }

    post("/set/overlay") {
        try {
            val body = call.receiveText()
            val overlay = HttpServer.json.decodeFromString<OverlayValues>(body)
            OverlayStateStore.updateInfoValues(InfoValues(overlay = overlay))
            val result = HttpServer.json.encodeToJsonElement(
                InfoValues.serializer(),
                OverlayStateStore.infoValues.value
            )
            call.respond(ApiResponse.success("Overlay settings updated", result))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ApiResponse.error("Invalid request: ${e.message}"))
        }
    }

    post("/set/notifications") {
        try {
            val body = call.receiveText()
            val notifications = HttpServer.json.decodeFromString<NotificationValues>(body)
            OverlayStateStore.updateInfoValues(InfoValues(notifications = notifications))
            val result = HttpServer.json.encodeToJsonElement(
                InfoValues.serializer(),
                OverlayStateStore.infoValues.value
            )
            call.respond(ApiResponse.success("Notification settings updated", result))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ApiResponse.error("Invalid request: ${e.message}"))
        }
    }

    post("/set/settings") {
        try {
            val body = call.receiveText()
            val settings = HttpServer.json.decodeFromString<SettingsValues>(body)
            OverlayStateStore.updateInfoValues(InfoValues(settings = settings))
            val result = HttpServer.json.encodeToJsonElement(
                InfoValues.serializer(),
                OverlayStateStore.infoValues.value
            )
            call.respond(ApiResponse.success("Settings updated", result))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ApiResponse.error("Invalid request: ${e.message}"))
        }
    }
}
