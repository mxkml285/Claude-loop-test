package de.smartcomfort.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Premium Control Center: a dark glass panel over whatever is on screen, operated with the remote
 * (up/down choose, left/right change, OK toggles, Back closes). Shows the TV's real current values.
 *
 * Manual changes stay until the next plan event (07/17/18 Uhr, source change, wake): the Controller
 * only applies the plan on events, so nothing here is overwritten in between.
 * While the PS5 (any HDMI input) is shown, brightness and Eye Care are locked: no picture changes in games.
 */
public class ControlCenterActivity extends Activity {
    static final String TAG = "SmartComfortCC";
    static final int[] SLEEP_MIN = {0, 15, 30, 60, 90};
    static final String SLEEP_DELAY = "tcl_app_power_off_time_delay";
    static final String SLEEP_EXPIRE = "tcl_app_power_off_time_expire";

    private final Handler h = new Handler(Looper.getMainLooper());
    private TclDisplay display;
    private boolean gaming;
    private int backlight = -1;
    private boolean eyeCare;
    private Row rBright, rEye, rAuto, rSleep, rScreen;
    private View bar, barFill;
    private TextView hint;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        display = TclDisplay.connect();
        gaming = Ps5Watch.gaming(this);

        FrameLayout root = new FrameLayout(this);
        root.setOnClickListener(v -> finish());

        FrameLayout panel = new FrameLayout(this);
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(236, 22, 22, 27), Color.argb(228, 12, 12, 15)});
        glass.setCornerRadius(dp(26));
        glass.setStroke(Math.max(1, dp(0.7f)), Color.argb(46, 216, 194, 157));
        panel.setBackground(glass);
        panel.setClipToOutline(true);
        panel.setOutlineProvider(new Tile.Rounded(dp(26)));
        panel.setElevation(dp(24));
        panel.addView(new Ribbons(this), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(34), dp(26), dp(34), dp(24));
        TextView title = text(22, HomeActivity.IVORY, "sans-serif-light");
        title.setText("Control Center");
        title.setLetterSpacing(0.02f);
        col.addView(title);
        hint = text(12, HomeActivity.CHAMPAGNE, "sans-serif");
        hint.setLetterSpacing(0.12f);
        col.addView(hint, lp(-1, -2, 0, dp(4), 0, dp(14)));

        rBright = new Row(this, "Helligkeit");
        bar = new View(this);
        GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(dp(2));
        track.setColor(Color.argb(40, 255, 255, 255));
        bar.setBackground(track);
        barFill = new View(this);
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(176, 150, 110), Color.rgb(238, 222, 190)});
        fill.setCornerRadius(dp(2));
        barFill.setBackground(fill);
        FrameLayout barBox = new FrameLayout(this);
        barBox.addView(bar, new FrameLayout.LayoutParams(-1, dp(4), Gravity.CENTER_VERTICAL));
        barBox.addView(barFill, new FrameLayout.LayoutParams(0, dp(4), Gravity.CENTER_VERTICAL));
        rBright.addBelow(barBox);
        rEye = new Row(this, "Eye Care");
        rAuto = new Row(this, "Automatik (Zeitplan)");
        rSleep = new Row(this, "Sleep-Timer");
        rScreen = new Row(this, "Bildschirm aus, Ton läuft weiter");
        for (Row r : new Row[]{rBright, rEye, rAuto, rSleep, rScreen}) col.addView(r, lp(-1, -2, 0, 0, 0, dp(6)));

        TextView foot = text(11, HomeActivity.IVORY_FAINT, "sans-serif-light");
        foot.setText("◂ ▸ ändern   ·   OK umschalten   ·   Zurück schließen");
        col.addView(foot, lp(-1, -2, 0, dp(10), 0, 0));
        panel.addView(col, new FrameLayout.LayoutParams(-1, -2));

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(470), -2, Gravity.END | Gravity.CENTER_VERTICAL);
        plp.setMarginEnd(dp(48));
        panel.setClickable(true);
        root.addView(panel, plp);
        setContentView(root);

        rBright.setOnKeyListener((v, code, e) -> e.getAction() == KeyEvent.ACTION_DOWN && step(code, 5, this::nudgeBacklight));
        rEye.setOnClickListener(v -> toggleEyeCare());
        rAuto.setOnClickListener(v -> { Controller.setEnabled(this, !Controller.enabled(this)); refresh(); if (Controller.enabled(this)) Controller.tick(this, "enabled"); refresh(); });
        rSleep.setOnClickListener(v -> nudgeSleep(1));
        rSleep.setOnKeyListener((v, code, e) -> e.getAction() == KeyEvent.ACTION_DOWN && step(code, 1, this::nudgeSleep));
        rScreen.setOnClickListener(v -> screenOff());

        refresh();
        (gaming ? rAuto : rBright).requestFocus();
    }

    interface Nudge { void by(int d); }

    private boolean step(int code, int amount, Nudge n) {
        if (code == KeyEvent.KEYCODE_DPAD_LEFT) { n.by(-amount); return true; }
        if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { n.by(amount); return true; }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        h.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(ticker);
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() { refreshSleep(); h.postDelayed(this, 1000); }
    };

    private void refresh() {
        try {
            if (display != null) { backlight = display.getBacklight(); eyeCare = display.getLowBlueRay() != 0; }
        } catch (Exception e) { Log.w(TAG, "read", e); }
        hint.setText(gaming ? "GAMING · BILD WIRD NICHT VERÄNDERT" : "LIVE-WERTE DES FERNSEHERS");
        rBright.setValue(backlight < 0 ? "–" : String.valueOf(backlight));
        rBright.setLocked(gaming || display == null);
        rEye.setValue(eyeCare ? "An" : "Aus");
        rEye.setLocked(gaming || display == null);
        rAuto.setValue(Controller.enabled(this) ? "An" : "Aus");
        rScreen.setValue("OK");
        bar.post(() -> {
            FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) barFill.getLayoutParams();
            l.width = Math.round(bar.getWidth() * Math.max(0, backlight) / 100f);
            barFill.setLayoutParams(l);
        });
        refreshSleep();
    }

    private void nudgeBacklight(int d) {
        if (gaming || display == null || backlight < 0) return;
        int v = Math.max(0, Math.min(100, backlight + d));
        try { if (display.setBacklight(v)) Log.i(TAG, "Helligkeit manuell " + v); } catch (Exception e) { Log.w(TAG, "set", e); }
        refresh();
    }

    private void toggleEyeCare() {
        if (gaming || display == null) return;
        try { display.setLowBlueRay(!eyeCare); } catch (Exception e) { Log.w(TAG, "eye", e); }
        refresh();
    }

    // ---- Sleep timer: TCL's own power-off timer (same as Einstellungen → Sleep-Timer).

    static long sleepRemainingMs(Context c) {
        long exp = Settings.Global.getLong(c.getContentResolver(), SLEEP_EXPIRE, 0);
        long delay = Settings.Global.getLong(c.getContentResolver(), SLEEP_DELAY, -1);
        if (delay <= 0 || exp <= 0) return 0;
        return Math.max(0, exp - SystemClock.elapsedRealtime());
    }

    private int sleepIndex() {
        long delay = Settings.Global.getLong(getContentResolver(), SLEEP_DELAY, -1);
        for (int i = 1; i < SLEEP_MIN.length; i++) if (delay == SLEEP_MIN[i] * 60_000L) return i;
        return delay > 0 ? -1 : 0;
    }

    private void nudgeSleep(int d) {
        int i = sleepIndex();
        if (i < 0) i = 0;
        i = (i + d + SLEEP_MIN.length) % SLEEP_MIN.length;
        long ms = SLEEP_MIN[i] == 0 ? -1 : SLEEP_MIN[i] * 60_000L;
        try {
            Settings.Global.putLong(getContentResolver(), SLEEP_DELAY, ms);
            Controller.log(this, "Sleep-Timer " + (ms < 0 ? "aus" : SLEEP_MIN[i] + " Min"));
        } catch (SecurityException e) {
            rSleep.setValue("keine Berechtigung");
            Log.w(TAG, "sleep", e);
            return;
        }
        h.postDelayed(this::refreshSleep, 300);
        refreshSleep();
    }

    private void refreshSleep() {
        long left = sleepRemainingMs(this);
        int i = sleepIndex();
        if (i == 0) rSleep.setValue("Aus");
        else if (left > 0) {
            long s = (left + 999) / 1000;
            rSleep.setValue((i > 0 ? SLEEP_MIN[i] + " Min · " : "") + "noch " + (s / 60) + ":" + String.format("%02d", s % 60));
        } else rSleep.setValue(i > 0 ? SLEEP_MIN[i] + " Min" : "An");
    }

    // ---- Screen off, sound on: TCL's own "Nur Ton" (audio only) flow from its quick settings.

    private void screenOff() {
        try {
            startService(new Intent("com.tcl.settings.SHOW_WINDOW")
                    .setComponent(new ComponentName("com.android.tv.settings", "com.tcl.settings.ShowWindowService"))
                    .putExtra("Type", "audio_only"));
            Controller.log(this, "Bildschirm aus (Nur Ton)");
            finish();
        } catch (Exception e) {
            Log.w(TAG, "audio only", e);
            rScreen.setValue("nicht verfügbar");
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    // ---- Views

    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView text(float sp, int color, String family) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    private static LinearLayout.LayoutParams lp(int w, int hgt, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, hgt);
        p.setMargins(l, t, r, b);
        return p;
    }

    /** One focusable line: label left, value right, champagne glow on focus. */
    final class Row extends LinearLayout {
        final TextView label, value;
        final GradientDrawable bg = new GradientDrawable();
        boolean locked;

        Row(Context c, String name) {
            super(c);
            setOrientation(VERTICAL);
            setFocusable(true);
            setClickable(true);
            setPadding(dp(18), dp(13), dp(18), dp(13));
            bg.setCornerRadius(dp(14));
            setBackground(bg);
            LinearLayout line = new LinearLayout(c);
            line.setGravity(Gravity.CENTER_VERTICAL);
            label = text(16, HomeActivity.IVORY, "sans-serif-light");
            label.setText(name);
            value = text(16, HomeActivity.CHAMPAGNE, "sans-serif");
            value.setGravity(Gravity.END);
            line.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            line.addView(value, new LinearLayout.LayoutParams(-2, -2));
            addView(line);
            setOnFocusChangeListener((v, has) -> paint(has));
            paint(false);
        }

        void addBelow(View v) { addView(v, lp(-1, dp(14), 0, dp(10), 0, 0)); }

        void setValue(String s) { value.setText(locked ? "gesperrt" : s); }

        void setLocked(boolean l) {
            locked = l;
            setAlpha(l ? 0.45f : 1f);
            if (l) value.setText("gesperrt");
        }

        private void paint(boolean focus) {
            bg.setColor(focus ? Color.argb(34, 238, 222, 190) : Color.TRANSPARENT);
            bg.setStroke(Math.max(1, dp(1f)), focus ? Color.argb(150, 216, 194, 157) : Color.TRANSPARENT);
            animate().scaleX(focus ? 1.02f : 1f).scaleY(focus ? 1.02f : 1f).setDuration(160).start();
        }
    }

    /** Three slow silk ribbons behind the panel content (same LightTrail as the start page). */
    static final class Ribbons extends View {
        private final LightTrail[] trails = {
                new LightTrail(Color.rgb(232, 206, 160), 0.30f, 0.22f, 0.06f, 1.1f, 36f, 14f, 0.10f),
                new LightTrail(Color.rgb(150, 180, 255), 0.22f, 0.55f, 0.08f, 0.9f, 43f, 18f, 0.55f),
                new LightTrail(Color.rgb(222, 170, 140), 0.24f, 0.84f, 0.05f, 1.3f, 31f, 12f, 0.30f),
        };
        private final Runnable frame = this::invalidate;

        Ribbons(Context c) { super(c); }

        @Override protected void onDraw(Canvas c) {
            double t = SystemClock.uptimeMillis() / 1000.0;
            for (LightTrail tr : trails) tr.draw(c, t, getWidth(), getHeight());
            if (isAttachedToWindow()) postDelayed(frame, 40);
        }
    }
}
