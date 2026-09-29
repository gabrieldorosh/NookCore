package net.nikosnook.core;

/** Tick-based transitions keep travel tuning independent of server wall-clock pauses. */
final class TravelRules {
    private TravelRules() {}
    static double approach(double current, double target, double step) {
        return current < target ? Math.min(target,current+step) : Math.max(target,current-step);
    }
    static double roadBoost(double current, long tick, long lastRoad, int grace, int ramp, boolean contact) {
        boolean eligible=contact || lastRoad>=0 && tick-lastRoad<=grace;
        return approach(current,eligible?1:0,1.0/ramp);
    }
    static boolean rocketAllowed(long tick, Long lastGround, int window) {
        return lastGround!=null && tick-lastGround>=0 && tick-lastGround<=window;
    }
    /** A landing ends the previous flight. Only uninterrupted ground contact re-arms it. */
    static final class Takeoff {
        private long groundedSince=-1;
        private Long departed;
        private boolean ready;
        void update(long tick, boolean grounded, int rearm) {
            if(grounded) {
                if(groundedSince<0){groundedSince=tick;departed=null;ready=false;}
                if(tick-groundedSince>=rearm)ready=true;
            } else if(groundedSince>=0) {
                departed=ready?tick:null;
                groundedSince=-1;ready=false;
            }
        }
        boolean allowed(long tick,int window){return rocketAllowed(tick,departed,window);}
    }
    static double bounded(double value,double min,double max,String name) {
        if(!Double.isFinite(value) || value<min || value>max)throw new IllegalArgumentException(name+" must be between "+min+" and "+max);
        return value;
    }
}
