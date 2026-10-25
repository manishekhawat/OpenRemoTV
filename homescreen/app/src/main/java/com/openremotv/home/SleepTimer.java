package com.openremotv.home;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.Toast;

/**
 * sleep_timer: puts the TV to sleep after 30, 60 or 90 minutes. An alarm warns a minute before, then pauses
 * whatever plays and locks the screen through device admin, which on Android TV sends the box to standby.
 * Admin is switched on once over ADB (dpm set-active-admin com.openremotv.home/.SleepTimer$Admin); without it the
 * timer still pauses playback and goes back to Home. restore.sh turns admin off again with
 * am broadcast -a com.openremotv.home.ADMIN_OFF -n com.openremotv.home/.SleepTimer$Receiver.
 */
final class SleepTimer {

    static final int[] CHOICES = {30, 60, 90};
    private static final String PREFS = "sleep_timer";
    private static final String UNTIL = "until";
    private static final String WARN = "com.openremotv.home.SLEEP_WARN";
    private static final String SLEEP = "com.openremotv.home.SLEEP_NOW";
    static final String ADMIN_OFF = "com.openremotv.home.ADMIN_OFF";

    private SleepTimer() {
    }

    /** Minutes left, rounded up, or 0 when no timer runs. */
    static int minutesLeft(Context ctx) {
        long until = prefs(ctx).getLong(UNTIL, 0);
        long left = until - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) ((left + 59_999) / 60_000);
    }

    /** Starts a timer of {@code minutes}, or cancels it with 0. */
    static void set(Context ctx, int minutes) {
        AlarmManager alarms = ctx.getSystemService(AlarmManager.class);
        alarms.cancel(pending(ctx, WARN));
        alarms.cancel(pending(ctx, SLEEP));
        if (minutes <= 0) {
            prefs(ctx).edit().remove(UNTIL).apply();
            return;
        }
        long ms = minutes * 60_000L;
        prefs(ctx).edit().putLong(UNTIL, System.currentTimeMillis() + ms).apply();
        long now = SystemClock.elapsedRealtime();
        alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, now + ms - 60_000L, pending(ctx, WARN));
        alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, now + ms, pending(ctx, SLEEP));
    }

    static boolean adminActive(Context ctx) {
        DevicePolicyManager dpm = ctx.getSystemService(DevicePolicyManager.class);
        return dpm != null && dpm.isAdminActive(new ComponentName(ctx, Admin.class));
    }

    private static PendingIntent pending(Context ctx, String action) {
        Intent i = new Intent(action).setClass(ctx, Receiver.class);
        return PendingIntent.getBroadcast(ctx, action.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void sleepNow(Context ctx) {
        prefs(ctx).edit().remove(UNTIL).apply();
        Radio.stop();
        AudioManager audio = ctx.getSystemService(AudioManager.class);
        if (audio != null) {
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE));
        }
        if (adminActive(ctx)) {
            try {
                ctx.getSystemService(DevicePolicyManager.class).lockNow();
                return;
            } catch (RuntimeException ignored) {
                // fall back to Home
            }
        }
        ctx.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** The timer's alarms, and restore.sh's request to give up device admin. */
    public static class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (WARN.equals(action)) {
                Toast.makeText(ctx, R.string.sleep_warning, Toast.LENGTH_LONG).show();
            } else if (SLEEP.equals(action)) {
                sleepNow(ctx);
            } else if (ADMIN_OFF.equals(action)) {
                set(ctx, 0);
                DevicePolicyManager dpm = ctx.getSystemService(DevicePolicyManager.class);
                ComponentName admin = new ComponentName(ctx, Admin.class);
                if (dpm != null && dpm.isAdminActive(admin)) dpm.removeActiveAdmin(admin);
            }
        }
    }

    /** Device admin with only the force-lock policy, for lockNow(). */
    public static class Admin extends DeviceAdminReceiver {
    }
}
