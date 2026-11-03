package net.nikosnook.core;

import java.time.*;

/** Daily puzzles turn over at 02:00 London local time, not after a fixed 24 hours. */
record WordleDay(String id,long nextReset) {
    static WordleDay at(long millis){
        Instant now=Instant.ofEpochMilli(millis);LocalDate date=now.atZone(WeekSchedule.ZONE).toLocalDate();
        if(now.isBefore(date.atTime(2,0).atZone(WeekSchedule.ZONE).toInstant()))date=date.minusDays(1);
        return new WordleDay(date.toString(),date.plusDays(1).atTime(2,0).atZone(WeekSchedule.ZONE).toInstant().toEpochMilli());
    }
}
