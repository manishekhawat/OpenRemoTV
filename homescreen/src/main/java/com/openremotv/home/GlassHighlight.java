package com.openremotv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;

/**
 * The frosted glass that follows the remote on IndiaMinimalist: a pane with a soft sheen, a bright top edge, a crisp
 * white rim and a faint glow, gliding from button to button and tile to tile. There is no live blur behind it (a
 * 2 GB TV can't afford one): the frost is the sheen and the rim.
 */
final class GlassHighlight extends View {
    private final float d;
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final boolean reducedMotion;
    private float radius;
    private View target;
    private float targetScale = 1f;
    private boolean shown;

    GlassHighlight(Context context, boolean reducedMotion) {
        super(context);
        this.reducedMotion = reducedMotion;
        d = getResources().getDisplayMetrics().density;
        fill.setColor(0x1FFFFFFF);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.6f * d);
        rim.setColor(0xD9FFFFFF);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(6f * d);
        glow.setColor(0x26FFFFFF);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setAlpha(0f);
        // Drawn once into a texture: moving it is then only a translation for the GPU.
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /** Glides to {@code v}, which grows to {@code scale} as it takes focus; {@code round}: a circle, not a card. */
    void moveTo(View v, float scale, boolean round) {
        target = v;
        targetScale = scale;
        float cornerDp = round ? -1 : 16;
        if (v.getWidth() == 0 || getParent() == null || ((View) getParent()).getWidth() == 0) {
            // Not laid out yet (Home just opened): try again on the next frame.
            v.post(() -> {
                if (target == v) moveTo(v, scale, round);
            });
            return;
        }
        boolean first = !shown;
        place(cornerDp, first);
        shown = true;
        if (first) animate().alpha(1f).setDuration(reducedMotion ? 0 : 150).start();
    }

    /** Follows the focused view when the page under it scrolls. */
    void follow() {
        if (target != null && shown) place(radius < 0 ? -1 : radius / d, true);
    }

    void hide() {
        shown = false;
        target = null;
        animate().alpha(0f).setDuration(reducedMotion ? 0 : 150).start();
    }

    private void place(float cornerDp, boolean jump) {
        View v = target;
        ViewGroup parent = (ViewGroup) getParent();
        if (v == null || parent == null || v.getWidth() == 0) return;
        // The view's layout box in the page's coordinates (scrolling included, its zoom not), then grown to the
        // size its zoom is heading for, plus a small margin all round.
        android.graphics.Rect box = new android.graphics.Rect();
        v.getDrawingRect(box);
        parent.offsetDescendantRectToMyCoords(v, box);
        float pad = 3 * d;
        float gw = box.width() * (targetScale - 1f) / 2f, gh = box.height() * (targetScale - 1f) / 2f;
        float x = box.left - gw - pad, y = box.top - gh - pad;
        int w = Math.round(box.width() + 2 * gw + 2 * pad), h = Math.round(box.height() + 2 * gh + 2 * pad);
        radius = cornerDp < 0 ? -1 : (cornerDp * targetScale + 3) * d;
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp.width != w || lp.height != h) {
            lp.width = w;
            lp.height = h;
            setLayoutParams(lp);
        }
        animate().cancel();
        if (jump) {
            setTranslationX(x);
            setTranslationY(y);
        } else {
            // A glide, quick at first and settling softly, the same with or without reduced motion: it is how the
            // remote's position is shown, and a jump loses the eye.
            animate().translationX(x).translationY(y).setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
        }
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        // A bright top edge fading down: the frost of the pane.
        sheen.setShader(new LinearGradient(0, 0, 0, h * 0.6f, 0x4DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas c) {
        float inset = 3 * d;
        r.set(inset, inset, getWidth() - inset, getHeight() - inset);
        float rad = radius < 0 ? Math.min(r.width(), r.height()) / 2f : radius;
        c.drawRoundRect(r, rad, rad, glow);
        c.drawRoundRect(r, rad, rad, fill);
        c.drawRoundRect(r, rad, rad, sheen);
        c.drawRoundRect(r, rad, rad, rim);
    }
}
