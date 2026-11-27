package de.smartcomfort.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Makes the Home key open the Smart Comfort start page (Google TV gives installed apps no way to
 * become the real home screen). Sees only key events and window-state changes of the Google TV
 * launcher; reads no window content. Switched off in Settings → Bedienungshilfen = everything as before.
 *
 * - Home key: consumed, start page opened.
 * - Video key (TCL 4057): consumed, MediaPlayer IBO opened. TV key (TCL 4001): consumed, HDMI 2 (PS5).
 * - 123 key (TCL 4056): short press toggles Eye Care with a status pill (not while gaming), long press
 *   opens the Control Center. The number pad is no longer reachable with this key.
 * - Also runs Ps5Watch (PS5 on -> HDMI 2, PS5 rest/off -> start page), as this service is always alive.
 * - Google TV home appears by other means (after boot, Back from an app opened there): replaced by
 *   the start page, unless the user chose "Google TV" on the start page, until the next Home press.
 */
public class HomeKeyService extends AccessibilityService {
    static final String TAG = "SmartComfortHome";
    static final String GOOGLE_HOME_CLASS = HomeActivity.GOOGLE_HOME.getClassName();

    private static volatile boolean googleAllowed;

    static void allowGoogleHome() { googleAllowed = true; }

    /** TCL remote: white key with the blue video symbol (below Netflix), normally TCL MediaCenter. */
    static final int KEY_TCL_VIDEO = 4057;
    /** TCL remote: black key with the TV symbol (below Home), normally the red TV app. */
    static final int KEY_TCL_TV = 4001;
    /** TCL remote: "123" key (number pad). Short = Eye Care on/off, long = Control Center. */
    static final int KEY_TCL_123 = 4056;
    static final long LONG_PRESS_MS = 600;

    private Ps5Watch ps5;
    private StatusOverlay overlay;
    private ControlCenter controlCenter;
    private final Runnable keep = new Runnable() {
        @Override public void run() {
            Controller.keep(HomeKeyService.this);
            handler.postDelayed(this, 5_000);
        }
    };
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean key123Down, key123Long;
    private final Runnable key123LongPress = new Runnable() {
        @Override public void run() {
            key123Long = true;
            Log.i(TAG, "123 lang -> Control Center");
            controlCenter.open();
        }
    };

    @Override
    protected void onServiceConnected() {
        overlay = new StatusOverlay(this);
        controlCenter = new ControlCenter(this);
        handler.postDelayed(keep, 5_000);
        ps5 = new Ps5Watch(this);
        ps5.start();
    }

    @Override
    public boolean onUnbind(Intent i) {
        if (ps5 != null) ps5.stop();
        handler.removeCallbacks(keep);
        if (controlCenter != null) controlCenter.close();
        return super.onUnbind(i);
    }

    /** Key repeats arrive after this filter, so a long press is timed from the first DOWN. */
    private void key123(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() > 0 || key123Down) return;
            key123Down = true;
            key123Long = false;
            handler.postDelayed(key123LongPress, LONG_PRESS_MS);
        } else if (e.getAction() == KeyEvent.ACTION_UP) {
            key123Down = false;
            handler.removeCallbacks(key123LongPress);
            if (!key123Long && !e.isCanceled()) toggleEyeCare();
        }
    }

    private void toggleEyeCare() {
        if (Ps5Watch.gaming(this)) {
            overlay.show("Gaming aktiv", "EYE CARE BLEIBT UNVERÄNDERT", false);
            return;
        }
        TclDisplay d = TclDisplay.connect();
        try {
            boolean on = d != null && d.getLowBlueRay() == 0;
            if (d != null && d.setLowBlueRay(on)) {
                Controller.hold(this, d.getBacklight(), on);
                Log.i(TAG, "123 kurz -> Eye Care " + on);
                overlay.show(on ? "Eye Care aktiviert" : "Eye Care deaktiviert",
                        "BIS ZUM NÄCHSTEN ZEITPLAN-WECHSEL", on);
                return;
            }
        } catch (Exception ex) { Log.w(TAG, "eye care", ex); }
        overlay.show("Eye Care nicht verfügbar", null, false);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KEY_TCL_123) { key123(e); return true; }
        if (controlCenter != null && controlCenter.isOpen() && controlCenter.onKey(e)) return true;
        if (code != KeyEvent.KEYCODE_HOME && code != KEY_TCL_VIDEO && code != KEY_TCL_TV) return false;
        // Down, repeats and up are all swallowed, so the key's original action never runs.
        if (code == KeyEvent.KEYCODE_HOME) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) {
                googleAllowed = false;
                openStartPage("Home-Taste");
            }
        } else if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            // Act on the press itself, no waiting for release.
            if (code == KEY_TCL_VIDEO) {
                Log.i(TAG, "Videotaste -> MediaPlayer IBO");
                if (!Launch.app(this, HomeActivity.IBO)) openStartPage("IBO fehlt");
            } else {
                Log.i(TAG, "TV-Taste -> HDMI 2 (PS5)");
                Launch.ps5(this);
            }
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent ev) {
        if (ev.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence cls = ev.getClassName();
        Log.d(TAG, "window " + ev.getPackageName() + "/" + cls);
        if (!googleAllowed && cls != null && GOOGLE_HOME_CLASS.contentEquals(cls)) {
            openStartPage("Google-TV-Startseite erschienen");
        }
    }

    private void openStartPage(String why) {
        Log.i(TAG, "open start page: " + why);
        try {
            startActivity(new Intent(this, HomeActivity.class).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } catch (Exception ex) {
            // Never leave the user without a home screen: fall back to Google TV.
            Log.w(TAG, "start page failed, falling back to Google TV", ex);
            googleAllowed = true;
            try {
                startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setComponent(HomeActivity.GOOGLE_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { }
        }
    }

    @Override
    public void onInterrupt() { }
}
