package de.smartcomfort.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.util.Random;

/**
 * One soft streak of light that glides from left to right along a calm wave. Now and then the path
 * makes a single slow loop in the middle. Each pass picks a new height, wave and colour, then rests a
 * few seconds before the next one. Drawn as short line pieces that fade and thin out toward the tail,
 * with a wide faint halo under a thin bright core.
 */
final class LightTrail {
    private static final int SEGMENTS = 70;
    private static final int[] COLORS = {
            Color.rgb(232, 206, 160),  // champagne
            Color.rgb(150, 180, 255),  // soft blue
            Color.rgb(245, 236, 220),  // warm white
    };

    private final Random rnd;
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Current pass.
    private double start;          // seconds (uptime) when the head enters on the left
    private double duration;       // seconds for the head to cross
    private float baseY, amp, freq, phase, length;
    private boolean loop;
    private float loopAt, loopR;
    private int color;

    LightTrail(long seed, double firstStart) {
        rnd = new Random(seed);
        for (Paint p : new Paint[]{halo, core}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
        }
        newPass(firstStart);
    }

    private void newPass(double at) {
        start = at;
        duration = 15 + rnd.nextDouble() * 7;           // 15–22 s across the screen
        baseY = 0.22f + rnd.nextFloat() * 0.6f;        // somewhere between top and bottom
        amp = 0.04f + rnd.nextFloat() * 0.07f;          // gentle wave
        freq = 1.0f + rnd.nextFloat() * 1.2f;           // 1–2 waves across
        phase = rnd.nextFloat() * 6.283f;
        length = 0.22f + rnd.nextFloat() * 0.12f;       // tail length (share of the path)
        loop = rnd.nextFloat() < 0.4f;                  // a loop in roughly 2 of 5 passes
        loopAt = 0.38f + rnd.nextFloat() * 0.24f;
        loopR = 0.09f + rnd.nextFloat() * 0.04f;      // loop radius (share of height)
        color = COLORS[rnd.nextInt(COLORS.length)];
    }

    /** Path position for u in [0,1], in pixels. Writes x,y into out. */
    private void point(float u, float w, float h, float[] out) {
        double x = (-0.25 + u * 1.5) * w;               // from off-screen left to off-screen right
        double y = (baseY + amp * Math.sin(2 * Math.PI * freq * u + phase)) * h;
        if (loop) {
            double d = 0.06;                            // half the share of the path the loop takes
            double v = (u - loopAt) / d;
            if (v > -1 && v < 1) {
                // Prolate cycloid: the forward motion over the window (~0.18 w) is smaller than the
                // circle, so the streak swings back once and closes a soft loop upward.
                double th = Math.PI * (v + 1);          // 0 → 2π
                double r = loopR * h;
                x -= r * Math.sin(th);
                y -= r * (1 - Math.cos(th));
            }
        }
        out[0] = (float) x;
        out[1] = (float) y;
    }

    void draw(Canvas c, double now, int w, int h) {
        double head = (now - start) / duration;
        if (head > 1 + length) {                        // finished: rest, then a new pass
            newPass(now + 2 + rnd.nextDouble() * 6);
            return;
        }
        if (head <= 0) return;
        float[] a = new float[2], b = new float[2];
        float density = w / 960f;                       // 1 at 1920 px / density 2
        for (int i = 0; i < SEGMENTS; i++) {
            float f0 = i / (float) SEGMENTS, f1 = (i + 1) / (float) SEGMENTS; // 0 = tail, 1 = head
            float u0 = (float) (head - length * (1 - f0)), u1 = (float) (head - length * (1 - f1));
            if (u1 < 0 || u0 > 1) continue;
            point(Math.max(0, u0), w, h, a);
            point(Math.min(1, u1), w, h, b);
            float k = f1 * f1;                          // fades toward the tail
            halo.setColor(color);
            halo.setAlpha(Math.round(30 * k));
            halo.setStrokeWidth((5 + 14 * k) * density);
            core.setColor(color);
            core.setAlpha(Math.round(120 * k));
            core.setStrokeWidth((0.8f + 2.2f * k) * density);
            c.drawLine(a[0], a[1], b[0], b[1], halo);
            c.drawLine(a[0], a[1], b[0], b[1], core);
        }
    }
}
