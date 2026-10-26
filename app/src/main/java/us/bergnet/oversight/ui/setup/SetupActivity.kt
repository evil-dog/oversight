package us.bergnet.oversight.ui.setup

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import us.bergnet.oversight.OverSightApplication
import us.bergnet.oversight.data.store.OverlayStateStore
import us.bergnet.oversight.service.OverlayService
import us.bergnet.oversight.util.SettingsScreens

class SetupActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SetupActivity"
    }

    private val hasOverlayPermission = mutableStateOf(false)
    private val hasBatteryExemption = mutableStateOf(false)
    // Fixed for the device, so resolve once rather than on every resume
    private val canRequestBatteryExemption by lazy { SettingsScreens.canRequestBatteryExemption(this) }
    private val onboardingComplete = mutableStateOf<Boolean?>(null) // null = loading

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        updatePermissionStates()
        val app = OverSightApplication.from(this)

        lifecycleScope.launch {
            // Settings must reflect persisted state even if the service isn't running
            app.stateLoaded.await()

            val complete = app.persistence.isOnboardingComplete()
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
                        canRequestBatteryExemption = canRequestBatteryExemption,
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onRequestBatteryExemption = { requestBatteryOptimizationExemption() },
                        onComplete = {
                            lifecycleScope.launch {
                                app.persistence.setOnboardingComplete()
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
                        canRequestBatteryExemption = canRequestBatteryExemption,
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
                        },
                        appVersion = appVersion
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

    private val appVersion: String by lazy {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
    }

    private fun updatePermissionStates() {
        hasOverlayPermission.value = Settings.canDrawOverlays(this)
        val pm = getSystemService(PowerManager::class.java)
        hasBatteryExemption.value = pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestOverlayPermission() {
        launchSettingsOrShowAdbHint(
            SettingsScreens.overlayPermissionIntents(this),
            unavailableMessage = "Overlay settings aren't available on this device. Grant via ADB:\n" +
                "adb shell appops set $packageName SYSTEM_ALERT_WINDOW allow",
        )
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        launchSettingsOrShowAdbHint(
            SettingsScreens.batteryExemptionIntents(this),
            unavailableMessage = "Battery optimization settings aren't available on this device. Exempt via ADB:\n" +
                "adb shell dumpsys deviceidle whitelist +$packageName",
        )
    }

    /** Starts the first usable settings screen in [candidates], else shows [unavailableMessage]. */
    private fun launchSettingsOrShowAdbHint(candidates: List<Intent>, unavailableMessage: String) {
        val launched = SettingsScreens.usableIntent(this, candidates)?.let { intent ->
            try {
                startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        } ?: false
        if (!launched) {
            Log.w(TAG, "No usable settings screen for ${candidates.first().action}")
            Toast.makeText(this, unavailableMessage, Toast.LENGTH_LONG).show()
        }
    }

    private fun startOverlayService() {
        OverlayService.start(this)
    }

    private fun stopOverlayService() {
        OverlayService.stop(this)
    }
}
