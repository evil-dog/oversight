package us.bergnet.oversight.server

import android.app.Application
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import us.bergnet.oversight.data.model.InfoValues
import us.bergnet.oversight.data.model.NotificationLayoutList
import us.bergnet.oversight.data.store.OverlayStateStore

/** Exercises the REST routes in-process; Robolectric supplies the Context used for /info status. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ApiRoutesTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun reset() {
        OverlayStateStore.setInfoValues(InfoValues())
        OverlayStateStore.setFixedNotifications(emptyList())
        OverlayStateStore.setLayoutList(NotificationLayoutList())
        OverlayStateStore.clearNotifications()
        OverlayStateStore.setDeviceId("test-device")
        OverlayStateStore.setScreenOn(true)
    }

    private fun api(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { apiModule(context) }
        block()
    }

    private suspend fun HttpResponse.json(): JsonObject =
        HttpServer.json.parseToJsonElement(bodyAsText()).jsonObject

    private val JsonObject.result get() = getValue("result").jsonObject

    // --- Fixed notification delete ---

    @Test
    fun deleteRemovesFixedNotificationThen404s() = api {
        val created = client.post("/notify_fixed") { setBody("""{"id":"badge","text":"hi"}""") }
        assertEquals(HttpStatusCode.OK, created.status)
        assertEquals(1, OverlayStateStore.getActiveFixedNotifications().size)

        val deleted = client.delete("/notify_fixed/badge")
        assertEquals(HttpStatusCode.OK, deleted.status)
        assertEquals(JsonNull, deleted.json()["result"])
        assertNull(OverlayStateStore.getFixedNotification("badge"))

        val missing = client.delete("/notify_fixed/badge")
        assertEquals(HttpStatusCode.NotFound, missing.status)
        val body = missing.json()
        assertFalse(body.getValue("success").jsonPrimitive.boolean)
        assertEquals("No fixed notification with id 'badge'", body.getValue("message").jsonPrimitive.content)
    }

    @Test
    fun deleteDecodesUrlEncodedIds() = api {
        client.post("/notify_fixed") { setBody("""{"id":"front door","text":"hi"}""") }
        assertEquals(HttpStatusCode.OK, client.delete("/notify_fixed/front%20door").status)
        assertNull(OverlayStateStore.getFixedNotification("front door"))
    }

    // --- /info ---

    @Test
    fun infoReportsDefaultsAndStatus() = api {
        ShadowSettings.setCanDrawOverlays(true)
        shadowOf(context.getSystemService(PowerManager::class.java))
            .setIgnoringBatteryOptimizations(context.packageName, true)
        OverlayStateStore.setScreenOn(false)

        val response = client.get("/info")
        assertEquals(HttpStatusCode.OK, response.status)
        val result = response.json().result
        assertEquals("test-device", result.getValue("deviceId").jsonPrimitive.content)

        val settings = result.getValue("settings").jsonObject
        assertEquals("OverSight Device", settings.getValue("deviceName").jsonPrimitive.content)
        assertEquals(5001, settings.getValue("remotePort").jsonPrimitive.int)
        assertEquals("top_end", result.getValue("overlay").jsonObject.getValue("hotCorner").jsonPrimitive.content)
        val notifications = result.getValue("notifications").jsonObject
        assertEquals(8, notifications.getValue("notificationDuration").jsonPrimitive.int)
        assertEquals(JsonNull, notifications["notificationLayoutName"])

        val status = result.getValue("status").jsonObject
        assertEquals(1, status.getValue("version").jsonPrimitive.int)
        assertFalse(status.getValue("isScreenOn").jsonPrimitive.boolean)
        assertEquals("GRANTED", status.getValue("permissionState").jsonPrimitive.content)
        assertEquals("ENABLED", status.getValue("batteryOptimizationState").jsonPrimitive.content)
    }

    @Test
    fun infoReportsMissingPermissionAndExemption() = api {
        ShadowSettings.setCanDrawOverlays(false)
        val status = client.get("/info").json().result.getValue("status").jsonObject
        assertEquals("DENIED_SETTING_AVAILABLE", status.getValue("permissionState").jsonPrimitive.content)
        assertEquals("SKIPPED", status.getValue("batteryOptimizationState").jsonPrimitive.content)
    }

    @Test
    fun layoutCatchAllDoesNotShadowInfo() = api {
        // /{filter} would otherwise answer GET /info with the layout list
        assertTrue(client.get("/info").json().result.containsKey("deviceId"))
    }

    // --- /set/* ---

    @Test
    fun setNotificationsReturnsFullInfo() = api {
        val response = client.post("/set/notifications") { setBody("""{"notificationDuration":12}""") }
        assertEquals(HttpStatusCode.OK, response.status)
        val result = response.json().result
        assertEquals(12, result.getValue("notifications").jsonObject.getValue("notificationDuration").jsonPrimitive.int)
        assertEquals(100, result.getValue("notifications").jsonObject.getValue("fixedNotificationsVisibility").jsonPrimitive.int)
        assertTrue(result.getValue("status").jsonObject.containsKey("isScreenOn"))
        assertEquals("OverSight Device", result.getValue("settings").jsonObject.getValue("deviceName").jsonPrimitive.content)
    }

    @Test
    fun outOfRangeSettingsReturn400AndChangeNothing() = api {
        listOf(
            "/set/notifications" to """{"notificationDuration":31}""",
            "/set/notifications" to """{"fixedNotificationsVisibility":101}""",
            "/set/overlay" to """{"overlayVisibility":96}""",
            "/set/overlay" to """{"clockOverlayVisibility":-1}""",
            "/set/overlay" to """{"hotCorner":"middle"}""",
            "/set/settings" to """{"remotePort":80}""",
        ).forEach { (path, body) ->
            val response = client.post(path) { setBody(body) }
            assertEquals("$path $body", HttpStatusCode.BadRequest, response.status)
            val message = response.json().getValue("message").jsonPrimitive.content
            assertTrue("$path $body: $message", message.startsWith("hotCorner must be one of") || "must be between" in message)
        }
        assertEquals(InfoValues(), OverlayStateStore.infoValues.value)
    }
}
