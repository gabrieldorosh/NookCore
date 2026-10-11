import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class AdminStatusProbe extends JavaPlugin {
 JavaPlugin core;int checks;List<String> messages=new ArrayList<>();
 void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;getLogger().info("STATUS_PASS "+label);}
 Field field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f;}
 CommandSender sender(boolean admin){return (CommandSender)Proxy.newProxyInstance(getClassLoader(),new Class[]{CommandSender.class},(o,m,a)->switch(m.getName()){
  case "hasPermission","isPermissionSet","isOp"->admin;case "getName"->admin?"StatusAdmin":"Regular";case "getServer"->Bukkit.getServer();case "getEffectivePermissions"->Set.of();
  case "sendMessage"->{for(var value:a){if(value instanceof Component c)messages.add(PlainTextComponentSerializer.plainText().serialize(c));else if(value instanceof String s)messages.add(s);else if(value instanceof String[] arr)messages.addAll(Arrays.asList(arr));}yield null;}
  case "hashCode"->123;case "equals"->o==a[0];default->null;
 });}
 void status(boolean admin,String...args){messages.clear();core.getCommand("nookadmin").execute(sender(admin),"nookadmin",args);}
 long changes(Connection db)throws Exception{try(var s=db.createStatement();var r=s.executeQuery("SELECT total_changes()")){r.next();return r.getLong(1);}}
 void run()throws Exception{
  core=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NookCore");check(core!=null&&core.isEnabled(),"plugin starts");Object store=field(core,"store").get(core);Connection db=(Connection)field(store,"db").get(store);long before=changes(db);
  status(false,"status");check(messages.stream().noneMatch(s->s.contains("Source:")||s.contains("reviews:")),"ordinary user receives no diagnostics");
  status(true,"status");String output=String.join("\n",messages);check(output.contains("Source:")&&!output.contains("unrecorded"),"packaged build identity is recorded");check(output.contains("Runtime-source SHA256:"),"runtime source fingerprint reported");check(output.contains("Shop inventory reviews: 0"),"empty review queue reported without mutation");check(output.contains("Quests:")&&output.contains("Metrics:"),"quest and metrics state reported");getLogger().info("STATUS_SAMPLE "+output.replace('\n','|'));
  messages.clear();core.getCommand("nookadmin").execute(sender(true),"nookadmin",new String[]{"help"});check(messages.toString().contains("/nookadmin quests")&&messages.toString().contains("/nookadmin shops"),"admin help is grouped");
  messages.clear();core.getCommand("nookadmin").execute(sender(false),"nookadmin",new String[]{"shops","review"});check(messages.stream().noneMatch(s->s.contains("receipt")||s.contains("stock")),"nested admin route denies ordinary users");
  messages.clear();core.getCommand("nookadmin").execute(sender(true),"nookadmin",new String[]{"quests","contributions","collect","stone"});check(messages.toString().contains("<quantity>"),"community collection requires explicit quantity");
  messages.clear();core.getCommand("nookshops").execute(sender(true),"nookshops",new String[]{"help"});check(!messages.toString().contains("/nookshops collect")&&!messages.toString().contains("/nookshops preview"),"retired shop commands absent from help");
  check(core.getCommand("list")!=null,"visible-player list is registered for command discovery");
  status(true,"status","repair");check(messages.size()==1&&messages.getFirst().contains("Use /nookadmin status"),"extra arguments cannot trigger repair");
  field(core,"healthy").set(core,false);status(true,"status");check(messages.toString().contains("PAUSED"),"status works while economy is paused");check(!(boolean)field(core,"healthy").get(core),"diagnostics do not resume economy");
  Object quests=field(core,"weeklyQuests").get(core);var enabled=field(quests,"enabled");boolean oldEnabled=enabled.getBoolean(quests);enabled.setBoolean(quests,true);var week=field(quests,"week");Object original=week.get(quests);week.set(quests,null);status(true,"status");check(messages.toString().contains("active week not loaded")&&week.get(quests)==null,"quest status does not create a weekly rotation");week.set(quests,original);enabled.setBoolean(quests,oldEnabled);
  check(changes(db)==before,"status leaves SQLite total changes unchanged");
  var blocker=core.getClass().getDeclaredMethod("blockStartup",String.class);blocker.setAccessible(true);blocker.invoke(core,"PROBE_SECRET_STARTUP_DETAIL");
  status(true,"status");check(messages.toString().contains("Startup blocked")&&!messages.toString().contains("PROBE_SECRET"),"startup-blocked command remains available and redacted");
  field(core,"store").set(core,null);status(true,"status");check(messages.toString().contains("store not initialised"),"missing store is reported safely");field(core,"store").set(core,store);
  status(false,"status");check(messages.stream().noneMatch(s->s.contains("Source:")),"startup-blocked diagnostics remain private");
  getLogger().info("STATUS_COMPLETE checks="+checks);
 }
 public void onEnable(){Bukkit.getScheduler().runTask(this,()->{try{run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"STATUS_FAILED",e);}finally{Bukkit.shutdown();}});}
}
