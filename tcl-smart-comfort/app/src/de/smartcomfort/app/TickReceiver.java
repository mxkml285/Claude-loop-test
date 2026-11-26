package de.smartcomfort.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarm ticks, boot, app update, clock or time zone change: all end in one Controller tick. */
public class TickReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction() == null ? "" : i.getAction();
        String reason;
        switch (a) {
            case Intent.ACTION_BOOT_COMPLETED: reason = "boot"; break;
            case Intent.ACTION_MY_PACKAGE_REPLACED: reason = "update"; break;
            case Intent.ACTION_TIME_CHANGED:
            case Intent.ACTION_TIMEZONE_CHANGED: reason = "Uhrzeit"; break;
            default: reason = "Zeitplan";
        }
        Controller.tick(c, reason);
    }
}
