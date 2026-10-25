# Changelog

All notable changes, newest first. Versions follow [Semantic Versioning](https://semver.org).

## 0.1.0 (2026-10-25)

The first open-source release.

### Phone app
- Remote with hold-to-repeat keys, Apps, Send (YouTube links, photos, videos, files), Sweep and Check.
- Sweep: backup first, 260 tested TV lists or the common list, a switch per app, optional clean home screen, share
  card, and Undo anytime back to the first backup.
- Change the TV's home screen theme straight from the Sweep tab.
- English and Hindi; screen-reader labels, roles and spoken progress; About screen with every credit.
- The TV helper answers only the phone that started it and signs in with an HMAC challenge.

### TV home screen (OpenRemoTV Home)
- MangaHome: a full comic page with Remo, comic Today, Phone to TV, pop-ups and app library, drawn for fast focus
  moves (about 10 ms a frame on a 2 GB box).
- IndiaMinimalist: 10 photos of India, the Panchang, festivals and glass cards.
- Forked from cleanyourtv; the race-weekend panel and the Shamsi and lunar dates were removed.

### Project
- MIT licence, third-party notices, security and privacy notes, contributing guide and CI.
- Android 15 SDK (target 35); one Gradle build for the app, the helper and the home screen.
