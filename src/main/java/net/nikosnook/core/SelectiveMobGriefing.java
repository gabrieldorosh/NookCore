package net.nikosnook.core;

import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;

/** Preserve mobGriefing for villagers; suppress only the requested destructive actions. */
final class SelectiveMobGriefing implements Listener {
    private final boolean creepers,endermen;
    SelectiveMobGriefing(boolean creepers,boolean endermen){this.creepers=creepers;this.endermen=endermen;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explosion(EntityExplodeEvent event){
        if(creepers && event.getEntity() instanceof Creeper)event.blockList().clear();
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void changeBlock(EntityChangeBlockEvent event){
        if(endermen && event.getEntity() instanceof Enderman)event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void hanging(HangingBreakByEntityEvent event){
        if(creepers && event.getRemover() instanceof Creeper)event.setCancelled(true);
    }
}
