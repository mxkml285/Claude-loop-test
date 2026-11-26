package de.smartcomfort.probe;

import android.app.Activity;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;
import android.widget.TextView;

import java.lang.reflect.Method;

/**
 * Read-only check: can an ordinary app (untrusted_app, no permissions) read backlight and
 * low blue ray from TCL's tcl_tv_display service? Calls ONLY the two getters, never a setter.
 * Transaction codes are those of firmware UTT2.250416.001 AU02 (jar sha256 c14f5ea2...4600).
 */
public class ProbeActivity extends Activity {
    static final String TAG = "SmartComfortProbe";
    static final String SERVICE = "tcl_tv_display";
    static final String DESCRIPTOR = "com.tcl.tv.display.ITvDisplayManagerService";
    static final int TX_GET_BACKLIGHT = 101;   // int getBacklight()
    static final int TX_GET_LOW_BLUE_RAY = 109; // int getLowBlueRay(int windowId)

    private final StringBuilder out = new StringBuilder();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView tv = new TextView(this);
        tv.setTextSize(24);
        tv.setPadding(64, 48, 64, 48);
        setContentView(tv);

        line("Smart Comfort Prüfung (nur lesen)");
        line("uid = " + android.os.Process.myUid());

        IBinder binder = null;
        try {
            Method gs = Class.forName("android.os.ServiceManager").getMethod("getService", String.class);
            binder = (IBinder) gs.invoke(null, SERVICE);
            line("ServiceManager.getService: " + (binder != null ? "OK" : "null"));
        } catch (Throwable t) {
            line("ServiceManager.getService: FEHLER " + t);
        }

        if (binder != null) {
            line("getBacklight = " + call(binder, TX_GET_BACKLIGHT, false));
            line("getLowBlueRay = " + call(binder, TX_GET_LOW_BLUE_RAY, true));
            // Write test only when started explicitly with --ez writetest true (approved: 20 -> 21 -> 20).
            if (getIntent().getBooleanExtra("writetest", false)) writeTest(binder);
        }

        // Second path: TCL's own shared library (uses-library com.tcl.tv.display), reflection only.
        try {
            Class<?> c = Class.forName("com.tcl.tv.display.TvDisplayManager");
            line("TCL-Bibliothek geladen: " + c.getName());
        } catch (Throwable t) {
            line("TCL-Bibliothek: nicht geladen (" + t.getClass().getSimpleName() + ")");
        }

        line("FERTIG");
        tv.setText(out.toString());
    }

    static final int TX_SET_BACKLIGHT = 100; // boolean setBacklight(int type, int value, int act)
    static final int APPLY_CURRENT = 0;      // EnApplyType.EN_APPLY_CURRENT
    static final int ACT_EXEC = 0;           // EnTclActType.EN_TCL_ACT_EXEC: apply, do not save

    private void writeTest(IBinder binder) {
        String before = call(binder, TX_GET_BACKLIGHT, false);
        line("SCHREIBTEST: vorher " + before);
        int orig;
        try { orig = Integer.parseInt(before); } catch (NumberFormatException e) { line("abgebrochen"); return; }
        if (orig < 0 || orig > 99) { line("abgebrochen: unerwarteter Wert"); return; }
        line("setBacklight(" + (orig + 1) + ") = " + setBacklight(binder, orig + 1));
        sleep(1500);
        line("nachher getBacklight = " + call(binder, TX_GET_BACKLIGHT, false));
        line("zurueck setBacklight(" + orig + ") = " + setBacklight(binder, orig));
        sleep(500);
        line("final getBacklight = " + call(binder, TX_GET_BACKLIGHT, false));
    }

    private String setBacklight(IBinder binder, int value) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(APPLY_CURRENT);
            data.writeInt(value);
            data.writeInt(ACT_EXEC);
            if (!binder.transact(TX_SET_BACKLIGHT, data, reply, 0)) return "transact=false";
            reply.readException();
            return String.valueOf(reply.readInt() != 0);
        } catch (Throwable t) {
            return "FEHLER " + t;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }

    private String call(IBinder binder, int code, boolean windowArg) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            if (windowArg) data.writeInt(0);
            boolean ok = binder.transact(code, data, reply, 0);
            if (!ok) return "transact=false";
            reply.readException();
            return String.valueOf(reply.readInt());
        } catch (Throwable t) {
            return "FEHLER " + t;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private void line(String s) {
        Log.i(TAG, s);
        out.append(s).append('\n');
    }
}
