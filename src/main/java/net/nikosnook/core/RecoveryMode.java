package net.nikosnook.core;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.command.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import java.util.function.Supplier;

/** Recovery is deliberately independent of the economy database and rental membership reconciliation. */
final class RecoveryMode implements Listener {
    static final String MESSAGE="Temporarily disabled while we recover server data.";
    private final JavaPlugin plugin;
    private static final NamespacedKey ANCHOR=new NamespacedKey("nookcore","native-shop");
    RecoveryMode(JavaPlugin plugin,AdminTeleports teleports,Supplier<List<String>> status){
        this.plugin=plugin;
        for(String name:List.of("nooks","baltop","plots","quests","nookshops","bunny","nookadmin")){
            var command=plugin.getCommand(name);
            command.setExecutor((s,c,label,a)->{
                if(c.getName().equals("nookadmin") && a.length>0){
                    if(a[0].equalsIgnoreCase("status"))return AdminStatus.execute(s,a,status);
                    if(Set.of("teleport","return").contains(a[0].toLowerCase(Locale.ROOT))){teleports.execute(s,a);return true;}
                }
                s.sendMessage(NookUi.problem("NookCore",MESSAGE));return true;
            });
            command.setTabCompleter((s,c,label,a)->c.getName().equals("nookadmin") && s.hasPermission("nookcore.admin") && a.length==1?NookUi.complete(a[0],List.of("status","teleport","return")):List.of());
        }
        if(Bukkit.getPluginManager().getPlugin("Vault")!=null)
            Bukkit.getServicesManager().register(net.milkbowl.vault.economy.Economy.class,new VaultBridge(null,()->false,e->{}),plugin,org.bukkit.plugin.ServicePriority.Highest);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Bukkit.getScheduler().runTask(plugin,this::disableChestShop);
    }
    private void disableChestShop(){var shop=Bukkit.getPluginManager().getPlugin("ChestShop");if(shop!=null && shop.isEnabled()){Bukkit.getPluginManager().disablePlugin(shop);plugin.getLogger().warning("ChestShop disabled during NookCore recovery.");}}
    @EventHandler public void pluginEnabled(PluginEnableEvent e){if(e.getPlugin().getName().equals("ChestShop"))disableChestShop();}
    @EventHandler public void join(PlayerJoinEvent e){e.getPlayer().sendMessage(NookUi.message("NookCore","Nooks, shops and quests are temporarily disabled. Your data is being recovered."));}
    static boolean marked(Block b){
        if(b.getState() instanceof Container c){
            if(c.getPersistentDataContainer().has(ANCHOR,PersistentDataType.STRING))return true;
            if(c.getInventory().getHolder() instanceof DoubleChest d)
                return tagged(d.getLeftSide()) || tagged(d.getRightSide());
        }
        return false;
    }
    private static boolean tagged(org.bukkit.inventory.InventoryHolder h){return h instanceof Container c && c.getPersistentDataContainer().has(ANCHOR,PersistentDataType.STRING);}
    private static boolean shopOrLabel(Block b){
        if(marked(b))return true;
        if(!(b.getState() instanceof Sign))return false;
        var data=b.getBlockData();
        if(data instanceof org.bukkit.block.data.type.WallSign w)return marked(b.getRelative(w.getFacing().getOppositeFace()));
        if(data instanceof org.bukkit.block.data.type.Sign)return marked(b.getRelative(BlockFace.DOWN));
        if(data instanceof org.bukkit.block.data.type.HangingSign)return marked(b.getRelative(BlockFace.UP));
        return false;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void interact(PlayerInteractEvent e){if(e.getClickedBlock()!=null && shopOrLabel(e.getClickedBlock())){e.setCancelled(true);if(e.getHand()==org.bukkit.inventory.EquipmentSlot.HAND)e.getPlayer().sendMessage(NookUi.problem("NookShops",MESSAGE));}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void breakBlock(BlockBreakEvent e){if(shopOrLabel(e.getBlock())){e.setCancelled(true);e.getPlayer().sendMessage(NookUi.problem("NookShops",MESSAGE));}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void open(InventoryOpenEvent e){var h=e.getInventory().getHolder();if(h instanceof Container c && marked(c.getBlock()) || h instanceof DoubleChest d && (tagged(d.getLeftSide()) || tagged(d.getRightSide()))){e.setCancelled(true);e.getPlayer().sendMessage(NookUi.problem("NookShops",MESSAGE));}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void move(InventoryMoveItemEvent e){for(var inv:List.of(e.getSource(),e.getDestination())){var h=inv.getHolder();if(h instanceof Container c && marked(c.getBlock()) || h instanceof DoubleChest d && (tagged(d.getLeftSide()) || tagged(d.getRightSide()))){e.setCancelled(true);return;}}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explode(EntityExplodeEvent e){e.blockList().removeIf(RecoveryMode::shopOrLabel);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explodeBlock(BlockExplodeEvent e){e.blockList().removeIf(RecoveryMode::shopOrLabel);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void extend(BlockPistonExtendEvent e){if(e.getBlocks().stream().anyMatch(RecoveryMode::shopOrLabel))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void retract(BlockPistonRetractEvent e){if(e.getBlocks().stream().anyMatch(RecoveryMode::shopOrLabel))e.setCancelled(true);}
}
