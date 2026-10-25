# KM8 Remote

A phone app for the MECOOL KM8 TV box, inspired by [TidyTV](https://github.com/jolan94/tidytv) (MIT). It talks to
the TV over Wi-Fi with ADB (port 5555), using [dadb](https://github.com/mobile-dev-inc/dadb).

- **Remote:** arrows, OK, back, home, play/pause, volume, mute, power, and typing into the TV.
- **Apps:** everything on the TV's home screen; tap to open it on the TV.
- **Send:** a YouTube link plays in YouTube on the TV; a photo or video opens on the TV; any file lands in the TV's
  Download/KM8Remote folder. Share from YouTube, Photos or Files to "KM8 Remote" to send in one tap.
- **Tidy:** turn apps off and on (disabled, never uninstalled); apps the TV needs are locked. "Undo everything"
  turns back on all that this phone turned off. Each change is logged on the TV in /sdcard/KM8Remote/changes.txt.
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
