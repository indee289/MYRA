package com.myra.assistant.managers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Locale

/**
 * Restored from CFR-decompiled bytecode (decompiled-java/.../managers/AppLaunchManager.java).
 */
class AppLaunchManager(private val context: Context) {

    /**
     * Finds the package name of an installed app by its display (label) name.
     * Two passes over the installed apps, exactly as decompiled:
     *  1. exact match on the label, lower-cased (Locale.ROOT) and trimmed;
     *  2. label *contains* the (lower-cased, trimmed) query.
     * Returns null when no installed app matches.
     */
    fun getPackageName(appName: String): String? {
        val packageManager = context.packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val query = appName.lowercase(Locale.ROOT).trim()
        for (app in installedApps) {
            val label = packageManager.getApplicationLabel(app).toString()
                .lowercase(Locale.ROOT).trim()
            if (label == query) {
                return app.packageName
            }
        }
        for (app in installedApps) {
            val label = packageManager.getApplicationLabel(app).toString()
                .lowercase(Locale.ROOT).trim()
            if (label.contains(query)) {
                return app.packageName
            }
        }
        return null
    }

    /**
     * Launches the app with the given package name via its launch intent
     * (FLAG_ACTIVITY_NEW_TASK). Returns true if a launch intent existed
     * and the app was started, false otherwise.
     */
    fun openApp(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (intent != null) {
            context.startActivity(intent)
            true
        } else {
            false
        }
    }
}
