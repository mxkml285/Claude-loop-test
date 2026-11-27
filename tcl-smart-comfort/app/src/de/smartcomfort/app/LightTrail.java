package de.smartcomfort.app;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Shader;

/**
 * One silky ribbon of light across the whole screen: a few thin strands that follow the same calm wave,
 * slightly offset like fibres of silk. The wave crests drift slowly from left to right, the ribbon
 * breathes a little, and a soft glint glides along it from left to right. Both ends fade into the dark.
 *
 * Cheap on the TV GPU: the wave moves as a whole (both harmonics travel at the same speed), so the
 * ribbon is drawn ONCE into a seamless image strip (half resolution) and every frame only shifts and
 * stretches that strip through a shader matrix. Per frame: two rectangles, no geometry.
 */
final class LightTrail {
    private static final int STRANDS = 3;
    private static final float RES = 0.5f;                 // strip resolution vs. screen

    private final int color;
    private final float baseY, amp, waves, drift, breathe, glintPeriod, glintPhase, alpha, phase;

    private final Paint ribbon = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint glint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix stripMatrix = new Matrix();
    private final Matrix glintMatrix = new Matrix();
    private BitmapShader strip;
    private LinearGradient glintShader;
    private Bitmap bitmap;
    private float periodPx, stripH;                        // in screen pixels
    private int sizedW = -1, sizedH = -1;

    /**
     * @param baseY  resting height (share of screen height)
     * @param amp    wave height (share of screen height)
     * @param waves  number of waves across the screen
     * @param drift  seconds for one crest to move one wave length to the right
     * @param glint  seconds for the glint to cross the screen
     */
    LightTrail(int color, float alpha, float baseY, float amp, float waves, float drift, float glint, float phase) {
        this.color = color;
        this.alpha = alpha;
        this.baseY = baseY;
        this.amp = amp;
        this.waves = waves;
        this.drift = drift;
        this.breathe = drift * 1.7f;
        this.glintPeriod = glint;
        this.glintPhase = phase;
        this.phase = phase * 2.1f;
    }

    /** Wave height at screen position u (share of width), relative to the ribbon's middle, in pixels. */
    private double wave(double u, int h) {
        return amp * h * (Math.sin(2 * Math.PI * waves * u + phase)
                + 0.35 * Math.sin(Math.PI * waves * u + phase * 1.7));
    }

    private void size(int w, int h) {
        sizedW = w;
        sizedH = h;
        if (bitmap != null) bitmap.recycle();
        float density = w / 960f;
        periodPx = 2f / waves * w;                          // the two harmonics repeat after 2 waves
        stripH = 2 * 1.35f * amp * h + 40 * density;
        int bw = Math.max(1, Math.round(periodPx * RES)), bh = Math.max(1, Math.round(stripH * RES));
        bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        c.scale(bw / periodPx, bh / stripH);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        Path path = new Path();
        float mid = stripH / 2;
        for (int s = 0; s < STRANDS; s++) {
            path.rewind();
            double off = s * 0.16 / (2 * Math.PI * waves);  // strands drift apart a bit
            float dy = s * 2.2f * density;
            // Draw a little beyond both ends so the strip tiles without a seam.
            for (int i = -4; i <= 132; i++) {
                double x = i / 128.0 * periodPx;
                float y = (float) (mid + dy + wave(x / w + off, h));
                if (i == -4) path.moveTo((float) x, y); else path.lineTo((float) x, y);
            }
            if (s == 0) {
                p.setStrokeWidth(9 * density);
                p.setAlpha(46);
                c.drawPath(path, p);
            }
            p.setStrokeWidth((s == 0 ? 1.6f : 0.9f) * density);
            p.setAlpha(s == 0 ? 255 : 160 - s * 40);
            c.drawPath(path, p);
        }
        strip = new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP);

        // Ribbon: the strip, faded toward both screen edges.
        int n = 13;
        int[] cols = new int[n];
        float[] pos = new float[n];
        for (int i = 0; i < n; i++) {
            float x = i / (float) (n - 1);
            cols[i] = Color.argb(Math.round(255 * alpha * (float) Math.pow(Math.sin(Math.PI * x), 1.4)), 255, 255, 255);
            pos[i] = x;
        }
        LinearGradient edge = new LinearGradient(0, 0, w, 0, cols, pos, Shader.TileMode.CLAMP);
        ribbon.setShader(new ComposeShader(strip, edge, PorterDuff.Mode.DST_IN));

        // Glint: a soft, whiter band (moved with its matrix) shown only where the strands are.
        int white = Color.rgb((Color.red(color) + 255) / 2, (Color.green(color) + 255) / 2, (Color.blue(color) + 255) / 2);
        float half = 0.16f * w;
        glintShader = new LinearGradient(-half, 0, half, 0,
                new int[]{withAlpha(white, 0), withAlpha(white, 0.35f), withAlpha(white, 0.85f), withAlpha(white, 0.35f), withAlpha(white, 0)},
                new float[]{0f, 0.3f, 0.5f, 0.7f, 1f}, Shader.TileMode.CLAMP);
        glint.setShader(new ComposeShader(strip, glintShader, PorterDuff.Mode.SRC_IN));
    }

    private static int withAlpha(int c, float a) {
        return Color.argb(Math.round(255 * Math.max(0, Math.min(1, a))), Color.red(c), Color.green(c), Color.blue(c));
    }

    void draw(Canvas c, double t, int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (w != sizedW || h != sizedH) size(w, h);

        // Crests move right: shift the strip by drift progress (one wave length per `drift` seconds).
        double shift = (t / drift) * (periodPx / 2) % periodPx;
        float b = (float) (0.8 + 0.2 * Math.sin(2 * Math.PI * t / breathe + phase));   // breathing
        float top = baseY * h - stripH / 2 * b;
        stripMatrix.setScale(periodPx / bitmap.getWidth(), stripH * b / bitmap.getHeight());
        stripMatrix.postTranslate((float) shift, top);
        strip.setLocalMatrix(stripMatrix);

        double g = ((t / glintPeriod + glintPhase) % 1.0) * 1.4 - 0.2;
        glintMatrix.setTranslate((float) (g * w), 0);
        glintShader.setLocalMatrix(glintMatrix);
        float edgeAtGlint = (float) Math.pow(Math.max(0, Math.sin(Math.PI * Math.max(0, Math.min(1, g)))), 1.4);

        float bottom = top + stripH * b;
        c.drawRect(0, top, w, bottom, ribbon);
        if (edgeAtGlint > 0.01f) {                       // only the glint's own band, not the whole width
            glint.setAlpha(Math.round(255 * edgeAtGlint));
            float gx = (float) (g * w), half = 0.16f * w;
            c.drawRect(Math.max(0, gx - half), top, Math.min(w, gx + half), bottom, glint);
        }
    }
}
