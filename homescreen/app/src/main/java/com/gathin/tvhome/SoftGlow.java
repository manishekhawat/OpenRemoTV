package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * A dark oval glow centred on the right edge of its bounds, fading to clear at the top, bottom and left edges, so
 * text over a photo reads without any box or edge showing (the wallpaper caption under readable_text).
 */
final class SoftGlow extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] colors;
    private final float[] stops;

    SoftGlow(int[] colors, float[] stops) {
        this.colors = colors;
        this.stops = stops;
    }

    @Override
    protected void onBoundsChange(Rect b) {
        if (b.width() <= 0 || b.height() <= 0) return;
        float ry = b.height() / 2f;
        RadialGradient g = new RadialGradient(0, 0, ry, colors, stops, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(b.width() / ry, 1f);           // an oval: as wide as the view, as tall as the view
        m.postTranslate(b.right, b.exactCenterY());
        g.setLocalMatrix(m);
        paint.setShader(g);
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawRect(getBounds(), paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        paint.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
