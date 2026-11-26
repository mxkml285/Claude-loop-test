package de.smartcomfort.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Premium start page: two large tiles (MediaPlayer IBO, PlayStation 5), four small ones (YouTube,
 * Netflix, Smart Comfort, TCL Channel), clock and settings top right, Smart Comfort status line.
 * No ads, no recommendations, no network. Opened by the Home key via HomeKeyService.
 */
public class HomeActivity extends Activity {
    static final String IBO = "com.ibostore.meplayerib4k";
    static final String YOUTUBE = "com.google.android.youtube.tv";
    static final String NETFLIX = "com.netflix.ninja";
    static final String TCL_CHANNEL = "com.tcl.waterfall.overseas";
    static final String TCL_TV = "com.tcl.tv";
    static final ComponentName TCL_TV_ACTIVITY = new ComponentName(TCL_TV, "com.tcl.player.TVActivity");
    /** HDMI 2, labelled "Playstation5" on this TV (see docs/STARTSEITE-ANALYSE.md). */
    static final String PS5_INPUT = "com.tcl.tvinput/.TvPassThroughService/HW16";
    static final ComponentName GOOGLE_HOME =
            new ComponentName("com.google.android.apps.tv.launcherx", "com.google.android.apps.tv.launcherx.home.HomeActivity");

    static final int TEXT = Color.rgb(236, 233, 226);
    static final int TEXT_DIM = Color.argb(150, 236, 233, 226);
    static final int TEXT_FAINT = Color.argb(95, 236, 233, 226);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView clock, date, status;
    private View statusDot;
    private Tile ibo;

    private final Runnable tickClock = new Runnable() {
        @Override public void run() {
            updateClockAndStatus();
            long ms = System.currentTimeMillis();
            handler.postDelayed(this, 60_000 - (ms % 60_000) + 50);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(backdrop());
        root.setClipChildren(false);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(dp(56), dp(34), dp(56), dp(24));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        col.addView(topBar(), new LinearLayout.LayoutParams(-1, dp(48)));
        col.addView(statusLine(), lp(-1, -2, 0, dp(6), 0, 0));

        // Main row: the two large tiles.
        LinearLayout big = row();
        ibo = bigTile("MediaPlayer IBO", "FERNSEHEN · FILME · SERIEN", appIcon(IBO), null,
                Color.rgb(214, 160, 92), v -> launch(IBO));
        Tile ps5 = bigTile("PlayStation 5", "HDMI 2", null, getDrawable(R.drawable.ic_controller),
                Color.rgb(92, 140, 230), v -> openPs5());
        big.addView(ibo, weightLp(dp(222), 0));
        big.addView(ps5, weightLp(dp(222), dp(28)));
        col.addView(big, lp(-1, -2, 0, dp(40), 0, 0));

        // Second row: four smaller tiles with the apps' own TV banners.
        LinearLayout small = row();
        small.addView(smallTile(YOUTUBE, "YouTube", v -> launch(YOUTUBE)), weightLp(dp(108), 0));
        small.addView(smallTile(NETFLIX, "Netflix", v -> launch(NETFLIX)), weightLp(dp(108), dp(22)));
        small.addView(smallTile(getPackageName(), "Smart Comfort",
                v -> startActivity(new Intent(this, MainActivity.class))), weightLp(dp(108), dp(22)));
        small.addView(smallTile(TCL_CHANNEL, "TCL Channel", v -> launch(TCL_CHANNEL)), weightLp(dp(108), dp(22)));
        col.addView(small, lp(-1, -2, 0, dp(34), 0, 0));

        setContentView(root);
        ibo.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(tickClock);
        Controller.tick(this, "Startseite"); // fresh status; applies only on a real event (see Controller)
        tickClock.run();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tickClock);
    }

    @Override
    public void onBackPressed() {
        // This is the start page: Back stays here.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_BACK) return true;
        return super.onKeyDown(keyCode, e);
    }

    // ---------------------------------------------------------------- top bar

    private View topBar() {
        FrameLayout bar = new FrameLayout(this);
        bar.setClipChildren(false);

        date = text(15, TEXT_DIM, false);
        date.setLetterSpacing(0.14f);
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.CENTER_VERTICAL);
        bar.addView(date, dl);

        LinearLayout right = new LinearLayout(this);
        right.setGravity(Gravity.CENTER_VERTICAL);
        right.setClipChildren(false);
        clock = text(30, TEXT, true);
        right.addView(clock, lp(-2, -2, 0, 0, dp(20), 0));
        right.addView(iconButton(R.drawable.ic_apps, "Google TV", v -> openGoogleTv()), new LinearLayout.LayoutParams(dp(44), dp(44)));
        right.addView(iconButton(R.drawable.ic_settings, "Einstellungen", v -> openSettings()), lp(dp(44), dp(44), dp(10), 0, 0, 0));
        bar.addView(right, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL));
        return bar;
    }

    private View statusLine() {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(this);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(Color.rgb(120, 200, 150));
        statusDot.setBackground(dot);
        l.addView(statusDot, lp(dp(7), dp(7), 0, 0, dp(10), 0));
        status = text(14, TEXT_FAINT, false);
        status.setLetterSpacing(0.04f);
        l.addView(status);
        return l;
    }

    private Tile iconButton(int icon, String label, View.OnClickListener click) {
        Tile t = new Tile(this, dp(22), 1.12f);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(22, 255, 255, 255));
        t.setBackground(bg);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(TEXT_DIM);
        t.addView(iv, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        t.setContentDescription(label);
        t.setOnClickListener(click);
        return t;
    }

    private void updateClockAndStatus() {
        ZonedDateTime now = ZonedDateTime.now(Schedule.ZONE);
        clock.setText(now.format(DateTimeFormatter.ofPattern("HH:mm")));
        date.setText(now.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMANY)).toUpperCase(Locale.GERMANY));
        String s = Controller.prefs(this).getString("status", "");
        boolean active = s.startsWith("Aktiv");
        String next = Schedule.nextBoundary(now).format(DateTimeFormatter.ofPattern("HH:mm"));
        String shown;
        if (active) {
            int bl = Schedule.backlight(now.toLocalTime());
            boolean ec = Schedule.eyeCare(now.toLocalTime());
            shown = "Smart Comfort  ·  Helligkeit " + bl + "  ·  Eye Care " + (ec ? "an" : "aus") + "  ·  nächste Änderung " + next;
        } else {
            shown = "Smart Comfort  ·  " + (s.isEmpty() ? "noch kein Status" : s);
        }
        status.setText(shown);
        ((GradientDrawable) statusDot.getBackground()).setColor(active ? Color.rgb(120, 200, 150) : Color.rgb(200, 160, 90));
    }

    // ---------------------------------------------------------------- tiles

    private Tile bigTile(String title, String subtitle, Drawable appIcon, Drawable glyph, int accent,
                         View.OnClickListener click) {
        Tile t = new Tile(this, dp(26), 1.045f);
        // Dark card with a faint accent glow in the upper left, like light falling on glass.
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(28, 31, 37), Color.rgb(17, 19, 23)});
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(0.18f, 0.2f);
        glow.setGradientRadius(dp(300));
        glow.setColors(new int[]{withAlpha(accent, 70), withAlpha(accent, 0)});
        t.setBackground(new LayerDrawable(new Drawable[]{base, glow}));

        LinearLayout content = new LinearLayout(this);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(dp(32), 0, dp(20), 0);

        if (appIcon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(appIcon);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setOutlineProvider(new Tile.Rounded(dp(20)));
            iv.setClipToOutline(true);
            content.addView(iv, lp(dp(84), dp(84), 0, 0, dp(24), 0));
        } else if (glyph != null) {
            FrameLayout disc = new FrameLayout(this);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColors(new int[]{withAlpha(accent, 90), withAlpha(accent, 30)});
            disc.setBackground(d);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(glyph);
            iv.setColorFilter(TEXT);
            disc.addView(iv, new FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER));
            content.addView(disc, lp(dp(84), dp(84), 0, 0, dp(24), 0));
        }

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView tt = text(29, TEXT, true);
        tt.setSingleLine(true);
        tt.setText(title);
        TextView st = text(13, withAlpha(accent, 230), false);
        st.setLetterSpacing(0.18f);
        st.setText(subtitle);
        texts.addView(tt);
        texts.addView(st, lp(-2, -2, 0, dp(6), 0, 0));
        content.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        t.addView(content, new FrameLayout.LayoutParams(-1, -1));
        t.setContentDescription(title);
        t.setOnClickListener(click);
        return t;
    }

    private Tile smallTile(String pkg, String label, View.OnClickListener click) {
        Tile t = new Tile(this, dp(16), 1.07f);
        t.setBackgroundColor(Color.rgb(24, 27, 32));
        Drawable banner = appBanner(pkg);
        if (banner != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(banner);
            t.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            LinearLayout l = new LinearLayout(this);
            l.setOrientation(LinearLayout.VERTICAL);
            l.setGravity(Gravity.CENTER);
            Drawable icon = appIcon(pkg);
            if (icon != null) {
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(icon);
                l.addView(iv, new LinearLayout.LayoutParams(dp(44), dp(44)));
            }
            TextView tv = text(15, TEXT, false);
            tv.setText(label);
            l.addView(tv, lp(-2, -2, 0, dp(8), 0, 0));
            t.addView(l, new FrameLayout.LayoutParams(-1, -1));
        }
        t.setContentDescription(label);
        t.setOnClickListener(click);
        return t;
    }

    private Drawable appBanner(String pkg) {
        PackageManager pm = getPackageManager();
        try {
            Intent li = pm.getLeanbackLaunchIntentForPackage(pkg);
            Drawable d = li != null ? pm.getActivityBanner(li) : null;
            return d != null ? d : pm.getApplicationBanner(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    private Drawable appIcon(String pkg) {
        try {
            return getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- actions

    private void launch(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) { toast("Nicht installiert: " + pkg); return; }
        start(i);
    }

    /** Opens HDMI 2 directly through TCL's own TV app (the red "TV" app), no input menu. */
    private void openPs5() {
        Uri uri = TvContract.buildChannelUriForPassthroughInput(PS5_INPUT);
        Intent i = new Intent(Intent.ACTION_VIEW, uri).setComponent(TCL_TV_ACTIVITY);
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            launch(TCL_TV);
        }
    }

    private void openSettings() { start(new Intent(Settings.ACTION_SETTINGS)); }

    private void openGoogleTv() {
        HomeKeyService.allowGoogleHome();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(GOOGLE_HOME);
        start(i);
    }

    private void start(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            toast("Konnte nicht öffnen");
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ---------------------------------------------------------------- helpers

    private Drawable backdrop() {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(16, 18, 22), Color.rgb(8, 9, 11)});
        GradientDrawable warm = new GradientDrawable();
        warm.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        warm.setGradientCenter(0.1f, 0.0f);
        warm.setGradientRadius(dp(620));
        warm.setColors(new int[]{Color.argb(34, 214, 160, 92), Color.argb(0, 214, 160, 92)});
        return new LayerDrawable(new Drawable[]{base, warm});
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        return r;
    }

    private TextView text(float sp, int color, boolean light) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(light ? "sans-serif-light" : "sans-serif", Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    private LinearLayout.LayoutParams weightLp(int height, int leftMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, height, 1f);
        p.leftMargin = leftMargin;
        return p;
    }

    private static LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    private static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    private static int withAlpha(int c, int a) { return Color.argb(a, Color.red(c), Color.green(c), Color.blue(c)); }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
