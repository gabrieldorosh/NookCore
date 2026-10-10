package net.nikosnook.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeStockRecoveryTest {
    @TempDir Path dir;NookStore store;UUID owner=UUID.randomUUID(),receipt=UUID.randomUUID();NativeShop.Offer offer;
    byte[] before=new byte[32],after=new byte[32];
    @BeforeEach void setup()throws Exception {
        after[0]=1;store=new NookStore(dir.resolve("db"));store.join(owner,"Moon",0,10000);
        store.definePlot("one",3000);store.rent("one",owner,0);
        offer=store.publishNativeOffer("world:1:2:3","one",owner,new byte[]{12,34},4,250,1);
        store.planNativeStockDeposit(receipt,offer.id(),owner,8,before,after,1234);
    }
    @AfterEach void close()throws Exception {store.close();}
    boolean resolve(NativeStockReview preview,NativeStockReview.Recovery action)throws Exception {
        return store.resolveNativeStock(preview,action,"console","Player and staff checked the missing items",2000);
    }
    @Test void creditIsAuditedAndCannotRepeatAfterRestart()throws Exception {
        var preview=store.nativeStockReview(receipt);long balance=store.account(owner).orElseThrow().cents();
        assertTrue(resolve(preview,NativeStockReview.Recovery.CREDIT));
        var done=store.nativeStockReview(receipt);assertEquals("COMPLETE",done.state());assertEquals(8,done.currentStock());
        assertEquals(Integer.valueOf(8),done.creditedQuantity());assertEquals(NativeStockReview.Recovery.CREDIT,done.resolution().recovery());
        assertEquals("console",done.resolution().staff());assertEquals("Player and staff checked the missing items",done.resolution().reason());assertEquals(2000,done.resolution().time());
        assertEquals(balance,store.account(owner).orElseThrow().cents());assertFalse(store.nativeNeedsReview(owner));assertTrue(store.nativeReviews().isEmpty());
        store.close();store=new NookStore(dir.resolve("db"));assertFalse(resolve(preview,NativeStockReview.Recovery.CREDIT));
        assertEquals(8,store.nativeOffer(offer.id()).stock());assertThrows(IllegalArgumentException.class,()->resolve(preview,NativeStockReview.Recovery.NO_CREDIT));
        assertThrows(IllegalArgumentException.class,()->store.resolveNativeStock(preview,NativeStockReview.Recovery.CREDIT,"another admin","different story",3000));
        assertEquals(8,store.nativeOffer(offer.id()).stock());
    }
    @Test void noCreditLeavesStockAndMoneyAloneAndReleasesPendingLock()throws Exception {
        var preview=store.nativeStockReview(receipt);long balance=store.account(owner).orElseThrow().cents();
        assertTrue(resolve(preview,NativeStockReview.Recovery.NO_CREDIT));
        var done=store.nativeStockReview(receipt);assertEquals("COMPLETE",done.state());assertNull(done.creditedQuantity());assertEquals(0,done.currentStock());
        assertEquals(NativeStockReview.Recovery.NO_CREDIT,done.resolution().recovery());assertFalse(store.nativeNeedsReview(owner));
        assertEquals(balance,store.account(owner).orElseThrow().cents());
        store.close();store=new NookStore(dir.resolve("db"));assertFalse(resolve(preview,NativeStockReview.Recovery.NO_CREDIT));
        store.confirmNativeStockDeposit(receipt,owner,after,3000);assertEquals(0,store.nativeOffer(offer.id()).stock());
        assertThrows(IllegalArgumentException.class,()->resolve(preview,NativeStockReview.Recovery.CREDIT));
        assertTrue(store.planNativeStockDeposit(UUID.randomUUID(),offer.id(),owner,4,before,after,4000));
    }
    @Test void staleStockSnapshotAndAlreadyCreditedReceiptAreRejected()throws Exception {
        var preview=store.nativeStockReview(receipt);store.creditNativeStock(UUID.randomUUID(),offer.id(),owner,5,1500);
        assertThrows(IllegalArgumentException.class,()->resolve(preview,NativeStockReview.Recovery.CREDIT));
        assertEquals("REVIEW",store.nativeStockIntentState(receipt));assertNull(store.nativeStockReview(receipt).resolution());
        var fresh=store.nativeStockReview(receipt);store.confirmNativeStockDeposit(receipt,owner,after,1600);
        assertThrows(IllegalArgumentException.class,()->resolve(fresh,NativeStockReview.Recovery.NO_CREDIT));assertEquals(13,store.nativeOffer(offer.id()).stock());
    }
    @Test void alteredItemQuantityAndHashSnapshotsAreRejected()throws Exception {
        var p=store.nativeStockReview(receipt);byte[] changedHash=p.afterHash();changedHash[0]=12;
        var changedQuantity=new NativeStockReview(p.receipt(),p.offer(),p.actor(),p.actorName(),9,p.time(),p.state(),p.plot(),p.item(),p.beforeHash(),p.afterHash(),p.creditedQuantity(),p.currentStock(),null);
        var changedItem=new NativeStockReview(p.receipt(),p.offer(),p.actor(),p.actorName(),p.quantity(),p.time(),p.state(),p.plot(),new byte[]{56},p.beforeHash(),p.afterHash(),p.creditedQuantity(),p.currentStock(),null);
        var changedAfter=new NativeStockReview(p.receipt(),p.offer(),p.actor(),p.actorName(),p.quantity(),p.time(),p.state(),p.plot(),p.item(),p.beforeHash(),changedHash,p.creditedQuantity(),p.currentStock(),null);
        for(var changed:List.of(changedQuantity,changedItem,changedAfter))assertThrows(IllegalArgumentException.class,()->resolve(changed,NativeStockReview.Recovery.CREDIT));
        assertEquals(0,store.nativeOffer(offer.id()).stock());assertEquals("REVIEW",store.nativeStockIntentState(receipt));
    }
    @Test void auditWriteFailureRollsBackCreditReceiptAndState()throws Exception {
        var preview=store.nativeStockReview(receipt);
        sql("CREATE TRIGGER fail_recovery BEFORE INSERT ON native_stock_resolutions BEGIN SELECT RAISE(ABORT,'injected'); END");
        assertThrows(SQLException.class,()->resolve(preview,NativeStockReview.Recovery.CREDIT));
        var afterFailure=store.nativeStockReview(receipt);assertEquals(0,afterFailure.currentStock());assertNull(afterFailure.creditedQuantity());
        assertNull(afterFailure.resolution());assertEquals("REVIEW",afterFailure.state());assertTrue(store.nativeNeedsReview(owner));
        assertThrows(SQLException.class,()->resolve(preview,NativeStockReview.Recovery.NO_CREDIT));assertEquals("REVIEW",store.nativeStockIntentState(receipt));
        sql("DROP TRIGGER fail_recovery");assertTrue(resolve(preview,NativeStockReview.Recovery.CREDIT));assertEquals(8,store.nativeOffer(offer.id()).stock());
    }
    @Test void fullStockCannotOverflowButCanBeResolvedWithoutCredit()throws Exception {
        store.creditNativeStock(UUID.randomUUID(),offer.id(),owner,NativeShop.MAX_STOCK-4,1500);var preview=store.nativeStockReview(receipt);
        assertThrows(IllegalArgumentException.class,()->resolve(preview,NativeStockReview.Recovery.CREDIT));
        assertEquals("REVIEW",store.nativeStockIntentState(receipt));assertNull(store.nativeStockReview(receipt).resolution());
        assertTrue(resolve(preview,NativeStockReview.Recovery.NO_CREDIT));assertEquals(NativeShop.MAX_STOCK-4,store.nativeOffer(offer.id()).stock());
    }
    @Test void emptyAuditDetailsDoNotResolveAnything()throws Exception {
        var preview=store.nativeStockReview(receipt);
        assertThrows(IllegalArgumentException.class,()->store.resolveNativeStock(preview,NativeStockReview.Recovery.CREDIT,"","checked",2000));
        assertThrows(IllegalArgumentException.class,()->store.resolveNativeStock(preview,NativeStockReview.Recovery.CREDIT,"console"," ",2000));
        assertThrows(IllegalArgumentException.class,()->store.resolveNativeStock(preview,NativeStockReview.Recovery.CREDIT,"console","line\nbreak",2000));
        assertThrows(IllegalArgumentException.class,()->store.resolveNativeStock(preview,null,"console","checked",2000));
        assertThrows(IllegalArgumentException.class,()->store.resolveNativeStock(preview,NativeStockReview.Recovery.CREDIT,"console","checked",1000));
        assertEquals("REVIEW",store.nativeStockIntentState(receipt));assertEquals(0,store.nativeOffer(offer.id()).stock());
    }
    void sql(String sql)throws Exception {try(var c=DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("db"));var s=c.createStatement()){s.execute(sql);}}
}
