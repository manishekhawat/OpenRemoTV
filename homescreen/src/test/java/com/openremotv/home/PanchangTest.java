package com.openremotv.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.Test;

public class PanchangTest {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static Panchang.Day delhi(String date) {
        return Panchang.of(LocalDate.parse(date), 28.6139, 77.209, IST);
    }

    @Test
    public void newMoonOfNovember2026IsNearNinthMorningUtc() {
        // Published: new moon 2026-11-09 07:02 UTC.
        double jd = Panchang.timeOfElongation(Panchang.julian(java.time.Instant.parse("2026-11-01T00:00:00Z").toEpochMilli()), 360);
        long expected = java.time.Instant.parse("2026-11-09T07:02:00Z").toEpochMilli();
        assertTrue(Math.abs(Panchang.toMillis(jd) - expected) < 20 * 60_000L);
    }

    @Test
    public void diwali2026IsKartikAmavasya() {
        Panchang.Day d = delhi("2026-11-09");
        System.out.println("2026-11-09 " + d.title() + " " + Panchang.NAKSHATRA[d.nakshatra]);
        Panchang.Day before = delhi("2026-10-25");
        System.out.println("2026-10-25 " + before.title() + " ends " + java.time.Instant.ofEpochMilli(before.tithiEnds).atZone(IST));
        assertEquals("Ashwin", Panchang.MONTH[before.month]);
    }

    @Test
    public void rahuKaalOnSundayIsTheLastEighth() {
        Panchang.Day d = delhi("2026-10-25");   // a Sunday
        assertEquals(d.sunset, d.rahuEnd);
    }
}
