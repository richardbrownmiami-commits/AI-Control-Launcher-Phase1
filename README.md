# AI Control Launcher

A native Android HOME launcher targeted strictly at Android 11 / API 30 and 32-bit ARMv7a. The app sets `minSdk=30`, `targetSdk=30`, and manifest `maxSdkVersion=30`; CMake and packaging remain `armeabi-v7a` only.

## Launcher experience

- **Three swipeable home pages** with saved page selection, shortcuts, row/column-aware capacity, reorder/move/remove controls, and installed Android widgets on the selected page.
- **Home and app drawer** with live clock/date, wallpaper background, assistant entry, dock, fixed drawer button, searchable/sortable installed-app grid, package lookup limited to launcher-intent visibility, and themed icons used consistently in the home grid and drawer.
- **Dock and settings** for rows, columns, icon size/labels, dock visibility/count, startup page, drawer sort/search/labels, compatible installed icon pack, layout preset, and active theme.
- **Theme gallery** with nine original presets (Default, Midnight, Ocean, Ember, Aurora, Sunset, Sage, Paper, Graphite), generated gradient/background styles, card treatments, typography, icon shapes, and layout variants. Saved custom bundles can be previewed and edited without silently applying them.

## AI and complete theme bundles

The assistant is a dedicated, persistent conversation screen and uses the existing Gemini or OpenRouter provider when configured. Provider requests keep role-structured conversation history. With no active provider, the deterministic offline helper remains available and the screen explains its narrower scope.

For a request to create a themed look, the assistant proposes a validated palette, display/layout choices, and an abstract reusable-art search. The user reviews and confirms the plan. Then the app automatically:

1. Searches Wikimedia Commons using bounded query variants for abstract wallpaper and selects a supported raster image that passes the license allowlist, Commons-host/path check, title filters, minimum-resolution/aspect checks, and byte/decode validation.
2. Downloads one image from Commons' official thumbnail/original hosts and records its creator, license, license URL, and source page.
3. Reads the official OpenMoji catalog and matches generic symbols (such as camera, mail, clock, phone, browser, or settings) to installed-app labels **locally on the device**. Matching app labels are not sent to that catalog. Selected original 72px PNG assets are saved into the private library with CC BY-SA 4.0 attribution; app mappings are copied into the draft theme.
4. Saves a complete custom-theme draft and presents one cohesive preview with wallpaper, palette, layout, icon mappings, and source/license details. Nothing changes on the launcher until the user presses **Apply complete theme**. There are no per-image approval prompts in this automatic flow.

The asset finder rejects unknown, non-commercial (NC), no-derivatives (ND), and other non-allowlisted Commons licenses. Search phrases that name common copyrighted characters/franchises are rejected; a superhero-inspired request is expressed as an abstract color/pattern theme. No Spider-Man/Marvel artwork or proprietary Nova assets are bundled. Previewed public artwork remains subject to its recorded upstream license.

### Optional local images

The document picker is an **optional** alternative to network asset discovery. It can import up to six JPEG/PNG/WebP files explicitly selected by the user (10 MB each). The app never scans Downloads or reads a folder in the background. Only the chosen images are validated and, for multi-select theme selection, compared on-device by filename and sampled color similarity; files/names are not uploaded to Gemini, OpenRouter, or Commons. If the local match is ambiguous, the user chooses one before the draft preview.

### Installed packs and widgets

- The app discovers installed Nova/ADW-compatible icon-pack activities and resolves documented `appfilter.xml` component-to-drawable mappings from installed package resources. Plans can use only packs already installed. The store action opens official Google Play search; the launcher never sideloads an arbitrary APK.
- Widgets are installed Android providers selected through the system `AppWidgetHost` picker. Widgets are installed executable components, not theme images; configuration/permissions remain in Android's widget flow.
- Private `.novabackup` import/restore is not supported (a Nova backup is launcher state, not a theme/icon pack).

## Asset safety and privacy

- Commons uses HTTPS, a two-host/path allowlist, selected raster MIME checks, a 10 MB transfer bound, safe dimension/decode checks, and retained title/creator/license/source attribution.
- OpenMoji catalog metadata is capped at 6 MB and cached locally; each selected icon PNG is capped at 512 KB, decoded before use, and downloaded only from the official `raw.githubusercontent.com` repository.
- Imported/downloaded theme assets live under app-private `filesDir/launcher-assets/`. Local images are never implicitly uploaded. The AI receives the user prompt, recent conversation, and limited appearance/theme state only when its existing provider is configured and used.
- Applying a downloaded image to the launcher background and changing the Android device wallpaper are separate actions. The automated theme flow only changes the launcher after the final explicit Apply.

## Build and verification

Requires JDK 17+, Android SDK platform 35, Build Tools 35.0.0, NDK 27.0.12077973, CMake 3.31.6, and the committed Gradle wrapper.

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

APK files are written under `app/build/outputs/apk/`. The GitHub Actions workflow is intentionally unchanged. Its existing “Verify debug APK Android 9-15” step asserts `sdkVersion:'28'` and `targetSdkVersion:'35'`, which conflict with the required API-30-only manifest; the latest upstream run fails there after the APK build. This local patch is not pushed and does not claim to rerun remote Actions. The release build uses the repository's debug signing configuration, so configure a private release keystore before distribution. No physical-device/emulator test is claimed.
