package net.nikosnook.core;

import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import java.util.*;
import java.util.function.*;

/** Account-backed prepaid time; item duplication cannot duplicate the time balance. */
final class BunnyBoots implements Listener,CommandExecutor,TabCompleter {
    private final JavaPlugin plugin;
    private final NookStore store;
    private final BooleanSupplier healthy;
    private final Consumer<Exception> failure;
    private final NamespacedKey itemKey,boostKey;
    private final boolean enabled;
    private final long price;
    private final Set<UUID> active=new HashSet<>();
    BunnyBoots(JavaPlugin plugin,NookStore store,BooleanSupplier healthy,Consumer<Exception> failure){
        this.plugin=plugin;this.store=store;this.healthy=healthy;this.failure=failure;
        itemKey=new NamespacedKey(plugin,"bunny-boots");boostKey=new NamespacedKey(plugin,"bunny-jump");
        enabled=plugin.getConfig().getBoolean("bunny-boots.enabled",false);
        price=plugin.getConfig().getLong("bunny-boots.cents-per-minute",0);
        if(price<0 || price>Money.MAX)throw new IllegalArgumentException("Invalid bunny time price.");
        var command=Objects.requireNonNull(plugin.getCommand("bunny"));command.setExecutor(this);command.setTabCompleter(this);
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        for(var player:Bukkit.getOnlinePlayers())removeBoost(player);
        Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,20);
    }
    static boolean boots(Material type){return Set.of(Material.LEATHER_BOOTS,Material.CHAINMAIL_BOOTS,Material.IRON_BOOTS,Material.COPPER_BOOTS,Material.GOLDEN_BOOTS,Material.DIAMOND_BOOTS,Material.NETHERITE_BOOTS).contains(type);}
    private boolean wearing(Player player){var boots=player.getInventory().getBoots();return boots!=null && boots(boots.getType()) && boots.hasItemMeta() && boots.getItemMeta().getPersistentDataContainer().has(itemKey,PersistentDataType.BYTE);}
    static boolean eligible(boolean enabled,boolean healthy,boolean wearing,GameMode mode,boolean dead){return enabled && healthy && wearing && mode==GameMode.SURVIVAL && !dead;}
    private void removeBoost(Player player){
        var attribute=player.getAttribute(Attribute.JUMP_STRENGTH);
        if(attribute!=null)for(var modifier:attribute.getModifiers())if(modifier.getKey().equals(boostKey))attribute.removeModifier(modifier);
        active.remove(player.getUniqueId());
    }
    private void tick(){
        for(var player:Bukkit.getOnlinePlayers()){
            if(!eligible(enabled,healthy.getAsBoolean(),wearing(player),player.getGameMode(),player.isDead())){removeBoost(player);continue;}
            try{
                if(store.consumeBunnySecond(player.getUniqueId())){
                    var attribute=player.getAttribute(Attribute.JUMP_STRENGTH);
                    if(attribute==null)throw new IllegalStateException("Player jump attribute unavailable");
                    if(attribute.getModifiers().stream().noneMatch(m->m.getKey().equals(boostKey)))attribute.addTransientModifier(new AttributeModifier(boostKey,0.1,AttributeModifier.Operation.ADD_NUMBER));
                    active.add(player.getUniqueId());
                }else{
                    boolean wasActive=active.contains(player.getUniqueId());removeBoost(player);
                    if(wasActive)player.sendMessage(NookUi.message("NookBunny","Your bunny time has run out. Fall protection remains while wearing the boots."));
                }
            }catch(Exception e){removeBoost(player);failure.accept(e);}
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageEvent event){
        if(enabled && event.getCause()==EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player player && wearing(player))event.setDamage(event.getDamage()*0.5);
    }
    @EventHandler public void quit(PlayerQuitEvent event){removeBoost(event.getPlayer());}
    @EventHandler public void join(PlayerJoinEvent event){removeBoost(event.getPlayer());}
    @EventHandler public void death(PlayerDeathEvent event){removeBoost(event.getEntity());}
    @EventHandler public void mode(PlayerGameModeChangeEvent event){removeBoost(event.getPlayer());}
    @EventHandler public void armour(com.destroystokyo.paper.event.player.PlayerArmorChangeEvent event){removeBoost(event.getPlayer());}
    void close(){for(var player:Bukkit.getOnlinePlayers())removeBoost(player);}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        try{
            if(!enabled)throw new IllegalArgumentException("Bunny boots are not open yet.");
            if(!healthy.getAsBoolean())throw new IllegalArgumentException("The economy is paused. Please ask staff for help.");
            if(args.length==3 && args[0].equalsIgnoreCase("grant")){
                if(!sender.hasPermission("nookcore.admin"))throw new IllegalArgumentException("Only admins can grant test time.");
                var account=store.byName(args[1]).orElseThrow(()->new IllegalArgumentException("Unknown player."));
                int minutes=Integer.parseInt(args[2]);store.grantBunnyTime(account.id(),minutes,sender.getName(),System.currentTimeMillis());
                sender.sendMessage(NookUi.message("NookBunny","Granted "+minutes+" minutes to "+account.name()+"."));
                var recipient=Bukkit.getPlayer(account.id());if(recipient!=null)recipient.sendMessage(NookUi.message("NookBunny","Staff added "+minutes+" minutes of bunny time."));return true;
            }
            if(!(sender instanceof Player player))throw new IllegalArgumentException("Use bunny grant <player> <minutes> from console.");
            if(args.length==1 && args[0].equalsIgnoreCase("enchant")){
                if(!sender.hasPermission("nookcore.admin"))throw new IllegalArgumentException("Only admins can prepare bunny boots during testing.");
                ItemStack held=player.getInventory().getItemInMainHand();if(!boots(held.getType()))throw new IllegalArgumentException("Hold a pair of boots.");
                var meta=held.getItemMeta();if(meta.getPersistentDataContainer().has(itemKey,PersistentDataType.BYTE))throw new IllegalArgumentException("These are already bunny boots.");
                meta.getPersistentDataContainer().set(itemKey,PersistentDataType.BYTE,(byte)1);
                var lore=new ArrayList<Component>(meta.lore()==null?List.of():meta.lore());lore.add(Component.text("Bunny I",NookUi.ACCENT));lore.add(NookUi.text("Prepaid jump time · 50% less fall damage"));meta.lore(lore);meta.setEnchantmentGlintOverride(true);held.setItemMeta(meta);player.getInventory().setItemInMainHand(held);
                player.sendMessage(NookUi.message("NookBunny","Bunny boots prepared. Existing enchantments are preserved."));return true;
            }
            if(args.length==2 && args[0].equalsIgnoreCase("charge")){
                int minutes=Integer.parseInt(args[1]);store.buyBunnyTime(player.getUniqueId(),minutes,price,System.currentTimeMillis());
                player.sendMessage(NookUi.message("NookBunny","Added "+minutes+" minutes for "+Money.format(price*minutes)+"."));
            }else if(args.length>0 && !(args.length==1 && args[0].equalsIgnoreCase("help")))throw new IllegalArgumentException("Use /bunny or /bunny charge <minutes>.");
            long seconds=store.bunnySeconds(player.getUniqueId());
            player.sendMessage(NookUi.message("NookBunny","Time remaining: "+seconds/60+"m "+seconds%60+"s. Pauses when the boots are off or you are offline."));
            if(price>0)NookUi.help(sender,"Bunny boots","/bunny charge <minutes> — buy 1–60 minutes at "+Money.format(price)+" per minute");
            else player.sendMessage(NookUi.text("Purchases are not open yet. Staff can grant test time."));
            if(sender.hasPermission("nookcore.admin"))NookUi.help(sender,"Staff","/bunny enchant — prepare held boots","/bunny grant <player> <minutes> — grant test time");
        }catch(NumberFormatException e){sender.sendMessage(NookUi.problem("NookBunny","Choose a whole number of minutes from 1 to 60."));}
        catch(IllegalArgumentException e){sender.sendMessage(NookUi.problem("NookBunny",e.getMessage()));}
        catch(Exception e){failure.accept(e);sender.sendMessage(NookUi.problem("NookBunny","Could not update bunny time. Ask staff to check the logs."));}
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String label,String[] args){
        if(!enabled)return List.of();
        if(args.length==1)return NookUi.complete(args[0],sender.hasPermission("nookcore.admin")?List.of("help","charge","enchant","grant"):List.of("help","charge"));
        if(args.length==2 && args[0].equalsIgnoreCase("grant") && sender.hasPermission("nookcore.admin"))return NookUi.complete(args[1],Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        if(args.length==2 && args[0].equalsIgnoreCase("charge") || args.length==3 && args[0].equalsIgnoreCase("grant") && sender.hasPermission("nookcore.admin"))return NookUi.complete(args[args.length-1],List.of("1","5","10","30","60"));
        return List.of();
    }
}
