package de.smartcomfort.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

/**
 * One silky ribbon of light across the whole screen: a few thin strands that follow the same calm wave,
 * slightly offset like fibres of silk. The wave crests drift slowly from left to right, and a soft glint
 * of brighter light glides along the ribbon from left to right. Both ends fade into the dark.
 * Each ribbon is one smooth Path per strand (no joints, no beads).
 */
final class LightTrail {
    private static final int POINTS = 64;
    private static final int STOPS = 24;
    private static final int STRANDS = 4;

    private final int color;
    private final float baseY, amp, waves, drift, breathe, glintPeriod, glintPhase, alpha, phase;
    private final Paint strand = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int[] colors = new int[STOPS];
    private final float[] stops = new float[STOPS];

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
        for (Paint p : new Paint[]{strand, halo}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
        for (int i = 0; i < STOPS; i++) stops[i] = i / (float) (STOPS - 1);
    }

    void draw(Canvas c, double t, int w, int h) {
        float density = w / 960f;
        // Glint position 0..1 (a little beyond the edges so it enters and leaves softly).
        double g = ((t / glintPeriod + glintPhase) % 1.0) * 1.4 - 0.2;
        for (int i = 0; i < STOPS; i++) {
            float x = stops[i];
            float edge = (float) Math.pow(Math.sin(Math.PI * x), 1.4);         // fades at both ends
            float glint = (float) Math.exp(-Math.pow((x - g) / 0.09, 2));       // soft bright band
            float a = Math.min(1f, edge * (alpha + 0.75f * glint));
            int r = Color.red(color), gg = Color.green(color), b = Color.blue(color);
            // The glint is a little whiter than the ribbon.
            r += (int) ((255 - r) * 0.5f * glint); gg += (int) ((255 - gg) * 0.5f * glint); b += (int) ((255 - b) * 0.5f * glint);
            colors[i] = Color.argb(Math.round(255 * a), r, gg, b);
        }
        Shader shader = new LinearGradient(0, 0, w, 0, colors, stops, Shader.TileMode.CLAMP);
        strand.setShader(shader);
        halo.setShader(shader);

        double crest = 2 * Math.PI * t / drift;                                  // crests move right
        float a = amp * (float) (0.8 + 0.2 * Math.sin(2 * Math.PI * t / breathe + phase));
        for (int s = 0; s < STRANDS; s++) {
            path.rewind();
            double off = s * 0.16;                                               // strands drift apart a bit
            for (int i = 0; i <= POINTS; i++) {
                float u = i / (float) POINTS;
                double y = baseY
                        + a * Math.sin(2 * Math.PI * waves * u - crest + phase + off)
                        + a * 0.35 * Math.sin(2 * Math.PI * waves * 0.47 * u - crest * 0.6 + phase * 1.7);
                float px = u * w, py = (float) (y * h) + s * 2.2f * density;
                if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
            }
            if (s == 0) {
                halo.setStrokeWidth(9 * density);
                halo.setAlpha(46);
                c.drawPath(path, halo);
            }
            strand.setStrokeWidth((s == 0 ? 1.6f : 0.9f) * density);
            strand.setAlpha(s == 0 ? 255 : 150 - s * 30);
            c.drawPath(path, strand);
        }
    }
}
