package us.bergnet.oversight.server

import android.util.Log
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import us.bergnet.oversight.server.routes.*
import us.bergnet.oversight.service.OverlayService

class HttpServer(
    private val port: Int,
    private val service: OverlayService
) {
    companion object {
        private const val TAG = "HttpServer"

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            prettyPrint = false
            coerceInputValues = true
        }
    }

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    fun start() {
        try {
            server = embeddedServer(Netty, port = port) {
                install(ContentNegotiation) {
                    json(json)
                }
                routing {
                    notifyRoutes(service)
                    settingsRoutes()
                    overlayRoutes()
                    controlRoutes(service)
                    layoutRoutes()
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
