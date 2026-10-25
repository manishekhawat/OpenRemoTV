package com.openremotv.home;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * A list of choices in the middle of the screen, one row each with an icon, a title and a line on the right: dark
 * glass with white text on IndiaMinimalist, paper with ink text and a red focused row on MangaHome. OK picks a row;
 * Back closes the list.
 */
final class PopupList {
    private static final int INK = 0xFF141414, PAPER = 0xFFFBF8F1, RED = 0xFFE0262B;

    private final Activity activity;
    private final boolean comic;
    private final float d;
    private final LinearLayout rows;
    private final PopupWindow window;
    private View focus;

    PopupList(Activity activity, boolean comic, String title, int widthDp) {
        this.activity = activity;
        this.comic = comic;
        d = activity.getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(comic ? 10 : 20));
        if (comic) {
            bg.setColor(PAPER);
            bg.setStroke(dp(4), INK);
        } else {
            bg.setColor(0xF0161616);
            bg.setStroke(Math.max(1, dp(1)), 0x33FFFFFF);
        }
        panel.setBackground(bg);
        if (title != null) {
            TextView t = new TextView(activity);
            t.setText(comic ? title.toUpperCase(java.util.Locale.getDefault()) : title);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, comic ? 20 : 18);
            t.setTextColor(comic ? INK : 0xFFFFFFFF);
            t.setTypeface(comic ? MangaSkin.lettering(UiFont.get(activity, UiFont.MEDIUM)) : UiFont.get(activity, UiFont.MEDIUM));
            t.setPadding(dp(10), 0, dp(10), dp(10));
            panel.addView(t);
        }
        rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(rows);
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        window = new PopupWindow(panel, dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        window.setBackgroundDrawable(new ColorDrawable(0));   // needed for Back to close it
        window.setElevation(dp(24));
    }

    private int dp(float v) {
        return Math.round(v * d);
    }

    /** Adds a row; {@code onPick} runs after the list closes. {@code marked} gets a dot and the first focus. */
    PopupList row(int icon, String title, String status, boolean marked, Runnable onPick) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(12), dp(10), dp(14), dp(10));
        row.setBackground(rowBackground());
        if (icon != 0) {
            ImageView i = new ImageView(activity);
            i.setImageResource(icon);
            i.setDuplicateParentStateEnabled(true);
            i.setImageTintList(comic ? MangaSkin.rowText() : android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(24), dp(24));
            ip.setMarginEnd(dp(14));
            row.addView(i, ip);
        }
        TextView t = text(16, UiFont.MEDIUM);
        t.setText(title);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (marked) {
            View dot = new View(activity);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(comic ? RED : 0xFF4ADE80);
            dot.setBackground(g);
            LinearLayout.LayoutParams dpl = new LinearLayout.LayoutParams(dp(8), dp(8));
            dpl.setMarginEnd(dp(10));
            row.addView(dot, dpl);
        }
        if (status != null && !status.isEmpty()) {
            TextView s = text(13, UiFont.REGULAR);
            s.setText(status);
            s.setAlpha(comic ? 1f : 0.7f);
            row.addView(s);
        }
        row.setContentDescription(status == null || status.isEmpty() ? title : title + ", " + status);
        row.setOnClickListener(v -> {
            window.dismiss();
            onPick.run();
        });
        rows.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (marked && focus == null) focus = row;
        return this;
    }

    private TextView text(int sp, int weight) {
        TextView t = new TextView(activity);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setDuplicateParentStateEnabled(true);
        t.setTypeface(UiFont.get(activity, weight));
        t.setTextColor(comic ? MangaSkin.rowText() : android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        return t;
    }

    private Drawable rowBackground() {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable on = new GradientDrawable();
        on.setCornerRadius(dp(comic ? 6 : 12));
        if (comic) {
            // A yellow caption box in a thick red line: the ink lettering reads on it.
            on.setColor(0xFFFFF4C2);
            on.setStroke(dp(4), RED);
        } else {
            on.setColor(0x33FFFFFF);
            on.setStroke(Math.max(1, dp(1.5f)), 0xCCFFFFFF);
        }
        s.addState(new int[]{android.R.attr.state_focused}, on);
        s.addState(new int[]{}, new ColorDrawable(0));
        return s;
    }

    boolean isEmpty() {
        return rows.getChildCount() == 0;
    }

    /** Opens the list in the middle of the screen with the marked row (or the first) focused. */
    void show(View anchor) {
        if (isEmpty()) return;
        window.showAtLocation(anchor, Gravity.CENTER, 0, 0);
        View first = focus != null ? focus : rows.getChildAt(0);
        first.post(first::requestFocus);
    }
}
