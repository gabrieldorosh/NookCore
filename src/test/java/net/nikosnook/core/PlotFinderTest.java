package net.nikosnook.core;
import org.junit.jupiter.api.Test;
import org.bukkit.*;
import java.util.UUID;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;
class PlotFinderTest {
    @Test void rectangularNegativeCoordinatesHaveInclusiveDimensions(){
        var b=new PlotFinder.Bounds(UUID.randomUUID(),-20,-30,-14,-20);
        assertEquals(7,b.width());assertEquals(11,b.depth());assertEquals(-16.5,b.x());assertEquals(-24.5,b.z());
        assertTrue(b.contains(-13.1,-19.1));assertFalse(b.contains(-13,-20));assertFalse(b.contains(-21,-25));
    }
    @Test void directionsFollowMinecraftAxes(){
        assertEquals("north",PlotFinder.direction(0,-1));assertEquals("south",PlotFinder.direction(0,1));
        assertEquals("east",PlotFinder.direction(1,0));assertEquals("west",PlotFinder.direction(-1,0));
        assertEquals("northeast",PlotFinder.direction(1,-1));assertEquals("southwest",PlotFinder.direction(-1,1));
    }
    @Test void differentWorldDoesNotGiveMisleadingDistance(){
        var b=new PlotFinder.Bounds(UUID.randomUUID(),0,0,6,10);
        assertTrue(PlotFinder.description(b,new Location(null,0,0,0)).contains("Return to"));
    }
    @Test void sameWorldShowsArrivalOrStraightLineDistance(){
        UUID id=UUID.randomUUID();World w=(World)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{World.class},(o,m,a)->m.getName().equals("getUID")?id:null);
        var b=new PlotFinder.Bounds(id,0,0,6,10);
        assertTrue(PlotFinder.description(b,new Location(w,2,70,2)).contains("inside"));
        assertTrue(PlotFinder.description(b,new Location(w,3.5,70,15.5)).contains("10 blocks north"));
    }
}
