package net.nikosnook.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlotAbandonmentTest {
    @TempDir Path dir;
    NookStore store;
    UUID owner=UUID.randomUUID(),member=UUID.randomUUID(),guest=UUID.randomUUID();
    long now=1_800_000_000_000L;
    static final long WEEK=NookStore.WEEK;
    @BeforeEach void setup()throws Exception {
        store=new NookStore(dir.resolve("nooks.db"));
        store.join(owner,"Owner",now,100_000);store.join(member,"Member",now,100_000);store.join(guest,"Guest",now,100_000);
        for(String plot:List.of("one","two","three"))store.definePlot(plot,3000);
        store.rent("one",owner,now);
    }
    @AfterEach void close()throws Exception {store.close();}
    @Test void currentWeekNeverRefundedEvenImmediatelyAfterRent()throws Exception {
        assertEquals(0,store.abandonmentQuote("one",owner,now).refund());
        assertEquals(0,store.abandonmentQuote("one",owner,now+1).refund());
        assertEquals(0,store.abandonmentQuote("one",owner,now+WEEK-1).refund());
    }
    @Test void onlyWhollyUnusedFutureWeeksRefundedAtAnniversaryBoundary()throws Exception {
        store.prepay("one",owner,2,now);
        assertEquals(6000,store.abandonmentQuote("one",owner,now).refund());
        assertEquals(6000,store.abandonmentQuote("one",owner,now+WEEK-1).refund());
        assertEquals(3000,store.abandonmentQuote("one",owner,now+WEEK).refund());
        assertEquals(3000,store.abandonmentQuote("one",owner,now+WEEK+1).refund());
        assertEquals(0,store.abandonmentQuote("one",owner,now+2*WEEK).refund());
    }
    @Test void closesSalesRefundsOnceAndReleasesEveryonesAllowance()throws Exception {
        store.prepay("one",owner,2,now);
        store.acceptInvitation(store.invite("one",owner,member,"BUILD_STOCK",now).token(),member,now);
        var pending=store.invite("one",owner,guest,"STOCK",now);
        var quote=store.abandonmentQuote("one",owner,now+1);
        long before=store.account(owner).orElseThrow().cents();
        assertEquals(6000,store.abandon(quote,owner,now+2));
        assertEquals(before+6000,store.account(owner).orElseThrow().cents());
        assertEquals("RECLAIM",store.plot("one").state());assertEquals(owner,store.plot("one").owner());
        assertTrue(store.abandoned("one"));assertFalse(store.canTrade("one",now+2));assertTrue(store.members("one").isEmpty());
        assertTrue(store.invitations(guest,now+2).isEmpty());
        assertThrows(IllegalArgumentException.class,()->store.acceptInvitation(pending.token(),guest,now+2));
        assertThrows(IllegalArgumentException.class,()->store.abandon(quote,owner,now+3));
        assertEquals(1,store.history(owner).stream().filter(e->e.kind().equals("rent-refund")).count());
        for(var action:PlotPermissions.Action.values()){
            assertFalse(new PlotPermissions(store).allowed("one",owner,action));
            assertFalse(new PlotPermissions(store).allowed("one",member,action));
        }
        store.rent("two",owner,now+3);store.rent("three",member,now+3);
        assertThrows(IllegalArgumentException.class,()->store.rent("one",guest,now+3));
    }
    @Test void anotherPlayerCannotQuoteOrConfirmAbandonment()throws Exception {
        var quote=store.abandonmentQuote("one",owner,now);
        assertThrows(IllegalArgumentException.class,()->store.abandonmentQuote("one",member,now));
        assertThrows(IllegalArgumentException.class,()->store.abandon(quote,member,now));
        assertEquals("ACTIVE",store.plot("one").state());
    }
    @Test void prepayAfterQuoteRequiresAnotherReview()throws Exception {
        var quote=store.abandonmentQuote("one",owner,now);store.prepay("one",owner,1,now);
        long balance=store.account(owner).orElseThrow().cents();
        assertThrows(IllegalArgumentException.class,()->store.abandon(quote,owner,now+1));
        assertEquals(balance,store.account(owner).orElseThrow().cents());assertEquals("ACTIVE",store.plot("one").state());
    }
    @Test void confirmationCrossingRefundBoundaryRequiresReview()throws Exception {
        store.prepay("one",owner,2,now);var quote=store.abandonmentQuote("one",owner,now+WEEK-1);
        assertThrows(IllegalArgumentException.class,()->store.abandon(quote,owner,now+WEEK));
        assertEquals("OWNER",store.role("one",owner));
    }
    @Test void refundFailureRollsBackClosureAndMembershipRemoval()throws Exception {
        store.prepay("one",owner,1,now);
        store.adjust(owner,Money.MAX-store.account(owner).orElseThrow().cents(),"staff","balance limit fixture",now);
        var quote=store.abandonmentQuote("one",owner,now);
        assertThrows(IllegalArgumentException.class,()->store.abandon(quote,owner,now));
        assertEquals(Money.MAX,store.account(owner).orElseThrow().cents());assertEquals("ACTIVE",store.plot("one").state());
        assertEquals("OWNER",store.role("one",owner));assertFalse(store.abandoned("one"));
    }
    @Test void overdueLeaseCanBeAbandonedWithoutRefundOrLaterRenewal()throws Exception {
        store.setAbsence("one",now+4*WEEK,"staff","holiday",now);
        // An overdue ACTIVE lease can be closed before billing ticks, without charging for another week.
        var quote=store.abandonmentQuote("one",owner,now+WEEK+1);assertEquals(0,quote.refund());
        long balance=store.account(owner).orElseThrow().cents();store.abandon(quote,owner,now+WEEK+1);store.tick(now+2*WEEK);
        assertEquals(balance,store.account(owner).orElseThrow().cents());assertEquals("RECLAIM",store.plot("one").state());
    }
    @Test void graceAndReclaimCanReleaseAllowanceWithoutRefund()throws Exception {
        store.adjust(owner,-store.account(owner).orElseThrow().cents(),"staff","empty test balance",now);
        store.tick(now+WEEK);assertEquals("GRACE",store.plot("one").state());
        assertEquals(0,store.abandonmentQuote("one",owner,now+WEEK).refund());
        store.tick(now+2*WEEK);assertEquals("RECLAIM",store.plot("one").state());
        store.abandon(store.abandonmentQuote("one",owner,now+2*WEEK),owner,now+2*WEEK);
        assertEquals("NONE",store.role("one",owner));
    }
    @Test void restartKeepsRefundAuditAndClosedPlot()throws Exception {
        store.prepay("one",owner,1,now);store.abandon(store.abandonmentQuote("one",owner,now),owner,now);
        long balance=store.account(owner).orElseThrow().cents();store.close();store=new NookStore(dir.resolve("nooks.db"));
        assertTrue(store.abandoned("one"));assertEquals("RECLAIM",store.plot("one").state());assertEquals("NONE",store.role("one",owner));
        assertEquals(balance,store.account(owner).orElseThrow().cents());
    }
    @Test void staffClearRequiresArchiveAndOldQuoteCannotCloseNewLease()throws Exception {
        var old=store.abandonmentQuote("one",owner,now);store.abandon(old,owner,now);
        assertThrows(IllegalArgumentException.class,()->store.confirmCleared("one","staff","",now));
        store.confirmCleared("one","staff","archive/owner-one",now+1);store.rent("one",owner,now+2);
        assertFalse(store.abandoned("one"));assertNotEquals(old.lease(),store.abandonmentQuote("one",owner,now+2).lease());
        assertThrows(IllegalArgumentException.class,()->store.abandon(old,owner,now+3));
        assertTrue(store.canTrade("one",now+3));
    }
    @Test void existingLeaseMigrationIsStableAndDoesNotChangeBalances()throws Exception {
        store.close();
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("nooks.db").toAbsolutePath());var sql=db.createStatement()){
            sql.execute("DROP TABLE plot_abandonments");sql.execute("DROP TABLE plot_leases");
        }
        store=new NookStore(dir.resolve("nooks.db"));var quote=store.abandonmentQuote("one",owner,now);
        assertEquals(97000,store.account(owner).orElseThrow().cents());
        store.close();store=new NookStore(dir.resolve("nooks.db"));assertEquals(quote,store.abandonmentQuote("one",owner,now));
    }
}
