import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.chat.SignedMessage;
import me.clip.placeholderapi.PlaceholderAPI;

public class AfkProbe extends JavaPlugin {
    int checks;Duration idle=Duration.ZERO;Object chat;Player player;
    void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;getLogger().info("PASS "+name);}
    Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    Object invoke(Object o,String n,Class<?>[] t,Object...a)throws Exception{var m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(o,a);}
    String value(String key){return PlaceholderAPI.setPlaceholders(player,"%nookcore_"+key+"%");}
    void refresh()throws Exception{invoke(chat,"refresh",new Class[]{Player.class},player);}
    Component rendered()throws Exception{var c=Component.text("hello");var e=new AsyncChatEvent(false,player,new HashSet<>(),ChatRenderer.defaultRenderer(),c,c,SignedMessage.system("hello",c));invoke(chat,"chat",new Class[]{AsyncChatEvent.class},e);return e.renderer().render(player,Component.text("TestPlayer"),e.message(),player);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{
        var core=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NookCore");check(core!=null && core.isEnabled(),"plugin starts with existing config");chat=field(core,"chat");
        UUID id=UUID.fromString("983f1c6c-7f17-4729-ab16-8c0d3407592a");
        player=(Player)Proxy.newProxyInstance(getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
            case "getUniqueId"->id;case "getName"->"TestPlayer";case "isOnline"->true;case "getPlayer"->o;
            case "hasPermission"->false;case "getStatistic"->0;case "hashCode"->id.hashCode();case "equals"->o==a[0];
            case "getIdleDuration"->{check(Bukkit.isPrimaryThread(),"idle timer read on server thread");yield idle;}
            default->null;
        });
        refresh();String rank=value("rank"),name=value("name"),balance=value("balance"),pronouns=value("pronouns");Component activeChat=rendered();
        check(rank.contains("#f1dea0") && rank.contains("miss bee"),"active title retains colour");check(value("afk").equals("false"),"active placeholder false");
        idle=Duration.ofMillis(299999);refresh();check(value("rank").equals(rank),"no early greying");
        idle=Duration.ofSeconds(300);refresh();check(value("afk").equals("true"),"five minutes becomes AFK");
        check(value("rank").equals("&#AAAAAA[miss bee] &r"),"AFK title grey through real PlaceholderAPI");check(value("name").equals("&#AAAAAATestPlayer&r"),"AFK name grey through real PlaceholderAPI");
        check(value("balance").equals(balance) && value("pronouns").equals(pronouns),"balance and pronouns preserved");check(rendered().equals(activeChat),"public chat keeps normal colours while AFK");
        idle=Duration.ZERO;refresh();check(value("rank").equals(rank) && value("name").equals(name) && value("afk").equals("false"),"activity restores original display");
        Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerQuitEvent(player,Component.empty()));check(value("name").isEmpty(),"quit removes cached display");
        getLogger().info("AFK_PASS checks="+checks);
    }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"AFK_FAILED",e);}finally{Bukkit.shutdown();}},40);}
}
