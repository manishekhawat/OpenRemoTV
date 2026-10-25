package com.openremotv.home;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The theme the phone app put on the TV: {@code config.json} and {@code wallpapers/} in this app's files folder
 * (Android/data/com.openremotv.home/files), written there by OpenRemoTV over ADB. Every key is optional; without a
 * theme Home shows the IndiaMinimalist layout on a plain background.
 *
 * <pre>
 * {
 *   "layout": "manga" | "india",
 *   "dock_apps": ["com.google.android.youtube.tv", ...],
 *   "wallpaper_rotation_minutes": 1, "wallpaper_order": "shuffle" | "name", "wallpaper_dim": 35,
 *   "wallpaper_months": {"07.webp": [12, 1, 2]},
 *   "captions": true, "daily_card": true, "news_ticker": true, "screensaver": true, "reduced_motion": true,
 *   "weather_city": "Mumbai", "weather_latitude": 19.07, "weather_longitude": 72.88,   (or "auto": the TV finds it)
 *   "festival_dates": [{"festival": "diwali", "from": "2026-11-08", "until": "2026-11-09", "greeting": "…"}],
 *   "calendar_dates": [{"name": "Republic Day", "date": "2027-01-26"}],
 *   "family_dates": [{"name": "Maa's birthday", "date": "03-14"}]
 * }
 * </pre>
 */
final class Theme {
    static final String CONFIG = "config.json";
    static final String WALLPAPERS = "wallpapers";
    /** Written last by the phone app; Home reloads when it changes. */
    static final String REVISION = "theme_revision";

    /** "manga": the comic page; anything else: the IndiaMinimalist page. */
    final boolean mangaLayout;
    /** The comic look for pages other than Home (Today, Phone to TV, pop-ups): same as the layout. */
    final boolean mangaStyle;
    final List<String> dockApps;
    final List<File> wallpapers;
    final int rotationMinutes;
    final boolean shuffle;
    /** 0 to 90: how much the wallpaper is darkened behind the cards. */
    final int wallpaperDim;
    final Map<String, Set<Integer>> wallpaperMonths;
    final boolean captions;
    final boolean dailyCard;
    final boolean newsTicker;
    final boolean screensaver;
    final boolean reducedMotion;
    final String weatherCity;
    final double weatherLatitude, weatherLongitude;
    final List<FestivalDate> festivalDates;
    final List<Daily.Event> calendarDates;
    final List<FamilyDate> familyDates;
    final File folder;
    final String revision;

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

    /** The theme folder: shared storage the shell can write to, or private storage when there is none. */
    static File folder(Context context) {
        File dir = context.getExternalFilesDir(null);
        return dir != null ? dir : context.getFilesDir();
    }

    static Theme load(Context context) {
        File dir = folder(context);
        JSONObject o = null;
        try {
            o = new JSONObject(new String(Files.readAllBytes(new File(dir, CONFIG).toPath()), StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // no theme yet, or a broken one: the defaults
        }
        String revision = "";
        try {
            revision = new String(Files.readAllBytes(new File(dir, REVISION).toPath()), StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {
            // never written
        }
        if (o == null) o = new JSONObject();
        // No place from the phone: the city the TV found from its internet address (GeoIp), when it has one.
        String city = o.optString("weather_city", "").trim();
        boolean fromTv = city.isEmpty() || "auto".equalsIgnoreCase(city) || Double.isNaN(o.optDouble("weather_latitude", Double.NaN));
        if (fromTv) {
            Object[] geo = GeoIp.cached(context);
            try {
                if (geo != null) {
                    o.put("weather_city", geo[0]).put("weather_latitude", geo[1]).put("weather_longitude", geo[2]);
                } else {
                    o.remove("weather_city");
                }
            } catch (org.json.JSONException ignored) {
                // keeps the theme without a place
            }
        }
        Theme t = new Theme(o, dir, revision);
        t.placeFromTv = fromTv;
        return t;
    }

    /** True when the theme has no place of its own and the TV looks one up (see {@link GeoIp}). */
    boolean placeFromTv;

    Theme(JSONObject o, File dir, String revision) {
        folder = dir;
        this.revision = revision;
        mangaLayout = "manga".equals(o.optString("layout", ""));
        mangaStyle = mangaLayout;
        dockApps = strings(o.optJSONArray("dock_apps"));
        rotationMinutes = Math.max(1, o.optInt("wallpaper_rotation_minutes", 1));
        shuffle = !"name".equals(o.optString("wallpaper_order", "shuffle"));
        wallpaperDim = Math.max(0, Math.min(90, o.optInt("wallpaper_dim", 35)));
        wallpaperMonths = months(o.optJSONObject("wallpaper_months"));
        captions = o.optBoolean("captions", true);
        dailyCard = o.optBoolean("daily_card", false);
        newsTicker = o.optBoolean("news_ticker", false);
        screensaver = o.optBoolean("screensaver", true);
        reducedMotion = o.optBoolean("reduced_motion", false);
        weatherCity = o.optString("weather_city", "").trim();
        weatherLatitude = o.optDouble("weather_latitude", Double.NaN);
        weatherLongitude = o.optDouble("weather_longitude", Double.NaN);
        festivalDates = festivals(o.optJSONArray("festival_dates"));
        calendarDates = calendar(o.optJSONArray("calendar_dates"));
        familyDates = family(o.optJSONArray("family_dates"));
        wallpapers = images(dir == null ? null : new File(dir, WALLPAPERS));
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

    /** The wallpapers for this month: seasonal ones only in their months, the rest all year. Never none. */
    List<File> wallpapersFor(int month) {
        if (wallpaperMonths.isEmpty()) return wallpapers;
        List<File> out = new ArrayList<>();
        for (File f : wallpapers) {
            Set<Integer> m = wallpaperMonths.get(f.getName());
            if (m == null || m.contains(month)) out.add(f);
        }
        return out.isEmpty() ? wallpapers : out;
    }

    private static List<File> images(File dir) {
        File[] files = dir == null ? null : dir.listFiles((d, name) -> {
            String n = name.toLowerCase(java.util.Locale.ROOT);
            return n.endsWith(".webp") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png");
        });
        if (files == null) return Collections.emptyList();
        Arrays.sort(files);
        return Collections.unmodifiableList(Arrays.asList(files));
    }

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        for (int i = 0; a != null && i < a.length(); i++) {
            String s = a.optString(i, "").trim();
            if (!s.isEmpty()) out.add(s);
        }
        return Collections.unmodifiableList(out);
    }

    private static Map<String, Set<Integer>> months(JSONObject o) {
        Map<String, Set<Integer>> out = new HashMap<>();
        if (o == null) return out;
        for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            String name = it.next();
            JSONArray ms = o.optJSONArray(name);
            Set<Integer> set = new HashSet<>();
            for (int i = 0; ms != null && i < ms.length(); i++) {
                int m = ms.optInt(i, 0);
                if (m >= 1 && m <= 12) set.add(m);
            }
            if (!set.isEmpty()) out.put(name, set);
        }
        return Collections.unmodifiableMap(out);
    }

    private static List<FestivalDate> festivals(JSONArray list) {
        List<FestivalDate> out = new ArrayList<>();
        for (int i = 0; list != null && i < list.length(); i++) {
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

    private static List<Daily.Event> calendar(JSONArray list) {
        List<Daily.Event> out = new ArrayList<>();
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject f = list.optJSONObject(i);
            try {
                if (f != null) out.add(new Daily.Event(f.optString("name", "").trim(), LocalDate.parse(f.optString("date", ""))));
            } catch (RuntimeException ignored) {
                // a malformed entry is left out on its own
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static List<FamilyDate> family(JSONArray list) {
        List<FamilyDate> out = new ArrayList<>();
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject f = list.optJSONObject(i);
            try {
                if (f != null) out.add(new FamilyDate(f.optString("name", "").trim(), Daily.monthDay(f.optString("date", ""))));
            } catch (RuntimeException ignored) {
                // a malformed entry is left out on its own
            }
        }
        return Collections.unmodifiableList(out);
    }
}
