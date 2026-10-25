package com.openremotv.home;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import java.util.Random;

/**
 * Soft background sound for the screensaver, from free recordings (see NOTICE.md): rain on repeat, a brass temple
 * bell (ghanta) rung now and then, or a bansuri flute played with long pauses. The choice (off, rain, bells, rain
 * and bells, flute) is kept in the "ambient" preferences and set on the Today page.
 */
final class AmbientSound {

    static final String[] MODES = {"off", "rain", "bells", "both", "flute"};
    private static final float RAIN_VOLUME = 0.55f, BELL_VOLUME = 0.6f, FLUTE_VOLUME = 0.5f;

    private final Context context;
    private final String mode;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private MediaPlayer rain, bell, flute;
    private boolean running;

    AmbientSound(Context context, String mode) {
        this.context = context.getApplicationContext();
        this.mode = mode;
    }

    static String mode(Context ctx) {
        return ctx.getSharedPreferences("ambient", Context.MODE_PRIVATE).getString("sound", "off");
    }

    static void setMode(Context ctx, String mode) {
        ctx.getSharedPreferences("ambient", Context.MODE_PRIVATE).edit().putString("sound", mode).apply();
    }

    static String label(String mode) {
        switch (mode) {
            case "rain": return L10n.get(R.string.sound_rain);
            case "bells": return L10n.get(R.string.sound_bells);
            case "both": return L10n.get(R.string.sound_both);
            case "flute": return L10n.get(R.string.sound_flute);
            default: return L10n.get(R.string.off);
        }
    }

    void start() {
        if (running || "off".equals(mode)) return;
        running = true;
        if ("rain".equals(mode) || "both".equals(mode)) {
            rain = player(R.raw.rain, true);
            fadeIn(rain, RAIN_VOLUME);
        }
        if ("bells".equals(mode) || "both".equals(mode)) {
            bell = player(R.raw.ghanta, false);
            handler.postDelayed(this::ringBell, 3_000);
        }
        if ("flute".equals(mode)) {
            flute = player(R.raw.bansuri, false);
            handler.postDelayed(this::playFlute, 2_000);
        }
    }

    void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        rain = release(rain);
        bell = release(bell);
        flute = release(flute);
    }

    /** The bell recording, then a pause of half a minute to a minute before it rings again. */
    private void ringBell() {
        if (!running || bell == null) return;
        bell.setVolume(BELL_VOLUME, BELL_VOLUME);
        bell.seekTo(0);
        bell.start();
        bell.setOnCompletionListener(p -> handler.postDelayed(this::ringBell, 30_000 + random.nextInt(30_000)));
    }

    /** The flute piece, faded in, then a rest of a minute or two. */
    private void playFlute() {
        if (!running || flute == null) return;
        flute.seekTo(0);
        flute.start();
        fadeIn(flute, FLUTE_VOLUME);
        flute.setOnCompletionListener(p -> handler.postDelayed(this::playFlute, 60_000 + random.nextInt(60_000)));
    }

    private MediaPlayer player(int res, boolean loop) {
        try {
            MediaPlayer p = MediaPlayer.create(context, res);
            if (p != null) {
                p.setLooping(loop);
                p.setVolume(0f, 0f);
                if (loop) p.start();
            }
            return p;
        } catch (RuntimeException e) {
            return null;   // no sound is better than a crash in the screensaver
        }
    }

    /** Raises the volume to {@code to} over three seconds. */
    private void fadeIn(MediaPlayer p, float to) {
        if (p == null) return;
        for (int i = 1; i <= 10; i++) {
            float v = to * i / 10f;
            handler.postDelayed(() -> {
                if (running) p.setVolume(v, v);
            }, i * 300L);
        }
    }

    private static MediaPlayer release(MediaPlayer p) {
        if (p == null) return null;
        try {
            p.stop();
        } catch (RuntimeException ignored) {
            // never started
        }
        p.release();
        return null;
    }
}
