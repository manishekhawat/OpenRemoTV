package com.openremotv.home;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Random;

/**
 * Soft background sound for the screensaver, made on the fly rather than shipped as recordings: rain is filtered
 * noise that swells and fades with scattered drops, and the temple bell is a struck bell's partials, each dying away
 * at its own rate, rung every half minute or so. One mono 22 kHz stream on its own thread; a fraction of a
 * percent of the CPU. The choice (off, rain, bells, both) is kept in the "ambient" preferences, set on the
 * Today page.
 */
final class AmbientSound {

    static final String[] MODES = {"off", "rain", "bells", "both"};
    private static final int RATE = 22050;
    /** Partials of a small temple bell as multiples of its strike note, with their loudness and ring time (s). */
    private static final double[][] BELL = {
            {0.5, 0.35, 9}, {1.0, 0.6, 6}, {1.19, 0.25, 4.5}, {1.51, 0.3, 3.5}, {2.0, 0.22, 3},
            {2.52, 0.16, 2.2}, {2.67, 0.12, 2}, {3.01, 0.1, 1.6}, {4.17, 0.07, 1.1}};

    private final String mode;
    private volatile boolean running;
    private Thread thread;

    AmbientSound(String mode) {
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
        thread = new Thread(this::play, "ambient-sound");
        thread.setPriority(Thread.MIN_PRIORITY + 1);
        thread.start();
    }

    void stop() {
        running = false;
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
        boolean rain = "rain".equals(mode) || "both".equals(mode);
        boolean bells = "bells".equals(mode) || "both".equals(mode);
        Random random = new Random();
        short[] block = new short[1024];
        double low = 0, high = 0, prev = 0, swell = 0.6, swellTarget = 0.6, drop = 0;
        double[] phase = new double[BELL.length];
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
                            strikeNote = 520 + random.nextInt(80);
                            // now and then a second ring a moment later, as a hand bell is shaken
                            nextStrike = sample + (random.nextInt(4) == 0 ? RATE * 7 / 10 : RATE * (18L + random.nextInt(20)));
                        }
                        if (strikeAge < RATE * 10.0) {
                            double t = strikeAge / RATE;
                            double ring = 0;
                            for (int p = 0; p < BELL.length; p++) {
                                phase[p] += 2 * Math.PI * strikeNote * BELL[p][0] / RATE;
                                ring += BELL[p][1] * Math.exp(-t / BELL[p][2] * 2.2) * Math.sin(phase[p]);
                            }
                            out += ring * 0.35 * Math.min(1, strikeAge / 40.0);
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
