package de.smartcomfort.app;

import android.content.Context;
import android.content.Intent;
import android.media.tv.TvInputManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

/**
 * Follows the PS5 on HDMI 2 through Android's own HDMI input state (no CEC commands sent):
 * CONNECTED = PS5 on, CONNECTED_STANDBY = rest mode, DISCONNECTED = off / unplugged.
 *
 * - PS5 turns on (standby/off -> on, stable 2 s) while the screen is on and another source is shown:
 *   HDMI 2 is opened at once (TCL's own switch would follow about 10 s later anyway).
 * - PS5 goes to rest mode or off (stable 6 s) while HDMI 2 is shown: back to the start page. The
 *   brightness plan is then applied by the Controller's source-change tick.
 * Short signal drops during HDR, VRR or resolution changes do not change this state; the debounce
 * covers any flicker anyway. Nothing on the picture is touched while HDMI 2 is shown.
 */
final class Ps5Watch {
    static final String TAG = "SmartComfortPs5";
    static final int SOURCE_PS5 = 16;
    static final long ON_STABLE_MS = 2_000, OFF_STABLE_MS = 6_000, POLL_MS = 3_000;

    private final Context c;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final TvInputManager tim;
    private int stable = -1;          // last state we acted on (or started with)
    private int pending = -1;         // state waiting for its debounce
    private boolean running;

    Ps5Watch(Context c) {
        this.c = c;
        this.tim = c.getSystemService(TvInputManager.class);
    }

    void start() {
        if (tim == null || running) return;
        running = true;
        stable = state();
        Log.i(TAG, "start, PS5 state " + name(stable));
        try { tim.registerCallback(new Cb(), h); } catch (Exception e) { Log.w(TAG, "callback", e); }
        h.post(poll);
    }

    void stop() {
        running = false;
        h.removeCallbacksAndMessages(null);
    }

    private int state() {
        try { return tim.getInputState(HomeActivity.PS5_INPUT); } catch (Exception e) { return -1; }
    }

    static String name(int s) {
        switch (s) {
            case TvInputManager.INPUT_STATE_CONNECTED: return "an";
            case TvInputManager.INPUT_STATE_CONNECTED_STANDBY: return "Ruhemodus";
            case TvInputManager.INPUT_STATE_DISCONNECTED: return "aus";
            default: return "unbekannt";
        }
    }

    /** Polling backs up the callback (cheap: one binder call every 3 s). */
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!running) return;
            seen(state());
            h.postDelayed(this, POLL_MS);
        }
    };

    private final Runnable confirm = new Runnable() {
        @Override public void run() { decide(); }
    };

    private void seen(int s) {
        if (s < 0) return;
        if (s == stable) {                       // flicker back: drop the pending change
            if (pending != -1) { Log.d(TAG, "ignored short change to " + name(pending)); pending = -1; h.removeCallbacks(confirm); }
            return;
        }
        if (s == pending) return;
        pending = s;
        h.removeCallbacks(confirm);
        h.postDelayed(confirm, s == TvInputManager.INPUT_STATE_CONNECTED ? ON_STABLE_MS : OFF_STABLE_MS);
    }

    private void decide() {
        int s = state();
        if (s != pending) { pending = -1; if (s >= 0) seen(s); return; }
        int was = stable;
        stable = s;
        pending = -1;
        boolean on = s == TvInputManager.INPUT_STATE_CONNECTED;
        boolean wasOn = was == TvInputManager.INPUT_STATE_CONNECTED;
        int src = Controller.source(c);
        Log.i(TAG, "PS5 " + name(was) + " -> " + name(s) + ", Quelle " + src);
        if (!screenOn()) return;
        if (on && !wasOn && src != SOURCE_PS5) {
            Controller.log(c, "PS5 eingeschaltet → HDMI 2");
            Launch.ps5(c);
        } else if (!on && wasOn && src == SOURCE_PS5) {
            Controller.log(c, "PS5 " + name(s) + " → Startseite");
            try {
                c.startActivity(new Intent(c, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            } catch (Exception e) { Log.w(TAG, "start page", e); }
        }
    }

    private boolean screenOn() {
        PowerManager pm = c.getSystemService(PowerManager.class);
        return pm == null || pm.isInteractive();
    }

    final class Cb extends TvInputManager.TvInputCallback {
        @Override public void onInputStateChanged(String id, int s) {
            if (HomeActivity.PS5_INPUT.equals(id)) seen(s);
        }
    }

    /** True while the PS5 picture is shown: no Eye Care key, no picture controls. */
    static boolean gaming(Context c) {
        int src = Controller.source(c);
        return src >= 15 && src <= 18;
    }
}
