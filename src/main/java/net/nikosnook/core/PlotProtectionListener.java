package net.nikosnook.core;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import java.util.*;
import java.util.function.*;
import static net.nikosnook.core.PlotPermissions.Action.*;

/** Additional role restrictions. WorldGuard remains the base protection provider. */
public final class PlotProtectionListener implements Listener {
    private final ChestShopRentalGate regions;
    private final PlotPermissions permissions;
    private final BooleanSupplier healthy;
    private final Consumer<Exception> failure;
    public PlotProtectionListener(ChestShopRentalGate regions,NookStore store,BooleanSupplier healthy,Consumer<Exception> failure){
        this.regions=regions;permissions=new PlotPermissions(store);this.healthy=healthy;this.failure=failure;
    }
    private boolean allowed(Player player,Location location,PlotPermissions.Action action){
        try {
            String plot=regions.regionAt(location);
            if(plot.equals(ShopTradePolicy.TOWN))return true;
            if(!healthy.getAsBoolean())return false;
            if(player.hasPermission("nookcore.admin"))return true;
            return permissions.allowed(plot,player.getUniqueId(),action);
        }catch(Exception ex){failure.accept(ex);return false;}
    }
    private boolean protectedLocation(Location location){
        try{return !regions.regionAt(location).equals(ShopTradePolicy.TOWN);}
        catch(Exception ex){failure.accept(ex);return true;}
    }
    private boolean inventoryAllowed(Player player,InventoryHolder holder){
        if(holder instanceof HumanEntity)return true;
        if(holder instanceof DoubleChest chest)return inventoryAllowed(player,chest.getLeftSide()) && inventoryAllowed(player,chest.getRightSide());
        if(holder instanceof Container container)return allowed(player,container.getLocation(),STOCK);
        if(holder instanceof Entity entity)return allowed(player,entity.getLocation(),STOCK);
        return true; // Crafting/virtual inventories carry no plot stock themselves.
    }
    private boolean protectedInventory(Inventory inventory){
        var holder=inventory.getHolder();
        if(holder instanceof HumanEntity)return false;
        if(holder instanceof DoubleChest chest)return protectedInventory(chest.getLeftSide().getInventory()) || protectedInventory(chest.getRightSide().getInventory());
        if(holder instanceof Container container)return protectedLocation(container.getLocation());
        if(holder instanceof Entity entity)return protectedLocation(entity.getLocation());
        return false;
    }
    private void deny(Cancellable event,Player player){event.setCancelled(true);player.sendMessage(NookUi.message("NookPlots","This area is protected. Use /nookplots list for rental spaces, or ask staff for help."));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void place(BlockPlaceEvent event){if(!allowed(event.getPlayer(),event.getBlockPlaced().getLocation(),BUILD))deny(event,event.getPlayer());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void placeMultiple(BlockMultiPlaceEvent event){for(var state:event.getReplacedBlockStates())if(!allowed(event.getPlayer(),state.getLocation(),BUILD)){deny(event,event.getPlayer());break;}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent event){
        var state=event.getBlock().getState();
        if(!allowed(event.getPlayer(),state.getLocation(),state instanceof Container || state instanceof Sign?ALTER_CONTAINER:BUILD))deny(event,event.getPlayer());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void sign(SignChangeEvent event){if(!allowed(event.getPlayer(),event.getBlock().getLocation(),ALTER_CONTAINER))deny(event,event.getPlayer());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void open(InventoryOpenEvent event){if(event.getPlayer() instanceof Player player && !inventoryAllowed(player,event.getInventory().getHolder()))deny(event,player);}
    static boolean affectsTopInventory(int rawSlot,int topSize,InventoryAction action){
        if(rawSlot>=0 && rawSlot<topSize)return true;
        return action==InventoryAction.MOVE_TO_OTHER_INVENTORY
            || action==InventoryAction.COLLECT_TO_CURSOR
            || action==InventoryAction.UNKNOWN;
    }

    static boolean affectsTopInventory(Set<Integer> rawSlots,int topSize){
        return rawSlots.stream().anyMatch(slot->slot>=0 && slot<topSize);
    }

    // Recheck transfers that can actually touch the top inventory:
    // removal/role changes must invalidate already-open protected stock without
    // preventing the player from rearranging their own inventory.
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void click(InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player player))return;
        var top=event.getView().getTopInventory();
        if(affectsTopInventory(event.getRawSlot(),top.getSize(),event.getAction())
                && !inventoryAllowed(player,top.getHolder()))
            deny(event,player);
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void drag(InventoryDragEvent event){
        if(!(event.getWhoClicked() instanceof Player player))return;
        var top=event.getView().getTopInventory();
        if(affectsTopInventory(event.getRawSlots(),top.getSize())
                && !inventoryAllowed(player,top.getHolder()))
            deny(event,player);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void move(InventoryMoveItemEvent event){if(protectedInventory(event.getSource()) || protectedInventory(event.getDestination()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void pickup(InventoryPickupItemEvent event){if(protectedInventory(event.getInventory()) || protectedLocation(event.getItem().getLocation()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void bucketEmpty(PlayerBucketEmptyEvent event){if(!allowed(event.getPlayer(),event.getBlock().getLocation(),BUILD))deny(event,event.getPlayer());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void bucketFill(PlayerBucketFillEvent event){if(!allowed(event.getPlayer(),event.getBlock().getLocation(),BUILD))deny(event,event.getPlayer());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void entityInteract(PlayerInteractEntityEvent event){
        if(event.getRightClicked() instanceof ArmorStand || event.getRightClicked() instanceof ItemFrame)
            if(!allowed(event.getPlayer(),event.getRightClicked().getLocation(),ALTER_CONTAINER))deny(event,event.getPlayer());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void entityInteractAt(PlayerInteractAtEntityEvent event){entityInteract(event);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void entityPlace(EntityPlaceEvent event){if(event.getPlayer()!=null && !allowed(event.getPlayer(),event.getEntity().getLocation(),ALTER_CONTAINER))deny(event,event.getPlayer());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void hangingPlace(HangingPlaceEvent event){if(event.getPlayer()!=null && !allowed(event.getPlayer(),event.getEntity().getLocation(),ALTER_CONTAINER))deny(event,event.getPlayer());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void armorStand(PlayerArmorStandManipulateEvent event){if(!allowed(event.getPlayer(),event.getRightClicked().getLocation(),ALTER_CONTAINER))deny(event,event.getPlayer());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageEvent event){
        if(!(event.getEntity() instanceof ArmorStand || event.getEntity() instanceof ItemFrame))return;
        if(event instanceof EntityDamageByEntityEvent hit && hit.getDamager() instanceof Player player){
            if(!allowed(player,event.getEntity().getLocation(),ALTER_CONTAINER))deny(event,player);
        }else if(protectedLocation(event.getEntity().getLocation()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void hanging(HangingBreakEvent event){
        if(event instanceof HangingBreakByEntityEvent broken && broken.getRemover() instanceof Player player){
            if(!allowed(player,event.getEntity().getLocation(),ALTER_CONTAINER))deny(event,player);
        }else if(protectedLocation(event.getEntity().getLocation()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void entityExplosion(EntityExplodeEvent event){event.blockList().removeIf(block->protectedLocation(block.getLocation()));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void blockExplosion(BlockExplodeEvent event){event.blockList().removeIf(block->protectedLocation(block.getLocation()));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void flow(BlockFromToEvent event){
        try {
            String from=regions.regionAt(event.getBlock().getLocation()),to=regions.regionAt(event.getToBlock().getLocation());
            if(!allowsFlow(event.getBlock().getType()==Material.WATER,from,to))event.setCancelled(true);
        }catch(Exception ex){event.setCancelled(true);failure.accept(ex);}
    }
    static boolean allowsFlow(boolean water,String from,String to){
        if(water)return from.equals(to);
        return from.equals(ShopTradePolicy.TOWN) && to.equals(ShopTradePolicy.TOWN);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void burn(BlockBurnEvent event){if(protectedLocation(event.getBlock().getLocation()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void dispense(BlockDispenseEvent event){
        if(protectedLocation(event.getBlock().getLocation())){event.setCancelled(true);return;}
        if(event.getBlock().getBlockData() instanceof org.bukkit.block.data.Directional facing)
            if(protectedLocation(event.getBlock().getRelative(facing.getFacing()).getLocation()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void pistonExtend(BlockPistonExtendEvent event){
        if(protectedLocation(event.getBlock().getLocation()) || protectedLocation(event.getBlock().getRelative(event.getDirection()).getLocation()) || event.getBlocks().stream().anyMatch(b->protectedLocation(b.getLocation()) || protectedLocation(b.getRelative(event.getDirection()).getLocation())))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void pistonRetract(BlockPistonRetractEvent event){
        if(protectedLocation(event.getBlock().getLocation()) || protectedLocation(event.getBlock().getRelative(event.getDirection()).getLocation()) || event.getBlocks().stream().anyMatch(b->protectedLocation(b.getLocation()) || protectedLocation(b.getRelative(event.getDirection()).getLocation())))event.setCancelled(true);
    }
}
