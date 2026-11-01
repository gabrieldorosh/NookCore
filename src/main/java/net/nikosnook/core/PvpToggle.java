package net.nikosnook.core;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Bilateral consent layered over vanilla and other plugins' protections. */
final class PvpToggle implements Listener,CommandExecutor,TabCompleter {
    private final JavaPlugin plugin;private final BooleanSupplier healthy;private PvpSettings settings;
    private final Map<UUID,Long> lastNotice=new HashMap<>();
    private static final Set<PotionEffectType> HARMFUL=Set.of(PotionEffectType.POISON,PotionEffectType.WITHER,PotionEffectType.INSTANT_DAMAGE,
        PotionEffectType.SLOWNESS,PotionEffectType.WEAKNESS,PotionEffectType.BLINDNESS,PotionEffectType.HUNGER,PotionEffectType.NAUSEA,
        PotionEffectType.MINING_FATIGUE,PotionEffectType.LEVITATION,PotionEffectType.UNLUCK,PotionEffectType.DARKNESS,
        PotionEffectType.INFESTED,PotionEffectType.OOZING,PotionEffectType.WEAVING,PotionEffectType.WIND_CHARGED);
    PvpToggle(JavaPlugin plugin,BooleanSupplier healthy){
        this.plugin=plugin;this.healthy=healthy;
        try{int seconds=(int)TravelRules.bounded(plugin.getConfig().getInt("pvp.combat-lock-seconds",15),1,300,"PvP combat lock");
            settings=new PvpSettings(plugin.getDataFolder().toPath().resolve("pvp.properties"),System::currentTimeMillis,seconds*1000L);
        }catch(Exception e){plugin.getLogger().log(java.util.logging.Level.SEVERE,"PvP preferences could not load; player combat is blocked and the file was preserved",e);}
        plugin.getCommand("pvp").setExecutor(this);plugin.getCommand("pvp").setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    String status(){return "PvP: "+(settings==null?"PAUSED; preferences unavailable":"both players must opt in; default off");}
    static UUID attacker(Entity entity){
        if(entity instanceof Player p)return p.getUniqueId();
        if(entity instanceof Projectile projectile && projectile.getShooter() instanceof Player p)return p.getUniqueId();
        if(entity instanceof TNTPrimed tnt && tnt.getSource()!=null)return attacker(tnt.getSource());
        if(entity instanceof Tameable pet && pet.getOwner()!=null)return pet.getOwner().getUniqueId();
        if(entity instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player p)return p.getUniqueId();
        return null;
    }
    private static UUID attacker(EntityDamageByEntityEvent event){
        UUID direct=attacker(event.getDamager());
        return direct!=null?direct:attacker(event.getDamageSource().getCausingEntity());
    }
    private boolean allowed(UUID attacker,Player target){return attacker==null || attacker.equals(target.getUniqueId()) || settings!=null && healthy.getAsBoolean() && settings.allows(attacker,target.getUniqueId());}
    private void notice(UUID attacker){
        long now=System.currentTimeMillis();if(now-lastNotice.getOrDefault(attacker,0L)<2000)return;lastNotice.put(attacker,now);
        var player=Bukkit.getPlayer(attacker);if(player!=null)player.sendActionBar(NookUi.text("Both players must enable /pvp before fighting."));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageByEntityEvent e){
        if(e.getEntity() instanceof Player target){UUID source=attacker(e);if(!allowed(source,target)){e.setCancelled(true);notice(source);}}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void combat(EntityDamageByEntityEvent e){
        if(settings!=null && e.getFinalDamage()>0 && e.getEntity() instanceof Player target){UUID source=attacker(e);if(source!=null && allowed(source,target))settings.combat(source,target.getUniqueId());}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fire(EntityCombustByEntityEvent e){
        if(e.getEntity() instanceof Player target){UUID source=attacker(e.getCombuster());if(!allowed(source,target))e.setCancelled(true);}
    }
    private static boolean harmful(Collection<PotionEffect> effects){return effects.stream().anyMatch(e->HARMFUL.contains(e.getType()));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void splash(PotionSplashEvent e){
        UUID source=attacker(e.getPotion());if(source==null || !harmful(e.getPotion().getEffects()))return;
        for(var entity:e.getAffectedEntities())if(entity instanceof Player p && !allowed(source,p))e.setIntensity(entity,0);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void splashCombat(PotionSplashEvent e){
        UUID source=attacker(e.getPotion());if(settings==null || source==null || !harmful(e.getPotion().getEffects()))return;
        for(var entity:e.getAffectedEntities())if(entity instanceof Player p && e.getIntensity(p)>0 && allowed(source,p))settings.combat(source,p.getUniqueId());
    }
    private static boolean harmful(AreaEffectCloud cloud){return harmful(cloud.getCustomEffects()) || cloud.getBasePotionType()!=null && harmful(cloud.getBasePotionType().getPotionEffects());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void cloud(AreaEffectCloudApplyEvent e){
        UUID source=attacker(e.getEntity());if(source!=null && harmful(e.getEntity()))e.getAffectedEntities().removeIf(entity->entity instanceof Player p && !allowed(source,p));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void cloudCombat(AreaEffectCloudApplyEvent e){
        UUID source=attacker(e.getEntity());if(settings==null || source==null || !harmful(e.getEntity()))return;
        for(var entity:e.getAffectedEntities())if(entity instanceof Player p && allowed(source,p))settings.combat(source,p.getUniqueId());
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player p)){sender.sendMessage(NookUi.text("Use /pvp in game to change your own preference."));return true;}
        try{
            if(settings==null)throw new IllegalArgumentException("PvP is temporarily unavailable. Ask an admin to check the log.");
            String sub=args.length==0?(settings.enabled(p.getUniqueId())?"off":"on"):args[0].toLowerCase(Locale.ROOT);
            if(args.length>1 || !Set.of("on","off","status").contains(sub))throw new IllegalArgumentException("/pvp [on|off|status]");
            if(!sub.equals("status")){
                boolean value=sub.equals("on");
                if(value && (!healthy.getAsBoolean() || !p.hasPermission("nookcore.pvp")))throw new IllegalArgumentException("You cannot enable PvP right now.");
                if(value && !p.getWorld().getPVP())throw new IllegalArgumentException("PvP is disabled in this world.");
                settings.set(p.getUniqueId(),value);
            }
            p.sendMessage(NookUi.message("PvP",settings.enabled(p.getUniqueId())?"PvP enabled. You can fight other players who also enable it.":"PvP disabled. Both players must enable /pvp before fighting."));
        }catch(IllegalArgumentException e){sender.sendMessage(NookUi.problem("PvP",e.getMessage()));}
        catch(Exception e){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Could not save PvP preference; existing setting kept",e);sender.sendMessage(NookUi.problem("PvP","Could not save your PvP setting. Your existing preference was kept."));}
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command c,String l,String[] args){return args.length==1?NookUi.complete(args[0],List.of("on","off","status")):List.of();}
}
