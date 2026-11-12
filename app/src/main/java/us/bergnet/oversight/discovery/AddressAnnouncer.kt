package us.bergnet.oversight.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import us.bergnet.oversight.data.store.OverlayStateStore
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL

/**
 * Tells Home Assistant this device's address, for networks where mDNS can't reach it (e.g. across
 * VLANs). Home Assistant sets the webhook URL via POST /set/webhook; this posts
 * `{deviceId, host, port, deviceName}` to it on service start and whenever the IP, port or webhook
 * changes, retrying with backoff until Home Assistant accepts it.
 */
class AddressAnnouncer(context: Context, private val scope: CoroutineScope) {

    companion object {
        private const val TAG = "AddressAnnouncer"
        private const val SETTLE_MS = 2_000L
        private const val TIMEOUT_MS = 10_000
        private val RETRY_DELAYS_MS = listOf(5_000L, 15_000L, 30_000L, 60_000L, 120_000L, 300_000L)
        private const val MAX_RETRY_DELAY_MS = 900_000L

        /** Delay before retry number [attempt] (0-based); keeps retrying every 15 minutes after the backoff. */
        fun retryDelayMs(attempt: Int): Long = RETRY_DELAYS_MS.getOrElse(attempt) { MAX_RETRY_DELAY_MS }

        /**
         * Whether to retry after HTTP [code]. 400 means Home Assistant rejected the announcement
         * (another device's ID, or an address it checked and found a different device at), which
         * retrying won't change. 404 is retried: the webhook is missing while Home Assistant restarts.
         */
        fun shouldRetry(code: Int?): Boolean = code == null || (code !in 200..299 && code != 400)

        fun payload(deviceId: String, host: String, port: Int, deviceName: String): String =
            buildJsonObject {
                put("deviceId", deviceId)
                put("host", host)
                put("port", port)
                put("deviceName", deviceName)
            }.toString()
    }

    private data class Target(val url: String?, val host: String?, val port: Int)

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val deviceIp = MutableStateFlow<String?>(null)
    private var defaultNetwork: Network? = null
    private var job: Job? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            defaultNetwork = network
            deviceIp.value = linkProperties.linkAddresses
                .map { it.address }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        }

        override fun onLost(network: Network) {
            // A replacement default network may already have been reported
            if (network == defaultNetwork) {
                defaultNetwork = null
                deviceIp.value = null
            }
        }
    }

    @OptIn(FlowPreview::class)
    fun start() {
        if (job != null) return
        try {
            connectivity.registerDefaultNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to watch network changes", e)
        }
        job = scope.launch {
            combine(
                OverlayStateStore.webhookUrl,
                deviceIp,
                OverlayStateStore.infoValues.map { OverlayStateStore.getRemotePort() },
            ) { url, host, port -> Target(url, host, port) }
                .distinctUntilChanged()
                // Network changes arrive in bursts (link up, address assigned); announce once they settle
                .debounce(SETTLE_MS)
                .collectLatest { target ->
                    val url = target.url ?: return@collectLatest
                    val host = target.host ?: return@collectLatest
                    announce(url, host, target.port)
                }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        try {
            connectivity.unregisterNetworkCallback(networkCallback)
        } catch (_: IllegalArgumentException) {
            // Not registered
        }
    }

    /** Post until Home Assistant accepts or rejects it; cancelled by collectLatest when the target changes. */
    private suspend fun announce(url: String, host: String, port: Int) {
        var attempt = 0
        while (true) {
            val body = payload(OverlayStateStore.deviceId.value, host, port, OverlayStateStore.getDeviceName())
            val code = post(url, body)
            if (code in 200..299) {
                Log.d(TAG, "Announced $host:$port to Home Assistant")
                return
            }
            if (!shouldRetry(code)) {
                Log.w(TAG, "Home Assistant rejected the announcement of $host:$port (HTTP $code)")
                return
            }
            val wait = retryDelayMs(attempt++)
            Log.d(TAG, "Announcing $host:$port failed (${code?.let { "HTTP $it" } ?: "no response"}), retrying in ${wait / 1000}s")
            delay(wait)
        }
    }

    /** POST [body] as JSON; returns the HTTP status, or null if the request failed. */
    private suspend fun post(url: String, body: String): Int? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            connection.outputStream.use { it.write(body.toByteArray()) }
            connection.responseCode
        } catch (e: Exception) {
            Log.d(TAG, "Announcement request failed: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }
}
