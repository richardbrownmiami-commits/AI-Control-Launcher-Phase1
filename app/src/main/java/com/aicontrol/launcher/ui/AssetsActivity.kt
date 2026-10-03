package com.aicontrol.launcher.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.aicontrol.launcher.actions.ActionEngine
import com.aicontrol.launcher.assets.AssetAttribution
import com.aicontrol.launcher.assets.AssetCatalog
import com.aicontrol.launcher.assets.AssetImporter
import com.aicontrol.launcher.assets.CreativeCommonsAssetSearch
import com.aicontrol.launcher.assets.ImageAssetValidation
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
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class AssetsActivity : Activity() {
    companion object {
        const val EXTRA_SUGGESTED_QUERY = "suggested_wallpaper_query"
        private const val REQUEST_IMPORT_IMAGE = 5401
    }

    private lateinit var catalog: AssetCatalog
    private lateinit var manager: LauncherAssetManager
    private lateinit var list: LinearLayout
    private lateinit var searchProgress: ProgressBar
    private lateinit var searchProgressLabel: TextView
    private lateinit var searchRow: LinearLayout
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiTheme.bind(ActionEngine(this))
        catalog = AssetCatalog(this)
        manager = LauncherAssetManager(this)
        buildUi()
        intent.getStringExtra(EXTRA_SUGGESTED_QUERY)?.takeIf { it.isNotBlank() }?.let { searchWallpapers(it) }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = UiTheme.background()
        }
        root.addView(TextView(this).apply {
            text = "Wallpaper & assets"
            textSize = 24f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(UiTheme.textPrimary)
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
            text = "Open-license search runs only when you request it. Each result shows creator, license, and source before download. Local images are copied into app-private storage. PNG icon sets here are image assets, not installable APK packs; compatible Nova/ADW packs are installed Android apps managed in Launcher settings. Widgets are provided by installed apps."
            textSize = 11f
            setTextColor(UiTheme.textMuted)
            setPadding(0, dp(8), 0, dp(8))
        })
        searchRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = UiTheme.rounded(UiTheme.card2, 14f, UiTheme.accent, 1)
            visibility = View.GONE
        }
        searchProgress = ProgressBar(this).apply { isIndeterminate = true }
        searchProgressLabel = TextView(this).apply {
            text = "Searching Wikimedia Commons…"
            textSize = 11f
            setTextColor(UiTheme.textPrimary)
            setPadding(dp(9), 0, 0, 0)
        }
        searchRow.addView(searchProgress, LinearLayout.LayoutParams(dp(22), dp(22)))
        searchRow.addView(searchProgressLabel)
        root.addView(searchRow, LinearLayout.LayoutParams(-1, dp(38)).apply { bottomMargin = dp(5) })
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
        searchProgressLabel.text = "Searching Wikimedia Commons…"
        searchRow.visibility = View.VISIBLE
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
            } finally {
                searchRow.visibility = View.GONE
            }
        }
    }

    private fun showCandidates(results: List<WallpaperCandidate>) {
        val cards = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(6))
        }
        results.forEach { candidate ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), dp(10), dp(13), dp(10))
                background = UiTheme.rounded(UiTheme.card, 17f, UiTheme.accent, 1)
            }
            card.addView(TextView(this).apply {
                text = candidate.title
                textSize = 14f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setTextColor(UiTheme.textPrimary)
            })
            card.addView(TextView(this).apply {
                text = "${candidate.license}  ·  ${candidate.creator}"
                textSize = 10f
                setTextColor(UiTheme.textMuted)
                setPadding(0, dp(3), 0, dp(5))
            })
            val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            actions.addView(actionButton("Source & license") {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(candidate.pageUrl))) }
                    .onFailure { Toast.makeText(this@AssetsActivity, "Could not open the Commons source page", Toast.LENGTH_LONG).show() }
            }, LinearLayout.LayoutParams(0, dp(38), 1f))
            actions.addView(actionButton("Review & download") { reviewCandidate(candidate) },
                LinearLayout.LayoutParams(0, dp(38), 1f).apply { leftMargin = dp(5) })
            card.addView(actions)
            cards.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        }
        val scroll = ScrollView(this).apply { addView(cards) }
        AlertDialog.Builder(this)
            .setTitle("Reuse-cleared results · ${results.size}")
            .setView(scroll)
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
        val canceled = AtomicBoolean(false)
        val details = TextView(this).apply {
            text = "${candidate.title}\n${candidate.license} · ${candidate.creator}\n\nConnecting to Wikimedia Commons…"
            textSize = 12f
            setTextColor(UiTheme.textPrimary)
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            max = 100
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(details)
            addView(progress, LinearLayout.LayoutParams(-1, dp(40)))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Downloading licensed wallpaper")
            .setView(body)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                canceled.set(true)
                details.text = "Canceling download… The partial file will be discarded."
                it.isEnabled = false
            }
        }
        dialog.show()
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    manager.downloadLicensedWallpaper(candidate, { !canceled.get() }) { downloaded, total ->
                        runOnUiThread {
                            if (!canceled.get() && dialog.isShowing) {
                                if (total > 0) {
                                    progress.isIndeterminate = false
                                    progress.progress = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                                    details.text = "${candidate.title}\n${candidate.license} · ${candidate.creator}\n\n${formatBytes(downloaded)} of ${formatBytes(total)}"
                                } else details.text = "${candidate.title}\n${candidate.license} · ${candidate.creator}\n\n${formatBytes(downloaded)} downloaded…"
                            }
                        }
                    }.getOrThrow()
                }
                if (canceled.get()) return@launch
                dialog.dismiss()
                render()
                showDownloadedPreview(file, candidate)
            } catch (error: Exception) {
                if (error is CancellationException && !canceled.get()) throw error
                dialog.dismiss()
                val message = if (canceled.get()) "Download canceled. No partial image was saved." else error.message ?: "Wallpaper download failed."
                AlertDialog.Builder(this@AssetsActivity).setTitle(if (canceled.get()) "Download canceled" else "Download failed")
                    .setMessage(message).setPositiveButton(android.R.string.ok, null).show()
            }
        }
    }

    private fun showDownloadedPreview(file: File, candidate: WallpaperCandidate) {
        val preview = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(6), dp(18), dp(8))
        }
        val bitmap = ImageAssetValidation.decodeSampled(file, 1200)
        if (bitmap != null) preview.addView(ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Preview of ${candidate.title}"
        }, LinearLayout.LayoutParams(-1, dp(190)))
        preview.addView(TextView(this).apply {
            text = "${candidate.title}\n\nCreator: ${candidate.creator}\nLicense: ${candidate.license}\nLicense details: ${candidate.licenseUrl}\nSource: ${candidate.pageUrl}\n\nChoose where to use this downloaded image. Applying inside the launcher does not alter Android's device wallpaper."
            textSize = 11f
            setTextColor(UiTheme.textPrimary)
            setPadding(0, dp(8), 0, 0)
        })
        val dialog = AlertDialog.Builder(this)
            .setTitle("Preview downloaded wallpaper")
            .setView(preview)
            .setNegativeButton("Keep in library", null)
            .setNeutralButton("Device wallpaper…", null)
            .setPositiveButton("Set launcher background", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                manager.setLauncherWallpaper(file).onSuccess {
                    Toast.makeText(this, "Launcher background updated successfully", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }.onFailure {
                    Toast.makeText(this, it.message ?: "Launcher background was not changed", Toast.LENGTH_LONG).show()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss()
                confirmDeviceWallpaper(file)
            }
        }
        dialog.show()
    }

    private fun formatBytes(bytes: Long): String = String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))

    private fun render() {
        list.removeAllViews()
        section("Wallpapers", catalog.listWallpapers(), true)
        section("Images", catalog.listImages(), false)
        section("Icon overrides", catalog.listIconOverrides(), false)
        section("Local PNG icon sets", catalog.listIconPacks(), true)
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
                            "Local PNG icon sets" -> catalog.deleteIconPack(file.name)
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
