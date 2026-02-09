package us.bergnet.oversight.ui.setup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import us.bergnet.oversight.data.repository.PersistenceManager
import us.bergnet.oversight.data.store.OverlayStateStore
import us.bergnet.oversight.service.OverlayService

class SetupActivity : ComponentActivity() {

    private val hasOverlayPermission = mutableStateOf(false)
    private val hasBatteryExemption = mutableStateOf(false)
    private val onboardingComplete = mutableStateOf<Boolean?>(null) // null = loading
    private var persistenceManager: PersistenceManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        updatePermissionStates()
        persistenceManager = PersistenceManager(this)

        lifecycleScope.launch {
            // Load persisted state so settings are available even if service isn't running
            if (!OverlayStateStore.isServiceRunning.value) {
                persistenceManager?.loadAll()
                persistenceManager?.startAutoSave(lifecycleScope)
            }

            val complete = persistenceManager?.isOnboardingComplete() ?: false
            onboardingComplete.value = complete

            if (complete && hasOverlayPermission.value) {
                startOverlayService()
            }
        }

        setContent {
            val onboarded by onboardingComplete
            val overlayGranted by hasOverlayPermission
            val batteryGranted by hasBatteryExemption

            when {
                onboarded == null -> {
                    // Loading - show nothing briefly
                }
                onboarded == false -> {
                    OnboardingScreen(
                        hasOverlayPermission = overlayGranted,
                        hasBatteryExemption = batteryGranted,
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onRequestBatteryExemption = { requestBatteryOptimizationExemption() },
                        onComplete = {
                            lifecycleScope.launch {
                                persistenceManager?.setOnboardingComplete()
                            }
                            onboardingComplete.value = true
                            if (overlayGranted) {
                                startOverlayService()
                            }
                        }
                    )
                }
                else -> {
                    SetupScreen(
                        hasOverlayPermission = overlayGranted,
                        hasBatteryExemption = batteryGranted,
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onRequestBatteryExemption = { requestBatteryOptimizationExemption() },
                        onStartService = { startOverlayService() },
                        onStopService = { stopOverlayService() },
                        onRestartService = {
                            stopOverlayService()
                            window.decorView.postDelayed({ startOverlayService() }, 500)
                        },
                        onClearFixedNotifications = {
                            OverlayStateStore.setFixedNotifications(emptyList())
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
        // If onboarding is done and we have permission, ensure service is started
        if (onboardingComplete.value == true && hasOverlayPermission.value) {
            startOverlayService()
        }
    }

    private fun updatePermissionStates() {
        hasOverlayPermission.value = Settings.canDrawOverlays(this)
        val pm = getSystemService(PowerManager::class.java)
        hasBatteryExemption.value = pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                @Suppress("BatteryLife")
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    private fun startOverlayService() {
        OverlayService.start(this)
    }

    private fun stopOverlayService() {
        OverlayService.stop(this)
    }
}
