package net.nikosnook.core;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NikoRoamingTest {
    private static NikoRoute.Point p(double x,double y,double z){return new NikoRoute.Point("world",x,y,z);}
    private static final NikoRoute.Point HOME=p(.5,65,.5);
    private static final List<NikoRoute.Point> POINTS=List.of(HOME,p(2.5,65,.5));
    @Test void disabledOrMissingConfigurationPreservesStationaryBehavior(){
        var moves=new AtomicInteger();var checks=new AtomicInteger();
        var roaming=new NikoRoaming<Object>(false,List.of(),(a,b)->{checks.incrementAndGet();return "bad";},(n,a,b)->moves.incrementAndGet(),s->fail(s));
        for(int i=0;i<500;i++)roaming.update(new Object(),HOME,false);
        assertEquals(0,moves.get());assertEquals(0,checks.get());assertEquals("off",roaming.status());
    }
    @Test void providerFailureWarnsOnceAndStopsOnlyRoaming(){
        var warnings=new ArrayList<String>();var moves=new AtomicInteger();var actor=new Object();
        var roaming=new NikoRoaming<Object>(true,POINTS,(a,b)->null,(n,a,b)->{moves.incrementAndGet();throw new NoSuchMethodError("missing movement");},warnings::add);
        roaming.update(actor,HOME,false);for(int i=0;i<50;i++)assertDoesNotThrow(()->roaming.update(actor,HOME,false));
        assertEquals(1,moves.get());assertEquals(1,warnings.size());assertTrue(warnings.getFirst().contains("quests remain available"));
        assertTrue(roaming.status().startsWith("disabled"));
    }
    @Test void invalidInitialRouteNeverMovesAndDoesNotRetryOnReplacement(){
        var warnings=new ArrayList<String>();var checks=new AtomicInteger();
        var roaming=new NikoRoaming<Object>(true,POINTS,(a,b)->{checks.incrementAndGet();return "off-road";},(n,a,b)->fail("must stay home"),warnings::add);
        for(int i=0;i<10;i++)roaming.update(new Object(),HOME,false);
        assertEquals(1,checks.get());assertEquals(1,warnings.size());
    }
    @Test void recreationAndRemovalDiscardInMemoryProgress(){
        var moves=new ArrayList<NikoRoute.Point>();var actor=new Object();
        var roaming=new NikoRoaming<Object>(true,POINTS,(a,b)->null,(n,a,b)->moves.add(b),s->fail(s));
        roaming.update(actor,HOME,false);for(int i=0;i<10;i++)roaming.update(actor,HOME,false);
        assertEquals(1.1,moves.getLast().x(),1e-9);roaming.forget();roaming.update(new Object(),HOME,false);
        var replacement=new Object();roaming.update(replacement,HOME,false);roaming.update(replacement,HOME,false);
        assertEquals(.56,moves.getLast().x(),1e-9);
    }
    @Test void parsesOnlyExplicitFiniteCoordinatesWithBoundedPoints(){
        assertThrows(IllegalArgumentException.class,()->NikoRoaming.readPoints(null));
        assertThrows(IllegalArgumentException.class,()->NikoRoaming.readPoints(List.of("world,1,2,3","world,2,2,3")));
        assertThrows(IllegalArgumentException.class,()->NikoRoaming.readPoints(List.of(Map.of("world","world","x",0,"y",65,"z",0),Map.of("world","world","x",1,"y",65,"z",Double.POSITIVE_INFINITY))));
        assertEquals(POINTS,NikoRoaming.readPoints(List.of(Map.of("world","world","x",.5,"y",65,"z",.5),Map.of("world","world","x",2.5,"y",65,"z",.5))));
    }
    static final class Terrain {
        int reads,roadY=64,missingX=Integer.MAX_VALUE,wallX=Integer.MAX_VALUE,wallY=65;
        int unloadedChunk=Integer.MAX_VALUE;Material road=Material.COBBLESTONE;double top=1;
        final World world=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class[]{World.class},(o,m,a)->switch(m.getName()){
            case "getMinHeight"->-64;case "getMaxHeight"->320;case "getName"->"world";case "getUID"->new UUID(0,0);
            case "isChunkLoaded"->(int)a[0]!=unloadedChunk;
            case "getBlockAt"->{reads++;int x=(int)a[0],y=(int)a[1],z=(int)a[2];assertNotEquals(unloadedChunk,x>>4,"must not fetch unloaded blocks");yield block(x,y,z);}
            case "equals"->o==a[0];case "hashCode"->System.identityHashCode(o);case "toString"->"test world";
            default->throw new UnsupportedOperationException(m.getName());
        });
        Block block(int x,int y,int z){
            Material type=y==roadY && x!=missingX?road:x==wallX && y==wallY?Material.STONE:Material.AIR;
            List<BoundingBox> boxes=type==Material.AIR?List.of():List.of(new BoundingBox(0,0,0,1,y==roadY?top:1,1));
            var shape=(VoxelShape)Proxy.newProxyInstance(VoxelShape.class.getClassLoader(),new Class[]{VoxelShape.class},(o,m,a)->{
                if(m.getName().equals("getBoundingBoxes"))return boxes;throw new UnsupportedOperationException(m.getName());
            });
            return (Block)Proxy.newProxyInstance(Block.class.getClassLoader(),new Class[]{Block.class},(o,m,a)->switch(m.getName()){
                case "getType"->type;case "isLiquid"->false;case "getCollisionShape"->shape;
                case "isPassable"->throw new AssertionError("do not use isPassable on the foot block");
                default->throw new UnsupportedOperationException(m.getName());
            });
        }
    }
    @Test void fullBlocksAndActualDirtPathTopAreSupported(){
        var terrain=new Terrain();assertNull(NikoRoaming.safeSegment(terrain.world,HOME,p(2.5,65,.5)));
        terrain.road=Material.DIRT_PATH;terrain.top=.9375;
        assertNull(NikoRoaming.safeSegment(terrain.world,p(.5,64.9375,.5),p(2.5,64.9375,.5)));
        assertNotNull(NikoRoaming.safeSegment(terrain.world,HOME,p(2.5,65,.5)),"floating above the path is unsafe");
    }
    @Test void bodyAndHeadCollisionsUseWorldShiftedBoxes(){
        var terrain=new Terrain();terrain.wallX=1;
        assertTrue(NikoRoaming.safeSegment(terrain.world,HOME,p(2.5,65,.5)).contains("obstructed"));
        terrain.wallY=66;assertTrue(NikoRoaming.safeSegment(terrain.world,HOME,p(2.5,65,.5)).contains("obstructed"));
        terrain.wallY=67;assertNull(NikoRoaming.safeSegment(terrain.world,HOME,p(2.5,65,.5)));
    }
    @Test void entireFootprintAndSweptEdgesNeedRoadSupport(){
        var terrain=new Terrain();terrain.missingX=1;
        assertNotNull(NikoRoaming.safeSegment(terrain.world,p(.75,65,.5),p(.76,65,.5)),"center is on road but body overhangs missing column");
        assertNotNull(NikoRoaming.safeSegment(terrain.world,p(.5,65,.5),p(2.5,65,.5)),"a missing strip between endpoints cannot be skipped");
    }
    @Test void unloadedFootprintFailsBeforeAnyBlockRead(){
        var terrain=new Terrain();terrain.unloadedChunk=1;
        assertTrue(NikoRoaming.safeSegment(terrain.world,p(15.8,65,.5),p(15.85,65,.5)).contains("unloaded"));assertEquals(0,terrain.reads);
    }
    @Test void slabsStairsAndHeightChangesAreRejected(){
        var terrain=new Terrain();terrain.road=Material.COBBLESTONE_SLAB;terrain.top=.5;
        assertNotNull(NikoRoaming.safeSegment(terrain.world,p(.5,64.5,.5),p(2.5,64.5,.5)));
        terrain.road=Material.COBBLESTONE_STAIRS;terrain.top=1;
        assertNotNull(NikoRoaming.safeSegment(terrain.world,HOME,p(2.5,65,.5)));
        terrain.road=Material.COBBLESTONE;assertTrue(NikoRoaming.safeSegment(terrain.world,HOME,p(2.5,66,.5)).contains("height"));
    }
    static Player player(World world,GameMode mode,double x,boolean visible,boolean vanished){
        MetadataValue metadata=(MetadataValue)Proxy.newProxyInstance(MetadataValue.class.getClassLoader(),new Class[]{MetadataValue.class},(o,m,a)->m.getName().equals("asBoolean")?vanished:null);
        return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
            case "isOnline"->true;case "isDead"->false;case "getGameMode"->mode;case "getUniqueId"->new UUID(0,123);
            case "isVisibleByDefault"->visible;case "hasMetadata"->vanished;case "getMetadata"->List.of(metadata);
            case "getWorld"->world;case "getLocation"->new Location(world,x,65,.5);
            default->throw new UnsupportedOperationException(m.getName());
        });
    }
    @Test void allVisibleNonSpectatorPlayersPauseWithinSixBlocks(){
        var world=new Terrain().world;var at=new Location(world,.5,65,.5);
        for(var mode:List.of(GameMode.SURVIVAL,GameMode.ADVENTURE,GameMode.CREATIVE))assertTrue(NikoRoaming.pauses(player(world,mode,6.5,true,false),at));
        assertFalse(NikoRoaming.pauses(player(world,GameMode.SURVIVAL,6.51,true,false),at));
        assertFalse(NikoRoaming.pauses(player(world,GameMode.SPECTATOR,.5,true,false),at));
        assertFalse(NikoRoaming.pauses(player(world,GameMode.SURVIVAL,.5,false,false),at));
        assertFalse(NikoRoaming.pauses(player(world,GameMode.SURVIVAL,.5,true,true),at));
    }
}
