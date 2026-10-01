# AI Control Launcher — Phase 1

A minimal Android 11 / ARMv7a launcher with a real AI control layer and an isolated internet asset subsystem.

## Implemented
- HOME launcher entry point.
- Installed/launchable app discovery using `LauncherApps`.
- Launcher app grid (text buttons in this phase; icon rendering comes next).
- Persistent API settings: endpoint, model, API key.
- OpenAI-compatible HTTP provider interface.
- AI command -> structured JSON plan.
- Persistent launcher state.
- Allowlisted AI actions:
  - `HIDE_APPS`
  - `SHOW_APPS`
  - `SET_THEME`
  - `SET_LAYOUT`
  - `CREATE_WORKSPACE`
  - `DOWNLOAD_WALLPAPER`
  - `DOWNLOAD_IMAGE`
  - `DOWNLOAD_ICON`
  - `CLEAR_ICON_OVERRIDE`
- Dedicated private asset storage under the app's internal `files/launcher-assets/` directory.
- HTTPS-only image downloading with MIME checks, image decoding validation, and a 10 MB default limit.
- Wallpaper application through Android's `WallpaperManager`.
- Downloaded icon overrides stored per package for future icon-grid rendering.
- Asset catalog for listing and deleting launcher-owned assets.
- ARMv7a-only build filter; no universal APK configuration.

## Asset storage

The launcher owns its downloaded assets:

```
files/
└── launcher-assets/
    ├── wallpapers/
    ├── icons/
    ├── images/
    └── cache/
```

The asset subsystem never writes downloaded content into arbitrary shared Android directories.

## AI asset examples

```json
{"actions":[{"action":"DOWNLOAD_WALLPAPER","url":"https://example.com/wallpaper.jpg","apply":true}]}
```

```json
{"actions":[{"action":"DOWNLOAD_IMAGE","url":"https://example.com/image.png","name":"home-background"}]}
```

```json
{"actions":[{"action":"DOWNLOAD_ICON","url":"https://example.com/icon.png","package":"com.example.app"}]}
```

The AI can request these actions, but the launcher validates the URL and image before accepting the asset.

## API configuration

The launcher UI accepts an API key, model, and OpenAI-compatible endpoint. The default endpoint is the OpenAI Chat Completions endpoint. You can point it at another compatible service by changing the endpoint.

**Security note:** Phase 1 stores the key in app-private SharedPreferences. This is suitable for a prototype, not production-grade secret storage. Move it to Android Keystore-backed protection before production.

## Example command

`Download a dark space wallpaper and use it as my home wallpaper.`

The AI is instructed to return a structured action plan. The launcher executes only the allowlisted action set; it does not execute arbitrary AI-generated code.

## Build

Use JDK 17 and Android SDK 34:

```bash
./gradlew assembleDebug
```

The APK is under `app/build/outputs/apk/debug/`.

## Next phase
- real icon grid using downloaded icon overrides
- provider selector (OpenAI/Gemini/custom)
- Android Keystore secret storage
- richer action validation and preview/apply
- theme/layout renderer
- visual wallpaper layers above the wallpaper
- gestures and animations
- full workspaces and folders
- rollback/snapshots
