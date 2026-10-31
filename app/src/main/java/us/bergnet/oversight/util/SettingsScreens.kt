package us.bergnet.oversight.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** System settings screens used to grant OverSight's permissions, and whether this device really has them. */
object SettingsScreens {
    fun overlayPermissionIntents(context: Context) = listOf(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
    )

    fun batteryExemptionIntents(context: Context) = listOf(
        @Suppress("BatteryLife")
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )

    fun accessibilitySettingsIntents() = listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    /**
     * The first candidate whose settings screen really exists. Many Android TV builds don't ship
     * these screens at all, and Google TV routes some of them to TvSettings' EmptyStubActivity,
     * which closes immediately so the request appears to do nothing. Resolving requires the
     * manifest's `<queries>` entries.
     */
    fun usableIntent(context: Context, candidates: List<Intent>): Intent? = candidates.firstOrNull { intent ->
        val target = context.packageManager.resolveActivity(intent, 0)?.activityInfo
        target != null && !target.name.endsWith("EmptyStubActivity")
    }

    fun canRequestBatteryExemption(context: Context) =
        usableIntent(context, batteryExemptionIntents(context)) != null
}
