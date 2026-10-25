package com.openremotv.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Plain JVM checks of the home screen's logic: the sun, the theme, the files the phone may write. */
public class HomeLogicTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** Delhi on 25 October 2026: sunrise about 06:28, sunset about 17:42 (IST), as the weather service reports. */
    @Test public void sunTimesForDelhi() {
        int[] rs = Sun.riseAndSet(LocalDate.of(2026, 10, 25), 28.6139, 77.209, ZoneId.of("Asia/Kolkata"));
        assertNotNull(rs);
        assertEquals(6 * 60 + 28, rs[0], 3);
        assertEquals(17 * 60 + 42, rs[1], 3);
    }

    @Test public void noSunsetInPolarSummer() {
        assertNull(Sun.riseAndSet(LocalDate.of(2026, 6, 21), 78.2, 15.6, ZoneId.of("Europe/Oslo")));
    }

    @Test public void themeFilesOnly() {
        assertEquals("config.json", ThemeProvider.path(Arrays.asList("config.json")));
        assertEquals("wallpapers/01.webp", ThemeProvider.path(Arrays.asList("wallpapers", "01.webp")));
        assertEquals("wallpapers/captions.txt", ThemeProvider.path(Arrays.asList("wallpapers", "captions.txt")));
        assertNull(ThemeProvider.path(Arrays.asList("..", "config.json")));
        assertNull(ThemeProvider.path(Arrays.asList("wallpapers", "../../x.webp")));
        assertNull(ThemeProvider.path(Arrays.asList("shared_prefs", "a.xml")));
        assertNull(ThemeProvider.path(Arrays.asList("wallpapers", "run.sh")));
    }

    @Test public void themeReadsItsSettingsAndSeasons() throws Exception {
        File dir = tmp.newFolder();
        File walls = new File(dir, Theme.WALLPAPERS);
        assertTrue(walls.mkdirs());
        for (String n : new String[]{"01.webp", "02.webp", "07.webp"}) assertTrue(new File(walls, n).createNewFile());
        JSONObject o = new JSONObject("{\"layout\":\"manga\",\"dock_apps\":[\"a.b\",\" \"],\"wallpaper_dim\":200,"
                + "\"wallpaper_months\":{\"07.webp\":[12,1,2]},\"weather_city\":\"Delhi\",\"weather_latitude\":28.6,"
                + "\"weather_longitude\":77.2,\"festival_dates\":[{\"festival\":\"diwali\",\"from\":\"2026-11-08\",\"until\":\"2026-11-09\"}]}");
        Theme t = new Theme(o, dir, "r1");
        assertTrue(t.mangaLayout);
        assertEquals(Arrays.asList("a.b"), t.dockApps);
        assertEquals(90, t.wallpaperDim);
        assertTrue(t.weatherEnabled());
        assertEquals(3, t.wallpapers.size());
        assertEquals(2, t.wallpapersFor(10).size());
        assertEquals(3, t.wallpapersFor(1).size());
        assertNotNull(t.festivalToday(LocalDate.of(2026, 11, 9)));
        assertNull(t.festivalToday(LocalDate.of(2026, 11, 10)));
    }

    @Test public void emptyThemeIsIndiaWithDefaults() {
        Theme t = new Theme(new JSONObject(), null, "");
        assertFalse(t.mangaLayout);
        assertFalse(t.weatherEnabled());
        assertTrue(t.wallpapers.isEmpty());
    }

    @Test public void captionsAreTabSeparated() throws Exception {
        File f = tmp.newFile("captions.txt");
        Files.write(f.toPath(), "01.webp\tTaj Mahal, Agra\nno tab here\n02.webp\tGolden Temple\n".getBytes(StandardCharsets.UTF_8));
        Map<String, String> m = Wallpapers.read(f);
        assertEquals(2, m.size());
        assertEquals("Taj Mahal, Agra", m.get("01.webp"));
    }

    @Test public void radioAndRecipeListsParse() throws Exception {
        List<String> lines = Files.readAllLines(new File("src/main/assets/today/radio.txt").toPath());
        assertTrue(lines.stream().anyMatch(l -> l.split("\\|").length >= 3));
    }
}
