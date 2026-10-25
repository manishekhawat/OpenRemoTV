package com.openremotv.home;

import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * All apps: every app on the TV as a grid of banners over the dimmed Home, most recently opened first. Paper tiles in
 * ink on MangaHome, dark glass tiles on IndiaMinimalist. OK opens the app; Back closes the grid.
 */
final class AppLibrary {
    private static final int COLUMNS = 5;

    private AppLibrary() {}

    static void show(HomeActivity a) {
        boolean comic = a.theme.mangaStyle;
        float d = a.getResources().getDisplayMetrics().density;
        PackageManager pm = a.getPackageManager();
        Dialog dialog = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(comic ? 0xF2FBF8F1 : 0xE6000000));
        }

        LinearLayout page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(px(d, 64), px(d, 32), px(d, 64), px(d, 24));
        TextView title = new TextView(a);
        title.setText(a.getString(R.string.all_apps));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, comic ? 32 : 28);
        title.setTextColor(comic ? MangaSkin.INK : 0xFFFFFFFF);
        title.setTypeface(comic ? MangaSkin.lettering(UiFont.get(a, UiFont.MEDIUM)) : UiFont.get(a, UiFont.LIGHT));
        title.setPadding(0, 0, 0, px(d, 16));
        page.addView(title);

        GridLayout grid = new GridLayout(a);
        grid.setColumnCount(COLUMNS);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(px(d, 8), px(d, 8), px(d, 8), px(d, 24));
        List<AppList.App> apps = AppList.all(a);
        int tileW = px(d, 152), tileH = px(d, 112);
        for (AppList.App app : apps) {
            FrameLayout tile = new FrameLayout(a);
            tile.setFocusable(true);
            tile.setClickable(true);
            tile.setBackground(tileBackground(d, comic));
            tile.setContentDescription(app.label);
            ImageView art = new ImageView(a);
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setImageDrawable(app.art(pm));
            FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(d, 66), Gravity.TOP);
            ap.setMargins(px(d, 10), px(d, 10), px(d, 10), 0);
            tile.addView(art, ap);
            TextView name = new TextView(a);
            name.setText(app.label);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            name.setTextColor(comic ? MangaSkin.INK : 0xE6FFFFFF);
            name.setTypeface(UiFont.get(a, UiFont.MEDIUM));
            FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            np.setMargins(px(d, 6), 0, px(d, 6), px(d, 8));
            tile.addView(name, np);
            tile.setOnFocusChangeListener((v, f) -> v.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f)
                    .setDuration(a.theme.reducedMotion ? 0 : 120).start());
            tile.setOnClickListener(v -> {
                dialog.dismiss();
                a.openApp(app);
            });
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = tileW;
            gp.height = tileH;
            gp.setMargins(px(d, 10), px(d, 10), px(d, 10), px(d, 10));
            grid.addView(tile, gp);
        }
        ScrollView scroll = new ScrollView(a);
        scroll.setClipChildren(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(grid);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 0, 1));
        LinearLayout.LayoutParams sp = (LinearLayout.LayoutParams) scroll.getLayoutParams();
        sp.gravity = Gravity.CENTER_HORIZONTAL;
        dialog.setContentView(page);
        dialog.show();
        if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
    }

    private static StateListDrawable tileBackground(float d, boolean comic) {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable on = new GradientDrawable();
        GradientDrawable off = new GradientDrawable();
        on.setCornerRadius(px(d, comic ? 6 : 14));
        off.setCornerRadius(px(d, comic ? 6 : 14));
        if (comic) {
            on.setColor(MangaSkin.PAPER);
            on.setStroke(px(d, 5), MangaSkin.RED);
            off.setColor(MangaSkin.PAPER);
            off.setStroke(px(d, 3), MangaSkin.INK);
        } else {
            on.setColor(0x40FFFFFF);
            on.setStroke(px(d, 2), 0xFFFFFFFF);
            off.setColor(0x26FFFFFF);
            off.setStroke(Math.max(1, px(d, 1)), 0x24FFFFFF);
        }
        s.addState(new int[]{android.R.attr.state_focused}, on);
        s.addState(new int[]{}, off);
        return s;
    }

    private static int px(float d, float dp) {
        return Math.round(dp * d);
    }
}
