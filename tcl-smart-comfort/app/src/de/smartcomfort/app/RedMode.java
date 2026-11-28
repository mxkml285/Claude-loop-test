package de.smartcomfort.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Red Mode: only the red channel stays, green and blue are switched off in the TV's own picture
 * processing (TCL "RGB-Modus: Nur Rot"), so it works for every app incl. IBO, Netflix and YouTube.
 * Intensity is the picture contrast (red signal level); backlight and Eye Care stay with the plan.
 *
 * - Values are applied without saving into the TV's presets (act EXEC): after a reboot the TV is normal.
 * - Before switching on, the TV's own RGB mode and contrast are stored; switching off writes them back.
 * - Never on HDMI (PS5 etc.): on an HDMI input Red Mode is suspended, the RGB mode is set back to the
 *   TV's own value and nothing else is touched; back on apps it is applied again.
 * - TCL may reload the picture mode's stored values (video start, signal change): keep() re-applies.
 * No time schedule: on and off only with the red key or the Control Center.
 */
final class RedMode {
    static final String TAG = "SmartComfortRed";
    static final int MIN = 20, MAX = 100, STEP = 10, DEFAULT = 70;

    private RedMode() { }

    private static SharedPreferences p(Context c) { return Controller.prefs(c); }

    static boolean on(Context c) { return p(c).getBoolean("redOn", false); }

    static int intensity(Context c) { return p(c).getInt("redIntensity", DEFAULT); }

    /** Toggle (red key, Control Center). Returns the user-facing result, or null if not possible. */
    static synchronized String toggle(Context c) {
        return on(c) ? disable(c) : enable(c);
    }

    static synchronized String enable(Context c) {
        if (Ps5Watch.gaming(c)) return null;
        TclDisplay d = TclDisplay.connect();
        if (d == null) return null;
        try {
            int rgb = d.getRgbMode(), con = d.getContrast();
            p(c).edit().putBoolean("redOn", true)
                    .putInt("redOrigRgb", rgb == TclDisplay.RGB_RED_ONLY ? TclDisplay.RGB_OFF : rgb)
                    .putInt("redOrigContrast", con).apply();
            apply(c, d);
            Controller.log(c, "Red Mode an (Intensität " + intensity(c) + ", vorher RGB " + rgb + ", Kontrast " + con + ")");
            return "on";
        } catch (Exception e) {
            Log.w(TAG, "enable", e);
            return null;
        }
    }

    static synchronized String disable(Context c) {
        p(c).edit().putBoolean("redOn", false).apply();
        restore(c);
        Controller.log(c, "Red Mode aus, Farben wiederhergestellt");
        return "off";
    }

    static synchronized void setIntensity(Context c, int v) {
        v = Math.max(MIN, Math.min(MAX, v));
        p(c).edit().putInt("redIntensity", v).apply();
        if (on(c) && !Ps5Watch.gaming(c)) {
            TclDisplay d = TclDisplay.connect();
            try { if (d != null) d.setContrast(v); } catch (Exception e) { Log.w(TAG, "intensity", e); }
        }
    }

    private static void apply(Context c, TclDisplay d) throws Exception {
        if (d.getRgbMode() != TclDisplay.RGB_RED_ONLY) d.setRgbMode(TclDisplay.RGB_RED_ONLY);
        int want = intensity(c);
        if (d.getContrast() != want) d.setContrast(want);
    }

    /** Writes the stored original values back (RGB mode and contrast). */
    private static void restore(Context c) {
        TclDisplay d = TclDisplay.connect();
        if (d == null) return;
        try {
            int rgb = p(c).getInt("redOrigRgb", TclDisplay.RGB_OFF);
            int con = p(c).getInt("redOrigContrast", -1);
            if (d.getRgbMode() != rgb) d.setRgbMode(rgb);
            if (con >= 0 && !Ps5Watch.gaming(c) && d.getContrast() != con) d.setContrast(con);
            Log.i(TAG, "restored RGB " + rgb + ", Kontrast " + con);
        } catch (Exception e) {
            Log.w(TAG, "restore", e);
        }
    }

    /** Called every few seconds by HomeKeyService: hold Red Mode on apps, keep it off HDMI. */
    static synchronized void keep(Context c) {
        TclDisplay d = TclDisplay.connect();
        if (d == null) return;
        try {
            boolean hdmi = Ps5Watch.gaming(c);
            if (!on(c) || hdmi) {
                // Never leave red on a game or after switching off (e.g. TCL kept it on a source change).
                int orig = p(c).getInt("redOrigRgb", TclDisplay.RGB_OFF);
                if (d.getRgbMode() == TclDisplay.RGB_RED_ONLY && orig != TclDisplay.RGB_RED_ONLY) {
                    d.setRgbMode(orig);
                    Log.i(TAG, hdmi ? "HDMI: Red Mode pausiert" : "Rot-Rest entfernt");
                }
                return;
            }
            if (!Controller.allowedSource(Controller.source(c))) return;
            if (d.getRgbMode() != TclDisplay.RGB_RED_ONLY || d.getContrast() != intensity(c)) {
                apply(c, d);
                Log.i(TAG, "Red Mode erneut angewendet");
            }
        } catch (Exception e) {
            Log.w(TAG, "keep", e);
        }
    }
}
