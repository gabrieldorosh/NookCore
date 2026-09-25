package net.nikosnook.core;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class OfflineIncomeTest {
    @TempDir Path dir;
    NookStore store;UUID owner=UUID.randomUUID(),buyer=UUID.randomUUID();
    @BeforeEach void setup()throws Exception{store=new NookStore(dir.resolve("db"));store.join(owner,"Owner",0,10000);store.join(buyer,"Buyer",0,10000);}
    @AfterEach void close()throws Exception{store.close();}
    @Test void paymentsGroupBySenderAndAcknowledgementDoesNotChangeBalance()throws Exception{
        store.transfer(buyer,owner,100,1);store.transfer(buyer,owner,200,2);
        var report=store.offlineIncome(owner);assertEquals(300,report.payments());assertEquals(0,report.sales());assertEquals(1,report.senders().size());assertEquals("Buyer",report.senders().getFirst().name());
        store.acknowledgeIncome(owner,report.through());assertEquals(0,store.offlineIncome(owner).through());assertEquals(10300,store.account(owner).orElseThrow().cents());
    }
    @Test void onlinePaymentsAndUnrelatedCreditsAreExcluded()throws Exception{
        store.online(owner,true);store.transfer(buyer,owner,100,1);store.online(owner,false);store.adjust(owner,100,"staff","test",2);store.award(owner,"test",100,3);
        assertEquals(0,store.offlineIncome(owner).through());store.transfer(buyer,owner,200,4);assertEquals(200,store.offlineIncome(owner).payments());
    }
    @Test void pendingReportSurvivesRestartAndAcknowledgesOnlyDisplayedRange()throws Exception{
        store.transfer(buyer,owner,100,1);long through=store.offlineIncome(owner).through();store.close();store=new NookStore(dir.resolve("db"));
        store.transfer(buyer,owner,200,2);store.acknowledgeIncome(owner,through);assertEquals(200,store.offlineIncome(owner).payments());
        store.acknowledgeIncome(buyer,Long.MAX_VALUE);assertEquals(200,store.offlineIncome(owner).payments());
    }
    @Test void failedTransferLeavesNoReport()throws Exception{
        store.adjust(owner,Money.MAX-10000,"test","capacity",0);
        assertThrows(NookStore.BalanceCapacityException.class,()->store.transfer(buyer,owner,100,1));
        assertEquals(0,store.offlineIncome(owner).through());assertEquals(10000,store.account(buyer).orElseThrow().cents());
    }
    @Test void successfulShopSaleIsSeparateAndRetryDoesNotDuplicateIncome()throws Exception{
        store.definePlot("one",3000);store.rent("one",owner,0);var offer=store.createNativeOffer("one",owner,new byte[]{1},1,250,1);store.creditNativeStock(UUID.randomUUID(),offer.id(),owner,3,1);
        UUID receipt=UUID.randomUUID();store.settleNativePurchase(receipt,offer.id(),buyer,1,2);store.settleNativePurchase(receipt,offer.id(),buyer,1,2);
        assertEquals(250,store.offlineIncome(owner).sales());assertEquals(0,store.offlineIncome(owner).payments());
        store.online(owner,true);store.settleNativePurchase(UUID.randomUUID(),offer.id(),buyer,1,3);assertEquals(250,store.offlineIncome(owner).sales());
    }
}
