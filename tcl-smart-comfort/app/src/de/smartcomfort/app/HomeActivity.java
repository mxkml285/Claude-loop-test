package de.smartcomfort.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
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
 * Premium start page. Two large tiles (MediaPlayer IBO, PlayStation 5), four quiet small ones
 * (YouTube, Netflix, TCL Channel, Smart Comfort), clock and settings top right, Smart Comfort status.
 * Deep black, dark glass, hairline borders, champagne accents. No ads, no recommendations, no network.
 * Opened by the Home key via HomeKeyService.
 */
public class HomeActivity extends Activity {
    static final String IBO = "com.ibostore.meplayerib4k";
    static final int GRAPHITE = Color.rgb(34, 34, 38);
    static final int IBO_RED = Color.rgb(168, 36, 44);
    static final String YOUTUBE = "com.google.android.youtube.tv";
    static final String NETFLIX = "com.netflix.ninja";
    static final String TCL_CHANNEL = "com.tcl.waterfall.overseas";
    static final String TCL_TV = "com.tcl.tv";
    static final ComponentName TCL_TV_ACTIVITY = new ComponentName(TCL_TV, "com.tcl.player.TVActivity");
    /** HDMI 2, labelled "Playstation5" on this TV (see docs/STARTSEITE-ANALYSE.md). */
    static final String PS5_INPUT = "com.tcl.tvinput/.TvPassThroughService/HW16";
    static final ComponentName GOOGLE_HOME =
            new ComponentName("com.google.android.apps.tv.launcherx", "com.google.android.apps.tv.launcherx.home.HomeActivity");

    static final int IVORY = Color.rgb(238, 233, 224);
    static final int IVORY_DIM = Color.argb(150, 238, 233, 224);
    static final int IVORY_FAINT = Color.argb(100, 238, 233, 224);
    static final int CHAMPAGNE = Color.rgb(216, 194, 157);
    static final int HAIRLINE = Color.argb(20, 255, 255, 255);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView clock, date, status;
    private View statusDot;
    private Tile ibo;

    /** Refresh the status line as soon as Smart Comfort writes a new status (e.g. right after leaving HDMI). */
    private final SharedPreferences.OnSharedPreferenceChangeListener statusListener =
            (prefs, key) -> { if ("status".equals(key)) handler.post(this::updateClockAndStatus); };

    private final Runnable tickClock = new Runnable() {
        @Override public void run() {
            updateClockAndStatus();
            long ms = System.currentTimeMillis();
            handler.postDelayed(this, 60_000 - (ms % 60_000) + 50);
        }
    };

    private AmbientView ambient;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        ambient = new AmbientView(this);
        root.addView(ambient, new FrameLayout.LayoutParams(-1, -1));
        root.setClipChildren(false);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(dp(60), dp(36), dp(60), dp(24));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        col.addView(topBar(), new LinearLayout.LayoutParams(-1, dp(50)));
        col.addView(statusLine(), lp(-1, -2, 0, dp(4), 0, 0));

        // Main row: the two large tiles.
        LinearLayout big = row();
        ibo = iboTile();
        big.addView(ibo, weightLp(dp(226), 0));
        big.addView(ps5Tile(), weightLp(dp(226), dp(28)));
        col.addView(big, lp(-1, -2, 0, dp(36), 0, 0));

        // Second row: four quiet tiles. Smart Comfort sits on the far right.
        LinearLayout small = row();
        small.addView(bannerTile(appBanner(YOUTUBE), appIcon(YOUTUBE), "YouTube", v -> launch(YOUTUBE)), weightLp(dp(112), 0));
        small.addView(bannerTile(appBanner(NETFLIX), appIcon(NETFLIX), "Netflix", v -> launch(NETFLIX)), weightLp(dp(112), dp(22)));
        small.addView(bannerTile(appBanner(TCL_CHANNEL), appIcon(TCL_CHANNEL), "TCL Channel", v -> launch(TCL_CHANNEL)), weightLp(dp(112), dp(22)));
        small.addView(bannerTile(getDrawable(R.drawable.banner), getDrawable(R.drawable.ic_comfort), "Smart Comfort",
                v -> startActivity(new Intent(this, MainActivity.class))), weightLp(dp(112), dp(22)));
        col.addView(small, lp(-1, -2, 0, dp(30), 0, 0));

        setContentView(root);
        ibo.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(tickClock);
        Controller.prefs(this).registerOnSharedPreferenceChangeListener(statusListener);
        Controller.tick(this, "Startseite"); // fresh status; applies only on a real event (see Controller)
        tickClock.run();
        ambient.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tickClock);
        ambient.stop();
        Controller.prefs(this).unregisterOnSharedPreferenceChangeListener(statusListener);
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

        date = text(13, CHAMPAGNE, "sans-serif");
        date.setLetterSpacing(0.32f);
        bar.addView(date, new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.CENTER_VERTICAL));

        LinearLayout right = new LinearLayout(this);
        right.setGravity(Gravity.CENTER_VERTICAL);
        right.setClipChildren(false);
        clock = text(34, IVORY, "sans-serif-thin");
        clock.setLetterSpacing(0.04f);
        right.addView(clock, lp(-2, -2, 0, 0, dp(24), 0));
        right.addView(iconButton(R.drawable.ic_apps, "Google TV", v -> openGoogleTv()), new LinearLayout.LayoutParams(dp(42), dp(42)));
        right.addView(iconButton(R.drawable.ic_settings, "Einstellungen", v -> openSettings()), lp(dp(42), dp(42), dp(12), 0, 0, 0));
        bar.addView(right, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL));
        return bar;
    }

    private View statusLine() {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(this);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(CHAMPAGNE);
        statusDot.setBackground(dot);
        l.addView(statusDot, lp(dp(5), dp(5), 0, 0, dp(12), 0));
        status = text(13, IVORY_FAINT, "sans-serif-light");
        status.setLetterSpacing(0.06f);
        l.addView(status);
        return l;
    }

    private Tile iconButton(int icon, String label, View.OnClickListener click) {
        Tile t = new Tile(this, dp(21), 1.12f);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(14, 255, 255, 255));
        bg.setStroke(1, HAIRLINE);
        t.setBackground(bg);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(IVORY_DIM);
        t.addView(iv, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        t.setContentDescription(label);
        t.setOnClickListener(click);
        return t;
    }

    private void updateClockAndStatus() {
        ZonedDateTime now = ZonedDateTime.now(Schedule.ZONE);
        clock.setText(now.format(DateTimeFormatter.ofPattern("HH:mm")));
        date.setText(now.format(DateTimeFormatter.ofPattern("EEEE  d. MMMM", Locale.GERMANY)).toUpperCase(Locale.GERMANY));
        String s = Controller.prefs(this).getString("status", "");
        boolean active = s.startsWith("Aktiv") || s.contains("keine Änderung");
        String next = Schedule.nextBoundary(now).format(DateTimeFormatter.ofPattern("HH:mm"));
        String shown;
        if (active) {
            int bl = Schedule.backlight(now.toLocalTime());
            boolean ec = Schedule.eyeCare(now.toLocalTime());
            shown = "Smart Comfort   ·   Helligkeit " + bl + "   ·   Eye Care " + (ec ? "an" : "aus") + "   ·   ab " + next + " Uhr neu";
        } else {
            shown = "Smart Comfort   ·   " + (s.isEmpty() ? "startet" : s);
        }
        status.setText(shown);
        ((GradientDrawable) statusDot.getBackground()).setColor(active ? CHAMPAGNE : Color.rgb(150, 110, 90));
    }

    // ---------------------------------------------------------------- tiles

    /** Dark glass card: subtle diagonal gradient, an optional soft light, and a hairline border. */
    private Drawable glass(int top, int bottom, int glowColor, float glowX, float glowY, float radiusDp) {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{top, bottom});
        base.setCornerRadius(dp(radiusDp));
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(glowX, glowY);
        glow.setGradientRadius(dp(320));
        glow.setColors(new int[]{glowColor, withAlpha(glowColor, 0)});
        glow.setCornerRadius(dp(radiusDp));
        GradientDrawable edge = new GradientDrawable();
        edge.setColor(Color.TRANSPARENT);
        edge.setCornerRadius(dp(radiusDp));
        edge.setStroke(Math.max(1, dp(0.75f)), HAIRLINE);
        return new LayerDrawable(new Drawable[]{base, glow, edge});
    }

    private Tile iboTile() {
        Tile t = new Tile(this, dp(24), 1.04f);
        // Brushed silver like the IBO icon itself: light in the middle, darker toward the edges.
        GradientDrawable silver = new GradientDrawable();
        silver.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        silver.setGradientCenter(0.32f, 0.38f);
        silver.setGradientRadius(dp(430));
        silver.setColors(new int[]{Color.rgb(216, 216, 218), Color.rgb(198, 198, 201), Color.rgb(160, 161, 165)});
        silver.setCornerRadius(dp(24));
        GradientDrawable edge = new GradientDrawable();
        edge.setColor(Color.TRANSPARENT);
        edge.setCornerRadius(dp(24));
        edge.setStroke(Math.max(1, dp(0.75f)), Color.argb(70, 255, 255, 255));
        t.setBackground(new LayerDrawable(new Drawable[]{silver, edge}));
        t.setRimColor(Color.rgb(150, 116, 70)); // darker bronze rim, visible on silver
        LinearLayout content = tileContent();
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ibo_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        content.addView(logo, lp(dp(124), dp(92), 0, 0, dp(24), 0));
        content.addView(titles("MediaPlayer", "FERNSEHEN", GRAPHITE, IBO_RED),
                new LinearLayout.LayoutParams(0, -2, 1f));
        t.addView(content, new FrameLayout.LayoutParams(-1, -1));
        t.setContentDescription("MediaPlayer IBO");
        t.setOnClickListener(v -> launch(IBO));
        return t;
    }

    private Tile ps5Tile() {
        Tile t = new Tile(this, dp(24), 1.04f);
        // Deep PlayStation blue, lit from the upper left.
        t.setBackground(glass(Color.rgb(10, 30, 78), Color.rgb(4, 10, 28), Color.argb(70, 90, 140, 255), 0.2f, 0.1f, 24));
        LinearLayout content = tileContent();
        ImageView logo = new ImageView(this);
        logo.setImageDrawable(playStationLogo());
        logo.setColorFilter(IVORY);
        content.addView(logo, lp(dp(92), dp(92), 0, 0, dp(28), 0));
        content.addView(titles("PlayStation 5", "SPIELEN", IVORY, Color.rgb(170, 195, 245)), new LinearLayout.LayoutParams(0, -2, 1f));
        t.addView(content, new FrameLayout.LayoutParams(-1, -1));
        t.setContentDescription("PlayStation 5");
        t.setOnClickListener(v -> openPs5());
        return t;
    }

    /** The PlayStation logo if it is in the build (res/drawable/ic_playstation.xml), else a controller glyph. */
    private Drawable playStationLogo() {
        int id = getResources().getIdentifier("ic_playstation", "drawable", getPackageName());
        return getDrawable(id != 0 ? id : R.drawable.ic_controller);
    }

    private LinearLayout tileContent() {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(36), 0, dp(20), 0);
        return c;
    }

    private LinearLayout titles(String title, String subtitle, int titleColor, int accent) {
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView tt = text(31, titleColor, "sans-serif-thin");
        tt.setSingleLine(true);
        tt.setLetterSpacing(0.01f);
        tt.setText(title);
        View rule = new View(this);
        rule.setBackgroundColor(withAlpha(accent, 120));
        TextView st = text(11, withAlpha(accent, 235), "sans-serif");
        st.setLetterSpacing(0.28f);
        st.setSingleLine(true);
        st.setText(subtitle);
        texts.addView(tt);
        texts.addView(rule, lp(dp(28), Math.max(1, dp(0.75f)), 0, dp(12), 0, dp(10)));
        texts.addView(st);
        return texts;
    }

    private Tile smallTile(Drawable icon, String label, View.OnClickListener click) {
        Tile t = new Tile(this, dp(18), 1.07f);
        t.setBackground(glass(Color.rgb(22, 23, 26), Color.rgb(12, 13, 15), Color.argb(18, 255, 255, 255), 0.5f, 0f, 18));
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setOutlineProvider(new Tile.Rounded(dp(12)));
            iv.setClipToOutline(true);
            l.addView(iv, new LinearLayout.LayoutParams(dp(46), dp(46)));
        }
        TextView tv = text(11, IVORY_DIM, "sans-serif");
        tv.setLetterSpacing(0.24f);
        tv.setText(label.toUpperCase(Locale.GERMANY));
        l.addView(tv, lp(-2, -2, 0, dp(12), 0, 0));
        t.addView(l, new FrameLayout.LayoutParams(-1, -1));
        t.setContentDescription(label);
        t.setOnClickListener(click);
        return t;
    }

    /** Small tile filled edge to edge with the app's TV banner; falls back to icon + label. */
    private Tile bannerTile(Drawable banner, Drawable icon, String label, View.OnClickListener click) {
        if (banner == null) return smallTile(icon, label, click);
        Tile t = new Tile(this, dp(18), 1.07f);
        t.setBackgroundColor(Color.rgb(12, 13, 15));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(banner);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        t.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        t.setContentDescription(label);
        t.setOnClickListener(click);
        return t;
    }

    /** The banner Google TV itself shows for the app (leanback activity first, then application). */
    private Drawable appBanner(String pkg) {
        PackageManager pm = getPackageManager();
        try {
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i != null && i.getComponent() != null) {
                Drawable d = pm.getActivityBanner(i.getComponent());
                if (d != null) return d;
            }
            return pm.getApplicationBanner(pkg);
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
        start(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(GOOGLE_HOME));
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

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        return r;
    }

    private TextView text(float sp, int color, String family) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
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

    private static int withAlpha(int c, int a) { return Color.argb(a, Color.red(c), Color.green(c), Color.blue(c)); }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
