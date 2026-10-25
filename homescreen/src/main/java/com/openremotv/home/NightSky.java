package com.openremotv.home;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The night sky over a place: the moon's phase and which bright planets are up. Planets come from JPL's
 * approximate Keplerian elements (valid 1800 to 2050, good to a fraction of a degree), which is plenty to say
 * "Jupiter, low in the east". Pure maths, no Android types, so it runs in JVM tests.
 */
final class NightSky {

    static final class Planet {
        final String name;
        /** Degrees above the horizon and compass bearing (0 = north, 90 = east). */
        final double altitude, azimuth;

        Planet(String name, double altitude, double azimuth) {
            this.name = name;
            this.altitude = altitude;
            this.azimuth = azimuth;
        }

        /** "Jupiter, high in the south-east". */
        String describe() {
            String height = altitude > 55 ? "high" : altitude > 25 ? "mid-sky" : "low";
            return name + ", " + height + " in the " + compass(azimuth);
        }
    }

    static final class Report {
        /** 0 = new, 0.5 = full, back to 1 = new. */
        final double phase;
        /** Lit fraction, 0 to 1. */
        final double illumination;
        /** When the planets were looked at: now if it is dark, else 9 pm tonight. */
        final ZonedDateTime at;
        final List<Planet> planets;

        Report(double phase, double illumination, ZonedDateTime at, List<Planet> planets) {
            this.phase = phase;
            this.illumination = illumination;
            this.at = at;
            this.planets = planets;
        }

        String phaseName() {
            double p = phase;
            if (p < 0.03 || p > 0.97) return "New moon";
            if (p < 0.22) return "Waxing crescent";
            if (p < 0.28) return "First quarter";
            if (p < 0.47) return "Waxing gibbous";
            if (p < 0.53) return "Full moon";
            if (p < 0.72) return "Waning gibbous";
            if (p < 0.78) return "Last quarter";
            return "Waning crescent";
        }
    }

    // a, e, I, L, long. perihelion, long. node at J2000, then their rates per century (JPL, Standish).
    private static final String[] NAMES = {"Mercury", "Venus", "Earth", "Mars", "Jupiter", "Saturn"};
    private static final double[][] ELEMENTS = {
            {0.38709927, 0.20563593, 7.00497902, 252.25032350, 77.45779628, 48.33076593,
                    0.00000037, 0.00001906, -0.00594749, 149472.67411175, 0.16047689, -0.12534081},
            {0.72333566, 0.00677672, 3.39467605, 181.97909950, 131.60246718, 76.67984255,
                    0.00000390, -0.00004107, -0.00078890, 58517.81538729, 0.00268329, -0.27769418},
            {1.00000261, 0.01671123, -0.00001531, 100.46457166, 102.93768193, 0.0,
                    0.00000562, -0.00004392, -0.01294668, 35999.37244981, 0.32327364, 0.0},
            {1.52371034, 0.09339410, 1.84969142, -4.55343205, -23.94362959, 49.55953891,
                    0.00001847, 0.00007882, -0.00813131, 19140.30268499, 0.44441088, -0.29257343},
            {5.20288700, 0.04838624, 1.30439695, 34.39644051, 14.72847983, 100.47390909,
                    -0.00011607, -0.00013253, -0.00183714, 3034.74612775, 0.21252668, 0.20469106},
            {9.53667594, 0.05386179, 2.48599187, 49.95424423, 92.59887831, 113.66242448,
                    -0.00125060, -0.00050991, 0.00193609, 1222.49362201, -0.41897216, -0.28867794}};
    private static final double OBLIQUITY = Math.toRadians(23.43928);

    private NightSky() {
    }

    static Report tonight(ZonedDateTime now, double latitude, double longitude) {
        double jdNow = Panchang.julian(now.toInstant().toEpochMilli());
        double elong = Panchang.elongation(jdNow);
        double phase = elong / 360.0;
        double illumination = (1 - Math.cos(Math.toRadians(elong))) / 2;
        ZonedDateTime at = now;
        if (sunAltitude(jdNow, latitude, longitude) > -6) {
            LocalDate day = now.getHour() < 4 ? now.toLocalDate().minusDays(1) : now.toLocalDate();
            at = day.atTime(21, 0).atZone(now.getZone());
        }
        double jd = Panchang.julian(at.toInstant().toEpochMilli());
        List<Planet> up = new ArrayList<>();
        double[] earth = heliocentric(2, jd);
        for (int i : new int[]{1, 4, 3, 5, 0}) {   // brightest first: Venus, Jupiter, Mars, Saturn, Mercury
            double[] p = heliocentric(i, jd);
            double[] hz = horizontal(p[0] - earth[0], p[1] - earth[1], p[2] - earth[2], jd, latitude, longitude);
            if (hz[0] > 8) up.add(new Planet(NAMES[i], hz[0], hz[1]));
        }
        return new Report(phase, illumination, at, up);
    }

    static Report tonight(ZoneId zone, double latitude, double longitude) {
        return tonight(ZonedDateTime.now(zone), latitude, longitude);
    }

    /** Heliocentric ecliptic x, y, z in AU (J2000 ecliptic). */
    static double[] heliocentric(int planet, double jd) {
        double t = (jd - 2451545.0) / 36525.0;
        double[] k = ELEMENTS[planet];
        double a = k[0] + k[6] * t, e = k[1] + k[7] * t;
        double inc = Math.toRadians(k[2] + k[8] * t);
        double l = k[3] + k[9] * t, peri = k[4] + k[10] * t, node = k[5] + k[11] * t;
        double m = Math.toRadians(Panchang.norm(l - peri));
        double w = Math.toRadians(peri - node), om = Math.toRadians(node);
        double ecc = m + e * Math.sin(m);
        for (int i = 0; i < 8; i++) ecc -= (ecc - e * Math.sin(ecc) - m) / (1 - e * Math.cos(ecc));
        double xp = a * (Math.cos(ecc) - e), yp = a * Math.sqrt(1 - e * e) * Math.sin(ecc);
        double cw = Math.cos(w), sw = Math.sin(w), co = Math.cos(om), so = Math.sin(om), ci = Math.cos(inc), si = Math.sin(inc);
        return new double[]{
                (cw * co - sw * so * ci) * xp + (-sw * co - cw * so * ci) * yp,
                (cw * so + sw * co * ci) * xp + (-sw * so + cw * co * ci) * yp,
                sw * si * xp + cw * si * yp};
    }

    /** Ecliptic direction to {altitude, azimuth} in degrees for the place at jd. */
    static double[] horizontal(double x, double y, double z, double jd, double latitude, double longitude) {
        double ye = y * Math.cos(OBLIQUITY) - z * Math.sin(OBLIQUITY);
        double ze = y * Math.sin(OBLIQUITY) + z * Math.cos(OBLIQUITY);
        double ra = Math.atan2(ye, x);
        double dec = Math.atan2(ze, Math.hypot(x, ye));
        double lst = Math.toRadians(Panchang.norm(280.46061837 + 360.98564736629 * (jd - 2451545.0) + longitude));
        double h = lst - ra;
        double lat = Math.toRadians(latitude);
        double alt = Math.asin(Math.sin(lat) * Math.sin(dec) + Math.cos(lat) * Math.cos(dec) * Math.cos(h));
        double az = Math.atan2(Math.sin(h), Math.cos(h) * Math.sin(lat) - Math.tan(dec) * Math.cos(lat));
        return new double[]{Math.toDegrees(alt), Panchang.norm(Math.toDegrees(az) + 180)};
    }

    static double sunAltitude(double jd, double latitude, double longitude) {
        double lon = Math.toRadians(Panchang.sunLongitude(jd));
        return horizontal(Math.cos(lon), Math.sin(lon), 0, jd, latitude, longitude)[0];
    }

    static String compass(double azimuth) {
        String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return names[(int) Math.round(azimuth / 45.0) % 8];
    }
}
