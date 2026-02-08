package us.bergnet.oversight.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import us.bergnet.oversight.data.store.OverlayStateStore

class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                Log.d("ScreenStateReceiver", "Screen ON")
                OverlayStateStore.setScreenOn(true)
            }
            Intent.ACTION_SCREEN_OFF -> {
                Log.d("ScreenStateReceiver", "Screen OFF")
                OverlayStateStore.setScreenOn(false)
            }
        }
    }
}
