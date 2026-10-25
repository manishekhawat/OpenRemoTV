package com.openremotv.home;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;

/**
 * The home screen's typeface: Outfit (SIL Open Font License, assets/fonts), one weight per use. Each weight is
 * built once per process and shared: building a variable font per text view costs several milliseconds each on a
 * TV's CPU. Android 8 can't pick a weight from a variable font, so it gets the default instance, or the system font
 * if the file can't be read.
 */
final class UiFont {
    static final int EXTRA_LIGHT = 200, LIGHT = 300, REGULAR = 400, MEDIUM = 500;

    private static final Typeface[] cache = new Typeface[10];

    private UiFont() {}

    static synchronized Typeface get(Context context, int weight) {
        int slot = Math.max(0, Math.min(9, weight / 100));
        if (cache[slot] != null) return cache[slot];
        Typeface t;
        try {
            Typeface.Builder b = new Typeface.Builder(context.getAssets(), "fonts/Outfit.ttf");
            if (Build.VERSION.SDK_INT >= 28) b.setFontVariationSettings("'wght' " + weight).setWeight(weight);
            t = b.build();
        } catch (RuntimeException e) {
            t = null;
        }
        if (t == null) t = weight >= MEDIUM ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT;
        cache[slot] = t;
        return t;
    }
}
