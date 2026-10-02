# AI Control Launcher

An Android HOME launcher prototype targeted strictly at Android 11 / API 30 and 32-bit ARMv7a. Gradle sets `minSdk=30`, `targetSdk=30`, the manifest declares `maxSdkVersion=30`, CMake targets Android 30, and native packaging is restricted to `armeabi-v7a`.

## Launcher experience

- **Three swipeable home pages:** left, main, and right workspace pages. The selected page persists. Page shortcuts are independently stored, can be moved between pages, moved earlier/later in their grid, or removed. Shortcut capacity follows the configured row/column grid.
- **App drawer:** a distinct Apps button opens a searchable, sortable launchable-app grid. Sorting supports A–Z, Z–A, and package name. Long-press an app to launch it, add a home shortcut, add it to the dock, or hide it from this launcher. Package discovery uses the `MAIN`/`LAUNCHER` `<queries>` declaration; the app does not request `QUERY_ALL_PACKAGES`.
- **Dock:** persisted dock app shortcuts remain separate from home-page shortcuts. Slot count and dock visibility are configurable; the app-drawer button remains available when the dock is hidden.
- **Installed Android widgets:** uses API 30 `AppWidgetHost`/`AppWidgetManager` picker and provider configuration flow. A widget is assigned to the currently selected home page and can be removed. Widget IDs and page placements persist locally.
- **Settings hub:** persist rows, columns, shortcut icon size, labels, dock count/visibility, startup page, drawer sort/search/labels, installed compatible icon pack, layout preset, and active theme.

## Appearance and themes

The theme gallery contains nine original built-in presets: Default, Midnight, Ocean, Ember, Aurora, Sunset, Sage, Paper, and Graphite. Each defines a color palette, card treatment, typography, icon shape, and background treatment. Presets are previewed before applying. Applying a different preset records the previous theme for one-step rollback. No Nova/Lawnchair code or assets are copied or bundled.

Custom themes created through the existing offline helper or configured AI provider are validated and previewed before the user confirms them. The strict JSON allowlist accepts only validated palette and display options. AI plans remain explicitly reviewed/confirmed; model output is never executed as code or passed to a generic action runner.

## Assistant, wallpapers, and assets

- When an OpenRouter or Gemini provider and key are configured in AI Settings, the prompt field sends the request, a short recent conversation, and limited launcher appearance/theme context to that provider. With no active key, the deterministic offline parser remains available.
- The assistant supports validated theme operations and bounded layout/style actions, plus confirmed Wikimedia wallpaper searches and the platform widget picker. Each downloadable Commons result still requires a separate user confirmation.
- Users can select an image through Android's document picker, or search Wikimedia Commons for reusable wallpaper. Only CC0, public-domain, CC BY, and CC BY-SA candidates pass the reuse filter; creator, license, and source are retained and shown. Unknown, NC, and ND licenses are rejected.
- Applying an image as the launcher's background does not change the Android device wallpaper. The separate device-wallpaper option requires explicit confirmation.
- Imported/downloaded assets are stored in app-private `filesDir/launcher-assets/` with MIME, size, and decoded-dimension validation. No third-party APK is installed or modified.

## Privacy and limitations

- AI requests include only the prompt, recent chat, and limited launcher appearance/theme context. Installed-app inventory, local asset names, and local file contents are not sent.
- A Commons search phrase is sent to Wikimedia only after the user confirms the search plan. The selected AI provider receives the request only when configured and used.
- Downloads are not scanned wholesale; an image must be selected through Android's document picker. Wallpapers and icon packs are not represented as installed widgets.
- Home shortcut move/reorder is menu-based (page selection and earlier/later); free-form drag-and-drop and widget resize/reorder are not implemented. Drawer and grid geometry are configurable, but widget size is provider/system controlled.
- API-30-only and ARMv7a constraints intentionally exclude newer Android versions and 64-bit-only devices. No physical-device runtime test is claimed here.

## Build and verification

Requires JDK 17+, Android SDK platform 35, Build Tools 35.0.0, NDK 27.0.12077973, CMake 3.31.6, and the committed Gradle 8.9 wrapper.

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

APK files are written under `app/build/outputs/apk/`. The committed GitHub Actions workflow is unchanged and still checks `sdkVersion:'28'` and `targetSdkVersion:'35'`, which conflicts with this requested API-30-only target. Local verification is not a CI run. The release build currently uses the debug signing key; configure a private release keystore before distribution.
