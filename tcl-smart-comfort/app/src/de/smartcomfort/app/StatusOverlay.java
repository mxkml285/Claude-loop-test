package de.smartcomfort.app;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Small glass pill at the top of the screen ("Eye Care aktiviert"), shown above any app through the
 * accessibility service's own overlay window (no extra permission). Never takes focus or keys.
 */
final class StatusOverlay {
    private final AccessibilityService s;
    private final WindowManager wm;
    private final Handler h = new Handler(Looper.getMainLooper());
    private LinearLayout view;
    private TextView title, sub;
    private View dot;

    StatusOverlay(AccessibilityService s) {
        this.s = s;
        this.wm = s.getSystemService(WindowManager.class);
    }

    private int dp(float v) { return Math.round(v * s.getResources().getDisplayMetrics().density); }

    void show(String text, String detail, boolean on) {
        try {
            if (view == null) create();
            title.setText(text);
            sub.setText(detail);
            sub.setVisibility(detail == null ? View.GONE : View.VISIBLE);
            ((GradientDrawable) dot.getBackground()).setColor(on ? HomeActivity.CHAMPAGNE : Color.argb(120, 238, 233, 224));
            if (!view.isAttachedToWindow()) {
                WindowManager.LayoutParams p = new WindowManager.LayoutParams(-2, -2,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                p.y = dp(40);
                view.setAlpha(0f);
                view.setTranslationY(-dp(10));
                wm.addView(view, p);
            }
            view.animate().alpha(1f).translationY(0).setDuration(220).start();
            h.removeCallbacksAndMessages(null);
            h.postDelayed(this::hide, 2200);
        } catch (Exception e) {
            android.util.Log.w("SmartComfortHome", "overlay", e);
        }
    }

    private void hide() {
        if (view == null || !view.isAttachedToWindow()) return;
        view.animate().alpha(0f).translationY(-dp(10)).setDuration(320).withEndAction(() -> {
            try { if (view.isAttachedToWindow()) wm.removeView(view); } catch (Exception ignored) { }
        }).start();
    }

    private void create() {
        view = new LinearLayout(s);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(22), dp(13), dp(26), dp(13));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(235, 24, 24, 29), Color.argb(228, 12, 12, 15)});
        bg.setCornerRadius(dp(30));
        bg.setStroke(Math.max(1, dp(0.8f)), Color.argb(90, 216, 194, 157));
        view.setBackground(bg);
        view.setElevation(dp(12));
        dot = new View(s);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        dot.setBackground(d);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(8), dp(8));
        dl.setMarginEnd(dp(14));
        view.addView(dot, dl);
        LinearLayout col = new LinearLayout(s);
        col.setOrientation(LinearLayout.VERTICAL);
        title = new TextView(s);
        title.setTextSize(17);
        title.setTextColor(HomeActivity.IVORY);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        sub = new TextView(s);
        sub.setTextSize(11);
        sub.setTextColor(HomeActivity.CHAMPAGNE);
        sub.setLetterSpacing(0.1f);
        col.addView(title);
        col.addView(sub);
        view.addView(col);
    }
}
