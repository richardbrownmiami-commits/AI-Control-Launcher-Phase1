# AI Control Launcher — Phase 3

Android 11 / API 30 HOME launcher, ARMv7a-only.

## Phase 3
- AI providers: **OpenRouter and Gemini only**, using the requested free/free-tier model lists.
- API settings screen with provider, exact model ID, matching API key, Save and Test Connection controls.
- AI chats with the user and controls themes, layouts, styles, workspaces, icon overrides, icon packs, wallpapers, images and shortcuts.
- Private asset library at `context.filesDir/launcher-assets/` with wallpapers, images, icons, icon-packs, themes, styles and cache. Assets can be browsed, applied or deleted from the Assets screen.
- Image downloads retain HTTPS-only, image MIME validation, decoded-image validation, and the 10 MB limit.
- AI responses are constrained to an allowlisted JSON action protocol; arbitrary AI-generated code is never executed. Chat history persists for the last 8 turns.
- API keys remain in app-private SharedPreferences for this prototype. Android Keystore protection is future work.

## Chat and command examples
The AI must use real HTTPS image URLs for downloads. It never relies on a bundled wallpaper rotation; downloaded assets become the launcher asset library.
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
