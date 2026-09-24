package net.nikosnook.core;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;
import java.util.function.*;

/** Optional indoor world spawn; leaves saved login positions, beds and anchors alone. */
final class ExactWorldSpawn implements Listener {
    private final Predicate<Location> safe;
    private final Consumer<String> warning;
    ExactWorldSpawn(Predicate<Location> safe,Consumer<String> warning){this.safe=safe;this.warning=warning;}
    private Location destination(World world){
        if(world==null || world.getEnvironment()!=World.Environment.NORMAL)return null;
        Location target=world.getSpawnLocation().clone();
        target.setX(target.getBlockX()+.5);target.setZ(target.getBlockZ()+.5);
        try{if(safe.test(target))return target;}catch(RuntimeException ignored){}
        warning.accept("Exact world spawn is unsafe or unavailable in "+world.getName()+"; keeping the server-selected spawn.");
        return null;
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void firstJoin(PlayerSpawnLocationEvent event){
        if(event.getPlayer().hasPlayedBefore())return;
        Location target=destination(event.getSpawnLocation().getWorld());
        if(target!=null)event.setSpawnLocation(target);
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void respawn(PlayerRespawnEvent event){
        if(event.getRespawnReason()!=PlayerRespawnEvent.RespawnReason.DEATH || event.isBedSpawn() || event.isAnchorSpawn())return;
        Location target=destination(event.getRespawnLocation().getWorld());
        if(target!=null)event.setRespawnLocation(target);
    }
}
