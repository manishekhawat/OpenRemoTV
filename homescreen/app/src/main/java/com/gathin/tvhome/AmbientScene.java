package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * ambient_screensaver: the screensaver as a slow gallery of the theme's wallpapers (in season, family photos
 * included) with the time, date and weather at the bottom left and the picture's name and fact at the bottom
 * right. Every third named picture is a quiz: "Where is this?" first, the answer twenty seconds later. The
 * text block shifts a little every minute so nothing burns in. The ambient sound plays under it when chosen.
 */
final class AmbientScene {

    private static final long PICTURE_MS = 60_000L;
    private static final long QUIZ_REVEAL_MS = 20_000L;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final ImageView[] images = new ImageView[2];
    private final LinearLayout overlay;
    private final TextView weather, name, fact;
    private final List<File> pictures;
    private final Map<String, String> names, facts;
    private final float density;
    private final int screenW, screenH;
    private AmbientSound sound;
    private int front, index, named;
    private int generation;
    private boolean running;

    private final Runnable next = new Runnable() {
        @Override public void run() {
            showNext();
            handler.postDelayed(this, PICTURE_MS);
        }
    };

    AmbientScene(FrameLayout root, Config cfg, Extras extras) {
        context = root.getContext();
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        density = dm.density;
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        List<File> all = Extras.inSeason(new ArrayList<>(cfg.wallpapers), extras.wallpaperMonths,
                LocalDate.now().getMonthValue());
        pictures = new ArrayList<>(all);
        Collections.shuffle(pictures, random);
        File folder = pictures.isEmpty() ? null : pictures.get(0).getParentFile();
        names = WallpaperCaptions.read(folder);
        facts = folder == null ? Collections.emptyMap() : WallpaperCaptions.readFile(new File(folder, "facts.txt"));
        for (int i = 0; i < 2; i++) {
            images[i] = new ImageView(context);
            images[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
            images[i].setAlpha(0f);
            root.addView(images[i], i, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        View shade = new View(context);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xD9000000, 0x66000000, 0x00000000}));
        root.addView(shade, 2, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(screenH * 0.45f), Gravity.BOTTOM));

        overlay = new LinearLayout(context);
        overlay.setOrientation(LinearLayout.HORIZONTAL);
        overlay.setGravity(Gravity.BOTTOM);
        FrameLayout.LayoutParams olp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        olp.leftMargin = olp.rightMargin = dp(64);
        olp.bottomMargin = dp(48);
        root.addView(overlay, olp);

        LinearLayout left = new LinearLayout(context);
        left.setOrientation(LinearLayout.VERTICAL);
        overlay.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextClock time = new TextClock(context);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        style(time, 72, 0xFFFFFFFF, R.string.font_extralight);
        left.addView(time);
        TextClock date = new TextClock(context);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        style(date, 20, 0xE6FFFFFF, R.string.font_regular);
        left.addView(date);
        weather = text(18, 0xCCFFFFFF, R.string.font_regular);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        wp.topMargin = dp(6);
        left.addView(weather, wp);

        LinearLayout right = new LinearLayout(context);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        overlay.addView(right, new LinearLayout.LayoutParams(dp(380), ViewGroup.LayoutParams.WRAP_CONTENT));
        name = text(24, 0xFFFFFFFF, R.string.font_medium);
        name.setGravity(Gravity.END);
        right.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        fact = text(16, 0xD9FFFFFF, R.string.font_regular);
        fact.setGravity(Gravity.END);
        fact.setSingleLine(false);
        fact.setMaxLines(3);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        fp.topMargin = dp(6);
        right.addView(fact, fp);
    }

    boolean hasPictures() {
        return !pictures.isEmpty();
    }

    void start() {
        if (running) return;
        running = true;
        updateWeather();
        handler.removeCallbacks(next);
        next.run();
        String mode = AmbientSound.mode(context);
        sound = new AmbientSound(mode);
        sound.start();
    }

    void stop() {
        running = false;
        generation++;
        handler.removeCallbacksAndMessages(null);
        for (ImageView i : images) i.animate().cancel();
        if (sound != null) sound.stop();
        sound = null;
    }

    private void showNext() {
        if (pictures.isEmpty()) return;
        File file = pictures.get(index++ % pictures.size());
        int gen = generation;
        new Thread(() -> {
            Bitmap bmp = ClockScene.decode(file, screenW, screenH);
            if (bmp == null) return;
            handler.post(() -> {
                if (gen != generation || !running) {
                    bmp.recycle();
                    return;
                }
                show(file.getName(), bmp);
            });
        }, "ambient-picture").start();
        updateWeather();
        // A small step every picture keeps the text off the same pixels.
        overlay.animate().translationX((random.nextFloat() - 0.5f) * dp(24))
                .translationY(-random.nextFloat() * dp(16)).setDuration(1500).start();
    }

    private void show(String file, Bitmap bmp) {
        ImageView in = images[1 - front];
        ImageView out = images[front];
        front = 1 - front;
        in.setImageDrawable(new BitmapDrawable(context.getResources(), bmp));
        in.setScaleX(1f);
        in.setScaleY(1f);
        in.animate().alpha(1f).setDuration(1800).start();
        in.animate().scaleX(1.08f).scaleY(1.08f).setDuration(PICTURE_MS + 3000).withLayer().start();
        out.animate().alpha(0f).setDuration(1800).withEndAction(() -> out.setImageDrawable(null)).start();
        String title = names.get(file);
        String line = facts.get(file);
        if (title == null) {
            name.setText("");
            fact.setText("");
            return;
        }
        boolean quiz = ++named % 3 == 0;
        if (quiz) {
            name.setText(context.getString(R.string.quiz_question));
            fact.setText(context.getString(R.string.quiz_hint));
            handler.postDelayed(() -> reveal(title, line), QUIZ_REVEAL_MS);
        } else {
            reveal(title, line);
        }
    }

    private void reveal(String title, String line) {
        name.animate().alpha(0f).setDuration(300).withEndAction(() -> {
            name.setText(title);
            fact.setText(line == null ? "" : line);
            name.animate().alpha(1f).setDuration(500).start();
        }).start();
    }

    /** The weather Home last fetched (WeatherPill's cache): "22° Clear · AQI 183 · Moderate". */
    private void updateWeather() {
        SharedPreferences cache = context.getSharedPreferences("weather", Context.MODE_PRIVATE);
        String text = cache.getString("text", "");
        try {
            JSONObject raw = new JSONObject(cache.getString("raw", "{}"));
            if (raw.has("aqi")) text = text + " · " + Widgets.aqiWord(raw.optLong("aqi"));
        } catch (Exception ignored) {
            // the short text is enough
        }
        weather.setText(text);
        weather.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    private TextView text(int sp, int color, int font) {
        TextView t = new TextView(context);
        style(t, sp, color, font);
        return t;
    }

    private void style(TextView t, int sp, int color, int font) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setShadowLayer(dp(8), 0, dp(1), 0xE6000000);
        try {
            t.setTypeface(UiFont.get(context, font));
        } catch (Exception ignored) {
            // the system font is fine
        }
    }
}
