import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

public class PvpProbe extends JavaPlugin {
 Object pvp,settings;int checks;World world;JavaPlugin core;
 Object call(Object object,String name,Object...args)throws Exception{
  for(var method:object.getClass().getDeclaredMethods())if(method.getName().equals(name)&&method.getParameterCount()==args.length){method.setAccessible(true);try{return method.invoke(object,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;throw e;}}
  throw new NoSuchMethodException(name);
 }
 Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
 void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;getLogger().info("PVP_PASS "+label);}
 @SuppressWarnings("unchecked") <T>T mock(Class<T> api,Function<String,Object> values){return (T)Proxy.newProxyInstance(getClassLoader(),new Class[]{api},(o,m,a)->{
  if(m.getName().equals("hashCode"))return System.identityHashCode(o);if(m.getName().equals("equals"))return o==a[0];
  Object value=values.apply(m.getName());if(value!=null)return value;var t=m.getReturnType();if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;if(t==double.class)return 0D;if(t==float.class)return 0F;return null;
 });}
 class User {
  UUID id=UUID.randomUUID();String name;List<Object> messages=new ArrayList<>();Player player;
  User(String name){this.name=name;player=(Player)Proxy.newProxyInstance(getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
   case "getUniqueId"->id;case "getName"->name;case "getWorld"->world;case "getLocation"->new Location(world,.5,121,.5);case "hasPermission"->true;case "isOnline"->true;
   case "sendMessage","sendActionBar"->{messages.add(a[0]);yield null;}case "hashCode"->id.hashCode();case "equals"->o==a[0];
   default->{var t=m.getReturnType();if(t==boolean.class)yield false;if(t==int.class)yield 0;if(t==double.class)yield 0D;if(t==float.class)yield 0F;yield null;}
  });}
 }
 org.bukkit.damage.DamageSource source(Entity actor){return mock(org.bukkit.damage.DamageSource.class,name->name.equals("getCausingEntity")||name.equals("getDirectEntity")?actor:null);}
 EntityDamageByEntityEvent hit(Entity actor,Player victim){return new EntityDamageByEntityEvent(actor,victim,EntityDamageEvent.DamageCause.ENTITY_ATTACK,source(actor),3);}
 boolean enabled(User u)throws Exception{return (boolean)call(settings,"enabled",u.id);}
 void set(User u,boolean value)throws Exception{((Map<?,?>)field(settings,"lockedUntil")).clear();call(settings,"set",u.id,value);}
 void run()throws Exception{
  core=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NookCore");pvp=field(core,"pvp");settings=field(pvp,"settings");world=Bukkit.getWorlds().getFirst();world.setPVP(true);
  User a=new User("PvpA"),b=new User("PvpB");check(!enabled(a)&&!enabled(b),"new players default to PvP off");
  var denied=hit(a.player,b.player);call(pvp,"damage",denied);check(denied.isCancelled(),"direct player damage blocked when both opted out");
  call(pvp,"onCommand",a.player,core.getCommand("pvp"),"pvp",new String[]{"on"});check(enabled(a),"pvp on enables only the sender");
  denied=hit(a.player,b.player);call(pvp,"damage",denied);check(denied.isCancelled()&&!enabled(b),"one player opting in does not enable damage to the other");
  call(pvp,"onCommand",b.player,core.getCommand("pvp"),"pvp",new String[]{"on"});var allowed=hit(a.player,b.player);call(pvp,"damage",allowed);check(!allowed.isCancelled(),"direct combat allowed only after both opt in");
  var protectedHit=hit(a.player,b.player);protectedHit.setCancelled(true);call(pvp,"damage",protectedHit);check(protectedHit.isCancelled(),"PvP consent never uncancels another protection");
  call(pvp,"combat",allowed);call(pvp,"onCommand",a.player,core.getCommand("pvp"),"pvp",new String[]{"off"});check(enabled(a)&&a.messages.toString().contains("after combat"),"successful damage prevents immediate PvP disable");
  call(pvp,"onCommand",b.player,core.getCommand("pvp"),"pvp",new String[]{"off"});check(enabled(b),"victim receives the same combat lock");
  set(b,false);
  Arrow arrow=mock(Arrow.class,n->n.equals("getShooter")?a.player:null);var arrowHit=hit(arrow,b.player);call(pvp,"damage",arrowHit);check(arrowHit.isCancelled(),"arrows use their shooter's opt-in preference");
  TNTPrimed tnt=mock(TNTPrimed.class,n->n.equals("getSource")?a.player:null);var blast=hit(tnt,b.player);call(pvp,"damage",blast);check(blast.isCancelled(),"player-owned TNT cannot damage an opted-out player");
  Wolf wolf=mock(Wolf.class,n->n.equals("getOwner")?a.player:null);var bite=hit(wolf,b.player);call(pvp,"damage",bite);check(bite.isCancelled(),"tamed pets use their owner's consent");
  Cow cow=mock(Cow.class,n->null);var environmental=hit(cow,b.player);call(pvp,"damage",environmental);check(!environmental.isCancelled(),"non-player creature damage remains unchanged");
  EnderCrystal crystal=mock(EnderCrystal.class,n->null);var crystalHit=new EntityDamageByEntityEvent(crystal,b.player,EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,source(a.player),3);call(pvp,"damage",crystalHit);check(crystalHit.isCancelled(),"indirect explosion uses the damage source's responsible player");
  var self=hit(a.player,a.player);call(pvp,"damage",self);check(!self.isCancelled(),"self damage is not treated as PvP");
  var fire=new EntityCombustByEntityEvent(arrow,b.player,5);call(pvp,"fire",fire);check(fire.isCancelled(),"projectile fire cannot bypass opted-out protection");
  ThrownPotion poison=mock(ThrownPotion.class,n->switch(n){case "getShooter"->a.player;case "getEffects"->List.of(new PotionEffect(PotionEffectType.POISON,200,0));default->null;});
  var splash=new PotionSplashEvent(poison,new HashMap<>(Map.of(b.player,1D)));call(pvp,"splash",splash);check(splash.getIntensity(b.player)==0,"harmful splash effect removed for opted-out target");
  ThrownPotion healing=mock(ThrownPotion.class,n->switch(n){case "getShooter"->a.player;case "getEffects"->List.of(new PotionEffect(PotionEffectType.REGENERATION,200,0));default->null;});
  var beneficial=new PotionSplashEvent(healing,new HashMap<>(Map.of(b.player,1D)));call(pvp,"splash",beneficial);check(beneficial.getIntensity(b.player)==1,"beneficial splash potions remain available");
  AreaEffectCloud cloud=mock(AreaEffectCloud.class,n->switch(n){case "getSource"->a.player;case "getCustomEffects"->List.of(new PotionEffect(PotionEffectType.POISON,200,0));default->null;});
  var lingering=new AreaEffectCloudApplyEvent(cloud,new ArrayList<>(List.of(b.player)));call(pvp,"cloud",lingering);check(lingering.getAffectedEntities().isEmpty(),"harmful lingering potion excludes opted-out player");
  var snapshot=new java.util.Properties();try(var input=java.nio.file.Files.newInputStream(core.getDataFolder().toPath().resolve("pvp.properties"))){snapshot.load(input);}check(snapshot.getProperty(a.id.toString()).equals("true")&&!snapshot.containsKey(b.id.toString()),"preferences written independently of the economy database");
  call(pvp,"onCommand",b.player,core.getCommand("pvp"),"pvp",new String[]{"not-a-subcommand"});check(!enabled(b)&&b.messages.toString().contains("/pvp [on|off|status]"),"invalid syntax does not change preference");
  check(core.getCommand("pvp").getExecutor().getClass().getSimpleName().equals("PvpToggle"),"pvp command is registered with the correct executor");
  getLogger().info("PVP_COMPLETE checks="+checks);
 }
 @Override public void onEnable(){Bukkit.getScheduler().runTask(this,()->{try{run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"PVP_FAILED",e);}finally{Bukkit.shutdown();}});}
}
