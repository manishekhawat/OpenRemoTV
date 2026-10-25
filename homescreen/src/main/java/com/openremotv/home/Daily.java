package com.openremotv.home;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The day's things to read: a Hindi word and a Gita verse (bundled in assets/today, one a day in turn) and the
 * coming calendar (festival_dates, calendar_dates, family_dates and the next Ekadashi, Purnima and Amavasya).
 */
final class Daily {

    static final class Word {
        final String word, sound, meaning, example;

        Word(String[] f) {
            word = f[0];
            sound = f[1];
            meaning = f[2];
            example = f.length > 3 ? f[3] : "";
        }
    }

    static final class Verse {
        final String ref, line1, line2, meaning;

        Verse(String[] f) {
            ref = f[0];
            line1 = f[1];
            line2 = f[2];
            meaning = f[3];
        }
    }

    static final class Event implements Comparable<Event> {
        final String name;
        final LocalDate date;

        Event(String name, LocalDate date) {
            this.name = name;
            this.date = date;
        }

        @Override public int compareTo(Event o) {
            return date.compareTo(o.date);
        }

        /** "Diwali today", "Diwali tomorrow", "Diwali in 12 days", "Diwali Sun 8 Nov". */
        String when(LocalDate today) {
            long days = ChronoUnit.DAYS.between(today, date);
            if (days == 0) return name + " today";
            if (days == 1) return name + " tomorrow";
            if (days < 7) return name + " on " + date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault());
            return name + " in " + days + " days";
        }
    }

    private Daily() {
    }

    static Word word(Context ctx, LocalDate day) {
        List<String[]> rows = read(ctx, "today/words.txt", 3);
        return rows.isEmpty() ? null : new Word(rows.get(pick(day, rows.size(), 0)));
    }

    static Verse verse(Context ctx, LocalDate day) {
        List<String[]> rows = read(ctx, "today/gita.txt", 4);
        return rows.isEmpty() ? null : new Verse(rows.get(pick(day, rows.size(), 7)));
    }

    /** Day number mod count; the offset keeps two lists of similar length from moving in step. */
    static int pick(LocalDate day, int count, int offset) {
        return (int) Math.floorMod(day.toEpochDay() + offset, (long) count);
    }

    /** The coming events from today, nearest first, at most {@code limit}. */
    static List<Event> upcoming(Extras extras, LocalDate today, ZoneId zone, int limit) {
        List<Event> out = new ArrayList<>();
        for (Extras.FestivalDate f : extras.festivalDates) {
            if (!today.isAfter(f.until)) {
                String name = f.greeting.isEmpty() ? title(f.festival) : f.greeting.replaceFirst("^(Happy|Merry) ", "");
                out.add(new Event(name, f.from.isBefore(today) ? today : f.from));
            }
        }
        for (Event e : extras.calendarDates) if (!e.date.isBefore(today)) out.add(e);
        for (Extras.FamilyDate f : extras.familyDates) {
            LocalDate d = f.day.atYear(today.getYear());
            if (d.isBefore(today)) d = f.day.atYear(today.getYear() + 1);
            out.add(new Event(f.name, d));
        }
        if (extras.weatherEnabled()) {
            double lat = extras.weatherLatitude, lon = extras.weatherLongitude;
            LocalDate e = Panchang.next(today, lat, lon, zone, 10, 25);
            if (e != null) out.add(new Event("Ekadashi", e));
            LocalDate p = Panchang.next(today, lat, lon, zone, 14);
            if (p != null) out.add(new Event("Purnima", p));
            LocalDate a = Panchang.next(today, lat, lon, zone, 29);
            if (a != null) out.add(new Event("Amavasya", a));
        }
        Collections.sort(out);
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    static String title(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Lines of an asset split on " | ", skipping blanks and # comments and rows with too few fields. */
    static List<String[]> read(Context ctx, String asset, int minFields) {
        List<String[]> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(ctx.getAssets().open(asset), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] f = line.split("\\s*\\|\\s*");
                if (f.length >= minFields) out.add(f);
            }
        } catch (Exception ignored) {
            // a missing list leaves its card out
        }
        return out;
    }

    static MonthDay monthDay(String s) {
        return MonthDay.parse("--" + s.trim());
    }
}
