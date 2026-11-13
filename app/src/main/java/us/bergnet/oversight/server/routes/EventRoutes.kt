package us.bergnet.oversight.server.routes

import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import us.bergnet.oversight.data.model.FixedNotification
import us.bergnet.oversight.data.store.OverlayStateStore
import us.bergnet.oversight.server.HttpServer
import kotlin.time.Duration.Companion.seconds

/** Seconds between keep-alive comments, so idle connections aren't dropped by the client or network. */
internal const val SSE_KEEP_ALIVE_SECONDS = 15

/**
 * GET /events: Server-Sent Events for clients that would otherwise poll (Foyer).
 * `fixed_notifications` carries the same array as GET /fixed_notifications: once on connect as a
 * full snapshot, then on every change, including a badge expiring.
 */
fun Routing.eventRoutes() {
    sse("/events") {
        heartbeat {
            period = SSE_KEEP_ALIVE_SECONDS.seconds
            event = ServerSentEvent(comments = "keep-alive")
        }
        fixedNotificationEvents().collect { data ->
            send(ServerSentEvent(data = data, event = "fixed_notifications"))
        }
    }
}

/** Data of each `fixed_notifications` event: the current active badges, then each change to them. */
internal fun fixedNotificationEvents(): Flow<String> =
    OverlayStateStore.fixedNotifications
        .map { notifications ->
            HttpServer.json.encodeToString(
                ListSerializer(FixedNotification.serializer()),
                OverlayStateStore.active(notifications)
            )
        }
        .distinctUntilChanged()
