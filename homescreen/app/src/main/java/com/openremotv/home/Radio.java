package com.openremotv.home;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

/**
 * The Today page's radio: one stream at a time through MediaPlayer (HLS for All India Radio, MP3 for the rest).
 * It keeps playing after the page closes, until it is stopped there or the sleep timer ends. The stations are
 * assets/today/radio.txt.
 */
final class Radio {

    private static MediaPlayer player;
    private static String playing = "";
    private static Runnable listener;

    private Radio() {
    }

    static String playing() {
        return playing;
    }

    /** Called on the main thread when playback starts, fails or stops. */
    static void setListener(Runnable r) {
        listener = r;
    }

    static void play(Context ctx, String name, String url) {
        stop();
        MediaPlayer p = new MediaPlayer();
        player = p;
        playing = name + " (tuning…)";
        notifyListener();
        try {
            p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            p.setDataSource(ctx.getApplicationContext(), android.net.Uri.parse(url));
            p.setOnPreparedListener(mp -> {
                if (mp != player) return;
                mp.start();
                playing = name;
                notifyListener();
            });
            p.setOnErrorListener((mp, what, extra) -> {
                if (mp == player) {
                    stop();
                    playing = "";
                    android.widget.Toast.makeText(ctx.getApplicationContext(), name + " is not answering right now",
                            android.widget.Toast.LENGTH_LONG).show();
                    notifyListener();
                }
                return true;
            });
            p.prepareAsync();
        } catch (Exception e) {
            stop();
        }
    }

    static void stop() {
        MediaPlayer p = player;
        player = null;
        playing = "";
        if (p != null) {
            try {
                p.reset();
            } catch (RuntimeException ignored) {
                // already in an error state
            }
            p.release();
        }
        notifyListener();
    }

    private static void notifyListener() {
        Runnable r = listener;
        if (r != null) new Handler(Looper.getMainLooper()).post(r);
    }
}
