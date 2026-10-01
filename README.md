# AI Control Launcher — Phase 3

Android 11 / API 30 HOME launcher, ARMv7a-only.

## Phase 3
- AI providers: **OpenRouter and Gemini only**, using the requested free/free-tier model lists.
- API settings screen with provider, model, and API key fields.
- AI controls themes, layouts, workspaces, icon overrides, icon packs, wallpapers and images.
- Private asset library at `files/launcher-assets/` with an Assets screen for browsing, applying wallpapers/icon packs, and deleting assets.
- Image downloads retain HTTPS-only, image MIME validation, decoded-image validation, and the 10 MB limit.
- AI responses are constrained to an allowlisted JSON action protocol; arbitrary AI-generated code is not executed.
- API keys remain in app-private SharedPreferences for this prototype. Android Keystore protection is future work.

## Chat and command examples
- `What can you do?`
- `Make a midnight theme`
- `Hide the calculator app`
- `Download a dark space wallpaper and apply it.`
- `Hide these apps: com.example.app, com.example.other.`
- `Create a theme called Ocean with blue background and cyan accent.`
- `Create an icon pack named Minimal using HTTPS icon images for my selected apps.`
- `Apply my Minimal icon pack.`

## Build
```bash
./gradlew assembleDebug
./gradlew assembleRelease
```
