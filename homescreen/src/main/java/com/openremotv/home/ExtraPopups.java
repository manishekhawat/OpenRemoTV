package com.openremotv.home;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.List;

/**
 * The pop-ups behind the home screen's added buttons: sleep timer, radio, free memory, live darshan, recipe of the
 * day and games. Each is a list of rows in the home screen's own pop-up style (glass, or paper in MangaHome).
 */
final class ExtraPopups {
    private final MainActivity a;

    ExtraPopups(MainActivity activity) {
        this.a = activity;
    }

    /** sleep_timer: Off, 30, 60 or 90 minutes under the moon pill; the running choice is marked. */
    void showSleepChoices() {
        View anchor = a.findViewById(R.id.btnSleep);
        View panel = a.getLayoutInflater().inflate(R.layout.popup_inputs, a.root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        final PopupWindow popup = a.glassPopup(panel, (int) (240 * a.density));
        int left = SleepTimer.minutesLeft(a);
        View focus = null;
        int[] choices = {0, 30, 60, 90};
        for (int minutes : choices) {
            View row = a.getLayoutInflater().inflate(R.layout.item_input, rows, false);
            ((ImageView) row.findViewById(R.id.icon)).setImageResource(minutes == 0 ? R.drawable.ic_play : R.drawable.ic_moon);
            ((TextView) row.findViewById(R.id.title)).setText(minutes == 0
                    ? a.getString(R.string.sleep_off) : minutes + " minutes");
            row.findViewById(R.id.dot).setVisibility(View.GONE);
            TextView status = row.findViewById(R.id.status);
            boolean current = minutes == 0 ? left == 0 : left > 0 && left <= minutes && (minutes == 30 || left > minutes - 30);
            status.setText(current ? (left == 0 ? "" : left + " min left") : "");
            row.setOnClickListener(v -> {
                popup.dismiss();
                SleepTimer.set(a, minutes);
                a.updateSleepPill();
            });
            rows.addView(row);
            if (current) focus = row;
        }
        a.dropIn(popup, panel, anchor, Gravity.END);
        (focus != null ? focus : rows.getChildAt(0)).requestFocus();
    }

    /** radio_pill: the stations under the pill, the playing one marked, with Stop while one plays. */
    void showRadio() {
        View anchor = a.findViewById(R.id.btnRadio);
        View panel = a.getLayoutInflater().inflate(R.layout.popup_inputs, a.root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        final PopupWindow popup = a.glassPopup(panel, (int) (500 * a.density));
        String playing = Radio.playing();
        View focus = null;
        if (!playing.isEmpty()) {
            View stop = a.getLayoutInflater().inflate(R.layout.item_input, rows, false);
            ((ImageView) stop.findViewById(R.id.icon)).setImageResource(R.drawable.ic_radio);
            ((TextView) stop.findViewById(R.id.title)).setText("Stop the radio");
            stop.findViewById(R.id.dot).setVisibility(View.GONE);
            stop.setOnClickListener(v -> {
                popup.dismiss();
                Radio.stop();
            });
            rows.addView(stop);
        }
        for (String[] st : Daily.read(a, "today/radio.txt", 3)) {
            View row = a.getLayoutInflater().inflate(R.layout.item_input, rows, false);
            ((ImageView) row.findViewById(R.id.icon)).setImageResource(R.drawable.ic_radio);
            ((TextView) row.findViewById(R.id.title)).setText(st[0]);
            boolean on = playing.startsWith(st[0]);
            row.findViewById(R.id.dot).setVisibility(on ? View.VISIBLE : View.GONE);
            ((TextView) row.findViewById(R.id.status)).setText(on ? "Playing" : st[1]);
            row.setOnClickListener(v -> {
                popup.dismiss();
                Radio.play(a, st[0], st[2]);
            });
            rows.addView(row);
            if (on) focus = row;
        }
        // In the middle of the screen, where the list is easy to read, instead of hanging off the pill.
        panel.setAlpha(0f);
        if (a.comicHome != null) a.comicPopup(panel);
        popup.showAtLocation(anchor, Gravity.CENTER, 0, 0);
        panel.animate().alpha(1f).setDuration(a.reducedMotion ? Motion.REDUCED_MS : 200).start();
        (focus != null ? focus : rows.getChildAt(0)).requestFocus();
    }

    /** more_pills: ends other apps' background processes and says how much memory came back. */
    void freeMemory() {
        android.app.ActivityManager am = a.getSystemService(android.app.ActivityManager.class);
        android.app.ActivityManager.MemoryInfo before = new android.app.ActivityManager.MemoryInfo();
        am.getMemoryInfo(before);
        for (ApplicationInfo app : a.getPackageManager().getInstalledApplications(0)) {
            if (!app.packageName.equals(a.getPackageName())) am.killBackgroundProcesses(app.packageName);
        }
        a.root.postDelayed(() -> {
            android.app.ActivityManager.MemoryInfo after = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(after);
            long freed = Math.max(0, after.availMem - before.availMem) / 1048576;
            android.widget.Toast.makeText(a, "Freed " + freed + " MB · " + (after.availMem / 1048576)
                    + " MB of " + (after.totalMem / 1048576) + " MB free", android.widget.Toast.LENGTH_LONG).show();
        }, 800);
    }

    /** more_pills: the temples under the pill; OK searches YouTube for that temple's live stream. */
    void showDarshan() {
        View anchor = a.findViewById(R.id.btnDarshan);
        View panel = a.getLayoutInflater().inflate(R.layout.popup_inputs, a.root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        final PopupWindow popup = a.glassPopup(panel, (int) (340 * a.density));
        for (String[] t : Daily.read(a, "today/darshan.txt", 3)) {
            View row = a.getLayoutInflater().inflate(R.layout.item_input, rows, false);
            ((ImageView) row.findViewById(R.id.icon)).setImageResource(R.drawable.ic_temple);
            ((TextView) row.findViewById(R.id.title)).setText(t[0]);
            row.findViewById(R.id.dot).setVisibility(View.GONE);
            ((TextView) row.findViewById(R.id.status)).setText(t[1]);
            row.setOnClickListener(v -> {
                popup.dismiss();
                YouTube.search(a, t[2]);
            });
            rows.addView(row);
        }
        a.dropIn(popup, panel, anchor, Gravity.END);
        if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus();
    }

    /** more_pills: today's dish first, then a few others; OK searches YouTube for how it is made. */
    void showRecipe() {
        List<String[]> recipes = Daily.read(a, "today/recipes.txt", 3);
        if (recipes.isEmpty()) return;
        View anchor = a.findViewById(R.id.btnRecipe);
        View panel = a.getLayoutInflater().inflate(R.layout.popup_inputs, a.root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        final PopupWindow popup = a.glassPopup(panel, (int) (520 * a.density));
        int today = Daily.pick(LocalDate.now(), recipes.size(), 3);
        for (int i = 0; i < Math.min(5, recipes.size()); i++) {
            String[] r = recipes.get((today + i * 7) % recipes.size());
            View row = a.getLayoutInflater().inflate(R.layout.item_input, rows, false);
            ((ImageView) row.findViewById(R.id.icon)).setImageResource(R.drawable.ic_recipe);
            ((TextView) row.findViewById(R.id.title)).setText(i == 0 ? "Today: " + r[0] : r[0]);
            row.findViewById(R.id.dot).setVisibility(View.GONE);
            ((TextView) row.findViewById(R.id.status)).setText(r[1]);
            row.setOnClickListener(v -> {
                popup.dismiss();
                YouTube.search(a, r[2]);
            });
            rows.addView(row);
        }
        a.dropIn(popup, panel, anchor, Gravity.END);
        rows.getChildAt(0).requestFocus();
    }

    /** games_pill: the games under the dice pill; OK starts one. */
    void showGames() {
        View anchor = a.findViewById(R.id.btnSurprise);
        View panel = a.getLayoutInflater().inflate(R.layout.popup_inputs, a.root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        final PopupWindow popup = a.glassPopup(panel, (int) (340 * a.density));
        String[][] games = {{"memory", "Memory", "Find the pairs"}, {"tictactoe", "Tic-tac-toe", "Play against the TV"}};
        for (String[] g : games) {
            View row = a.getLayoutInflater().inflate(R.layout.item_input, rows, false);
            ((ImageView) row.findViewById(R.id.icon)).setImageResource(R.drawable.ic_dice);
            ((TextView) row.findViewById(R.id.title)).setText(g[1]);
            row.findViewById(R.id.dot).setVisibility(View.GONE);
            ((TextView) row.findViewById(R.id.status)).setText(g[2]);
            row.setOnClickListener(v -> {
                popup.dismiss();
                a.open(new Intent(a, GameActivity.class).putExtra(GameActivity.EXTRA_GAME, g[0]));
            });
            rows.addView(row);
        }
        a.dropIn(popup, panel, anchor, Gravity.END);
        rows.getChildAt(0).requestFocus();
    }
}
