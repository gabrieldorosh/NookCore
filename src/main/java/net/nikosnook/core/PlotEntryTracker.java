package net.nikosnook.core;

import java.util.*;

/** Emit only after a region has remained stable; never queue notices for old locations. */
final class PlotEntryTracker {
    private record Visit(String region,long since,boolean shown) {}
    private final Map<UUID,Visit> visits=new HashMap<>();
    boolean entered(UUID player,String region,long now){
        region=Objects.requireNonNullElse(region,ShopTradePolicy.TOWN);
        Visit previous=visits.get(player);
        if(previous==null || !previous.region().equals(region)){
            visits.put(player,new Visit(region,now,false));return false;
        }
        if(previous.shown() || now-previous.since()<500)return false;
        visits.put(player,new Visit(region,previous.since(),true));
        return !region.equals(ShopTradePolicy.TOWN);
    }
    void retain(Set<UUID> online){visits.keySet().retainAll(online);}
    void clear(){visits.clear();}
}
