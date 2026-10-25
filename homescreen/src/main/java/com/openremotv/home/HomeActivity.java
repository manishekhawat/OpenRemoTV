package com.openremotv.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.text.DateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * OpenRemoTV Home: the TV's home screen. It shows one of two pages, chosen by the theme the phone app installed:
 * MangaHome ({@link ComicHome}, a comic page) or IndiaMinimalist ({@link IndiaHome}, photos of India behind glass
 * cards). Both share the weather, the news, the Panchang and the lists behind the round buttons.
 */
public final class HomeActivity extends Activity implements IndiaHome.Host, ComicHome.Host {
    private static final long MINUTE = 60_000L;

    Theme theme;
    private FrameLayout root;
    private IndiaHome india;
    private ComicHome comic;
    private Weather weather;
    private Ticker ticker;
    private ExtraPopups popups;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int minutes;

    private final Runnable minuteTick = new Runnable() {
        @Override public void run() {
            minutes++;
            refresh();
            if (india != null && minutes % theme.rotationMinutes == 0) india.nextPhoto();
            main.postDelayed(this, MINUTE - System.currentTimeMillis() % MINUTE);
        }
    };

    /** Apps installed, removed, turned on or off: the dock and the grid follow. */
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (india != null) india.fillApps();
            if (comic != null) comic.fillApps();
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        L10n.init(this);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        theme = Theme.load(this);
        popups = new ExtraPopups(this);
        root = new FrameLayout(this);
        setContentView(root);

        if (theme.mangaLayout) {
            comic = new ComicHome(this, theme, theme.dockApps, this);
            root.addView(comic);
        } else {
            india = new IndiaHome(this, theme, this);
            root.addView(india);
        }
        if (theme.placeFromTv) GeoIp.refresh(this, this::recreate);
        if (theme.weatherEnabled()) {
            weather = new Weather(this, theme);
            weather.setListener(raw -> {
                if (india != null) india.cards.setWeather(raw);
                if (comic != null) comic.refresh();
            });
        }
        if (theme.newsTicker) ticker = new Ticker(this, india);
        if (india != null && theme.dailyCard) {
            india.cards.setDaily(dailyItems());
            india.cards.setVerse(Daily.verse(this, LocalDate.now()));
        }

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        refresh();
        if (india != null) india.focusStart();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
        if (weather != null) weather.update();
        if (ticker != null) ticker.resume();
        main.removeCallbacks(minuteTick);
        main.postDelayed(minuteTick, MINUTE - System.currentTimeMillis() % MINUTE);
    }

    @Override protected void onPause() {
        main.removeCallbacks(minuteTick);
        if (ticker != null) ticker.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        unregisterReceiver(packages);
        super.onDestroy();
    }

    /** Home pressed while Home is showing: back to the start of the page. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (india != null) india.focusStart();
    }

    @Override public void onBackPressed() {
        // Home has nowhere to go back to; only climb back up from the lower rows.
        if (india != null) india.onBack();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            AppLibrary.show(this);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** The greeting, the Panchang line and the weather and news caches, once a minute and on every return. */
    private void refresh() {
        if (india != null) {
            india.cards.setGreeting(greeting());
            india.setToday(todayText());
        }
        if (comic != null) comic.refresh();
        if (weather != null) weather.update();
    }

    private String greeting() {
        Theme.FestivalDate festival = theme.festivalToday(LocalDate.now());
        if (festival != null && !festival.greeting.isEmpty()) return festival.greeting;
        int h = LocalTime.now().getHour();
        return getString(h < 5 ? R.string.good_night : h < 12 ? R.string.good_morning : h < 17 ? R.string.good_afternoon
                : h < 21 ? R.string.good_evening : R.string.good_night);
    }

    /** "Ashwin Shukla Chaturdashi / Purnima tomorrow / Rahu kaal 4:18 – 5:43 PM". */
    private CharSequence todayText() {
        if (!theme.weatherEnabled()) return "";
        LocalDate today = LocalDate.now();
        ZoneId zone = ZoneId.systemDefault();
        Panchang.Day p = Panchang.of(today, theme.weatherLatitude, theme.weatherLongitude, zone);
        DateFormat time = android.text.format.DateFormat.getTimeFormat(this);
        long now = System.currentTimeMillis();
        StringBuilder line = new StringBuilder(p.title());
        List<Daily.Event> next = Daily.upcoming(theme, today, zone, 1);
        if (!next.isEmpty()) line.append("\n").append(next.get(0).when(today));
        if (p.rahuStart != 0 && now < p.rahuEnd) {
            line.append("\n").append(getString(R.string.rahu_kaal, time.format(new Date(p.rahuStart)), time.format(new Date(p.rahuEnd))));
        } else if (now < p.sunset) {
            line.append("\n").append(getString(R.string.sunset_at, time.format(new Date(p.sunset))));
        } else {
            line.append("\n").append(getString(R.string.sunrise_tomorrow, time.format(new Date(p.sunrise + 86_400_000L))));
        }
        return line;
    }

    /** The Hindi word of the day and tonight's sky, for the third card to turn over to. */
    private List<String[]> dailyItems() {
        List<String[]> out = new ArrayList<>();
        LocalDate today = LocalDate.now();
        Daily.Word w = Daily.word(this, today);
        if (w != null) out.add(new String[]{w.word + "  ·  " + w.sound, getString(R.string.word_of_the_day, w.meaning)});
        if (theme.weatherEnabled()) {
            NightSky.Report sky = NightSky.tonight(ZoneId.systemDefault(), theme.weatherLatitude, theme.weatherLongitude);
            StringBuilder planets = new StringBuilder();
            for (NightSky.Planet planet : sky.planets) {
                if (planets.length() > 0) planets.append(", ");
                planets.append(planet.name);
            }
            out.add(new String[]{getString(R.string.tonight, sky.phaseName()),
                    planets.length() == 0 ? getString(R.string.no_planets) : getString(R.string.look_for, planets)});
        }
        return out;
    }

    // ---- what the pages ask of Home --------------------------------------------------------------------------

    @Override public void openApp(AppList.App app) {
        AppList.opened(this, app.pkg);
        open(app.launch);
    }

    @Override public void open(Intent intent) {
        if (intent == null) return;
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void button(String which) {
        button(which, root);
    }

    @Override public void button(String which, View anchor) {
        switch (which) {
            case "wifi": openSettings(Settings.ACTION_WIFI_SETTINGS); break;
            case "voice": openVoice(); break;
            case "radio": popups.showRadio(anchor); break;
            case "games": popups.showGames(anchor); break;
            case "memory": popups.freeMemory(anchor); break;
            case "darshan": popups.showDarshan(anchor); break;
            case "phone": open(new Intent(this, PhoneDropActivity.class)); break;
            case "recipe": popups.showRecipe(anchor); break;
            case "sleep": popups.showSleepChoices(anchor); break;
            case "today": open(new Intent(this, TodayActivity.class)); break;
            case "settings": openSettings(Settings.ACTION_SETTINGS); break;
            case "apps": AppLibrary.show(this); break;
            default: break;
        }
    }

    private void openSettings(String action) {
        Intent i = new Intent(action);
        if (i.resolveActivity(getPackageManager()) == null) i = new Intent(Settings.ACTION_SETTINGS);
        open(i);
    }

    /** The TV's assistant, as the remote's mic button would open it. */
    private void openVoice() {
        Intent assist = new Intent(Intent.ACTION_ASSIST);
        if (assist.resolveActivity(getPackageManager()) == null) assist = new Intent("android.speech.action.WEB_SEARCH");
        open(assist);
    }
}
