package net.nikosnook.core;

import java.time.*;

final class RentalOpening {
    static long parse(String value){
        if(value==null || value.isBlank())return 0;
        try{return OffsetDateTime.parse(value).toInstant().toEpochMilli();}
        catch(DateTimeException e){throw new IllegalArgumentException("plot-rentals-open-at must be an ISO date with an offset, or blank.",e);}
    }
    static void check(long opens,long now){
        if(now>=opens)return;
        long seconds=(opens-now+999)/1000;
        long hours=seconds/3600,minutes=seconds%3600/60,remainder=seconds%60;
        throw new IllegalArgumentException("Plot rentals open in "+hours+"h "+minutes+"m "+remainder+"s. Explore and settle in first!");
    }
}
