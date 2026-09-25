package net.nikosnook.core;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class NativeCustodyTest {
 @TempDir Path dir;NookStore store;UUID owner=UUID.randomUUID(),buyer=UUID.randomUUID();NativeShop.Offer offer;
 @BeforeEach void setup()throws Exception {store=new NookStore(dir.resolve("db"));store.join(owner,"Owner",0,10000);store.join(buyer,"Buyer",0,10000);store.definePlot("one",3000);store.rent("one",owner,0);offer=store.publishNativeOffer("world:1:2:3","one",owner,new byte[]{1},4,250,1);}
 @AfterEach void end()throws Exception{store.close();}
 static class Inventory implements NativeCustody.Inventory {
  List<NativeInventoryPlan.Stack> slots;boolean failSave,failApply;int writes;
  Inventory(int count){slots=new ArrayList<>(Arrays.asList(count==0?null:new NativeInventoryPlan.Stack("exact",count,64),null));}
  public List<NativeInventoryPlan.Stack> capture(){return slots;}
  public void apply(List<NativeInventoryPlan.Stack> next)throws Exception{writes++;if(failApply)throw new java.io.IOException("injected write failure");slots=next;}
  public byte[] persist(List<NativeInventoryPlan.Stack> expected,UUID receipt)throws Exception{if(failSave)throw new java.io.IOException("injected save failure");return NativeInventoryPlan.hash(slots);}
 }
 @Test void publicationAndLocationAreAtomicAndDuplicateCannotLeakOffer()throws Exception {
  assertEquals(offer.id(),store.nativeAt("world:1:2:3").orElseThrow().id());assertThrows(IllegalArgumentException.class,()->store.publishNativeOffer("world:1:2:3","one",owner,new byte[]{1},4,250,2));assertEquals(1,store.nativeOffers(owner).size());
  try(var c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("db"));var s=c.createStatement()){s.execute("CREATE TRIGGER location_failure BEFORE INSERT ON native_locations BEGIN SELECT RAISE(ABORT,'injected'); END");}
  assertThrows(java.sql.SQLException.class,()->store.publishNativeOffer("world:9:2:3","one",owner,new byte[]{1},4,250,2));assertEquals(1,store.nativeOffers(owner).size());
 }
 @Test void oldLeaseCanBeReplacedWithoutLosingFormerStock()throws Exception {
  store.creditNativeStock(UUID.randomUUID(),offer.id(),owner,8,2);store.abandon(store.abandonmentQuote("one",owner,3),owner,3);store.confirmCleared("one","staff","archive",4);store.rent("one",buyer,5);
  var replacement=store.publishNativeOffer("world:1:2:3","one",buyer,new byte[]{2},1,100,6);assertEquals(replacement.id(),store.nativeAt("world:1:2:3").orElseThrow().id());assertTrue(store.nativeOffer(offer.id()).closed());assertEquals(8,store.nativeOffer(offer.id()).stock());
 }
 @Test void depositAppliesOnceAndCreditsOnlyAfterSave()throws Exception {
  var inventory=new Inventory(8);new NativeCustody(store).deposit(offer.id(),owner,4,"exact",inventory,2);assertEquals(4,store.nativeOffer(offer.id()).stock());assertEquals(4,inventory.slots.getFirst().count());assertFalse(store.nativeNeedsReview(owner));
 }
 @Test void uncertainDepositIsNotCreditedAndBlocksAnotherWriteAfterRestart()throws Exception {
  var inventory=new Inventory(8);inventory.failSave=true;assertThrows(java.io.IOException.class,()->new NativeCustody(store).deposit(offer.id(),owner,4,"exact",inventory,2));assertEquals(0,store.nativeOffer(offer.id()).stock());store.close();store=new NookStore(dir.resolve("db"));assertTrue(store.nativeNeedsReview(owner));inventory.failSave=false;assertThrows(IllegalArgumentException.class,()->new NativeCustody(store).deposit(offer.id(),owner,4,"exact",inventory,3));assertEquals(1,inventory.writes);
 }
 @Test void failedInventoryWriteStillLeavesReviewNotNewStock()throws Exception {
  var inventory=new Inventory(8);inventory.failApply=true;assertThrows(java.io.IOException.class,()->new NativeCustody(store).deposit(offer.id(),owner,4,"exact",inventory,2));assertTrue(store.nativeNeedsReview(owner));assertEquals(0,store.nativeOffer(offer.id()).stock());
 }
 NativeShop.Receipt purchase()throws Exception {store.creditNativeStock(UUID.randomUUID(),offer.id(),owner,4,2);return store.settleNativePurchase(UUID.randomUUID(),offer.id(),buyer,1,3);}
 @Test void deliveryCompletesOnlyAfterSavedInventoryAndCannotReplay()throws Exception {
  var receipt=purchase();var inventory=new Inventory(0);var custody=new NativeCustody(store);custody.deliver(receipt.id(),buyer,new NativeInventoryPlan.Stack("exact",1,64),inventory);assertEquals(4,inventory.slots.getFirst().count());assertEquals("DELIVERED",store.nativeDeliveryState(receipt.id()));assertTrue(store.nativeDeliveries(buyer).isEmpty());assertThrows(IllegalArgumentException.class,()->custody.deliver(receipt.id(),buyer,new NativeInventoryPlan.Stack("exact",1,64),inventory));assertEquals(1,inventory.writes);
 }
 @Test void uncertainDeliverySurvivesRestartAndNeverReplays()throws Exception {
  var receipt=purchase();var inventory=new Inventory(0);inventory.failSave=true;assertThrows(java.io.IOException.class,()->new NativeCustody(store).deliver(receipt.id(),buyer,new NativeInventoryPlan.Stack("exact",1,64),inventory));store.close();store=new NookStore(dir.resolve("db"));assertEquals("REVIEW",store.nativeDeliveryState(receipt.id()));assertEquals(1,store.nativeDeliveries(buyer).size());inventory.failSave=false;assertThrows(IllegalArgumentException.class,()->new NativeCustody(store).deliver(receipt.id(),buyer,new NativeInventoryPlan.Stack("exact",1,64),inventory));assertEquals(1,inventory.writes);
 }
 @Test void fullInventoryLeavesEntitlementPendingAndMoneyIsNotTakenAgain()throws Exception {
  var receipt=purchase();var inventory=new Inventory(64);inventory.slots=List.of(new NativeInventoryPlan.Stack("other",64,64));assertThrows(IllegalArgumentException.class,()->new NativeCustody(store).deliver(receipt.id(),buyer,new NativeInventoryPlan.Stack("exact",1,64),inventory));assertEquals("PENDING",store.nativeDeliveryState(receipt.id()));assertEquals(0,inventory.writes);assertEquals(9750,store.account(buyer).orElseThrow().cents());assertTrue(store.nativeDeliveries(owner).isEmpty());
 }
 @Test void anotherPlayerCannotClaimPurchasedItems()throws Exception {
  var receipt=purchase();var inventory=new Inventory(0);assertThrows(IllegalArgumentException.class,()->new NativeCustody(store).deliver(receipt.id(),owner,new NativeInventoryPlan.Stack("exact",1,64),inventory));assertEquals(0,inventory.writes);assertEquals("PENDING",store.nativeDeliveryState(receipt.id()));
 }
}
