package net.nikosnook.core;

import java.time.*;
import java.time.temporal.TemporalAdjusters;

/** Quests follow local calendar weeks, including the UK's daylight-saving transitions. */
public final class WeekSchedule {
    public static final ZoneId ZONE = ZoneId.of("Europe/London");
    private WeekSchedule() {}
    public static Instant start(Instant now) {
        ZonedDateTime local = now.atZone(ZONE);
        ZonedDateTime candidate = local.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY)).atTime(2, 0).atZone(ZONE);
        if (candidate.toInstant().isAfter(now)) candidate = candidate.minusWeeks(1);
        return candidate.toInstant();
    }
    public static Instant next(Instant now) { return start(now).atZone(ZONE).plusWeeks(1).toInstant(); }
}
