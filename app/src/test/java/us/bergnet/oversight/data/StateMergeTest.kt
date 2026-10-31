package us.bergnet.oversight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import us.bergnet.oversight.data.model.ButtonMapping
import us.bergnet.oversight.data.model.FixedNotification
import us.bergnet.oversight.data.model.InfoValues
import us.bergnet.oversight.data.model.NotificationLayout
import us.bergnet.oversight.data.model.NotificationLayoutList
import us.bergnet.oversight.data.model.NotificationValues
import us.bergnet.oversight.data.model.OverlayCustomization
import us.bergnet.oversight.data.model.OverlayValues
import us.bergnet.oversight.data.model.ReceivedNotification
import us.bergnet.oversight.data.model.RemapAction
import us.bergnet.oversight.data.model.SettingsValues
import us.bergnet.oversight.data.model.enums.FontWeightEnum
import us.bergnet.oversight.data.store.OverlayStateStore
import us.bergnet.oversight.server.HttpServer

class StateMergeTest {

    @Before
    fun reset() {
        OverlayStateStore.setInfoValues(InfoValues())
        OverlayStateStore.setFixedNotifications(emptyList())
        OverlayStateStore.setLayoutList(NotificationLayoutList())
        OverlayStateStore.clearNotifications()
        OverlayStateStore.setButtonMappings(emptyList())
    }

    private fun decodeFixed(json: String) = HttpServer.json.decodeFromString<FixedNotification>(json)

    // --- Enum decoding ---

    @Test
    fun fontWeightAcceptsDocumentedLowercaseAndSerialNames() {
        val cases = mapOf(
            "bold" to FontWeightEnum.BOLD,
            "Bold" to FontWeightEnum.BOLD,
            "semibold" to FontWeightEnum.SEMI_BOLD,
            "extrabold" to FontWeightEnum.EXTRA_BOLD,
            "thin" to FontWeightEnum.THIN,
        )
        cases.forEach { (input, expected) ->
            val c = HttpServer.json.decodeFromString<OverlayCustomization>("""{"fontWeight":"$input"}""")
            assertEquals("fontWeight \"$input\"", expected, c.fontWeight)
        }
    }

    // --- Fixed notification partial updates ---

    @Test
    fun partialUpdateKeepsHiddenNotificationHidden() {
        OverlayStateStore.upsertFixedNotification(decodeFixed("""{"id":"a","text":"x","visible":false}"""))
        OverlayStateStore.upsertFixedNotification(decodeFixed("""{"id":"a","text":"y"}"""))
        val n = OverlayStateStore.getFixedNotification("a")!!
        assertEquals("y", n.text)
        assertFalse(n.isVisible())
    }

    @Test
    fun explicitVisibleTrueUnhides() {
        OverlayStateStore.upsertFixedNotification(decodeFixed("""{"id":"a","text":"x","visible":false}"""))
        OverlayStateStore.upsertFixedNotification(decodeFixed("""{"id":"a","visible":true}"""))
        assertTrue(OverlayStateStore.getFixedNotification("a")!!.isVisible())
    }

    @Test
    fun storedNotificationsAlwaysHaveExplicitVisible() {
        OverlayStateStore.upsertFixedNotification(decodeFixed("""{"id":"a","text":"x"}"""))
        assertEquals(true, OverlayStateStore.getFixedNotification("a")!!.visible)
    }

    @Test
    fun updateWithNewExpirationExtendsIt() {
        OverlayStateStore.upsertFixedNotification(decodeFixed("""{"id":"e","text":"x","expiration":"60"}"""))
        val first = OverlayStateStore.getFixedNotification("e")!!.expirationEpoch!!
        OverlayStateStore.upsertFixedNotification(decodeFixed("""{"id":"e","expiration":"1h"}"""))
        val second = OverlayStateStore.getFixedNotification("e")!!.expirationEpoch!!
        val extendedBySec = (second - first) / 1000
        assertTrue("extended by ${extendedBySec}s", extendedBySec in 3530..3545)
    }

    @Test
    fun updateWithoutExpirationKeepsIt() {
        OverlayStateStore.upsertFixedNotification(decodeFixed("""{"id":"e","text":"x","expiration":"60"}"""))
        val first = OverlayStateStore.getFixedNotification("e")!!.expirationEpoch
        OverlayStateStore.upsertFixedNotification(decodeFixed("""{"id":"e","text":"y"}"""))
        assertEquals(first, OverlayStateStore.getFixedNotification("e")!!.expirationEpoch)
    }

    // --- Layout selection ---

    @Test
    fun selectingLayoutMakesItActiveEvenWhenNameWasSetBefore() {
        OverlayStateStore.updateInfoValues(InfoValues(notifications = NotificationValues(notificationLayoutName = "Default")))
        OverlayStateStore.upsertLayout(NotificationLayout(name = "Custom"), select = true)
        assertEquals("Custom", OverlayStateStore.getCurrentLayout().name)
    }

    @Test
    fun upsertWithoutSelectLeavesActiveLayout() {
        OverlayStateStore.upsertLayout(NotificationLayout(name = "Custom"), select = false)
        assertEquals("Default", OverlayStateStore.getCurrentLayout().name)
    }

    // --- Port validation ---

    @Test
    fun invalidPortsAreIgnored() {
        listOf(0, 80, 1023, 65536, -1).forEach { port ->
            OverlayStateStore.updateInfoValues(InfoValues(settings = SettingsValues(remotePort = port)))
            assertEquals("port $port", 5001, OverlayStateStore.getRemotePort())
        }
        OverlayStateStore.updateInfoValues(InfoValues(settings = SettingsValues(remotePort = 8080)))
        assertEquals(8080, OverlayStateStore.getRemotePort())
    }

    @Test
    fun invalidPersistedPortFallsBackToDefault() {
        // setInfoValues is the load path and bypasses merge validation
        OverlayStateStore.setInfoValues(InfoValues(settings = SettingsValues(remotePort = 80)))
        assertEquals(5001, OverlayStateStore.getRemotePort())
    }

    // --- Notifications toggle ---

    @Test
    fun disablingNotificationsClearsCurrentAndQueue() {
        OverlayStateStore.enqueueNotification(ReceivedNotification(id = "1", title = "a"))
        OverlayStateStore.enqueueNotification(ReceivedNotification(id = "2", title = "b"))
        assertEquals(1, OverlayStateStore.notificationQueue.value.size)

        OverlayStateStore.updateInfoValues(InfoValues(notifications = NotificationValues(displayNotifications = false)))
        assertNull(OverlayStateStore.currentNotification.value)
        assertTrue(OverlayStateStore.notificationQueue.value.isEmpty())

        // Re-enabling must not resurrect the old toast
        OverlayStateStore.updateInfoValues(InfoValues(notifications = NotificationValues(displayNotifications = true)))
        assertNull(OverlayStateStore.currentNotification.value)
    }

    // --- Range validation ---

    @Test
    fun outOfRangeNotificationValuesAreRejected() {
        assertNotNull(NotificationValues(notificationDuration = 2).validate())
        assertNotNull(NotificationValues(notificationDuration = 31).validate())
        assertNotNull(NotificationValues(fixedNotificationsVisibility = 101).validate())
        assertNull(NotificationValues(notificationDuration = 3, fixedNotificationsVisibility = 0).validate())
        assertNull(NotificationValues().validate())

        OverlayStateStore.updateInfoValues(InfoValues(notifications = NotificationValues(notificationDuration = 999)))
        assertNull(OverlayStateStore.infoValues.value.notifications?.notificationDuration)
    }

    @Test
    fun outOfRangeOverlayValuesAreRejected() {
        assertNotNull(OverlayValues(overlayVisibility = 96).validate())
        assertNotNull(OverlayValues(clockOverlayVisibility = -1).validate())
        assertNotNull(OverlayValues(hotCorner = "middle").validate())
        assertNull(OverlayValues(overlayVisibility = 95, clockOverlayVisibility = 100, hotCorner = "bottom_start").validate())
    }

    // --- API responses ---

    @Test
    fun withDefaultsFillsUnsetFieldsButKeepsLayoutNameUnset() {
        OverlayStateStore.updateInfoValues(InfoValues(settings = SettingsValues(deviceName = "Den")))
        val info = OverlayStateStore.infoValues.value.withDefaults()
        assertEquals("Den", info.settings?.deviceName)
        assertEquals(5001, info.settings?.remotePort)
        assertEquals(false, info.settings?.displayDebug)
        assertEquals("top_end", info.overlay?.hotCorner)
        assertEquals(8, info.notifications?.notificationDuration)
        assertEquals(100, info.notifications?.fixedNotificationsVisibility)
        assertNull(info.notifications?.notificationLayoutName)
        // Stored state is untouched
        assertNull(OverlayStateStore.infoValues.value.overlay)
    }

    @Test
    fun removeFixedNotificationReportsWhetherItExisted() {
        OverlayStateStore.upsertFixedNotification(decodeFixed("""{"id":"a","text":"hi"}"""))
        assertTrue(OverlayStateStore.removeFixedNotification("a"))
        assertNull(OverlayStateStore.getFixedNotification("a"))
        assertFalse(OverlayStateStore.removeFixedNotification("a"))
    }

    // --- Button mappings ---

    @Test
    fun buttonMappingUpsertReplacesSameKeyAndPressOnly() {
        OverlayStateStore.upsertButtonMapping(ButtonMapping(4, false, RemapAction.HOME))
        OverlayStateStore.upsertButtonMapping(ButtonMapping(4, true, RemapAction.RECENTS))
        OverlayStateStore.upsertButtonMapping(ButtonMapping(4, false, RemapAction.BLOCK))
        val mappings = OverlayStateStore.buttonMappings.value
        assertEquals(2, mappings.size)
        assertEquals(RemapAction.BLOCK, mappings.single { !it.longPress }.action)
        assertEquals(RemapAction.RECENTS, mappings.single { it.longPress }.action)

        OverlayStateStore.removeButtonMapping(4, longPress = true)
        assertEquals(listOf(ButtonMapping(4, false, RemapAction.BLOCK)), OverlayStateStore.buttonMappings.value)
    }

    @Test
    fun buttonMappingsRoundTripThroughJson() {
        val mappings = listOf(
            ButtonMapping(188, false, RemapAction.LAUNCH_APP, "com.plexapp.android", "Plex"),
            ButtonMapping(4, true, RemapAction.HOME),
        )
        val json = HttpServer.json.encodeToString(mappings)
        assertEquals(mappings, HttpServer.json.decodeFromString<List<ButtonMapping>>(json))
    }
}
