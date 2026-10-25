package us.bergnet.oversight.server

import android.content.Context
import android.util.Log
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import us.bergnet.oversight.server.routes.*
import us.bergnet.oversight.service.OverlayService

class HttpServer(
    private val port: Int,
    private val service: OverlayService
) {
    companion object {
        private const val TAG = "HttpServer"

        @OptIn(ExperimentalSerializationApi::class)
        val json = Json {
            ignoreUnknownKeys = true
            // README documents lowercase enum values ("bold", "semibold"); serial names are "Bold", "SemiBold".
            // Without this, coerceInputValues silently turns them into null.
            decodeEnumsCaseInsensitive = true
            isLenient = true
            encodeDefaults = true
            prettyPrint = false
            coerceInputValues = true
        }
    }

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    fun start() {
        if (server != null) {
            Log.w(TAG, "HTTP server already running, ignoring start()")
            return
        }
        try {
            server = embeddedServer(Netty, port = port) {
                apiModule(service)
                routing {
                    controlRoutes(service)
                }
            }.also {
                it.start(wait = false)
            }
            Log.d(TAG, "HTTP server started on port $port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start HTTP server", e)
        }
    }

    fun stop() {
        try {
            server?.stop(1000, 2000)
            server = null
            Log.d(TAG, "HTTP server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop HTTP server", e)
        }
    }
}

/** Everything except the service-control routes, so tests can run the API without an [OverlayService]. */
fun Application.apiModule(context: Context) {
    install(ContentNegotiation) {
        json(HttpServer.json)
    }
    routing {
        notifyRoutes()
        settingsRoutes(context)
        overlayRoutes()
        layoutRoutes()
    }
}
