package com.aicontrol.launcher.icons

import android.app.Activity
import android.content.Intent
import android.net.Uri

/** Opens an official pack details page; installation is explicitly left to the user and Android. */
object IconPackStore {
    fun openAppstract(activity: Activity): Result<Unit> = runCatching {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(IconPackCompatibility.APPSTRACT_FDROID_URL)))
    }

    /** Backwards-compatible browse entrypoint; never downloads or installs an icon-pack APK. */
    fun open(activity: Activity): Result<Unit> = openAppstract(activity)
}
