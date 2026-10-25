package com.openremotv.home;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Random;

/**
 * Soft background sound for the screensaver. Rain is a real recording (res/raw/rain.ogg, a 45-second loop by
 * Ylmir, CC0) played on repeat; the temple bell is made on the fly, a struck bronze bell's partials each dying away
 * at its own rate, rung every half minute or so, on one mono stream on its own thread. The choice (off, rain, bells, both) is kept in the "ambient" preferences, set on the
 * Today page.
 */
final class AmbientSound {

    static final String[] MODES = {"off", "rain", "bells", "both"};
    private static final int RATE = 32000;
    /**
     * Partials of a bronze temple bell (ghanta) as multiples of its strike note, with loudness and ring time (s):
     * the hum and fundamental ring longest, the bright upper partials of the clapper's strike die quickly. Each is
     * played as a pair a fraction of a hertz apart, which gives a real bell's slow shimmer.
     */
    private static final double[][] BELL = {
            {0.5, 0.30, 11}, {1.0, 0.55, 8}, {1.2, 0.30, 5}, {1.5, 0.22, 4}, {2.0, 0.30, 3.5},
            {2.51, 0.18, 2.4}, {3.0, 0.14, 1.8}, {4.07, 0.12, 1.2}, {5.24, 0.08, 0.7}, {6.8, 0.06, 0.4}};

    private final android.content.Context context;
    private final String mode;
    private volatile boolean running;
    private Thread thread;
    private android.media.MediaPlayer rainPlayer;

    AmbientSound(android.content.Context context, String mode) {
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
            default: return L10n.get(R.string.off);
        }
    }

    void start() {
        if (running || "off".equals(mode)) return;
        running = true;
        if ("rain".equals(mode) || "both".equals(mode)) startRain();
        if (!"bells".equals(mode) && !"both".equals(mode)) return;
        thread = new Thread(this::play, "ambient-sound");
        thread.setPriority(Thread.MIN_PRIORITY + 1);
        thread.start();
    }

    /** The rain recording on repeat, fading in over three seconds. */
    private void startRain() {
        try {
            android.media.MediaPlayer p = android.media.MediaPlayer.create(context, R.raw.rain);
            if (p == null) return;
            p.setLooping(true);
            p.setVolume(0f, 0f);
            p.start();
            rainPlayer = p;
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            for (int i = 1; i <= 10; i++) {
                float v = 0.55f * i / 10f;
                h.postDelayed(() -> {
                    if (rainPlayer == p) p.setVolume(v, v);
                }, i * 300L);
            }
        } catch (RuntimeException ignored) {
            // no sound is better than a crash in the screensaver
        }
    }

    void stop() {
        running = false;
        if (rainPlayer != null) {
            try {
                rainPlayer.stop();
            } catch (RuntimeException ignored) {
                // already stopped
            }
            rainPlayer.release();
            rainPlayer = null;
        }
        if (thread != null) thread.interrupt();
        thread = null;
    }

    private void play() {
        int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack track;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(Math.max(min, RATE / 2 * 2))
                    .setTransferMode(AudioTrack.MODE_STREAM).build();
        } catch (RuntimeException e) {
            running = false;
            return;
        }
        boolean rain = false;   // rain plays from its recording (startRain)
        boolean bells = "bells".equals(mode) || "both".equals(mode);
        Random random = new Random();
        short[] block = new short[1024];
        double low = 0, high = 0, prev = 0, swell = 0.6, swellTarget = 0.6, drop = 0;
        double[] phase = new double[BELL.length], twin = new double[BELL.length];
        double clickLow = 0;
        double strikeAge = 1e9, strikeNote = 560;
        long nextStrike = bells ? RATE * 3L : Long.MAX_VALUE;
        long sample = 0;
        float fadeIn = 0;
        track.play();
        try {
            while (running) {
                for (int i = 0; i < block.length; i++, sample++) {
                    double out = 0;
                    if (rain) {
                        double white = random.nextGaussian();
                        low += 0.08 * (white - low);              // soft hiss, not a roar
                        high = 0.97 * (high + low - prev);        // take out the rumble
                        prev = low;
                        if (sample % 2205 == 0) swellTarget = 0.45 + 0.4 * random.nextDouble();
                        swell += 0.00002 * (swellTarget - swell);
                        if (random.nextInt(RATE / 6) == 0) drop = 0.5 + random.nextDouble();
                        drop *= 0.995;
                        out += (high * swell * 0.55) + drop * random.nextGaussian() * 0.2;
                    }
                    if (bells) {
                        if (sample >= nextStrike) {
                            strikeAge = 0;
                            strikeNote = 420 + random.nextInt(60);
                            // now and then a second ring a moment later, as a hand bell is shaken
                            nextStrike = sample + (random.nextInt(4) == 0 ? RATE * 7 / 10 : RATE * (18L + random.nextInt(20)));
                        }
                        if (strikeAge < RATE * 12.0) {
                            double t = strikeAge / RATE;
                            double ring = 0;
                            for (int p = 0; p < BELL.length; p++) {
                                double f = strikeNote * BELL[p][0];
                                phase[p] += 2 * Math.PI * f / RATE;
                                // the pair's twin, slightly sharp: the two beat slowly against each other
                                twin[p] += 2 * Math.PI * (f + 0.4 + 0.25 * p) / RATE;
                                double env = BELL[p][1] * Math.exp(-t / BELL[p][2] * 2.0);
                                ring += env * (Math.sin(phase[p]) + 0.7 * Math.sin(twin[p]));
                            }
                            // the clapper's strike: a short metallic click before the ring takes over
                            double click = strikeAge < RATE * 0.03 ? random.nextGaussian() * Math.exp(-strikeAge / (RATE * 0.004)) : 0;
                            clickLow += 0.35 * (click - clickLow);
                            out += ring * 0.28 * Math.min(1, strikeAge / 30.0) + (click - clickLow) * 0.5;
                            strikeAge++;
                        }
                    }
                    if (fadeIn < 1) fadeIn += 1f / (RATE * 3);
                    double v = Math.max(-1, Math.min(1, out * 0.5 * fadeIn));
                    block[i] = (short) (v * 32767);
                }
                if (track.write(block, 0, block.length) < 0) break;
            }
        } finally {
            try {
                track.pause();
                track.flush();
            } catch (RuntimeException ignored) {
                // already released
            }
            track.release();
        }
    }
}
