package net.nikosnook.core;

import java.time.*;

/** Calendar times are London wall times, never fixed seven-day millisecond offsets. */
final class VendorSchedule {
    record Visit(String id,long arrival,long restock,long departure,long nextArrival) {
        boolean open(long now){return now>=arrival && now<departure;}
        int allocation(long now){return now>=restock?2:1;}
    }
    static Visit at(long now){
        var start=WeekSchedule.start(Instant.ofEpochMilli(now)).atZone(WeekSchedule.ZONE);
        return new Visit(start.toLocalDate().toString(),start.toInstant().toEpochMilli(),
            start.plusDays(2).withHour(19).toInstant().toEpochMilli(),
            start.plusDays(5).toInstant().toEpochMilli(),start.plusWeeks(1).toInstant().toEpochMilli());
    }
}
