package net.nikosnook.core;

import java.time.*;
import java.time.format.DateTimeParseException;

/** Saved configuration plus the current instant determines entry; no timer or database mutation. */
record EndOpening(boolean manualLock,Instant opensAt) {
    static EndOpening parse(boolean locked,String value){
        if(value==null || value.isBlank())return new EndOpening(locked,null);
        try{return new EndOpening(locked,OffsetDateTime.parse(value.strip()).toInstant());}
        catch(DateTimeParseException ex){throw new IllegalArgumentException("travel.end-opens-at must include a date, time and offset, for example 2026-10-23T19:00:00+01:00",ex);}
    }
    boolean locked(long now){return manualLock && (opensAt==null || Instant.ofEpochMilli(now).isBefore(opensAt));}
    String status(long now){
        if(!manualLock)return "End access: OPEN (lock-end=false)";
        if(opensAt==null)return "End access: LOCKED (no opening scheduled)";
        return "End access: "+(locked(now)?"LOCKED until ":"OPEN since ")+NookUi.date(opensAt.toEpochMilli());
    }
}
