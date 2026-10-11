package net.nikosnook.core;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;import java.nio.file.*;import java.util.*;
class ShopRetirementTest {
 @TempDir Path dir;NookStore store;UUID owner=UUID.randomUUID(),buyer=UUID.randomUUID();NativeShop.Offer offer;
 @BeforeEach void setup()throws Exception{store=new NookStore(dir.resolve("db"));store.join(owner,"Owner",0,10000);store.join(buyer,"Buyer",0,10000);store.definePlot("one",100);store.rent("one",owner,0);offer=store.publishNativeOffer("loc","one",owner,new byte[]{1},1,100,1);}
 @AfterEach void close()throws Exception{store.close();}
 @Test void emptyClosedShopReleasesLocationButRetainsHistory()throws Exception{store.closeNativeOffer(offer.id(),owner);store.retireEmptyNativeLocation(offer.id());assertTrue(store.nativeAt("loc").isEmpty());assertTrue(store.nativeOffer(offer.id()).closed());assertNotNull(store.publishNativeOffer("loc","one",owner,new byte[]{2},1,200,2));}
 @Test void stockCannotBeOrphanedByReplacement()throws Exception{store.creditNativeStock(UUID.randomUUID(),offer.id(),owner,1,2);store.closeNativeOffer(offer.id(),owner);assertThrows(IllegalArgumentException.class,()->store.retireEmptyNativeLocation(offer.id()));assertThrows(IllegalArgumentException.class,()->store.publishNativeOffer("loc","one",owner,new byte[]{2},1,200,3));assertEquals(1,store.nativeOffer(offer.id()).stock());}
 @Test void pendingDeliveryKeepsCollectionLocation()throws Exception{store.creditNativeStock(UUID.randomUUID(),offer.id(),owner,1,2);var receipt=store.settleNativePurchase(UUID.randomUUID(),offer.id(),buyer,offer.revision(),3);store.closeNativeOffer(offer.id(),owner);store.retireEmptyNativeLocation(offer.id());assertTrue(store.nativeAt("loc").isPresent());assertFalse(store.nativeFullyCollected(offer.id()));assertEquals(Map.of(owner,100L),store.nativeSaleShares(receipt.id()));}
}
