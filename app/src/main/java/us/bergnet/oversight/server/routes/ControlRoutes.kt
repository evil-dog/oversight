package us.bergnet.oversight.server.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*
import us.bergnet.oversight.data.model.ApiResponse
import us.bergnet.oversight.service.OverlayService

fun Routing.controlRoutes(service: OverlayService) {
    post("/screen_on") {
        service.acquireTemporaryWakeLock()
        call.respond(ApiResponse.success("Screen wake lock acquired"))
    }

    post("/restart_service") {
        call.respond(ApiResponse.success("Service restart requested"))
        // Restart after responding
        val context = service.applicationContext
        OverlayService.stop(context)
        OverlayService.start(context)
    }
}
