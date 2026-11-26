package de.smartcomfort.app;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Plain-Java checks for Schedule, run on the Mac by test.sh (no JUnit needed). */
public class ScheduleTest {
    static int failures = 0;

    static void check(String what, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            failures++;
            System.out.println("FAIL " + what + ": expected " + expected + ", got " + actual);
        }
    }

    static LocalTime t(String s) { return LocalTime.parse(s); }

    static ZonedDateTime berlin(String s) {
        return ZonedDateTime.of(java.time.LocalDateTime.parse(s), Schedule.ZONE);
    }

    public static void main(String[] a) {
        check("06:59 backlight", 20, Schedule.backlight(t("06:59")));
        check("07:00 backlight", 60, Schedule.backlight(t("07:00")));
        check("16:59 backlight", 60, Schedule.backlight(t("16:59")));
        check("17:00 backlight", 20, Schedule.backlight(t("17:00")));
        check("23:30 backlight", 20, Schedule.backlight(t("23:30")));

        check("06:59 eye care", true, Schedule.eyeCare(t("06:59")));
        check("07:00 eye care", false, Schedule.eyeCare(t("07:00")));
        check("17:30 eye care", false, Schedule.eyeCare(t("17:30")));
        check("18:00 eye care", true, Schedule.eyeCare(t("18:00")));
        check("00:00 eye care", true, Schedule.eyeCare(t("00:00")));

        check("slot 17:30", "20/0", Schedule.slot(t("17:30")));
        check("slot 18:30", "20/1", Schedule.slot(t("18:30")));
        check("slot 12:00", "60/0", Schedule.slot(t("12:00")));

        check("next after 06:00", berlin("2026-11-26T07:00"), Schedule.nextBoundary(berlin("2026-11-26T06:00")));
        check("next after 07:00", berlin("2026-11-26T17:00"), Schedule.nextBoundary(berlin("2026-11-26T07:00")));
        check("next after 17:20", berlin("2026-11-26T18:00"), Schedule.nextBoundary(berlin("2026-11-26T17:20")));
        check("next after 18:00", berlin("2026-11-27T07:00"), Schedule.nextBoundary(berlin("2026-11-26T18:00")));
        check("next after 23:59", berlin("2026-11-27T07:00"), Schedule.nextBoundary(berlin("2026-11-26T23:59")));

        // Input in another zone is converted to Berlin first (UTC 16:30 = Berlin 17:30 in winter).
        ZonedDateTime utc = ZonedDateTime.of(java.time.LocalDateTime.parse("2026-11-26T16:30"), ZoneId.of("UTC"));
        check("next from UTC", berlin("2026-11-26T18:00").toInstant(), Schedule.nextBoundary(utc).toInstant());

        // Daylight saving: 29 Mar 2026 02:00 -> 03:00, 25 Oct 2026 03:00 -> 02:00.
        check("next DST spring", berlin("2026-03-29T07:00"), Schedule.nextBoundary(berlin("2026-03-29T01:30")));
        check("next DST autumn", berlin("2026-10-25T07:00"), Schedule.nextBoundary(berlin("2026-10-25T01:30")));

        System.out.println(failures == 0 ? "ScheduleTest: all passed" : "ScheduleTest: " + failures + " failed");
        System.exit(failures == 0 ? 0 : 1);
    }
}
