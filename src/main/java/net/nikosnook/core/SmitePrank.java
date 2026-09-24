package net.nikosnook.core;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.*;

/** Paper visual-only lightning; damage is applied separately to the caller. */
final class SmitePrank implements CommandExecutor,TabCompleter,Listener {
    private final Map<UUID,Long> cooldowns=new HashMap<>();
    private final Set<UUID> damaging=new HashSet<>();
    private final LongSupplier clock;
    private final Consumer<Player> effect;
    private final Consumer<Exception> visualFailure;
    SmitePrank(JavaPlugin plugin){
        this(System::currentTimeMillis,SmitePrank::lightning,e->plugin.getLogger().warning("Smite visual failed: "+e.getClass().getSimpleName()));
        var command=Objects.requireNonNull(plugin.getCommand("smite"));command.setExecutor(this);command.setTabCompleter(this);
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
    }
    SmitePrank(LongSupplier clock,Consumer<Player> effect,Consumer<Exception> visualFailure){this.clock=clock;this.effect=effect;this.visualFailure=visualFailure;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player player)){sender.sendMessage(NookUi.message("NookCore","Use /smite <player> in game."));return true;}
        if(args.length!=1){sender.sendMessage(NookUi.message("NookCore","Use /smite <player>."));return true;}
        if(player.isDead()){sender.sendMessage(NookUi.message("NookCore","Wait until you have respawned."));return true;}
        long now=clock.getAsLong();cooldowns.values().removeIf(until->until<=now);
        Long until=cooldowns.get(player.getUniqueId());
        if(until!=null){sender.sendMessage(NookUi.message("NookCore","The heavens need another "+((until-now+999)/1000)+" seconds."));return true;}
        cooldowns.put(player.getUniqueId(),now+30_000);
        // The argument is intentionally never resolved to another player.
        try{effect.accept(player);}catch(RuntimeException e){visualFailure.accept(e);}
        damaging.add(player.getUniqueId());
        try{player.damage(4.0);}finally{damaging.remove(player.getUniqueId());}
        sender.sendMessage(NookUi.message("NookCore","The heavens have terrible aim."));return true;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void command(PlayerCommandPreprocessEvent event){
        String[] words=event.getMessage().trim().split("\\s+");
        String root=words[0].toLowerCase(Locale.ROOT);
        if(!Set.of("/smite","/nookcore:smite","/essentials:smite").contains(root))return;
        event.setCancelled(true);onCommand(event.getPlayer(),null,"smite",Arrays.copyOfRange(words,1,words.length));
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        return args.length==1?NookUi.complete(args[0],Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()):List.of();
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void death(org.bukkit.event.entity.PlayerDeathEvent event){
        event.deathMessage(deathMessage(event.getEntity(),event.deathMessage()));
    }
    net.kyori.adventure.text.Component deathMessage(Player player,net.kyori.adventure.text.Component original){
        if(original==null || !damaging.contains(player.getUniqueId()))return original;
        return NookUi.name(player.getUniqueId(),player.getName()).append(NookUi.text(" tried to play god and missed."));
    }
    static void lightning(Player player){
        // The pinned Paper build's isEffect flag skips fire, rods, copper and entity strikes.
        player.getWorld().strikeLightningEffect(player.getLocation());
    }
}
