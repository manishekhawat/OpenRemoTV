package com.openremotv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.time.LocalTime;

/** A small clock face for the widgets' clock card: thin ring, twelve ticks, hour and minute hands. Redraws each minute. */
final class AnalogClockView extends View {
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hand = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            invalidate();
            long now = System.currentTimeMillis();
            postDelayed(this, 60_000L - now % 60_000L + 50);
        }
    };

    AnalogClockView(Context context) {
        super(context);
        float d = context.getResources().getDisplayMetrics().density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(1.5f * d);
        ring.setColor(0x66FFFFFF);
        tick.setStrokeWidth(1.5f * d);
        tick.setStrokeCap(Paint.Cap.ROUND);
        tick.setColor(0x99FFFFFF);
        hand.setStrokeCap(Paint.Cap.ROUND);
        hand.setColor(0xFFFFFFFF);
        accent.setStrokeCap(Paint.Cap.ROUND);
        accent.setColor(0xFFFF8A65);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** MangaHome: ink on paper, with a red second accent. */
    void useInk() {
        ring.setColor(0xCC141414);
        ring.setStrokeWidth(ring.getStrokeWidth() * 2);
        tick.setColor(0xFF141414);
        hand.setColor(0xFF141414);
        accent.setColor(0xFFE0262B);
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(tickRunnable);
        post(tickRunnable);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tickRunnable);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - ring.getStrokeWidth();
        if (r <= 0) return;
        canvas.drawCircle(cx, cy, r, ring);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            float inner = r * (i % 3 == 0 ? 0.78f : 0.86f);
            canvas.drawLine(cx + (float) Math.sin(a) * inner, cy - (float) Math.cos(a) * inner,
                    cx + (float) Math.sin(a) * r * 0.94f, cy - (float) Math.cos(a) * r * 0.94f, tick);
        }
        LocalTime now = LocalTime.now();
        double minute = now.getMinute() / 60.0;
        double hour = (now.getHour() % 12 + minute) / 12.0;
        hand.setStrokeWidth(r * 0.09f);
        drawHand(canvas, cx, cy, hour, r * 0.5f, hand);
        accent.setStrokeWidth(r * 0.06f);
        drawHand(canvas, cx, cy, minute, r * 0.76f, accent);
        canvas.drawCircle(cx, cy, r * 0.07f, hand);
    }

    private static void drawHand(Canvas canvas, float cx, float cy, double turns, float length, Paint paint) {
        double a = Math.PI * 2 * turns;
        canvas.drawLine(cx, cy, cx + (float) Math.sin(a) * length, cy - (float) Math.cos(a) * length, paint);
    }
}
