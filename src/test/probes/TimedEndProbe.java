import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Isolated real-Paper event integration; dimension environments are proxies, not a physical portal test. */
public final class TimedEndProbe extends JavaPlugin {
    JavaPlugin core;Class<?> planType;Field planField;Object original;Player player;World overworld,end,nether;UUID id=UUID.randomUUID();boolean bypass;int checks;
    void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;getLogger().info("END_PASS "+name);}
    Object parse(boolean lock,String date)throws Exception{var m=planType.getDeclaredMethod("parse",boolean.class,String.class);m.setAccessible(true);return m.invoke(null,lock,date);}
    Object field(String name)throws Exception{var f=core.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(core);}
    @SuppressWarnings("unchecked") <T>T proxy(Class<T> c,InvocationHandler h){return (T)Proxy.newProxyInstance(getClassLoader(),new Class[]{c},h);}
    World world(World.Environment env){return proxy(World.class,(o,m,a)->switch(m.getName()){case "getEnvironment"->env;case "getName"->env.name();case "getUID"->UUID.nameUUIDFromBytes(env.name().getBytes());case "equals"->o==a[0];case "hashCode"->System.identityHashCode(o);default->null;});}
    PlayerTeleportEvent portal(World destination,PlayerTeleportEvent.TeleportCause cause,boolean cancelled)throws Exception{
        var e=new PlayerTeleportEvent(player,new Location(overworld,0,100,0),new Location(destination,0,70,0),cause);e.setCancelled(cancelled);
        core.getClass().getMethod("teleport",PlayerTeleportEvent.class).invoke(core,e);return e;
    }
    String status()throws Exception{var m=core.getClass().getDeclaredMethod("statusSnapshot");m.setAccessible(true);return m.invoke(core).toString();}
    void run()throws Exception{
        core=(JavaPlugin)getServer().getPluginManager().getPlugin("NookCore");
        planField=core.getClass().getDeclaredField("endOpening");planField.setAccessible(true);original=planField.get(core);planType=original.getClass();
        check((boolean)field("healthy"),"economy remains ready with scheduled opening");
        boolean future=System.getProperty("nook.end.phase").equals("future");
        check(status().contains(future?"End access: LOCKED until":"End access: OPEN since"),"startup reads saved "+(future?"future":"past")+" opening and reports effective state");
        overworld=world(World.Environment.NORMAL);end=world(World.Environment.THE_END);nether=world(World.Environment.NETHER);
        player=proxy(Player.class,(o,m,a)->switch(m.getName()){case "getUniqueId"->id;case "getName"->"EndProbePlayer";case "hasPermission"->bypass;case "getGameMode"->GameMode.SURVIVAL;case "getWorld"->overworld;case "isOnline"->true;case "isDead"->false;default->null;});
        check(portal(end,PlayerTeleportEvent.TeleportCause.END_PORTAL,false).isCancelled()==future,"real handler applies startup schedule to ordinary End portal");
        planField.set(core,parse(true,OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5).toString()));
        check(portal(end,PlayerTeleportEvent.TeleportCause.END_PORTAL,false).isCancelled(),"future opening blocks ordinary players");
        check(portal(end,PlayerTeleportEvent.TeleportCause.ENDER_PEARL,false).isCancelled(),"future lock also covers other End destination causes");
        check(!portal(overworld,PlayerTeleportEvent.TeleportCause.END_PORTAL,false).isCancelled(),"End lock does not prevent returning to Overworld");
        core.getConfig().set("travel.lock-nether",false);check(!portal(nether,PlayerTeleportEvent.TeleportCause.NETHER_PORTAL,false).isCancelled(),"open Nether is unchanged before End opening");
        core.getConfig().set("travel.lock-nether",true);check(portal(nether,PlayerTeleportEvent.TeleportCause.NETHER_PORTAL,false).isCancelled(),"Nether has its independent lock");core.getConfig().set("travel.lock-nether",false);
        bypass=true;check(!portal(end,PlayerTeleportEvent.TeleportCause.END_PORTAL,false).isCancelled(),"existing staff travel bypass is retained");bypass=false;
        planField.set(core,parse(true,OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1).toString()));
        check(!portal(end,PlayerTeleportEvent.TeleportCause.END_PORTAL,false).isCancelled(),"clock passing opening admits ordinary End portal without restart");
        check(!portal(end,PlayerTeleportEvent.TeleportCause.END_GATEWAY,false).isCancelled(),"End gateways work after opening");
        check(portal(end,PlayerTeleportEvent.TeleportCause.COMMAND,false).isCancelled(),"opening does not grant blocked command teleports");
        check(portal(end,PlayerTeleportEvent.TeleportCause.END_PORTAL,true).isCancelled(),"opening never uncancels another protection decision");
        planField.set(core,parse(true,""));check(portal(end,PlayerTeleportEvent.TeleportCause.END_PORTAL,false).isCancelled(),"blank schedule preserves old manual lock");
        planField.set(core,parse(false,""));check(!portal(end,PlayerTeleportEvent.TeleportCause.END_PORTAL,false).isCancelled(),"manual open stays compatible");
        planField.set(core,original);check(status().contains(future?"End access: LOCKED until":"End access: OPEN since"),"status remains correct after test-only state restoration");
        getLogger().info("END_COMPLETE checks="+checks);
    }
    public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"END_FAILED",e);}finally{Bukkit.shutdown();}},60);}
}
