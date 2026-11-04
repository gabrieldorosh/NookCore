import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Main-thread opt-in probe on an already built local road. No block/config/store changes. */
public final class NikoRoamingChecks {
    private static int passed;
    private static void check(boolean good,String label){if(!good)throw new AssertionError(label);passed++;}
    private static Object call(Class<?> type,Object receiver,String name,Object... args)throws Exception {
        for(var method:type.getDeclaredMethods())if(method.getName().equals(name) && method.getParameterCount()==args.length){
            method.setAccessible(true);try{return method.invoke(receiver,args);}catch(InvocationTargetException ex){
                if(ex.getCause() instanceof Exception error)throw error;throw new AssertionError(ex.getCause());
            }
        }
        throw new NoSuchMethodException(name);
    }
    private static Map<String,Object> waypoint(Location at){return Map.of("world",at.getWorld().getName(),"x",at.getX(),"y",at.getY(),"z",at.getZ());}
    public static int run(JavaPlugin core,Location home)throws Exception {
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Run on the main server thread");
        if(home==null || home.getWorld()==null)throw new IllegalArgumentException("Use a loaded local test world HOME");
        passed=0;
        var type=Class.forName("net.nikosnook.core.NikoRoaming",true,core.getClass().getClassLoader());
        var warnings=new ArrayList<String>();var config=new YamlConfiguration();
        config.set("niko-roaming.enabled",true);
        config.set("niko-roaming.waypoints",List.of(waypoint(home),waypoint(home.clone().add(2,0,0)),waypoint(home.clone().add(2,0,2)),waypoint(home.clone().add(0,0,2))));
        Object roaming=call(type,null,"fancy",config,(Consumer<String>)warnings::add);
        Object homePoint=call(type,null,"point",home);
        var manager=FancyNpcsPlugin.get().getNpcManager();
        if(!manager.isLoaded())throw new IllegalStateException("Wait until FancyNpcs has loaded");
        var data=new NpcData("niko_roaming_probe_"+UUID.randomUUID(),new UUID(0,99),home.clone());
        data.setDisplayName("Niko roaming probe").setCollidable(false).setShowInTab(false).setTurnToPlayer(false);
        Npc npc=FancyNpcsPlugin.get().getNpcAdapter().apply(data);npc.setSaveToFile(false);
        try{
            npc.create();manager.registerNpc(npc);npc.spawnForAll();
            call(type,roaming,"update",npc,homePoint,false);
            check(warnings.isEmpty(),"loaded toy loop must pass: "+warnings);
            check(call(type,roaming,"status").equals("flat loop"),"route is active");
            for(int i=0;i<20;i++)call(type,roaming,"update",npc,homePoint,false);
            check(Math.abs(data.getLocation().getX()-home.getX()-1.2)<1e-6,"exact adapter accepts twenty gentle moves");
            check(Math.abs(data.getLocation().getY()-home.getY())<1e-6,"flat height is unchanged");
            check(!data.isCollidable() && !npc.isSaveToFile(),"packet NPC stays non-colliding and unsaved");
            var paused=data.getLocation().clone();
            for(int i=0;i<100;i++)call(type,roaming,"update",npc,homePoint,true);
            check(data.getLocation().equals(paused),"player/menu hold sends no movement");
            call(type,roaming,"interact");for(int i=0;i<40;i++)call(type,roaming,"update",npc,homePoint,false);
            check(data.getLocation().equals(paused),"real-click hold lasts two seconds");
            for(int i=0;i<14;i++)call(type,roaming,"update",npc,homePoint,false);
            check(Math.abs(data.getLocation().getX()-home.getX()-2)<1e-6,"first point reached without overshoot");
            paused=data.getLocation().clone();for(int i=0;i<40;i++)call(type,roaming,"update",npc,homePoint,false);
            check(data.getLocation().equals(paused),"point stop holds forty ticks");
            call(type,roaming,"forget");data.setLocation(home.clone());
            call(type,roaming,"update",npc,homePoint,false);call(type,roaming,"update",npc,homePoint,false);
            check(Math.abs(data.getLocation().getX()-home.getX()-.06)<1e-6,"recreated state starts at saved HOME");
            data.setLocation(home.clone().add(.5,0,.5));paused=data.getLocation().clone();
            call(type,roaming,"update",npc,homePoint,false);
            check(warnings.size()==1 && call(type,roaming,"status").toString().startsWith("disabled"),"external movement disables only roaming once");
            check(data.getLocation().equals(paused),"external movement never triggers a route shortcut");
            call(type,roaming,"update",npc,homePoint,false);check(warnings.size()==1,"disabled provider is not retried");
            return passed;
        }finally{npc.removeForAll();manager.removeNpc(npc);}
    }
}
