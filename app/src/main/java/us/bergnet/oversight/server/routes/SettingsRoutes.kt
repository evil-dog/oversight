package us.bergnet.oversight.server.routes

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import us.bergnet.oversight.data.model.*
import us.bergnet.oversight.data.model.enums.BatteryOptimizationState
import us.bergnet.oversight.data.model.enums.PermissionState
import us.bergnet.oversight.data.store.OverlayStateStore
import us.bergnet.oversight.server.HttpServer

/** Live device status; computed per request since permissions can change outside the app. */
private fun Context.currentStatus(): StatusValues {
    val pm = getSystemService(PowerManager::class.java)
    return StatusValues(
        version = packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt(),
        permissionState = if (Settings.canDrawOverlays(this)) PermissionState.GRANTED
            else PermissionState.DENIED_SETTING_AVAILABLE,
        batteryOptimizationState = if (pm.isIgnoringBatteryOptimizations(packageName)) BatteryOptimizationState.ENABLED
            else BatteryOptimizationState.SKIPPED,
        isScreenOn = OverlayStateStore.isScreenOn.value,
    )
}

/** Current settings with defaults applied, plus live status. */
private fun Context.currentInfo(): JsonElement = HttpServer.json.encodeToJsonElement(
    InfoValues.serializer(),
    OverlayStateStore.infoValues.value.withDefaults(status = currentStatus())
)

fun Routing.settingsRoutes(context: Context) {
    get("/info") {
        val deviceId = OverlayStateStore.deviceId.value
        // Wrap result in an object that includes deviceId at the top level
        val resultObj = buildJsonObject {
            put("deviceId", deviceId)
            // Spread the InfoValues fields into the result
            context.currentInfo().jsonObject.forEach { (key, value) -> put(key, value) }
        }
        call.respond(ApiResponse.success("Device info", resultObj))
    }

    post("/set/overlay") {
        try {
            val body = call.receiveText()
            val overlay = HttpServer.json.decodeFromString<OverlayValues>(body)
            overlay.validate()?.let { error ->
                call.respond(HttpStatusCode.BadRequest, ApiResponse.error(error))
                return@post
            }
            OverlayStateStore.updateInfoValues(InfoValues(overlay = overlay))
            call.respond(ApiResponse.success("Overlay settings updated", context.currentInfo()))
        } catch (e: Exception) {
            call.respondError(e)
        }
    }

    post("/set/notifications") {
        try {
            val body = call.receiveText()
            val notifications = HttpServer.json.decodeFromString<NotificationValues>(body)
            notifications.validate()?.let { error ->
                call.respond(HttpStatusCode.BadRequest, ApiResponse.error(error))
                return@post
            }
            OverlayStateStore.updateInfoValues(InfoValues(notifications = notifications))
            call.respond(ApiResponse.success("Notification settings updated", context.currentInfo()))
        } catch (e: Exception) {
            call.respondError(e)
        }
    }

    post("/set/settings") {
        try {
            val body = call.receiveText()
            val settings = HttpServer.json.decodeFromString<SettingsValues>(body)
            settings.remotePort?.let { port ->
                if (port !in SettingsValues.VALID_PORTS) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse.error("remotePort must be between ${SettingsValues.VALID_PORTS.first} and ${SettingsValues.VALID_PORTS.last}")
                    )
                    return@post
                }
            }
            OverlayStateStore.updateInfoValues(InfoValues(settings = settings))
            call.respond(ApiResponse.success("Settings updated", context.currentInfo()))
        } catch (e: Exception) {
            call.respondError(e)
        }
    }
}
