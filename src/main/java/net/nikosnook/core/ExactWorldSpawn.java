package net.nikosnook.core;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerRespawnEvent;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import java.util.function.*;

/** Optional indoor world spawn; leaves saved login positions, beds and anchors alone. */
final class ExactWorldSpawn implements Listener {
    private final Predicate<Location> safe;
    private final Function<Supplier<Location>,Location> onMain;
    private final Consumer<String> warning;
    ExactWorldSpawn(Predicate<Location> safe,Consumer<String> warning){this(safe,warning,Supplier::get);}
    ExactWorldSpawn(Predicate<Location> safe,Consumer<String> warning,Function<Supplier<Location>,Location> onMain){this.safe=safe;this.warning=warning;this.onMain=onMain;}
    ExactWorldSpawn(org.bukkit.plugin.java.JavaPlugin plugin,Predicate<Location> safe,Consumer<String> warning){
        this(safe,warning,task->{
            if(org.bukkit.Bukkit.isPrimaryThread())return task.get();
            var future=org.bukkit.Bukkit.getScheduler().callSyncMethod(plugin,task::get);
            try{return future.get(5,java.util.concurrent.TimeUnit.SECONDS);}
            catch(InterruptedException e){Thread.currentThread().interrupt();future.cancel(false);warning.accept("Exact spawn check interrupted; preserving server-selected location.");return null;}
            catch(Exception e){future.cancel(false);warning.accept("Exact spawn check unavailable; preserving server-selected location.");return null;}
        });
    }
    private Location destination(World world){
        if(world==null || world.getEnvironment()!=World.Environment.NORMAL)return null;
        Location target=world.getSpawnLocation().clone();
        target.setX(target.getBlockX()+.5);target.setZ(target.getBlockZ()+.5);
        try{if(safe.test(target))return target;}catch(RuntimeException ignored){}
        warning.accept("Exact world spawn is unsafe or unavailable in "+world.getName()+"; keeping the server-selected spawn.");
        return null;
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void firstJoin(AsyncPlayerSpawnLocationEvent event){
        if(!event.isNewPlayer())return;
        Location target=onMain.apply(()->destination(event.getSpawnLocation().getWorld()));
        if(target!=null)event.setSpawnLocation(target);
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void respawn(PlayerRespawnEvent event){
        if(event.getRespawnReason()!=PlayerRespawnEvent.RespawnReason.DEATH || event.isBedSpawn() || event.isAnchorSpawn())return;
        Location target=destination(event.getRespawnLocation().getWorld());
        if(target!=null)event.setRespawnLocation(target);
    }
}
