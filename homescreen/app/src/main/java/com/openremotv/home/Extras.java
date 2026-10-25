package com.openremotv.home;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Theme keys added for the KM8 home screen, read straight from the theme's config.json so the long
 * {@link Config} constructor stays as it is. Every key is optional and off unless the theme sets it.
 */
final class Extras {
    /** wallpaper_motion: a slow Ken Burns pan and zoom across each wallpaper. */
    final boolean wallpaperMotion;
    /** wallpaper_facts: a short fact about the shown picture from wallpapers/facts.txt, under its caption. */
    final boolean wallpaperFacts;
    /** voice_pill: a microphone pill in the top bar that opens the TV's voice assistant. */
    final boolean voicePill;
    /** sort_by_recent: grid apps in the order they were last opened from Home; the dock stays as set. */
    final boolean sortByRecent;
    /** date_below_clock: the day and date on their own line under the time instead of beside it. */
    final boolean dateBelowClock;
    /** readable_text: a dark gradient behind the header and soft shadows on the clock, date and names. */
    final boolean readableText;
    /** widgets: glass cards for the clock (with an analog face), the weather and the wallpaper's monument. */
    final boolean widgets;
    /** weather_city, weather_latitude, weather_longitude: the weather and air quality pill; empty city is off. */
    final String weatherCity;
    final double weatherLatitude, weatherLongitude;
    /** festival_dates: [{"festival": "diwali", "from": "2026-11-07", "until": "2026-11-09"}, ...]. */
    final List<FestivalDate> festivalDates;
    /** today_line: a line under the cards with the Panchang (tithi, Rahu kaal) and the next calendar event. */
    final boolean todayLine;
    /** today_page: a top-bar pill opening the Today page (Panchang, calendar, word, verse, sky, storage). */
    final boolean todayPage;
    /** sleep_timer: a moon pill that puts the TV to sleep after 30, 60 or 90 minutes. */
    final boolean sleepTimer;
    /** games_pill: a dice pill in the top bar listing the games (Memory, Tic-tac-toe). */
    final boolean gamesPill;
    /** more_pills: top-bar pills for Free memory, Live darshan, Phone to TV and Recipe of the day. */
    final boolean morePills;
    /** manga_style: the glass cards become comic panels (paper, ink border, hard shadow) with ink lettering. */
    final boolean mangaStyle;
    /** manga_layout: Home as a comic page (ComicHome) in place of the usual layout. */
    final boolean mangaLayout;
    /** radio_pill: a radio pill in the top bar listing the stations; the playing one is marked. */
    final boolean radioPill;
    /** daily_card: the monument card turns over to the Hindi word, the Gita verse or the night sky each half minute. */
    final boolean dailyCard;
    /** family_dates: [{"name": "Maa's birthday", "date": "03-14"}, ...], every year (month-day). */
    final List<FamilyDate> familyDates;
    /** wallpaper_months: {"14.jpg": [12, 1, 2], ...}: seasonal pictures, shown only in their months. */
    final java.util.Map<String, java.util.Set<Integer>> wallpaperMonths;
    /** news_ticker: cricket and India headlines turning over along the bottom of Home. */
    final boolean newsTicker;
    /** ambient_screensaver: the screensaver rotates the wallpapers with the time, weather and a monument quiz. */
    final boolean ambientScreensaver;
    /** calendar_dates: [{"name": "Republic Day", "date": "2027-01-26"}, ...], once. */
    final List<Daily.Event> calendarDates;

    static final class FamilyDate {
        final String name;
        final MonthDay day;

        FamilyDate(String name, MonthDay day) {
            this.name = name;
            this.day = day;
        }
    }

    static final class FestivalDate {
        final String festival, greeting, accent;
        final LocalDate from, until;

        FestivalDate(String festival, String greeting, String accent, LocalDate from, LocalDate until) {
            this.festival = festival;
            this.greeting = greeting;
            this.accent = accent;
            this.from = from;
            this.until = until;
        }
    }

    private Extras(JSONObject o, Config config) {
        boolean motionDefault = config.animations && !config.reducedMotion;
        wallpaperMotion = o != null && o.optBoolean("wallpaper_motion", false) && motionDefault;
        wallpaperFacts = o != null && o.optBoolean("wallpaper_facts", false);
        voicePill = o != null && o.optBoolean("voice_pill", false);
        sortByRecent = o != null && o.optBoolean("sort_by_recent", false);
        dateBelowClock = o != null && o.optBoolean("date_below_clock", false);
        readableText = o != null && o.optBoolean("readable_text", false);
        widgets = o != null && o.optBoolean("widgets", false);
        weatherCity = o == null ? "" : o.optString("weather_city", "").trim();
        weatherLatitude = o == null ? Double.NaN : o.optDouble("weather_latitude", Double.NaN);
        weatherLongitude = o == null ? Double.NaN : o.optDouble("weather_longitude", Double.NaN);
        festivalDates = o == null ? Collections.emptyList() : festivalDates(o.optJSONArray("festival_dates"));
        todayLine = o != null && o.optBoolean("today_line", false);
        todayPage = o != null && o.optBoolean("today_page", false);
        sleepTimer = o != null && o.optBoolean("sleep_timer", false);
        gamesPill = o != null && o.optBoolean("games_pill", false);
        morePills = o != null && o.optBoolean("more_pills", false);
        mangaStyle = o != null && o.optBoolean("manga_style", false);
        mangaLayout = o != null && o.optBoolean("manga_layout", false);
        radioPill = o != null && o.optBoolean("radio_pill", false);
        dailyCard = o != null && o.optBoolean("daily_card", false);
        List<FamilyDate> family = new ArrayList<>();
        List<Daily.Event> dates = new ArrayList<>();
        JSONArray fl = o == null ? null : o.optJSONArray("family_dates");
        for (int i = 0; fl != null && i < fl.length(); i++) {
            JSONObject f = fl.optJSONObject(i);
            try {
                if (f != null) family.add(new FamilyDate(f.optString("name", "").trim(), Daily.monthDay(f.optString("date", ""))));
            } catch (RuntimeException ignored) {
                // a malformed entry is left out on its own
            }
        }
        JSONArray cl = o == null ? null : o.optJSONArray("calendar_dates");
        for (int i = 0; cl != null && i < cl.length(); i++) {
            JSONObject f = cl.optJSONObject(i);
            try {
                if (f != null) dates.add(new Daily.Event(f.optString("name", "").trim(), LocalDate.parse(f.optString("date", ""))));
            } catch (RuntimeException ignored) {
                // a malformed entry is left out on its own
            }
        }
        java.util.Map<String, java.util.Set<Integer>> seasons = new java.util.HashMap<>();
        JSONObject wm = o == null ? null : o.optJSONObject("wallpaper_months");
        if (wm != null) {
            for (java.util.Iterator<String> it = wm.keys(); it.hasNext(); ) {
                String name = it.next();
                JSONArray ms = wm.optJSONArray(name);
                java.util.Set<Integer> set = new java.util.HashSet<>();
                for (int i = 0; ms != null && i < ms.length(); i++) {
                    int m = ms.optInt(i, 0);
                    if (m >= 1 && m <= 12) set.add(m);
                }
                if (!set.isEmpty()) seasons.put(name, set);
            }
        }
        wallpaperMonths = Collections.unmodifiableMap(seasons);
        newsTicker = o != null && o.optBoolean("news_ticker", false);
        ambientScreensaver = o != null && o.optBoolean("ambient_screensaver", false);
        familyDates = Collections.unmodifiableList(family);
        calendarDates = Collections.unmodifiableList(dates);
    }

    /** wallpaper_months: pictures listed there only in their months; the rest all year. Never leaves none. */
    static List<java.io.File> inSeason(List<java.io.File> pool, java.util.Map<String, java.util.Set<Integer>> months, int month) {
        if (months.isEmpty()) return pool;
        List<java.io.File> out = new ArrayList<>();
        for (java.io.File f : pool) {
            java.util.Set<Integer> m = months.get(f.getName());
            if (m == null || m.contains(month)) out.add(f);
        }
        return out.isEmpty() ? pool : out;
    }

    /** Extras from the theme alone, for screens other than Home (the Today page, the screensaver). */
    static Extras load(android.content.Context ctx) {
        return of(Config.load(ctx));
    }

    static Extras of(Config config) {
        return new Extras(config.overlay, config);
    }

    boolean weatherEnabled() {
        return !weatherCity.isEmpty() && !Double.isNaN(weatherLatitude) && !Double.isNaN(weatherLongitude);
    }

    /** The festival whose dates include today, or null. */
    FestivalDate festivalToday(LocalDate today) {
        for (FestivalDate f : festivalDates) {
            if (!today.isBefore(f.from) && !today.isAfter(f.until)) return f;
        }
        return null;
    }

    private static List<FestivalDate> festivalDates(JSONArray list) {
        if (list == null) return Collections.emptyList();
        List<FestivalDate> out = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject f = list.optJSONObject(i);
            if (f == null) continue;
            try {
                LocalDate from = LocalDate.parse(f.optString("from", ""));
                LocalDate until = LocalDate.parse(f.optString("until", f.optString("from", "")));
                String name = f.optString("festival", "").trim();
                if (name.isEmpty() || until.isBefore(from)) continue;
                out.add(new FestivalDate(name, f.optString("greeting", "").trim(), f.optString("accent_color", "").trim(), from, until));
            } catch (RuntimeException ignored) {
                // a malformed entry is left out on its own
            }
        }
        return Collections.unmodifiableList(out);
    }
}
