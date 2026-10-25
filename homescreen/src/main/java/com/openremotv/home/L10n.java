package com.openremotv.home;

import android.content.Context;

/**
 * Text for the classes that have no Context of their own (the Panchang, the night sky, the weather words): the
 * app's resources, set once by each screen as it starts, so they follow the TV's language.
 */
final class L10n {
    private static Context app;

    private L10n() {}

    static void init(Context context) {
        Context a = context.getApplicationContext();
        app = a != null ? a : context;
    }

    static String get(int res, Object... args) {
        return app.getString(res, args);
    }

    static String[] array(int res) {
        return app.getResources().getStringArray(res);
    }
}
