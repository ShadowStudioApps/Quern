package com.quern.app.data.local

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.quern.app.domain.model.AppIconOption

/**
 * Dynamically changes the external Android Phone & Android TV launcher icon + TV banner
 * using PackageManager activity-alias switching.
 */
object AppIconManager {
    fun applyIcon(context: Context, selected: AppIconOption) {
        val pm = context.packageManager
        val pkg = context.packageName
        // Enable target first so the app always has an active launcher entry
        pm.setComponentEnabledSetting(
            ComponentName(pkg, selected.aliasName),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        AppIconOption.entries.filter { it != selected }.forEach { other ->
            pm.setComponentEnabledSetting(
                ComponentName(pkg, other.aliasName),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
