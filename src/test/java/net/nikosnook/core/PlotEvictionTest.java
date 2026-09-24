package net.nikosnook.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PlotEvictionTest {
    @TempDir Path dir;
    NookStore store;UUID owner=UUID.randomUUID(),member=UUID.randomUUID();
    long now=Instant.parse("2026-09-24T11:00:00Z").toEpochMilli();
    @BeforeEach void setup()throws Exception {
        store=new NookStore(dir.resolve("nooks.db"));store.join(owner,"Owner",now,10000);store.join(member,"Member",now,10000);
        store.definePlot("one",700);store.rent("one",owner,now);
        store.acceptInvitation(store.invite("one",owner,member,"STOCK",now).token(),member,now);
    }
    @AfterEach void close()throws Exception {store.close();}
    @Test void refundsUnusedDaysOnceClosesSalesAndRetainsCollectionOwner()throws Exception {
        store.prepay("one",owner,1,now);var quote=store.evictionQuote("one",now);assertEquals(1300,quote.refund());
        long before=store.account(owner).orElseThrow().cents();assertEquals(1300,store.evict(quote,"Admin","District redesign",now));
        assertEquals(before+1300,store.account(owner).orElseThrow().cents());assertEquals("RECLAIM",store.plot("one").state());
        assertEquals(owner,store.plot("one").owner());assertTrue(store.members("one").isEmpty());assertFalse(store.canTrade("one",now));
        assertThrows(IllegalArgumentException.class,()->store.evict(quote,"Admin","Again",now));
        store.confirmCleared("one","Admin","Belongings in chest A",now);assertEquals("AVAILABLE",store.plot("one").state());
    }
    @Test void changedLeaseRequiresAnotherQuote()throws Exception {
        var quote=store.evictionQuote("one",now);store.prepay("one",owner,1,now);
        assertThrows(IllegalArgumentException.class,()->store.evict(quote,"Admin","Reason",now));assertEquals("OWNER",store.role("one",owner));
    }
    @Test void failedRefundRollsBackMembershipAndClosure()throws Exception {
        var quote=store.evictionQuote("one",now);store.adjust(owner,Money.MAX-store.account(owner).orElseThrow().cents(),"test","cap",now);
        assertThrows(IllegalArgumentException.class,()->store.evict(quote,"Admin","Reason",now));
        assertEquals("ACTIVE",store.plot("one").state());assertEquals("STOCK",store.role("one",member));assertFalse(store.abandoned("one"));
    }
    @Test void ukCalendarDaysExcludeTodayAndPartialFinalDayAcrossDst(){
        long time=Instant.parse("2026-10-24T23:01:00Z").toEpochMilli(); // 00:01 Sunday in the UK
        long end=Instant.parse("2026-10-28T12:00:00Z").toEpochMilli();
        assertEquals(200,NookStore.evictionRefund(new NookStore.Plot("one",700,owner,end,"ACTIVE"),time));
        assertEquals(0,NookStore.evictionRefund(new NookStore.Plot("one",700,owner,end,"GRACE"),time));
    }
    @Test void listPutsOpenThenAvailableThenClosed(){
        assertEquals(0,PlotCommands.listOrder(storePlot("ACTIVE",now+1),now));
        assertEquals(1,PlotCommands.listOrder(storePlot("AVAILABLE",0),now));
        assertEquals(2,PlotCommands.listOrder(storePlot("ACTIVE",now),now));
        assertEquals(2,PlotCommands.listOrder(storePlot("RECLAIM",0),now));
    }
    NookStore.Plot storePlot(String state,long until){return new NookStore.Plot("one",700,owner,until,state);}
}
