package net.nikosnook.core;

import org.junit.jupiter.api.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;

class AdminTeleportsTest {
    AdminTeleports teleports;Map<String,Player> players=new HashMap<>();List<String> messages=new ArrayList<>(),audit=new ArrayList<>();
    Player moved,target;ConsoleCommandSender console;Location location;int calls;Function<Location,Boolean> attempt;
    @SuppressWarnings("unchecked") <T>T proxy(Class<T> type,InvocationHandler handler){return (T)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{type},handler);}
    Object message(Object[] args){if(args!=null && args.length>0 && args[0] instanceof Component c)messages.add(PlainTextComponentSerializer.plainText().serialize(c));return null;}
    Player player(String name,Set<String> permissions){
        UUID id=UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return proxy(Player.class,(o,m,a)->switch(m.getName()){
            case "getUniqueId"->id;case "getName"->name;case "getLocation"->location.clone();case "hasPermission"->permissions.contains(a[0]);
            case "sendMessage"->message(a);
            case "teleport"->{calls++;yield attempt.apply((Location)a[0]);}
            default->null;
        });
    }
    @BeforeEach void setup(){
        UUID worldId=UUID.randomUUID();World world=proxy(World.class,(o,m,a)->switch(m.getName()){case "equals"->o==a[0];case "hashCode"->1;case "getUID"->worldId;default->null;});
        location=new Location(world,12,70,25,90,0);
        moved=player("Moved",Set.of());target=player("Target",Set.of());players.put("Moved",moved);players.put("Target",target);
        console=proxy(ConsoleCommandSender.class,(o,m,a)->switch(m.getName()){case "getName"->"CONSOLE";case "sendMessage"->message(a);default->null;});
        teleports=new AdminTeleports(players::get,audit::add);
        attempt=to->teleports.consume(event(moved,to,PlayerTeleportEvent.TeleportCause.PLUGIN));
    }
    PlayerTeleportEvent event(Player player,Location to,PlayerTeleportEvent.TeleportCause cause){return new PlayerTeleportEvent(player,location.clone(),to,cause);}
    void run(CommandSender sender){teleports.execute(sender,new String[]{"teleport","Moved","Target"});}
    @Test void consoleCanMoveOrdinaryPlayerWithoutGivingPersistentBypass(){
        run(console);assertEquals(1,calls);assertTrue(audit.getFirst().contains("completed"));
        assertFalse(teleports.consume(event(moved,location,PlayerTeleportEvent.TeleportCause.PLUGIN)));
    }
    @Test void helperAndPartialPermissionCannotTeleport(){
        for(var permissions:List.of(Set.<String>of(),Set.of("nookcore.admin"),Set.of("nookcore.travel.bypass")))run(player("Helper",permissions));
        assertEquals(0,calls);assertTrue(audit.isEmpty());
        run(player("Admin",Set.of("nookcore.admin","nookcore.travel.bypass")));assertEquals(1,calls);
    }
    @Test void permitIsDestinationPlayerCauseAndSingleUseScoped(){
        attempt=to->{
            assertFalse(teleports.consume(event(target,to,PlayerTeleportEvent.TeleportCause.PLUGIN)));
            assertFalse(teleports.consume(event(moved,to.clone().add(1,0,0),PlayerTeleportEvent.TeleportCause.PLUGIN)));
            assertFalse(teleports.consume(event(moved,to,PlayerTeleportEvent.TeleportCause.COMMAND)));
            assertTrue(teleports.consume(event(moved,to,PlayerTeleportEvent.TeleportCause.PLUGIN)));
            assertFalse(teleports.consume(event(moved,to,PlayerTeleportEvent.TeleportCause.PLUGIN)));return true;
        };run(console);assertEquals(1,calls);
    }
    @Test void cancellationCleansPermitAndReportsFailure(){
        attempt=to->false;run(console);assertTrue(audit.getFirst().contains("cancelled"));
        assertTrue(messages.stream().anyMatch(s->s.contains("cancelled")));assertFalse(teleports.consume(event(moved,location,PlayerTeleportEvent.TeleportCause.PLUGIN)));
    }
    @Test void exceptionCleansPermitAndDoesNotReportSuccess(){
        attempt=to->{throw new IllegalStateException("test");};run(console);
        assertTrue(audit.getFirst().contains("failed"));assertFalse(teleports.consume(event(moved,location,PlayerTeleportEvent.TeleportCause.PLUGIN)));
    }
    @Test void malformedOfflineAndSelfTargetsDoNotMove(){
        teleports.execute(console,new String[]{"teleport"});teleports.execute(console,new String[]{"teleport","Unknown","Target"});
        teleports.execute(console,new String[]{"teleport","Moved","Moved"});assertEquals(0,calls);
    }
    boolean route(CommandSender sender,String command){var cancelled=new java.util.concurrent.atomic.AtomicBoolean();teleports.routeConsole(sender,command,()->cancelled.set(true));return cancelled.get();}
    @Test void simpleConsoleAliasesUseScopedPath(){
        for(String command:List.of("tp Moved Target","teleport Moved Target","minecraft:tp Moved Target","/minecraft:teleport Moved Target")){
            assertTrue(route(console,command));
        }assertEquals(4,calls);
    }
    @Test void commandBlocksAndUnsupportedGrammarAreNotIntercepted(){
        CommandSender block=proxy(BlockCommandSender.class,(o,m,a)->m.getName().equals("hasPermission")?true:null);
        assertFalse(route(block,"tp Moved Target"));
        for(String command:List.of("tp @a Target","tp Moved 1 2 3","say tp Moved Target")){
            assertFalse(route(console,command));
        }assertEquals(0,calls);
    }
}
