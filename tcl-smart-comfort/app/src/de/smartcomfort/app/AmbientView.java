package de.smartcomfort.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * Calm, slowly moving backdrop: a near-black base with three soft lights (champagne, deep blue, warm
 * amber) that drift on long, never-repeating-looking paths and breathe very gently. One full drift takes
 * about 40 seconds, so it reads as "alive" without ever pulling the eye. Redraws at most ~30 times a
 * second and only while the start page is visible.
 */
final class AmbientView extends View {
    private static final long FRAME_MS = 33;

    /** Light: color, radius (fraction of width), base position, drift amplitude, periods in seconds. */
    private static final class Light {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int color, alpha;
        final float radius, x, y, ax, ay, px, py, pa, phase;
        Light(int color, int alpha, float radius, float x, float y, float ax, float ay,
              float px, float py, float pa, float phase) {
            this.color = color; this.alpha = alpha; this.radius = radius; this.x = x; this.y = y;
            this.ax = ax; this.ay = ay; this.px = px; this.py = py; this.pa = pa; this.phase = phase;
        }
    }

    private final Light[] lights = {
            new Light(Color.rgb(216, 180, 130), 78, 0.62f, 0.15f, 0.10f, 0.22f, 0.18f, 41, 33, 19, 0.0f),
            new Light(Color.rgb(60, 90, 210), 80, 0.70f, 0.88f, 0.90f, 0.20f, 0.22f, 37, 47, 23, 1.7f),
            new Light(Color.rgb(200, 120, 80), 46, 0.50f, 0.50f, 0.55f, 0.34f, 0.26f, 53, 43, 29, 3.1f),
    };
    private final Paint base = new Paint();
    private boolean running;
    private long lastFrame;

    AmbientView(Context c) { super(c); }

    void start() { running = true; postInvalidateOnAnimation(); }

    void stop() { running = false; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        base.setShader(new LinearGradient(0, 0, 0, h, Color.rgb(13, 13, 15), Color.rgb(6, 6, 8), Shader.TileMode.CLAMP));
        for (Light l : lights) {
            float r = l.radius * w;
            // Centered at (0,0); moved with canvas.translate so the shader is built only once.
            l.paint.setShader(new RadialGradient(0, 0, r,
                    new int[]{l.color, withAlpha(l.color, 0.45f), withAlpha(l.color, 0f)},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        }
    }

    private static int withAlpha(int color, float f) {
        return Color.argb(Math.round(255 * f), Color.red(color), Color.green(color), Color.blue(color));
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawRect(0, 0, w, h, base);
        double t = SystemClock.uptimeMillis() / 1000.0;
        for (Light l : lights) {
            double tau = 2 * Math.PI;
            float cx = (float) (l.x + l.ax * Math.sin(tau * t / l.px + l.phase)) * w;
            float cy = (float) (l.y + l.ay * Math.sin(tau * t / l.py + l.phase * 1.3)) * h;
            float breathe = (float) (0.75 + 0.25 * Math.sin(tau * t / l.pa + l.phase));
            l.paint.setAlpha(Math.round(l.alpha * breathe));
            c.save();
            c.translate(cx, cy);
            c.drawCircle(0, 0, l.radius * w, l.paint);
            c.restore();
        }
        if (running) {
            long now = SystemClock.uptimeMillis();
            long wait = Math.max(0, FRAME_MS - (now - lastFrame));
            lastFrame = now + wait;
            postInvalidateDelayed(wait);
        }
    }
}
