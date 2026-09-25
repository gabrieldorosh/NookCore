package net.nikosnook.core;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class NativeShopSettlementTest {
    @TempDir Path dir;
    NookStore store;UUID seller=UUID.randomUUID(),buyer=UUID.randomUUID(),other=UUID.randomUUID();NativeShop.Offer offer;
    @BeforeEach void setup()throws Exception {
        store=new NookStore(dir.resolve("db"));store.join(seller,"Seller",0,10000);store.join(buyer,"Buyer",0,10000);store.join(other,"Other",0,10000);store.definePlot("one",3000);store.rent("one",seller,0);
        offer=store.createNativeOffer("one",seller,new byte[]{1,2,3},4,250,1);
    }
    @AfterEach void close()throws Exception {store.close();}
    void stock(int count)throws Exception {store.creditNativeStock(UUID.randomUUID(),offer.id(),seller,count,2);}
    long balance(UUID id)throws Exception {return store.account(id).orElseThrow().cents();}
    @Test void paymentStockAndReceiptPersistTogetherAndRetryIsIdempotent()throws Exception {
        stock(8);UUID receipt=UUID.randomUUID();long before=balance(seller);
        var paid=store.settleNativePurchase(receipt,offer.id(),buyer,1,3);assertEquals(4,paid.quantity());assertEquals(250,paid.cents());assertEquals(9750,balance(buyer));assertEquals(before+250,balance(seller));assertEquals(4,store.nativeOffer(offer.id()).stock());
        store.close();store=new NookStore(dir.resolve("db"));var retry=store.settleNativePurchase(receipt,offer.id(),buyer,1,4);assertEquals(paid.id(),retry.id());assertArrayEquals(new byte[]{1,2,3},retry.item());assertEquals(9750,balance(buyer));assertEquals(4,store.nativeOffer(offer.id()).stock());
    }
    @Test void sellerCapacityFailureRollsBackBuyerStockAndReceipt()throws Exception {
        stock(4);store.adjust(seller,Money.MAX-balance(seller),"test","capacity",2);UUID receipt=UUID.randomUUID();
        assertThrows(NookStore.BalanceCapacityException.class,()->store.settleNativePurchase(receipt,offer.id(),buyer,1,3));assertEquals(10000,balance(buyer));assertEquals(Money.MAX,balance(seller));assertEquals(4,store.nativeOffer(offer.id()).stock());assertTrue(store.nativeReceipt(receipt).isEmpty());
    }
    @Test void insufficientMoneyOrStockNeverPartiallySettles()throws Exception {
        assertThrows(IllegalArgumentException.class,()->store.settleNativePurchase(UUID.randomUUID(),offer.id(),buyer,1,3));stock(4);store.adjust(buyer,-10000,"test","empty",2);long before=balance(seller);assertThrows(IllegalArgumentException.class,()->store.settleNativePurchase(UUID.randomUUID(),offer.id(),buyer,1,3));assertEquals(4,store.nativeOffer(offer.id()).stock());assertEquals(before,balance(seller));
    }
    @Test void staleQuoteRequiresFreshConfirmation()throws Exception {
        stock(4);store.priceNativeOffer(offer.id(),seller,500,2);assertThrows(IllegalArgumentException.class,()->store.settleNativePurchase(UUID.randomUUID(),offer.id(),buyer,1,3));assertEquals(10000,balance(buyer));var paid=store.settleNativePurchase(UUID.randomUUID(),offer.id(),buyer,2,3);assertEquals(500,paid.cents());
    }
    @Test void receiptIdsCannotBeReusedForDifferentBuyers()throws Exception {
        stock(8);UUID receipt=UUID.randomUUID();store.settleNativePurchase(receipt,offer.id(),buyer,1,3);assertThrows(IllegalArgumentException.class,()->store.settleNativePurchase(receipt,offer.id(),other,1,4));assertEquals(10000,balance(other));assertEquals(4,store.nativeOffer(offer.id()).stock());
    }
    @Test void stockReceiptIsIdempotentAndConflictingRetryRejected()throws Exception {
        assertThrows(IllegalArgumentException.class,()->store.creditNativeStock(null,offer.id(),seller,8,2));UUID receipt=UUID.randomUUID();store.creditNativeStock(receipt,offer.id(),seller,8,2);store.creditNativeStock(receipt,offer.id(),seller,8,3);assertEquals(8,store.nativeOffer(offer.id()).stock());assertThrows(IllegalArgumentException.class,()->store.creditNativeStock(receipt,offer.id(),seller,9,4));assertThrows(IllegalArgumentException.class,()->store.creditNativeStock(UUID.randomUUID(),offer.id(),other,4,4));
    }
    @Test void closedAndReRentedPlotsCannotReuseOldOffers()throws Exception {
        stock(4);store.abandon(store.abandonmentQuote("one",seller,2),seller,2);assertThrows(IllegalArgumentException.class,()->store.settleNativePurchase(UUID.randomUUID(),offer.id(),buyer,1,3));store.confirmCleared("one","staff","A1",4);store.rent("one",seller,5);assertThrows(IllegalArgumentException.class,()->store.settleNativePurchase(UUID.randomUUID(),offer.id(),buyer,1,6));assertEquals(4,store.nativeOffer(offer.id()).stock());
    }
    @Test void expiredRentAndSelfTradingDenied()throws Exception {
        stock(8);assertThrows(IllegalArgumentException.class,()->store.settleNativePurchase(UUID.randomUUID(),offer.id(),seller,1,3));assertThrows(IllegalArgumentException.class,()->store.settleNativePurchase(UUID.randomUUID(),offer.id(),buyer,1,NookStore.WEEK));assertEquals(8,store.nativeOffer(offer.id()).stock());
    }
    @Test void concurrentBuyersCannotOversellLastBundle()throws Exception {
        stock(4);
        try(var workers=Executors.newFixedThreadPool(2)){
            List<Future<Boolean>> results=new ArrayList<>();for(UUID id:List.of(buyer,other))results.add(workers.submit(()->{try{store.settleNativePurchase(UUID.randomUUID(),offer.id(),id,1,3);return true;}catch(IllegalArgumentException e){return false;}}));
            int successes=0;for(var result:results)if(result.get())successes++;assertEquals(1,successes);
        }
        assertEquals(0,store.nativeOffer(offer.id()).stock());assertEquals(19750,balance(buyer)+balance(other));
    }
    @Test void itemSnapshotsAreDefensiveAndStockBounded()throws Exception {
        byte[] bytes=offer.item();bytes[0]=99;assertArrayEquals(new byte[]{1,2,3},offer.item());assertArrayEquals(new byte[]{1,2,3},store.nativeOffer(offer.id()).item());stock(NativeShop.MAX_STOCK);assertThrows(IllegalArgumentException.class,()->stock(1));assertEquals(NativeShop.MAX_STOCK,store.nativeOffer(offer.id()).stock());
    }
    @Test void stockCoOwnerCannotTradeAndPendingDeliverySurvivesRestart()throws Exception {
        stock(8);var invitation=store.invite("one",seller,other,"STOCK",2);store.acceptInvitation(invitation.token(),other,2);
        assertThrows(IllegalArgumentException.class,()->store.settleNativePurchase(UUID.randomUUID(),offer.id(),other,1,3));
        UUID receipt=UUID.randomUUID();store.settleNativePurchase(receipt,offer.id(),buyer,1,3);
        try(var connection=java.sql.DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("db"));var statement=connection.prepareStatement("SELECT delivery FROM native_trade_receipts WHERE id=?")){
            statement.setString(1,receipt.toString());try(var rows=statement.executeQuery()){assertTrue(rows.next());assertEquals("PENDING",rows.getString(1));}
        }
        assertEquals(10125,balance(other));
    }
    @Test void receiptWriteFailureRollsBackAllSettlementChanges()throws Exception {
        stock(4);long sellerBefore=balance(seller);
        try(var connection=java.sql.DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("db"));var statement=connection.createStatement()){statement.execute("CREATE TRIGGER fail_receipt BEFORE INSERT ON native_trade_receipts BEGIN SELECT RAISE(ABORT,'injected'); END");}
        UUID receipt=UUID.randomUUID();assertThrows(java.sql.SQLException.class,()->store.settleNativePurchase(receipt,offer.id(),buyer,1,3));assertEquals(10000,balance(buyer));assertEquals(sellerBefore,balance(seller));assertEquals(4,store.nativeOffer(offer.id()).stock());assertTrue(store.nativeReceipt(receipt).isEmpty());
    }
    @Test void deliveryIntentPersistsAndMismatchedConfirmationCannotCompleteIt()throws Exception {
        stock(4);UUID receipt=UUID.randomUUID();store.settleNativePurchase(receipt,offer.id(),buyer,1,3);byte[] before=new byte[32],after=new byte[32];after[0]=1;
        assertTrue(store.planNativeDelivery(receipt,buyer,before,after));assertEquals("REVIEW",store.nativeDeliveryState(receipt));store.close();store=new NookStore(dir.resolve("db"));
        assertEquals("REVIEW",store.nativeDeliveryState(receipt));assertFalse(store.planNativeDelivery(receipt,buyer,before,after));
        assertThrows(IllegalArgumentException.class,()->store.confirmNativeDelivery(receipt,buyer,before));assertThrows(IllegalArgumentException.class,()->store.confirmNativeDelivery(receipt,other,after));assertEquals("REVIEW",store.nativeDeliveryState(receipt));
        store.confirmNativeDelivery(receipt,buyer,after);store.confirmNativeDelivery(receipt,buyer,after);assertEquals("DELIVERED",store.nativeDeliveryState(receipt));assertFalse(store.planNativeDelivery(receipt,buyer,before,after));assertEquals(9750,balance(buyer));
    }
    @Test void oneUnresolvedDeliveryBlocksAnotherForSameBuyer()throws Exception {
        stock(8);UUID a=UUID.randomUUID(),b=UUID.randomUUID();store.settleNativePurchase(a,offer.id(),buyer,1,3);store.settleNativePurchase(b,offer.id(),buyer,1,4);byte[] before=new byte[32],after=new byte[32];after[0]=1;
        store.planNativeDelivery(a,buyer,before,after);assertThrows(IllegalArgumentException.class,()->store.planNativeDelivery(b,buyer,before,after));assertEquals("PENDING",store.nativeDeliveryState(b));store.confirmNativeDelivery(a,buyer,after);store.planNativeDelivery(b,buyer,before,after);assertEquals("REVIEW",store.nativeDeliveryState(b));
    }
    @Test void formerOwnerCanRecoverClosedStockAfterEvictionWithoutMoneyChanges()throws Exception {
        stock(8);store.abandon(store.abandonmentQuote("one",seller,2),seller,2);store.confirmCleared("one","staff","A1",3);store.rent("one",other,4);
        assertThrows(IllegalArgumentException.class,()->store.closeNativeOffer(offer.id(),other));store.closeNativeOffer(offer.id(),seller);store.closeNativeOffer(offer.id(),seller);long before=balance(seller);UUID receipt=UUID.randomUUID();
        var returned=store.reserveNativeStockReturn(receipt,offer.id(),seller,4,5);assertEquals(0,returned.cents());assertEquals(4,returned.quantity());assertEquals(4,store.nativeOffer(offer.id()).stock());assertEquals(before,balance(seller));assertEquals("PENDING",store.nativeDeliveryState(receipt));assertTrue(store.nativeStockReturn(receipt));
        store.reserveNativeStockReturn(receipt,offer.id(),seller,4,6);assertEquals(4,store.nativeOffer(offer.id()).stock());assertThrows(IllegalArgumentException.class,()->store.reserveNativeStockReturn(UUID.randomUUID(),offer.id(),other,4,6));
    }
    @Test void closedOffersStopSalesAndReceiptsCannotSwitchOperationType()throws Exception {
        stock(12);UUID sale=UUID.randomUUID();store.settleNativePurchase(sale,offer.id(),buyer,1,3);assertThrows(IllegalArgumentException.class,()->store.reserveNativeStockReturn(sale,offer.id(),buyer,4,4));
        store.closeNativeOffer(offer.id(),seller);assertThrows(IllegalArgumentException.class,()->store.settleNativePurchase(UUID.randomUUID(),offer.id(),buyer,2,4));UUID returned=UUID.randomUUID();store.reserveNativeStockReturn(returned,offer.id(),seller,4,4);assertThrows(IllegalArgumentException.class,()->store.settleNativePurchase(returned,offer.id(),seller,2,5));assertEquals(4,store.nativeOffer(offer.id()).stock());
    }
    byte[] fingerprint(int value){byte[] hash=new byte[32];hash[0]=(byte)value;return hash;}
    @Test void stockIntentSurvivesRestartAndCreditsOnlyOnceAfterConfirmation()throws Exception {
        UUID id=UUID.randomUUID();assertTrue(store.planNativeStockDeposit(id,offer.id(),seller,4,fingerprint(0),fingerprint(1),2));assertEquals(0,store.nativeOffer(offer.id()).stock());
        store.close();store=new NookStore(dir.resolve("db"));assertEquals("REVIEW",store.nativeStockIntentState(id));assertFalse(store.planNativeStockDeposit(id,offer.id(),seller,4,fingerprint(0),fingerprint(1),3));
        store.confirmNativeStockDeposit(id,seller,fingerprint(1),4);store.confirmNativeStockDeposit(id,seller,fingerprint(1),5);assertEquals(4,store.nativeOffer(offer.id()).stock());assertEquals("COMPLETE",store.nativeStockIntentState(id));assertFalse(store.planNativeStockDeposit(id,offer.id(),seller,4,fingerprint(0),fingerprint(1),6));
    }
    @Test void badStockConfirmationOrConflictingRetryCannotCreditItems()throws Exception {
        UUID id=UUID.randomUUID();store.planNativeStockDeposit(id,offer.id(),seller,4,fingerprint(0),fingerprint(1),2);
        assertThrows(IllegalArgumentException.class,()->store.confirmNativeStockDeposit(id,seller,fingerprint(0),3));assertThrows(IllegalArgumentException.class,()->store.confirmNativeStockDeposit(id,other,fingerprint(1),3));
        assertThrows(IllegalArgumentException.class,()->store.planNativeStockDeposit(id,offer.id(),seller,5,fingerprint(0),fingerprint(1),3));assertEquals(0,store.nativeOffer(offer.id()).stock());assertEquals("REVIEW",store.nativeStockIntentState(id));
    }
    @Test void confirmedRemovedItemsRemainRecoverableIfLeaseEndsBeforeConfirmation()throws Exception {
        UUID id=UUID.randomUUID();store.planNativeStockDeposit(id,offer.id(),seller,4,fingerprint(0),fingerprint(1),2);store.abandon(store.abandonmentQuote("one",seller,3),seller,3);store.confirmCleared("one","staff","A1",4);store.rent("one",other,5);store.closeNativeOffer(offer.id(),seller);
        store.confirmNativeStockDeposit(id,seller,fingerprint(1),6);assertEquals(4,store.nativeOffer(offer.id()).stock());store.reserveNativeStockReturn(UUID.randomUUID(),offer.id(),seller,4,7);assertEquals(0,store.nativeOffer(offer.id()).stock());
    }
    @Test void oneInventoryCannotHaveTwoStockIntents()throws Exception {
        store.planNativeStockDeposit(UUID.randomUUID(),offer.id(),seller,4,fingerprint(0),fingerprint(1),2);assertThrows(IllegalArgumentException.class,()->store.planNativeStockDeposit(UUID.randomUUID(),offer.id(),seller,4,fingerprint(0),fingerprint(1),3));
    }
    @Test void failedStockReceiptWriteKeepsIntentUncreditedForRecovery()throws Exception {
        UUID id=UUID.randomUUID();store.planNativeStockDeposit(id,offer.id(),seller,4,fingerprint(0),fingerprint(1),2);
        try(var connection=java.sql.DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("db"));var statement=connection.createStatement()){statement.execute("CREATE TRIGGER fail_stock BEFORE INSERT ON native_stock_receipts BEGIN SELECT RAISE(ABORT,'injected'); END");}
        assertThrows(java.sql.SQLException.class,()->store.confirmNativeStockDeposit(id,seller,fingerprint(1),3));assertEquals(0,store.nativeOffer(offer.id()).stock());assertEquals("REVIEW",store.nativeStockIntentState(id));
    }
    @Test void stockRemovalAndDeliveryCannotOverlapForSamePlayer()throws Exception {
        store.definePlot("two",3000);store.rent("two",other,1);var otherOffer=store.createNativeOffer("two",other,new byte[]{4},1,100,1);store.creditNativeStock(UUID.randomUUID(),otherOffer.id(),other,2,1);
        UUID deposit=UUID.randomUUID(),purchase=UUID.randomUUID();store.planNativeStockDeposit(deposit,offer.id(),seller,4,fingerprint(0),fingerprint(1),2);store.settleNativePurchase(purchase,otherOffer.id(),seller,1,2);
        assertThrows(IllegalArgumentException.class,()->store.planNativeDelivery(purchase,seller,fingerprint(1),fingerprint(2)));store.confirmNativeStockDeposit(deposit,seller,fingerprint(1),3);assertTrue(store.planNativeDelivery(purchase,seller,fingerprint(1),fingerprint(2)));
        assertThrows(IllegalArgumentException.class,()->store.planNativeStockDeposit(UUID.randomUUID(),offer.id(),seller,4,fingerprint(2),fingerprint(3),4));
    }
}
