package com.aicontrol.launcher.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.aicontrol.launcher.actions.ActionEngine
import com.aicontrol.launcher.apps.AppRepository
import com.aicontrol.launcher.assets.AssetStore
import com.aicontrol.launcher.assets.BundledOpenMojiCatalog
import com.aicontrol.launcher.assets.ImageAssetValidation
import com.aicontrol.launcher.assets.OpenMojiIconLibrary
import com.aicontrol.launcher.assets.OpenMojiIconMatcher
import com.aicontrol.launcher.theme.ThemeSpec

/** Resolves preview images from the same persistent files the launcher itself applies. */
internal class ThemePreviewAssets(
    private val context: Context,
    private val engine: ActionEngine,
    private val apps: AppRepository
) {
    private val store = AssetStore(context)
    private val openMoji = OpenMojiIconLibrary(context)
    private val installedApps by lazy { apps.listLaunchableApps().distinctBy { it.packageName } }

    fun bundledIconFile(label: String, themeName: String) = openMoji.bundledIconFile(label, themeName)

    fun wallpaper(values: ThemeSpec.Values, heightDp: Int): ImageView? {
        val name = values.wallpaperAsset ?: return null
        val file = store.wallpaper(name).takeIf { it.isFile } ?: return null
        val bitmap = runCatching { ImageAssetValidation.decodeSampled(file, 1000) }.getOrNull() ?: return null
        return ImageView(context).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "${values.name} Commons wallpaper preview"
            clipToOutline = true
            background = UiTheme.rounded(UiTheme.card2, 12f)
            layoutParams = LinearLayout.LayoutParams(-1, dp(heightDp))
        }
    }

    fun iconStrip(values: ThemeSpec.Values, maxIcons: Int = 5, iconSizeDp: Int = 36): LinearLayout {
        val labelsByPackage = installedApps.associate { it.packageName to it.label }
        val savedIcons = values.iconAssets.keys.mapNotNull { packageName ->
            val file = engine.themeIconFile(values.name, packageName)?.takeIf { it.isFile } ?: return@mapNotNull null
            val label = labelsByPackage[packageName] ?: packageName.substringAfterLast('.')
            PreviewIcon(label, file)
        }.take(maxIcons)

        val automaticIcons = if (savedIcons.isEmpty()) {
            val assignments = OpenMojiIconMatcher.assign(
                BundledOpenMojiCatalog.glyphs,
                installedApps.map { it.packageName to it.label },
                values.name
            )
            assignments.take(maxIcons).mapNotNull { assignment ->
                openMoji.bundledIconFile(assignment.appLabel, values.name)
                    ?.let { PreviewIcon(assignment.appLabel, it) }
            }
        } else emptyList()

        val samples = if (savedIcons.isEmpty() && automaticIcons.isEmpty()) {
            listOf("Phone", "Messages", "Camera", "Settings", "Files").take(maxIcons).mapNotNull { label ->
                openMoji.bundledIconFile(label, values.name)?.let { PreviewIcon(label, it) }
            }
        } else emptyList()
        val visible = (savedIcons + automaticIcons + samples).take(maxIcons)

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(4), dp(3), dp(3))
            visible.forEach { item ->
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    val icon = ImageView(context).apply {
                        val drawable: Drawable? = runCatching { Drawable.createFromPath(item.file.absolutePath) }.getOrNull()
                        setImageDrawable(drawable)
                        contentDescription = "${item.label} OpenMoji app icon preview"
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        background = UiTheme.rounded(UiTheme.card2, 10f)
                        setPadding(dp(5), dp(5), dp(5), dp(5))
                        layoutParams = LinearLayout.LayoutParams(dp(iconSizeDp), dp(iconSizeDp))
                    }
                    addView(icon)
                    addView(TextView(context).apply {
                        text = item.label
                        textSize = 8f
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        gravity = Gravity.CENTER
                        setTextColor(UiTheme.textMuted)
                        contentDescription = "App icon label ${item.label}"
                    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
                }, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    leftMargin = dp(2)
                    rightMargin = dp(2)
                })
            }
        }
    }

    private data class PreviewIcon(val label: String, val file: java.io.File)
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
