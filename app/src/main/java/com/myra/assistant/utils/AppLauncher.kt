package com.myra.assistant.utils

import android.content.Context
import android.content.Intent
import java.util.Locale

/**
 * Launches apps by package name and finds a package name from a
 * (partial, case-insensitive) launcher label.
 *
 * Restored from CFR-decompiled bytecode of the user's own MYRA app
 * (com.myra.assistant).
 */
object AppLauncher {

    fun findPackage(context: Context, appName: String): String? {
        val packageManager = context.packageManager
        val activities = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0
        )
        val match = activities.firstOrNull { info ->
            info.loadLabel(packageManager).toString().lowercase(Locale.ROOT)
                .contains(appName.lowercase(Locale.ROOT))
        }
        return match?.activityInfo?.packageName
    }

    fun launch(context: Context, packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // The decompiled code passes the (possibly null) intent straight to
            // startActivity, so a null launch intent throws and launch()
            // returns false; the !! below preserves exactly that behaviour.
            context.startActivity(intent!!)
            true
        } catch (e: Exception) {
            false
        }
    }
}
