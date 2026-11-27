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
import android.view.ViewGroup;

/**
 * Calm, slowly moving backdrop: a near-black base with three soft lights (champagne, deep blue, warm
 * amber) that drift on long paths and breathe very gently, and on top six silky LightTrail ribbons whose
 * crests and glints move from left to right. Redraws at most 25 times a second and only while the start
 * page is visible.
 *
 * GPU budget (the TV's Mali GPU is the limit, not the CPU): the big soft lights are drawn into a
 * hardware layer at a quarter of the screen size and scaled up, which costs a sixteenth of the fill and
 * blurs them, as they should be anyway. Only the thin ribbons are drawn at full resolution.
 */
final class AmbientView extends ViewGroup {
    private static final long FRAME_MS = 40;
    private static final int LIGHT_SCALE = 4;

    private final Lights lights;
    private final Ribbons ribbons;
    private boolean running;
    private long lastFrame;

    AmbientView(Context c) {
        super(c);
        lights = new Lights(c);
        lights.setLayerType(LAYER_TYPE_HARDWARE, null);
        lights.setPivotX(0);
        lights.setPivotY(0);
        lights.setScaleX(LIGHT_SCALE);
        lights.setScaleY(LIGHT_SCALE);
        ribbons = new Ribbons(c);
        addView(lights);
        addView(ribbons);
    }

    void start() {
        running = true;
        tick();
    }

    void stop() { running = false; }

    private final Runnable frame = this::tick;

    private void tick() {
        if (!running) return;
        lights.invalidate();
        ribbons.invalidate();
        long now = SystemClock.uptimeMillis();
        long wait = Math.max(1, FRAME_MS - (now - lastFrame));
        lastFrame = now + wait;
        removeCallbacks(frame);
        postDelayed(frame, wait);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, h = b - t;
        int lw = (w + LIGHT_SCALE - 1) / LIGHT_SCALE, lh = (h + LIGHT_SCALE - 1) / LIGHT_SCALE;
        lights.measure(MeasureSpec.makeMeasureSpec(lw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(lh, MeasureSpec.EXACTLY));
        lights.layout(0, 0, lw, lh);
        ribbons.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        ribbons.layout(0, 0, w, h);
    }

    private static int withAlpha(int color, float f) {
        return Color.argb(Math.round(255 * f), Color.red(color), Color.green(color), Color.blue(color));
    }

    /** Base gradient and three drifting soft lights. Drawn small, shown scaled up. */
    private static final class Lights extends View {
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

        Lights(Context c) { super(c); }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            base.setShader(new LinearGradient(0, 0, 0, h, Color.rgb(13, 13, 15), Color.rgb(6, 6, 8), Shader.TileMode.CLAMP));
            for (Light l : lights) {
                // Centered at (0,0); moved with canvas.translate so the shader is built only once.
                l.paint.setShader(new RadialGradient(0, 0, l.radius * w,
                        new int[]{l.color, withAlpha(l.color, 0.45f), withAlpha(l.color, 0f)},
                        new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            c.drawRect(0, 0, w, h, base);
            double t = SystemClock.uptimeMillis() / 1000.0;
            double tau = 2 * Math.PI;
            for (Light l : lights) {
                float cx = (float) (l.x + l.ax * Math.sin(tau * t / l.px + l.phase)) * w;
                float cy = (float) (l.y + l.ay * Math.sin(tau * t / l.py + l.phase * 1.3)) * h;
                float breathe = (float) (0.75 + 0.25 * Math.sin(tau * t / l.pa + l.phase));
                l.paint.setAlpha(Math.round(l.alpha * breathe));
                c.save();
                c.translate(cx, cy);
                c.drawCircle(0, 0, l.radius * w, l.paint);
                c.restore();
            }
        }
    }

    /** Six silky ribbons of light, spread over the height, each with its own wave, pace and glint. */
    private static final class Ribbons extends View {
        private final LightTrail[] trails = {
                //            colour                          alpha  y      amp    waves drift  glint phase
                new LightTrail(Color.rgb(232, 206, 160), 0.30f, 0.20f, 0.07f, 1.3f, 34f, 13f, 0.00f),
                new LightTrail(Color.rgb(150, 180, 255), 0.26f, 0.34f, 0.09f, 1.0f, 41f, 17f, 0.35f),
                new LightTrail(Color.rgb(245, 236, 220), 0.22f, 0.48f, 0.06f, 1.6f, 29f, 11f, 0.62f),
                new LightTrail(Color.rgb(222, 170, 140), 0.26f, 0.60f, 0.10f, 0.9f, 47f, 19f, 0.18f),
                new LightTrail(Color.rgb(120, 150, 240), 0.24f, 0.74f, 0.08f, 1.2f, 37f, 15f, 0.80f),
                new LightTrail(Color.rgb(232, 206, 160), 0.22f, 0.88f, 0.06f, 1.5f, 31f, 12f, 0.47f),
        };

        Ribbons(Context c) { super(c); }

        @Override
        protected void onDraw(Canvas c) {
            double t = SystemClock.uptimeMillis() / 1000.0;
            for (LightTrail tr : trails) tr.draw(c, t, getWidth(), getHeight());
        }
    }
}
