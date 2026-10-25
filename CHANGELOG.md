# Changelog

All notable changes, newest first. Versions follow [Semantic Versioning](https://semver.org).

## 0.1.9 (2026-10-26)

### TV home screen
- Live TV: a new TV button beside the radio lists ten free HD channels (Hindi news, movies and sport, and English news), and plays each at its
  sharpest picture (Aaj Tak HD in 1080p, not the 180p the TV's player starts on), full screen. Up and down change
  channel; Back returns Home.
- The weather says whose weather it is: the town now shows on both MangaHome and IndiaMinimalist.
- Memory and Tic-tac-toe are drawn as a comic page on MangaHome.
- The air quality comes last in the weather box, after the temperatures.

### Phone app
- The sparkle key is back on the phone's own speech recogniser, which hands your question to the TV's search. The
  TV's own voice link didn't hear the phone reliably on Android TV 8.

## 0.1.8 (2026-10-26)

- The sparkle key makes the phone the TV's own microphone: pair once with the code the TV shows, then speak, and the
  TV's voice search (Google Assistant or Gemini) hears you as if you held its remote. If the TV's remote service
  doesn't answer, the phone's speech recogniser takes over as before.

## 0.1.7 (2026-10-26)

- The remote's assistant key sits after volume up.

## 0.1.6 (2026-10-26)

### Phone app
- Speak to type: the mic beside the text box turns your words into text for the TV.
- Ask the TV: the sparkle key on the remote takes a spoken question to the TV's Google Assistant or Gemini.
- Typing then GO no longer adds an "a": the TV's on-screen keyboard is closed before Enter is sent.

## 0.1.5 (2026-10-25)

- Picking a theme no longer fails with "INSTALL_FAILED_VERSION_DOWNGRADE" when the TV has a newer test build of the
  home screen: the app replaces it, then copies the theme back in.

## 0.1.4 (2026-10-25)

- The weather, Panchang, sun times and night sky follow where the TV is, not a fixed Delhi: set it from the phone's
  location or a typed city when you pick a theme, or let the TV find its city from its internet address.
- The Today page says when the TV's place isn't known yet, instead of guessing.

## 0.1.3 (2026-10-25)

### TV home screen
- Screensaver sounds are all real, licence-free recordings now: rain (CC0), a brass temple bell, a ghanta (CC0), and a new bansuri flute (public domain).

## 0.1.2 (2026-10-25)

### TV home screen
- The frosted glass lines up exactly with the app tiles and round buttons, and nothing at the row ends is cut off.
- The glass glides from item to item instead of jumping.
- The screensaver's rain is a real recording (CC0), and the temple bell has a clapper strike and a bronze shimmer.

## 0.1.1 (2026-10-25)

### TV home screen
- IndiaMinimalist: a frosted glass pane glides with the remote over the buttons and app tiles again.
- The shade at the top and bottom of the photo is back, and the app row sits at the bottom, clear of the middle.
- Memory and Tic-tac-toe are centred on the screen.
- Everything on the home screen is in Hindi when the TV is set to Hindi, the Panchang included.
- MangaHome's sound-effect bursts are hidden from TalkBack; with reduced motion the focus zoom is instant.

### Phone app
- Picking a theme replaces a home screen signed with a different key instead of failing.

## 0.1.0 (2026-10-25)

The first open-source release.

### Phone app
- Remote with hold-to-repeat keys, Apps, Send (YouTube links, photos, videos, files), Sweep and Check.
- Sweep: backup first, OpenRemoTV's own lists for each TV family or the common list, a switch per app, optional clean home screen, share
  card, and Undo anytime back to the first backup.
- Change the TV's home screen theme straight from the Sweep tab.
- English and Hindi; screen-reader labels, roles and spoken progress; About screen with every credit.
- The TV helper answers only the phone that started it and signs in with an HMAC challenge.

### TV home screen (OpenRemoTV Home)
- MangaHome: a full comic page with Remo, comic Today, Phone to TV, pop-ups and app library, drawn for fast focus
  moves (about 10 ms a frame on a 2 GB box).
- IndiaMinimalist: 10 photos of India, the Panchang, festivals and glass cards.
- Written from scratch for OpenRemoTV: one activity, two pages, a 167 KB APK.

### Project
- MIT licence, third-party notices, security and privacy notes, contributing guide and CI.
- Android 15 SDK (target 35); one Gradle build for the app, the helper and the home screen.
