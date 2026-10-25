# Privacy

OpenRemoTV has no accounts, no analytics, no ads and no tracking. Nothing is sent to the project or to anyone else
about you or your TV.

## The phone app

- Talks only to your TV, on your own Wi-Fi: ADB (port 5555), the app's helper on the TV (port 8766), and, once you
  pair with the code the TV shows, the TV's own remote service (ports 6466 and 6467).
- To find your TV, it tries port 5555 on every address of your Wi-Fi network once, when you tap **Find my TV**.
- Keeps, on your phone only: the TV's address, its ADB key, and backups of which apps were on and off.
- **Where the TV is**, for its weather and Panchang, only if you set it: "Use my phone's location" reads the phone's
  last known position once (Android asks your permission first), and "Type a city" looks the name up with
  [Open-Meteo's geocoder](https://open-meteo.com/en/docs/geocoding-api), sending only what you typed. The place is
  kept on the phone and written into the theme on the TV.
- **Your voice**, only while you talk to the TV: after you tap the sparkle key (Android asks for the microphone
  first), the phone records for up to 15 seconds and streams the sound straight to your TV's voice search. Nothing is
  saved on the phone or sent anywhere else by this app. The pairing key stays in the phone's Android keystore.

## The TV home screen (OpenRemoTV Home)

It fetches a few public feeds straight from the TV. Each request goes to that service directly; no project server
sits in between.

| What | Service | What it sends |
| --- | --- | --- |
| Weather and air quality | [Open-Meteo](https://open-meteo.com) (`api.open-meteo.com`, `air-quality-api.open-meteo.com`) | The latitude and longitude of the TV's place (a city, not your exact spot) |
| The TV's city, when the phone didn't set one | [ipwho.is](https://ipwho.is) | A request from the TV's internet address, at most once a day; the answer (city and its coordinates) stays on the TV |
| News ticker | Google News RSS (`news.google.com`) | A request for the public headlines feed |
| Radio | The stations' own stream servers (All India Radio and others in `today/radio.txt`) | A stream request, only when you play a station |
| Live darshan and YouTube shortcuts | YouTube (`www.youtube.com`) | Opens YouTube on the TV, only when you choose it |

Its permissions and why:

| Permission | Why |
| --- | --- |
| Internet, network state | The feeds above |
| Wi-Fi state, precise location | Android needs location permission to read the Wi-Fi network's name for the network button. Location itself is never read |
| Usage stats | The "screen time" card on the Today page. It stays on the TV |
| Kill background processes | The "Free memory" button |
| Write secure settings | Putting the remote's accessibility helper back after the home screen restarts |

Every one of these stays on the TV. The home screen sends nothing about your usage anywhere.
