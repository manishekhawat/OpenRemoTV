package com.openremotv.home;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The theme's pictures: which one comes next (this month's, shuffled or by name), its caption and fact
 * (wallpapers/captions.txt and facts.txt, one "name&lt;tab&gt;text" line each), and decoding it no larger than the
 * screen, in RGB_565, so a 1080p picture costs 4 MB of a 2 GB TV's memory, not 8.
 */
final class Wallpapers {
    private final Theme theme;
    private final Map<String, String> captions, facts;
    private final Random random = new Random();
    private List<File> order = Collections.emptyList();
    private int next;
    private int month;

    Wallpapers(Theme theme) {
        this.theme = theme;
        File dir = new File(theme.folder, Theme.WALLPAPERS);
        captions = read(new File(dir, "captions.txt"));
        facts = read(new File(dir, "facts.txt"));
    }

    boolean isEmpty() {
        return theme.wallpapers.isEmpty();
    }

    /** The next picture to show, or null when the theme has none. */
    File next() {
        int m = LocalDate.now().getMonthValue();
        if (order.isEmpty() || next >= order.size() || m != month) {
            month = m;
            order = new ArrayList<>(theme.wallpapersFor(m));
            if (theme.shuffle) Collections.shuffle(order, random);
            next = 0;
        }
        return order.isEmpty() ? null : order.get(next++);
    }

    String caption(File f) {
        return f == null ? "" : captions.getOrDefault(f.getName(), "");
    }

    String fact(File f) {
        return f == null ? "" : facts.getOrDefault(f.getName(), "");
    }

    /** "name<TAB>text" lines; anything else is skipped. */
    static Map<String, String> read(File file) {
        Map<String, String> out = new HashMap<>();
        try {
            for (String line : new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).split("\n")) {
                int tab = line.indexOf('\t');
                if (tab > 0) out.put(line.substring(0, tab).trim(), line.substring(tab + 1).trim());
            }
        } catch (Exception ignored) {
            // no file: no captions
        }
        return out;
    }

    /** The picture scaled down (by powers of two) until it is no more than twice the screen, or null. */
    static Bitmap decode(File file, int width, int height) {
        if (file == null) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        try {
            return BitmapFactory.decodeFile(file.getPath(), o);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }
}
