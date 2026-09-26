package com.mustfa.heatguard

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/** Jo permission Android app ko khud dene deta hai, woh ek saath maang lo. */
object Perms {

    fun ask(activity: Activity) {
        val need = ArrayList<String>()
        if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (activity.checkSelfPermission(Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            need.add(Manifest.permission.CAMERA)
        }
        if (need.isNotEmpty()) {
            activity.requestPermissions(need.toTypedArray(), 77)
        }
        if (!usageGranted(activity)) {
            val prefs = activity.getSharedPreferences("heatguard_perms", Activity.MODE_PRIVATE)
            if (!prefs.getBoolean("usage_opened", false)) {
                prefs.edit().putBoolean("usage_opened", true).apply()
                runCatching {
                    activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun usageGranted(activity: Activity): Boolean {
        val appOps = activity.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = runCatching {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                activity.packageName
            )
        }.getOrDefault(AppOpsManager.MODE_ERRORED)
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
