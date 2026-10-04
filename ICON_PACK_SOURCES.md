# App icon-pack source and implementation notes

Verified on 2026-10-04 from the official F-Droid listing and source repository.

- **App:** Appstract Icon Pack
- **Android package:** `dev.appstract.iconpack`
- **Official F-Droid details/install page:** https://f-droid.org/en/packages/dev.appstract.iconpack/
- **Official source repository:** https://github.com/yangchoo/Appstract
- **License:** Apache License 2.0 — https://www.apache.org/licenses/LICENSE-2.0
- **Coverage and compatibility:** The F-Droid listing describes nearly 590 hand-designed icons and support for Nova, Lawnchair, ADW, Apex, Action, and other launchers.
- **No APK installation by this launcher:** AI Control Launcher opens the official F-Droid details page only. Android/F-Droid owns any user-initiated installation; the launcher never downloads or installs pack APKs.

## Source-inspected integration format

The official Appstract source manifest declares Nova (`com.novalauncher.THEME`), ADW (`org.adw.launcher.THEMES`), Apex (`android.intent.action.MAIN` with category `com.anddoes.launcher.THEME`), and Lawnchair (`ch.deletescape.lawnchair.ICONPACK` with category `ch.deletescape.lawnchair.PICK_ICON`) compatibility filters. The pack's `app/src/main/res/xml/appfilter.xml` contains standard `<item component="ComponentInfo{package/activity}" drawable="resource_name"/>` mappings. Its Gradle build also copies `appfilter.xml` into APK assets, while icon PNGs are packaged as drawable resources. The launcher therefore resolves component mappings and then loads the named drawable from the installed pack package.

Themes save Appstract's package identity only when that package is detected as installed and compatible. The launcher parses the installed pack's real component mappings, shows real mapped icons in theme previews, and then applies those mappings to the launcher's app grid/dock/drawer when the user applies the theme. If it is missing, the F-Droid details link is explicit, installation stays user-controlled, and the gallery refreshes on return. OpenMoji is separate optional CC BY-SA 4.0 illustration artwork; its glyphs are not an Android icon pack and never replace real installed app icons.
