package com.openremotv.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.Test;

public class NightSkyTest {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Test
    public void sunIsHighAtDelhiNoonAndDownAtMidnight() {
        double noon = Panchang.julian(ZonedDateTime.of(2026, 6, 21, 12, 20, 0, 0, IST).toInstant().toEpochMilli());
        double night = Panchang.julian(ZonedDateTime.of(2026, 6, 21, 0, 20, 0, 0, IST).toInstant().toEpochMilli());
        assertTrue(NightSky.sunAltitude(noon, 28.6139, 77.209) > 80);
        assertTrue(NightSky.sunAltitude(night, 28.6139, 77.209) < -30);
    }

    @Test
    public void lateOctober2026EveningShowsSaturnNotVenus() {
        NightSky.Report r = NightSky.tonight(ZonedDateTime.of(2026, 10, 25, 15, 0, 0, 0, IST), 28.6139, 77.209);
        StringBuilder s = new StringBuilder();
        for (NightSky.Planet p : r.planets) s.append(p.describe()).append("; ");
        System.out.println(r.at + " " + r.phaseName() + " " + Math.round(r.illumination * 100) + "% " + s);
        assertEquals(21, r.at.getHour());
        assertTrue(s.toString().contains("Saturn"));
        assertTrue(!s.toString().contains("Venus"));
        assertEquals("Full moon", r.phaseName());   // full on the 26th, 09:42 IST
    }
}
