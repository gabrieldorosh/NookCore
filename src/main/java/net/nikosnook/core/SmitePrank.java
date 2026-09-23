package net.nikosnook.core;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.*;

/** Particle-only lightning. No lightning or firework entity is created. */
final class SmitePrank implements CommandExecutor,TabCompleter,Listener {
    private final Map<UUID,Long> cooldowns=new HashMap<>();
    private final LongSupplier clock;
    private final Consumer<Player> effect;
    private final Consumer<Exception> visualFailure;
    SmitePrank(JavaPlugin plugin){
        this(System::currentTimeMillis,SmitePrank::particles,e->plugin.getLogger().warning("Smite visual failed: "+e.getClass().getSimpleName()));
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
        if(until!=null){sender.sendMessage(NookUi.message("NookCore","The heavens need "+((until-now+999)/1000)+" more seconds."));return true;}
        cooldowns.put(player.getUniqueId(),now+30_000);
        // The argument is intentionally never resolved to another player.
        try{effect.accept(player);}catch(RuntimeException e){visualFailure.accept(e);}
        player.damage(4.0);
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
    private static void particles(Player player){
        var base=player.getLocation();var world=player.getWorld();
        var dust=new Particle.DustOptions(Color.fromRGB(225,220,255),1.25f);
        // Zig-zag bolt made exclusively of cosmetic particles, ending at the caller.
        for(int i=0;i<=48;i++){
            double height=8.0-i/6.0,offset=(1.0-i/48.0)*Math.sin(i*0.65)*0.45;
            world.spawnParticle(Particle.DUST,base.clone().add(offset,height,offset*0.45),1,0,0,0,0,dust);
        }
        world.spawnParticle(Particle.ELECTRIC_SPARK,base.clone().add(0,1,0),20,0.25,0.6,0.25,0.03);
        world.playSound(base,Sound.ENTITY_LIGHTNING_BOLT_IMPACT,0.6f,1.2f);
    }
}
