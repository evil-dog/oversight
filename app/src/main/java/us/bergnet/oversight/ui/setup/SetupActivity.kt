package us.bergnet.oversight.ui.setup

import android.content.ActivityNotFoundException
import android.content.ComponentName
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
import us.bergnet.oversight.service.ButtonRemapService
import us.bergnet.oversight.service.OverlayService
import us.bergnet.oversight.util.SettingsScreens

class SetupActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SetupActivity"

        /**
         * Features introduced after first-run onboarding existed, by persisted name. An install that
         * finished onboarding before a feature shipped is shown its step once as a "what's new" intro.
         */
        private val FEATURE_INTROS = mapOf("button_remap" to OnboardingStep.BUTTON_REMAP)
    }

    private val hasOverlayPermission = mutableStateOf(false)
    private val hasBatteryExemption = mutableStateOf(false)
    // Fixed for the device, so resolve once rather than on every resume
    private val canRequestBatteryExemption by lazy { SettingsScreens.canRequestBatteryExemption(this) }
    private val hasRemapService = mutableStateOf(false)
    private val onboardingComplete = mutableStateOf<Boolean?>(null) // null = loading
    // Onboarding or feature intro steps still to show; empty once done
    private val pendingSteps = mutableStateOf<List<OnboardingStep>>(emptyList())
    private val isFeatureIntro = mutableStateOf(false)
    private val initialSettingsItem = mutableStateOf(SId.ABOUT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        updatePermissionStates()
        val app = OverSightApplication.from(this)

        lifecycleScope.launch {
            // Settings must reflect persisted state even if the service isn't running
            app.stateLoaded.await()

            val complete = app.persistence.isOnboardingComplete()
            if (complete) {
                val seen = app.persistence.seenFeatureIntros()
                val unseen = FEATURE_INTROS.filterKeys { it !in seen }
                // Already set up (e.g. enabled via ADB): nothing to introduce
                val (done, todo) = unseen.entries.partition { isStepDone(it.value) }
                if (done.isNotEmpty()) app.persistence.markFeatureIntrosSeen(done.map { it.key })
                pendingSteps.value = todo.map { it.value }
                isFeatureIntro.value = true
            } else {
                pendingSteps.value = listOfNotNull(
                    OnboardingStep.WELCOME,
                    OnboardingStep.OVERLAY,
                    OnboardingStep.BATTERY.takeIf { canRequestBatteryExemption },
                    OnboardingStep.BUTTON_REMAP,
                )
            }
            onboardingComplete.value = complete

            if (complete && hasOverlayPermission.value) {
                startOverlayService()
            }
        }

        setContent {
            val onboarded by onboardingComplete
            val overlayGranted by hasOverlayPermission
            val batteryGranted by hasBatteryExemption
            val remapEnabled by hasRemapService
            val steps by pendingSteps

            when {
                onboarded == null -> {
                    // Loading - show nothing briefly
                }
                steps.isNotEmpty() -> {
                    val featureIntro by isFeatureIntro
                    OnboardingScreen(
                        steps = steps,
                        isFeatureIntro = featureIntro,
                        hasOverlayPermission = overlayGranted,
                        hasBatteryExemption = batteryGranted,
                        hasRemapService = remapEnabled,
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onRequestBatteryExemption = { requestBatteryOptimizationExemption() },
                        onRequestRemapService = { requestAccessibilitySettings() },
                        onComplete = {
                            lifecycleScope.launch {
                                app.persistence.setOnboardingComplete()
                                // First-run onboarding covers every feature, so none needs an intro later
                                app.persistence.markFeatureIntrosSeen(FEATURE_INTROS.keys)
                            }
                            // Straight to the new feature's settings if it was just turned on
                            if (featureIntro && OnboardingStep.BUTTON_REMAP in steps && remapEnabled) {
                                initialSettingsItem.value = SId.BUTTON_MAPPINGS
                            }
                            pendingSteps.value = emptyList()
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
                        hasRemapPermission = remapEnabled,
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
                        onOpenAccessibilitySettings = { requestAccessibilitySettings() },
                        loadLaunchableApps = { launchableApps() },
                        appVersion = appVersion,
                        initialItem = initialSettingsItem.value
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        OverlayStateStore.isSetupForeground = true
        updatePermissionStates()
        // If onboarding is done and we have permission, ensure service is started
        if (onboardingComplete.value == true && hasOverlayPermission.value) {
            startOverlayService()
        }
    }

    override fun onPause() {
        OverlayStateStore.isSetupForeground = false
        OverlayStateStore.keyCaptureListener = null
        super.onPause()
    }

    private val appVersion: String by lazy {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
    }

    private fun updatePermissionStates() {
        hasOverlayPermission.value = Settings.canDrawOverlays(this)
        val pm = getSystemService(PowerManager::class.java)
        hasBatteryExemption.value = pm.isIgnoringBatteryOptimizations(packageName)
        hasRemapService.value = isRemapServiceEnabled()
    }

    private fun isStepDone(step: OnboardingStep) = when (step) {
        OnboardingStep.BUTTON_REMAP -> hasRemapService.value
        else -> false
    }

    /** Checks the setting rather than OverlayStateStore.isRemapServiceConnected, which lags until the service binds. */
    private fun isRemapServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val component = ButtonRemapService.component(this)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
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

    private fun requestAccessibilitySettings() {
        launchSettingsOrShowAdbHint(
            SettingsScreens.accessibilitySettingsIntents(),
            unavailableMessage = "Accessibility settings aren't available on this device. Enable via ADB:\n" +
                "adb shell settings put secure enabled_accessibility_services " +
                ButtonRemapService.component(this).flattenToString(),
        )
    }

    /** Apps with a TV or phone launcher entry, for the "Launch App" action. */
    private fun launchableApps(): List<LaunchableApp> {
        val pm = packageManager
        return listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
            .flatMap { category -> pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0) }
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != packageName }
            .map { LaunchableApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }
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
