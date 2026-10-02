package com.aicontrol.launcher.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.aicontrol.launcher.assets.AssetAttribution
import com.aicontrol.launcher.assets.AssetCatalog
import com.aicontrol.launcher.assets.AssetImporter
import com.aicontrol.launcher.assets.CreativeCommonsAssetSearch
import com.aicontrol.launcher.assets.LauncherAssetManager
import com.aicontrol.launcher.assets.WallpaperCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AssetsActivity : Activity() {
    companion object {
        const val EXTRA_SUGGESTED_QUERY = "suggested_wallpaper_query"
        private const val REQUEST_IMPORT_IMAGE = 5401
    }

    private lateinit var catalog: AssetCatalog
    private lateinit var manager: LauncherAssetManager
    private lateinit var list: LinearLayout
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        catalog = AssetCatalog(this)
        manager = LauncherAssetManager(this)
        buildUi()
        intent.getStringExtra(EXTRA_SUGGESTED_QUERY)?.takeIf { it.isNotBlank() }?.let { searchWallpapers(it) }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = UiTheme.gradient(0f)
        }
        root.addView(TextView(this).apply {
            text = "Launcher Assets"
            textSize = 24f
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "Your private library. Choose an image from Downloads, or search Commons for reusable wallpaper."
            setTextColor(UiTheme.textMuted)
            setPadding(0, dp(6), 0, dp(10))
        })
        val buttons = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        buttons.addView(actionButton("Import from Downloads") { pickLocalImage() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        buttons.addView(actionButton("Find free wallpaper") {
            val query = intent.getStringExtra(EXTRA_SUGGESTED_QUERY) ?: "abstract colorful geometric wallpaper"
            askWallpaperQuery(query)
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(8) })
        root.addView(buttons)
        root.addView(TextView(this).apply {
            text = "Open-license search runs only when you request it. Each result shows its creator, license, and source before download. Local images are copied into app-private storage."
            textSize = 11f
            setTextColor(UiTheme.textMuted)
            setPadding(0, dp(8), 0, dp(8))
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) render()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 11f
        setTextColor(UiTheme.textPrimary)
        background = UiTheme.rounded(UiTheme.card, 16f, UiTheme.accent, 1)
        setOnClickListener { action() }
    }

    private fun pickLocalImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_IMPORT_IMAGE)
    }

    @Deprecated("The file picker result API is retained for this API-30-only launcher.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMPORT_IMAGE && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            Toast.makeText(this, "Importing image…", Toast.LENGTH_SHORT).show()
            scope.launch {
                try {
                    val file = withContext(Dispatchers.IO) { AssetImporter(this@AssetsActivity).importWallpaper(uri) }
                    render()
                    showImportedPreview(file)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Toast.makeText(this@AssetsActivity, error.message ?: "Could not import image", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showImportedPreview(file: File) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        var sample = 1
        while (options.outWidth / sample > 1200 || options.outHeight / sample > 1200) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        val preview = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        if (bitmap != null) preview.addView(ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Preview of imported wallpaper"
        }, LinearLayout.LayoutParams(-1, dp(180)))
        preview.addView(TextView(this).apply {
            text = "${file.name}\nCopied to this app's private wallpaper library. Setting it here changes only this launcher's background."
            setTextColor(UiTheme.textPrimary)
            setPadding(0, dp(8), 0, 0)
        })
        val dialog = AlertDialog.Builder(this)
            .setTitle("Preview local image")
            .setView(preview)
            .setPositiveButton("Use in launcher", null)
            .setNegativeButton("Keep in library", null)
            .setNeutralButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                manager.setLauncherWallpaper(file).onSuccess {
                    Toast.makeText(this, "Launcher background updated", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }.onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }
            }
        }
        dialog.show()
    }

    private fun askWallpaperQuery(defaultQuery: String) {
        val input = EditText(this).apply {
            setSingleLine()
            setText(defaultQuery)
            setSelection(text.length)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            hint = "e.g. abstract red blue geometric wallpaper"
        }
        AlertDialog.Builder(this)
            .setTitle("Search reusable wallpaper")
            .setMessage("Search terms are sent only to Wikimedia Commons. Character artwork is not searched or bundled; try an abstract color/pattern description.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Search", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val query = input.text.toString().trim()
                        dialog.dismiss()
                        searchWallpapers(query)
                    }
                }
                dialog.show()
            }
    }

    private fun searchWallpapers(query: String) {
        Toast.makeText(this, "Searching Wikimedia Commons…", Toast.LENGTH_SHORT).show()
        scope.launch {
            try {
                val results = withContext(Dispatchers.IO) { CreativeCommonsAssetSearch().search(query) }
                if (results.isEmpty()) {
                    AlertDialog.Builder(this@AssetsActivity)
                        .setTitle("No reusable images found")
                        .setMessage("No supported JPEG, PNG, or WebP results with an approved reuse license were returned. Try a broader abstract search.")
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                } else showCandidates(results)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                AlertDialog.Builder(this@AssetsActivity)
                    .setTitle("Search unavailable")
                    .setMessage(error.message ?: "Unable to reach Wikimedia Commons.")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    private fun showCandidates(results: List<WallpaperCandidate>) {
        val labels = results.map { "${it.title}\n${it.license} · ${it.creator}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Reusable wallpapers (${results.size})")
            .setItems(labels) { _, which -> reviewCandidate(results[which]) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun reviewCandidate(candidate: WallpaperCandidate) {
        val licenseLink = candidate.licenseUrl.ifBlank { "License URL not supplied by Commons metadata" }
        val message = "${candidate.title}\n\nCreator: ${candidate.creator}\nLicense: ${candidate.license}\nLicense details: $licenseLink\nSource: ${candidate.pageUrl}\n\nDownloading saves a copy in Assets and preserves this attribution. It will not change your device wallpaper."
        AlertDialog.Builder(this)
            .setTitle("Review license and source")
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Download") { _, _ -> downloadCandidate(candidate) }
            .show()
    }

    private fun downloadCandidate(candidate: WallpaperCandidate) {
        Toast.makeText(this, "Downloading wallpaper…", Toast.LENGTH_SHORT).show()
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { manager.downloadLicensedWallpaper(candidate).getOrThrow() }
                render()
                AlertDialog.Builder(this@AssetsActivity)
                    .setTitle("Wallpaper saved")
                    .setMessage("${candidate.title}\n\nLicense: ${candidate.license}\nCreator: ${candidate.creator}\nSource: ${candidate.pageUrl}\n\nSet as this launcher's background? The device wallpaper will not be changed.")
                    .setNegativeButton("Not now", null)
                    .setPositiveButton("Use in launcher") { _, _ ->
                        manager.setLauncherWallpaper(file)
                            .onSuccess { Toast.makeText(this@AssetsActivity, "Launcher background updated", Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(this@AssetsActivity, it.message, Toast.LENGTH_LONG).show() }
                    }
                    .show()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Toast.makeText(this@AssetsActivity, error.message ?: "Wallpaper download failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun render() {
        list.removeAllViews()
        section("Wallpapers", catalog.listWallpapers(), true)
        section("Images", catalog.listImages(), false)
        section("Icon overrides", catalog.listIconOverrides(), false)
        section("Icon packs", catalog.listIconPacks(), true)
        section("Custom themes", catalog.listThemes(), false)
        section("Styles", catalog.listStyles(), false)
    }

    private fun section(title: String, files: List<File>, canApply: Boolean) {
        list.addView(TextView(this).apply {
            text = "$title (${files.size})"
            textSize = 18f
            setTextColor(UiTheme.accent)
            setPadding(0, dp(14), 0, dp(6))
        })
        files.forEach { file ->
            val line = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(6), dp(6), dp(6))
                UiTheme.styleCard(this, UiTheme.card, true)
            }
            val attribution: AssetAttribution? = if (title == "Wallpapers") catalog.wallpaperAttribution(file.name) else null
            line.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@AssetsActivity).apply {
                    text = file.name
                    setTextColor(UiTheme.textPrimary)
                    maxLines = 1
                })
                if (attribution != null) addView(TextView(this@AssetsActivity).apply {
                    text = "${attribution.license} · ${attribution.creator}\nSource: ${attribution.sourceUrl}"
                    textSize = 9f
                    maxLines = 3
                    setTextColor(UiTheme.textMuted)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            if (canApply && title == "Wallpapers") {
                line.addView(actionButton("Use here") { useWallpaper(file) })
                line.addView(actionButton("Device") { confirmDeviceWallpaper(file) }.apply {
                    layoutParams = LinearLayout.LayoutParams(-2, dp(42)).apply { leftMargin = dp(3) }
                })
            } else if (canApply) line.addView(actionButton("Apply") {
                manager.applyIconPack(file.name).onFailure { Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show() }
                render()
            })
            line.addView(actionButton("Delete") {
                AlertDialog.Builder(this@AssetsActivity)
                    .setTitle("Delete ${file.name}?")
                    .setMessage("This removes the local asset from this launcher's private library.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        val ok = when (title) {
                            "Wallpapers" -> catalog.deleteWallpaper(file.name)
                            "Images" -> catalog.deleteImage(file.name)
                            "Icon overrides" -> catalog.deleteIconOverride(file.name)
                            "Icon packs" -> catalog.deleteIconPack(file.name)
                            "Styles" -> catalog.deleteStyle(file.name)
                            else -> catalog.deleteTheme(file.name)
                        }
                        if (!ok) Toast.makeText(this@AssetsActivity, "Delete failed", Toast.LENGTH_SHORT).show()
                        render()
                    }.show()
            }.apply { layoutParams = LinearLayout.LayoutParams(-2, dp(42)).apply { leftMargin = dp(3) } })
            list.addView(line)
        }
    }

    private fun useWallpaper(file: File) {
        manager.setLauncherWallpaper(file).onSuccess {
            Toast.makeText(this, "Launcher background updated", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }
    }

    private fun confirmDeviceWallpaper(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Change device wallpaper?")
            .setMessage("This changes Android's system wallpaper for the device, not just this launcher. Continue with ${file.name}?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Change device wallpaper") { _, _ ->
                manager.applyWallpaper(file).onSuccess {
                    Toast.makeText(this, "Device wallpaper changed", Toast.LENGTH_SHORT).show()
                }.onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }
            }.show()
    }
}
