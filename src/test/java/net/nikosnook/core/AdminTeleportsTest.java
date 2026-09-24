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
        UUID worldId=UUID.randomUUID();World world=proxy(World.class,(o,m,a)->switch(m.getName()){case "equals"->o==a[0];case "hashCode"->1;case "getUID"->worldId;case "getName"->"test_world";default->null;});
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
    @Test void singleNameMovesAdminWithoutDuplicateMovedMessage(){
        Player admin=player("Admin",Set.of("nookcore.admin","nookcore.travel.bypass"));players.put("Admin",admin);
        attempt=to->teleports.consume(event(admin,to,PlayerTeleportEvent.TeleportCause.PLUGIN));
        teleports.execute(admin,new String[]{"teleport","Target"});assertEquals(1,calls);
        assertEquals(1,messages.size());assertTrue(messages.getFirst().startsWith("NookAdmin » Teleported Admin"));
    }
    @Test void adminPlayerAliasCanMoveOrdinaryPlayer(){
        Player admin=player("Admin",Set.of("nookcore.admin","nookcore.travel.bypass"));
        for(String root:List.of("/tp","/teleport","/minecraft:tp","/essentials:tp")){
            var event=new org.bukkit.event.player.PlayerCommandPreprocessEvent(admin,root+" Moved Target",Set.of());
            teleports.playerCommand(event);assertTrue(event.isCancelled());
        }
        assertEquals(4,calls);assertFalse(teleports.consume(event(moved,location,PlayerTeleportEvent.TeleportCause.PLUGIN)));
    }
    @Test void helperAliasesCannotAcquirePermit(){
        var event=new org.bukkit.event.player.PlayerCommandPreprocessEvent(moved,"/tp Moved Target",Set.of());
        teleports.playerCommand(event);assertFalse(event.isCancelled());assertEquals(0,calls);
    }
    @Test void coordinatesUseScopedPermitAndPreserveRotation(){
        attempt=to->{assertEquals(100.5,to.getX());assertEquals(72,to.getY());assertEquals(25,to.getZ());assertEquals(90,to.getYaw());return teleports.consume(event(moved,to,PlayerTeleportEvent.TeleportCause.PLUGIN));};
        teleports.execute(console,new String[]{"teleport","Moved","100.5","~2","~"});assertEquals(1,calls);assertTrue(audit.getFirst().contains("completed"));
    }
    @Test void invalidCoordinatesAndUnauthorisedRequestsDoNotMove(){
        for(String x:List.of("NaN","Infinity","30000001","^1","no"))teleports.execute(console,new String[]{"teleport","Moved",x,"70","25"});
        teleports.execute(moved,new String[]{"teleport","1","70","25"});
        teleports.execute(console,new String[]{"teleport","1","70","25"});assertEquals(0,calls);
    }
    @Test void adminCanTeleportSelfToCoordinates(){
        Player admin=player("Admin",Set.of("nookcore.admin","nookcore.travel.bypass"));
        attempt=to->teleports.consume(event(admin,to,PlayerTeleportEvent.TeleportCause.PLUGIN));
        teleports.execute(admin,new String[]{"teleport","10","70","20"});assertEquals(1,calls);assertEquals(1,messages.size());
    }
    @Test void successfulReturnUsesOriginalLocationAndIsSingleUse(){
        teleports=new AdminTeleports(players::get,audit::add,()->100L,p->true);
        Location original=location.clone();run(console);location=location.clone().add(50,0,50);
        attempt=to->{assertEquals(original,to);return teleports.consume(event(moved,to,PlayerTeleportEvent.TeleportCause.PLUGIN));};
        teleports.execute(console,new String[]{"return","Moved"});assertEquals(2,calls);
        teleports.execute(console,new String[]{"return","Moved"});assertEquals(2,calls);
    }
    @Test void cancelledTeleportDoesNotCreateReturnPoint(){
        teleports=new AdminTeleports(players::get,audit::add,()->100L,p->true);attempt=to->false;run(console);
        teleports.execute(console,new String[]{"return","Moved"});assertEquals(1,calls);
    }
    @Test void unsafeAndCancelledReturnsKeepPointForRetry(){
        var safe=new java.util.concurrent.atomic.AtomicBoolean(false);
        teleports=new AdminTeleports(players::get,audit::add,()->100L,p->safe.get());run(console);
        teleports.execute(console,new String[]{"return","Moved"});assertEquals(1,calls);
        safe.set(true);attempt=to->false;teleports.execute(console,new String[]{"return","Moved"});assertEquals(2,calls);
        attempt=to->true;teleports.execute(console,new String[]{"return","Moved"});assertEquals(3,calls);
        teleports.execute(console,new String[]{"return","Moved"});assertEquals(3,calls);
    }
    @Test void returnExpiresExactlyAtDeadline(){
        var time=new java.util.concurrent.atomic.AtomicLong(100);
        teleports=new AdminTeleports(players::get,audit::add,time::get,p->true);run(console);
        time.addAndGet(AdminTeleports.RETURN_LIFETIME);teleports.execute(console,new String[]{"return","Moved"});assertEquals(1,calls);
    }
    @Test void forgetAndUnauthorisedReturnCannotMovePlayer(){
        teleports=new AdminTeleports(players::get,audit::add,()->100L,p->true);run(console);
        teleports.execute(moved,new String[]{"return","Moved"});assertEquals(1,calls);
        teleports.forget(moved.getUniqueId());teleports.execute(console,new String[]{"return","Moved"});assertEquals(1,calls);
    }
    @Test void latestSuccessfulTeleportReplacesOriginButFailureDoesNot(){
        teleports=new AdminTeleports(players::get,audit::add,()->100L,p->true);run(console);
        location=location.clone().add(20,0,0);Location second=location.clone();run(console);
        location=location.clone().add(20,0,0);attempt=to->false;run(console);
        attempt=to->{assertEquals(second,to);return true;};teleports.execute(console,new String[]{"return","Moved"});assertEquals(4,calls);
    }
    boolean safeLanding(Location location){return AdminTeleports.safeReturn(location);}
    Location landing(Material floor,Material body,boolean generated,boolean inside){return shapedLanding(floor,body,generated,inside,1,70);}
    Location shapedLanding(Material floor,Material body,boolean generated,boolean inside,double height,double feet){
        var border=proxy(WorldBorder.class,(o,m,a)->m.getName().equals("isInside")?inside:null);
        World world=proxy(World.class,(o,m,a)->switch(m.getName()){
            case "getMinHeight"->-64;case "getMaxHeight"->320;case "getWorldBorder"->border;case "isChunkGenerated"->generated;
            case "getBlockAt"->{int by=(int)a[1];Material type=by==69?floor:by>=70?body:Material.AIR;
                var boxes=type==Material.AIR || type==Material.WATER?List.<org.bukkit.util.BoundingBox>of():List.of(new org.bukkit.util.BoundingBox(0,0,0,1,by==69?height:1,1));
                var shape=proxy(org.bukkit.util.VoxelShape.class,(v,method,args)->method.getName().equals("getBoundingBoxes")?boxes:null);
                yield proxy(org.bukkit.block.Block.class,(block,method,args)->switch(method.getName()){case "getType"->type;case "getCollisionShape"->shape;default->null;});}
            default->null;
        });return new Location(world,12.5,feet,25.5);
    }
    @Test void landingRequiresAirAndSolidNonHazardousFloor(){
        assertTrue(safeLanding(landing(Material.STONE,Material.AIR,true,true)));
        assertFalse(safeLanding(landing(Material.AIR,Material.AIR,true,true)));
        assertFalse(safeLanding(landing(Material.MAGMA_BLOCK,Material.AIR,true,true)));
        assertFalse(safeLanding(landing(Material.STONE,Material.STONE,true,true)));
        assertFalse(safeLanding(landing(Material.STONE,Material.WATER,true,true)));
    }
    @Test void landingRejectsUnGeneratedTerrainBorderAndHeight(){
        assertFalse(safeLanding(landing(Material.STONE,Material.AIR,false,true)));
        assertFalse(safeLanding(landing(Material.STONE,Material.AIR,true,false)));
        var low=landing(Material.STONE,Material.AIR,true,true);low.setY(-64);assertFalse(safeLanding(low));
        var high=landing(Material.STONE,Material.AIR,true,true);high.setY(320);assertFalse(safeLanding(high));
    }
    @Test void returnAcceptsPathsSlabsGlassAndCarpetAtTheirActualSurface(){
        assertTrue(safeLanding(shapedLanding(Material.DIRT_PATH,Material.AIR,true,true,.9375,69.9375)));
        assertTrue(safeLanding(shapedLanding(Material.STONE_SLAB,Material.AIR,true,true,.5,69.5)));
        assertTrue(safeLanding(shapedLanding(Material.GLASS,Material.AIR,true,true,1,70)));
        assertTrue(safeLanding(shapedLanding(Material.WHITE_CARPET,Material.AIR,true,true,.0625,69.0625)));
    }
    @Test void returnRejectsEmbeddingAndFloatingAboveChangedSurface(){
        assertFalse(safeLanding(shapedLanding(Material.STONE,Material.AIR,true,true,1,69.9375)));
        assertFalse(safeLanding(shapedLanding(Material.STONE_SLAB,Material.AIR,true,true,.5,70)));
    }
    @Test void rejectionIncludesSavedWorldAndCoordinates(){
        teleports=new AdminTeleports(players::get,audit::add,()->100L,p->false);run(console);messages.clear();
        teleports.execute(console,new String[]{"return","Moved"});assertTrue(messages.getFirst().contains("test_world · X 12.000, Y 70.000, Z 25.000"));assertEquals(1,calls);
    }
}
