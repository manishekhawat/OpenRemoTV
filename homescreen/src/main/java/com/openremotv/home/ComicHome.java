package com.openremotv.home;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * MangaHome: Home as a page of a comic, in place of the usual layout. Ink panels on paper with screentone:
 * a big lettered clock with Remo saying hello, the weather with its sound effect, Today as narration boxes
 * (Panchang, the next festival, the Gita verse or the Hindi word), the apps as slanted panels that "POW!" when the
 * remote lands on them, round speech-bubble buttons, and the news as a caption strip. Every button and app panel
 * takes focus; the information panels don't.
 */
final class ComicHome extends FrameLayout {

    /** What the page asks of Home. */
    interface Host {
        void open(Intent intent);
        void button(String which);
    }

    static final int INK = 0xFF141414;
    static final int PAPER = 0xFFFBF8F1;
    static final int RED = 0xFFE0262B;
    static final int CAPTION = 0xFFFFF4C2;

    private final float d;
    private final Host host;
    private final Theme extras;
    private final List<String> dock;
    private TextView greeting, weatherBig, weatherWord, weatherSfx, weatherMore;
    private TextView tithi, rahu, festival, wisdom, caption;
    private LinearLayout apps, buttons;
    private int wisdomTurn;
    private final Random random = new Random();

    ComicHome(Context context, Theme extras, List<String> dockApps, Host host) {
        super(context);
        L10n.init(context);
        this.host = host;
        this.extras = extras;
        this.dock = dockApps;
        d = getResources().getDisplayMetrics().density;
        setBackground(new PageDrawable(d));
        setClipChildren(false);
        build();
        refresh();
    }

    private int dp(float v) {
        return Math.round(v * d);
    }

    private void build() {
        // Top row: clock and Remo, the weather, Today.
        FrameLayout clock = panel(false, -1.5f);
        place(clock, 24, 18, 388, 196);
        Remo remo = new Remo(getContext());
        clock.addView(remo, new LayoutParams(dp(112), dp(112), Gravity.START | Gravity.BOTTOM));
        greeting = lettering(15, INK);
        greeting.setBackground(new BubbleDrawable(d));
        greeting.setPadding(dp(12), dp(6), dp(12), dp(14));
        LayoutParams gp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.TOP);
        gp.leftMargin = dp(14);
        gp.topMargin = dp(10);
        clock.addView(greeting, gp);
        LinearLayout time = new LinearLayout(getContext());
        time.setOrientation(LinearLayout.VERTICAL);
        time.setGravity(Gravity.END);
        TextClock t = new TextClock(getContext());
        t.setFormat12Hour("h:mm");
        t.setFormat24Hour("HH:mm");
        style(t, 64, INK, true);
        time.addView(t);
        TextClock date = new TextClock(getContext());
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        style(date, 15, INK, true);
        time.addView(date);
        LayoutParams tp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.BOTTOM);
        tp.rightMargin = dp(18);
        tp.bottomMargin = dp(16);
        clock.addView(time, tp);

        FrameLayout weather = panel(false, 1.5f);
        place(weather, 424, 18, 220, 196);
        LinearLayout w = column(weather, 16, 14);
        weatherBig = lettering(46, INK);
        w.addView(weatherBig);
        weatherWord = lettering(17, INK);
        w.addView(weatherWord);
        weatherMore = plain(13, INK);
        weatherMore.setSingleLine(false);
        weatherMore.setMaxLines(4);
        w.addView(weatherMore);
        weatherSfx = lettering(26, RED);
        weatherSfx.setRotation(-12f);
        LayoutParams sp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.TOP);
        sp.rightMargin = dp(10);
        sp.topMargin = dp(20);
        weather.addView(weatherSfx, sp);

        FrameLayout today = panel(false, -1f);
        place(today, 660, 18, 276, 196);
        LinearLayout c = column(today, 12, 12);
        TextView head = lettering(13, INK);
        head.setText(R.string.comic_today);
        c.addView(head);
        tithi = caption(c);
        rahu = caption(c);
        festival = caption(c);
        wisdom = plain(13, INK);
        wisdom.setSingleLine(false);
        wisdom.setMaxLines(3);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        wp.topMargin = dp(6);
        c.addView(wisdom, wp);

        // Middle row: the apps.
        apps = new LinearLayout(getContext());
        apps.setOrientation(LinearLayout.HORIZONTAL);
        apps.setClipChildren(false);
        LayoutParams ap = new LayoutParams(dp(912), dp(176));
        ap.leftMargin = dp(24);
        ap.topMargin = dp(234);
        addView(apps, ap);
        fillApps();

        // Bottom: speech-bubble buttons, then the news as a caption strip.
        LinearLayout buttons = new LinearLayout(getContext());
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        LayoutParams bp = new LayoutParams(dp(912), dp(60));
        bp.leftMargin = dp(24);
        bp.topMargin = dp(424);
        addView(buttons, bp);
        String[][] list = {
                {"wifi", getContext().getString(R.string.btn_wifi)}, {"voice", getContext().getString(R.string.btn_voice)},
                {"radio", getContext().getString(R.string.radio)}, {"tv", getContext().getString(R.string.tv)}, {"games", getContext().getString(R.string.games)},
                {"today", getContext().getString(R.string.today)}, {"darshan", getContext().getString(R.string.darshan)},
                {"recipe", getContext().getString(R.string.recipe)}, {"phone", getContext().getString(R.string.btn_phone)},
                {"memory", getContext().getString(R.string.btn_memory)}, {"sleep", getContext().getString(R.string.sleep_title)},
                {"settings", getContext().getString(R.string.btn_settings)}};
        int[] icons = {R.drawable.ic_wifi, R.drawable.ic_mic, R.drawable.ic_radio, R.drawable.ic_tv, R.drawable.ic_dice, R.drawable.ic_today,
                R.drawable.ic_temple, R.drawable.ic_recipe, R.drawable.ic_phone, R.drawable.ic_memory, R.drawable.ic_moon,
                R.drawable.ic_settings};
        for (int i = 0; i < list.length; i++) {
            String which = list[i][0];
            ImageView b = new ImageView(getContext());
            b.setImageResource(icons[i]);
            b.setColorFilter(INK);
            b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            b.setPadding(dp(13), dp(13), dp(13), dp(13));
            b.setBackground(new RoundBubble(d));
            b.setFocusable(true);
            b.setClickable(true);
            b.setContentDescription(list[i][1]);
            b.setOnClickListener(v -> host.button(which));
            b.setOnFocusChangeListener((v, f) -> {
                ((ImageView) v).setColorFilter(f ? PAPER : INK);
                v.animate().scaleX(f ? 1.15f : 1f).scaleY(f ? 1.15f : 1f).setDuration(extras.reducedMotion ? 0 : 120).start();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(52), dp(52));
            lp.setMarginStart(dp(i == 0 ? 0 : 14));
            buttons.addView(b, lp);
        }
        this.buttons = buttons;
        linkFocus();
        caption = plain(14, INK);
        caption.setTypeface(Typeface.create(caption.getTypeface(), Typeface.BOLD_ITALIC));
        caption.setBackground(new CaptionDrawable(d));
        caption.setGravity(Gravity.CENTER_VERTICAL);
        caption.setPadding(dp(16), 0, dp(16), 0);
        LayoutParams cp = new LayoutParams(dp(912), dp(36));
        cp.leftMargin = dp(24);
        cp.topMargin = dp(494);
        addView(caption, cp);
    }

    /** The dock's apps (and the next most used ones up to four), then "All apps". */
    void fillApps() {
        apps.removeAllViews();
        PackageManager pm = getContext().getPackageManager();
        List<String> pkgs = new ArrayList<>();
        for (String p : dock) if (launch(pm, p) != null && pkgs.size() < 4) pkgs.add(p);
        if (pkgs.size() < 4) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)) {
                String p = ri.activityInfo.packageName;
                if (pkgs.size() >= 4) break;
                if (!pkgs.contains(p) && !p.equals(getContext().getPackageName())) pkgs.add(p);
            }
        }
        float[] tilt = {-2f, 1.5f, -1f, 2f, -1.5f};
        for (int i = 0; i < pkgs.size(); i++) {
            String p = pkgs.get(i);
            Intent intent = launch(pm, p);
            FrameLayout panel = appPanel(tilt[i % tilt.length]);
            ImageView art = new ImageView(getContext());
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Drawable banner = null;
            CharSequence label = p;
            try {
                ApplicationInfo info = pm.getApplicationInfo(p, 0);
                banner = info.loadBanner(pm);
                if (banner == null) banner = info.loadIcon(pm);
                label = info.loadLabel(pm);
            } catch (PackageManager.NameNotFoundException ignored) {
                // gone meanwhile
            }
            art.setImageDrawable(banner);
            LayoutParams artp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            artp.setMargins(dp(14), dp(14), dp(14), dp(40));
            panel.addView(art, artp);
            TextView name = plain(13, INK);
            name.setTypeface(Typeface.create(name.getTypeface(), Typeface.BOLD_ITALIC));
            name.setText(label);
            name.setBackground(new CaptionDrawable(d));
            name.setPadding(dp(8), dp(2), dp(8), dp(2));
            LayoutParams np = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.START);
            np.leftMargin = dp(12);
            np.bottomMargin = dp(12);
            panel.addView(name, np);
            panel.setOnClickListener(v -> host.open(intent));
            apps.addView(panel, appParams(i, 1f));
        }
        FrameLayout all = appPanel(-1f);
        TextView label = lettering(20, PAPER);
        label.setSingleLine(false);
        label.setText(R.string.comic_all_apps);
        label.setGravity(Gravity.CENTER);
        all.addView(label, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        ((PanelDrawable) all.getBackground()).fill = INK;
        all.setOnClickListener(v -> host.button("apps"));
        apps.addView(all, appParams(pkgs.size(), 0.7f));
        if (apps.getChildCount() > 0) apps.getChildAt(0).post(() -> apps.getChildAt(0).requestFocus());
        linkFocus();
    }

    /**
     * Wires the remote's moves by hand: along each row, Down from the apps to the buttons and Up back. The tilted,
     * zoomed panels otherwise sent Right down to the buttons now and then.
     */
    private void linkFocus() {
        if (buttons == null) return;
        int na = apps.getChildCount(), nb = buttons.getChildCount();
        for (int i = 0; i < na; i++) if (apps.getChildAt(i).getId() == NO_ID) apps.getChildAt(i).setId(generateViewId());
        for (int i = 0; i < nb; i++) if (buttons.getChildAt(i).getId() == NO_ID) buttons.getChildAt(i).setId(generateViewId());
        for (int i = 0; i < na; i++) {
            View v = apps.getChildAt(i);
            v.setNextFocusLeftId(apps.getChildAt(Math.max(0, i - 1)).getId());
            v.setNextFocusRightId(apps.getChildAt(Math.min(na - 1, i + 1)).getId());
            v.setNextFocusUpId(v.getId());
            v.setNextFocusDownId(buttons.getChildAt(Math.min(nb - 1, Math.round(i * (nb - 1) / (float) Math.max(1, na - 1)))).getId());
        }
        for (int i = 0; i < nb; i++) {
            View v = buttons.getChildAt(i);
            v.setNextFocusLeftId(buttons.getChildAt(Math.max(0, i - 1)).getId());
            v.setNextFocusRightId(buttons.getChildAt(Math.min(nb - 1, i + 1)).getId());
            v.setNextFocusUpId(apps.getChildAt(Math.min(na - 1, Math.round(i * (na - 1) / (float) Math.max(1, nb - 1)))).getId());
            v.setNextFocusDownId(v.getId());
        }
    }

    private LinearLayout.LayoutParams appParams(int i, float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
        lp.setMarginStart(dp(i == 0 ? 0 : 14));
        return lp;
    }

    private static Intent launch(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    /** A focusable app panel: zooms and "POW!"s when the remote lands on it. */
    private FrameLayout appPanel(float tilt) {
        FrameLayout panel = new FrameLayout(getContext());
        PanelDrawable bg = new PanelDrawable(d, tilt, true);
        panel.setBackground(bg);
        panel.setFocusable(true);
        panel.setClickable(true);
        panel.setClipChildren(false);
        // Raised over its neighbours when focused, without a shadow to render.
        panel.setOutlineProvider(null);
        TextView pow = lettering(22, RED);
        pow.setText(new String[]{"POW!", "DON!", "BAM!", "ZOOM!"}[random.nextInt(4)]);
        pow.setRotation(-14f);
        pow.setVisibility(INVISIBLE);
        // A sound effect for the eye only: TalkBack reads the app's name, not "POW!".
        pow.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        pow.setShadowLayer(0.01f, 0, 0, 0);
        LayoutParams pp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.TOP);
        pp.topMargin = -dp(18);
        pp.rightMargin = -dp(6);
        panel.addView(pow, pp);
        panel.setOnFocusChangeListener((v, f) -> {
            bg.focused = f;
            bg.invalidateSelf();
            pow.setVisibility(f ? VISIBLE : INVISIBLE);
            v.animate().scaleX(f ? 1.07f : 1f).scaleY(f ? 1.07f : 1f).setDuration(extras.reducedMotion ? 0 : 140).start();
            v.setZ(f ? 10 : 0);
        });
        return panel;
    }

    private FrameLayout panel(boolean focusable, float tilt) {
        FrameLayout p = new FrameLayout(getContext());
        p.setBackground(new PanelDrawable(d, tilt, false));
        p.setFocusable(focusable);
        p.setClipChildren(false);
        return p;
    }

    private void place(View v, int x, int y, int w, int h) {
        LayoutParams lp = new LayoutParams(dp(w), dp(h));
        lp.leftMargin = dp(x);
        lp.topMargin = dp(y);
        addView(v, lp);
    }

    private LinearLayout column(FrameLayout parent, int padH, int padV) {
        LinearLayout c = new LinearLayout(getContext());
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(padH), dp(padV), dp(padH), dp(padV));
        parent.addView(c, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return c;
    }

    private TextView caption(LinearLayout parent) {
        TextView t = plain(13, INK);
        t.setBackground(new CaptionDrawable(d));
        t.setPadding(dp(8), dp(3), dp(8), dp(3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        parent.addView(t, lp);
        return t;
    }

    private TextView lettering(int sp, int color) {
        TextView t = new TextView(getContext());
        style(t, sp, color, true);
        return t;
    }

    private TextView plain(int sp, int color) {
        TextView t = new TextView(getContext());
        style(t, sp, color, false);
        return t;
    }

    private void style(TextView t, int sp, int color, boolean shout) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(!shout || sp < 30);
        t.setEllipsize(TextUtils.TruncateAt.END);
        Typeface base;
        try {
            base = UiFont.get(getContext(), UiFont.MEDIUM);
        } catch (Exception e) {
            base = Typeface.DEFAULT;
        }
        // Comic lettering: the heaviest italic sans there is (the UI font has no black weight).
        t.setTypeface(shout ? Typeface.create("sans-serif-black", Typeface.ITALIC) : base);
        if (shout) t.setLetterSpacing(0.02f);
    }

    /** Re-reads the time of day, the weather cache, the Panchang and the headlines. Called on resume and every minute. */
    void refresh() {
        int hour = LocalTime.now().getHour();
        greeting.setText(getContext().getString(hour < 5 ? R.string.remo_late : hour < 12 ? R.string.remo_morning
                : hour < 17 ? R.string.remo_afternoon : hour < 21 ? R.string.remo_evening : R.string.remo_night));
        Context ctx = getContext();
        try {
            JSONObject w = new JSONObject(ctx.getSharedPreferences("weather", Context.MODE_PRIVATE).getString("raw", "{}"));
            JSONObject now = w.optJSONObject("current");
            if (now != null) {
                int code = now.optInt("weather_code", -1);
                weatherBig.setText(Math.round(now.optDouble("temperature_2m")) + "°");
                weatherWord.setText(Weather.condition(code).toUpperCase(java.util.Locale.getDefault()));
                weatherSfx.setText(sfx(code, now.optDouble("temperature_2m")));
                StringBuilder more = new StringBuilder();
                if (!extras.weatherCity.isEmpty()) more.append(extras.weatherCity).append("\n");
                more.append(ctx.getString(R.string.feels, Math.round(now.optDouble("apparent_temperature"))));
                if (w.has("aqi")) more.append(" · ").append(ctx.getString(R.string.aqi_line, w.optLong("aqi"), Widgets.aqiWord(w.optLong("aqi"))));
                JSONObject daily = w.optJSONObject("daily");
                JSONArray max = daily == null ? null : daily.optJSONArray("temperature_2m_max");
                JSONArray min = daily == null ? null : daily.optJSONArray("temperature_2m_min");
                if (max != null && min != null && max.length() > 1) {
                    more.append("\n").append(ctx.getString(R.string.tomorrow_temps, Math.round(max.optDouble(1)), Math.round(min.optDouble(1))));
                }
                weatherMore.setText(more);
            } else {
                weatherBig.setText("--°");
                weatherWord.setText(ctx.getString(R.string.weather).toUpperCase(java.util.Locale.getDefault()));
                weatherMore.setText(R.string.weather_waiting);
            }
        } catch (Exception ignored) {
            // the cache fills on the next fetch
        }
        LocalDate today = LocalDate.now();
        ZoneId zone = ZoneId.systemDefault();
        if (extras.weatherEnabled()) {
            Panchang.Day p = Panchang.of(today, extras.weatherLatitude, extras.weatherLongitude, zone);
            java.text.DateFormat time = android.text.format.DateFormat.getTimeFormat(ctx);
            tithi.setText(p.title());
            long now = System.currentTimeMillis();
            rahu.setText(p.rahuStart != 0 && now < p.rahuEnd
                    ? ctx.getString(R.string.rahu_kaal, time.format(new java.util.Date(p.rahuStart)), time.format(new java.util.Date(p.rahuEnd)))
                    : ctx.getString(R.string.sunset_at, time.format(new java.util.Date(p.sunset))));
            List<Daily.Event> next = Daily.upcoming(extras, today, zone, 1);
            festival.setText(next.isEmpty() ? ctx.getString(R.string.no_festival) : next.get(0).when(today));
        }
        wisdomTurn++;
        if (wisdomTurn % 2 == 0) {
            Daily.Word word = Daily.word(ctx, today);
            if (word != null) wisdom.setText(word.word + " (" + word.sound + "): " + word.meaning);
        } else {
            Daily.Verse verse = Daily.verse(ctx, today);
            if (verse != null) wisdom.setText(ctx.getString(R.string.gita_line, verse.ref, verse.meaning));
        }
        String items = ctx.getSharedPreferences("ticker", Context.MODE_PRIVATE).getString("items", "");
        if (!items.isEmpty()) {
            String[] lines = items.split("\n");
            caption.setText(ctx.getString(R.string.meanwhile) + "  " + lines[random.nextInt(lines.length)].replaceFirst("^[^\\p{L}\\p{N}']+", ""));
        } else {
            caption.setText(ctx.getString(R.string.meanwhile) + "  " + ctx.getString(R.string.fetching_news));
        }
    }

    /** The weather's sound effect, the way a manga would letter it. */
    private static String sfx(int code, double temp) {
        if (code >= 95) return "GOROGORO!";
        if (code >= 51 && code <= 82) return "ZAAA…";
        if (code == 45 || code == 48) return "MOWAA…";
        if (code >= 71 && code <= 77) return "SHIN…";
        if (code >= 2) return "MOKU MOKU";
        return temp >= 30 ? "JIRI JIRI!" : "KIRA!";
    }

    // ---- drawing ----------------------------------------------------------------------------------------

    /** The page: paper with screentone dots, heavier towards the bottom. */
    static final class PageDrawable extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;

        PageDrawable(float d) {
            this.d = d;
        }

        /** The dots drawn once into a picture: thousands of circles replayed on every frame slowed the focus moves. */
        private android.graphics.Bitmap page;

        @Override public void draw(Canvas c) {
            int w = getBounds().width(), h = getBounds().height();
            if (w <= 0 || h <= 0) return;
            if (page == null || page.getWidth() != w || page.getHeight() != h) {
                page = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.RGB_565);
                Canvas pc = new Canvas(page);
                pc.drawColor(PAPER);
                p.setColor(0xFFDDD7CA);
                float step = 12 * d;
                int row = 0;
                for (float y = 0; y < h; y += step * 0.87f, row++) {
                    float r = (0.6f + 1.8f * y / h) * d;
                    for (float x = row % 2 == 0 ? 0 : step / 2; x < w; x += step) pc.drawCircle(x, y, r, p);
                }
            }
            c.drawBitmap(page, getBounds().left, getBounds().top, null);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
    }

    /** A panel: a slightly skewed quadrilateral of paper (or ink) in a thick ink line with a hard shadow. */
    static final class PanelDrawable extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d, tilt;
        private final boolean focusRing;
        int fill = PAPER;
        boolean focused;

        PanelDrawable(float d, float tilt, boolean focusRing) {
            this.d = d;
            this.tilt = tilt;
            this.focusRing = focusRing;
        }

        /**
         * The panel's slant as a skewed rectangle at (dx, dy): drawn as a rect the GPU takes as plain triangles. A
         * Path became a full-panel texture, three per panel, which slowed every focus move.
         */
        private RectF shape(Canvas c, float dx, float dy) {
            float w = getBounds().width() - 6 * d, h = getBounds().height() - 6 * d;
            float k = tilt * d * 3;
            if (k >= 0) {
                c.translate(dx + k, dy);
                c.skew(-k / h, 0);
                return new RectF(0, 0, w - k, h);
            }
            c.translate(dx, dy);
            c.skew(0, -k / w);
            return new RectF(0, 0, w, h + k);
        }

        @Override public void draw(Canvas c) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(focused ? RED : INK);
            int save = c.save();
            c.drawRect(shape(c, 6 * d, 6 * d), p);
            c.restoreToCount(save);
            save = c.save();
            RectF main = shape(c, 0, 0);
            p.setColor(fill);
            c.drawRect(main, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.MITER);
            p.setColor(focused && focusRing ? RED : INK);
            p.setStrokeWidth((focused && focusRing ? 6 : 4) * d);
            c.drawRect(main, p);
            c.restoreToCount(save);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** A narration box: pale yellow in a thin ink line, the way a comic captions a scene. */
    static final class CaptionDrawable extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;

        CaptionDrawable(float d) {
            this.d = d;
        }

        @Override public void draw(Canvas c) {
            RectF r = new RectF(getBounds());
            r.inset(1.5f * d, 1.5f * d);
            p.setStyle(Paint.Style.FILL);
            p.setColor(CAPTION);
            c.drawRect(r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.5f * d);
            p.setColor(INK);
            c.drawRect(r, p);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** A speech bubble with its tail pointing down-left, at Remo. */
    static final class BubbleDrawable extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;

        BubbleDrawable(float d) {
            this.d = d;
        }

        @Override public void draw(Canvas c) {
            float w = getBounds().width(), h = getBounds().height() - 8 * d;
            RectF r = new RectF(2 * d, 2 * d, w - 2 * d, h);
            Path tail = new Path();
            tail.moveTo(w * 0.22f, h - 2 * d);
            tail.lineTo(w * 0.12f, h + 8 * d);
            tail.lineTo(w * 0.36f, h - 2 * d);
            tail.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            c.drawRoundRect(r, h / 2, h / 2, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3 * d);
            p.setColor(INK);
            c.drawRoundRect(r, h / 2, h / 2, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            c.drawPath(tail, p);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(INK);
            c.drawLine(w * 0.22f, h - 1.5f * d, w * 0.12f, h + 8 * d, p);
            c.drawLine(w * 0.36f, h - 1.5f * d, w * 0.12f, h + 8 * d, p);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** A round speech-bubble button: paper in ink, red when focused, with a hard shadow. */
    static final class RoundBubble extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        private boolean focused;

        RoundBubble(float d) {
            this.d = d;
        }

        @Override public boolean isStateful() {
            return true;
        }

        @Override protected boolean onStateChange(int[] state) {
            boolean f = false;
            for (int s : state) if (s == android.R.attr.state_focused) f = true;
            if (f == focused) return false;
            focused = f;
            invalidateSelf();
            return true;
        }

        @Override public void draw(Canvas c) {
            float r = Math.min(getBounds().width(), getBounds().height()) / 2f - 4 * d;
            // Centred on the view, so the icon sits in the middle of the circle; the shadow hangs off down-right.
            float cx = getBounds().width() / 2f, cy = getBounds().height() / 2f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(INK);
            c.drawCircle(cx + 3 * d, cy + 3 * d, r, p);
            p.setColor(focused ? RED : PAPER);
            c.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3 * d);
            p.setColor(INK);
            c.drawCircle(cx, cy, r, p);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** Remo, OpenRemoTV's mascot: round face, big shining eyes, spiky ink hair and a little antenna. */
    static final class Remo extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Remo(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight());
            float l = (getWidth() - s) / 2f, t = (getHeight() - s) / 2f;
            p.reset();
            p.setAntiAlias(true);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(INK);
            p.setStrokeWidth(s * 0.03f);
            c.drawLine(l + 0.5f * s, t + 0.2f * s, l + 0.62f * s, t + 0.04f * s, p);
            p.setColor(RED);
            c.drawCircle(l + 0.63f * s, t + 0.04f * s, s * 0.04f, p);
            p.setColor(INK);
            float[] hx = {0.14f, 0.08f, 0.24f, 0.26f, 0.40f, 0.50f, 0.60f, 0.76f, 0.77f, 0.93f, 0.86f};
            float[] hy = {0.55f, 0.30f, 0.36f, 0.14f, 0.28f, 0.10f, 0.28f, 0.15f, 0.36f, 0.31f, 0.55f};
            Path hair = new Path();
            for (int i = 0; i < hx.length; i++) {
                if (i == 0) hair.moveTo(l + hx[i] * s, t + hy[i] * s);
                else hair.lineTo(l + hx[i] * s, t + hy[i] * s);
            }
            hair.close();
            c.drawPath(hair, p);
            float fx = l + 0.5f * s, fy = t + 0.58f * s, fr = s * 0.34f;
            p.setColor(PAPER);
            c.drawCircle(fx, fy, fr, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * 0.035f);
            p.setColor(INK);
            c.drawCircle(fx, fy, fr, p);
            p.setStyle(Paint.Style.FILL);
            Path fringe = new Path();
            float[][] f = {{-0.95f, -0.25f}, {-0.55f, -0.55f}, {-0.35f, -0.18f}, {-0.05f, -0.62f}, {0.15f, -0.2f},
                    {0.45f, -0.6f}, {0.6f, -0.22f}, {0.95f, -0.3f}, {1f, -0.9f}, {-1f, -0.9f}};
            for (int i = 0; i < f.length; i++) {
                if (i == 0) fringe.moveTo(fx + f[i][0] * fr, fy + f[i][1] * fr);
                else fringe.lineTo(fx + f[i][0] * fr, fy + f[i][1] * fr);
            }
            fringe.close();
            c.drawPath(fringe, p);
            for (int side = -1; side <= 1; side += 2) {
                float ex = fx + side * fr * 0.42f, ey = fy + fr * 0.12f;
                p.setColor(INK);
                c.drawOval(new RectF(ex - fr * 0.17f, ey - fr * 0.26f, ex + fr * 0.17f, ey + fr * 0.26f), p);
                p.setColor(PAPER);
                c.drawCircle(ex + fr * 0.05f, ey - fr * 0.1f, fr * 0.08f, p);
                c.drawCircle(ex - fr * 0.06f, ey + fr * 0.12f, fr * 0.04f, p);
                p.setColor(0x59E0262B);
                c.drawOval(new RectF(ex - fr * 0.2f, ey + fr * 0.32f, ex + fr * 0.1f, ey + fr * 0.44f), p);
            }
            p.setColor(INK);
            p.setStyle(Paint.Style.STROKE);
            Path m = new Path();
            m.moveTo(fx - fr * 0.16f, fy + fr * 0.5f);
            m.quadTo(fx, fy + fr * 0.72f, fx + fr * 0.16f, fy + fr * 0.5f);
            c.drawPath(m, p);
        }
    }
}
