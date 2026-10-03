package com.aicontrol.launcher.icons

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri

/** Opens an official store search; this app never downloads or installs icon-pack APKs. */
object IconPackStore {
    private const val MARKET_SEARCH = "market://search?q=icon%20pack"
    private const val PLAY_SEARCH = "https://play.google.com/store/search?q=icon%20pack&c=apps"

    fun open(activity: Activity): Result<Unit> {
        val marketResult = runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MARKET_SEARCH)))
        }
        if (marketResult.isSuccess) return marketResult
        return runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_SEARCH)))
        }.recoverCatching { webError ->
            val marketError = marketResult.exceptionOrNull()
            throw IllegalStateException(
                "Could not open Google Play or a browser for the official icon-pack search: " +
                    (marketError?.message ?: webError.message ?: "no activity handled the link"),
                webError
            )
        }
    }
}
