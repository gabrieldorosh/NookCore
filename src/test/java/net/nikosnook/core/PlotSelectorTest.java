package net.nikosnook.core;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PlotSelectorTest {
    @Test void addressResolvesWithoutChangingTheStoredId(){
        assertEquals("shop_1",PlotSelector.resolve("Willow-Way",Map.of("shop_1","Willow Way")));
        assertEquals("unknown",PlotSelector.resolve("unknown",Map.of("shop_1","Willow Way")));
    }
    @Test void exactIdsWinAndAmbiguousAddressesAreRejected(){
        assertEquals("willow-way",PlotSelector.resolve("willow-way",Map.of("willow-way","Other Road","shop_1","Willow Way")));
        assertThrows(IllegalArgumentException.class,()->PlotSelector.resolve("willow-way",Map.of("one","Willow Way","two","Willow Way")));
    }
}
