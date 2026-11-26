package de.smartcomfort.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * One "tick": decide whether to apply the plan, apply it, schedule the next tick.
 *
 * Applies the plan only on events: plan slot changed (07/17/18 Uhr), source or picture mode changed,
 * TV woke from standby or booted, or the user pressed "Jetzt anwenden". Between events a manual
 * change on the remote is left alone until the next event.
 *
 * Only for normal TV and Google TV apps: on any HDMI or AV input, or an unknown source, nothing is
 * touched. Values are applied with act=EXEC (not saved into the TV's picture presets), so the TV's
 * own saved settings, e.g. those the PS5 input uses, are not rewritten.
 */
final class Controller {
    static final String TAG = "SmartComfort";
    static final String ACTION_TICK = "de.smartcomfort.app.TICK";
    static final long TICK_MS = 60_000;

    /** TCL device ids from tcl_sys_tv_input_current_device_id: 21 = Google TV apps, 1/2 = tuner. */
    static final int SOURCE_APPS = 21;
    static final int[] SOURCES_TUNER = {1, 2};

    private Controller() { }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("state", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", true); }

    static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean("enabled", on).putString("lastSlot", "").apply();
        log(c, on ? "eingeschaltet" : "pausiert");
    }

    static int source(Context c) {
        try {
            String v = Settings.Secure.getString(c.getContentResolver(), "tcl_sys_tv_input_current_device_id");
            return v == null ? -1 : Integer.parseInt(v.trim());
        } catch (Exception e) {
            return -2;
        }
    }

    static boolean allowedSource(int src) {
        if (src == SOURCE_APPS) return true;
        for (int s : SOURCES_TUNER) if (s == src) return true;
        return false;
    }

    static String sourceName(int src) {
        if (src == SOURCE_APPS) return "Google-TV-Apps";
        if (src == 1 || src == 2) return "Fernsehen (Tuner)";
        if (src >= 15 && src <= 18) return "HDMI " + (src - 14);
        if (src == 3) return "AV";
        if (src == -1) return "unbekannt";
        if (src == -2) return "nicht lesbar";
        return "Quelle " + src;
    }

    static synchronized void tick(Context c, String reason) {
        scheduleNext(c);
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit();
        ZonedDateTime now = ZonedDateTime.now(Schedule.ZONE);
        String slot = Schedule.slot(now.toLocalTime());
        int wantBl = Schedule.backlight(now.toLocalTime());
        boolean wantEc = Schedule.eyeCare(now.toLocalTime());

        // Standby/boot detection: deep sleep grows (elapsedRealtime - uptime); a reboot changes boot time.
        long sleptMs = SystemClock.elapsedRealtime() - SystemClock.uptimeMillis();
        long bootAt = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000;
        boolean woke = sleptMs - p.getLong("sleptMs", sleptMs) > 30_000 || Math.abs(bootAt - p.getLong("bootAt", bootAt)) > 2;
        e.putLong("sleptMs", sleptMs).putLong("bootAt", bootAt);

        String status = "";
        try {
            if (!enabled(c)) { status = "Pausiert"; return; }
            String fw = FirmwareGuard.problem();
            if (fw != null) { status = "Gesperrt: " + fw; return; }
            TclDisplay d = TclDisplay.connect();
            if (d == null) { status = "TCL-Dienst nicht erreichbar"; return; }

            int src = source(c);
            if (!allowedSource(src)) {
                status = sourceName(src) + ": keine Änderung";
                e.putString("lastContext", "src" + src);
                return;
            }
            String context = src + ":" + d.getPictureMode();
            boolean force = reason.equals("boot") || reason.equals("manual") || reason.equals("enabled")
                    || woke || !slot.equals(p.getString("lastSlot", "")) || !context.equals(p.getString("lastContext", ""));
            int bl = d.getBacklight();
            boolean ec = d.getLowBlueRay() != 0;
            if (force) {
                StringBuilder did = new StringBuilder();
                if (bl != wantBl) { did.append(" Helligkeit ").append(bl).append("→").append(wantBl).append(d.setBacklight(wantBl) ? "" : "(Fehler)"); }
                if (ec != wantEc) { did.append(" EyeCare ").append(wantEc ? "an" : "aus").append(d.setLowBlueRay(wantEc) ? "" : "(Fehler)"); }
                String why = woke ? "Aufwachen" : reason;
                log(c, why + " · " + sourceName(src) + (did.length() == 0 ? " · schon richtig" : " ·" + did));
                e.putString("lastSlot", slot).putString("lastContext", context);
                bl = d.getBacklight();
                ec = d.getLowBlueRay() != 0;
            }
            status = "Aktiv · " + sourceName(src) + " · Helligkeit " + bl + " · Eye Care " + (ec ? "an" : "aus");
        } catch (Exception ex) {
            status = "Fehler: " + ex;
            Log.w(TAG, "tick failed", ex);
        } finally {
            Log.d(TAG, "tick " + reason + " -> " + status);
            e.putString("status", status).apply();
        }
    }

    static void scheduleNext(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        long now = System.currentTimeMillis();
        long boundary = Schedule.nextBoundary(ZonedDateTime.now(Schedule.ZONE)).toInstant().toEpochMilli() + 1_000;
        long at = Math.min(now + TICK_MS, boundary);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0,
                new Intent(c, TickReceiver.class).setAction(ACTION_TICK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // RTC (not RTC_WAKEUP): never wakes the TV; a tick missed in standby fires on wake.
        if (am.canScheduleExactAlarms()) am.setExact(AlarmManager.RTC, at, pi);
        else am.set(AlarmManager.RTC, at, pi);
    }

    static void log(Context c, String msg) {
        Log.i(TAG, msg);
        String line = ZonedDateTime.now(Schedule.ZONE).format(DateTimeFormatter.ofPattern("dd.MM. HH:mm")) + "  " + msg;
        SharedPreferences p = prefs(c);
        String old = p.getString("log", "");
        String all = line + (old.isEmpty() ? "" : "\n" + old);
        String[] lines = all.split("\n");
        StringBuilder keep = new StringBuilder();
        for (int i = 0; i < Math.min(12, lines.length); i++) keep.append(i == 0 ? "" : "\n").append(lines[i]);
        p.edit().putString("log", keep.toString()).apply();
    }
}
