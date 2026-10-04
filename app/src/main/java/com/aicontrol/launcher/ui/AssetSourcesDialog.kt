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

            <p><b>Real Android app icon packs</b><br>
            Themes can use installed app-filter packs. The recommended open-source option is Appstract
            (<code>dev.appstract.iconpack</code>), which lists nearly 590 icons and support for Nova,
            Lawnchair, ADW, Apex, Action and other launchers. Compatible mappings are read from the
            installed pack and applied only to apps it maps. The launcher never downloads or silently
            installs an APK; installation remains a user action in F-Droid.<br>
            <a href="https://f-droid.org/en/packages/dev.appstract.iconpack/">Appstract on F-Droid</a> ·
            <a href="https://github.com/yangchoo/Appstract">Official Appstract source</a> ·
            <a href="https://www.apache.org/licenses/LICENSE-2.0">Apache License 2.0</a></p>

            <p><b>Optional OpenMoji artwork</b><br>
            OpenMoji PNGs are decorative symbol illustrations, <b>not app icons and not an installable
            icon pack</b>. They are licensed <a href="https://creativecommons.org/licenses/by-sa/4.0/">CC BY-SA 4.0</a>
            and retain glyph-level source/creator attribution.<br>
            <a href="https://github.com/hfg-gmuend/openmoji">Official OpenMoji repository</a> ·
            <a href="https://creativecommons.org/licenses/by-sa/4.0/">CC BY-SA 4.0 license</a></p>
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
