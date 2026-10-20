package net.nikosnook.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SpawnNpcStoreTest {
    @TempDir Path dir;
    NookStore store;UUID player=UUID.randomUUID();
    long now=Instant.parse("2026-10-14T08:00:00Z").toEpochMilli();
    VendorSchedule.Visit visit=VendorSchedule.at(now);
    QuestPlan.Goal goal=new QuestPlan.Goal("hunt","Defeat two witches","KILL","WITCH",2,1500);
    @BeforeEach void start()throws Exception{store=new NookStore(dir.resolve("nooks.db"));store.join(player,"Player",now,6000);}
    @AfterEach void close()throws Exception{store.close();}
    List<NookStore.VendorOffer> offers(){var all=new ArrayList<NookStore.VendorOffer>();for(int i=0;i<6;i++)all.add(new NookStore.VendorOffer(visit.id(),"head"+i,"Head "+i,new byte[]{1,2,3},400,2,2,0));return all;}
    void open()throws Exception{store.openVendorVisit(visit,offers(),"halloween");}
    UUID purchase(int quantity,long time)throws Exception{return store.beginVendorPurchase(player,visit.id(),"head0",quantity,new byte[32],new byte[32],time);}
    @Test void stockErrorsDistinguishSoldOutFromAvailable()throws Exception{
        open();assertEquals("Only 2 left in stock.",assertThrows(IllegalArgumentException.class,()->purchase(3,now)).getMessage());
        store.finishVendorPurchase(purchase(2,now));assertEquals("Sold out.",assertThrows(IllegalArgumentException.class,()->purchase(1,now)).getMessage());
    }
    @Test void seventhDesignPreservesPurchasesAndAccountsForPriorRestock()throws Exception{
        open();store.finishVendorPurchase(purchase(1,now));store.restockVendor(visit.id(),visit.restock());
        var extra=new NookStore.VendorOffer(visit.id(),"extra","Extra",new byte[]{1},400,3,3,0);
        store.expandVendorVisit(extra);store.expandVendorVisit(extra);
        assertEquals(7,store.vendorOffers(visit.id()).size());assertEquals(3,store.vendorOffers(visit.id()).getFirst().stock());assertEquals(6,store.vendorOffers(visit.id()).getLast().stock());assertEquals(5600,store.account(player).orElseThrow().cents());
        store.restockVendor(visit.id(),visit.restock()+1);assertEquals(6,store.vendorOffers(visit.id()).getLast().stock());
    }
    @Test void questProgressStartsOnlyAfterConfirmationAndRewardsOnce()throws Exception{
        store.questRotation(visit.id(),List.of(goal));store.configureNikoGate(true,true,now);
        assertTrue(store.advanceQuests(player,visit.id(),"KILL","WITCH",null,now).isEmpty());
        assertTrue(store.acceptNiko(player,visit.id(),"niko",now));assertFalse(store.acceptNiko(player,visit.id(),"niko",now));
        assertFalse(store.advanceQuests(player,visit.id(),"KILL","WITCH",null,now).getFirst().paid());
        assertTrue(store.advanceQuests(player,visit.id(),"KILL","WITCH",null,now).getFirst().paid());
        assertTrue(store.advanceQuests(player,visit.id(),"KILL","WITCH",null,now).isEmpty());assertEquals(7500,store.account(player).orElseThrow().cents());
    }
    @Test void midweekMigrationPreservesProgressAndGatesNextWeek()throws Exception{
        store.questRotation(visit.id(),List.of(goal));store.advanceQuests(player,visit.id(),"KILL","WITCH",null,now);
        store.configureNikoGate(true,false,now);assertTrue(store.nikoIntroWeek(visit.id()));assertTrue(store.questAccepted(player,visit.id()));assertEquals(1,store.questProgress(player,visit.id(),goal.id()));
        long next=visit.nextArrival();String week=QuestPlan.week(next).id();store.questRotation(week,List.of(goal));assertFalse(store.nikoIntroWeek(week));assertFalse(store.questAccepted(player,week));
        store.close();store=new NookStore(dir.resolve("nooks.db"));store.configureNikoGate(true,false,next);assertFalse(store.questAccepted(player,week));
        assertThrows(IllegalArgumentException.class,()->store.acceptNiko(player,visit.id(),"niko",next));
    }
    @Test void acceptanceSurvivesRestartButNotRollover()throws Exception{
        store.questRotation(visit.id(),List.of(goal));store.configureNikoGate(true,true,now);store.acceptNiko(player,visit.id(),"niko",now);
        store.close();store=new NookStore(dir.resolve("nooks.db"));store.configureNikoGate(true,true,now);assertTrue(store.questAccepted(player,visit.id()));assertFalse(store.questAccepted(player,QuestPlan.week(visit.nextArrival()).id()));
    }
    @Test void contributionIsBlockedBeforeAnyInventoryIntent()throws Exception{
        store.questRotation(visit.id(),List.of(new QuestPlan.Goal("stone","Collect stone","DEPOSIT","STONE",64,1500)));store.configureNikoGate(true,true,now);
        assertThrows(IllegalArgumentException.class,()->store.beginQuestDeposit(UUID.randomUUID(),player,visit.id(),"STONE",2,"a","b",now));
    }
    @Test void stockAndChargeCommitTogetherAndPendingBlocksRetry()throws Exception{
        open();UUID id=purchase(1,now);assertEquals(5600,store.account(player).orElseThrow().cents());assertEquals(1,store.vendorOffers(visit.id()).getFirst().stock());
        assertThrows(IllegalArgumentException.class,()->purchase(1,now));assertEquals(1,store.vendorReviews().size());
        store.finishVendorPurchase(id);assertTrue(store.vendorReviews().isEmpty());store.finishVendorPurchase(purchase(1,now));
        assertThrows(IllegalArgumentException.class,()->purchase(1,now));assertEquals(5200,store.account(player).orElseThrow().cents());
    }
    @Test void failedChargeDoesNotConsumeStock()throws Exception{
        open();store.adjust(player,-6000,"admin","empty",now);assertThrows(IllegalArgumentException.class,()->purchase(1,now));assertEquals(2,store.vendorOffers(visit.id()).getFirst().stock());assertTrue(store.vendorReviews().isEmpty());
    }
    @Test void restockIsOnceAndAddsToUnsoldStockAcrossRestart()throws Exception{
        open();store.finishVendorPurchase(purchase(1,now));store.restockVendor(visit.id(),visit.restock()-1);assertEquals(1,store.vendorOffers(visit.id()).getFirst().stock());
        store.restockVendor(visit.id(),visit.restock());assertEquals(3,store.vendorOffers(visit.id()).getFirst().stock());
        store.close();store=new NookStore(dir.resolve("nooks.db"));store.openVendorVisit(visit,offers(),"halloween");store.restockVendor(visit.id(),visit.restock()+1);assertEquals(3,store.vendorOffers(visit.id()).getFirst().stock());
    }
    @Test void buyCatchesUpMissedFridayRestockAndDepartureClosesOldMenu()throws Exception{
        open();store.finishVendorPurchase(purchase(2,now));store.finishVendorPurchase(purchase(1,visit.restock()));assertEquals(1,store.vendorOffers(visit.id()).getFirst().stock());
        assertThrows(IllegalArgumentException.class,()->purchase(1,visit.departure()));
    }
    @Test void receiptSurvivesRestartAndRefundIsExactlyOnce()throws Exception{
        open();UUID receipt=purchase(1,now);store.close();store=new NookStore(dir.resolve("nooks.db"));assertEquals(receipt,store.vendorReviews().getFirst().id());
        store.resolveVendor(receipt,false,"admin","Verified no item in saved inventory",now);assertEquals(6000,store.account(player).orElseThrow().cents());assertEquals(2,store.vendorOffers(visit.id()).getFirst().stock());
        assertThrows(IllegalArgumentException.class,()->store.resolveVendor(receipt,false,"admin","again",now));
    }
    @Test void confirmingDeliveryNeverMintsAnItemOrRefund()throws Exception{
        open();UUID receipt=purchase(1,now);store.resolveVendor(receipt,true,"admin","Player received head",now);assertEquals(5600,store.account(player).orElseThrow().cents());assertEquals(1,store.vendorOffers(visit.id()).getFirst().stock());assertTrue(store.vendorReviews().isEmpty());
    }
    @Test void capsAreFrozenAndRefundReleasesAllowance()throws Exception{
        var items=new ArrayList<>(offers());var first=items.getFirst();items.set(0,new NookStore.VendorOffer(first.visit(),first.design(),first.name(),first.item(),first.price(),first.allocation(),first.stock(),1));store.openVendorVisit(visit,items,"halloween");
        UUID receipt=purchase(1,now);store.resolveVendor(receipt,false,"admin","not delivered",now);store.finishVendorPurchase(purchase(1,now));
        assertThrows(IllegalArgumentException.class,()->purchase(1,visit.restock()));assertEquals(1,store.vendorBought(player,visit.id(),"head0"));
    }
    @Test void concurrentBuyersCannotBothTakeLastStock()throws Exception{
        open();store.finishVendorPurchase(purchase(1,now));var other=UUID.randomUUID();store.join(other,"Other",now,6000);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            var results=pool.invokeAll(List.<java.util.concurrent.Callable<Boolean>>of(()->{try{purchase(1,now);return true;}catch(IllegalArgumentException e){return false;}},()->{try{store.beginVendorPurchase(other,visit.id(),"head0",1,new byte[32],new byte[32],now);return true;}catch(IllegalArgumentException e){return false;}}));
            assertNotEquals(results.get(0).get(),results.get(1).get());assertEquals(0,store.vendorOffers(visit.id()).getFirst().stock());
        }finally{pool.shutdown();}
    }
    @Test void refundAtBalanceCapRollsBackAllRecoveryChanges()throws Exception{
        open();UUID receipt=purchase(1,now);store.adjust(player,Money.MAX-5600,"admin","cap",now);
        assertThrows(IllegalArgumentException.class,()->store.resolveVendor(receipt,false,"admin","no delivery",now));assertEquals(1,store.vendorReviews().size());assertEquals(1,store.vendorOffers(visit.id()).getFirst().stock());
    }
}
