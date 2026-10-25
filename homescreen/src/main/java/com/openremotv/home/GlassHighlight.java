package com.openremotv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;

/**
 * The frosted glass that follows the remote on IndiaMinimalist: a pane with a soft sheen, a bright top edge, a crisp
 * white rim and a faint glow, gliding from button to button and tile to tile with a little overshoot. There is no
 * live blur behind it (a 2 GB TV can't afford one): the frost is the sheen and the rim. With reduced motion it
 * jumps instead of gliding.
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
        place(cornerDp, first || reducedMotion);
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
        int[] at = new int[2], base = new int[2];
        v.getLocationInWindow(at);
        parent.getLocationInWindow(base);
        // The view's own box, before its scale animation, grown to the size it is heading for, plus a margin.
        float grow = (targetScale - 1f) / 2f, pad = 4 * d;
        float sx = v.getScaleX(), sy = v.getScaleY();
        float w0 = v.getWidth(), h0 = v.getHeight();
        // getLocationInWindow includes the current scale (about the centre); undo it to find the unscaled box.
        float left = at[0] - base[0] + w0 * (sx - 1f) / 2f;
        float top = at[1] - base[1] + h0 * (sy - 1f) / 2f;
        float w = w0 * (1 + 2 * grow) + 2 * pad, h = h0 * (1 + 2 * grow) + 2 * pad;
        float x = left - w0 * grow - pad, y = top - h0 * grow - pad;
        boolean resize = getLayoutParams().width != Math.round(w) || getLayoutParams().height != Math.round(h);
        radius = cornerDp < 0 ? -1 : cornerDp * d + pad;
        if (resize) {
            ViewGroup.LayoutParams lp = getLayoutParams();
            lp.width = Math.round(w);
            lp.height = Math.round(h);
            setLayoutParams(lp);
        }
        animate().cancel();
        if (jump) {
            setTranslationX(x);
            setTranslationY(y);
        } else {
            animate().translationX(x).translationY(y).setDuration(260)
                    .setInterpolator(new OvershootInterpolator(0.8f)).start();
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
