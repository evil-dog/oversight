package us.bergnet.oversight.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import us.bergnet.oversight.R
import us.bergnet.oversight.data.store.OverlayStateStore
import us.bergnet.oversight.receiver.ScreenStateReceiver
import us.bergnet.oversight.server.HttpServer
import us.bergnet.oversight.util.NetworkUtils

class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        private const val CHANNEL_ID = "oversight_service"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    private var overlayWindowManager: OverlayWindowManager? = null
    private var httpServer: HttpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val screenStateReceiver = ScreenStateReceiver()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // Register screen state receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenStateReceiver, filter)

        // Create overlay window
        overlayWindowManager = OverlayWindowManager(this)
        showOverlay()

        // Start HTTP server
        val port = OverlayStateStore.getRemotePort()
        httpServer = HttpServer(port, this).also { it.start() }

        OverlayStateStore.setServiceRunning(true)
        Log.d(TAG, "Service started, IP: ${NetworkUtils.getDeviceIpAddress()}, port: $port")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")

        httpServer?.stop()
        httpServer = null

        overlayWindowManager?.dismiss()
        overlayWindowManager = null

        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {}

        releaseWakeLock()
        OverlayStateStore.setServiceRunning(false)
    }

    private fun showOverlay() {
        overlayWindowManager?.show {
            // Placeholder overlay - will be replaced in Phase 4
            val isRunning by OverlayStateStore.isServiceRunning.collectAsState()
            if (isRunning) {
                Text(text = "", color = Color.Transparent)
            }
        }
    }

    fun acquireTemporaryWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock?.release()
        wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "oversight:screen_on"
        ).apply {
            acquire(3000L) // 3 seconds
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.service_channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val port = OverlayStateStore.getRemotePort()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.service_notification_title))
                .setContentText(getString(R.string.service_notification_text, port))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(getString(R.string.service_notification_title))
                .setContentText(getString(R.string.service_notification_text, port))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build()
        }
    }
}
