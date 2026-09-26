package net.nikosnook.core;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DoubleChestProtectionTest {
    private final Location leftLocation=new Location(null,10,64,10);
    private final Location rightLocation=new Location(null,11,64,10);
    private final AtomicReference<Inventory> joined=new AtomicReference<>();

    private Chest half(Location location){
        return (Chest)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Chest.class},(o,m,a)->switch(m.getName()){
            case "getLocation" -> location;
            case "getInventory" -> joined.get();
            default -> null;
        });
    }
    private DoubleChest chest(){
        var left=half(leftLocation);var right=half(rightLocation);
        var chest=new DoubleChest(null){
            @Override public InventoryHolder getLeftSide(){return left;}
            @Override public InventoryHolder getRightSide(){return right;}
        };
        joined.set((Inventory)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Inventory.class},(o,m,a)->m.getName().equals("getHolder")?chest:null));
        assertSame(chest,left.getInventory().getHolder());
        assertSame(chest,right.getInventory().getHolder());
        return chest;
    }
    @Test void unprotectedJoinedChestTerminatesWithoutFollowingInventoryCycle(){
        assertFalse(PlotProtectionListener.protectedInventory(chest(),location->false));
    }
    @Test void protectedLeftHalfBlocksAutomation(){
        assertTrue(PlotProtectionListener.protectedInventory(chest(),leftLocation::equals));
    }
    @Test void protectedRightHalfBlocksAutomation(){
        assertTrue(PlotProtectionListener.protectedInventory(chest(),rightLocation::equals));
    }
    @Test void singleChestUsesItsLocation(){
        assertTrue(PlotProtectionListener.protectedInventory(half(leftLocation),leftLocation::equals));
        assertFalse(PlotProtectionListener.protectedInventory(half(leftLocation),rightLocation::equals));
    }
    @Test void virtualInventoryHasNoPlotLocation(){
        assertFalse(PlotProtectionListener.protectedInventory(null,location->{throw new AssertionError();}));
    }
}
