# AI Control Launcher

A native Android HOME launcher targeted strictly at Android 11 / API 30 and 32-bit ARMv7a. The app sets `minSdk=30`, `targetSdk=30`, and manifest `maxSdkVersion=30`; CMake and packaging remain `armeabi-v7a` only.

## Launcher experience

- **Three swipeable home pages** with saved page selection, shortcuts, row/column-aware capacity, reorder/move/remove controls, and installed Android widgets on the selected page.
- **Home and app drawer** with live clock/date, wallpaper background, assistant entry, dock, fixed drawer button, searchable/sortable installed-app grid, package lookup limited to launcher-intent visibility, and real installed icon-pack mappings applied to matching apps.
- **Dock and settings** for rows, columns, icon size/labels, dock visibility/count, startup page, drawer sort/search/labels, compatible installed icon packs, layout preset, and active theme.
- **Theme gallery** with nine original presets (Default, Midnight, Ocean, Ember, Aurora, Sunset, Sage, Paper, Graphite), generated gradient/background styles, card treatments, typography, icon shapes, and layout variants. Each card previews its saved wallpaper and actual app icons; custom bundles can be inspected and edited without silently applying them.

## AI and complete theme bundles

The assistant is a dedicated, persistent conversation screen and uses the existing Gemini or OpenRouter provider when configured. Provider requests keep role-structured conversation history. With no active provider, the deterministic offline helper remains available and the screen explains its narrower scope.

For a request to create a themed look, the assistant proposes a validated palette, display/layout choices, and an abstract reusable-art search. The user reviews and confirms the plan. Then the app automatically:

1. Searches Wikimedia Commons using bounded query variants for abstract wallpaper and selects a supported raster image that passes the license allowlist, Commons host/path check, title filters, minimum-resolution/aspect checks, and byte/decode validation.
2. Saves that image and its creator, individual-file license, license URL, and Commons source page into the private theme library before showing a success state. If network search/download is unavailable, the app uses its included CC0 wallpaper and reports that fallback.
3. Attaches the package identity of a **compatible Android app icon pack already installed on the device**. If several are installed, Appstract is preferred unless the user has selected another pack. Actual icons are resolved from the pack's standard `appfilter.xml` component mappings and drawable resources; unmapped apps keep their real installed icons. The installed-app list never leaves the device.
4. Saves a theme draft and previews the saved wallpaper plus icons resolved from that installed pack. If no compatible pack is installed, the preview says so and links to Appstract's official F-Droid page. After the user installs it through Android/F-Droid and returns, the gallery refreshes and lets them explicitly save the pack for the theme. **OpenMoji is decorative artwork only, never a substitute app icon or Android icon pack.** Nothing changes until the user presses Apply.

The asset finder rejects unknown, non-commercial (NC), no-derivatives (ND), and other non-allowlisted Commons licenses. Search phrases that name common copyrighted characters/franchises are rejected; a superhero-inspired request is expressed as an abstract color/pattern theme. No Spider-Man/Marvel artwork or proprietary Nova assets are bundled. Previewed public artwork remains subject to its recorded upstream license.

### Optional local images

The document picker is an **optional** alternative to network asset discovery. It can import up to six JPEG/PNG/WebP files explicitly selected by the user (10 MB each). The app never scans Downloads or reads a folder in the background. Only the chosen images are validated and, for multi-select theme selection, compared on-device by filename and sampled color similarity; files/names are not uploaded to Gemini, OpenRouter, or Commons. If the local match is ambiguous, the user chooses one before the draft preview.

### Installed packs and widgets

- The app discovers installed Nova, ADW, Apex, and Lawnchair-compatible icon-pack activities using Android package visibility and resolves standard appfilter component-to-drawable mappings. Themes persist a pack package identity and preflight it before apply.
- **Appstract** (`dev.appstract.iconpack`) is the recommended open-source pack; its F-Droid listing describes nearly 590 icons and support for Nova, Lawnchair, ADW, Apex, Action, and other launchers. The app exposes links to the official [F-Droid page](https://f-droid.org/en/packages/dev.appstract.iconpack/), [source repository](https://github.com/yangchoo/Appstract), and [Apache-2.0 license](https://www.apache.org/licenses/LICENSE-2.0). The launcher opens the details page only: it never silently downloads or installs an APK.
- Widgets are installed Android providers selected through the system `AppWidgetHost` picker. Widgets are installed executable components, not theme images; configuration/permissions remain in Android's widget flow.
- Private `.novabackup` import/restore is not supported (a Nova backup is launcher state, not a theme/icon pack).

## Asset safety and privacy

- Commons uses HTTPS, a two-host/path allowlist, selected raster MIME checks, a 10 MB transfer bound, safe dimension/decode checks, and retained title/creator/license/source attribution.
- OpenMoji resources are CC BY-SA 4.0 **decorative symbol illustrations** with visible source/license attribution; they are not matched to app labels and are never used as app icons.
- Installed app-pack names, mappings, and local images remain on-device. No arbitrary pack APK is fetched or installed. The AI receives the user prompt, recent conversation, and limited appearance/theme state only when its existing provider is configured and used.
- Applying a downloaded image to the launcher background and changing the Android device wallpaper are separate actions. The automated theme flow only changes the launcher after the final explicit Apply.

## Build and verification

Requires JDK 17+, Android SDK platform 35, Build Tools 35.0.0, NDK 27.0.12077973, CMake 3.31.6, and the committed Gradle wrapper. The APK is intentionally `minSdk=30`, `targetSdk=30`, manifest `maxSdkVersion=30`, and 32-bit ARMv7a only.

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

APK files are written under `app/build/outputs/apk/`. The GitHub Actions workflow is intentionally unchanged. Its existing “Verify debug APK Android 9-15” step asserts `sdkVersion:'28'` and `targetSdkVersion:'35'`, which conflict with the required API-30-only manifest; the latest upstream run fails there after the APK build. This local patch is not pushed and does not claim to rerun remote Actions. The release build uses the repository's debug signing configuration, so configure a private release keystore before distribution. No physical-device/emulator test is claimed.
