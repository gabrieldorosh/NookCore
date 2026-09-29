import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
public final class MetricsProbe extends JavaPlugin {
 private int checks;private JavaPlugin core;private Object metrics;private Connection db;private long before;private final List<String> output=new ArrayList<>();
 private void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;getLogger().info("PASS "+name);}
 private CommandSender sender(boolean admin,List<String> lines){return (CommandSender)Proxy.newProxyInstance(getClassLoader(),new Class[]{CommandSender.class},(o,m,a)->switch(m.getName()){
  case "hasPermission","isOp"->admin;case "getName"->"MetricsProbe";case "getServer"->Bukkit.getServer();case "sendMessage"->{if(a[0] instanceof Component c)lines.add(PlainTextComponentSerializer.plainText().serialize(c));yield null;}default->null;
 });}
 private Object field(Object target,String name)throws Exception {var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 private long changes()throws Exception {try(var s=db.createStatement();var r=s.executeQuery("SELECT total_changes()")){r.next();return r.getLong(1);}}
 private void fail(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"METRICS_PROBE_FAILED",e);Bukkit.shutdown();}
 @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{
  core=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NookCore");check(core!=null && core.isEnabled(),"combined NookCore enables");metrics=field(core,"economyMetrics");check(metrics!=null,"private collector initialises");db=(Connection)field(field(core,"store"),"db");before=changes();
  core.onCommand(sender(false,output),core.getCommand("nooks"),"nooks",new String[]{"metrics"});check(output.size()==1 && output.getFirst().contains("for admins"),"ordinary player receives no metrics");output.clear();
  check(core.onTabComplete(sender(false,output),core.getCommand("nooks"),"nooks",new String[]{"metrics",""}).isEmpty(),"ordinary player gets no metrics completions");
  core.onCommand(sender(true,output),core.getCommand("nooks"),"nooks",new String[]{"metrics","0"});check(output.size()==1 && output.getFirst().contains("1 to 90"),"invalid period rejected");output.clear();
  core.onCommand(sender(true,output),core.getCommand("nooks"),"nooks",new String[]{"report","7"});check(output.stream().anyMatch(s->s.contains("Economy report")),"existing economy report still works");output.clear();
  var count=metrics.getClass().getDeclaredMethod("count",UUID.class,String.class,long.class);count.setAccessible(true);var id=UUID.fromString("00000000-0000-0000-0000-000000000082");count.invoke(metrics,id,"active_seconds",60L);count.invoke(metrics,id,"survival_seconds",120L);count.invoke(metrics,id,"ore_blocks:DIAMOND_ORE",3L);
  core.onCommand(sender(true,output),core.getCommand("nooks"),"nooks",new String[]{"metrics","export","1"});
  poll(0);
 }catch(Throwable e){fail(e);}},80);}
 private void poll(int attempt){Bukkit.getScheduler().runTaskLater(this,()->{try{
  if(output.stream().noneMatch(s->s.contains("Saved private export:"))){if(attempt>=40)throw new AssertionError("async export timeout: "+output);poll(attempt+1);return;}
  check(output.stream().anyMatch(s->s.contains("Private activity metrics")),"asynchronous staff summary returns");
  check(output.stream().anyMatch(s->s.contains("ore blocks: 3")),"flushed harvest counters appear");
  check(output.stream().anyMatch(s->s.contains("Do not divide")),"incomplete observation caveat visible");
  try(var files=java.nio.file.Files.list(core.getDataFolder().toPath().resolve("reports"))){var csv=files.filter(p->p.toString().endsWith(".csv")).toList();check(!csv.isEmpty(),"staff CSV exists");check(java.nio.file.Files.readString(csv.getLast()).contains("ore_blocks:DIAMOND_ORE,3"),"export retains material breakdown");}
  check(changes()==before,"metrics and reports leave economy database unchanged");
  getLogger().info("METRICS_PROBE_PASS checks="+checks);Bukkit.shutdown();
 }catch(Throwable e){fail(e);}},5);}
}
