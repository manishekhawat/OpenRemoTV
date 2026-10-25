package com.gathin.tvhome;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Today's Panchang (tithi, paksha, lunar month, nakshatra, Rahu kaal) computed offline. The sun and moon come from
 * Jean Meeus' truncated series (moon to about 0.01 degree), which puts a tithi's end within a few minutes; the
 * sidereal positions use the Lahiri ayanamsa. Months are named the North Indian (purnimanta) way: the dark half
 * belongs to the month that ends with the coming full moon's month. Pure maths, no Android types, so it runs in
 * JVM tests.
 */
final class Panchang {

    static final String[] TITHI = {"Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami", "Shashthi",
            "Saptami", "Ashtami", "Navami", "Dashami", "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi"};
    static final String[] MONTH = {"Chaitra", "Vaishakh", "Jyeshtha", "Ashadh", "Shravan", "Bhadrapad",
            "Ashwin", "Kartik", "Margashirsha", "Paush", "Magh", "Phalgun"};
    static final String[] NAKSHATRA = {"Ashwini", "Bharani", "Krittika", "Rohini", "Mrigashira", "Ardra",
            "Punarvasu", "Pushya", "Ashlesha", "Magha", "Purva Phalguni", "Uttara Phalguni", "Hasta", "Chitra",
            "Swati", "Vishakha", "Anuradha", "Jyeshtha", "Mula", "Purva Ashadha", "Uttara Ashadha", "Shravana",
            "Dhanishta", "Shatabhisha", "Purva Bhadrapada", "Uttara Bhadrapada", "Revati"};
    /** Which eighth of the day is Rahu kaal, Monday first (Mon 2nd ... Sun 8th). */
    private static final int[] RAHU_EIGHTH = {2, 7, 5, 6, 4, 3, 8};
    /** Mean speed of the moon away from the sun, degrees a day. */
    private static final double ELONGATION_PER_DAY = 12.190749;

    /** The day's Panchang. Times are epoch milliseconds; rahuStart is 0 when the sun does not rise. */
    static final class Day {
        /** 0 to 29: Shukla Pratipada .. Purnima (14), Krishna Pratipada .. Amavasya (29). */
        final int tithi;
        final long tithiEnds;
        final int month;
        final boolean adhik;
        final int nakshatra;
        final long sunrise, sunset, rahuStart, rahuEnd;

        Day(int tithi, long tithiEnds, int month, boolean adhik, int nakshatra, long sunrise, long sunset,
            long rahuStart, long rahuEnd) {
            this.tithi = tithi;
            this.tithiEnds = tithiEnds;
            this.month = month;
            this.adhik = adhik;
            this.nakshatra = nakshatra;
            this.sunrise = sunrise;
            this.sunset = sunset;
            this.rahuStart = rahuStart;
            this.rahuEnd = rahuEnd;
        }

        boolean shukla() {
            return tithi < 15;
        }

        /** "Purnima", "Amavasya" or the tithi's name within its fortnight. */
        String tithiName() {
            if (tithi == 14) return "Purnima";
            if (tithi == 29) return "Amavasya";
            return TITHI[tithi % 15];
        }

        String monthName() {
            return (adhik ? "Adhik " : "") + MONTH[month];
        }

        /** "Kartik Shukla Chaturdashi", or "Kartik Purnima" / "Kartik Amavasya" with no fortnight. */
        String title() {
            if (tithi == 14 || tithi == 29) return monthName() + " " + tithiName();
            return monthName() + " " + (shukla() ? "Shukla" : "Krishna") + " " + tithiName();
        }
    }

    private Panchang() {
    }

    /** The Panchang of {@code date} at a place; the tithi and nakshatra are those at sunrise, as calendars print. */
    static Day of(LocalDate date, double latitude, double longitude, ZoneId zone) {
        int[] rs = Sun.riseAndSet(date, latitude, longitude, zone);
        long midnight = date.atStartOfDay(zone).toInstant().toEpochMilli();
        long sunrise = rs == null ? midnight + 6 * 3_600_000L : midnight + rs[0] * 60_000L;
        long sunset = rs == null ? midnight + 18 * 3_600_000L : midnight + rs[1] * 60_000L;
        double jd = julian(sunrise);
        double elong = elongation(jd);
        int tithi = (int) Math.floor(elong / 12.0) % 30;
        long ends = toMillis(timeOfElongation(jd, (tithi + 1) * 12.0));
        // Amanta month: the sun's sidereal sign at the new moon that began it; the sign after Meena is Chaitra.
        double newMoon = timeOfElongation(jd - elong / ELONGATION_PER_DAY - 1, 360.0);
        int sign = (int) Math.floor(sidereal(sunLongitude(newMoon), newMoon) / 30.0);
        double nextNewMoon = timeOfElongation(newMoon + 25, 360.0);
        boolean adhik = sign == (int) Math.floor(sidereal(sunLongitude(nextNewMoon), nextNewMoon) / 30.0);
        int month = Math.floorMod(sign + 1, 12);
        // Purnimanta: the dark half already carries the next month's name (an adhik month's dark half keeps it).
        if (tithi >= 15 && !adhik) month = (month + 1) % 12;
        int nakshatra = (int) Math.floor(sidereal(moonLongitude(jd), jd) / (360.0 / 27)) % 27;
        long rahuStart = 0, rahuEnd = 0;
        if (rs != null) {
            long eighth = (sunset - sunrise) / 8;
            int which = RAHU_EIGHTH[date.getDayOfWeek().getValue() - 1];
            rahuStart = sunrise + (which - 1) * eighth;
            rahuEnd = rahuStart + eighth;
        }
        return new Day(tithi, ends, month, adhik, nakshatra, sunrise, sunset, rahuStart, rahuEnd);
    }

    /** The next day on or after {@code from} whose sunrise tithi is one of {@code tithis}, within 40 days, or null. */
    static LocalDate next(LocalDate from, double latitude, double longitude, ZoneId zone, int... tithis) {
        for (int i = 0; i < 40; i++) {
            LocalDate d = from.plusDays(i);
            int t = of(d, latitude, longitude, zone).tithi;
            for (int want : tithis) if (t == want) return d;
        }
        return null;
    }

    // ---- astronomy ----------------------------------------------------------------------------------

    static double julian(long millis) {
        return millis / 86_400_000.0 + 2440587.5;
    }

    static long toMillis(double jd) {
        return Math.round((jd - 2440587.5) * 86_400_000.0);
    }

    /** The moon's distance east of the sun, 0 to 360 degrees. */
    static double elongation(double jd) {
        return norm(moonLongitude(jd) - sunLongitude(jd));
    }

    /**
     * The moment after {@code jd} (within about a month) when the elongation reaches {@code target} degrees
     * (360 = new moon), by Newton steps on the mean speed.
     */
    static double timeOfElongation(double jd, double target) {
        double t = jd + norm(target - elongation(jd)) / ELONGATION_PER_DAY;
        for (int i = 0; i < 6; i++) {
            double diff = norm(target - elongation(t) + 180) - 180;
            t += diff / ELONGATION_PER_DAY;
            if (Math.abs(diff) < 1e-5) break;
        }
        return t;
    }

    /** The sun's apparent ecliptic longitude, degrees. */
    static double sunLongitude(double jd) {
        double t = (jd - 2451545.0) / 36525.0;
        double l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t;
        double m = Math.toRadians(357.52911 + 35999.05029 * t - 0.0001537 * t * t);
        double c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * Math.sin(m)
                + (0.019993 - 0.000101 * t) * Math.sin(2 * m) + 0.000289 * Math.sin(3 * m);
        double omega = Math.toRadians(125.04 - 1934.136 * t);
        return norm(l0 + c - 0.00569 - 0.00478 * Math.sin(omega));
    }

    /** The moon's ecliptic longitude from the largest terms of Meeus' table 47.A, degrees. */
    static double moonLongitude(double jd) {
        double t = (jd - 2451545.0) / 36525.0;
        double lp = 218.3164477 + 481267.88123421 * t - 0.0015786 * t * t;
        double d = Math.toRadians(297.8501921 + 445267.1114034 * t - 0.0018819 * t * t);
        double m = Math.toRadians(357.5291092 + 35999.0502909 * t - 0.0001536 * t * t);
        double mp = Math.toRadians(134.9633964 + 477198.8675055 * t + 0.0087414 * t * t);
        double f = Math.toRadians(93.2720950 + 483202.0175233 * t - 0.0036539 * t * t);
        double e = 1 - 0.002516 * t;
        double sum = 6.288774 * Math.sin(mp)
                + 1.274027 * Math.sin(2 * d - mp)
                + 0.658314 * Math.sin(2 * d)
                + 0.213618 * Math.sin(2 * mp)
                - 0.185116 * e * Math.sin(m)
                - 0.114332 * Math.sin(2 * f)
                + 0.058793 * Math.sin(2 * d - 2 * mp)
                + 0.057066 * e * Math.sin(2 * d - m - mp)
                + 0.053322 * Math.sin(2 * d + mp)
                + 0.045758 * e * Math.sin(2 * d - m)
                - 0.040923 * e * Math.sin(m - mp)
                - 0.034720 * Math.sin(d)
                - 0.030383 * e * Math.sin(m + mp)
                + 0.015327 * Math.sin(2 * d - 2 * f)
                - 0.012528 * Math.sin(mp + 2 * f)
                + 0.010980 * Math.sin(mp - 2 * f)
                + 0.010675 * Math.sin(4 * d - mp)
                + 0.010034 * Math.sin(3 * mp)
                + 0.008548 * Math.sin(4 * d - 2 * mp)
                - 0.007888 * e * Math.sin(2 * d + m - mp)
                - 0.006766 * e * Math.sin(2 * d + m)
                - 0.005163 * Math.sin(d - mp)
                + 0.004987 * e * Math.sin(d + m)
                + 0.004036 * e * Math.sin(2 * d - m + mp);
        double omega = Math.toRadians(125.04 - 1934.136 * t);
        return norm(lp + sum - 0.00478 * Math.sin(omega));
    }

    /** Tropical to sidereal longitude with the Lahiri ayanamsa (24.0 degrees around 2026, growing 50" a year). */
    static double sidereal(double tropical, double jd) {
        double years = (jd - 2451545.0) / 365.25;
        return norm(tropical - (23.85306 + 0.0139667 * years));
    }

    static double norm(double degrees) {
        double d = degrees % 360.0;
        return d < 0 ? d + 360.0 : d;
    }

    /** For tests and the Today page: Panchang at a zoned moment's date. */
    static Day of(ZonedDateTime at, double latitude, double longitude) {
        return of(at.toLocalDate(), latitude, longitude, at.getZone());
    }

    static boolean isWeekday(LocalDate d, DayOfWeek w) {
        return d.getDayOfWeek() == w;
    }

    static Instant instant(long millis) {
        return Instant.ofEpochMilli(millis);
    }
}
