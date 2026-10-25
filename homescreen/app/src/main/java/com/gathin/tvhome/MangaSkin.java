package com.gathin.tvhome;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;

/**
 * manga_style's look for the parts of Home outside the cards: ink-framed tiles, paper pills with an ink ring that
 * go red when focused, ink panels with a paper border for the dock and the pop-ups, a red focus ring, a paper
 * speech bubble for the Panchang line and a paper strip for the ticker. Ink #141414, paper #FBF8F1, red #E0262B.
 */
final class MangaSkin {
    static final int INK = 0xFF141414;
    static final int PAPER = 0xFFFBF8F1;
    static final int RED = 0xFFE0262B;

    private MangaSkin() {
    }

    /** A rounded rectangle; stroke 0 for none. */
    static GradientDrawable shape(int fill, int strokePx, int stroke, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        if (strokePx > 0) d.setStroke(strokePx, stroke);
        d.setCornerRadius(radiusPx);
        return d;
    }

    /** A paper frame drawn over a tile's picture (the tile's foreground), as panels on a black page. */
    static Drawable tileFrame(float density, float radiusPx) {
        return shape(0x00000000, Math.round(3 * density), PAPER, radiusPx);
    }

    /** A top-bar pill: paper with an ink ring, red with an ink ring when focused, with a hard ink shadow. */
    static Drawable pill(float density) {
        int line = Math.round(3 * density);
        int drop = Math.round(3 * density);
        float r = 21 * density;
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, dropped(shape(RED, line, INK, r), shape(INK, 0, 0, r), drop));
        s.addState(new int[]{}, dropped(shape(PAPER, line, INK, r), shape(INK, 0, 0, r), drop));
        return s;
    }

    /** Icons and text in a pill: ink on paper, paper on red. */
    static ColorStateList pillTint() {
        return new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{PAPER, INK});
    }

    /** A pop-up panel: ink with a paper border and a red hard shadow. */
    static Drawable panel(float density) {
        float r = 10 * density;
        return dropped(shape(0xF7141414, Math.round(3 * density), PAPER, r), shape(RED, 0, 0, r), Math.round(6 * density));
    }

    /** The Panchang line's speech bubble: paper with an ink border. */
    static Drawable bubble(float density) {
        return dropped(shape(PAPER, Math.round(3 * density), INK, 16 * density), shape(INK, 0, 0, 16 * density),
                Math.round(4 * density));
    }

    /** The ticker strip: paper with a thick ink line along its top. */
    static Drawable strip(float density) {
        LayerDrawable l = new LayerDrawable(new Drawable[]{shape(INK, 0, 0, 0), shape(PAPER, 0, 0, 0)});
        l.setLayerInset(1, 0, Math.round(4 * density), 0, 0);
        return l;
    }

    /** A comic pop-up: paper in a thick ink line with a hard ink shadow (the comic page's pop-ups and app list). */
    static Drawable paperPanel(float density) {
        float r = 10 * density;
        return dropped(shape(PAPER, Math.round(4 * density), INK, r), shape(INK, 0, 0, r), Math.round(6 * density));
    }

    /** One app in the comic app list: a paper card in an ink line. */
    static Drawable paperCard(float density) {
        return shape(PAPER, Math.round(3 * density), INK, 10 * density);
    }

    /** Ink lettering on the rows, focused or not. */
    static ColorStateList rowText() {
        return ColorStateList.valueOf(INK);
    }

    /**
     * Restyles a pop-up's rows (item_input) for paper: a red bar under the focused row, ink lettering and icons
     * elsewhere, paper on red.
     */
    static void comicRows(android.view.ViewGroup rows, float density) {
        if (rows == null) return;
        for (int i = 0; i < rows.getChildCount(); i++) {
            android.view.View row = rows.getChildAt(i);
            StateListDrawable bg = new StateListDrawable();
            // Focused: a yellow caption box in a thick red line, so the ink lettering reads in both states.
            bg.addState(new int[]{android.R.attr.state_focused}, shape(0xFFFFF4C2, Math.round(4 * density), RED, 8 * density));
            bg.addState(new int[]{}, shape(0x00000000, 0, 0, 0));
            row.setBackground(bg);
            tint(row);
        }
    }

    private static void tint(android.view.View v) {
        if (v instanceof android.widget.TextView) {
            android.widget.TextView t = (android.widget.TextView) v;
            t.setTextColor(rowText());
            t.setShadowLayer(0, 0, 0, 0);
        } else if (v instanceof android.widget.ImageView) {
            ((android.widget.ImageView) v).setImageTintList(rowText());
        } else if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) tint(g.getChildAt(i));
        }
    }

    /** The comic lettering: bold italic of the UI font. */
    static Typeface lettering(Typeface base) {
        return Typeface.create(base == null ? Typeface.DEFAULT : base, Typeface.BOLD_ITALIC);
    }

    /** {@code front} over {@code shadow}, the shadow showing {@code drop} px down and to the right. */
    private static Drawable dropped(Drawable front, Drawable shadow, int drop) {
        LayerDrawable l = new LayerDrawable(new Drawable[]{shadow, front});
        l.setLayerInset(0, drop, drop, 0, 0);
        l.setLayerInset(1, 0, 0, drop, drop);
        return l;
    }
}
