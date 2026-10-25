package com.openremotv.home;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Html;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * news_ticker: a slim line along the bottom of Home that turns over, every eight seconds, between cricket and
 * India headlines from Google News' RSS. Fetched at most every 15 minutes, only while Home is shown; the headlines
 * are cached so the line is there at once after a restart. Display only: it never takes focus.
 */
final class Ticker {

    private static final long REFRESH_MS = 15 * 60_000L;
    private static final long TURN_MS = 8_000L;
    private static final String CRICKET = "https://news.google.com/rss/search?q=India+cricket+when:1d&hl=en-IN&gl=IN&ceid=IN:en";
    private static final String NEWS = "https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en";
    private static final Pattern TITLE = Pattern.compile("<item>.*?<title>(.*?)</title>", Pattern.DOTALL);

    private final TextView line;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final android.content.SharedPreferences cache;
    private List<String> items = new ArrayList<>();
    private int index;
    private long fetched;
    private boolean fetching, shown;

    private final Runnable turn = new Runnable() {
        @Override public void run() {
            if (!items.isEmpty()) {
                String next = items.get(index++ % items.size());
                line.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                    line.setText(next);
                    line.animate().alpha(1f).setDuration(350).start();
                }).start();
            }
            line.postDelayed(this, TURN_MS);
        }
    };

    Ticker(Context context, FrameLayout root) {
        float d = context.getResources().getDisplayMetrics().density;
        cache = context.getSharedPreferences("ticker", Context.MODE_PRIVATE);
        line = new TextView(context);
        line.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        line.setTextColor(0xF2FFFFFF);
        line.setSingleLine(true);
        line.setEllipsize(TextUtils.TruncateAt.END);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(Math.round(84 * d), Math.round(8 * d), Math.round(84 * d), Math.round(6 * d));
        line.setShadowLayer(6 * d, 0, d, 0xE6000000);
        // A solid band, drawn above the tiles (their elevation would otherwise put a half-shown row on top of it).
        line.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xF2000000, 0xE6000000, 0xB3000000}));
        line.setElevation(40 * d);
        line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        line.setFocusable(false);
        try {
            line.setTypeface(UiFont.get(context, R.string.font_medium));
        } catch (Exception ignored) {
            // the system font is fine
        }
        root.addView(line, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(40 * d), Gravity.BOTTOM));
        String saved = cache.getString("items", "");
        if (!saved.isEmpty()) items = new ArrayList<>(java.util.Arrays.asList(saved.split("\n")));
        fetched = cache.getLong("fetched", 0);
        line.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** manga_style: a paper strip under a thick ink line, with ink comic lettering. */
    void manga() {
        float d = line.getResources().getDisplayMetrics().density;
        line.setBackground(MangaSkin.strip(d));
        line.setTextColor(MangaSkin.INK);
        line.setShadowLayer(0, 0, 0, 0);
        line.setTypeface(MangaSkin.lettering(line.getTypeface()));
    }

    /** Home shown: start turning over, and fetch when the headlines are old. */
    void resume() {
        if (!shown) {
            shown = true;
            line.removeCallbacks(turn);
            turn.run();
        }
        long now = System.currentTimeMillis();
        if (fetching || now - fetched < REFRESH_MS) return;
        fetching = true;
        worker.execute(() -> {
            List<String> fresh = new ArrayList<>();
            List<String> cricket = titles(CRICKET, 4);
            List<String> news = titles(NEWS, 8);
            // Interleave: a cricket line, then two news lines, so neither runs for long on its own.
            int c = 0, n = 0;
            while (c < cricket.size() || n < news.size()) {
                if (c < cricket.size()) fresh.add("🏏  " + cricket.get(c++));
                for (int k = 0; k < 2 && n < news.size(); k++) fresh.add("●  " + news.get(n++));
            }
            line.post(() -> {
                fetching = false;
                fetched = System.currentTimeMillis();
                if (fresh.isEmpty()) return;
                items = fresh;
                index = 0;
                cache.edit().putString("items", TextUtils.join("\n", fresh)).putLong("fetched", fetched).apply();
                line.setVisibility(View.VISIBLE);
            });
        });
    }

    void pause() {
        shown = false;
        line.removeCallbacks(turn);
    }

    /** Up to {@code max} headline titles from a Google News feed, without the " - Source" tail. */
    static List<String> titles(String address, int max) {
        List<String> out = new ArrayList<>();
        try {
            Matcher m = TITLE.matcher(get(address));
            while (m.find() && out.size() < max) {
                String t = Html.fromHtml(m.group(1).replace("<![CDATA[", "").replace("]]>", ""), Html.FROM_HTML_MODE_LEGACY).toString().trim();
                int dash = t.lastIndexOf(" - ");
                if (dash > 20) t = t.substring(0, dash);
                if (!t.isEmpty()) out.add(t);
            }
        } catch (Exception ignored) {
            // no network: keep what is cached
        }
        return out;
    }

    static String get(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 8.0) KM8Home");
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0 && out.size() < 512 * 1024) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }
}
