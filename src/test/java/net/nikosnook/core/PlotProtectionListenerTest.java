package net.nikosnook.core;

import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PlotProtectionListenerTest {
    private static final int TOP_SIZE=27;

    @Test void directTopClickAffectsProtectedInventory(){
        assertTrue(PlotProtectionListener.affectsTopInventory(
            0,TOP_SIZE,InventoryAction.PICKUP_ALL
        ));
        assertTrue(PlotProtectionListener.affectsTopInventory(
            26,TOP_SIZE,InventoryAction.PICKUP_ALL
        ));
    }

    @Test void normalBottomClickDoesNotAffectProtectedInventory(){
        assertFalse(PlotProtectionListener.affectsTopInventory(
            27,TOP_SIZE,InventoryAction.PICKUP_ALL
        ));
        assertFalse(PlotProtectionListener.affectsTopInventory(
            40,TOP_SIZE,InventoryAction.SWAP_WITH_CURSOR
        ));
    }

    @Test void bottomHotbarSwapDoesNotAffectProtectedInventory(){
        assertFalse(PlotProtectionListener.affectsTopInventory(
            30,TOP_SIZE,InventoryAction.HOTBAR_SWAP
        ));
    }

    @Test void shiftClickFromBottomCanAffectTopInventory(){
        assertTrue(PlotProtectionListener.affectsTopInventory(
            30,TOP_SIZE,InventoryAction.MOVE_TO_OTHER_INVENTORY
        ));
    }

    @Test void collectToCursorCanAffectTopInventory(){
        assertTrue(PlotProtectionListener.affectsTopInventory(
            30,TOP_SIZE,InventoryAction.COLLECT_TO_CURSOR
        ));
    }

    @Test void unknownActionFailsClosed(){
        assertTrue(PlotProtectionListener.affectsTopInventory(
            30,TOP_SIZE,InventoryAction.UNKNOWN
        ));
    }

    @Test void dragOnlyBlocksWhenItTouchesTopInventory(){
        assertFalse(PlotProtectionListener.affectsTopInventory(
            Set.of(27,28,35),TOP_SIZE
        ));

        assertTrue(PlotProtectionListener.affectsTopInventory(
            Set.of(25,27,28),TOP_SIZE
        ));
    }
}