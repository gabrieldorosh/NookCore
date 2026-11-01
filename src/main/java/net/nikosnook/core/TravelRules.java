package net.nikosnook.core;

/** Road tuning and the elapsed-time rocket-flight policy, independent of Bukkit adapters. */
final class TravelRules {
    private TravelRules() {}
    static double approach(double current, double target, double step) {
        return current < target ? Math.min(target,current+step) : Math.max(target,current-step);
    }
    static double roadBoost(double current, long tick, long lastRoad, int grace, int ramp, boolean contact) {
        boolean eligible=contact || lastRoad>=0 && tick-lastRoad<=grace;
        return approach(current,eligible?1:0,1.0/ramp);
    }
    /** One boost, then a deadline started by the first landing; later movement cannot extend it. */
    static final class RocketFlight {
        private boolean spent,airborneAfterUse;
        private long readyAt=-1;
        RocketFlight() {}
        RocketFlight(boolean spent,long readyAt){
            this.spent=spent;this.readyAt=spent?readyAt:-1;
            // A restored spent flight can land immediately on login; no launch-edge inference survives a session.
            airborneAfterUse=spent;
        }
        void observe(long now,boolean landed,long cooldownMillis){
            if(!spent)return;
            if(readyAt>=0){if(now>=readyAt){spent=false;readyAt=-1;airborneAfterUse=false;}return;}
            if(!landed)airborneAfterUse=true;
            else if(airborneAfterUse)readyAt=Math.addExact(now,cooldownMillis);
        }
        void used(boolean landed){spent=true;readyAt=-1;airborneAfterUse=!landed;}
        boolean allowed(long now){return !spent || readyAt>=0 && now>=readyAt;}
        boolean spent(){return spent;}
        long readyAt(){return readyAt;}
        long remainingMillis(long now){return readyAt<0?-1:Math.max(0,readyAt-now);}
    }
    static double bounded(double value,double min,double max,String name) {
        if(!Double.isFinite(value) || value<min || value>max)throw new IllegalArgumentException(name+" must be between "+min+" and "+max);
        return value;
    }
}
