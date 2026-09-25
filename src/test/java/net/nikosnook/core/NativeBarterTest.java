package net.nikosnook.core;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class NativeBarterTest {
 @TempDir Path dir;NookStore store;UUID owner=UUID.randomUUID(),buyer=UUID.randomUUID(),co=UUID.randomUUID();NativeShop.Offer offer;
 @BeforeEach void setup()throws Exception {store=new NookStore(dir.resolve("db"));for(UUID p:List.of(owner,buyer,co))store.join(p,p.toString(),0,10000);store.definePlot("one",3000);store.rent("one",owner,0);offer=store.publishNativeOffer("world:1:2:3","one",owner,new byte[]{1},4,250,new byte[]{2},8,1);store.creditNativeStock(UUID.randomUUID(),offer.id(),owner,8,2);}
 @AfterEach void end()throws Exception{store.close();}
 byte[] hash(int n){byte[] b=new byte[32];b[0]=(byte)n;return b;}
 void pay(UUID who,int count)throws Exception {UUID receipt=UUID.randomUUID();assertTrue(store.planNativePayment(receipt,offer.id(),who,count,hash(0),hash(1),3));store.confirmNativePayment(receipt,who,hash(1));}
 @Test void barterMovesItemsAndStockWithoutChangingNooksAndSurvivesRestart()throws Exception {
  long seller=store.account(owner).orElseThrow().cents();pay(buyer,8);UUID id=UUID.randomUUID();var receipt=store.settleNativePurchase(id,offer.id(),buyer,1,4);assertEquals(0,receipt.cents());assertEquals(4,receipt.quantity());assertEquals(4,store.nativeOffer(offer.id()).stock());assertEquals(0,store.nativePaymentBalance(buyer,offer.id()));assertEquals(8,store.nativePaymentBalance(owner,offer.id()));assertEquals(seller,store.account(owner).orElseThrow().cents());assertEquals(10000,store.account(buyer).orElseThrow().cents());
  store.close();store=new NookStore(dir.resolve("db"));store.settleNativePurchase(id,offer.id(),buyer,1,5);assertEquals(8,store.nativePaymentBalance(owner,offer.id()));assertEquals(4,store.nativeOffer(offer.id()).stock());
 }
 @Test void itemPaymentsAreNotSharedWithCoOwners()throws Exception {
  var invite=store.invite("one",owner,co,"STOCK",2);store.acceptInvitation(invite.token(),co,2);
  pay(buyer,8);store.settleNativePurchase(UUID.randomUUID(),offer.id(),buyer,1,4);
  assertEquals(8,store.nativePaymentBalance(owner,offer.id()));assertEquals(0,store.nativePaymentBalance(co,offer.id()));assertEquals(10000,store.account(co).orElseThrow().cents());
 }
 @Test void noPaymentMeansNoSaleEvenWithNooksBalance()throws Exception {
  assertThrows(IllegalArgumentException.class,()->store.settleNativePurchase(UUID.randomUUID(),offer.id(),buyer,1,4));assertEquals(8,store.nativeOffer(offer.id()).stock());assertEquals(10000,store.account(buyer).orElseThrow().cents());
 }
 @Test void pendingPaymentCannotBeCreditedTwiceOrMixedWithStockDelivery()throws Exception {
  UUID id=UUID.randomUUID();assertTrue(store.planNativePayment(id,offer.id(),buyer,8,hash(0),hash(1),3));assertFalse(store.planNativePayment(id,offer.id(),buyer,8,hash(0),hash(1),3));assertTrue(store.nativeNeedsReview(buyer));assertThrows(IllegalArgumentException.class,()->store.planNativePayment(UUID.randomUUID(),offer.id(),buyer,8,hash(0),hash(1),3));assertThrows(IllegalArgumentException.class,()->store.confirmNativePayment(id,buyer,hash(0)));assertEquals(0,store.nativePaymentBalance(buyer,offer.id()));store.confirmNativePayment(id,buyer,hash(1));store.confirmNativePayment(id,buyer,hash(1));assertEquals(8,store.nativePaymentBalance(buyer,offer.id()));
 }
 @Test void failedSettlementLeavesBuyerPaymentRecoverableWithoutGivingSellerItems()throws Exception {
  pay(buyer,8);store.closeNativeOffer(offer.id(),owner);assertThrows(IllegalArgumentException.class,()->store.settleNativePurchase(UUID.randomUUID(),offer.id(),buyer,1,4));assertEquals(8,store.nativePaymentBalance(buyer,offer.id()));assertEquals(0,store.nativePaymentBalance(owner,offer.id()));var returned=store.reserveNativePaymentReturn(UUID.randomUUID(),offer.id(),buyer,8,5);assertArrayEquals(new byte[]{2},returned.item());assertEquals(0,store.nativePaymentBalance(buyer,offer.id()));assertEquals("PENDING",store.nativeDeliveryState(returned.id()));
 }
 @Test void injectedReceiptFailureRollsBackBarterBalancesAndStock()throws Exception {
  pay(buyer,8);try(var c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("db"));var s=c.createStatement()){s.execute("CREATE TRIGGER fail_sale BEFORE INSERT ON native_trade_receipts BEGIN SELECT RAISE(ABORT,'injected'); END");}
  assertThrows(java.sql.SQLException.class,()->store.settleNativePurchase(UUID.randomUUID(),offer.id(),buyer,1,4));assertEquals(8,store.nativePaymentBalance(buyer,offer.id()));assertEquals(0,store.nativePaymentBalance(owner,offer.id()));assertEquals(8,store.nativeOffer(offer.id()).stock());
 }
 @Test void formerOwnerCanCollectItemProceedsAfterRentalEnds()throws Exception {
  pay(buyer,8);store.settleNativePurchase(UUID.randomUUID(),offer.id(),buyer,1,4);store.abandon(store.abandonmentQuote("one",owner,5),owner,5);store.confirmCleared("one","staff","archive",6);store.rent("one",buyer,7);
  assertEquals(List.of(offer.id()),store.nativePaymentCollections(owner));var returned=store.reserveNativePaymentReturn(UUID.randomUUID(),offer.id(),owner,8,8);assertArrayEquals(new byte[]{2},returned.item());assertThrows(IllegalArgumentException.class,()->store.reserveNativePaymentReturn(UUID.randomUUID(),offer.id(),buyer,8,8));
 }
 @Test void stockMemberCanDepositButNotPayOrTradeAndRevocationBlocksMoreDeposits()throws Exception {
  var invite=store.invite("one",owner,co,"STOCK",2);store.acceptInvitation(invite.token(),co,2);UUID stock=UUID.randomUUID();assertTrue(store.planNativeStockDeposit(stock,offer.id(),co,4,hash(0),hash(1),3));store.confirmNativeStockDeposit(stock,co,hash(1),3);assertEquals(12,store.nativeOffer(offer.id()).stock());
  assertThrows(IllegalArgumentException.class,()->pay(co,8));assertThrows(IllegalArgumentException.class,()->store.settleNativePurchase(UUID.randomUUID(),offer.id(),co,1,4));store.changeRole("one",owner,co,"BUILD",5);assertThrows(IllegalArgumentException.class,()->store.planNativeStockDeposit(UUID.randomUUID(),offer.id(),co,4,hash(0),hash(1),6));
 }
 @Test void paymentCustodyFailureDoesNotCreditAndCannotReplay()throws Exception {
  var inventory=new NativeCustodyTest.Inventory(8);inventory.failSave=true;assertThrows(java.io.IOException.class,()->new NativeCustody(store).payment(offer.id(),buyer,8,"exact",inventory,3));assertEquals(0,store.nativePaymentBalance(buyer,offer.id()));assertTrue(store.nativeNeedsReview(buyer));assertEquals(1,inventory.writes);
 }
}
