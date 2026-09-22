package net.nikosnook.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShopTradePolicyTest {
    @TempDir Path dir;
    NookStore store;ShopTradePolicy policy;
    UUID owner=UUID.randomUUID(),coowner=UUID.randomUUID();long now=1_800_000_000_000L;
    @BeforeEach void setup()throws Exception {
        store=new NookStore(dir.resolve("nooks.db"));store.join(owner,"Owner",now,6000);store.join(coowner,"Coowner",now,6000);
        store.definePlot("small",3000);store.definePlot("other",3000);store.rent("small",owner,now);
        var invite=store.invite("small",owner,coowner,"BUILD_STOCK",now);store.acceptInvitation(invite.token(),coowner,now);
        policy=new ShopTradePolicy(store);
    }
    @AfterEach void close()throws Exception {store.close();}
    @Test void paidOriginalRenterMayTradeOffline()throws Exception {policy.check(List.of("small","small"),owner,now);}
    @Test void stockManagersCannotTradeTheirOwnStock()throws Exception {
        for(String role:List.of("STOCK","BUILD_STOCK")){
            store.changeRole("small",owner,coowner,role,now);
            assertThrows(IllegalArgumentException.class,()->policy.checkCustomer(List.of("small","small"),owner,coowner,now));
        }
        assertThrows(IllegalArgumentException.class,()->policy.checkCustomer(List.of("small","small"),owner,owner,now));
    }
    @Test void ordinaryCustomersAndBuildOnlyMembersCanStillTrade()throws Exception {
        policy.checkCustomer(List.of("small","small"),owner,UUID.randomUUID(),now);
        store.changeRole("small",owner,coowner,"BUILD",now);
        policy.checkCustomer(List.of("small","small"),owner,coowner,now);
    }
    @Test void StockRoleDoesNotBlockOtherPlotsOrTownTrades()throws Exception {
        UUID other=UUID.randomUUID();store.join(other,"Other",now,6000);store.rent("other",other,now);
        policy.checkCustomer(List.of("other","other"),other,coowner,now);
        policy.checkCustomer(List.of("",""),owner,coowner,now);
    }
    @Test void coownerCannotRedirectProceeds() {assertThrows(IllegalArgumentException.class,()->policy.check(List.of("small","small"),coowner,now));}
    @Test void townShopsNeedNoLease()throws Exception {policy.check(List.of("",""),coowner,now);}
    @Test void unallocatedDistrictRoadCannotTrade() {assertThrows(IllegalArgumentException.class,()->policy.check(List.of(ShopTradePolicy.ROAD,ShopTradePolicy.ROAD),owner,now));}
    @Test void crossPlotAndWildernessBoundariesAreRejected() {
        for(var locations:List.of(List.of("small","other"),List.of("small",""),List.of("","small"),List.of("small",ShopTradePolicy.ROAD)))
            assertThrows(IllegalArgumentException.class,()->policy.check(locations,owner,now));
    }
    @Test void bothHalvesOfDoubleChestMustBeInSamePlot()throws Exception {
        policy.check(List.of("small","small","small"),owner,now);
        assertThrows(IllegalArgumentException.class,()->policy.check(List.of("small","small","other"),owner,now));
        assertThrows(IllegalArgumentException.class,()->policy.check(List.of("","","small"),owner,now));
    }
    @Test void missingOrUnknownLocationsFailClosed() {
        assertThrows(IllegalArgumentException.class,()->policy.check(List.of("small"),owner,now));
        assertThrows(IllegalArgumentException.class,()->policy.check(Arrays.asList("small",null),owner,now));
        assertThrows(IllegalArgumentException.class,()->policy.check(List.of("missing","missing"),owner,now));
    }
    @Test void expiresBeforeSchedulerRuns() {
        assertThrows(IllegalArgumentException.class,()->policy.check(List.of("small","small"),owner,now+NookStore.WEEK));
    }
    @Test void graceStopsSalesUntilExplicitProratedReopen()throws Exception {
        long late=now+NookStore.WEEK+1000;store.tick(late);
        assertThrows(IllegalArgumentException.class,()->policy.check(List.of("small","small"),owner,late));
        store.reopen("small",owner,late);policy.check(List.of("small","small"),owner,late);
    }
    @Test void reclaimedOrAvailablePlotCannotTrade()throws Exception {
        long late=now+2*NookStore.WEEK;store.tick(late);
        assertThrows(IllegalArgumentException.class,()->policy.check(List.of("small","small"),owner,late));
        store.confirmCleared("small","staff","archive/one",late);
        assertThrows(IllegalArgumentException.class,()->policy.check(List.of("small","small"),owner,late));
    }
}
