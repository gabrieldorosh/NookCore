import java.lang.reflect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.command.*;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

final class ReleaseQuestChecks {
    private static int checks;
    private static void check(boolean ok,String message,JavaPlugin log){if(!ok)throw new AssertionError(message);checks++;log.getLogger().info("PASS "+message);}
    private static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static Object invoke(Object o,String name,Class<?>[] types,Object... args)throws Exception{var m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}
    static void run(JavaPlugin log)throws Exception{
        JavaPlugin core=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NookCore");Object store=field(core,"store");
        Object quests=HandlerList.getRegisteredListeners(core).stream().map(r->r.getListener()).filter(l->l.getClass().getSimpleName().equals("WeeklyQuests")).findFirst().orElseThrow();
        UUID id=UUID.randomUUID();World world=Bukkit.getWorlds().getFirst();List<String> messages=new ArrayList<>();
        Player player=(Player)Proxy.newProxyInstance(log.getClass().getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
            case "getUniqueId"->id;case "getName"->"QuestProbe";case "getGameMode"->GameMode.SURVIVAL;case "getWorld"->world;case "hasPermission"->false;
            case "sendMessage"->{if(a[0] instanceof Component c)messages.add(PlainTextComponentSerializer.plainText().serialize(c));yield null;}
            default->null;
        });
        invoke(store,"join",new Class[]{UUID.class,String.class,long.class,long.class},id,"QuestProbe",System.currentTimeMillis(),6000L);
        invoke(quests,"onCommand",new Class[]{CommandSender.class,Command.class,String.class,String[].class},player,null,"quests",new String[]{"contribute"});
        check(messages.stream().anyMatch(s->s.contains("/quests contribute rail")) && messages.stream().anyMatch(s->s.contains("/quests contribute oak_log")),"two contribution choices without accessing inventory",log);
        Object completions=invoke(quests,"onTabComplete",new Class[]{CommandSender.class,Command.class,String.class,String[].class},player,null,"quests",new String[]{"contribute",""});
        check(((List<?>)completions).containsAll(List.of("rail","oak_log")),"contribution completion offers both selected materials",log);
        Object week=field(quests,"week");String weekId=(String)invoke(week,"id",new Class[]{});
        var block=world.getBlockAt(6,65,18);block.setType(Material.DIAMOND_ORE);invoke(quests,"mine",new Class[]{BlockBreakEvent.class},new BlockBreakEvent(block,player));
        invoke(quests,"mine",new Class[]{BlockBreakEvent.class},new BlockBreakEvent(block,player));
        check((int)invoke(store,"questProgress",new Class[]{UUID.class,String.class,String.class},id,weekId,"diamonds")==1,"diamond ore location counts only once",log);
        var deep=world.getBlockAt(7,65,18);deep.setType(Material.DEEPSLATE_DIAMOND_ORE);invoke(quests,"mine",new Class[]{BlockBreakEvent.class},new BlockBreakEvent(deep,player));
        check((int)invoke(store,"questProgress",new Class[]{UUID.class,String.class,String.class},id,weekId,"diamonds")==2,"deepslate diamond contributes to same goal",log);
        var placed=world.getBlockAt(8,65,18);placed.setType(Material.DIAMOND_ORE);
        invoke(quests,"place",new Class[]{BlockPlaceEvent.class},new BlockPlaceEvent(placed,placed.getState(),placed.getRelative(0,-1,0),new ItemStack(Material.DIAMOND_ORE),player,true));
        invoke(quests,"mine",new Class[]{BlockBreakEvent.class},new BlockBreakEvent(placed,player));
        check((int)invoke(store,"questProgress",new Class[]{UUID.class,String.class,String.class},id,weekId,"diamonds")==2,"placed diamond ore is excluded",log);
        messages.clear();for(int n=0;n<200;n++){if(n<8)invoke(quests,"record",new Class[]{Player.class,String.class,String.class,String.class},player,"KILL","WITCH",null);invoke(quests,"record",new Class[]{Player.class,String.class,String.class,String.class},player,"KILL","HOSTILE",null);}
        check(messages.size()==4,"200 kills plus overlapping witches emit first and completion only in rapid burst",log);
        Object account=((Optional<?>)invoke(store,"account",new Class[]{UUID.class},id)).orElseThrow();
        check((long)invoke(account,"cents",new Class[]{})==10500L,"witch quest and hostile challenge pay once each",log);
        block.setType(Material.AIR);deep.setType(Material.AIR);placed.setType(Material.AIR);
        log.getLogger().info("RELEASE_QUEST_PASS checks="+checks);
    }
}
