package net.nikosnook.core;

import com.Acrobot.ChestShop.Events.PreTransactionEvent;
import com.Acrobot.ChestShop.Events.PreShopCreationEvent;
import com.Acrobot.ChestShop.Events.Protection.ProtectionCheckEvent;
import com.Acrobot.ChestShop.Utils.uBlock;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.event.*;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;
import java.sql.SQLException;
import java.util.*;
import java.util.function.*;

/** Staging-only shop bridge; player acceptance is required before production use. */
public final class ChestShopRentalGate implements Listener {
    private final JavaPlugin plugin;
    private final ShopTradePolicy policy;
    private final PlotPermissions permissions;
    private final BooleanSupplier healthy;
    private final Consumer<Exception> failure;
    private final UUID world;
    private final String district;
    private final Map<String,String> regions;
    public ChestShopRentalGate(JavaPlugin plugin,NookStore store,BooleanSupplier healthy,Consumer<Exception> failure) {
        this.plugin=plugin;this.policy=new ShopTradePolicy(store);this.permissions=new PlotPermissions(store);this.healthy=healthy;this.failure=failure;
        world=UUID.fromString(plugin.getConfig().getString("shop-gate.world-uuid",""));
        district=Objects.requireNonNull(plugin.getConfig().getString("shop-gate.district-region"));
        var section=plugin.getConfig().getConfigurationSection("shop-gate.plot-regions");
        if(district.isBlank() || section==null || section.getKeys(false).isEmpty())throw new IllegalArgumentException("Configure the district and plot region mapping first.");
        Map<String,String> mapping=new HashMap<>();
        for(String key:section.getKeys(false))mapping.put(key,Objects.requireNonNull(section.getString(key)));
        if(new HashSet<>(mapping.values()).size()!=mapping.size())throw new IllegalArgumentException("Each plot must map to one region.");
        regions=Map.copyOf(mapping);
        // Validate the whole mapping now and again on every trade; never treat a missing region as wilderness.
        regionAt(new Location(Objects.requireNonNull(Bukkit.getWorld(world),"District world is not loaded"),0,0,0));
        for(String id:regions.values())storePlot(store,id);
        validateConfiguration();
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void create(PreShopCreationEvent event) {
        try {
            if(!healthy.getAsBoolean())throw new IllegalArgumentException("Shop creation is paused; contact staff.");
            List<String> locations=new ArrayList<>();locations.add(regionAt(event.getSign().getLocation()));
            var container=uBlock.findConnectedContainer(event.getSign());
            if(container==null)throw new IllegalArgumentException("A physical stock container is required.");
            containers(container.getInventory().getHolder(),locations);
            for(String location:locations)if(!permissions.allowed(location,event.getPlayer().getUniqueId(),PlotPermissions.Action.ALTER_CONTAINER))
                throw new IllegalArgumentException("Creating a plot shop requires BUILD and STOCK permissions.");
            policy.check(locations,event.getOwnerAccount()==null?null:event.getOwnerAccount().getUuid(),System.currentTimeMillis());
        }catch(IllegalArgumentException ex){event.setCancelled(true);event.getPlayer().sendMessage(NookUi.error(ex.getMessage()));}
        catch(Exception ex){event.setCancelled(true);failure.accept(ex);event.getPlayer().sendMessage(NookUi.text("Shop creation is paused; contact staff."));}
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void stockAccess(ProtectionCheckEvent event){
        try{
            String plot=regionAt(event.getBlock().getLocation());
            if(plot.equals(ShopTradePolicy.TOWN))return; // Never grant account-wide access to a renter's other shops.
            if(!healthy.getAsBoolean()){event.setResult(Event.Result.DENY);return;}
            if(event.getPlayer().hasPermission("nookcore.admin")){event.setResult(Event.Result.ALLOW);return;}
            var state=event.getBlock().getState();
            var action=state instanceof Container?PlotPermissions.Action.STOCK:state instanceof Sign?PlotPermissions.Action.ALTER_CONTAINER:PlotPermissions.Action.BUILD;
            List<String> locations=new ArrayList<>();locations.add(plot);
            if(state instanceof Container container)containers(container.getInventory().getHolder(),locations);
            boolean permitted=locations.stream().allMatch(plot::equals);
            for(String location:locations)permitted &= permissions.allowed(location,event.getPlayer().getUniqueId(),action);
            // Override ChestShop's single-owner check only when WorldGuard independently allows access.
            boolean worldGuardAllows=WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().testBuild(
                BukkitAdapter.adapt(event.getBlock().getLocation()),WorldGuardPlugin.inst().wrapPlayer(event.getPlayer()),Flags.CHEST_ACCESS);
            event.setResult(permitted && worldGuardAllows?Event.Result.ALLOW:Event.Result.DENY);
        }catch(Exception ex){event.setResult(Event.Result.DENY);failure.accept(ex);}
    }
    private static void storePlot(NookStore store,String id) {
        try{store.plot(id);}catch(SQLException ex){throw new IllegalStateException("Cannot read configured plot",ex);}
    }
    void reconcileMembers(NookStore store)throws Exception {
        // Managed plot regions belong exclusively to NookCore; roads/parent retain staff configuration.
        var loaded=Objects.requireNonNull(Bukkit.getWorld(world));
        regionAt(new Location(loaded,0,0,0));
        var manager=WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(loaded));
        Objects.requireNonNull(manager.getRegion(district)).setFlag(Flags.DENY_MESSAGE,plugin.getConfig().getString("shop-gate.deny-message","&bNiko's Nook &8» &fThe shopping district is protected. Build in a plot you have access to, or ask staff for help."));
        for(var entry:regions.entrySet()) {
            var region=Objects.requireNonNull(manager.getRegion(entry.getKey()));
            var lease=store.plot(entry.getValue());
            DefaultDomain owners=new DefaultDomain(),members=new DefaultDomain();
            if(lease.owner()!=null && store.role(entry.getValue(),lease.owner()).equals("OWNER"))owners.addPlayer(lease.owner());
            for(var member:store.members(entry.getValue()).entrySet())
                if(!member.getKey().equals(lease.owner()))members.addPlayer(member.getKey());
            region.setOwners(owners);region.setMembers(members);
        }
        manager.save(); // Caller must keep payments/commands paused on failure.
    }
    boolean manages(String plot){return regions.containsValue(plot);}
    Set<String> managedPlots(){return Set.copyOf(regions.values());}
    void validateConfiguration(){
        var loaded=Objects.requireNonNull(Bukkit.getWorld(world),"District world is not loaded");
        regionAt(new Location(loaded,0,0,0));
        var manager=WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(loaded));
        var parent=manager.getRegion(district);
        if(!(parent instanceof ProtectedCuboidRegion))throw new IllegalStateException("Use a cuboid district region.");
        List<ProtectedCuboidRegion> checked=new ArrayList<>();
        for(String id:regions.keySet()){
            var region=manager.getRegion(id);
            if(!(region instanceof ProtectedCuboidRegion cube))throw new IllegalStateException("Use cuboid plot regions.");
            var min=cube.getMinimumPoint();var max=cube.getMaximumPoint();
            if(!parent.contains(min) || !parent.contains(max) || min.y()>loaded.getMinHeight() || max.y()<loaded.getMaxHeight()-1)
                throw new IllegalStateException("Plot must be inside the district and cover the full world height: "+id);
            for(var previous:checked){var a=previous.getMinimumPoint();var b=previous.getMaximumPoint();
                if(min.x()<=b.x() && max.x()>=a.x() && min.z()<=b.z() && max.z()>=a.z())throw new IllegalStateException("Plot footprints overlap.");
            }
            checked.add(cube);
        }
    }
    String regionAt(Location location) {
        if(!plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard") || !plugin.getServer().getPluginManager().isPluginEnabled("WorldEdit"))throw new IllegalStateException("Plot protection is unavailable.");
        if(!location.getWorld().getUID().equals(world))return ShopTradePolicy.TOWN;
        var manager=WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(location.getWorld()));
        if(manager==null)throw new IllegalStateException("District regions are unavailable.");
        var parent=manager.getRegion(district);
        if(parent==null || !parent.isPhysicalArea())throw new IllegalStateException("District region is missing.");
        var point=BlockVector3.at(location.getBlockX(),location.getBlockY(),location.getBlockZ());
        String found=null;
        for(var entry:regions.entrySet()) {
            var region=manager.getRegion(entry.getKey());
            if(region==null || !region.isPhysicalArea() || region.getParent()!=parent)
                throw new IllegalStateException("Plot region is missing or has the wrong parent: "+entry.getKey());
            if(region.contains(point)) {
                if(found!=null || !parent.contains(point))throw new IllegalStateException("Overlapping plots or plot outside district.");
                found=entry.getValue();
            }
        }
        return found!=null?found:parent.contains(point)?ShopTradePolicy.ROAD:ShopTradePolicy.TOWN;
    }
    void containers(InventoryHolder holder,List<String> result) {
        if(holder instanceof DoubleChest chest) {
            containers(chest.getLeftSide(),result);containers(chest.getRightSide(),result);
        }else if(holder instanceof Container container)result.add(regionAt(container.getLocation()));
        else throw new IllegalArgumentException("This stock container cannot be verified. Use a block container.");
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void trade(PreTransactionEvent event) {
        try {
            if(!healthy.getAsBoolean())throw new IllegalArgumentException("Shop payments are paused; contact staff.");
            List<String> locations=new ArrayList<>();locations.add(regionAt(event.getSign().getLocation()));
            if(event.getOwnerInventory()==null)throw new IllegalArgumentException("A physical stock container is required.");
            containers(event.getOwnerInventory().getHolder(),locations);
            policy.checkCustomer(locations,event.getOwnerAccount()==null?null:event.getOwnerAccount().getUuid(),event.getClient().getUniqueId(),System.currentTimeMillis());
        }catch(IllegalArgumentException ex) {
            event.setCancelled(true);event.getClient().sendMessage(NookUi.error(ex.getMessage()));
        }catch(Exception ex) {
            event.setCancelled(true);failure.accept(ex);event.getClient().sendMessage(NookUi.text("Shop payments are paused; contact staff."));
        }
    }
}
