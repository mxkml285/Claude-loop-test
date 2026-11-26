package de.smartcomfort.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * A remote-friendly tile: rounded, clipped, and on focus it grows slightly, lifts and gets a soft
 * light rim. 180 ms, decelerating, no bounce.
 */
final class Tile extends FrameLayout {
    /** Champagne focus rim. */
    static final int RIM = Color.rgb(216, 194, 157);
    private final GradientDrawable rim = new GradientDrawable();
    private final float radius;
    private final float scaleFocused;
    private ValueAnimator rimAnim;

    Tile(Context c, float radiusPx, float scaleFocused) {
        super(c);
        this.radius = radiusPx;
        this.scaleFocused = scaleFocused;
        setFocusable(true);
        setClickable(true);
        setClipToOutline(true);
        setOutlineProvider(new RoundedOutline());
        rim.setCornerRadius(radiusPx);
        rim.setColor(Color.TRANSPARENT);
        rim.setStroke(Math.max(2, dp(1.5f)), RIM);
        rim.setAlpha(0);
        setForeground(rim);
        setOnFocusChangeListener((v, has) -> animateFocus(has));
    }

    private void animateFocus(boolean has) {
        animate().scaleX(has ? scaleFocused : 1f).scaleY(has ? scaleFocused : 1f)
                .translationZ(has ? dp(18) : 0).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
        if (rimAnim != null) rimAnim.cancel();
        rimAnim = ValueAnimator.ofInt(rim.getAlpha(), has ? 235 : 0);
        rimAnim.setDuration(220);
        rimAnim.addUpdateListener(a -> rim.setAlpha((Integer) a.getAnimatedValue()));
        rimAnim.start();
    }

    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    final class RoundedOutline extends ViewOutlineProvider {
        @Override public void getOutline(View v, Outline o) {
            o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
        }
    }

    /** Rounded clip for any view, e.g. an app icon inside a tile. */
    static final class Rounded extends ViewOutlineProvider {
        private final float r;
        Rounded(float r) { this.r = r; }
        @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
    }
}
