# OpenRemoTV

A phone app for Android TV, Google TV and Fire TV, inspired by [TidyTV](https://github.com/jolan94/tidytv) (MIT),
with a manga look and an original mascot, Remo. It talks to the TV over Wi-Fi with ADB (port 5555), using
[dadb](https://github.com/mobile-dev-inc/dadb), plus a small helper it starts on the TV (`keyserver/`) for instant
keys, files and app switches.

First run: **Pick my TV** (Android TV, Google TV or Fire TV, with the steps to turn on network debugging) and
**Connect**. Then the **Sweep** tab walks through **Backup** (always first; the first backup of each TV is kept as
the original), the **tested list** for this TV (260 TVs from cleanyourtv's `examples/`, matched by model, or the
common list), a switch per app, the **sweep** itself, an optional **clean home screen** (MangaHome or IndiaMinimalist, on the bundled cleanyourtv launcher, or keep the current one), **Done**, **Share it** (a manga card through the share sheet)
and **Undo anytime** (back to the original backup). Rebuild the lists with `python3 tools/make-lists.py ../cleanyourtv`.

- **Remote:** arrows, OK, back, home, play/pause, volume, mute, power, and typing into the TV.
- **Apps:** everything on the TV's home screen; tap to open it on the TV.
- **Send:** a YouTube link plays in YouTube on the TV; a photo or video opens on the TV; any file lands in the TV's
  Download/OpenRemoTV folder. Share from YouTube, Photos or Files to "OpenRemoTV" to send in one tap.
- **Sweep:** turn apps off and on (disabled, never uninstalled); apps the TV needs are locked. "Undo everything"
  turns back on all that this phone turned off. Each change is logged on the TV in /sdcard/OpenRemoTV/changes.txt.
- **Check:** finds apps a TV update switched back on (from the 2026-10-24 cleanup and this app's own changes).

## What's in this repo

| Folder | What it is |
|---|---|
| `app/` | The OpenRemoTV phone app (Kotlin, Jetpack Compose). |
| `app/src/main/assets/themes/manga` | The **MangaHome** theme: a full comic-page home screen (`manga_layout`), Remo and six manga wallpapers. |
| `app/src/main/assets/themes/india` | The **IndiaMinimalist** theme: monuments of India and the world, the Panchang, festivals and glass cards. |
| `homescreen/` | The TV home screen both themes run on: the cleanyourtv launcher (`com.gathin.tvhome`, MIT, see `homescreen/LICENSE-cleanyourtv`) with the MangaHome comic page, Today, radio, games, Phone to TV and the other additions. |
| `keyserver/` | The small helper OpenRemoTV starts on the TV for instant keys, files and app switches. Its built `keyserver.dex` is in `app/src/main/assets`. |
| `tools/` | `make-lists.py` rebuilds the tested debloat lists; `build-all.sh` builds the home screen and the app; `build-keyserver.sh` rebuilds the TV helper. |

## Build and install

`tools/build-all.sh` builds the home screen, bundles it into the app as `assets/manga-home.apk` (OpenRemoTV installs it
on the TV when you pick MangaHome or IndiaMinimalist), then builds the app. JDK 17 and the Android SDK (platform 34)
are needed. Or build the app alone, if `assets/manga-home.apk` is already there:

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
