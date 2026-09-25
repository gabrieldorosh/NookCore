package net.nikosnook.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.time.Duration;
import java.util.*;
import java.util.function.*;

/** One notice per settled boundary crossing, with no per-frame database reads. */
final class PlotEntryNotices {
    private final PlotEntryTracker tracker=new PlotEntryTracker();
    enum Display { SUBTITLE, ACTIONBAR, OFF;
        static Display parse(String value){
            try{return valueOf(value.toUpperCase(Locale.ROOT));}
            catch(IllegalArgumentException | NullPointerException e){return ACTIONBAR;}
        }
    }
    PlotEntryNotices(JavaPlugin plugin,NookStore store,ChestShopRentalGate gate,BooleanSupplier ready,Consumer<Exception> failure){
        Display display=Display.parse(plugin.getConfig().getString("shop-gate.entry-notices","actionbar"));
        if(display==Display.OFF)return;
        Bukkit.getScheduler().runTaskTimer(plugin,()->{
            if(!ready.getAsBoolean()){tracker.clear();return;}
            Set<UUID> online=new HashSet<>();
            try{
                for(var player:Bukkit.getOnlinePlayers()){
                    UUID id=player.getUniqueId();online.add(id);
                    String plot=gate.regionAt(player.getLocation());
                    if(!tracker.entered(id,plot,System.currentTimeMillis()))continue;
                    Component message;
                    if(gate.manages(plot))message=NookUi.plot(store,store.plot(plot));
                    else if(ShopTradePolicy.ROAD.equals(plot))message=NookUi.text("Shopping district · /plots list");
                    else continue;
                    try{show(player,message,display);}
                    catch(RuntimeException e){plugin.getLogger().warning("Plot-entry display failed: "+e.getClass().getSimpleName());}
                }
                tracker.retain(online);
            }catch(Exception e){failure.accept(e);}
        },10,10);
    }
    static void show(Player player,Component message,Display display){
        if(display==Display.ACTIONBAR)player.sendActionBar(message);
        else if(display==Display.SUBTITLE)player.showTitle(Title.title(Component.empty(),message,
            Title.Times.times(Duration.ofMillis(500),Duration.ofSeconds(2),Duration.ofMillis(750))));
        // No delayed clear: it could erase a newer title from another plugin.
    }
}
