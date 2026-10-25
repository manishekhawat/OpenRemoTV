package com.openremotv.home;

import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.view.View;
import android.widget.Toast;

import java.time.LocalDate;
import java.util.List;

/**
 * The lists behind Home's round buttons: sleep timer, radio, live darshan, recipe of the day and games, plus Free
 * memory. Each opens in the middle of the screen in the page's style (glass, or paper on MangaHome).
 */
final class ExtraPopups {
    private final HomeActivity a;

    ExtraPopups(HomeActivity activity) {
        this.a = activity;
    }

    private PopupList list(int titleRes, int widthDp) {
        return new PopupList(a, a.theme.mangaStyle, a.getString(titleRes), widthDp);
    }

    /** Off, 30, 60 or 90 minutes; the running choice is marked. */
    void showSleepChoices(View anchor) {
        int left = SleepTimer.minutesLeft(a);
        PopupList p = list(R.string.sleep_title, 320);
        for (int minutes : new int[]{0, 30, 60, 90}) {
            boolean current = minutes == 0 ? left == 0 : left > 0 && left <= minutes && left > minutes - 30;
            String title = minutes == 0 ? a.getString(R.string.sleep_off) : a.getString(R.string.sleep_minutes, minutes);
            String status = current && left > 0 ? a.getString(R.string.sleep_left, left) : "";
            p.row(minutes == 0 ? R.drawable.ic_play : R.drawable.ic_moon, title, status, current, () -> SleepTimer.set(a, minutes));
        }
        p.show(anchor);
    }

    /** The stations, the playing one marked, with Stop while one plays. */
    void showRadio(View anchor) {
        String playing = Radio.playing();
        PopupList p = list(R.string.radio, 520);
        if (!playing.isEmpty()) p.row(R.drawable.ic_radio, a.getString(R.string.radio_stop), "", false, Radio::stop);
        for (String[] st : Daily.read(a, "today/radio.txt", 3)) {
            boolean on = playing.startsWith(st[0]);
            p.row(R.drawable.ic_radio, st[0], on ? a.getString(R.string.radio_playing) : st[1], on, () -> Radio.play(a, st[0], st[2]));
        }
        p.show(anchor);
    }

    /** Ends other apps' background processes and says how much memory came back. */
    void freeMemory(View anchor) {
        ActivityManager am = a.getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo before = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(before);
        for (ApplicationInfo app : a.getPackageManager().getInstalledApplications(0)) {
            if (!app.packageName.equals(a.getPackageName())) am.killBackgroundProcesses(app.packageName);
        }
        anchor.postDelayed(() -> {
            ActivityManager.MemoryInfo after = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(after);
            long freed = Math.max(0, after.availMem - before.availMem) / 1048576;
            Toast.makeText(a, a.getString(R.string.memory_freed, freed, after.availMem / 1048576, after.totalMem / 1048576),
                    Toast.LENGTH_LONG).show();
        }, 800);
    }

    /** The temples; OK searches YouTube for that temple's live stream. */
    void showDarshan(View anchor) {
        PopupList p = list(R.string.darshan, 460);
        for (String[] t : Daily.read(a, "today/darshan.txt", 3)) {
            p.row(R.drawable.ic_temple, t[0], t[1], false, () -> YouTube.search(a, t[2]));
        }
        p.show(anchor);
    }

    /** Today's dish first, then a few others; OK searches YouTube for how it is made. */
    void showRecipe(View anchor) {
        List<String[]> recipes = Daily.read(a, "today/recipes.txt", 3);
        if (recipes.isEmpty()) return;
        PopupList p = list(R.string.recipe, 560);
        int today = Daily.pick(LocalDate.now(), recipes.size(), 3);
        for (int i = 0; i < Math.min(5, recipes.size()); i++) {
            String[] r = recipes.get((today + i * 7) % recipes.size());
            p.row(R.drawable.ic_recipe, i == 0 ? a.getString(R.string.recipe_today, r[0]) : r[0], r[1], false,
                    () -> YouTube.search(a, r[2]));
        }
        p.show(anchor);
    }

    /** The games; OK starts one. */
    void showGames(View anchor) {
        PopupList p = list(R.string.games, 420);
        p.row(R.drawable.ic_dice, a.getString(R.string.game_memory), a.getString(R.string.game_memory_sub), false,
                () -> a.open(new Intent(a, GameActivity.class).putExtra(GameActivity.EXTRA_GAME, "memory")));
        p.row(R.drawable.ic_dice, a.getString(R.string.game_tictactoe), a.getString(R.string.game_tictactoe_sub), false,
                () -> a.open(new Intent(a, GameActivity.class).putExtra(GameActivity.EXTRA_GAME, "tictactoe")));
        p.show(anchor);
    }
}
