package com.aicontrol.launcher.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.aicontrol.launcher.R
import com.aicontrol.launcher.actions.ActionEngine
import com.aicontrol.launcher.apps.AppInfo
import com.aicontrol.launcher.apps.AppRepository
import com.aicontrol.launcher.assets.AssetStore
import com.aicontrol.launcher.assets.ImageAssetValidation
import com.aicontrol.launcher.icons.IconPackManager
import com.aicontrol.launcher.icons.InstalledIconPack
import com.aicontrol.launcher.theme.ThemeSpec

/** Resolves wallpaper and real installed app icons from the same files/packages the launcher applies. */
internal class ThemePreviewAssets(
    private val context: Context,
    private val engine: ActionEngine,
    private val apps: AppRepository
) {
    private val store = AssetStore(context)
    private val iconPacks = IconPackManager(context)
    private val installedApps by lazy { apps.listLaunchableApps().distinctBy { it.packageName } }

    fun wallpaper(values: ThemeSpec.Values, heightDp: Int): ImageView? {
        val name = values.wallpaperAsset ?: return null
        val file = store.wallpaper(name).takeIf { it.isFile } ?: return null
        val bitmap = runCatching { ImageAssetValidation.decodeSampled(file, 1000) }.getOrNull() ?: return null
        return ImageView(context).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "${values.name} saved wallpaper preview"
            clipToOutline = true
            background = UiTheme.rounded(UiTheme.card2, 12f)
            layoutParams = LinearLayout.LayoutParams(-1, dp(heightDp))
        }
    }

    fun packFor(values: ThemeSpec.Values): InstalledIconPack? {
        val requested = values.iconPackPackage?.takeIf { it.isNotBlank() }
            ?: engine.installedIconPack().takeIf { it.isNotBlank() }
            ?: return null
        return iconPacks.installedPacks().firstOrNull { it.packageName == requested }
    }

    fun packStatus(values: ThemeSpec.Values): String {
        val requested = values.iconPackPackage?.takeIf { it.isNotBlank() }
        val packageName = requested ?: engine.installedIconPack().takeIf { it.isNotBlank() }
        if (packageName == null) return "No compatible app icon pack selected · original installed app icons"
        val pack = iconPacks.installedPacks().firstOrNull { it.packageName == packageName }
        return if (pack != null) {
            val mapped = mappedAppCount(pack)
            if (requested != null) "Theme pack: ${pack.label} · installed · $mapped current apps mapped"
            else "Global pack preview: ${pack.label} · $mapped current apps mapped · not saved with this theme"
        } else "Saved app icon pack is not installed: $packageName"
    }

    /** Shows icons supplied by the saved installed pack; unmapped apps visibly retain their real icons. */
    fun iconStrip(values: ThemeSpec.Values, maxIcons: Int = 5, iconSizeDp: Int = 36): LinearLayout {
        val requested = values.iconPackPackage?.takeIf { it.isNotBlank() }
        val packageName = requested ?: engine.installedIconPack().takeIf { it.isNotBlank() }
        val installedPack = packageName?.let { pack ->
            iconPacks.installedPacks().firstOrNull { it.packageName == pack }
        }
        val previewIcons = installedApps.map { app ->
            val custom = engine.themeIconFile(values.name, app.packageName)?.takeIf { it.isFile }
                ?.let { runCatching { Drawable.createFromPath(it.absolutePath) }.getOrNull() }
            val packDrawable = installedPack?.let {
                iconPacks.iconDrawable(it.packageName, app.packageName, app.activityName)
            }
            val (drawable, source) = when {
                custom != null -> custom to "custom image override"
                packDrawable != null -> packDrawable to "${installedPack.label} icon pack"
                else -> app.icon to "original installed app icon"
            }
            PreviewIcon(app, drawable, source)
        }
        val mapped = previewIcons.filter { it.source.endsWith("icon pack") }
        val display = (mapped + previewIcons.filterNot { it.source.endsWith("icon pack") })
            .distinctBy { it.app.packageName }.take(maxIcons)
        val caption = when {
            requested != null && installedPack == null -> "SAVED PACK UNAVAILABLE · showing original installed app icons"
            installedPack != null && mapped.isEmpty() -> "PACK INSTALLED · ${installedPack.label} does not map these apps; original icons shown"
            installedPack != null && requested != null -> "REAL APP ICON-PACK PREVIEW · ${installedPack.label} · ${mapped.size} mapped"
            installedPack != null -> "GLOBAL PACK PREVIEW · ${installedPack.label} · ${mapped.size} mapped (not saved with this theme)"
            else -> "ORIGINAL INSTALLED APP ICONS · no compatible theme pack selected"
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = caption
                textSize = 8f
                maxLines = 2
                setTextColor(UiTheme.textMuted)
                contentDescription = caption
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(2) })
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(3), dp(2), dp(3), dp(3))
            }
            if (display.isEmpty()) {
                row.addView(TextView(context).apply {
                    text = "No launchable apps available for preview"
                    textSize = 9f
                    setTextColor(UiTheme.textMuted)
                })
            } else display.forEach { item ->
                row.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    val icon = ImageView(context).apply {
                        setImageDrawable(item.drawable)
                        contentDescription = "${item.app.label} app icon · ${item.source}"
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        background = UiTheme.rounded(UiTheme.card2, 10f)
                        setPadding(dp(4), dp(4), dp(4), dp(4))
                        layoutParams = LinearLayout.LayoutParams(dp(iconSizeDp), dp(iconSizeDp))
                    }
                    addView(icon)
                    addView(TextView(context).apply {
                        text = item.app.label
                        textSize = 8f
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        gravity = Gravity.CENTER
                        setTextColor(UiTheme.textMuted)
                        contentDescription = "App label ${item.app.label}"
                    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
                }, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    leftMargin = dp(2)
                    rightMargin = dp(2)
                })
            }
            addView(row)
        }
    }

    /** OpenMoji stays available as optional decorative artwork, never as a replacement app icon. */
    fun illustrationStrip(iconSizeDp: Int = 24): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(context).apply {
            text = "OPTIONAL OPENMOJI ARTWORK · SYMBOL ILLUSTRATIONS ONLY, NOT APP ICONS OR AN ICON PACK"
            textSize = 8f
            setTextColor(UiTheme.textMuted)
            contentDescription = "OpenMoji symbols are decorative illustrations, not application icons or an Android icon pack"
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            listOf(
                "Phone symbol" to R.drawable.openmoji_1f4f1,
                "Camera symbol" to R.drawable.openmoji_1f4f7,
                "Settings symbol" to R.drawable.openmoji_2699,
                "Music symbol" to R.drawable.openmoji_1f3b5
            ).forEach { (label, resource) ->
                addView(ImageView(context).apply {
                    setImageResource(resource)
                    contentDescription = "$label illustration · OpenMoji artwork, not an app icon"
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(iconSizeDp), dp(iconSizeDp)).apply {
                        leftMargin = dp(5)
                        rightMargin = dp(5)
                    }
                })
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
    }

    private data class PreviewIcon(val app: AppInfo, val drawable: Drawable, val source: String)
    private fun mappedAppCount(pack: InstalledIconPack) = installedApps.count { app ->
        iconPacks.hasIconMapping(pack.packageName, app.packageName, app.activityName)
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
