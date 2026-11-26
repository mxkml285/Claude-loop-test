package de.smartcomfort.app;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

/**
 * While the process is alive, react at once to screen-on and to source changes instead of waiting
 * for the next minute tick. The alarm tick keeps working if the process is gone.
 * (Named nested classes instead of anonymous ones: d8 8.2 fails on javac 27's anonymous classes.)
 */
public class SmartComfortApp extends Application {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        registerReceiver(new ScreenOnReceiver(), new IntentFilter(Intent.ACTION_SCREEN_ON));
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor("tcl_sys_tv_input_current_device_id"), false, new SourceObserver());
        Controller.scheduleNext(this);
    }

    void tickLater(String reason, long delayMs) {
        handler.postDelayed(new DelayedTick(this, reason), delayMs);
    }

    static final class DelayedTick implements Runnable {
        private final Context c;
        private final String reason;
        DelayedTick(Context c, String reason) { this.c = c; this.reason = reason; }
        @Override public void run() { Controller.tick(c, reason); }
    }

    final class ScreenOnReceiver extends BroadcastReceiver {
        // TCL restores its own picture values on wake; give it a moment, then apply.
        @Override public void onReceive(Context c, Intent i) { tickLater("Bildschirm an", 4000); }
    }

    final class SourceObserver extends ContentObserver {
        SourceObserver() { super(handler); }
        @Override public void onChange(boolean self) { tickLater("Quellenwechsel", 3000); }
    }
}
