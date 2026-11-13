package us.bergnet.oversight.server.routes

import android.content.Context
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import us.bergnet.oversight.data.model.ApiResponse
import us.bergnet.oversight.data.model.HostReport
import us.bergnet.oversight.data.model.HostRequest
import us.bergnet.oversight.data.model.HostState
import us.bergnet.oversight.data.store.OverlayStateStore
import us.bergnet.oversight.server.HttpServer
import us.bergnet.oversight.service.HostWatcher
import us.bergnet.oversight.util.Packages
import java.net.InetAddress

/** Whether [address] (an IP literal, as the server reports the peer) is this device. Covers IPv4-mapped IPv6. */
internal fun isLoopback(address: String): Boolean = try {
    InetAddress.getByName(address).isLoopbackAddress
} catch (_: Exception) {
    false
}

/** Default caller check for POST /host: only apps on this device. */
internal val loopbackCaller: (ApplicationCall) -> Boolean = { isLoopback(it.request.local.remoteAddress) }

/**
 * POST /host: a home screen app (Foyer) leases "home visible" (OverSight hides its clock and badges)
 * and claims button remapping. Accepted only from loopback and only for allowlisted, installed packages.
 */
fun Routing.hostRoutes(context: Context, isTrustedCaller: (ApplicationCall) -> Boolean) {
    post("/host") {
        if (!isTrustedCaller(call)) {
            call.respond(HttpStatusCode.Forbidden, ApiResponse.error("/host is only accepted from this device"))
            return@post
        }
        try {
            val request = HttpServer.json.decodeFromString<HostRequest>(call.receiveText())
            val pkg = request.packageName
            if (pkg.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse.error("package is required"))
                return@post
            }
            if (pkg !in HostState.ALLOWED_PACKAGES || !Packages.isInstalled(context, pkg)) {
                call.respond(HttpStatusCode.Forbidden, ApiResponse.error("Package '$pkg' may not register as host"))
                return@post
            }
            OverlayStateStore.applyHostRequest(request)
            // remapActive only holds while the host's accessibility service (its remapper) is on
            val current = OverlayStateStore.hostState.value
            OverlayStateStore.setHostState(
                HostWatcher.reconcile(
                    current, installed = true,
                    accessibilityEnabled = Packages.hasEnabledAccessibilityService(context, pkg)
                )
            )
            val report = OverlayStateStore.hostReport()
            call.respond(
                ApiResponse.success(
                    "Host updated",
                    report?.let { HttpServer.json.encodeToJsonElement(HostReport.serializer(), it) }
                )
            )
        } catch (e: Exception) {
            call.respondError(e)
        }
    }
}
