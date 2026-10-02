package com.aicontrol.launcher.apps

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps

class AppRepository(private val context: Context) {
    fun listLaunchableApps(): List<AppInfo> {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val user = android.os.Process.myUserHandle()
        return launcherApps.getActivityList(null, user).map {
            AppInfo(it.label?.toString() ?: it.componentName.packageName, it.componentName.packageName, it.componentName.className, it.getBadgedIcon(0))
        }.distinctBy { it.packageName + "/" + it.activityName }.sortedWith(
            compareBy<AppInfo> { it.label.lowercase() }.thenBy { it.packageName }.thenBy { it.activityName }
        )
    }
    fun launch(app: AppInfo) {
        val intent = Intent().apply { component = android.content.ComponentName(app.packageName, app.activityName); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }
}
