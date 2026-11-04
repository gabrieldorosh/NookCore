package net.nikosnook.core;

import java.util.Objects;
import java.util.UUID;

/** One player's unflushed horizontal travel. Bukkit eligibility and persistence stay outside. */
final class TravelProgress {
    enum Kind { ROAD_MOUNT, RAIL }
    record Sample(UUID world,UUID vehicle,Kind kind,double x,double y,double z,long tick,long nanos,boolean eligible) {}
    record Limits(double maxRoadBps,double maxRailBps,long maxGapTicks,long maxGapNanos) {
        Limits {
            if(!Double.isFinite(maxRoadBps) || !Double.isFinite(maxRailBps) || maxRoadBps<=0 || maxRailBps<=0
                || maxRoadBps>100 || maxRailBps>100 || maxGapTicks<1 || maxGapTicks>20 || maxGapNanos<1 || maxGapNanos>2_000_000_000L)
                throw new IllegalArgumentException("Travel sample limits must be positive and bounded");
        }
    }
    record Totals(long roadMillimetres,long railMillimetres) {}
    record Batch(long sequence,long roadMillimetres,long railMillimetres) {}
    private static final Limits DEFAULTS=new Limits(50,40,10,1_000_000_000L);
    private final Limits limits;
    private Sample anchor;
    private long roadMillimetres,railMillimetres,sequence;
    private double roadFraction,railFraction;
    private Batch outstanding;

    TravelProgress(){this(DEFAULTS);}
    TravelProgress(Limits limits){this.limits=Objects.requireNonNull(limits);}
    Sample anchor(){return anchor;}
    void reset(){anchor=null;}
    void observe(Sample sample){
        if(!valid(sample)){anchor=null;return;}
        var previous=anchor;anchor=sample;
        if(previous==null || !previous.world().equals(sample.world()) || !previous.vehicle().equals(sample.vehicle()) || previous.kind()!=sample.kind())return;
        long ticks,nanos;
        try{ticks=Math.subtractExact(sample.tick(),previous.tick());nanos=Math.subtractExact(sample.nanos(),previous.nanos());}
        catch(ArithmeticException overflow){return;}
        if(ticks<1 || ticks>limits.maxGapTicks() || nanos<1 || nanos>limits.maxGapNanos() || Math.abs(sample.y()-previous.y())>2)return;
        double distance=Math.hypot(sample.x()-previous.x(),sample.z()-previous.z());
        double seconds=Math.min(ticks/20.0,nanos/1_000_000_000.0);
        double maximum=(sample.kind()==Kind.ROAD_MOUNT?limits.maxRoadBps():limits.maxRailBps())*seconds;
        if(distance>maximum+0.000001)return;
        if(sample.kind()==Kind.ROAD_MOUNT){
            double millimetres=distance*1000+roadFraction;long whole=(long)Math.floor(millimetres+1e-9);
            roadMillimetres=Math.addExact(roadMillimetres,whole);roadFraction=Math.max(0,millimetres-whole);
        }else{
            double millimetres=distance*1000+railFraction;long whole=(long)Math.floor(millimetres+1e-9);
            railMillimetres=Math.addExact(railMillimetres,whole);railFraction=Math.max(0,millimetres-whole);
        }
    }
    private static boolean valid(Sample sample){
        return sample!=null && sample.eligible() && sample.world()!=null && sample.vehicle()!=null && sample.kind()!=null
            && Double.isFinite(sample.x()) && Double.isFinite(sample.y()) && Double.isFinite(sample.z())
            && Math.abs(sample.x())<=29_999_984 && Math.abs(sample.z())<=29_999_984;
    }
    Totals pending(){return new Totals(roadMillimetres,railMillimetres);}
    /** At most one threshold write is in flight; acknowledged goals need no extra writes. */
    boolean thresholdSave(long savedRoad,long savedRail,long roadGoal,long railGoal,boolean writing){
        return !writing && (savedRoad<roadGoal && roadMillimetres>=roadGoal-savedRoad
            || savedRail<railGoal && railMillimetres>=railGoal-savedRail);
    }
    /** Retry this same batch until its store transaction commits; new observations remain pending. */
    Batch snapshot(){
        if(outstanding==null && (roadMillimetres!=0 || railMillimetres!=0))outstanding=new Batch(++sequence,roadMillimetres,railMillimetres);
        return outstanding;
    }
    /** A repeated/stale acknowledgement cannot consume distance observed after its batch. */
    boolean acknowledge(Batch batch){
        if(batch==null || !batch.equals(outstanding))return false;
        roadMillimetres-=batch.roadMillimetres();railMillimetres-=batch.railMillimetres();outstanding=null;return true;
    }
}
