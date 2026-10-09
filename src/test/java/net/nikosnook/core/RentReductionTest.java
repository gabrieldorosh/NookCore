package net.nikosnook.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import org.bukkit.Material;
import static org.junit.jupiter.api.Assertions.*;
class RentReductionTest {
    @TempDir Path dir;
    @Test void changesRentsOnlyAndFutureQuotesFollowPolicy()throws Exception{
        try(var s=new NookStore(dir.resolve("r.db"))){
            UUID owner=UUID.randomUUID();s.join(owner,"Owner",0,100000);s.definePlot("small",3000);s.definePlot("medium",5000);s.definePlot("large",9000);s.rent("small",owner,0);s.prepay("small",owner,2,0);
            var before=s.plot("small");long balance=s.account(owner).orElseThrow().cents();assertEquals(3000,s.defaultPlotRent(81));
            s.reducePlotRents(s.plots(),"console",1);
            assertEquals(600,s.plot("small").weekly());assertEquals(1000,s.plot("medium").weekly());assertEquals(1800,s.plot("large").weekly());
            assertEquals(before.owner(),s.plot("small").owner());assertEquals(before.paidUntil(),s.plot("small").paidUntil());assertEquals(before.state(),s.plot("small").state());assertEquals(balance,s.account(owner).orElseThrow().cents());
            assertEquals(600,s.defaultPlotRent(81));s.prepay("small",owner,1,2);assertEquals(balance-600,s.account(owner).orElseThrow().cents());
        }
    }
    @Test void repeatRejectedAfterRestart()throws Exception{
        Path db=dir.resolve("r.db");try(var s=new NookStore(db)){s.definePlot("small",3000);s.reducePlotRents(s.plots(),"console",1);}
        try(var s=new NookStore(db)){assertTrue(s.rentReductionApplied());assertThrows(IllegalArgumentException.class,()->s.reducePlotRents(s.plots(),"console",2));assertEquals(600,s.plot("small").weekly());}
    }
    @Test void stalePreviewCannotPartiallyReprice()throws Exception{
        try(var s=new NookStore(dir.resolve("r.db"))){s.definePlot("one",3000);var old=s.plots();s.definePlot("two",5000);assertThrows(IllegalArgumentException.class,()->s.reducePlotRents(old,"console",1));assertFalse(s.rentReductionApplied());assertEquals(3000,s.plot("one").weekly());assertEquals(5000,s.plot("two").weekly());}
    }
    @Test void labelsNeedSneakingAndARealSignItem(){assertTrue(ShopLabels.placingLabel(true,Material.OAK_SIGN));assertTrue(ShopLabels.placingLabel(true,Material.OAK_HANGING_SIGN));assertFalse(ShopLabels.placingLabel(false,Material.OAK_SIGN));assertFalse(ShopLabels.placingLabel(true,Material.CHEST));}
    @Test void priceRoundingRemainsPositive(){assertEquals(1,NookStore.reducedRent(1));assertEquals(580,NookStore.reducedRent(2900));assertEquals(201,NookStore.reducedRent(1003));}
}
