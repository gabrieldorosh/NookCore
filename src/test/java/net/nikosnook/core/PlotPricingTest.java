package net.nikosnook.core;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PlotPricingTest {
    @Test void anchorPricesAndRectangles(){assertEquals(3000,PlotPricing.weekly(81));assertEquals(5000,PlotPricing.weekly(144));assertEquals(9000,PlotPricing.weekly(225));assertEquals(2900,PlotPricing.weekly(77));assertEquals(8600,PlotPricing.weekly(216));}
    @Test void rentIsPositiveWholeNooksAndNeverDecreases(){long previous=0;for(int a=1;a<=10000;a++){long current=PlotPricing.weekly(a);assertTrue(current>=previous);assertEquals(0,current%100);previous=current;}assertEquals(100,PlotPricing.weekly(1));}
    @Test void unsupportedAreasRejected(){assertThrows(IllegalArgumentException.class,()->PlotPricing.weekly(0));assertThrows(IllegalArgumentException.class,()->PlotPricing.weekly(1_000_001));}
    @Test void generatedAddressesStayUniqueAfterWordPoolIsExhausted(){Set<String> used=new HashSet<>(),labels=new HashSet<>();Random random=new Random(1);for(int i=0;i<200;i++){var address=PlotAddresses.choose(used::contains,random);assertTrue(used.add(address.id()));assertTrue(labels.add(address.label()));assertEquals(address.id(),PlotLayout.id(address.id()));assertEquals(address.label(),PlotNames.clean(address.label()));}}
}
