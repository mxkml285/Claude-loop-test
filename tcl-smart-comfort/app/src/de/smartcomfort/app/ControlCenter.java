package de.smartcomfort.app;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Premium Control Center: a dark glass panel on the right, drawn as an accessibility overlay ABOVE the
 * running app. It never takes focus, so IBO, TV and the PS5 keep playing underneath. While it is open,
 * HomeKeyService hands it the remote's arrow, OK and Back keys (up/down choose, left/right change,
 * OK toggles, Back closes). Closes by itself after 20 s without a key.
 *
 * Shows the TV's real current values. Manual changes are held (Controller.hold) until the next plan
 * event (07/17/18 Uhr, source change, wake). While the PS5 (any HDMI input) is shown, brightness and
 * Eye Care are locked: no picture changes in games.
 */
final class ControlCenter {
    static final String TAG = "SmartComfortCC";
    static final int[] SLEEP_MIN = {0, 15, 30, 60, 90};
    static final String SLEEP_DELAY = "tcl_app_power_off_time_delay";
    static final String SLEEP_EXPIRE = "tcl_app_power_off_time_expire";
    static final long IDLE_CLOSE_MS = 20_000;

    private final AccessibilityService c;
    private final WindowManager wm;
    private final Handler h = new Handler(Looper.getMainLooper());
    private FrameLayout panel;
    private TclDisplay display;
    private boolean gaming;
    private int backlight = -1;
    private boolean eyeCare;
    private Row rBright, rEye, rAuto, rSleep, rScreen;
    private Row[] rows;
    private int sel;
    private View bar, barFill;
    private TextView hint;

    ControlCenter(AccessibilityService c) {
        this.c = c;
        this.wm = c.getSystemService(WindowManager.class);
    }

    boolean isOpen() { return panel != null && panel.isAttachedToWindow(); }

    void open() {
        if (isOpen()) return;
        display = TclDisplay.connect();
        gaming = Ps5Watch.gaming(c);
        build();
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(dp(470), -2,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        p.x = dp(48);
        panel.setAlpha(0f);
        panel.setTranslationX(dp(30));
        try {
            wm.addView(panel, p);
        } catch (Exception e) {
            Log.w(TAG, "open", e);
            panel = null;
            return;
        }
        panel.animate().alpha(1f).translationX(0).setDuration(240).start();
        refresh();
        select(gaming ? 2 : 0);
        h.post(ticker);
        touch();
    }

    void close() {
        h.removeCallbacksAndMessages(null);
        final View v = panel;
        panel = null;
        if (v == null || !v.isAttachedToWindow()) return;
        v.animate().alpha(0f).translationX(dp(30)).setDuration(200).withEndAction(() -> {
            try { if (v.isAttachedToWindow()) wm.removeView(v); } catch (Exception ignored) { }
        }).start();
    }

    /** Keys while open. True = consumed. Other keys close the panel and keep their normal action. */
    boolean onKey(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down) select(sel - 1);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) select(sel + 1);
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) change(code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (down && e.getRepeatCount() == 0) ok();
                break;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (!down) close();
                break;
            default:
                close();
                return false;
        }
        touch();
        return true;
    }

    private void touch() {
        h.removeCallbacks(idle);
        h.postDelayed(idle, IDLE_CLOSE_MS);
    }

    private final Runnable idle = this::close;

    private final Runnable ticker = new Runnable() {
        @Override public void run() { if (isOpen()) { refreshSleep(); h.postDelayed(this, 1000); } }
    };

    private void select(int i) {
        sel = Math.max(0, Math.min(rows.length - 1, i));
        for (int k = 0; k < rows.length; k++) rows[k].paint(k == sel);
    }

    private void change(int dir) {
        Row r = rows[sel];
        if (r == rBright) nudgeBacklight(5 * dir);
        else if (r == rSleep) nudgeSleep(dir);
        else if (r == rEye) toggleEyeCare();
        else if (r == rAuto) toggleAuto();
    }

    private void ok() {
        Row r = rows[sel];
        if (r == rEye) toggleEyeCare();
        else if (r == rAuto) toggleAuto();
        else if (r == rSleep) nudgeSleep(1);
        else if (r == rScreen) screenOff();
    }

    private void refresh() {
        try {
            if (display != null) { backlight = display.getBacklight(); eyeCare = display.getLowBlueRay() != 0; }
        } catch (Exception e) { Log.w(TAG, "read", e); }
        hint.setText(gaming ? "GAMING · BILD WIRD NICHT VERÄNDERT" : "LIVE-WERTE DES FERNSEHERS");
        rBright.setLocked(gaming || display == null);
        rBright.setValue(backlight < 0 ? "–" : String.valueOf(backlight));
        rEye.setLocked(gaming || display == null);
        rEye.setValue(eyeCare ? "An" : "Aus");
        rAuto.setValue(Controller.enabled(c) ? "An" : "Aus");
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
        try {
            if (display.setBacklight(v)) { Controller.hold(c, v, eyeCare); Log.i(TAG, "Helligkeit manuell " + v); }
        } catch (Exception e) { Log.w(TAG, "set", e); }
        refresh();
    }

    private void toggleEyeCare() {
        if (gaming || display == null) return;
        try { if (display.setLowBlueRay(!eyeCare)) Controller.hold(c, backlight, !eyeCare); } catch (Exception e) { Log.w(TAG, "eye", e); }
        refresh();
    }

    private void toggleAuto() {
        boolean on = !Controller.enabled(c);
        Controller.setEnabled(c, on);
        if (on) Controller.tick(c, "enabled");
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
        long delay = Settings.Global.getLong(c.getContentResolver(), SLEEP_DELAY, -1);
        for (int i = 1; i < SLEEP_MIN.length; i++) if (delay == SLEEP_MIN[i] * 60_000L) return i;
        return delay > 0 ? -1 : 0;
    }

    private void nudgeSleep(int d) {
        int i = Math.max(0, sleepIndex());
        i = (i + d + SLEEP_MIN.length) % SLEEP_MIN.length;
        long ms = SLEEP_MIN[i] == 0 ? -1 : SLEEP_MIN[i] * 60_000L;
        try {
            Settings.Global.putLong(c.getContentResolver(), SLEEP_DELAY, ms);
            Controller.log(c, "Sleep-Timer " + (ms < 0 ? "aus" : SLEEP_MIN[i] + " Min"));
        } catch (SecurityException e) {
            rSleep.setValue("keine Berechtigung");
            Log.w(TAG, "sleep", e);
            return;
        }
        h.postDelayed(this::refreshSleep, 300);
        refreshSleep();
    }

    private void refreshSleep() {
        if (rSleep == null) return;
        long left = sleepRemainingMs(c);
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
            c.startService(new Intent("com.tcl.settings.SHOW_WINDOW")
                    .setComponent(new ComponentName("com.android.tv.settings", "com.tcl.settings.ShowWindowService"))
                    .putExtra("Type", "audio_only"));
            Controller.log(c, "Bildschirm aus (Nur Ton)");
            close();
        } catch (Exception e) {
            Log.w(TAG, "audio only", e);
            rScreen.setValue("nicht verfügbar");
        }
    }

    // ---- Views

    int dp(float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    private TextView text(float sp, int color, String family) {
        TextView t = new TextView(c);
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

    private void build() {
        panel = new FrameLayout(c);
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(236, 22, 22, 27), Color.argb(228, 12, 12, 15)});
        glass.setCornerRadius(dp(26));
        glass.setStroke(Math.max(1, dp(0.7f)), Color.argb(46, 216, 194, 157));
        panel.setBackground(glass);
        panel.setClipToOutline(true);
        panel.setOutlineProvider(new Tile.Rounded(dp(26)));
        panel.addView(new Ribbons(c), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(34), dp(26), dp(34), dp(24));
        TextView title = text(22, HomeActivity.IVORY, "sans-serif-light");
        title.setText("Control Center");
        title.setLetterSpacing(0.02f);
        col.addView(title);
        hint = text(12, HomeActivity.CHAMPAGNE, "sans-serif");
        hint.setLetterSpacing(0.12f);
        col.addView(hint, lp(-1, -2, 0, dp(4), 0, dp(14)));

        rBright = new Row(c, "Helligkeit");
        bar = new View(c);
        GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(dp(2));
        track.setColor(Color.argb(40, 255, 255, 255));
        bar.setBackground(track);
        barFill = new View(c);
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(176, 150, 110), Color.rgb(238, 222, 190)});
        fill.setCornerRadius(dp(2));
        barFill.setBackground(fill);
        FrameLayout barBox = new FrameLayout(c);
        barBox.addView(bar, new FrameLayout.LayoutParams(-1, dp(4), Gravity.CENTER_VERTICAL));
        barBox.addView(barFill, new FrameLayout.LayoutParams(0, dp(4), Gravity.CENTER_VERTICAL));
        rBright.addBelow(barBox);
        rEye = new Row(c, "Eye Care");
        rAuto = new Row(c, "Automatik (Zeitplan)");
        rSleep = new Row(c, "Sleep-Timer");
        rScreen = new Row(c, "Bildschirm aus, Ton läuft weiter");
        rows = new Row[]{rBright, rEye, rAuto, rSleep, rScreen};
        for (Row r : rows) col.addView(r, lp(-1, -2, 0, 0, 0, dp(6)));

        TextView foot = text(11, HomeActivity.IVORY_FAINT, "sans-serif-light");
        foot.setText("◂ ▸ ändern   ·   OK umschalten   ·   Zurück schließen");
        col.addView(foot, lp(-1, -2, 0, dp(10), 0, 0));
        panel.addView(col, new FrameLayout.LayoutParams(-1, -2));
    }

    /** One line: label left, value right, champagne glow when selected. */
    final class Row extends LinearLayout {
        final TextView label, value;
        final GradientDrawable bg = new GradientDrawable();
        boolean locked;

        Row(Context ctx, String name) {
            super(ctx);
            setOrientation(VERTICAL);
            setPadding(dp(18), dp(13), dp(18), dp(13));
            bg.setCornerRadius(dp(14));
            setBackground(bg);
            LinearLayout line = new LinearLayout(ctx);
            line.setGravity(Gravity.CENTER_VERTICAL);
            label = text(16, HomeActivity.IVORY, "sans-serif-light");
            label.setText(name);
            value = text(16, HomeActivity.CHAMPAGNE, "sans-serif");
            value.setGravity(Gravity.END);
            line.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            line.addView(value, new LinearLayout.LayoutParams(-2, -2));
            addView(line);
            paint(false);
        }

        void addBelow(View v) { addView(v, lp(-1, dp(14), 0, dp(10), 0, 0)); }

        void setValue(String s) { value.setText(locked ? "gesperrt" : s); }

        void setLocked(boolean l) {
            locked = l;
            setAlpha(l ? 0.45f : 1f);
        }

        void paint(boolean selected) {
            bg.setColor(selected ? Color.argb(34, 238, 222, 190) : Color.TRANSPARENT);
            bg.setStroke(Math.max(1, dp(1f)), selected ? Color.argb(150, 216, 194, 157) : Color.TRANSPARENT);
            animate().scaleX(selected ? 1.02f : 1f).scaleY(selected ? 1.02f : 1f).setDuration(160).start();
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

        Ribbons(Context ctx) { super(ctx); }

        @Override protected void onDraw(Canvas cv) {
            double t = SystemClock.uptimeMillis() / 1000.0;
            for (LightTrail tr : trails) tr.draw(cv, t, getWidth(), getHeight());
            if (isAttachedToWindow()) postDelayed(frame, 40);
        }
    }
}
