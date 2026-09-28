import java.lang.reflect.*;
import java.sql.Connection;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Uses the isolated snapshot; checks only read-only command paths. */
public final class EconomyReportProbe extends JavaPlugin {
    private int checks;
    private void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;getLogger().info("PASS "+name);}
    private CommandSender sender(boolean admin,List<String> output){
        return (CommandSender)Proxy.newProxyInstance(getClassLoader(),new Class[]{CommandSender.class},(o,m,a)->{
            if(m.getName().equals("hasPermission") || m.getName().equals("isOp"))return admin;
            if(m.getName().equals("getName"))return "LocalReportProbe";
            if(m.getName().equals("getServer"))return Bukkit.getServer();
            if(m.getName().equals("sendMessage") && a[0] instanceof Component c)output.add(PlainTextComponentSerializer.plainText().serialize(c));
            return null;
        });
    }
    private long changes(Connection db)throws Exception {try(var s=db.createStatement();var r=s.executeQuery("SELECT total_changes()")){r.next();return r.getLong(1);}}
    @Override public void onEnable(){getServer().getScheduler().runTaskLater(this,()->{
        try{
            var core=(JavaPlugin)getServer().getPluginManager().getPlugin("NookCore");
            Field field=core.getClass().getDeclaredField("store");field.setAccessible(true);Object store=field.get(core);
            Field connection=store.getClass().getDeclaredField("db");connection.setAccessible(true);Connection db=(Connection)connection.get(store);
            long before=changes(db);List<String> output=new ArrayList<>();var staff=sender(true,output);
            core.onCommand(staff,core.getCommand("nooks"),"nooks",new String[]{"report","7"});
            check(output.stream().anyMatch(s->s.contains("Economy report")),"report command reaches staff summary");
            check(output.stream().anyMatch(s->s.contains("Current balances:")),"snapshot balances render");
            check(output.stream().anyMatch(s->s.contains("weighted") || s.contains("No delivered Nooks sales")),"historical item rows or explicit empty state render");
            check(output.stream().noneMatch(s->s.contains("unreadable item")),"existing snapshot items decode");
            output.clear();core.onCommand(sender(false,output),core.getCommand("nooks"),"nooks",new String[]{"report","7"});
            check(output.size()==1 && output.getFirst().contains("for admins"),"ordinary player receives no private report");
            output.clear();core.onCommand(staff,core.getCommand("nooks"),"nooks",new String[]{"report","0"});
            check(output.size()==1 && output.getFirst().contains("1 to 90"),"invalid interval rejected without report");
            output.clear();core.onCommand(staff,core.getCommand("nooks"),"nooks",new String[]{"report","7","extra"});
            check(output.size()==1 && output.getFirst().contains("Use /nooks report"),"extra arguments rejected");
            check(core.onTabComplete(staff,core.getCommand("nooks"),"nooks",new String[]{"report",""}).contains("7"),"staff report interval completion");
            check(core.onTabComplete(sender(false,output),core.getCommand("nooks"),"nooks",new String[]{"report",""}).isEmpty(),"ordinary player has no report completion");
            Class<?> reports=Class.forName("net.nikosnook.core.EconomyReports",true,core.getClass().getClassLoader());
            Method item=reports.getDeclaredMethod("itemName",byte[].class);item.setAccessible(true);
            check(item.invoke(null,new ItemStack(Material.DIAMOND).serializeAsBytes()).equals("diamond"),"real Paper item snapshot decodes");
            check(changes(db)==before,"report commands make no database writes");
            getLogger().info("ECONOMY_REPORT_PROBE_PASS checks="+checks);
        }catch(Throwable ex){getLogger().log(java.util.logging.Level.SEVERE,"ECONOMY_REPORT_PROBE_FAILED",ex);}
        finally{Bukkit.shutdown();}
    },80);}
}