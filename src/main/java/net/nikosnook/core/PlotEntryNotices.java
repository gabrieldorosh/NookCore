package net.nikosnook.core;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.*;

/** Lightweight location polling; database reads only when crossing a district/plot boundary. */
final class PlotEntryNotices {
    private final Map<UUID,String> previous=new HashMap<>();
    PlotEntryNotices(JavaPlugin plugin,NookStore store,ChestShopRentalGate gate,BooleanSupplier ready,Consumer<Exception> failure){
        Bukkit.getScheduler().runTaskTimer(plugin,()->{
            if(!ready.getAsBoolean())return;
            Set<UUID> online=new HashSet<>();
            try{
                for(var player:Bukkit.getOnlinePlayers()){
                    UUID id=player.getUniqueId();online.add(id);
                    String plot=gate.regionAt(player.getLocation());
                    if(Objects.equals(previous.put(id,plot),plot))continue;
                    if(gate.manages(plot))player.sendActionBar(NookUi.plot(store,store.plot(plot)));
                    else if(plot.equals(ShopTradePolicy.ROAD))player.sendActionBar(NookUi.text("Shopping district · /nookplots list"));
                }
                previous.keySet().retainAll(online);
            }catch(Exception e){failure.accept(e);}
        },10,10);
    }
}
