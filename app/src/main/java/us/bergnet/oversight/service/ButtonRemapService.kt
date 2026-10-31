package us.bergnet.oversight.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import us.bergnet.oversight.OverSightApplication
import us.bergnet.oversight.data.model.ButtonMapping
import us.bergnet.oversight.data.model.RemapAction
import us.bergnet.oversight.data.store.OverlayStateStore

/**
 * Remaps remote buttons. An accessibility service with key filtering sees each key before the
 * foreground app and can consume it; it can't inject other key codes, so remapped buttons run
 * actions instead (global actions, media keys, volume, app launch). Power never reaches the filter.
 */
class ButtonRemapService : AccessibilityService() {

    companion object {
        private const val TAG = "ButtonRemapService"

        fun component(context: Context) = ComponentName(context, ButtonRemapService::class.java)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var stateLoaded = false

    private val remapper by lazy {
        ButtonRemapper(
            mappings = { OverlayStateStore.buttonMappings.value },
            isActive = { stateLoaded && !OverlayStateStore.isSetupForeground },
            takeCaptureListener = {
                OverlayStateStore.keyCaptureListener.also { OverlayStateStore.keyCaptureListener = null }
            },
            scheduler = { delayMs, block ->
                val runnable = Runnable(block)
                handler.postDelayed(runnable, delayMs)
                ButtonRemapper.Cancellable { handler.removeCallbacks(runnable) }
            },
            longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong(),
            actions = object : ButtonRemapper.Actions {
                override fun execute(mapping: ButtonMapping) = perform(mapping)
                override fun emulateDefault(keyCode: Int) = emulateKey(keyCode)
            },
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Connected")
        OverlayStateStore.setRemapServiceConnected(true)
        // Keys pass through until persisted mappings are in the store
        scope.launch {
            OverSightApplication.from(this@ButtonRemapService).stateLoaded.await()
            stateLoaded = true
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val isDown = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return false
        }
        return remapper.onKey(event.keyCode, isDown, event.repeatCount)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        OverlayStateStore.setRemapServiceConnected(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        OverlayStateStore.setRemapServiceConnected(false)
        remapper.reset()
        scope.cancel()
        super.onDestroy()
    }

    private fun perform(mapping: ButtonMapping) {
        Log.d(TAG, "${ButtonMapping.keyName(mapping.keyCode)}${if (mapping.longPress) " (long)" else ""} -> ${mapping.action}")
        when (mapping.action) {
            RemapAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            RemapAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            RemapAction.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            RemapAction.NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            RemapAction.PLAY_PAUSE -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            RemapAction.NEXT -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            RemapAction.PREVIOUS -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            RemapAction.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
            RemapAction.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
            RemapAction.MUTE -> adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE)
            RemapAction.LAUNCH_APP -> mapping.packageName?.let(::launchApp)
            RemapAction.BLOCK -> {}
        }
    }

    private fun emulateKey(keyCode: Int) {
        when {
            keyCode == KeyEvent.KEYCODE_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            keyCode == KeyEvent.KEYCODE_HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            ButtonMapping.isMediaKey(keyCode) -> sendMediaKey(keyCode)
            keyCode == KeyEvent.KEYCODE_VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
            keyCode == KeyEvent.KEYCODE_VOLUME_MUTE -> adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE)
        }
    }

    /** Routed to the active media session, the same as a remote's media button. */
    private fun sendMediaKey(keyCode: Int) {
        val audio = getSystemService(AudioManager::class.java)
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun adjustVolume(direction: Int) {
        getSystemService(AudioManager::class.java)
            .adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    /** Accessibility services are exempt from background activity start restrictions. */
    private fun launchApp(packageName: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Log.w(TAG, "No launch intent for $packageName")
            return
        }
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $packageName", e)
        }
    }
}

