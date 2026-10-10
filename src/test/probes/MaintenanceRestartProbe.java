import org.bukkit.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;

public class MaintenanceRestartProbe extends JavaPlugin {
 int checks;
 void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;getLogger().info("MAINTENANCE_PASS "+text);}
 Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 public void onEnable(){Bukkit.getScheduler().runTask(this,()->{try{
  var core=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NookCore");check(core!=null && core.isEnabled(),"plugin enables after normal restart");
  boolean disabled=core.getConfig().getBoolean("maintenance-probe-disabled",false);var metrics=field(core,"economyMetrics");check(metrics!=null,"metrics command service initialises");
  try(var db=DriverManager.getConnection("jdbc:sqlite:"+core.getDataFolder().toPath().resolve("activity.db"));var s=db.createStatement();var r=s.executeQuery("SELECT SUM(amount) FROM activity WHERE player='00000000-0000-0000-0000-000000000082' AND metric='ore_blocks:DIAMOND_ORE'")){
   r.next();check(r.getLong(1)==(disabled?5:3),disabled?"shutdown flush persists unexported counters":"exported counters survive server restart");
  }
  check((Boolean)field(core,"healthy"),"economy remains healthy");
  if(disabled){
   check(field(metrics,"activity")==null,"disabled collection opens no activity writer");
   Object chat=field(core,"chat");check(!(Boolean)field(chat,"afkEnabled"),"AFK display switch remains independently configurable");
  }else{
   var count=metrics.getClass().getDeclaredMethod("count",UUID.class,String.class,long.class);count.setAccessible(true);count.invoke(metrics,UUID.fromString("00000000-0000-0000-0000-000000000082"),"ore_blocks:DIAMOND_ORE",2L);
   check(!((Map<?,?>)field(metrics,"pending")).isEmpty(),"pending counters will flush during clean shutdown");
  }
  getLogger().info("MAINTENANCE_COMPLETE checks="+checks);
 }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"MAINTENANCE_FAILED",e);}finally{Bukkit.shutdown();}});}
}
