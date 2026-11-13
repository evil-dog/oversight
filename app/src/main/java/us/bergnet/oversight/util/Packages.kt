package us.bergnet.oversight.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

object Packages {
    /** Needs a <queries> entry for [packageName] on Android 11+. */
    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * Packages with an accessibility service turned on, from the ENABLED_ACCESSIBILITY_SERVICES setting
     * (colon-separated components, some in short ".Class" form, so only the package is compared).
     */
    fun enabledAccessibilityPackages(setting: String?): Set<String> =
        setting.orEmpty().split(':')
            .mapNotNull { ComponentName.unflattenFromString(it.trim())?.packageName }
            .toSet()

    fun hasEnabledAccessibilityService(context: Context, packageName: String): Boolean =
        packageName in enabledAccessibilityPackages(
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        )
}
