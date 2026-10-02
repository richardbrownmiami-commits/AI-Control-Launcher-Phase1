# AI Launcher Review and Next Steps

## Executive assessment

The repository already had AI-related provider and action code, but it did **not** have a prompt-to-action loop in the launcher. `MainActivity` previously showed app search, customization and assets only. `AiPlanner`, `AiProvider` and `AiProviders` define a prompt protocol and OpenRouter/Gemini calls; `SettingsActivity` can test a configured connection, but the launcher did not call `createProvider()` to handle user chat. `ActionEngine.stateSummary()` also was not connected to a chat UI. As a result, the existing provider code did not make the launcher conversational.

This change adds a small, explicitly labeled **local command helper** rather than claiming that a model is interpreting prompts. It works without credentials and makes no network request. External-model chat remains an optional future feature.

## What the app does now

The launcher accepts a bounded set of natural-language-style commands. It can create or select themes, change grid layout or card style, hide or restore an app **in this launcher**, and open an installed app. It resolves app names against the locally available launchable-app list; ambiguous names produce a clarification instead of an arbitrary choice. Every recognized operation displays a review dialog before applying it.

Theme creation accepts supported named colors or `#RRGGBB` / `#AARRGGBB` values. If a field is omitted, it uses a dark background (`#080A12`), cyan accent (`#46D2FF`), purple secondary accent (`#9B5CFF`), dark card (`#141723`) and glass style. Theme names, colors and styles are validated before saving. Persisted custom values are validated again when loaded; malformed data falls back to a safe default. A custom theme can use a preset’s name and then takes precedence for that name.

The launcher’s API connection settings are now reachable from its menu, but the screen is still a **connection tester**, not an AI chat screen. The provider code’s prompt/history and JSON action protocol are not yet integrated with the new local command flow.

## Existing architecture and gaps

- `AppRepository` lists and opens launchable apps. Android 11 package visibility now declares narrow launcher and ADW/Nova icon-pack intent queries rather than requesting `QUERY_ALL_PACKAGES`.
- `ActionEngine` persists themes, layout/style settings, hidden-package state, shortcuts, assets and workspaces. Hiding an app only filters it from this launcher’s grid; it does not uninstall, disable or hide it system-wide. Workspace state is stored, but there is no workspace selector in the UI.
- `LauncherAction` is a partial sealed model, while `ActionEngine.applyJson()` separately dispatches string action names. The new local helper has its own small typed command model. A future model-backed flow should converge these into one validated, typed action representation and validate an entire batch before changing state; today a later invalid JSON action could follow earlier side effects.
- Provider keys remain in app-private `SharedPreferences`, which is not encrypted. This change removes prompt/response text from provider log statements and moves the Gemini key out of the URL and into a request header, but it does not migrate previously stored keys to Android Keystore-backed storage.

## Prioritized improvements

1. **Keep the offline path as the baseline; add model chat only as an explicit opt-in.** If conversational AI is desired, connect the existing provider layer behind a user-selected remote mode. Before sending, disclose that the prompt and the minimum relevant launcher context leave the device, which provider receives it, and that provider quota/pricing may apply. Do not silently send app inventory or chat history. Local commands should continue to work with no key or network.
2. **Use one typed, policy-checked action pipeline.** Have both the local parser and any future model produce the same typed actions. Validate schemas, parameters, destination URLs, target app identity and the complete action list before execution. Never execute model-generated code. Show the exact target and effect in the review UI; keep confirmation for launcher-changing actions and add explicit confirmation for deleting assets, changing the device wallpaper, or downloading/applying icon packs.
3. **Protect credentials and private data.** Migrate provider keys to Android Keystore-backed encrypted storage with a safe migration path from existing preferences. Keep prompts, API keys, full provider responses and downloaded asset URLs out of logs and crash reports. Provide a clear “send this prompt to provider” state for remote inference.
4. **Make themes easier to trust and refine.** The new review dialog shows the concrete palette values. A next increment could render a small live preview, check text/background contrast, and offer undo/history. Keep custom palettes as validated data (not generated source code).
5. **Define the app-management boundary.** Label hide/show as launcher-only in UI and help. Add workspace selection only after deciding whether a workspace is a filter, a dock/page, or a persistent profile; the current saved workspace JSON has no navigation behavior. Keep installed-package access limited to explicit launcher queries.
6. **Choose distribution ABIs deliberately.** This build targets `armeabi-v7a` as requested and the NDK warns that an APK with native code but no 64-bit ABI is not acceptable for Google Play’s 64-bit requirement. If Play Store distribution is planned, add and verify `arm64-v8a` while retaining `armeabi-v7a`; that does not remove 32-bit Android 11 support.

## Prompt examples and expected behavior

| Prompt | Expected behavior |
| --- | --- |
| `Create a theme called Ocean with blue background and cyan accent.` | Show a confirmation preview for an Ocean custom theme: background `#174EA6`, accent `#46D2FF`, default purple secondary accent, default dark card and glass style; save and activate it only after Apply. |
| `Make a midnight theme.` | Preview and then apply the built-in midnight preset; do not create a new theme file. |
| `Set layout to dense.` | Preview and then switch the app grid to dense (five columns in the current launcher). |
| `Set card style to neon.` | Preview and then set the card style to neon. |
| `Hide Calculator.` | Resolve the installed Calculator app, show the package/app target in the review step, and remove it only from this launcher’s app grid after confirmation. |
| `Show Calculator.` | Restore a previously hidden app in this launcher after review. |
| `Open Camera.` | Resolve one launchable Camera app and open it only after review. |
| `Hide Maps.` when multiple Maps apps match | Ask for a fuller name; make no change until the user disambiguates. |

The interpreter is intentionally not a general-purpose chatbot: unsupported or underspecified requests are rejected or clarified. The existing provider implementation may support a future conversational mode, but it is not currently active in the launcher UI.
