package us.bergnet.oversight.server

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
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
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import us.bergnet.oversight.data.model.FixedNotification
import us.bergnet.oversight.server.routes.fixedNotificationEvents
import kotlinx.serialization.json.jsonArray
import us.bergnet.oversight.data.model.HostState
import us.bergnet.oversight.data.model.InfoValues
import us.bergnet.oversight.server.routes.isLoopback
import us.bergnet.oversight.service.HostWatcher
import us.bergnet.oversight.util.Packages
import us.bergnet.oversight.data.model.NotificationLayoutList
import us.bergnet.oversight.data.store.OverlayStateStore
import us.bergnet.oversight.util.SettingsScreens

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
        OverlayStateStore.setWebhookUrl(null)
        OverlayStateStore.setHostState(HostState())
        OverlayStateStore.clock = { now }
    }

    private var now = 1_000_000L

    private fun api(
        trustedHostCaller: Boolean = true,
        block: suspend ApplicationTestBuilder.() -> Unit
    ) = testApplication {
        // The test client is always local, so the loopback check is replaced to cover both cases
        application { apiModule(context, isTrustedHostCaller = { trustedHostCaller }) }
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

    /** Makes [intent] resolve to an activity named [activityName], as a real settings app would. */
    private fun registerSettingsScreen(intent: Intent, activityName: String) {
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.android.tv.settings"
                name = activityName
            }
        })
    }

    private suspend fun ApplicationTestBuilder.batteryState() =
        client.get("/info").json().result.getValue("status").jsonObject
            .getValue("batteryOptimizationState").jsonPrimitive.content

    @Test
    fun infoReportsMissingPermission() = api {
        ShadowSettings.setCanDrawOverlays(false)
        val status = client.get("/info").json().result.getValue("status").jsonObject
        assertEquals("DENIED_SETTING_AVAILABLE", status.getValue("permissionState").jsonPrimitive.content)
    }

    @Test
    fun batteryNotExemptButRequestableIsSkipped() = api {
        registerSettingsScreen(SettingsScreens.batteryExemptionIntents(context).first(), "com.android.tv.settings.RequestIgnoreBatteryOptimizations")
        assertEquals("SKIPPED", batteryState())
    }

    @Test
    fun batteryWithNoExemptionScreenIsBlocked() = api {
        assertEquals("BLOCKED", batteryState())
    }

    @Test
    fun batteryWithOnlyStubExemptionScreensIsBlocked() = api {
        // Google TV: both battery intents resolve, but to a stub that closes immediately
        SettingsScreens.batteryExemptionIntents(context).forEach {
            registerSettingsScreen(it, "com.android.tv.settings.EmptyStubActivity")
        }
        assertEquals("BLOCKED", batteryState())
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

    // --- Webhook ---

    private suspend fun ApplicationTestBuilder.webhookConfigured(): Boolean =
        client.get("/info").json().result.getValue("status").jsonObject
            .getValue("webhookConfigured").jsonPrimitive.boolean

    @Test
    fun setWebhookStoresUrlAndReportsIt() = api {
        assertFalse(webhookConfigured())

        val url = "http://192.168.25.2:8123/api/webhook/oversight_abc"
        val set = client.post("/set/webhook") { setBody("""{"url":" $url "}""") }
        assertEquals(HttpStatusCode.OK, set.status)
        assertEquals(url, OverlayStateStore.webhookUrl.value)
        assertTrue(webhookConfigured())

        val cleared = client.post("/set/webhook") { setBody("""{"url":null}""") }
        assertEquals(HttpStatusCode.OK, cleared.status)
        assertNull(OverlayStateStore.webhookUrl.value)
        assertFalse(webhookConfigured())
    }

    @Test
    fun invalidWebhookUrlReturns400AndKeepsCurrent() = api {
        val url = "http://192.168.25.2:8123/api/webhook/oversight_abc"
        OverlayStateStore.setWebhookUrl(url)
        listOf("ftp://host/x", "not a url", "/api/webhook/x", "http:///nohost").forEach { bad ->
            val response = client.post("/set/webhook") { setBody("""{"url":"$bad"}""") }
            assertEquals(bad, HttpStatusCode.BadRequest, response.status)
        }
        assertEquals(url, OverlayStateStore.webhookUrl.value)
    }

    // --- Host (Foyer) ---

    private fun installFoyer(accessibilityEnabled: Boolean = true) {
        shadowOf(context.packageManager).installPackage(
            android.content.pm.PackageInfo().apply { packageName = HostState.FOYER_PACKAGE }
        )
        Settings.Secure.putString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            if (accessibilityEnabled) "com.other/.Service:us.bergnet.foyer/.a11y.FoyerAccessibilityService" else ""
        )
    }

    private suspend fun ApplicationTestBuilder.postHost(body: String) =
        client.post("/host") { setBody(body) }

    private suspend fun ApplicationTestBuilder.reportedHost(): JsonObject? =
        client.get("/info").json().result.getValue("status").jsonObject["host"]
            ?.takeIf { it != JsonNull }?.jsonObject

    @Test
    fun loopbackCheck() {
        listOf("127.0.0.1", "::1", "::ffff:127.0.0.1").forEach { assertTrue(it, isLoopback(it)) }
        listOf("192.168.24.10", "fe80::1", "not an address").forEach { assertFalse(it, isLoopback(it)) }
    }

    @Test
    fun hostRejectsCallersOffDevice() = api(trustedHostCaller = false) {
        installFoyer()
        val response = postHost("""{"package":"us.bergnet.foyer","homeVisible":true}""")
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertNull(OverlayStateStore.hostState.value.packageName)
    }

    @Test
    fun hostRejectsUnknownOrMissingPackages() = api {
        assertEquals(HttpStatusCode.Forbidden, postHost("""{"package":"com.evil.app","homeVisible":true}""").status)
        // Allowlisted but not installed
        assertEquals(HttpStatusCode.Forbidden, postHost("""{"package":"us.bergnet.foyer","homeVisible":true}""").status)
        assertEquals(HttpStatusCode.BadRequest, postHost("""{"homeVisible":true}""").status)
        assertNull(reportedHost())
    }

    @Test
    fun homeVisibleLeaseExpires() = api {
        installFoyer()
        val response = postHost("""{"package":"us.bergnet.foyer","homeVisible":true,"leaseSeconds":30}""")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.json().result.getValue("homeVisible").jsonPrimitive.boolean)
        assertTrue(reportedHost()!!.getValue("homeVisible").jsonPrimitive.boolean)

        now += 29_000
        assertTrue(reportedHost()!!.getValue("homeVisible").jsonPrimitive.boolean)
        now += 2_000
        assertFalse(reportedHost()!!.getValue("homeVisible").jsonPrimitive.boolean)

        // Renewing restarts the lease; false ends it at once
        postHost("""{"package":"us.bergnet.foyer","homeVisible":true}""")
        assertTrue(reportedHost()!!.getValue("homeVisible").jsonPrimitive.boolean)
        postHost("""{"package":"us.bergnet.foyer","homeVisible":false}""")
        assertFalse(reportedHost()!!.getValue("homeVisible").jsonPrimitive.boolean)
    }

    @Test
    fun leaseSecondsIsClamped() = api {
        installFoyer()
        postHost("""{"package":"us.bergnet.foyer","homeVisible":true,"leaseSeconds":1}""")
        assertEquals(now + 5_000, OverlayStateStore.hostState.value.homeVisibleUntil)
        postHost("""{"package":"us.bergnet.foyer","homeVisible":true,"leaseSeconds":100000}""")
        assertEquals(now + 300_000, OverlayStateStore.hostState.value.homeVisibleUntil)
    }

    @Test
    fun omittedHostFieldsKeepTheirValue() = api {
        installFoyer()
        postHost("""{"package":"us.bergnet.foyer","remapActive":true}""")
        postHost("""{"package":"us.bergnet.foyer","homeVisible":true}""")
        val host = reportedHost()!!
        assertTrue(host.getValue("remapActive").jsonPrimitive.boolean)
        assertTrue(host.getValue("homeVisible").jsonPrimitive.boolean)
        assertEquals("us.bergnet.foyer", host.getValue("package").jsonPrimitive.content)
        assertTrue(OverlayStateStore.isRemapManagedExternally())
    }

    @Test
    fun remapActiveNeedsTheHostsAccessibilityService() = api {
        installFoyer(accessibilityEnabled = false)
        val response = postHost("""{"package":"us.bergnet.foyer","remapActive":true}""")
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(response.json().result.getValue("remapActive").jsonPrimitive.boolean)
        assertFalse(OverlayStateStore.isRemapManagedExternally())
    }

    @Test
    fun hostWatcherReconcile() {
        val foyer = HostState(packageName = HostState.FOYER_PACKAGE, remapActive = true, homeVisibleUntil = 5L)
        assertEquals(HostState(), HostWatcher.reconcile(foyer, installed = false, accessibilityEnabled = true))
        assertEquals(foyer.copy(remapActive = false), HostWatcher.reconcile(foyer, installed = true, accessibilityEnabled = false))
        assertEquals(foyer, HostWatcher.reconcile(foyer, installed = true, accessibilityEnabled = true))
        assertEquals(HostState(), HostWatcher.reconcile(HostState(), installed = false, accessibilityEnabled = false))
    }

    @Test
    fun enabledAccessibilityPackagesParsesShortAndLongForms() {
        assertEquals(
            setOf("us.bergnet.foyer", "us.bergnet.oversight"),
            Packages.enabledAccessibilityPackages(
                "us.bergnet.foyer/.a11y.FoyerAccessibilityService:" +
                    "us.bergnet.oversight/us.bergnet.oversight.service.ButtonRemapService"
            )
        )
        assertTrue(Packages.enabledAccessibilityPackages(null).isEmpty())
        assertTrue(Packages.enabledAccessibilityPackages("").isEmpty())
    }

    @Test
    fun hostPersistsWithoutTheLease() {
        val json = HttpServer.json
        val stored = json.encodeToString(
            HostState.serializer(),
            HostState(packageName = HostState.FOYER_PACKAGE, remapActive = true, homeVisibleUntil = 99L)
        )
        assertFalse(stored, "homeVisibleUntil" in stored)
        assertEquals(
            HostState(packageName = HostState.FOYER_PACKAGE, remapActive = true),
            json.decodeFromString(HostState.serializer(), stored)
        )
    }

    // --- Events (SSE) ---

    @Test
    fun fixedNotificationEventsSnapshotThenChanges() = runBlocking {
        OverlayStateStore.upsertFixedNotification(FixedNotification(id = "one", text = "first"))
        val events = mutableListOf<String>()
        withTimeout(5_000) {
            fixedNotificationEvents().take(3).collect { data ->
                events += data
                when (events.size) {
                    1 -> OverlayStateStore.upsertFixedNotification(FixedNotification(id = "two", text = "second"))
                    2 -> OverlayStateStore.removeFixedNotification("one")
                }
            }
        }
        val ids = events.map { e ->
            HttpServer.json.parseToJsonElement(e).jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
        }
        assertEquals(listOf(listOf("one"), listOf("one", "two"), listOf("two")), ids)
    }
}
