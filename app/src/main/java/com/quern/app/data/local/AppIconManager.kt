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
    fun applySavedIcon(context: Context, selected: AppIconOption) {
        val pm = context.packageManager
        val pkg = context.packageName
        val target = ComponentName(pkg, selected.aliasName)
        // Enable the target before changing any other launcher entry.
        if (!pm.isAliasEnabled(target)) {
            pm.setComponentEnabledSetting(
                target,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
        AppIconOption.entries.forEach { option ->
            if (option != selected) {
                val alias = ComponentName(pkg, option.aliasName)
                if (pm.isAliasEnabled(alias)) {
                    pm.setComponentEnabledSetting(
                        alias,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        }
    }

    private fun PackageManager.isAliasEnabled(alias: ComponentName): Boolean =
        when (getComponentEnabledSetting(alias)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ->
                getActivityInfo(alias, PackageManager.MATCH_DISABLED_COMPONENTS).enabled
            else -> false
        }
}
