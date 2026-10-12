package net.nikosnook.core;
import github.scarsz.discordsrv.api.events.*;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.ListenerPriority;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.junit.jupiter.api.*;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class StaffDiscordChatTest {
    final UUID id=UUID.randomUUID();
    final Player player=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){case "getUniqueId"->id;case "getName"->"Helper";default->null;});
    @SuppressWarnings("unchecked") Set<UUID> active() throws Exception {var f=StaffMode.class.getDeclaredField("ACTIVE");f.setAccessible(true);return (Set<UUID>)f.get(null);}
    @AfterEach void cleanup() throws Exception {active().remove(id);}
    @Test void hiddenPlayerBlockedBeforeAndAfterRelayFormatting() throws Exception {
        active().add(id);var listener=new StaffDiscordChat();
        var pre=new GameChatMessagePreProcessEvent("global","secret",player);listener.before(pre);assertTrue(pre.isCancelled());
        var post=new GameChatMessagePostProcessEvent("global","secret",player,false);listener.after(post);assertTrue(post.isCancelled());
    }
    @Test void ordinaryChatIsUnchanged() {
        var listener=new StaffDiscordChat();var pre=new GameChatMessagePreProcessEvent("global","hello",player);listener.before(pre);assertFalse(pre.isCancelled());
        var post=new GameChatMessagePostProcessEvent("global","hello",player,false);listener.after(post);assertFalse(post.isCancelled());
    }
    @Test void cancelledChatStaysBlockedAfterPlayerLeavesStaffMode() {
        var source=new AsyncPlayerChatEvent(false,player,"secret",new HashSet<>());source.setCancelled(true);
        var pre=new GameChatMessagePreProcessEvent("global",github.scarsz.discordsrv.dependencies.kyori.adventure.text.Component.text("secret"),player,source);
        var listener=new StaffDiscordChat();listener.before(pre);assertTrue(pre.isCancelled());
        var post=new GameChatMessagePostProcessEvent("global","secret",player,false,source);listener.after(post);assertTrue(post.isCancelled());
    }
    @Test void apiHandlersArePublicAndRunAtHighestPriority() throws Exception {
        assertEquals(ListenerPriority.HIGHEST,StaffDiscordChat.class.getMethod("before",GameChatMessagePreProcessEvent.class).getAnnotation(Subscribe.class).priority());
        assertEquals(ListenerPriority.HIGHEST,StaffDiscordChat.class.getMethod("after",GameChatMessagePostProcessEvent.class).getAnnotation(Subscribe.class).priority());
    }
}
