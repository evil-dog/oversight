package us.bergnet.oversight.discovery

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressAnnouncerTest {

    @Test
    fun retriesFailuresButNotRejections() {
        assertTrue(AddressAnnouncer.shouldRetry(null)) // no response
        assertTrue(AddressAnnouncer.shouldRetry(404)) // webhook missing while Home Assistant restarts
        assertTrue(AddressAnnouncer.shouldRetry(503)) // address not verifiable yet
        assertFalse(AddressAnnouncer.shouldRetry(200))
        assertFalse(AddressAnnouncer.shouldRetry(400))
    }

    @Test
    fun backoffGrowsThenSettlesAt15Minutes() {
        val delays = (0..8).map { AddressAnnouncer.retryDelayMs(it) }
        assertEquals(delays.sorted(), delays)
        assertEquals(5_000L, delays.first())
        assertEquals(900_000L, delays.last())
    }

    @Test
    fun payloadCarriesAddressAndIdentity() {
        val json = Json.parseToJsonElement(
            AddressAnnouncer.payload("device-1", "192.168.24.77", 5001, "Living Room")
        ).jsonObject
        assertEquals("device-1", json.getValue("deviceId").jsonPrimitive.content)
        assertEquals("192.168.24.77", json.getValue("host").jsonPrimitive.content)
        assertEquals(5001, json.getValue("port").jsonPrimitive.int)
        assertEquals("Living Room", json.getValue("deviceName").jsonPrimitive.content)
    }
}
