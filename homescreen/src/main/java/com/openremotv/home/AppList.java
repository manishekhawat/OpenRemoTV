package com.openremotv.home;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The apps Home can open: every app with a TV (Leanback) launcher entry, or a phone launcher entry when it has
 * none, minus this home screen. Their labels and banners (an icon when there is no banner) come from the package
 * manager; the last-opened times kept here order the grid, most recent first.
 */
final class AppList {
    /** One app on the TV. */
    static final class App {
        final String pkg;
        final CharSequence label;
        final Intent launch;

        App(String pkg, CharSequence label, Intent launch) {
            this.pkg = pkg;
            this.label = label;
            this.launch = launch;
        }

        Drawable art(PackageManager pm) {
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                Drawable banner = info.loadBanner(pm);
                return banner != null ? banner : info.loadIcon(pm);
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
        }

        boolean hasBanner(PackageManager pm) {
            try {
                return pm.getApplicationInfo(pkg, 0).loadBanner(pm) != null;
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }
    }

    private AppList() {}

    static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return i;
    }

    /** Every app Home can open, most recently opened first, then by name. */
    static List<App> all(Context context) {
        PackageManager pm = context.getPackageManager();
        Map<String, App> apps = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                String pkg = ri.activityInfo.packageName;
                if (apps.containsKey(pkg) || pkg.equals(context.getPackageName())) continue;
                Intent launch = launchIntent(pm, pkg);
                if (launch != null) apps.put(pkg, new App(pkg, ri.loadLabel(pm), launch));
            }
        }
        SharedPreferences opened = context.getSharedPreferences("opened", Context.MODE_PRIVATE);
        Collator byName = Collator.getInstance();
        List<App> out = new ArrayList<>(apps.values());
        out.sort(Comparator.<App>comparingLong(a -> -opened.getLong(a.pkg, 0))
                .thenComparing((a, b) -> byName.compare(a.label.toString(), b.label.toString())));
        return out;
    }

    /** The theme's dock apps that are installed, topped up from the most used apps to {@code count}. */
    static List<App> dock(Context context, List<String> wanted, int count) {
        List<App> all = all(context);
        List<App> out = new ArrayList<>();
        for (String pkg : wanted) {
            for (App a : all) if (a.pkg.equals(pkg) && out.size() < count) out.add(a);
        }
        for (App a : all) {
            if (out.size() >= count) break;
            if (!out.contains(a)) out.add(a);
        }
        return out;
    }

    /** Remembers an app being opened from Home, for the grid's order. */
    static void opened(Context context, String pkg) {
        context.getSharedPreferences("opened", Context.MODE_PRIVATE).edit().putLong(pkg, System.currentTimeMillis()).apply();
    }
}
