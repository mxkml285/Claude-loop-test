package de.smartcomfort.app;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * The fixed comfort plan (pure Java, no Android, tested on the Mac by ScheduleTest):
 *   07:00-17:00 backlight 60, 17:00-07:00 backlight 20, 18:00-07:00 Eye Care (LowBlueRay) on.
 * Always computed in Europe/Berlin, whatever the TV's time zone setting says.
 */
public final class Schedule {
    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    public static final int DAY_BACKLIGHT = 60;
    public static final int NIGHT_BACKLIGHT = 20;
    static final int[] BOUNDARY_HOURS = {7, 17, 18};

    private Schedule() { }

    public static int backlight(LocalTime t) {
        int h = t.getHour();
        return (h >= 7 && h < 17) ? DAY_BACKLIGHT : NIGHT_BACKLIGHT;
    }

    public static boolean eyeCare(LocalTime t) {
        int h = t.getHour();
        return h >= 18 || h < 7;
    }

    /** Identifies the plan slot, so a change of slot can be told apart from a manual change. */
    public static String slot(LocalTime t) {
        return backlight(t) + "/" + (eyeCare(t) ? 1 : 0);
    }

    /** The next moment strictly after {@code now} at which the plan changes. */
    public static ZonedDateTime nextBoundary(ZonedDateTime now) {
        ZonedDateTime local = now.withZoneSameInstant(ZONE);
        ZonedDateTime best = null;
        for (int dayOffset = 0; dayOffset <= 1 && best == null; dayOffset++) {
            for (int h : BOUNDARY_HOURS) {
                ZonedDateTime c = local.toLocalDate().plusDays(dayOffset).atTime(h, 0).atZone(ZONE);
                if (c.isAfter(local) && (best == null || c.isBefore(best))) best = c;
            }
        }
        return best;
    }
}
