package net.nikosnook.core;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Spectator tools with per-viewer hiding and durable, private return points. */
final class StaffMode implements Listener,CommandExecutor,TabCompleter {
    private static final Set<UUID> ACTIVE=ConcurrentHashMap.newKeySet();
    private final JavaPlugin plugin;private final StaffState state;
    private final java.util.function.Supplier<Collection<? extends Player>> players;
    private volatile Set<String> hiddenNames=Set.of();private volatile Set<UUID> seeNames=Set.of();
    private Object discordHook;private Set<Object> discordHooks;
    private final Set<UUID> changing=new HashSet<>();private final Map<UUID,Location> returns=new HashMap<>();
    static boolean active(UUID id){return ACTIVE.contains(id);}
    static boolean allowed(CommandSender s){return s.hasPermission("nookcore.staff.use")||s.hasPermission("nookcore.admin");}
    static boolean sees(Player p){return p.hasPermission("nookcore.staff.see")||p.hasPermission("nookcore.admin");}
    StaffMode(JavaPlugin plugin)throws Exception{
        this(plugin,new StaffState(plugin.getDataFolder().toPath().resolve("staff-mode.properties")),Bukkit::getOnlinePlayers);
    }
    StaffMode(JavaPlugin plugin,StaffState state,java.util.function.Supplier<Collection<? extends Player>> players){
        this.plugin=plugin;this.state=state;this.players=players;ACTIVE.clear();ACTIVE.addAll(state.ids());
        plugin.getCommand("staffmode").setExecutor(this);plugin.getCommand("staffmode").setTabCompleter(this);Bukkit.getPluginManager().registerEvents(this,plugin);
        connectDiscord();
        for(var p:players.get())if(active(p.getUniqueId()))conceal(p);
        Bukkit.getScheduler().runTaskTimer(plugin,()->{for(var p:players.get())if(active(p.getUniqueId())){conceal(p);p.sendActionBar(Component.text("Staff mode · hidden · /staffmode off to return",NookUi.COMMAND));}},20,20);
    }
    // DiscordSRV 1.30.5 reads VanishHook, not generic metadata, for lists and join/quit.
    // Keep the optional bridge isolated so NookCore can also run without DiscordSRV.
    static Object discordHook(Class<?> api,JavaPlugin owner){return java.lang.reflect.Proxy.newProxyInstance(api.getClassLoader(),new Class[]{api},(proxy,method,args)->switch(method.getName()){
        case "getPlugin"->owner;case "isEnabled"->owner.isEnabled();case "isVanished"->active(((Player)args[0]).getUniqueId());case "hook"->null;
        case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];case "toString"->"NookCore staff-mode vanish hook";
        default->throw new UnsupportedOperationException(method.getName());});}
    @SuppressWarnings("unchecked") private void connectDiscord(){
        var discord=Bukkit.getPluginManager().getPlugin("DiscordSRV");if(discord==null||!discord.isEnabled()||discordHook!=null)return;
        try{Class<?> api=Class.forName("github.scarsz.discordsrv.hooks.vanish.VanishHook",true,discord.getClass().getClassLoader());
            var hooks=(Set<Object>)discord.getClass().getMethod("getPluginHooks").invoke(discord);Object hook=discordHook(api,plugin);hooks.add(hook);discordHooks=hooks;discordHook=hook;
            plugin.getLogger().info("Staff mode connected to DiscordSRV vanish filtering.");
        }catch(Exception e){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Staff mode could not connect to DiscordSRV; entry will be blocked",e);}
    }
    private boolean discordReady(){var discord=Bukkit.getPluginManager().getPlugin("DiscordSRV");return discord==null||!discord.isEnabled()||discordHook!=null;}
    @EventHandler public void pluginEnabled(org.bukkit.event.server.PluginEnableEvent e){if(e.getPlugin().getName().equals("DiscordSRV"))connectDiscord();}
    @EventHandler public void pluginDisabled(org.bukkit.event.server.PluginDisableEvent e){if(e.getPlugin()==plugin||e.getPlugin().getName().equals("DiscordSRV")){if(discordHooks!=null&&discordHook!=null)discordHooks.remove(discordHook);discordHooks=null;discordHook=null;}}
    private void mode(Player p,GameMode mode){changing.add(p.getUniqueId());try{p.setGameMode(mode);}finally{changing.remove(p.getUniqueId());}if(p.getGameMode()!=mode)throw new IllegalArgumentException("A plugin prevented the game-mode change; staff mode remains hidden.");}
    private void refreshNames(){
        hiddenNames=players.get().stream().filter(p->active(p.getUniqueId())).map(p->p.getName().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        seeNames=players.get().stream().filter(StaffMode::sees).map(Player::getUniqueId).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private void conceal(Player p){
        refreshNames();
        p.setVisibleByDefault(false);p.setMetadata("vanished",new FixedMetadataValue(plugin,true));
        for(var viewer:players.get())if(!viewer.getUniqueId().equals(p.getUniqueId())){if(sees(viewer))viewer.showPlayer(plugin,p);else viewer.hidePlayer(plugin,p);}
        if(p.getGameMode()!=GameMode.SPECTATOR)try{mode(p,GameMode.SPECTATOR);}catch(Exception e){p.kick(Component.text("Staff mode could not keep you safely hidden. Ask an admin to check the server log."));plugin.getLogger().warning(e.getMessage());}
    }
    void enter(Player p)throws Exception{
        if(!allowed(p))throw new IllegalArgumentException("You do not have staff-mode permission.");
        if(!discordReady())throw new IllegalArgumentException("Discord visibility integration is unavailable; ask an admin to check the log.");
        if(active(p.getUniqueId()))throw new IllegalArgumentException("Staff mode is already on.");
        if(p.isDead()||p.isInsideVehicle())throw new IllegalArgumentException("Leave your vehicle and return to the world first.");
        if(p.hasMetadata("vanished") && p.getMetadata("vanished").stream().anyMatch(v->v.asBoolean()))throw new IllegalArgumentException("Another vanish system is active; disable it before using staff mode.");
        var at=p.getLocation();state.begin(p.getUniqueId(),new StaffState.Return(at.getWorld().getUID(),at.getX(),at.getY(),at.getZ(),at.getYaw(),at.getPitch(),p.getGameMode().name(),p.getAllowFlight(),p.isFlying(),p.isVisibleByDefault()));
        ACTIVE.add(p.getUniqueId());p.closeInventory();conceal(p);if(p.getGameMode()!=GameMode.SPECTATOR)throw new IllegalArgumentException("Staff entry was interrupted; your return state has been kept.");p.saveData();notice(p,"entered");p.sendMessage(NookUi.message("Staff","Hidden staff mode enabled. /staffmode off returns you."));
    }
    void leave(Player p)throws Exception{
        var saved=state.get(p.getUniqueId());if(saved==null)throw new IllegalArgumentException("Staff mode is already off.");
        World world=Bukkit.getWorld(saved.world());if(world==null)throw new IllegalArgumentException("Your return world is unavailable. You remain hidden; ask an admin for help.");
        Location to=new Location(world,saved.x(),saved.y(),saved.z(),saved.yaw(),saved.pitch());GameMode old=GameMode.valueOf(saved.mode());
        if(old!=GameMode.CREATIVE && old!=GameMode.SPECTATOR && !AdminTeleports.safeReturn(to))throw new IllegalArgumentException("Your saved return position is no longer safe. You remain hidden; ask an admin to clear the landing.");
        p.setSpectatorTarget(null);p.closeInventory();returns.put(p.getUniqueId(),to.clone());
        try{if(!p.teleport(to,PlayerTeleportEvent.TeleportCause.PLUGIN))throw new IllegalArgumentException("Return teleport was cancelled; you remain hidden.");}finally{returns.remove(p.getUniqueId());}
        try{mode(p,old);p.setAllowFlight(saved.flight());p.setFlying(saved.flight()&&saved.flying());p.setFallDistance(0);p.setVelocity(new org.bukkit.util.Vector());p.saveData();state.finish(p.getUniqueId());}
        catch(Exception e){conceal(p);throw e;}
        ACTIVE.remove(p.getUniqueId());refreshNames();p.removeMetadata("vanished",plugin);p.setVisibleByDefault(saved.visible());for(var viewer:players.get())viewer.showPlayer(plugin,p);
        notice(p,"left");p.sendMessage(NookUi.message("Staff","Staff mode off. Returned to your saved position."));
    }
    boolean returning(PlayerTeleportEvent e){return active(e.getPlayer().getUniqueId()) && e.getCause()==PlayerTeleportEvent.TeleportCause.PLUGIN && Objects.equals(returns.get(e.getPlayer().getUniqueId()),e.getTo());}
    private void notice(Player p,String action){plugin.getLogger().info("Staff mode: "+p.getName()+" "+action);for(var staff:players.get())if(sees(staff))staff.sendMessage(NookUi.message("Staff",p.getName()+" "+action+" staff mode."));}
    @EventHandler(priority=EventPriority.LOWEST) public void login(PlayerLoginEvent e){if(active(e.getPlayer().getUniqueId())){e.getPlayer().setVisibleByDefault(false);e.getPlayer().setMetadata("vanished",new FixedMetadataValue(plugin,true));}}
    @EventHandler(priority=EventPriority.LOWEST) public void join(PlayerJoinEvent e){if(active(e.getPlayer().getUniqueId())){conceal(e.getPlayer());e.joinMessage(null);}for(var p:players.get())if(active(p.getUniqueId()))conceal(p);}
    @EventHandler(priority=EventPriority.HIGHEST) public void joinMessage(PlayerJoinEvent e){if(active(e.getPlayer().getUniqueId()))e.joinMessage(null);}
    @EventHandler(priority=EventPriority.LOWEST) public void quit(PlayerQuitEvent e){if(active(e.getPlayer().getUniqueId()))e.quitMessage(null);}
    @EventHandler(priority=EventPriority.HIGHEST) public void quitMessage(PlayerQuitEvent e){quit(e);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void gameMode(PlayerGameModeChangeEvent e){if(active(e.getPlayer().getUniqueId())&&!changing.contains(e.getPlayer().getUniqueId())&&e.getNewGameMode()!=GameMode.SPECTATOR){e.setCancelled(true);e.getPlayer().sendMessage(NookUi.problem("Staff","Use /staffmode off to restore your saved mode and position."));}}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void chat(io.papermc.paper.event.player.AsyncChatEvent e){if(active(e.getPlayer().getUniqueId())){e.setCancelled(true);e.getPlayer().sendMessage(NookUi.problem("Staff","Public chat is blocked while hidden. Leave staff mode to speak publicly."));}}
    @EventHandler(priority=EventPriority.HIGHEST) public void ping(com.destroystokyo.paper.event.server.PaperServerListPingEvent e){int before=e.getNumPlayers(),removed=0;for(var it=e.iterator();it.hasNext();)if(active(it.next().getUniqueId())){it.remove();removed++;}e.setNumPlayers(Math.max(0,before-removed));e.getPlayerSample().removeIf(p->active(p.getId()));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void commands(PlayerCommandPreprocessEvent e){
        String root=e.getMessage().split("\\s+",2)[0].toLowerCase(Locale.ROOT);
        if(active(e.getPlayer().getUniqueId())&&Set.of("/me","/minecraft:me","/say","/minecraft:say").contains(root)){e.setCancelled(true);e.getPlayer().sendMessage(NookUi.problem("Staff","Public messages are blocked while hidden."));return;}
        if(Set.of("/list","/minecraft:list","/bukkit:list","/online","/who").contains(root)){e.setCancelled(true);var names=players.get().stream().filter(p->!active(p.getUniqueId())||sees(e.getPlayer())).map(Player::getName).sorted().toList();e.getPlayer().sendMessage(NookUi.message("Players",String.join(", ",names)));}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void completion(PlayerChatTabCompleteEvent e){if(!sees(e.getPlayer()))e.getTabCompletions().removeIf(name->players.get().stream().anyMatch(p->active(p.getUniqueId())&&p.getName().equalsIgnoreCase(name)));}
    @EventHandler(priority=EventPriority.HIGHEST) public void tab(org.bukkit.event.server.TabCompleteEvent e){if(e.getSender() instanceof Player p&&!sees(p))e.getCompletions().removeIf(name->hiddenNames.contains(name.toLowerCase(Locale.ROOT)));}
    @EventHandler(priority=EventPriority.HIGHEST) public void asyncTab(com.destroystokyo.paper.event.server.AsyncTabCompleteEvent e){if(e.getSender() instanceof Player p&&!seeNames.contains(p.getUniqueId()))e.completions().removeIf(c->hiddenNames.contains(c.suggestion().toLowerCase(Locale.ROOT)));}
    @EventHandler(priority=EventPriority.HIGHEST) public void advancement(PlayerAdvancementDoneEvent e){if(active(e.getPlayer().getUniqueId()))e.message(null);}
    private static final class Inspection implements InventoryHolder {Inventory inventory;public Inventory getInventory(){return inventory;}}
    private void inspect(Player p,Player target){Inspection holder=new Inspection();holder.inventory=Bukkit.createInventory(holder,45,Component.text(target.getName()+" · read-only snapshot"));for(int i=0;i<Math.min(41,target.getInventory().getSize());i++){ItemStack item=target.getInventory().getItem(i);if(item!=null)holder.inventory.setItem(i,item.clone());}p.openInventory(holder.inventory);}
    @EventHandler(priority=EventPriority.HIGHEST) public void inventory(InventoryClickEvent e){if(e.getView().getTopInventory().getHolder() instanceof Inspection)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Inspection)e.setCancelled(true);}
    @Override public boolean onCommand(CommandSender sender,Command c,String label,String[] args){
        if(!(sender instanceof Player p)){sender.sendMessage("Staff mode is an in-game command.");return true;}
        try{
            String sub=args.length==0?active(p.getUniqueId())?"off":"on":args[0].toLowerCase(Locale.ROOT);
            if(sub.equals("off")&&args.length<=1){leave(p);return true;}
            if(!allowed(p))throw new IllegalArgumentException("You do not have staff-mode permission.");
            if(sub.equals("on")&&args.length<=1){enter(p);return true;}
            if(sub.equals("list")&&args.length==1){p.sendMessage(NookUi.message("Staff","On duty: "+players.get().stream().filter(x->active(x.getUniqueId())).map(Player::getName).sorted().toList()));return true;}
            if(!Set.of("tp","inspect","spectate").contains(sub)||args.length!=2)throw new IllegalArgumentException("Use /staffmode [on|off|list|tp <player>|spectate <player>|inspect <player>].");
            if(!active(p.getUniqueId()))throw new IllegalArgumentException("Enter /staffmode first.");
            if(!p.hasPermission("nookcore.staff."+(sub.equals("inspect")?"inspect":"teleport"))&&!p.hasPermission("nookcore.admin"))throw new IllegalArgumentException("You do not have permission for that staff tool.");
            Player target=players.get().stream().filter(x->x.getName().equalsIgnoreCase(args[1])).findFirst().orElse(null);if(target==null||target==p||active(target.getUniqueId())&&!sees(p))throw new IllegalArgumentException("Choose an available online player.");
            if(sub.equals("inspect"))inspect(p,target);else{p.setSpectatorTarget(null);if(!p.teleport(target.getLocation(),PlayerTeleportEvent.TeleportCause.PLUGIN))throw new IllegalArgumentException("Teleport was cancelled; dimension restrictions still apply.");if(sub.equals("spectate"))p.setSpectatorTarget(target);}
            plugin.getLogger().info("Staff tool: "+p.getName()+" "+sub+" "+target.getName());
        }catch(IllegalArgumentException e){sender.sendMessage(NookUi.problem("Staff",e.getMessage()));}catch(Exception e){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Staff mode operation failed; keep saved return state",e);sender.sendMessage(NookUi.problem("Staff","Could not complete staff mode. Check the server log; saved return data was kept."));}return true;
    }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String label,String[] args){if(!allowed(s))return List.of();if(args.length==1)return NookUi.complete(args[0],List.of("on","off","list","tp","spectate","inspect"));if(args.length==2&&s instanceof Player viewer&&active(viewer.getUniqueId()))return NookUi.complete(args[1],players.get().stream().filter(p->!active(p.getUniqueId())||sees(viewer)).map(Player::getName).toList());return List.of();}
}
