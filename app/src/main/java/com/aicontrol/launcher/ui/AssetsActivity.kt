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
import com.aicontrol.launcher.theme.ThemeSpec
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
    private lateinit var previewAssets: ThemePreviewAssets
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
        previewAssets = ThemePreviewAssets(this, engine, appRepository)
        pendingThemeName = intent.getStringExtra(EXTRA_THEME_NAME)?.takeIf { it.isNotBlank() }
        buildUi()
        intent.getStringExtra(EXTRA_SUGGESTED_QUERY)?.takeIf { it.isNotBlank() }?.let { query ->
            if (pendingThemeName != null) buildThemeBundle(query) else searchWallpapers(query)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(14))
            background = UiTheme.background()
        }
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(Button(this).apply {
            text = "‹"
            textSize = 25f
            minWidth = 0
            minHeight = 0
            setTextColor(UiTheme.textPrimary)
            background = UiTheme.rounded(UiTheme.card2, 16f)
            contentDescription = "Back"
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        heading.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(TextView(this@AssetsActivity).apply {
                text = if (pendingThemeName != null) "Theme assets" else "Wallpapers & assets"
                textSize = 20f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setTextColor(UiTheme.textPrimary)
            })
            addView(TextView(this@AssetsActivity).apply {
                text = pendingThemeName?.let { "Commons wallpaper + installed Android app icon pack · $it" } ?: "Your private visual library"
                textSize = 11f
                setTextColor(UiTheme.textMuted)
            })
        })
        root.addView(heading, LinearLayout.LayoutParams(-1, dp(52)))

        val intro = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(11), dp(13), dp(11))
            UiTheme.styleCard(this, UiTheme.card, false)
            addView(TextView(this@AssetsActivity).apply {
                text = pendingThemeName?.let { "Build a complete wallpaper-and-icon bundle, then preview the saved files before applying." }
                    ?: "Import an image you choose, or search for reusable wallpaper. Preview before applying."
                textSize = 13f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setTextColor(UiTheme.textPrimary)
            })
            addView(TextView(this@AssetsActivity).apply {
                text = "Local files stay on-device. Online results show creator and license before download."
                textSize = 10.5f
                setTextColor(UiTheme.textMuted)
                setPadding(0, dp(3), 0, 0)
            })
        }
        root.addView(intro, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7); bottomMargin = dp(8) })
        root.addView(actionButton("Asset sources & licenses") { AssetSourcesDialog.show(this) },
            LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(7) })
        pendingThemeName?.let { themeName ->
            root.addView(actionButton("Choose or install an app icon pack") {
                startActivity(Intent(this, ThemeGalleryActivity::class.java)
                    .putExtra(ThemeGalleryActivity.EXTRA_SELECT_ICON_PACK_FOR_THEME, themeName))
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(7) })
        }

        val buttons = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        buttons.addView(actionButton("Import image") { pickLocalImage() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        buttons.addView(actionButton("Find wallpaper") {
            val query = intent.getStringExtra(EXTRA_SUGGESTED_QUERY) ?: "abstract colorful geometric wallpaper"
            askWallpaperQuery(query)
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(7) })
        root.addView(buttons)
        searchRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = UiTheme.rounded(UiTheme.card2, 14f)
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
            textSize = 10f
            isAllCaps = false
            setTextColor(UiTheme.textPrimary)
            background = UiTheme.rounded(UiTheme.card, 12f)
            visibility = View.GONE
            setOnClickListener {
                buildContinue.set(false)
                searchProgressLabel.text = "Canceling after the current operation…"
                isEnabled = false
            }
        }
        searchRow.addView(buildCancelButton, LinearLayout.LayoutParams(-2, dp(44)))
        root.addView(searchRow, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(7); bottomMargin = dp(5) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { clipToPadding = false; addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
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
        textSize = 10.5f
        isAllCaps = false
        minWidth = 0
        minHeight = 0
        setPadding(dp(6), 0, dp(6), 0)
        setTextColor(UiTheme.textPrimary)
        background = UiTheme.rounded(UiTheme.card2, 15f)
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
                    ThemeBundleBuilder(this@AssetsActivity).build(
                        themeName, query, { buildContinue.get() }
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
        val targetName = pendingThemeName ?: return
        val values = runCatching { engine.themeValues(targetName) }.getOrElse {
            AlertDialog.Builder(this).setTitle("Theme bundle could not be read")
                .setMessage("The theme JSON was not readable after asset creation. No complete theme is being reported or applied. ${it.message ?: ""}")
                .setPositiveButton("Keep draft", null).show()
            return
        }
        val savedWallpaper = values.wallpaperAsset?.let { AssetStore(this).wallpaper(it) }
        val wallpaperWasSaved = runCatching {
            val savedFile = requireNotNull(savedWallpaper)
            require(result.wallpaperFile.isFile && savedFile.canonicalFile == result.wallpaperFile.canonicalFile)
            ImageAssetValidation.validate(savedFile)
            true
        }.getOrDefault(false)
        if (!wallpaperWasSaved) {
            AlertDialog.Builder(this).setTitle("Wallpaper was not committed to the theme")
                .setMessage("The downloaded file and saved theme did not match, so this bundle is not complete and cannot be applied. Keep the palette as a draft or retry the build.")
                .setPositiveButton("Retry") { _, _ -> buildThemeBundle(ThemeSpec.suggestedWallpaperQuery(targetName)) }
                .setNegativeButton("Keep draft", null).show()
            return
        }
        val builderNotes = buildList {
            add("Wallpaper: ${result.wallpaper.title} (${result.wallpaper.license})")
            if (result.iconPack != null) {
                add("Real app icon pack saved: ${result.iconPack.label} (${result.iconPack.packageName})")
            } else {
                add("No compatible app icon pack is installed. Appstract can be installed by you from F-Droid, then selected from the theme gallery.")
            }
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
        val wallpaper = previewAssets.wallpaper(values, 180)
        if (wallpaper != null) body.addView(wallpaper)
        else body.addView(TextView(this).apply {
            text = if (values.wallpaperAsset == null) "Palette-only theme · no wallpaper requested" else "Wallpaper image is missing or unreadable · rebuild before applying"
            textSize = 11f
            setTextColor(UiTheme.textMuted)
            setPadding(0, dp(10), 0, dp(6))
        })
        body.addView(previewAssets.iconStrip(values, maxIcons = 6, iconSizeDp = 42),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        body.addView(previewAssets.illustrationStrip(22),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        body.addView(TextView(this).apply {
            text = "${values.layout.uppercase()} layout · ${values.style} cards · ${values.iconStyle} icon shape · " +
                "${values.iconAssets.keys.count { !engine.isOpenMojiIllustration(targetName, it) }} custom image overrides · ${previewAssets.packStatus(values)}"
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
        values.iconPackPackage?.let { catalog.anyAttribution(it)?.let(records::add) }
        values.iconAssets.values.distinct().forEach { assetName ->
            catalog.anyAttribution(assetName)?.let { if (it !in records) records += it }
        }
        if (values.iconAssets.isNotEmpty() || values.wallpaperAsset != null) {
            catalog.anyAttribution("openmoji_1f4f7.png")?.let { if (it !in records) records += it }
        }
        val requiredImageOverrides = values.iconAssets.keys.filterNot { engine.isOpenMojiIllustration(targetName, it) }
        val iconsAreReadable = requiredImageOverrides.all { packageName ->
            engine.themeIconFile(targetName, packageName)?.let { file ->
                runCatching { ImageAssetValidation.validate(file) }.isSuccess
            } == true
        }
        val wallpaperIsReadable = values.wallpaperAsset == null || wallpaper != null
        val savedPackAvailable = values.iconPackPackage == null || previewAssets.packFor(values)?.packageName == values.iconPackPackage
        val canApplyCompleteBundle = iconsAreReadable && wallpaperIsReadable && savedPackAvailable
        if (!canApplyCompleteBundle) body.addView(TextView(this).apply {
            text = "Some files referenced by this theme are missing or unreadable. Re-build the bundle before applying it."
            textSize = 11f
            setTextColor(UiTheme.accent)
            setPadding(0, dp(8), 0, dp(2))
        })
        if (values.iconPackPackage.isNullOrBlank()) body.addView(TextView(this).apply {
            text = "No real app icon pack is saved with this theme. Apps use the current launcher setting/original icons. Choose Appstract or another installed compatible pack to attach actual app-filter mappings."
            textSize = 11f
            setTextColor(UiTheme.textMuted)
            setPadding(0, dp(7), 0, dp(2))
        })
        else if (!savedPackAvailable) body.addView(TextView(this).apply {
            text = "The saved app icon pack is not installed. Install it from its official F-Droid page, then choose the detected pack before applying this theme."
            textSize = 11f
            setTextColor(UiTheme.accent)
            setPadding(0, dp(7), 0, dp(2))
        })
        val notes = buildList {
            if (buildNotes.isNotEmpty()) addAll(buildNotes)
            else add("Custom image overrides: ${requiredImageOverrides.size}. Real app icons come from the selected installed Android icon pack when mapped; otherwise the original installed app icon remains.")
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
            add("This changes the launcher's background only; it does not change Android's device wallpaper. OpenMoji symbols shown above are decorative illustrations, never app icons.")
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
            .setNeutralButton("Choose app icon pack") { _, _ ->
                startActivity(Intent(this, ThemeGalleryActivity::class.java)
                    .putExtra(ThemeGalleryActivity.EXTRA_SELECT_ICON_PACK_FOR_THEME, targetName))
            }
            .setPositiveButton(
                when {
                    !canApplyCompleteBundle -> "Bundle incomplete"
                    values.iconPackPackage.isNullOrBlank() -> "Apply without an app pack"
                    else -> "Apply theme"
                }, null
            )
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                isEnabled = canApplyCompleteBundle
                setOnClickListener {
                    runCatching {
                        engine.setTheme(targetName)
                        UiTheme.bind(engine)
                        Toast.makeText(
                            this@AssetsActivity,
                            if (values.iconPackPackage.isNullOrBlank()) "${values.name} applied; no dedicated app icon pack is attached" else "${values.name} applied with its saved app icon pack",
                            Toast.LENGTH_LONG
                        ).show()
                        dialog.dismiss()
                        finish()
                    }.onFailure {
                        Toast.makeText(this@AssetsActivity, it.message ?: "Could not apply theme", Toast.LENGTH_LONG).show()
                    }
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
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            actions.addView(actionButton("Review & download") { reviewCandidate(candidate) },
                LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(5) })
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
        val heading = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(12), dp(2), dp(7))
            addView(TextView(this@AssetsActivity).apply {
                text = title
                textSize = 14f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setTextColor(UiTheme.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            addView(TextView(this@AssetsActivity).apply {
                text = files.size.toString()
                textSize = 10f
                setTextColor(UiTheme.textMuted)
                background = UiTheme.rounded(UiTheme.card2, 12f)
                setPadding(dp(8), dp(4), dp(8), dp(4))
            })
        }
        list.addView(heading)
        if (files.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Nothing saved here yet"
                textSize = 11f
                setTextColor(UiTheme.textMuted)
                setPadding(dp(4), dp(2), dp(4), dp(7))
            })
            return
        }
        files.forEach { file ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(9), dp(10), dp(9))
                UiTheme.styleCard(this, UiTheme.card, false)
            }
            val attribution: AssetAttribution? = when (title) {
                "Wallpapers" -> catalog.wallpaperAttribution(file.name)
                "Images" -> catalog.imageAttribution(file.name)
                else -> null
            }
            if (title == "Wallpapers") {
                val bitmap = ImageAssetValidation.decodeSampled(file, 1200)
                if (bitmap != null) card.addView(ImageView(this).apply {
                    setImageBitmap(bitmap)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = "Wallpaper preview of ${file.name}"
                    clipToOutline = true
                    background = UiTheme.rounded(UiTheme.card2, 16f)
                }, LinearLayout.LayoutParams(-1, dp(148)).apply { bottomMargin = dp(9) })
            }
            val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            if (title in setOf("Images", "Icon overrides")) {
                val bitmap = ImageAssetValidation.decodeSampled(file, 128)
                if (bitmap != null) top.addView(ImageView(this).apply {
                    setImageBitmap(bitmap)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = "Preview of ${file.name}"
                    clipToOutline = true
                    background = UiTheme.rounded(UiTheme.card2, 12f)
                }, LinearLayout.LayoutParams(dp(52), dp(52)).apply { rightMargin = dp(10) })
            }
            top.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@AssetsActivity).apply {
                    text = file.name
                    textSize = if (title == "Wallpapers") 14f else 12f
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                    setTextColor(UiTheme.textPrimary)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                addView(TextView(this@AssetsActivity).apply {
                    text = attribution?.let { "${it.license} · ${it.creator}" } ?: "${(file.length() / 1024).coerceAtLeast(1)} KB"
                    textSize = 10f
                    setTextColor(UiTheme.textMuted)
                    maxLines = 2
                    setPadding(0, dp(3), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(top)
            attribution?.let { record ->
                card.addView(TextView(this).apply {
                    text = "Source: ${record.sourceUrl}"
                    textSize = 9f
                    setTextColor(UiTheme.textMuted)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(dp(2), dp(5), dp(2), 0)
                })
            }
            val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            fun action(label: String, click: () -> Unit) {
                actions.addView(actionButton(label, click), LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    leftMargin = dp(2); rightMargin = dp(2)
                })
            }
            if (canApply && title == "Wallpapers") {
                action("Use here") { useWallpaper(file) }
                action("Device") { confirmDeviceWallpaper(file) }
                if (pendingThemeName != null) action("Theme icon") { chooseTargetApp(file) }
            } else if (canApply) {
                action("Apply") {
                    manager.applyIconPack(file.name).onFailure { Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show() }
                    render()
                }
            } else if (title == "Images" && pendingThemeName != null) {
                action("Theme icon") { chooseTargetApp(file) }
            }
            action("Delete") {
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
            }
            card.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
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
