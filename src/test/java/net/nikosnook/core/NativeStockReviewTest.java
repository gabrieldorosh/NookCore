package net.nikosnook.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeStockReviewTest {
    @TempDir Path dir;NookStore store;UUID owner=UUID.randomUUID(),receipt=UUID.randomUUID();NativeShop.Offer offer;
    byte[] before=new byte[32],after=new byte[32];
    @BeforeEach void setup()throws Exception {
        after[0]=1;store=new NookStore(dir.resolve("db"));store.join(owner,"Moon",0,10000);
        store.definePlot("one",3000);store.rent("one",owner,0);
        offer=store.publishNativeOffer("world:1:2:3","one",owner,new byte[]{12,34},4,250,1);
        store.planNativeStockDeposit(receipt,offer.id(),owner,8,before,after,1234);
    }
    @AfterEach void close()throws Exception {store.close();}
    @Test void pendingDetailPreservesUncertainOperationAndOwnsItsSnapshots()throws Exception {
        long balance=store.account(owner).orElseThrow().cents();var detail=store.nativeStockReview(receipt);
        assertEquals(receipt,detail.receipt());assertEquals(offer.id(),detail.offer());assertEquals(owner,detail.actor());
        assertEquals("Moon",detail.actorName());assertEquals("one",detail.plot());assertEquals(8,detail.quantity());assertEquals(1234,detail.time());
        assertEquals("REVIEW",detail.state());assertNull(detail.creditedQuantity());assertEquals(0,detail.currentStock());
        assertArrayEquals(new byte[]{12,34},detail.item());assertArrayEquals(before,detail.beforeHash());assertArrayEquals(after,detail.afterHash());
        detail.item()[0]=0;detail.beforeHash()[0]=7;detail.afterHash()[0]=7;
        assertArrayEquals(new byte[]{12,34},detail.item());assertArrayEquals(before,detail.beforeHash());assertArrayEquals(after,detail.afterHash());
        assertTrue(store.nativeNeedsReview(owner));assertEquals("REVIEW",store.nativeStockIntentState(receipt));
        assertEquals(0,store.nativeOffer(offer.id()).stock());assertEquals(balance,store.account(owner).orElseThrow().cents());
    }
    @Test void completedDetailSeparatesReceiptCreditFromCurrentStock()throws Exception {
        store.confirmNativeStockDeposit(receipt,owner,after,2);store.creditNativeStock(UUID.randomUUID(),offer.id(),owner,5,3);
        var detail=store.nativeStockReview(receipt);assertEquals("COMPLETE",detail.state());
        assertEquals(Integer.valueOf(8),detail.creditedQuantity());assertEquals(13,detail.currentStock());assertFalse(store.nativeNeedsReview(owner));
    }
    @Test void unknownReceiptDoesNotClearExistingReview()throws Exception {
        assertThrows(IllegalArgumentException.class,()->store.nativeStockReview(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,()->store.nativeStockReview(null));
        assertEquals(1,store.nativeReviews().size());assertTrue(store.nativeNeedsReview(owner));assertEquals(0,store.nativeOffer(offer.id()).stock());
    }
}
