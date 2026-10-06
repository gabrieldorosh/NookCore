import java.lang.reflect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.chat.SignedMessage;

public class OctoberReleaseProbe extends JavaPlugin {
    int checks; List<String> received=new ArrayList<>();List<String> other=new ArrayList<>();
    Object chat;Player sender,observer;AsyncChatEvent first;
    Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    Object invoke(Object o,String n,Class<?>[] types,Object...args)throws Exception{var m=o.getClass().getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(o,args);}
    void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;getLogger().info("PASS "+name);}
    Player player(UUID id,List<String> inbox,boolean online){return (Player)Proxy.newProxyInstance(getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
        case "getUniqueId"->id;case "getName"->"TestPlayer";case "isOnline"->online;case "hasPermission"->false;case "getStatistic"->0;
        case "hashCode"->id.hashCode();case "equals"->o==a[0];
        case "sendMessage"->{for(Object v:a)if(v instanceof Component c)inbox.add(PlainTextComponentSerializer.plainText().serialize(c));yield null;}
        default->null;
    });}
    AsyncChatEvent event(Player p,String text){var c=Component.text(text);return new AsyncChatEvent(false,p,new HashSet<>(List.of(p,observer)),ChatRenderer.defaultRenderer(),c,c,SignedMessage.system(text,c));}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{
        var core=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NookCore");check(core!=null && core.isEnabled(),"maintenance plugin starts");chat=field(core,"chat");check(field(chat,"help")!=null,"keyword listener registered from defaults in old config");
        sender=player(UUID.fromString("983f1c6c-7f17-4729-ab16-8c0d3407592a"),received,true);observer=player(UUID.randomUUID(),other,true);
        invoke(chat,"refresh",new Class[]{Player.class},sender);
        String rank=(String)invoke(chat,"placeholder",new Class[]{UUID.class,String.class},sender.getUniqueId(),"rank");check(rank.contains("[miss bee]") && rank.toLowerCase().contains("#f1dea0"),"legacy bee mapping renders miss bee in pastel yellow");
        first=event(sender,"LAG!");Bukkit.getPluginManager().callEvent(first);
        var cancelled=event(player(UUID.randomUUID(),received,true),"admin");cancelled.setCancelled(true);Bukkit.getPluginManager().callEvent(cancelled);
        var late=event(player(UUID.randomUUID(),received,true),"admin");Bukkit.getPluginManager().callEvent(late);late.setCancelled(true);
        Bukkit.getPluginManager().callEvent(event(player(UUID.randomUUID(),received,false),"admin"));
        Bukkit.getScheduler().runTaskLater(this,()->{try{
            getLogger().info("Received="+received+" rules="+field(field(field(chat,"help"),"replies"),"rules"));check(received.size()==1 && received.getFirst().contains("TAB shows"),"exactly one private reply; cancelled, later-cancelled and disconnected senders skipped");check(other.isEmpty(),"observer gets no help reply");check(!first.isCancelled() && PlainTextComponentSerializer.plainText().serialize(first.message()).equals("LAG!") && first.viewers().size()==2,"original public chat and audience preserved");
            Bukkit.getPluginManager().callEvent(event(sender,"laggy admin"));
            Bukkit.getScheduler().runTaskLater(this,()->{try{check(received.size()==1,"repeated message does not spam private tips");OctoberQuestChecks.run(this);getLogger().info("CHAT_QOL_PASS checks="+checks);}catch(Throwable e){fail(e);}finally{Bukkit.shutdown();}},2);
        }catch(Throwable e){fail(e);Bukkit.shutdown();}},2);
    }catch(Throwable e){fail(e);Bukkit.shutdown();}},40);}
    void fail(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"CHAT_QOL_FAILED",e);}
}
