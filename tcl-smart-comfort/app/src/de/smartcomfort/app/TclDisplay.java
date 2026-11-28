package de.smartcomfort.app;

import android.os.IBinder;
import android.os.Parcel;

import java.lang.reflect.Method;

/**
 * Minimal client for TCL's tcl_tv_display service. Transaction codes and signatures are those of
 * firmware UTT2.250416.001 AU02 (checked by FirmwareGuard before any use). Only these five calls
 * exist here: no HDMI, input, CEC or audio methods.
 */
final class TclDisplay {
    static final String SERVICE = "tcl_tv_display";
    static final String DESCRIPTOR = "com.tcl.tv.display.ITvDisplayManagerService";
    static final int TX_GET_PICTURE_MODE = 27;  // int getPictureMode()
    static final int TX_SET_BACKLIGHT = 100;    // boolean setBacklight(int type, int value, int act)
    static final int TX_GET_BACKLIGHT = 101;    // int getBacklight()
    static final int TX_SET_LOW_BLUE_RAY = 108; // boolean setLowBlueRay(int windowId, int value, int act)
    static final int TX_GET_LOW_BLUE_RAY = 109; // int getLowBlueRay(int windowId)
    static final int TX_SET_CONTRAST = 31;      // boolean setContrast(int windowId, int type, int value, int act)
    static final int TX_GET_CONTRAST = 32;      // int getContrast(int windowId)
    static final int TX_SET_RGB_MODE = 68;      // boolean setRGBMode(int windowId, int type, int value, int act)
    static final int TX_GET_RGB_MODE = 69;      // int getRGBMode(int windowId)
    /** EnPicture.RgbMode: 0 OFF, 1 RED_ONLY, 2 GREEN_ONLY, 3 BLUE_ONLY (TCL picture menu "RGB-Modus"). */
    static final int RGB_OFF = 0, RGB_RED_ONLY = 1;

    static final int APPLY_CURRENT = 0; // EnApplyType.EN_APPLY_CURRENT: current picture mode only
    static final int ACT_EXEC = 0;      // EnTclActType.EN_TCL_ACT_EXEC: apply, do not save to the TV's presets
    static final int WINDOW_MAIN = 0;

    private final IBinder binder;

    private TclDisplay(IBinder b) { binder = b; }

    static TclDisplay connect() {
        try {
            Method gs = Class.forName("android.os.ServiceManager").getMethod("getService", String.class);
            IBinder b = (IBinder) gs.invoke(null, SERVICE);
            return b == null ? null : new TclDisplay(b);
        } catch (Throwable t) {
            return null;
        }
    }

    int getBacklight() throws Exception { return callInt(TX_GET_BACKLIGHT); }
    int getPictureMode() throws Exception { return callInt(TX_GET_PICTURE_MODE); }
    int getLowBlueRay() throws Exception { return callInt(TX_GET_LOW_BLUE_RAY, WINDOW_MAIN); }

    boolean setBacklight(int value) throws Exception {
        if (value < 0 || value > 100) throw new IllegalArgumentException("backlight " + value);
        return callInt(TX_SET_BACKLIGHT, APPLY_CURRENT, value, ACT_EXEC) != 0;
    }

    boolean setLowBlueRay(boolean on) throws Exception {
        return callInt(TX_SET_LOW_BLUE_RAY, WINDOW_MAIN, on ? 1 : 0, ACT_EXEC) != 0;
    }

    int getContrast() throws Exception { return callInt(TX_GET_CONTRAST, WINDOW_MAIN); }
    int getRgbMode() throws Exception { return callInt(TX_GET_RGB_MODE, WINDOW_MAIN); }

    boolean setContrast(int value) throws Exception {
        if (value < 0 || value > 100) throw new IllegalArgumentException("contrast " + value);
        return callInt(TX_SET_CONTRAST, WINDOW_MAIN, APPLY_CURRENT, value, ACT_EXEC) != 0;
    }

    boolean setRgbMode(int mode) throws Exception {
        if (mode < 0 || mode > 3) throw new IllegalArgumentException("rgb mode " + mode);
        return callInt(TX_SET_RGB_MODE, WINDOW_MAIN, APPLY_CURRENT, mode, ACT_EXEC) != 0;
    }

    private int callInt(int code, int... args) throws Exception {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            for (int a : args) data.writeInt(a);
            if (!binder.transact(code, data, reply, 0)) throw new IllegalStateException("transact " + code + " failed");
            reply.readException();
            return reply.readInt();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }
}
