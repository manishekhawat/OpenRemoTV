package com.openremotv.home;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Today page's screen time: this week's watching (from Monday) per app, against last week's. Needs usage
 * access, given once over ADB (appops set com.openremotv.home GET_USAGE_STATS allow).
 */
final class ScreenTime {

    private ScreenTime() {
    }

    static boolean allowed(Context ctx) {
        AppOpsManager ops = ctx.getSystemService(AppOpsManager.class);
        return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(),
                ctx.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    /** {"title", "body"} for the card. */
    static String[] summary(Context ctx) {
        if (!allowed(ctx)) return new String[]{"Not switched on", "Needs usage access, given once from the computer."};
        ZoneId zone = ZoneId.systemDefault();
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long weekStart = monday.atStartOfDay(zone).toInstant().toEpochMilli();
        long lastStart = monday.minusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long now = System.currentTimeMillis();
        UsageStatsManager usm = ctx.getSystemService(UsageStatsManager.class);
        Map<String, UsageStats> week = usm.queryAndAggregateUsageStats(weekStart, now);
        Map<String, UsageStats> last = usm.queryAndAggregateUsageStats(lastStart, weekStart);
        long total = 0, lastTotal = 0;
        List<UsageStats> apps = new ArrayList<>();
        for (UsageStats s : week.values()) {
            if (ignore(ctx, s.getPackageName()) || s.getTotalTimeInForeground() < 60_000) continue;
            total += s.getTotalTimeInForeground();
            apps.add(s);
        }
        for (UsageStats s : last.values()) {
            if (!ignore(ctx, s.getPackageName())) lastTotal += s.getTotalTimeInForeground();
        }
        apps.sort((a, b) -> Long.compare(b.getTotalTimeInForeground(), a.getTotalTimeInForeground()));
        StringBuilder body = new StringBuilder();
        PackageManager pm = ctx.getPackageManager();
        for (int i = 0; i < Math.min(4, apps.size()); i++) {
            String pkg = apps.get(i).getPackageName();
            CharSequence label;
            try {
                label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            } catch (PackageManager.NameNotFoundException e) {
                label = pkg;
            }
            body.append(label).append("  ").append(hours(apps.get(i).getTotalTimeInForeground())).append('\n');
        }
        if (lastTotal > 0) body.append("Last week ").append(hours(lastTotal));
        return new String[]{"This week " + hours(total), body.toString().trim()};
    }

    private static boolean ignore(Context ctx, String pkg) {
        return pkg.equals(ctx.getPackageName()) || pkg.startsWith("com.android.") || pkg.equals("android")
                || pkg.contains("launcher") || pkg.contains("settings");
    }

    static String hours(long ms) {
        long minutes = ms / 60_000;
        return minutes < 60 ? minutes + " min" : (minutes / 60) + " h " + (minutes % 60) + " min";
    }
}
