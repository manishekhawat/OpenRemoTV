package com.openremotv.home;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Three glass cards under the round buttons on IndiaMinimalist, in place of loose header
 * text: a clock card (analog face, time, date), a weather card (now, feels like, humidity, wind, AQI, sun times and
 * the next four days) and the wallpaper's monument with its fact. Plain translucent panels, no blur: a 2 GB box
 * cannot afford a live blur behind them. Nothing here takes focus.
 */
final class Widgets {
    private final Context context;
    private final float density;
    private final LinearLayout band;
    private final TextView greeting;
    private final TextView weatherNow, weatherCondition, weatherDetails, weatherAir, weatherSun;
    private final LinearLayout forecast;
    private final LinearLayout weatherCard;
    /** the Gita verse, taking the weather card's place every other minute. */
    private final LinearLayout weatherNowBox, verseBox;
    /** the weather's town, shown beside the sky's word so it's clear whose weather it is. */
    String place = "";
    private final TextView verseText, verseMeaning;
    private boolean verseShown;
    private final Runnable swapWeather = new Runnable() {
        @Override public void run() {
            if (verseText.length() > 0) showVerse(!verseShown);
            weatherCard.postDelayed(this, 60_000L);
        }
    };
    private final TextView monumentName, monumentFact;
    private final LinearLayout monumentCard;
    private final FrameLayout root;
    private final int lineTopDp;
    /** room kept on the right for the pills (six of 42dp with 10dp gaps, and the edge margin). */
    private int rightClearDp = 400;
    /** the Panchang and calendar line under the cards, made on first use. */
    private TextView todayLine;
    /** {title, body} pairs the monument card turns over to half a minute after each new picture. */
    private java.util.List<String[]> daily = java.util.Collections.emptyList();
    private int dailyIndex;
    private String shownName;
    private final Runnable flip = new Runnable() {
        @Override public void run() {
            if (daily.isEmpty() || shownName == null) return;
            String[] d = daily.get(dailyIndex++ % daily.size());
            turnTo(d[0], d[1]);
        }
    };

    /** MangaHome: paper cards with an ink border and a hard ink shadow, and ink lettering. */
    private final boolean manga;

    Widgets(Context context, FrameLayout root, int topDp, int heightDp) {
        this(context, root, topDp, heightDp, false);
    }

    Widgets(Context context, FrameLayout root, int topDp, int heightDp, boolean manga) {
        this.context = context;
        this.manga = manga;
        this.root = root;
        lineTopDp = 26;
        density = context.getResources().getDisplayMetrics().density;
        band = new LinearLayout(context);
        band.setOrientation(LinearLayout.HORIZONTAL);
        band.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                dp(heightDp), Gravity.TOP);
        lp.topMargin = dp(topDp);
        lp.leftMargin = dp(68);
        lp.rightMargin = dp(68);
        root.addView(band, lp);

        // Clock: the face, then the time, the date and the greeting.
        LinearLayout clockCard = card(LinearLayout.HORIZONTAL);
        clockCard.setGravity(Gravity.CENTER_VERTICAL);
        AnalogClockView face = new AnalogClockView(context);
        if (manga) face.useInk();
        clockCard.addView(face, new LinearLayout.LayoutParams(dp(heightDp - 34), dp(heightDp - 34)));
        LinearLayout clockText = new LinearLayout(context);
        clockText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ctp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        ctp.setMarginStart(dp(12));
        clockCard.addView(clockText, ctp);
        greeting = text(10, 0xA6FFFFFF, UiFont.MEDIUM);
        greeting.setLetterSpacing(0.2f);
        greeting.setSingleLine(false);
        greeting.setMaxLines(2);
        greeting.setAllCaps(true);   // after setSingleLine, which replaces the caps transformation
        greeting.setLineSpacing(dp(2), 1f);
        clockText.addView(greeting);
        LinearLayout timeRow = new LinearLayout(context);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.BOTTOM);
        TextClock time = new TextClock(context);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        style(time, 24, 0xFFFFFFFF, UiFont.LIGHT);
        time.setEllipsize(null);
        timeRow.addView(time);
        TextClock ampm = new TextClock(context);
        ampm.setFormat12Hour("a");
        ampm.setFormat24Hour("");
        style(ampm, 11, 0xB3FFFFFF, UiFont.MEDIUM);
        ampm.setAllCaps(true);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.setMarginStart(dp(4));
        ap.bottomMargin = dp(4);
        timeRow.addView(ampm, ap);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(2);
        tp.bottomMargin = dp(2);
        clockText.addView(timeRow, tp);
        TextClock date = new TextClock(context);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        style(date, 12, 0xCCFFFFFF, UiFont.REGULAR);
        clockText.addView(date);
        band.addView(clockCard, weight(0.29f, false));

        // Weather: now on the left, the next four days on the right.
        weatherCard = card(LinearLayout.HORIZONTAL);
        weatherCard.setGravity(Gravity.CENTER_VERTICAL);
        weatherCard.setVisibility(View.INVISIBLE);
        LinearLayout now = new LinearLayout(context);
        weatherNowBox = now;
        now.setOrientation(LinearLayout.VERTICAL);
        weatherCard.addView(now, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.BOTTOM);
        weatherNow = text(26, 0xFFFFFFFF, UiFont.LIGHT);
        top.addView(weatherNow);
        weatherCondition = text(13, 0xE6FFFFFF, UiFont.MEDIUM);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMarginStart(dp(8));
        cp.bottomMargin = dp(6);
        top.addView(weatherCondition, cp);
        now.addView(top);
        weatherDetails = text(11, 0xCCFFFFFF, UiFont.REGULAR);
        now.addView(weatherDetails);
        weatherAir = text(11, 0xCCFFFFFF, UiFont.REGULAR);
        now.addView(weatherAir);
        weatherSun = text(11, 0xCCFFFFFF, UiFont.REGULAR);
        now.addView(weatherSun);
        forecast = new LinearLayout(context);
        forecast.setOrientation(LinearLayout.HORIZONTAL);
        weatherCard.addView(forecast, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        verseBox = new LinearLayout(context);
        verseBox.setOrientation(LinearLayout.VERTICAL);
        verseBox.setVisibility(View.GONE);
        // As large as the card allows: each text shrinks only as far as its longest verse needs.
        verseText = text(16, 0xFFFFFFFF, UiFont.REGULAR);
        verseText.setSingleLine(false);
        verseText.setMaxLines(2);
        verseText.setEllipsize(null);
        verseText.setTypeface(android.graphics.Typeface.DEFAULT);   // the UI font has no Devanagari
        verseText.setAutoSizeTextTypeUniformWithConfiguration(11, 17, 1, TypedValue.COMPLEX_UNIT_SP);
        verseBox.addView(verseText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        verseMeaning = text(13, 0xE6FFFFFF, UiFont.REGULAR);
        verseMeaning.setSingleLine(false);
        verseMeaning.setMaxLines(2);
        verseMeaning.setEllipsize(null);
        verseMeaning.setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
        mp.topMargin = dp(2);
        verseBox.addView(verseMeaning, mp);
        weatherCard.addView(verseBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        band.addView(weatherCard, weight(0.43f, true));

        // Monument: the wallpaper's name and its fact.
        monumentCard = card(LinearLayout.VERTICAL);
        monumentCard.setGravity(Gravity.CENTER_VERTICAL);
        monumentCard.setVisibility(View.INVISIBLE);
        monumentName = text(14, 0xFFFFFFFF, UiFont.MEDIUM);
        monumentCard.addView(monumentName);
        monumentFact = text(11, 0xCCFFFFFF, UiFont.REGULAR);
        monumentFact.setSingleLine(false);
        monumentFact.setMaxLines(3);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        fp.topMargin = dp(4);
        monumentCard.addView(monumentFact, fp);
        band.addView(monumentCard, weight(0.28f, true));
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    private LinearLayout card(int orientation) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(orientation);
        if (manga) {
            // A comic panel: paper inside a thick ink line, with a solid ink shadow down and to the right.
            GradientDrawable shadow = new GradientDrawable();
            shadow.setColor(0xFF141414);
            shadow.setCornerRadius(dp(8));
            GradientDrawable paper = new GradientDrawable();
            paper.setColor(0xF7FBF8F1);
            paper.setStroke(dp(3), 0xFF141414);
            paper.setCornerRadius(dp(8));
            android.graphics.drawable.LayerDrawable layers =
                    new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{shadow, paper});
            layers.setLayerInset(0, dp(6), dp(6), 0, 0);
            layers.setLayerInset(1, 0, 0, dp(6), dp(6));
            card.setBackground(layers);
            card.setPadding(dp(16), dp(12), dp(22), dp(18));
            return card;
        }
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(0x80000000);
        panel.setStroke(Math.max(1, dp(1)), 0x24FFFFFF);
        panel.setCornerRadius(dp(18));
        card.setBackground(panel);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        return card;
    }

    private LinearLayout.LayoutParams weight(float weight, boolean gapBefore) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
        if (gapBefore) p.setMarginStart(dp(14));
        return p;
    }

    private TextView text(int sp, int color, int font) {
        TextView t = new TextView(context);
        style(t, sp, color, font);
        return t;
    }

    private void style(TextView t, int sp, int color, int font) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        // MangaHome: the same strengths of white become the same strengths of ink on the paper cards.
        t.setTextColor(manga ? (color & 0xFF000000) | 0x141414 : color);
        if (manga && sp >= 14) {
            t.setIncludeFontPadding(false);
            try {
                t.setTypeface(MangaSkin.lettering(UiFont.get(context, font)));
                return;
            } catch (Exception ignored) {
                // falls through to the usual font
            }
        }
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        try {
            t.setTypeface(UiFont.get(context, font));
        } catch (Exception ignored) {
            // the system font is fine
        }
    }

    /**
     * one line at the top left, level with the pills (the space there is otherwise empty), so it
     * does not crowd the Continue watching label under the cards; empty hides it.
     */
    void setTodayLine(CharSequence text) {
        if (todayLine == null) {
            todayLine = text(13, 0xF2FFFFFF, UiFont.MEDIUM);
            todayLine.setTextColor(0xF2FFFFFF);   // on the wallpaper, not on a card: stays white
            todayLine.setSingleLine(false);
            todayLine.setMaxLines(3);
            todayLine.setLineSpacing(dp(1), 1f);
            todayLine.setShadowLayer(dp(6), 0, dp(1), 0xE6000000);
            todayLine.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(62), Gravity.TOP);
            lp.topMargin = dp(lineTopDp - 12);
            lp.leftMargin = dp(68 + 16);
            lp.rightMargin = dp(rightClearDp);
            todayLine.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            root.addView(todayLine, root.indexOfChild(band) + 1, lp);
        }
        if (manga && todayLine.getBackground() == null) {
            // A paper speech bubble with ink lettering.
            todayLine.setBackground(MangaSkin.bubble(density));
            todayLine.setTextColor(MangaSkin.INK);
            todayLine.setShadowLayer(0, 0, 0, 0);
            todayLine.setPadding(dp(14), dp(4), dp(20), dp(8));
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) todayLine.getLayoutParams();
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;   // the bubble hugs its text, clear of the pills
            todayLine.setMaxWidth(dp(960 - 68 - rightClearDp));
            lp.leftMargin = dp(68);
            todayLine.setLayoutParams(lp);
            ((FrameLayout.LayoutParams) todayLine.getLayoutParams()).gravity = Gravity.TOP | Gravity.START;
            todayLine.setLayoutParams(todayLine.getLayoutParams());
        }
        todayLine.setText(text);
        todayLine.setVisibility(text == null || text.length() == 0 ? View.GONE : View.VISIBLE);
    }

    /** how much of the right side the pills take, so the line ends before them. */
    void setTodayLineRightClear(int dp) {
        rightClearDp = dp;
        if (todayLine != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) todayLine.getLayoutParams();
            if (lp.rightMargin != dp(dp)) {
                lp.rightMargin = dp(dp);
                todayLine.setLayoutParams(lp);
            }
            if (manga) todayLine.setMaxWidth(dp(960 - 68 - dp));
        }
    }

    /** the day's Gita verse, swapped with the weather every minute. */
    void setVerse(Daily.Verse v) {
        if (v == null) return;
        verseText.setText(v.line1 + "\n" + v.line2);
        verseMeaning.setText(v.meaning + "  — " + context.getString(R.string.gita_ref, v.ref));
        weatherCard.removeCallbacks(swapWeather);
        weatherCard.postDelayed(swapWeather, 60_000L);
    }

    private void showVerse(boolean verse) {
        if (weatherCard.getVisibility() != View.VISIBLE) return;
        weatherCard.animate().cancel();
        weatherCard.animate().alpha(0f).setDuration(350).withEndAction(() -> {
            verseShown = verse;
            weatherNowBox.setVisibility(verse ? View.GONE : View.VISIBLE);
            forecast.setVisibility(verse ? View.GONE : View.VISIBLE);
            verseBox.setVisibility(verse ? View.VISIBLE : View.GONE);
            weatherCard.animate().alpha(1f).setDuration(450).start();
        }).start();
    }

    /** the things the monument card turns over to, as {title, body}. */
    void setDaily(java.util.List<String[]> items) {
        daily = items == null ? java.util.Collections.emptyList() : items;
    }

    private void turnTo(String name, String fact) {
        monumentCard.animate().cancel();
        monumentCard.animate().alpha(0f).setDuration(350).withEndAction(() -> {
            monumentName.setText(name);
            monumentFact.setText(fact == null ? "" : fact);
            monumentFact.setVisibility(fact == null || fact.isEmpty() ? View.GONE : View.VISIBLE);
            monumentCard.setVisibility(View.VISIBLE);
            monumentCard.animate().alpha(1f).setDuration(450).start();
        }).start();
    }

    /** "Good afternoon" on two lines (GOOD, then AFTERNOON) so the card never cuts it short. */
    void setGreeting(CharSequence text) {
        greeting.setText(text == null ? "" : text.toString().replaceFirst(" ", "\n"));
    }

    /** The shown wallpaper's name and fact; an empty name hides the card. */
    void setMonument(String name, String fact) {
        monumentCard.removeCallbacks(flip);
        shownName = name;
        if (name == null || name.isEmpty()) {
            if (!daily.isEmpty()) {
                shownName = "";
                flip.run();   // no caption for this picture: straight to the day's card
                return;
            }
            monumentCard.setVisibility(View.INVISIBLE);
            return;
        }
        if (!daily.isEmpty()) monumentCard.postDelayed(flip, 30_000L);
        monumentCard.animate().cancel();
        monumentCard.setAlpha(1f);
        monumentName.setText(name);
        monumentFact.setText(fact == null ? "" : fact);
        monumentFact.setVisibility(fact == null || fact.isEmpty() ? View.GONE : View.VISIBLE);
        monumentCard.setVisibility(View.VISIBLE);
    }

    /** Weather's answer: {"current": {...}, "daily": {...}, "aqi": n}. */
    void setWeather(JSONObject w) {
        JSONObject now = w.optJSONObject("current");
        if (now == null) return;
        weatherNow.setText(Math.round(now.optDouble("temperature_2m")) + "°");
        String sky = Weather.condition(now.optInt("weather_code", -1));
        weatherCondition.setText(place.isEmpty() ? sky : sky + " · " + place);
        StringBuilder details = new StringBuilder();
        if (now.has("apparent_temperature")) details.append(context.getString(R.string.feels, Math.round(now.optDouble("apparent_temperature"))));
        if (now.has("relative_humidity_2m")) details.append(" · ").append(now.optInt("relative_humidity_2m")).append("%");
        if (now.has("wind_speed_10m")) details.append(" · ").append(Math.round(now.optDouble("wind_speed_10m"))).append(" km/h");
        weatherDetails.setText(details);
        JSONObject daily = w.optJSONObject("daily");
        weatherAir.setText(w.has("aqi") ? context.getString(R.string.aqi_line, w.optLong("aqi"), aqiWord(w.optLong("aqi"))) : "");
        weatherAir.setVisibility(w.has("aqi") ? View.VISIBLE : View.GONE);
        String rise = daily == null ? "" : timeOf(daily.optJSONArray("sunrise"));
        String set = daily == null ? "" : timeOf(daily.optJSONArray("sunset"));
        weatherSun.setText(rise.isEmpty() ? "" : context.getString(R.string.sun_times, rise, set));
        weatherSun.setVisibility(rise.isEmpty() ? View.GONE : View.VISIBLE);
        forecast.removeAllViews();
        if (daily != null) {
            JSONArray days = daily.optJSONArray("time");
            JSONArray max = daily.optJSONArray("temperature_2m_max"), min = daily.optJSONArray("temperature_2m_min");
            for (int i = 1; days != null && max != null && min != null && i < Math.min(5, days.length()); i++) {
                LinearLayout col = new LinearLayout(context);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.CENTER_HORIZONTAL);
                String day;
                try {
                    day = LocalDate.parse(days.optString(i)).getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault());
                } catch (Exception e) {
                    day = "";
                }
                col.setMinimumWidth(dp(30));
                TextView d = text(11, 0xB3FFFFFF, UiFont.MEDIUM);
                d.setEllipsize(null);
                d.setText(day.toUpperCase(Locale.getDefault()));
                col.addView(d);
                TextView hi = text(15, 0xFFFFFFFF, UiFont.REGULAR);
                hi.setEllipsize(null);
                hi.setText(Math.round(max.optDouble(i)) + "°");
                LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                hp.topMargin = dp(5);
                col.addView(hi, hp);
                TextView lo = text(11, 0x99FFFFFF, UiFont.REGULAR);
                lo.setEllipsize(null);
                lo.setText(Math.round(min.optDouble(i)) + "°");
                col.addView(lo);
                LinearLayout.LayoutParams colp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                colp.setMarginStart(dp(12));
                forecast.addView(col, colp);
            }
        }
        weatherCard.setVisibility(View.VISIBLE);
    }

    private static String timeOf(JSONArray list) {
        String t = list == null ? "" : list.optString(0, "");
        int at = t.indexOf('T');
        return at > 0 ? t.substring(at + 1) : "";
    }

    /** The US AQI bands in a word. */
    static String aqiWord(long aqi) {
        return L10n.get(aqi <= 50 ? R.string.aqi_good : aqi <= 100 ? R.string.aqi_moderate : aqi <= 150 ? R.string.aqi_poor
                : aqi <= 200 ? R.string.aqi_unhealthy : aqi <= 300 ? R.string.aqi_very_poor : R.string.aqi_hazardous);
    }
}
