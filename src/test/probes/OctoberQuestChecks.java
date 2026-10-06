import java.lang.reflect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.*;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

final class OctoberQuestChecks {
    static int checks;
    static void check(boolean ok,String name,JavaPlugin log){if(!ok)throw new AssertionError(name);checks++;log.getLogger().info("PASS "+name);}
    static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static Object invoke(Object o,String n,Class<?>[] types,Object...args)throws Exception{var m=o.getClass().getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(o,args);}
    @SuppressWarnings("unchecked") static <T>T proxy(Class<T> c,InvocationHandler h){return (T)Proxy.newProxyInstance(OctoberQuestChecks.class.getClassLoader(),new Class[]{c},h);}
    static void run(JavaPlugin log)throws Exception{
        var core=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NookCore");Object store=field(core,"store");
        Object q=HandlerList.getRegisteredListeners(core).stream().map(r->r.getListener()).filter(l->l.getClass().getSimpleName().equals("WeeklyQuests")).findFirst().orElseThrow();
        List<?> goals=(List<?>)field(q,"goals");long reward=0;for(Object g:goals)reward+=(long)invoke(g,"reward",new Class[]{});
        check(goals.size()==7 && reward==12000,"selected seven goals load with 120 Nooks total",log);
        UUID id=UUID.randomUUID();World world=Bukkit.getWorlds().getFirst();List<String> messages=new ArrayList<>();
        var villager=world.spawn(new Location(world,2,100,2),Villager.class);villager.setAI(false);
        MerchantInventory inventory=proxy(MerchantInventory.class,(o,m,a)->m.getName().equals("getMerchant")?villager:null);
        InventoryView view=proxy(InventoryView.class,(o,m,a)->m.getName().equals("getTopInventory")?inventory:null);
        Player p=proxy(Player.class,(o,m,a)->switch(m.getName()){
            case "getUniqueId"->id;case "getName"->"OctoberQuestProbe";case "getGameMode"->GameMode.SURVIVAL;case "getWorld"->world;case "getOpenInventory"->view;case "hasPermission"->false;
            case "sendMessage"->{if(a[0] instanceof Component c)messages.add(PlainTextComponentSerializer.plainText().serialize(c));yield null;}default->null;
        });
        invoke(store,"join",new Class[]{UUID.class,String.class,long.class,long.class},id,"OctoberQuestProbe",System.currentTimeMillis(),6000L);
        String week=(String)invoke(field(q,"week"),"id",new Class[]{});
        var block=world.getBlockAt(0,100,0);block.getRelative(0,-1,0).setType(Material.FARMLAND);
        for(Material material:List.of(Material.WHEAT,Material.CARROTS,Material.POTATOES,Material.BEETROOTS)){
            block.setType(material,false);Ageable age=(Ageable)block.getBlockData();age.setAge(age.getMaximumAge()-1);block.setBlockData(age,false);
            Bukkit.getPluginManager().callEvent(new BlockDropItemEvent(block,block.getState(),p,new ArrayList<>()));
            age.setAge(age.getMaximumAge());block.setBlockData(age,false);var state=block.getState();
            var cancelled=new BlockDropItemEvent(block,state,p,new ArrayList<>());cancelled.setCancelled(true);Bukkit.getPluginManager().callEvent(cancelled);
            block.setType(Material.AIR,false);Bukkit.getPluginManager().callEvent(new BlockDropItemEvent(block,state,p,new ArrayList<>()));
        }
        check(progress(store,id,week,"green_fingers")==4,"real age data: mature crops count once, immature/cancelled harvests excluded",log);
        block.setType(Material.WHEAT,false);Ageable age=(Ageable)block.getBlockData();age.setAge(age.getMaximumAge());block.setBlockData(age,false);var mature=block.getState();block.setType(Material.AIR,false);
        for(int i=4;i<70;i++)Bukkit.getPluginManager().callEvent(new BlockDropItemEvent(block,mature,p,new ArrayList<>()));
        check(progress(store,id,week,"green_fingers")==64,"crop quest caps at 64",log);
        check(messages.size()==2,"harvest burst gives first and completion messages only",log);
        messages.clear();
        var cancelled=new PlayerStatisticIncrementEvent(p,Statistic.TRADED_WITH_VILLAGER,0,1);cancelled.setCancelled(true);Bukkit.getPluginManager().callEvent(cancelled);
        Bukkit.getPluginManager().callEvent(new PlayerStatisticIncrementEvent(p,Statistic.TALKED_TO_VILLAGER,0,1));
        check(progress(store,id,week,"local_trade")==0,"cancelled trade statistic and talking do not count",log);
        for(int i=0;i<12;i++)Bukkit.getPluginManager().callEvent(new PlayerStatisticIncrementEvent(p,Statistic.TRADED_WITH_VILLAGER,i,i+1));
        check(progress(store,id,week,"local_trade")==8,"individual trade completions in batch cap at eight",log);
        check(messages.size()==2,"trade burst gives first and completion messages only",log);
        Object account=((Optional<?>)invoke(store,"account",new Class[]{UUID.class},id)).orElseThrow();
        check((long)invoke(account,"cents",new Class[]{})==9000,"crop and trade rewards pay once each",log);
        Object options=invoke(q,"onTabComplete",new Class[]{CommandSender.class,Command.class,String.class,String[].class},p,null,"quests",new String[]{"contribute",""});
        check(new HashSet<>((List<?>)options).equals(Set.of("glowstone","ghast_tear")),"Nether hand-ins list correct materials",log);
        villager.remove();log.getLogger().info("OCTOBER_QUEST_PASS checks="+checks);
    }
    static int progress(Object store,UUID id,String week,String goal)throws Exception{return (int)invoke(store,"questProgress",new Class[]{UUID.class,String.class,String.class},id,week,goal);}
}
