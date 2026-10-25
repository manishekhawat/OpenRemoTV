package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays and rotates a theme's local wallpaper images while Home is visible. */
final class WallpaperPlayer {
    private final FrameLayout root;
    private final ImageView image;
    private final int backgroundColor;
    private final List<File> files;
    private final long interval;
    private final String order;
    private final SharedPreferences preferences;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final int width;
    /** config wallpaper_true_color: ARGB_8888 instead of RGB_565. */
    private final Bitmap.Config bitmapConfig;
    private final int height;
    /** wallpaper_dim as an ARGB black, painted into each picture once instead of a full-screen shade view. */
    private final int dimColor;
    /** The picture on screen, released once the next one has been drawn. */
    private Bitmap current;
    private boolean active;
    private boolean closed;
    private boolean loading;
    private String displayed = "";
    /** wallpaper_captions: the shown picture's name from wallpapers/captions.txt; null when off or there are none. */
    private final TextView caption;
    private final Map<String, String> names;
    /** wallpaper_moods: the collection's groups by time of day; empty rotates through all of it. */
    private final Map<String, Set<String>> moods;
    /** wallpaper_months: file name to the months (1 to 12) it shows in. */
    private final Map<String, Set<Integer>> months;
    /** config.xml latitude and longitude for the sun's times, and today's {sunrise, sunset} once worked out. */
    private final String latitude, longitude;
    private LocalDate sunDay;
    private int[] sun;
    /** wallpaper_motion: a slow pan and zoom across each picture, restarted on every change. */
    private final boolean motion;
    private final long motionDuration;
    private boolean motionForward;
    /** wallpaper_facts: a line about the shown picture from wallpapers/facts.txt, under the caption. */
    private final TextView fact;
    private final Map<String, String> facts;
    /** widgets: the monument card shows the name and fact; the caption on the photo stays hidden. */
    private java.util.function.BiConsumer<String, String> captionSink;

    WallpaperPlayer(Context context, FrameLayout root, Config config) {
        this.root = root;
        backgroundColor = config.backgroundColor;
        files = new ArrayList<>(config.wallpapers);
        interval = config.wallpaperRotationMinutes * 60_000L;
        bitmapConfig = config.wallpaperTrueColor ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        // The dim is painted into the picture (decode), so no full-screen shade layer is drawn on every frame.
        dimColor = config.wallpaperDim > 0 ? (config.wallpaperDim * 255 / 100) << 24 : 0;
        order = config.wallpaperOrder;
        preferences = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
        moods = config.wallpaperMoods;
        months = Extras.of(config).wallpaperMonths;
        latitude = moods.isEmpty() ? "" : context.getString(R.string.latitude);
        longitude = moods.isEmpty() ? "" : context.getString(R.string.longitude);
        // Playback state belongs to one collection. Changing the collection starts from its first
        // picture instead of resuming a filename that belonged to the previous one.
        String collection = collectionKey(files);
        if (!collection.equals(preferences.getString("collection", ""))) {
            preferences.edit().putString("collection", collection)
                    .remove("image").remove("changed").apply();
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        names = config.wallpaperCaptions && config.wallpaperEnabled && !files.isEmpty()
                ? WallpaperCaptions.read(files.get(0).getParentFile()) : Collections.emptyMap();
        caption = names.isEmpty() ? null : makeCaption(context, root);
        Extras extras = Extras.of(config);
        motion = extras.wallpaperMotion;
        // One sweep spans a whole rotation, so the picture never stops moving; at least 30 s so it stays slow.
        motionDuration = Math.max(30_000L, interval > 0 ? interval : 60_000L);
        facts = extras.wallpaperFacts && config.wallpaperEnabled && !files.isEmpty()
                ? WallpaperCaptions.readFile(new File(files.get(0).getParentFile(), "facts.txt")) : Collections.emptyMap();
        // The fact shares the caption's panel (showCaption) instead of floating on the photo on its own line.
        fact = null;
        if (caption != null && extras.readableText) {
            // As YouTube labels a video: no box, a dark fade behind the text that runs out to nothing on the
            // side facing the picture.
            float d = context.getResources().getDisplayMetrics().density;
            caption.setBackground(new SoftGlow(new int[]{0xC0000000, 0x8C000000, 0x33000000, 0x00000000},
                    new float[]{0f, 0.45f, 0.78f, 1f}));
            caption.setPadding(Math.round(90 * d), Math.round(22 * d), Math.round(6 * d), Math.round(22 * d));
        }
        if (caption != null && !facts.isEmpty()) {
            caption.setSingleLine(false);
            caption.setMaxLines(3);
            caption.setGravity(Gravity.END);
            caption.setMaxWidth(Math.round(440 * context.getResources().getDisplayMetrics().density));
        }
        if (config.wallpaperEnabled && !files.isEmpty()) {
            root.addView(image, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            showFirst();
        }
    }

    /**
     * The picture Home opens with is decoded right here, on the main thread, as the single wallpaper always was:
     * Home's first frame already has it instead of drawing the plain colour and the picture popping in later.
     * Only the later changes of a rotation decode in the background.
     */
    private void showFirst() {
        String previous = preferences.getString("image", "");
        List<File> playable = playable();
        File first = WallpaperRotation.find(playable, previous);
        if (first == null) first = WallpaperRotation.pick(playable, previous, order, random);
        while (first != null) {
            Bitmap bitmap = decode(first);
            if (bitmap != null) {
                show(bitmap);
                // The picture covers the whole screen: the root's plain colour under it is overdraw.
                root.setBackground(null);
                displayed = first.getName();
                showCaption(false);
                if (!displayed.equals(previous)) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
                return;
            }
            files.remove(first);   // unreadable or over 4K: try another, or keep the plain colour
            playable = playable();
            first = WallpaperRotation.pick(playable, previous, order, random);
        }
    }

    void start() {
        active = true;
        if (current != null) startMotion();
        update();
    }

    void stop() {
        active = false;
        image.animate().cancel();
    }

    /** Drops the wallpaper texture when Home is hidden and Android asks the process to free memory. */
    void releaseForMemoryPressure() {
        if (current == null) return;
        image.setImageDrawable(null);
        // The renderer may still have a queued frame that references this hardware bitmap. Drop our references and
        // let Android reclaim its native buffer after rendering releases it instead of recycling it mid-frame.
        current = null;
        displayed = "";
        root.setBackgroundColor(backgroundColor);
    }

    /** The pictures the rotation picks from now: wallpaper_moods' group for the time of day, or the whole collection. */
    private List<File> playable() {
        return Extras.inSeason(timeOfDayPool(), months, LocalDateTime.now().getMonthValue());
    }

    private List<File> timeOfDayPool() {
        if (moods.isEmpty()) return files;
        LocalDateTime now = LocalDateTime.now();
        if (!now.toLocalDate().equals(sunDay)) {
            sunDay = now.toLocalDate();
            sun = WallpaperMood.sun(sunDay, latitude, longitude, ZoneId.systemDefault());
        }
        return WallpaperMood.select(files, moods, WallpaperMood.timeOfDay(now.getHour() * 60 + now.getMinute(), sun[0], sun[1]));
    }

    void close() {
        active = false;
        closed = true;
        decoder.shutdownNow();
    }

    void update() {
        if (!active || closed || loading || files.isEmpty()) return;
        String previous = preferences.getString("image", "");
        // A picture outside the time of day's group (the group changed) is replaced at once, not after the interval.
        List<File> playable = playable();
        File selected = WallpaperRotation.find(playable, previous);
        long changed = preferences.getLong("changed", 0);
        if (selected == null || WallpaperRotation.due(changed, System.currentTimeMillis(), interval)) {
            selected = WallpaperRotation.pick(playable, previous, order, random);
        }
        if (selected == null || selected.getName().equals(displayed)) return;
        File next = selected;
        loading = true;
        decoder.execute(() -> {
            Bitmap bitmap = decode(next);
            root.post(() -> {
                loading = false;
                if (closed || !active) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null) {
                    files.remove(next);
                    Log.w("TvHome.Wallpaper", "Skipping unreadable image: " + next.getName());
                    update();
                    return;
                }
                show(bitmap);
                displayed = next.getName();
                showCaption(true);
                if (!displayed.equals(previous) || changed == 0) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
            });
        });
    }

    /**
     * Small light text under the top-right buttons, above everything else and never focusable. It sits where the
     * top bar ends in every layout, so it does not cover the clock, the stats line or the tiles.
     */
    private static TextView makeCaption(Context context, FrameLayout root) {
        float dp = context.getResources().getDisplayMetrics().density;
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        text.setTextColor(0xD9FFFFFF);
        text.setShadowLayer(6 * dp, 0, dp, 0xCC000000);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setMaxWidth(Math.round(520 * dp));
        text.setFocusable(false);
        text.setAlpha(0f);
        try {
            text.setTypeface(UiFont.get(context, R.string.font_regular));
        } catch (Exception ignored) {
            // the system font is fine
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        params.topMargin = Math.round(116 * dp);
        params.setMarginEnd(Math.round(62 * dp));
        root.addView(text, params);
        return text;
    }

    /** A wider, dimmer line under the caption for the picture's fact; same style, never focusable. */
    private static TextView makeFact(Context context, FrameLayout root) {
        float dp = context.getResources().getDisplayMetrics().density;
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        text.setTextColor(0xB3FFFFFF);
        text.setShadowLayer(6 * dp, 0, dp, 0xCC000000);
        text.setMaxLines(2);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setGravity(Gravity.END);
        text.setMaxWidth(Math.round(420 * dp));
        text.setFocusable(false);
        text.setAlpha(0f);
        try {
            text.setTypeface(UiFont.get(context, R.string.font_regular));
        } catch (Exception ignored) {
            // the system font is fine
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        params.topMargin = Math.round(152 * dp);
        params.setMarginEnd(Math.round(62 * dp));
        root.addView(text, params);
        return text;
    }

    private void showFact(boolean fade) {
        if (fact == null) return;
        String line = facts.get(displayed);
        fact.animate().cancel();
        if (line == null) {
            fact.setAlpha(0f);
            return;
        }
        if (!fade || fact.getAlpha() == 0f) {
            fact.setText(line);
            fact.setAlpha(1f);
            return;
        }
        fact.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            fact.setText(line);
            fact.animate().alpha(1f).setDuration(350).start();
        }).start();
    }

    /**
     * Ken Burns: the picture view is zoomed past the screen edge and slides between two framings, alternating
     * direction on each change. Scale and translation are view properties, so the GPU moves one texture and the
     * bitmap is never redrawn; the translation never exceeds the zoom margin, so no edge shows.
     */
    private void startMotion() {
        if (!motion || !active) return;
        image.animate().cancel();
        motionForward = !motionForward;
        float near = 1.06f, far = 1.14f;
        float shift = (near - 1f) * width / 2f * 0.9f;
        float lift = (near - 1f) * height / 2f * 0.6f;
        float fromScale = motionForward ? near : far, toScale = motionForward ? far : near;
        float fromX = motionForward ? -shift : shift, toX = -fromX;
        float fromY = motionForward ? lift : -lift, toY = -fromY;
        image.setScaleX(fromScale);
        image.setScaleY(fromScale);
        image.setTranslationX(fromX);
        image.setTranslationY(fromY);
        image.animate().scaleX(toScale).scaleY(toScale).translationX(toX).translationY(toY)
                .setDuration(motionDuration).setInterpolator(new android.view.animation.LinearInterpolator())
                .start();
    }

    /** The displayed picture's name, faded across a change; a picture without a name hides the caption. */
    void redirectCaption(java.util.function.BiConsumer<String, String> sink) {
        captionSink = sink;
        if (caption != null) caption.setVisibility(View.GONE);
        sink.accept(names.get(displayed), facts.get(displayed));
    }

    private void showCaption(boolean fade) {
        if (captionSink != null) {
            captionSink.accept(names.get(displayed), facts.get(displayed));
            return;
        }
        showFact(fade);
        if (caption == null) return;
        String name = names.get(displayed);
        caption.animate().cancel();
        if (name == null) {
            caption.setAlpha(0f);
            return;
        }
        CharSequence shown = withFact(name, facts.get(displayed));
        caption.setContentDescription(shown);
        if (!fade || caption.getAlpha() == 0f) {
            caption.setText(shown);
            caption.setAlpha(1f);
            return;
        }
        caption.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            caption.setText(shown);
            caption.animate().alpha(1f).setDuration(350).start();
        }).start();
    }

    /** The picture's name, and under it its fact a size smaller and dimmer. */
    private static CharSequence withFact(String name, String fact) {
        if (fact == null || fact.isEmpty()) return name;
        android.text.SpannableStringBuilder text = new android.text.SpannableStringBuilder(name).append('\n');
        int start = text.length();
        text.append(fact);
        text.setSpan(new android.text.style.RelativeSizeSpan(0.82f), start, text.length(), 0);
        text.setSpan(new android.text.style.ForegroundColorSpan(0xCCFFFFFF), start, text.length(), 0);
        return text;
    }

    /** Names plus sizes: enough to notice a different collection without hashing every image. */
    private static String collectionKey(List<File> images) {
        StringBuilder key = new StringBuilder();
        for (File file : images) key.append(file.getName()).append(':').append(file.length()).append(';');
        return key.toString();
    }

    /** Puts a picture on screen and releases the previous one after a frame has drawn the new one. */
    private void show(Bitmap bitmap) {
        Bitmap old = current;
        current = bitmap;
        // The wallpaper covers the whole root; avoid drawing the fallback colour under it.
        root.setBackground(null);
        image.setImageBitmap(bitmap);
        startMotion();
        if (old != null && old != bitmap) image.postDelayed(old::recycle, 500);
    }

    private Bitmap decode(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, this.width, this.height);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, this.width, this.height);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = bitmapConfig;
            options.inMutable = dimColor != 0;
            Bitmap decoded = BitmapFactory.decodeFile(file.getPath(), options);
            if (decoded == null) return null;
            if (dimColor != 0) new Canvas(decoded).drawColor(dimColor);
            // One copy in GPU memory instead of a software bitmap plus its texture; the decode stays off the UI
            // thread for rotations, and Home's return no longer uploads 4-8 MB to the GPU.
            Bitmap gpu = decoded.copy(Bitmap.Config.HARDWARE, false);
            if (gpu == null) return decoded;
            decoded.recycle();
            return gpu;
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }
}
