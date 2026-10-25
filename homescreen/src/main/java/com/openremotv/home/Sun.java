package com.openremotv.home;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Sunrise and sunset for a place and day, offline, with the NOAA solar equations (good to a minute or two away from
 * the poles). Used by the Panchang and the Today page.
 */
final class Sun {
    private Sun() {}

    /**
     * {sunrise, sunset} as minutes after local midnight on {@code date}, or null when the sun doesn't rise or set
     * that day (polar day or night).
     */
    static int[] riseAndSet(LocalDate date, double latitude, double longitude, ZoneId zone) {
        double offsetMinutes = zone.getRules().getOffset(date.atStartOfDay(zone).toInstant()).getTotalSeconds() / 60.0;
        // Julian day at local noon, then centuries since J2000.
        double jd = date.toEpochDay() + 2440587.5 + 0.5 - offsetMinutes / 1440.0;
        double t = (jd - 2451545.0) / 36525.0;

        double meanLong = norm(280.46646 + t * (36000.76983 + t * 0.0003032));
        double meanAnomaly = 357.52911 + t * (35999.05029 - 0.0001537 * t);
        double eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t);
        double m = Math.toRadians(meanAnomaly);
        double centre = Math.sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t))
                + Math.sin(2 * m) * (0.019993 - 0.000101 * t) + Math.sin(3 * m) * 0.000289;
        double trueLong = meanLong + centre;
        double omega = 125.04 - 1934.136 * t;
        double apparentLong = trueLong - 0.00569 - 0.00478 * Math.sin(Math.toRadians(omega));
        double obliquity = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60
                + 0.00256 * Math.cos(Math.toRadians(omega));
        double declination = Math.asin(Math.sin(Math.toRadians(obliquity)) * Math.sin(Math.toRadians(apparentLong)));

        double y = Math.pow(Math.tan(Math.toRadians(obliquity / 2)), 2);
        double l0 = Math.toRadians(meanLong);
        double equationOfTime = 4 * Math.toDegrees(y * Math.sin(2 * l0) - 2 * eccentricity * Math.sin(m)
                + 4 * eccentricity * y * Math.sin(m) * Math.cos(2 * l0) - 0.5 * y * y * Math.sin(4 * l0)
                - 1.25 * eccentricity * eccentricity * Math.sin(2 * m));

        // The sun's centre 0.833° below the horizon: refraction plus its radius.
        double lat = Math.toRadians(latitude);
        double cosHourAngle = (Math.cos(Math.toRadians(90.833)) - Math.sin(lat) * Math.sin(declination))
                / (Math.cos(lat) * Math.cos(declination));
        if (cosHourAngle < -1 || cosHourAngle > 1) return null;
        double hourAngle = Math.toDegrees(Math.acos(cosHourAngle));

        double noon = 720 - 4 * longitude - equationOfTime + offsetMinutes;
        return new int[]{(int) Math.round(noon - hourAngle * 4), (int) Math.round(noon + hourAngle * 4)};
    }

    private static double norm(double degrees) {
        double d = degrees % 360;
        return d < 0 ? d + 360 : d;
    }
}
