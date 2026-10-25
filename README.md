# RemoTV

A phone app for Android TV, Google TV and Fire TV, inspired by [TidyTV](https://github.com/jolan94/tidytv) (MIT),
with a manga look and an original mascot, Remo. It talks to the TV over Wi-Fi with ADB (port 5555), using
[dadb](https://github.com/mobile-dev-inc/dadb), plus a small helper it starts on the TV (`keyserver/`) for instant
keys, files and app switches.

First run: **Pick my TV** (Android TV, Google TV or Fire TV, with the steps to turn on network debugging) and
**Connect**. Then the **Sweep** tab walks through **Backup** (always first; the first backup of each TV is kept as
the original), the **tested list** for this TV (260 TVs from cleanyourtv's `examples/`, matched by model, or the
common list), a switch per app, the **sweep** itself, an optional **clean home screen** (Manga Home, the bundled
cleanyourtv launcher; Projectivy Launcher; or keep), **Done**, **Share it** (a manga card through the share sheet)
and **Undo anytime** (back to the original backup). Rebuild the lists with `python3 tools/make-lists.py ../cleanyourtv`.

- **Remote:** arrows, OK, back, home, play/pause, volume, mute, power, and typing into the TV.
- **Apps:** everything on the TV's home screen; tap to open it on the TV.
- **Send:** a YouTube link plays in YouTube on the TV; a photo or video opens on the TV; any file lands in the TV's
  Download/RemoTV folder. Share from YouTube, Photos or Files to "RemoTV" to send in one tap.
- **Tidy:** turn apps off and on (disabled, never uninstalled); apps the TV needs are locked. "Undo everything"
  turns back on all that this phone turned off. Each change is logged on the TV in /sdcard/RemoTV/changes.txt.
- **Check:** finds apps a TV update switched back on (from the 2026-10-24 cleanup and this app's own changes).

## Build and install

```bash
JAVA_HOME=$(/usr/libexec/java_home) ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew assembleRelease
```

The APK is `app/build/outputs/apk/release/app-release.apk`. Copy it to the phone and open it to install
(allow "Install unknown apps" for the app you open it with).

## First connection

Phone and TV on the same Wi-Fi. Open the app, press "Find my TV" (or type the address, 192.168.1.50), then
"Connect". The TV shows "Allow USB debugging?": tick "Always allow" and press OK. That is needed only once.

`LiveTvTest` checks the library against the real TV from a Mac: `KM8_HOST=192.168.1.50 ./gradlew testReleaseUnitTest`
(macOS must allow Java on the local network).
