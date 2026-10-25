package com.openremotv.home;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.pm.ApplicationInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The Today page: one screen of things to read with the remote, as rows of glass cards: the Panchang, what is coming
 * up, the night sky, the Hindi word, the Gita verse, storage and memory, and the sleep timer. Every card takes
 * focus, so the remote walks through all of them and the page scrolls to follow; OK acts on the cards that do
 * something (free memory, sleep timer).
 */
public class TodayActivity extends Activity {

    private float density;
    /** MangaHome: the page drawn as a comic, like ComicHome. */
    private boolean comic;
    private int tiltTurn;
    private LinearLayout page;
    private LinearLayout row;
    private float rowWeight;
    private Theme extras;
    private TextView storageTitle, storageBody, sleepTitle;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        L10n.init(this);
        density = getResources().getDisplayMetrics().density;
        try {
            extras = Theme.load(this);
        } catch (RuntimeException e) {
            extras = null;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        comic = extras != null && (extras.mangaLayout || extras.mangaStyle);
        if (comic) scroll.setBackground(new ComicHome.PageDrawable(density));
        else scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1A1430, 0xFF0B1020, 0xFF02040A}));
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(56), dp(36), dp(56), dp(40));
        page.setClipChildren(false);
        page.setClipToPadding(false);
        scroll.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStorage(null);
        refreshSleep();
    }

    private void build() {
        LocalDate today = LocalDate.now();
        ZoneId zone = ZoneId.systemDefault();
        TextView head = text(30, 0xFFFFFFFF, UiFont.LIGHT);
        head.setText(getString(R.string.today));
        if (comic) {
            // A title strip: Remo with the day in his speech bubble.
            LinearLayout strip = new LinearLayout(this);
            strip.setOrientation(LinearLayout.HORIZONTAL);
            strip.setGravity(Gravity.CENTER_VERTICAL);
            strip.addView(new ComicHome.Remo(this), new LinearLayout.LayoutParams(dp(78), dp(78)));
            head.setText(getString(R.string.today_head));
            head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            hp.setMarginStart(dp(14));
            strip.addView(head, hp);
            page.addView(strip);
        } else {
            page.addView(head);
        }
        TextView date = text(15, 0xB3FFFFFF, UiFont.REGULAR);
        date.setText(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())));
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dp.topMargin = dp(4);
        dp.bottomMargin = dp(18);
        page.addView(date, dp);

        boolean place = extras != null && extras.weatherEnabled();
        double lat = place ? extras.weatherLatitude : 28.6139, lon = place ? extras.weatherLongitude : 77.209;
        DateFormat time = android.text.format.DateFormat.getTimeFormat(this);

        newRow();
        Panchang.Day p = Panchang.of(today, lat, lon, zone);
        card(1, up(R.string.card_panchang), p.title(),
                (p.tithiEnds > System.currentTimeMillis()
                        ? getString(R.string.tithi_until, time.format(new Date(p.tithiEnds)))
                        : getString(R.string.tithi_from, nextTithi(p), time.format(new Date(p.tithiEnds)))) + "\n"
                        + getString(R.string.nakshatra_is, p.nakshatraName()) + "\n"
                        + getString(R.string.sun_times, time.format(new Date(p.sunrise)), time.format(new Date(p.sunset))) + "\n"
                        + (p.rahuStart == 0 ? "" : getString(R.string.rahu_kaal, time.format(new Date(p.rahuStart)), time.format(new Date(p.rahuEnd)))),
                null);
        StringBuilder coming = new StringBuilder();
        if (extras != null) {
            List<Daily.Event> events = Daily.upcoming(extras, today, zone, 5);
            for (Daily.Event e : events) {
                if (coming.length() > 0) coming.append('\n');
                coming.append(e.date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())))
                        .append("   ").append(e.name);
            }
        }
        card(1, up(R.string.card_coming), "", coming.length() == 0 ? getString(R.string.nothing_coming) : coming.toString(), null);
        NightSky.Report sky = NightSky.tonight(ZonedDateTime.now(zone), lat, lon);
        StringBuilder planets = new StringBuilder();
        for (NightSky.Planet planet : sky.planets) {
            if (planets.length() > 0) planets.append('\n');
            planets.append(planet.describe());
        }
        card(1, up(R.string.card_sky), sky.phaseName() + " · " + Math.round(sky.illumination * 100) + "%",
                (planets.length() == 0 ? getString(R.string.no_planets_up) : planets) + "\n"
                        + getString(sky.at.toLocalTime().getHour() == 21 && sky.at.getMinute() == 0 ? R.string.seen_at_nine : R.string.up_now),
                null);

        newRow();
        Daily.Word word = Daily.word(this, today);
        if (word != null) {
            card(1, up(R.string.card_word), word.word + "  ·  " + word.sound,
                    word.meaning + (word.example.isEmpty() ? "" : "\n\n" + word.example), null);
        }
        Daily.Verse verse = Daily.verse(this, today);
        if (verse != null) {
            card(2, up(R.string.card_gita) + " " + verse.ref, verse.line1 + "\n" + verse.line2, verse.meaning, null);
        }

        newRow();
        TextView[] storage = new TextView[2];
        View storageCard = card(1, up(R.string.card_storage), "", "", storage);
        storageTitle = storage[0];
        storageBody = storage[1];
        storageCard.setOnClickListener(v -> freeMemory());
        TextView[] sleep = new TextView[2];
        View sleepCard = card(1, up(R.string.sleep_title), "", getString(SleepTimer.adminActive(this)
                ? R.string.sleep_card_standby : R.string.sleep_card_pause), sleep);
        sleepTitle = sleep[0];
        sleepCard.setOnClickListener(v -> cycleSleep());
        TextView[] sound = new TextView[2];
        View soundCard = card(1, up(R.string.card_sound), AmbientSound.label(AmbientSound.mode(this)),
                getString(R.string.sound_card_body), sound);
        soundCard.setOnClickListener(v -> {
            String mode = AmbientSound.mode(this);
            int i = java.util.Arrays.asList(AmbientSound.MODES).indexOf(mode);
            String nextMode = AmbientSound.MODES[(i + 1) % AmbientSound.MODES.length];
            AmbientSound.setMode(this, nextMode);
            sound[0].setText(AmbientSound.label(nextMode));
            preview(nextMode);
        });

        newRow();
        TextView[] radio = new TextView[2];
        View radioCard = card(1, up(R.string.radio), "", "", radio);
        radioTitle = radio[0];
        radioBody = radio[1];
        radioCard.setOnClickListener(v -> pickRadio());
        Radio.setListener(this::refreshRadio);
        refreshRadio();
        card(1, up(R.string.darshan), getString(R.string.darshan_card_title), getString(R.string.darshan_card_body), null)
                .setOnClickListener(v -> pickDarshan());
        List<String[]> recipes = Daily.read(this, "today/recipes.txt", 3);
        if (!recipes.isEmpty()) {
            String[] r = recipes.get(Daily.pick(today, recipes.size(), 3));
            card(1, up(R.string.recipe), r[0], r[1] + "\n" + getString(R.string.recipe_card_body), null)
                    .setOnClickListener(v -> YouTube.search(this, r[2]));
        }

        newRow();
        card(1, up(R.string.btn_phone), getString(R.string.drop_title), getString(R.string.drop_card_body), null)
                .setOnClickListener(v -> startActivity(new android.content.Intent(this, PhoneDropActivity.class)));
        TextView[] usage = new TextView[2];
        card(1, up(R.string.card_screen_time), "", "", usage);
        String[] week = ScreenTime.summary(this);
        usage[0].setText(week[0]);
        usage[1].setText(week[1]);
        gamesRow();

        page.post(() -> {
            View first = page.getChildAt(2);
            if (first instanceof ViewGroup && ((ViewGroup) first).getChildCount() > 0) {
                ((ViewGroup) first).getChildAt(0).requestFocus();
            }
        });
    }

    /** The tithi after the day's sunrise tithi, by name. */
    private static String nextTithi(Panchang.Day p) {
        int t = (p.tithi + 1) % 30;
        if (t == 14) return L10n.get(R.string.purnima);
        if (t == 29) return L10n.get(R.string.amavasya);
        return L10n.array(R.array.tithi)[t % 15];
    }

    private void newRow() {
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        rowWeight = 0;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(18);
        page.addView(row, lp);
    }

    /**
     * A focusable glass card in the current row, {@code span} thirds wide. {@code out}, when given, receives the
     * title and body views for later updates.
     */
    private View card(int span, String label, String title, CharSequence body, TextView[] out) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(16), dp(20), dp(18));
        card.setFocusable(true);
        card.setClickable(true);
        card.setMinimumHeight(dp(150));
        if (comic) {
            // A comic panel, a little tilted, that goes red when the remote lands on it.
            float[] tilts = {-1.5f, 1.2f, -0.8f, 1.6f};
            ComicHome.PanelDrawable panel = new ComicHome.PanelDrawable(density, tilts[tiltTurn++ % tilts.length], true);
            card.setBackground(panel);
            card.setPadding(dp(22), dp(18), dp(26), dp(24));
            card.setOnFocusChangeListener((v, focused) -> {
                panel.focused = focused;
                panel.invalidateSelf();
                v.animate().scaleX(focused ? 1.03f : 1f).scaleY(focused ? 1.03f : 1f).setDuration(150).start();
            });
        } else {
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_focused}, panel(0x26FFFFFF, 0xFFFFFFFF, 3));
            bg.addState(new int[]{}, panel(0x73000000, 0x2EFFFFFF, 1));
            card.setBackground(bg);
            card.setOnFocusChangeListener((v, focused) ->
                    v.animate().scaleX(focused ? 1.03f : 1f).scaleY(focused ? 1.03f : 1f).setDuration(150).start());
        }
        if (!label.isEmpty()) {
            TextView l = text(12, 0xB3FFFFFF, UiFont.MEDIUM);
            l.setLetterSpacing(0.16f);
            l.setText(label);
            if (comic) {
                l.setBackground(new ComicHome.CaptionDrawable(density));
                l.setPadding(dp(8), dp(3), dp(8), dp(3));
                l.setLetterSpacing(0.04f);
                card.addView(l, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            } else {
                card.addView(l);
            }
        }
        TextView t = text(20, 0xFFFFFFFF, UiFont.MEDIUM);
        t.setSingleLine(false);
        t.setLineSpacing(dp(4), 1f);
        t.setText(title);
        t.setVisibility(title.isEmpty() && out == null ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(8);
        card.addView(t, tp);
        TextView b = text(15, 0xE6FFFFFF, UiFont.REGULAR);
        b.setSingleLine(false);
        b.setLineSpacing(dp(5), 1f);
        b.setText(body);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(8);
        card.addView(b, bp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, span);
        if (rowWeight > 0) lp.setMarginStart(dp(18));
        rowWeight += span;
        row.addView(card, lp);
        if (out != null) {
            out[0] = t;
            out[1] = b;
        }
        return card;
    }

    private GradientDrawable panel(int fill, int stroke, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setStroke(dp(strokeDp), stroke);
        d.setCornerRadius(dp(18));
        return d;
    }

    private TextView radioTitle, radioBody;

    private void refreshRadio() {
        if (radioTitle == null) return;
        String now = Radio.playing();
        radioTitle.setText(now.isEmpty() ? getString(R.string.off) : now);
        radioBody.setText(now.isEmpty() ? R.string.radio_card_off : R.string.radio_card_on);
    }

    private void pickRadio() {
        List<String[]> stations = Daily.read(this, "today/radio.txt", 3);
        boolean on = !Radio.playing().isEmpty();
        String[] labels = new String[stations.size() + (on ? 1 : 0)];
        int i = 0;
        if (on) labels[i++] = getString(R.string.radio_stop);
        for (String[] st : stations) labels[i++] = st[0] + "  ·  " + st[1];
        new android.app.AlertDialog.Builder(this, comic ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.radio)
                .setItems(labels, (d, which) -> {
                    if (on && which == 0) {
                        Radio.stop();
                        return;
                    }
                    String[] st = stations.get(which - (on ? 1 : 0));
                    Radio.play(this, st[0], st[2]);
                }).show();
    }

    private void pickDarshan() {
        List<String[]> temples = Daily.read(this, "today/darshan.txt", 3);
        String[] labels = new String[temples.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = temples.get(i)[0] + ", " + temples.get(i)[1];
        new android.app.AlertDialog.Builder(this, comic ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.darshan)
                .setItems(labels, (d, which) -> YouTube.search(this, temples.get(which)[2])).show();
    }

    private void gamesRow() {
        card(1, up(R.string.games), getString(R.string.games_card_title), getString(R.string.games_card_body), null)
                .setOnClickListener(v -> new android.app.AlertDialog.Builder(this, comic ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle(R.string.games)
                        .setItems(new String[]{getString(R.string.game_memory_start), getString(R.string.game_tictactoe_long)}, (d, which) ->
                                startActivity(new android.content.Intent(this, GameActivity.class)
                                        .putExtra(GameActivity.EXTRA_GAME, which == 0 ? "memory" : "tictactoe")))
                        .show());
    }

    /** A few seconds of the chosen sound, so the choice can be heard; stopped when the page closes. */
    private AmbientSound previewSound;
    private final Runnable stopPreview = () -> {
        if (previewSound != null) previewSound.stop();
        previewSound = null;
    };

    private void preview(String mode) {
        page.removeCallbacks(stopPreview);
        stopPreview.run();
        previewSound = new AmbientSound(this, mode);
        previewSound.start();
        page.postDelayed(stopPreview, 8000);
    }

    @Override
    protected void onDestroy() {
        Radio.setListener(null);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        page.removeCallbacks(stopPreview);
        stopPreview.run();
        super.onPause();
    }

    private void refreshStorage(String note) {
        if (storageBody == null) return;
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        ActivityManager am = getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mem);
        storageTitle.setText(getString(R.string.free_amount, gb(fs.getAvailableBytes())));
        String text = getString(R.string.storage_line, gb(fs.getAvailableBytes()), gb(fs.getTotalBytes())) + "\n"
                + getString(R.string.memory_line, mb(mem.availMem), mb(mem.totalMem)) + "\n"
                + (note == null ? getString(R.string.ok_frees_memory) : note);
        storageBody.setText(text);
    }

    /** Ends the background processes of every other app, then shows how much memory came back. */
    private void freeMemory() {
        ActivityManager am = getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo before = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(before);
        for (ApplicationInfo app : getPackageManager().getInstalledApplications(0)) {
            if (!app.packageName.equals(getPackageName())) am.killBackgroundProcesses(app.packageName);
        }
        storageBody.postDelayed(() -> {
            ActivityManager.MemoryInfo after = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(after);
            long freed = Math.max(0, after.availMem - before.availMem);
            refreshStorage(getString(R.string.freed_amount, mb(freed)));
        }, 800);
    }

    private void cycleSleep() {
        int left = SleepTimer.minutesLeft(this);
        int next = SleepTimer.CHOICES[0];
        if (left > 0) {
            next = 0;
            for (int c : SleepTimer.CHOICES) {
                if (c > left) {
                    next = c;
                    break;
                }
            }
        }
        SleepTimer.set(this, next);
        refreshSleep();
    }

    private void refreshSleep() {
        if (sleepTitle == null) return;
        int left = SleepTimer.minutesLeft(this);
        sleepTitle.setText(left == 0 ? getString(R.string.off) : getString(R.string.sleeping_in, left));
    }

    private static String gb(long bytes) {
        return String.format(Locale.getDefault(), "%.1f GB", bytes / 1e9);
    }

    private static String mb(long bytes) {
        return Math.round(bytes / 1048576.0) + " MB";
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    private TextView text(int sp, int color, int font) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(comic ? ComicHome.INK : color);
        t.setIncludeFontPadding(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        try {
            t.setTypeface(UiFont.get(this, font));
        } catch (Exception e) {
            t.setTypeface(Typeface.DEFAULT);
        }
        if (comic && (sp >= 20 || font == UiFont.MEDIUM)) {
            t.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
        }
        t.setGravity(Gravity.START);
        return t;
    }

    private String up(int res) {
        return getString(res).toUpperCase(Locale.getDefault());
    }
}
