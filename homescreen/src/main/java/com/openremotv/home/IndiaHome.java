package com.openremotv.home;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The IndiaMinimalist page: a photo of India behind everything, today's Panchang at the top left and the round
 * buttons at the top right, three glass cards (clock, weather or the day's verse, the photo's place and fact), the
 * favourite apps in a glass dock, the rest of the apps below it, and the news along the bottom. The middle of the
 * screen stays clear for the photo: the rest of the apps fade in only when the remote goes down to them.
 */
final class IndiaHome extends FrameLayout {

    /** What the page asks of Home. */
    interface Host {
        void openApp(AppList.App app);
        void button(String which, View anchor);
    }

    /** The round buttons, left to right, and their spoken names. */
    static final String[] BUTTONS = {"wifi", "voice", "radio", "games", "memory", "darshan", "phone", "recipe", "sleep", "today", "settings"};
    static final int[] NAMES = {R.string.btn_wifi, R.string.btn_voice, R.string.radio, R.string.games, R.string.btn_memory,
            R.string.darshan, R.string.btn_phone, R.string.recipe, R.string.sleep_title, R.string.today, R.string.btn_settings};
    static final int[] ICONS = {
            R.drawable.ic_wifi, R.drawable.ic_mic, R.drawable.ic_radio, R.drawable.ic_dice, R.drawable.ic_memory,
            R.drawable.ic_temple, R.drawable.ic_phone, R.drawable.ic_recipe, R.drawable.ic_moon, R.drawable.ic_today,
            R.drawable.ic_settings,
    };

    private static final int DOCK_SIZE = 4;

    private final float d;
    private final Theme theme;
    private final Host host;
    private final Wallpapers wallpapers;
    private final ImageView[] photo = new ImageView[2];
    private int front;
    private final TextView today;
    private final LinearLayout buttons;
    final Widgets cards;
    private final ScrollView scroll;
    private final LinearLayout dock;
    private final GridLayout grid;
    private final View topShade;

    IndiaHome(Context context, Theme theme, Host host) {
        super(context);
        L10n.init(context);
        this.theme = theme;
        this.host = host;
        d = getResources().getDisplayMetrics().density;
        setBackgroundColor(0xFF0B0B0F);
        wallpapers = new Wallpapers(theme);

        // The photo, two deep so a new one fades in over the last.
        for (int i = 0; i < 2; i++) {
            photo[i] = new ImageView(context);
            photo[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo[i].setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(photo[i], new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        View dim = new View(context);
        dim.setBackgroundColor((Math.round(theme.wallpaperDim * 2.55f) << 24));
        addView(dim, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // A darker band at the top, so the Panchang and the buttons read over a bright sky.
        topShade = new View(context);
        topShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x99000000, 0x00000000}));
        addView(topShade, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150), Gravity.TOP));

        // Today's Panchang, top left.
        today = new TextView(context);
        today.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        today.setTextColor(0xF2FFFFFF);
        today.setTypeface(UiFont.get(context, UiFont.MEDIUM));
        today.setLineSpacing(dp(1), 1f);
        today.setShadowLayer(dp(6), 0, dp(1), 0xE6000000);
        today.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams tp = new LayoutParams(dp(300), dp(62), Gravity.TOP | Gravity.START);
        tp.leftMargin = dp(84);
        tp.topMargin = dp(14);
        today.setGravity(Gravity.CENTER_VERTICAL);
        addView(today, tp);

        // The round buttons, top right.
        buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LayoutParams bp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48), Gravity.TOP | Gravity.END);
        bp.rightMargin = dp(56);
        bp.topMargin = dp(20);
        addView(buttons, bp);
        for (int i = 0; i < BUTTONS.length; i++) {
            String which = BUTTONS[i];
            ImageView b = new ImageView(context);
            b.setImageResource(ICONS[i]);
            b.setImageTintList(new android.content.res.ColorStateList(
                    new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xFF141414, 0xFFFFFFFF}));
            b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            b.setPadding(dp(11), dp(11), dp(11), dp(11));
            b.setBackground(roundButton());
            b.setFocusable(true);
            b.setClickable(true);
            b.setContentDescription(context.getString(NAMES[i]));
            b.setOnClickListener(v -> host.button(which, v));
            b.setOnFocusChangeListener((v, f) -> v.animate().scaleX(f ? 1.12f : 1f).scaleY(f ? 1.12f : 1f)
                    .setDuration(theme.reducedMotion ? 0 : 120).start());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(42));
            lp.gravity = Gravity.CENTER_VERTICAL;
            lp.setMarginStart(dp(i == 0 ? 0 : 10));
            buttons.addView(b, lp);
            b.setId(generateViewId());
        }

        // The three cards.
        cards = new Widgets(context, this, 86, 104, false);

        // The dock and, below it, the rest of the apps, in one scrolling column under the cards.
        scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setSmoothScrollingEnabled(true);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setPadding(dp(60), dp(56), dp(60), dp(140));
        scroll.addView(column);
        LayoutParams sp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        sp.topMargin = dp(200);
        addView(scroll, sp);

        dock = new LinearLayout(context);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setClipChildren(false);
        dock.setPadding(dp(20), dp(13), dp(20), dp(13));
        GradientDrawable glass = new GradientDrawable();
        glass.setColor(0x1FFFFFFF);
        glass.setStroke(Math.max(1, dp(1)), 0x40FFFFFF);
        glass.setCornerRadius(dp(26));
        dock.setBackground(glass);
        column.addView(dock, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(130)));

        grid = new GridLayout(context);
        grid.setColumnCount(DOCK_SIZE);
        grid.setClipChildren(false);
        grid.setAlpha(0f);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.topMargin = dp(26);
        gp.leftMargin = dp(20);
        column.addView(grid, gp);

        fillApps();
        nextPhoto();
    }

    private int dp(float v) {
        return Math.round(v * d);
    }

    private StateListDrawable roundButton() {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable on = new GradientDrawable();
        on.setShape(GradientDrawable.OVAL);
        on.setColor(0xFFFFFFFF);
        GradientDrawable off = new GradientDrawable();
        off.setShape(GradientDrawable.OVAL);
        off.setColor(0x33FFFFFF);
        off.setStroke(Math.max(1, dp(1)), 0x40FFFFFF);
        s.addState(new int[]{android.R.attr.state_focused}, on);
        s.addState(new int[]{}, off);
        return s;
    }

    /** The dock's apps, then every other app in rows of four below. */
    void fillApps() {
        dock.removeAllViews();
        grid.removeAllViews();
        PackageManager pm = getContext().getPackageManager();
        List<AppList.App> docked = AppList.dock(getContext(), theme.dockApps, DOCK_SIZE);
        for (int i = 0; i < docked.size(); i++) {
            View tile = tile(docked.get(i), pm, true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            lp.setMarginStart(dp(i == 0 ? 0 : 30));
            dock.addView(tile, lp);
        }
        java.util.Set<String> inDock = new java.util.HashSet<>();
        for (AppList.App app : docked) inDock.add(app.pkg);
        List<AppList.App> rest = new ArrayList<>();
        for (AppList.App app : AppList.all(getContext())) if (!inDock.contains(app.pkg)) rest.add(app);
        for (AppList.App app : rest) {
            View tile = tile(app, pm, false);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(176);
            lp.height = dp(98);
            lp.setMargins(0, dp(10), dp(31), dp(32));
            grid.addView(tile, lp);
        }
        linkFocus();
    }

    /** A tile: the app's banner filling it, or its icon over its name; white-rimmed and larger when focused. */
    private View tile(AppList.App app, PackageManager pm, boolean inDock) {
        FrameLayout tile = new FrameLayout(getContext());
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setContentDescription(app.label);
        tile.setId(generateViewId());
        boolean banner = app.hasBanner(pm);
        GradientDrawable base = new GradientDrawable();
        base.setCornerRadius(dp(14));
        base.setColor(banner ? 0x00000000 : 0xE61C1C20);
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(14));
        ring.setColor(banner ? 0x00000000 : 0xF22A2A30);
        ring.setStroke(dp(3), 0xFFFFFFFF);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, ring);
        bg.addState(new int[]{}, base);
        tile.setForeground(banner ? bg : null);
        if (!banner) tile.setBackground(bg);
        ImageView art = new ImageView(getContext());
        art.setImageDrawable(app.art(pm));
        art.setClipToOutline(true);
        art.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(14));
            }
        });
        if (banner) {
            art.setScaleType(ImageView.ScaleType.FIT_XY);
            tile.addView(art, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LayoutParams ap = new LayoutParams(dp(40), dp(40), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            ap.topMargin = dp(16);
            tile.addView(art, ap);
            TextView name = new TextView(getContext());
            name.setText(app.label);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            name.setTextColor(0xF2FFFFFF);
            name.setTypeface(UiFont.get(getContext(), UiFont.REGULAR));
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            LayoutParams np = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            np.setMargins(dp(8), 0, dp(8), dp(12));
            tile.addView(name, np);
        }
        tile.setOnClickListener(v -> host.openApp(app));
        tile.setOnFocusChangeListener((v, f) -> {
            v.animate().scaleX(f ? 1.06f : 1f).scaleY(f ? 1.06f : 1f).setDuration(theme.reducedMotion ? 0 : 140).start();
            v.setZ(f ? dp(4) : 0);
            if (!f) return;
            if (inDock) {
                scroll.smoothScrollTo(0, 0);
                grid.animate().alpha(0f).setDuration(theme.reducedMotion ? 0 : 200).start();
            } else {
                grid.animate().alpha(1f).setDuration(theme.reducedMotion ? 0 : 200).start();
                // The focused row goes to the top of the space under the cards, clear of the news line.
                scroll.smoothScrollTo(0, grid.getTop() + v.getTop() - dp(12));
            }
        });
        tile.setOutlineProvider(null);
        return tile;
    }

    /** Up from the dock goes to the buttons, down to the first row; the buttons come back down to the dock. */
    private void linkFocus() {
        int nd = dock.getChildCount(), nb = buttons.getChildCount();
        for (int i = 0; i < nd; i++) {
            View t = dock.getChildAt(i);
            t.setNextFocusUpId(buttons.getChildAt(Math.min(nb - 1, Math.round(i * (nb - 1) / (float) Math.max(1, nd - 1)))).getId());
            t.setNextFocusLeftId(dock.getChildAt(Math.max(0, i - 1)).getId());
            t.setNextFocusRightId(dock.getChildAt(Math.min(nd - 1, i + 1)).getId());
            if (grid.getChildCount() > 0) t.setNextFocusDownId(grid.getChildAt(Math.min(grid.getChildCount() - 1, i)).getId());
        }
        for (int i = 0; i < nb; i++) {
            View b = buttons.getChildAt(i);
            b.setNextFocusLeftId(buttons.getChildAt(Math.max(0, i - 1)).getId());
            b.setNextFocusRightId(buttons.getChildAt(Math.min(nb - 1, i + 1)).getId());
            b.setNextFocusUpId(b.getId());
            if (nd > 0) b.setNextFocusDownId(dock.getChildAt(Math.min(nd - 1, Math.round(i * (nd - 1) / (float) Math.max(1, nb - 1)))).getId());
        }
        for (int i = 0; i < grid.getChildCount() && i < DOCK_SIZE; i++) {
            if (nd > 0) grid.getChildAt(i).setNextFocusUpId(dock.getChildAt(Math.min(nd - 1, i)).getId());
        }
    }

    /** Puts the remote on the first dock app. */
    void focusStart() {
        View first = dock.getChildCount() > 0 ? dock.getChildAt(0) : buttons.getChildAt(0);
        first.requestFocus();
        scroll.scrollTo(0, 0);
        grid.setAlpha(0f);
    }

    /** Back on Home while down in the apps: up to the dock again. */
    boolean onBack() {
        View f = findFocus();
        if (f != null && f.getParent() == grid) {
            focusStart();
            return true;
        }
        return false;
    }

    /** The next photo, faded in over the last, with its place and fact on the third card. */
    void nextPhoto() {
        File f = wallpapers.next();
        if (f == null) {
            cards.setMonument("", "");
            return;
        }
        int w = getResources().getDisplayMetrics().widthPixels, h = getResources().getDisplayMetrics().heightPixels;
        Bitmap bmp = Wallpapers.decode(f, w, h);
        if (bmp == null) return;
        ImageView back = photo[front], next = photo[1 - front];
        next.setImageBitmap(bmp);
        next.setAlpha(0f);
        // The incoming photo goes just above the outgoing one; everything else stays above both.
        ViewGroup.LayoutParams lp = next.getLayoutParams();
        removeView(next);
        addView(next, 1, lp);
        next.animate().alpha(1f).setDuration(theme.reducedMotion ? 300 : 900).withEndAction(() -> back.setImageDrawable(null)).start();
        front = 1 - front;
        if (theme.captions) cards.setMonument(wallpapers.caption(f), wallpapers.fact(f));
    }

    void setToday(CharSequence text) {
        today.setText(text);
    }
}
