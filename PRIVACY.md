# Privacy

OpenRemoTV has no accounts, no analytics, no ads and no tracking. Nothing is sent to the project or to anyone else
about you or your TV.

## The phone app

- Talks only to your TV, on your own Wi-Fi: ADB (port 5555) and the app's helper on the TV (port 8766).
- To find your TV, it tries port 5555 on every address of your Wi-Fi network once, when you tap **Find my TV**.
- Keeps, on your phone only: the TV's address, its ADB key, and backups of which apps were on and off.
- Uses the internet permission only for those local connections. It downloads nothing from the internet.

## The TV home screen (OpenRemoTV Home)

It fetches a few public feeds straight from the TV. Each request goes to that service directly; no project server
sits in between.

| What | Service | What it sends |
| --- | --- | --- |
| Weather and air quality | [Open-Meteo](https://open-meteo.com) (`api.open-meteo.com`, `air-quality-api.open-meteo.com`) | The latitude and longitude set in the theme (a city, not your exact spot) |
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
