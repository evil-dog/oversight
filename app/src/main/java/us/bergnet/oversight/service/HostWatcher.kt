package us.bergnet.oversight.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import us.bergnet.oversight.data.model.HostState
import us.bergnet.oversight.data.store.OverlayStateStore
import us.bergnet.oversight.util.Packages

/**
 * Keeps the persisted host registration honest: a host that was uninstalled is forgotten, and
 * remapping returns to OverSight once the host's accessibility service (which does its remapping)
 * is no longer enabled. Checked on start, then on package removal and accessibility setting changes.
 */
class HostWatcher(private val context: Context) {

    companion object {
        private const val TAG = "HostWatcher"

        /** What to do with [state] given whether its package is installed and its accessibility service is on. */
        fun reconcile(state: HostState, installed: Boolean, accessibilityEnabled: Boolean): HostState = when {
            state.packageName == null -> state
            !installed -> HostState()
            !accessibilityEnabled && state.remapActive -> state.copy(remapActive = false)
            else -> state
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Updates send REMOVED then ADDED with EXTRA_REPLACING; only a real uninstall counts
            if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            if (intent.data?.schemeSpecificPart == OverlayStateStore.hostState.value.packageName) check()
        }
    }

    private val accessibilityObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = check()
    }

    fun start() {
        check()
        ContextCompat.registerReceiver(
            context, packageReceiver,
            IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply { addDataScheme("package") },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, accessibilityObserver
        )
    }

    fun stop() {
        try {
            context.unregisterReceiver(packageReceiver)
        } catch (_: IllegalArgumentException) {
            // Not registered
        }
        context.contentResolver.unregisterContentObserver(accessibilityObserver)
    }

    private fun check() {
        val state = OverlayStateStore.hostState.value
        val pkg = state.packageName ?: return
        val next = reconcile(
            state,
            installed = Packages.isInstalled(context, pkg),
            accessibilityEnabled = Packages.hasEnabledAccessibilityService(context, pkg),
        )
        if (next != state) {
            Log.d(TAG, "Host $pkg: installed/accessibility changed, now $next")
            OverlayStateStore.setHostState(next)
        }
    }
}
