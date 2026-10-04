package com.aicontrol.launcher.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.ScrollView
import android.widget.TextView

internal object AssetSourcesDialog {
    fun show(activity: Activity) {
        val content = """
            <p><b>Wallpapers</b><br>
            Bundled presets use CC0 artwork. Generated bundles search Wikimedia Commons, verify the
            individual file's license and creator, then save the image and attribution before preview.
            If Commons is unavailable, the app uses an included CC0 image and reports that fallback.<br>
            <a href="https://commons.wikimedia.org/wiki/Special:MediaSearch?type=image&amp;search=abstract%20wallpaper">Browse Wikimedia Commons</a> ·
            <a href="https://commons.wikimedia.org/w/api.php">Commons API</a></p>

            <p><b>App icons</b><br>
            Common app labels map locally to original OpenMoji PNG symbols. The images are licensed
            <a href="https://creativecommons.org/licenses/by-sa/4.0/">CC BY-SA 4.0</a>; the app records
            glyph-level source and license details and shows attribution in theme previews.<br>
            <a href="https://github.com/hfg-gmuend/openmoji">Official OpenMoji repository</a></p>

            <p><b>Icon packs</b><br>
            OpenMoji files are visual PNG assets, not installable Android icon packs. Compatible
            Nova/ADW packs may be selected only when already installed on this device. The app does
            not download or install third-party APKs.</p>
        """.trimIndent()
        val message = TextView(activity).apply {
            text = Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY)
            movementMethod = LinkMovementMethod.getInstance()
            setLinkTextColor(UiTheme.accent)
            setTextColor(UiTheme.textPrimary)
            textSize = 14f
            setPadding(0, dp(activity, 8), 0, dp(activity, 8))
            setBackgroundColor(Color.TRANSPARENT)
        }
        AlertDialog.Builder(activity)
            .setTitle("Asset sources and licenses")
            .setView(ScrollView(activity).apply { addView(message) })
            .setPositiveButton("Done", null)
            .show()
    }

    private fun dp(activity: Activity, value: Int) =
        (value * activity.resources.displayMetrics.density).toInt()
}
