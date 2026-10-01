# AI Control Launcher — Phase 1

A minimal Android 11 / ARMv7a launcher with the first real AI control layer.

## Implemented
- HOME launcher entry point.
- Installed/launchable app discovery using `LauncherApps`.
- Launcher app grid (text buttons in this phase; icon rendering comes next).
- Persistent API settings: endpoint, model, API key.
- OpenAI-compatible HTTP provider interface.
- AI command -> structured JSON plan.
- Validated launcher action engine for:
  - `HIDE_APPS`
  - `SHOW_APPS`
  - `SET_THEME`
  - `SET_LAYOUT`
  - `CREATE_WORKSPACE`
- Persistent launcher state.
- ARMv7a-only build filter; no universal APK configuration.

## API configuration
The launcher UI accepts an API key, model, and OpenAI-compatible endpoint. The default endpoint is the OpenAI Chat Completions endpoint. You can point it at another compatible service by changing the endpoint.

**Security note:** Phase 1 stores the key in app-private SharedPreferences. This is suitable for a prototype, not production-grade secret storage. Later we should move it to Android Keystore-backed encryption and add a proper provider settings screen.

## Example command
`Hide YouTube and create a Work workspace.`

The AI is instructed to return only a JSON action plan. The launcher executes only the small allowlisted action set; it does not execute arbitrary AI-generated code.

## Build
Use JDK 17 and Android SDK 34:

```bash
./gradlew assembleDebug
```

The APK is under `app/build/outputs/apk/debug/`.

## Next phase
- real icon grid and folders
- provider selector (OpenAI/Gemini/custom)
- Android Keystore secret storage
- richer action validation and preview/apply
- theme/layout renderer
- wallpaper/image download + cache
- gestures and animations
- workspaces
- rollback/snapshots
