package net.nikosnook.core;

/** Area-only introductory rent, anchored to the three established square sizes. */
final class PlotPricing {
    static long weekly(long area){
        if(area<1 || area>1_000_000)throw new IllegalArgumentException("Choose an area from 1 to 1,000,000 blocks.");
        double nooks=area<=81?area*30.0/81:area<=144?30+(area-81)*20.0/63:50+(area-144)*40.0/81;
        return Math.max(1,Math.round(nooks))*100;
    }
}
