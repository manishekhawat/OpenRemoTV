# Contributing to OpenRemoTV

Thank you for helping. Bug reports, TV lists, translations, themes and code are all welcome.

## Ground rules

These keep people's TVs safe. A pull request that breaks one won't be merged.

- **Disable, never uninstall.** Apps are only ever turned off for the user (`pm disable-user`). No `pm uninstall`,
  no root.
- **Back up first.** Nothing changes on a TV before the backup is saved, and Undo must keep working.
- **Leave what the TV needs alone.** Android itself, settings, the keyboard, the Play Store, remote pairing and the TV
  inputs stay locked (`Apps.isProtected`).
- **No tracking.** No analytics, accounts or ads, and no new network calls without a line in [PRIVACY.md](PRIVACY.md).
- **No new permissions** without saying why in the pull request and in PRIVACY.md.
- **No secrets or identifiers** in commits or screenshots: no IP addresses (use `192.168.1.50` in docs), Wi-Fi
  names, emails, serial numbers or ADB keys.

## Build and test

You need JDK 17 and the Android SDK (platform 35, build-tools 35).

```bash
./gradlew :app:assembleRelease          # the app, with the TV helper and the home screen built and bundled
./gradlew test lint                     # unit tests and lint for both apps
./gradlew :app:verifyPaparazziDebug     # screenshot tests of the phone screens
./gradlew :app:recordPaparazziDebug     # re-record them after a deliberate UI change
```

`LiveTvTest` talks to a real TV when you set its address:
`OPENREMOTV_TV_HOST=192.168.1.50 ./gradlew :app:testReleaseUnitTest`.

Before a pull request: the build, tests and lint pass (CI runs them), and every visible UI change has a screenshot
in the pull request.

## Code style

- Kotlin follows the [official style](https://kotlinlang.org/docs/coding-conventions.html); Java follows the
  surrounding code.
- Comments say **why**, in full sentences, especially when working around a TV's quirk.
- Every string people see lives in `res/values/strings.xml`, with its Hindi in `res/values-hi/strings.xml` (write
  "TODO" there if you can't translate it, and say so in the pull request).
- Screens are Compose functions in `app/src/main/java/com/openremotv/ui/screens/`, one file per screen. Custom
  clickable things pass a `Role` so screen readers announce them.
- One topic per pull request, with a title like `app: hold to repeat volume keys` or `homescreen: …`.

## Adding things

- **A TV list:** add or extend an entry in `app/src/main/assets/lists.json` (`id`, `tv`, `os`, `brand`,
  `packages`). Only packages you have turned off on that TV yourself, with Undo checked afterwards.
- **A translation:** copy `app/src/main/res/values/strings.xml` to `values-<language>/strings.xml` and translate
  the text, keeping every `%1$s` and `%1$d`.
- **A theme:** add `app/src/main/assets/themes/<name>/` with a `config.json` (see the two existing themes),
  WebP wallpapers at 1920×1080, and a `CREDITS.md` for any picture you didn't make. Only images you may share under
  an open licence.

## Releases

Maintainers bump `version.properties`, add a `CHANGELOG.md` entry and push a tag `v<version>`. CI then builds,
signs and attaches the APK to a GitHub release.

Release signing reads these environment variables (CI gets them from repository secrets):
`OPENREMOTV_KEYSTORE`, `OPENREMOTV_KEYSTORE_PASSWORD`, `OPENREMOTV_KEY_ALIAS`, `OPENREMOTV_KEY_PASSWORD`. Without
them, builds use the debug key.

By contributing you agree that your work is released under the [MIT licence](LICENSE).
