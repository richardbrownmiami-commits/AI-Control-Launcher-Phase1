package com.aicontrol.launcher.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.aicontrol.launcher.actions.ActionEngine
import com.aicontrol.launcher.assets.AssetAttribution
import com.aicontrol.launcher.assets.AssetCatalog
import com.aicontrol.launcher.assets.AssetImporter
import com.aicontrol.launcher.assets.AssetAttributionStore
import com.aicontrol.launcher.assets.AssetStore
import com.aicontrol.launcher.assets.CreativeCommonsAssetSearch
import com.aicontrol.launcher.assets.ImageAssetValidation
import com.aicontrol.launcher.assets.LauncherAssetManager
import com.aicontrol.launcher.assets.LocalWallpaperMatcher
import com.aicontrol.launcher.assets.ThemeBundleBuilder
import com.aicontrol.launcher.assets.ThemeBundleBuildResult
import com.aicontrol.launcher.assets.WallpaperCandidate
import com.aicontrol.launcher.apps.AppRepository
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
        const val EXTRA_THEME_NAME = "theme_bundle_target_name"
        private const val REQUEST_IMPORT_IMAGE = 5401
    }

    private lateinit var catalog: AssetCatalog
    private lateinit var manager: LauncherAssetManager
    private lateinit var engine: ActionEngine
    private lateinit var appRepository: AppRepository
    private lateinit var list: LinearLayout
    private lateinit var searchProgress: ProgressBar
    private lateinit var searchProgressLabel: TextView
    private lateinit var searchRow: LinearLayout
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var buildCancelButton: Button? = null
    private val buildContinue = AtomicBoolean(true)
    private var pendingThemeName: String? = null
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = ActionEngine(this)
        UiTheme.bind(engine)
        catalog = AssetCatalog(this)
        manager = LauncherAssetManager(this)
        appRepository = AppRepository(this)
        pendingThemeName = intent.getStringExtra(EXTRA_THEME_NAME)?.takeIf { it.isNotBlank() }
        buildUi()
        intent.getStringExtra(EXTRA_SUGGESTED_QUERY)?.takeIf { it.isNotBlank() }?.let { query ->
            if (pendingThemeName != null) buildThemeBundle(query) else searchWallpapers(query)
        }
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
            text = pendingThemeName?.let { "Building ‘$it’ · searching reusable wallpaper and matching icon art. Your local Downloads remain untouched unless you explicitly choose a file." }
                ?: "Your private library. Choose an image from Downloads, or search Commons for reusable wallpaper."
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
        if (pendingThemeName != null) {
            root.addView(actionButton("Find open-license art for an app icon") {
                val query = com.aicontrol.launcher.theme.ThemeSpec.suggestedWallpaperQuery(pendingThemeName!!)
                    .replace("wallpaper", "minimal app icon")
                askWallpaperQuery(query)
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(5) })
            root.addView(TextView(this).apply {
                text = "AI builds from Commons CC-approved images and OpenMoji CC BY-SA icons after your plan confirmation. Local images are optional and stay on-device. No APKs are downloaded."
                textSize = 10f
                setTextColor(UiTheme.textMuted)
                setPadding(dp(3), dp(5), dp(3), dp(4))
            })
        }
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
        searchRow.addView(searchProgressLabel, LinearLayout.LayoutParams(0, -2, 1f))
        buildCancelButton = Button(this).apply {
            text = "Cancel"
            textSize = 9f
            setTextColor(UiTheme.textPrimary)
            background = UiTheme.rounded(UiTheme.card, 12f)
            visibility = View.GONE
            setOnClickListener {
                buildContinue.set(false)
                searchProgressLabel.text = "Canceling after the current safe operation…"
                isEnabled = false
            }
        }
        searchRow.addView(buildCancelButton, LinearLayout.LayoutParams(-2, dp(34)))
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
        buildContinue.set(false)
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
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_IMPORT_IMAGE)
    }

    @Deprecated("The file picker result API is retained for this API-30-only launcher.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMPORT_IMAGE && resultCode == RESULT_OK) {
            val uris = buildList {
                data?.clipData?.let { clip ->
                    for (index in 0 until minOf(clip.itemCount, 6)) clip.getItemAt(index).uri?.let(::add)
                }
                if (isEmpty()) data?.data?.let(::add)
            }
            if (uris.isEmpty()) return
            Toast.makeText(this, "Importing image…", Toast.LENGTH_SHORT).show()
            scope.launch {
                try {
                    val files = withContext(Dispatchers.IO) {
                        val importer = AssetImporter(this@AssetsActivity)
                        uris.map { importer.importWallpaper(it) }
                    }
                    render()
                    if (files.size > 1 && pendingThemeName != null) chooseLocalThemeWallpaper(files)
                    else if (files.size == 1) showImportedPreview(files.single())
                    else Toast.makeText(this@AssetsActivity, "${files.size} selected images were validated and saved locally.", Toast.LENGTH_LONG).show()
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Toast.makeText(this@AssetsActivity, error.message ?: "Could not import image", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun chooseLocalThemeWallpaper(files: List<File>) {
        val themeName = pendingThemeName ?: return
        val values = engine.themeValues(themeName)
        val keywords = themeName.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 2 && it !in setOf("theme", "wallpaper", "background") }.toSet()
        scope.launch {
            val ranked = withContext(Dispatchers.IO) {
                files.map { file ->
                    val filenameScore = keywords.count { it in file.name.lowercase(Locale.ROOT) }
                    val paletteScore = LocalWallpaperMatcher.paletteMatchPercent(file, values.accent, values.accent2)
                    Triple(file, filenameScore, paletteScore)
                }.sortedByDescending { it.second * 100 + it.third }
            }
            val best = ranked.firstOrNull() ?: return@launch
            val nextScore = ranked.getOrNull(1)?.let { it.second * 100 + it.third }
            val bestScore = best.second * 100 + best.third
            if (nextScore == null || bestScore - nextScore >= 25) {
                runCatching { engine.linkThemeWallpaper(themeName, best.first) }
                    .onSuccess {
                        render()
                        showDraftThemePreview(listOf("Local-only match selected: ${best.first.name}", "Theme-color similarity: ${best.third}% · filename terms matched: ${best.second}", "Only the files you chose were examined; no local image or filename was uploaded."))
                    }
                    .onFailure { Toast.makeText(this@AssetsActivity, it.message ?: "Could not add selected wallpaper", Toast.LENGTH_LONG).show() }
            } else {
                AlertDialog.Builder(this@AssetsActivity)
                    .setTitle("Choose a local wallpaper")
                    .setMessage("These explicitly selected images were scored against the theme's palette and filenames on-device. Nothing was uploaded.")
                    .setItems(ranked.map { "${it.first.name} · palette ${it.third}% · filename ${it.second}" }.toTypedArray()) { _, index -> showImportedPreview(ranked[index].first) }
                    .setNegativeButton("Keep all in library", null)
                    .show()
            }
        }
    }

    private fun showImportedPreview(file: File) {
        val bitmap = ImageAssetValidation.decodeSampled(file, 1200)
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
            text = "${file.name}\nCopied to this app's private wallpaper library. This optional local file is not uploaded."
            setTextColor(UiTheme.textPrimary)
            setPadding(0, dp(8), 0, 0)
        })
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (pendingThemeName != null) "Preview local theme asset" else "Preview local image")
            .setView(preview)
            .setPositiveButton(if (pendingThemeName != null) "Add to theme" else "Use in launcher", null)
            .setNegativeButton("Keep in library", null)
            .setNeutralButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (pendingThemeName != null) {
                    runCatching { engine.linkThemeWallpaper(pendingThemeName!!, file) }
                        .onSuccess { dialog.dismiss(); render(); showDraftThemePreview() }
                        .onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }
                } else manager.setLauncherWallpaper(file).onSuccess {
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

    private fun buildThemeBundle(query: String) {
        val themeName = pendingThemeName ?: return searchWallpapers(query)
        buildContinue.set(true)
        buildCancelButton?.apply { isEnabled = true; visibility = View.VISIBLE }
        searchProgressLabel.text = "Starting the confirmed theme build…"
        searchRow.visibility = View.VISIBLE
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val installedApps = AppRepository(applicationContext).listLaunchableApps()
                    ThemeBundleBuilder(this@AssetsActivity).build(
                        themeName, query, installedApps, { buildContinue.get() }
                    ) { message ->
                        runOnUiThread {
                            if (!isFinishing && buildContinue.get()) searchProgressLabel.text = message
                        }
                    }
                }
                if (buildContinue.get()) showThemeBundlePreview(result)
            } catch (error: Exception) {
                if (error is CancellationException && !buildContinue.get()) {
                    Toast.makeText(this@AssetsActivity, "Theme build canceled; your draft is unchanged.", Toast.LENGTH_LONG).show()
                } else if (error is CancellationException) {
                    throw error
                } else {
                    AlertDialog.Builder(this@AssetsActivity)
                        .setTitle("Theme assets could not be completed")
                        .setMessage("${error.message ?: "The free asset catalog was unavailable."}\n\nYour palette is still saved as a draft. You can retry the search or add a local wallpaper from the picker.")
                        .setPositiveButton("Retry search") { _, _ -> buildThemeBundle(query) }
                        .setNegativeButton("Keep draft", null)
                        .show()
                }
            } finally {
                buildCancelButton?.visibility = View.GONE
                searchRow.visibility = View.GONE
            }
        }
    }

    private fun showThemeBundlePreview(result: ThemeBundleBuildResult) {
        val values = pendingThemeName?.let { engine.themeValues(it) } ?: return
        val builderNotes = buildList {
            add("Wallpaper: ${result.wallpaper.title} (${result.wallpaper.license})")
            add("Matching OpenMoji app icons prepared: ${result.iconMappings.size}")
            result.iconMappings.take(12).forEach { add("${it.appLabel} → ${it.glyph.annotation}") }
            if (result.iconMappings.size > 12) add("…and ${result.iconMappings.size - 12} more mappings")
            addAll(result.warnings)
        }
        showDraftThemePreview(builderNotes)
    }

    private fun showDraftThemePreview(buildNotes: List<String> = emptyList()) {
        val targetName = pendingThemeName ?: return
        val values = runCatching { engine.themeValues(targetName) }.getOrElse {
            Toast.makeText(this, "Theme draft ‘$targetName’ is unavailable: ${it.message}", Toast.LENGTH_LONG).show()
            return
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(6), dp(17), dp(10))
        }
        val wallpaper = values.wallpaperAsset?.let { AssetStore(this).wallpaper(it) }
        val bitmap = wallpaper?.takeIf { it.isFile }?.let { ImageAssetValidation.decodeSampled(it, 1200) }
        if (bitmap != null) body.addView(ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Preview of ${values.name} theme wallpaper"
        }, LinearLayout.LayoutParams(-1, dp(180)))
        body.addView(TextView(this).apply {
            text = "${values.layout.uppercase()} layout · ${values.style} cards · ${values.iconStyle} icon shape · ${values.iconAssets.size} custom app-icon mappings${values.iconPackPackage?.let { " · installed pack: $it" } ?: ""}"
            textSize = 12f
            setTextColor(UiTheme.textPrimary)
            setPadding(0, dp(9), 0, dp(6))
        })
        val palette = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        listOf(values.background, values.accent, values.accent2, values.card).forEach { color ->
            palette.addView(View(this).apply {
                background = UiTheme.rounded(android.graphics.Color.parseColor(color), dp(6).toFloat())
                contentDescription = "Theme color $color"
            }, LinearLayout.LayoutParams(0, dp(24), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
        }
        body.addView(palette)
        val records = mutableListOf<com.aicontrol.launcher.assets.AssetAttribution>()
        values.wallpaperAsset?.let { catalog.wallpaperAttribution(it)?.let(records::add) }
        values.iconAssets.values.distinct().forEach { assetName ->
            catalog.anyAttribution(assetName)?.let { if (it !in records) records += it }
        }
        val notes = buildList {
            if (buildNotes.isNotEmpty()) addAll(buildNotes)
            else add("Saved app-icon mappings: ${values.iconAssets.size}. Unmapped apps keep their installed icons.")
            if (records.isNotEmpty()) {
                add("")
                add("ATTRIBUTION")
                records.forEach { record ->
                    add("${record.title} — ${record.creator}; ${record.license}; ${record.sourceUrl}")
                }
            }
            if (values.iconAssets.isNotEmpty() && records.any { it.title.contains("OpenMoji", true) }) {
                add("OpenMoji artwork is used unmodified under CC BY-SA 4.0. All emojis designed by OpenMoji – the open-source emoji and icon project.")
            }
            if (records.isEmpty() && values.wallpaperAsset != null) add("Local image chosen by you; no remote attribution was supplied.")
            add("No character logo/artwork was downloaded. This changes this launcher's appearance only; it does not change Android's device wallpaper.")
        }
        body.addView(TextView(this).apply {
            text = notes.joinToString("\n")
            textSize = 10f
            setTextColor(UiTheme.textMuted)
            setPadding(0, dp(9), 0, 0)
        })
        val scroll = ScrollView(this).apply { addView(body) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Preview complete theme · ${values.name}")
            .setView(scroll)
            .setNegativeButton("Keep as draft", null)
            .setPositiveButton("Apply complete theme", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                runCatching {
                    engine.setTheme(targetName)
                    UiTheme.bind(engine)
                    Toast.makeText(this, "${values.name} applied to this launcher", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    finish()
                }.onFailure {
                    Toast.makeText(this, it.message ?: "Could not apply theme", Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }

    private fun searchWallpapers(query: String) {
        buildCancelButton?.visibility = View.GONE
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
            text = "${candidate.title}\n\nCreator: ${candidate.creator}\nLicense: ${candidate.license}\nLicense details: ${candidate.licenseUrl}\nSource: ${candidate.pageUrl}\n\n${if (pendingThemeName != null) "This image will be added to the ‘$pendingThemeName’ draft for the complete preview." else "Choose where to use this downloaded image. Applying inside the launcher does not alter Android's device wallpaper."}"
            textSize = 11f
            setTextColor(UiTheme.textPrimary)
            setPadding(0, dp(8), 0, 0)
        })
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (pendingThemeName != null) "Preview image for theme" else "Preview downloaded wallpaper")
            .setView(preview)
            .setNegativeButton("Keep in library", null)
            .setNeutralButton("Device wallpaper…", null)
            .setPositiveButton(if (pendingThemeName != null) "Add to theme" else "Set launcher background", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (pendingThemeName != null) {
                    runCatching { engine.linkThemeWallpaper(pendingThemeName!!, file) }
                        .onSuccess { dialog.dismiss(); showDraftThemePreview() }
                        .onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }
                } else manager.setLauncherWallpaper(file).onSuccess {
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
            val attribution: AssetAttribution? = when (title) {
                "Wallpapers" -> catalog.wallpaperAttribution(file.name)
                "Images" -> catalog.imageAttribution(file.name)
                else -> null
            }
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
                if (pendingThemeName != null) line.addView(actionButton("Icon") { chooseTargetApp(file) }.apply {
                    textSize = 9f
                    layoutParams = LinearLayout.LayoutParams(-2, dp(42)).apply { leftMargin = dp(3) }
                })
            } else if (canApply) line.addView(actionButton("Apply") {
                manager.applyIconPack(file.name).onFailure { Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show() }
                render()
            })
            if (title == "Images" && pendingThemeName != null) line.addView(actionButton("Icon") { chooseTargetApp(file) }.apply {
                textSize = 9f
                layoutParams = LinearLayout.LayoutParams(-2, dp(42)).apply { leftMargin = dp(3) }
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

    private fun chooseTargetApp(file: File) {
        val themeName = pendingThemeName ?: return
        val installed = appRepository.listLaunchableApps().distinctBy { it.packageName }
        if (installed.isEmpty()) {
            Toast.makeText(this, "No launchable apps are available for icon mapping.", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Map image to installed app")
            .setItems(installed.map { "${it.label}  ·  ${it.packageName}" }.toTypedArray()) { _, index ->
                runCatching { engine.linkThemeIcon(themeName, installed[index].packageName, file) }
                    .onSuccess { render(); showDraftThemePreview() }
                    .onFailure { Toast.makeText(this, it.message ?: "Could not map icon", Toast.LENGTH_LONG).show() }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
