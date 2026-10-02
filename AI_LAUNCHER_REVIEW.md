# Launcher implementation review

## Implemented in this change

### Guided theme creation

The existing local parser now accepts concise named-theme requests such as `Make a Spider-Man theme`. The parser validates theme names/colors and uses a deterministic palette hint for names containing “spider”: dark navy background/card, red accent, blue secondary accent, and neon card style. Explicit user-supplied color/style values take precedence. Before saving, the launcher shows an actual swatch/mock launcher preview; **Apply** persists and activates the custom theme. **Apply + find wallpaper** activates that reviewed theme and opens Assets with an abstract, palette-matched search.

No character artwork or logo is synthesized, downloaded, or bundled. This is a color/style adaptation, not a Spider-Man asset pack. The offline example remains deterministic; general conversation uses the configured provider described below.

### Asset discovery and local images

- The user can choose an image from Downloads via Android's system document picker. The chosen file is copied into app-private storage, with JPEG/PNG/WebP MIME checks, a 10 MB cap, dimension validation, and sampled decoding. The app does not enumerate the full Downloads directory automatically.
- An explicit Assets action searches Wikimedia Commons for bitmap wallpapers. Candidates must be HTTPS files hosted by Wikimedia Commons and carry an allowlisted CC0, public-domain, CC BY, or CC BY-SA license. NC/ND and unknown licenses are excluded. The user sees creator, license URL, and source URL before selecting **Download**.
- Downloaded Commons attribution is stored locally and shown in the asset library. The search phrase is sent to Wikimedia Commons only when the user searches; no API key or paid image/model service is used.
- Selecting **Use in launcher** stores the private wallpaper reference for this app only. Changing Android's system wallpaper is a separate, explicitly confirmed action.

This is a conservative automated filter, not a guarantee that every search result's provenance is correct; the user is shown the Commons source/license for review. Character-specific imagery is not sourced.

### Android widgets and customization

The HOME activity now owns an `AppWidgetHost` and delegates provider selection/configuration to Android's widget picker. Selected installed widgets render on the launcher and can be removed. No third-party APK is installed. Static wallpaper/icon packs are not treated as widget providers.

Saved custom themes appear in the settings theme picker. Existing grid, style, icon-label, dock-slot, installed ADW/Nova-compatible icon-pack, asset-library, and app-grid operations remain. Hiding an app only removes it from this launcher's grid.

## Provider-backed assistant and safety boundary

The launcher prompt field now routes to the already-supported OpenRouter or Gemini client when the selected provider has a saved key. It includes the prompt, up to eight recent turns, and limited launcher appearance/theme context; it does not include installed-app inventory, local asset names, file contents, or Downloads content. No provider key is present in this repository, and this sandbox cannot inspect Android's private app preferences, so an installed user must select/configure a provider and key if one is not already saved. The deterministic offline parser remains the fallback when no active key is configured.

Model output is parsed as a bounded JSON object into typed theme, layout/style, Commons search, or widget-picker operations. Unknown tools/fields, URLs, downloads, delete operations, code, and malformed plans are rejected without applying anything. The assistant's natural-language response is displayed; missing information is returned as a clarification question. A valid plan is previewed and staged behind an explicit confirmation gate. Confirmation may apply theme/layout/style changes or initiate a Wikimedia Commons search / Android widget picker. Each found image must then be separately reviewed and confirmed before download. Local image import stays user-selected and on-device.

## Compatibility and release constraints

- Required target retained: `minSdk=30`, `targetSdk=30`, manifest `maxSdkVersion=30`, native ABI `armeabi-v7a` only. `compileSdk=35` remains for toolchain compatibility; CMake uses Android 30.
- No broad package visibility permission was added.
- The committed Actions workflow remains unchanged because its write permission was unavailable in the prior attempt. It still expects SDK 28 and target SDK 35, so its metadata verification conflicts with the strict API 30 target. The observed [run 37052477394](https://github.com/richardbrownmiami-commits/AI-Control-Launcher-Phase1/actions/runs/37052477394) for baseline commit `15de624d` completed **failure** at `Verify debug APK Android 9-15`. No CI run was triggered for these local edits.
- Local verification passed: 21/21 JVM tests, `lintDebug` (44 warnings, no lint failure), `assembleDebug`, and `assembleRelease`. Both packaged APKs report min/target/max SDK 30 and only 32-bit ARM EABI (`armeabi-v7a`, ELFCLASS32, `e_machine=40`).
- Release is signed with the debug key as configured in Gradle. No physical-device installation or runtime test is claimed.

## Follow-up items not included

1. Provider keys currently use app-private SharedPreferences; Android Keystore-backed secret storage remains future work. The current screen lists only the provider/model choices already present in the app.
2. The scope-preserving Downloads intake is user-directed through the document picker; a permission-based media index scanner is not implemented.
3. Per-icon artist/license attribution for arbitrary remote icon packs, a custom user-editable palette form, multiple home pages/workspaces, and a widget resize/reorder editor remain future work.
4. A test on a physical Android 11 ARMv7a device is needed to verify OEM-specific widget picker behavior; none is claimed here.
