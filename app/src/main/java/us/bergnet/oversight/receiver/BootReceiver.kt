package us.bergnet.oversight.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import us.bergnet.oversight.service.OverlayService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "android.intent.action.REBOOT"
        ) {
            Log.d("BootReceiver", "Boot completed, starting OverlayService")
            OverlayService.start(context)
        }
    }
}
