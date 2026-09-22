package net.nikosnook.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PlotInvitationTest {
    @TempDir Path dir;
    NookStore store;
    UUID owner=UUID.randomUUID(), member=UUID.randomUUID(), other=UUID.randomUUID();
    long now=1_800_000_000_000L;
    @BeforeEach void setup()throws Exception {
        store=new NookStore(dir.resolve("nooks.db"));
        store.join(owner,"Owner",now,6000);store.join(member,"Member",now,6000);store.join(other,"Other",now,6000);
        store.definePlot("one",3000);store.definePlot("two",3000);store.rent("one",owner,now);
    }
    @AfterEach void close()throws Exception {store.close();}
    NookStore.Invitation invite()throws Exception {return store.invite("one",owner,member,"STOCK",now);}
    @Test void invitationDoesNotReserveAnotherPlayersAllowance()throws Exception {
        invite();assertEquals("NONE",store.role("one",member));store.rent("two",member,now);
        assertEquals(member,store.plot("two").owner());
    }
    @Test void onlyRecipientCanAcceptAndReplayFails()throws Exception {
        var i=invite();assertThrows(IllegalArgumentException.class,()->store.acceptInvitation(i.token(),other,now));
        assertEquals("NONE",store.role("one",other));store.acceptInvitation(i.token(),member,now);
        assertEquals("STOCK",store.role("one",member));assertThrows(IllegalArgumentException.class,()->store.acceptInvitation(i.token(),member,now));
    }
    @Test void acceptanceSurvivesRestartWithExactOfferedRole()throws Exception {
        var i=invite();store.close();store=new NookStore(dir.resolve("nooks.db"));store.acceptInvitation(i.token(),member,now+1);
        assertEquals("STOCK",store.role("one",member));
    }
    @Test void expiredInvitationCannotBeAcceptedAtBoundary()throws Exception {
        var i=invite();assertThrows(IllegalArgumentException.class,()->store.acceptInvitation(i.token(),member,i.expires()));
        assertEquals("NONE",store.role("one",member));
    }
    @Test void replacementOfferInvalidatesOldToken()throws Exception {
        var old=invite();var replacement=store.invite("one",owner,member,"BUILD",now+1);
        assertThrows(IllegalArgumentException.class,()->store.acceptInvitation(old.token(),member,now+2));
        store.acceptInvitation(replacement.token(),member,now+2);assertEquals("BUILD",store.role("one",member));
    }
    @Test void removedOfferCannotBeAccepted()throws Exception {
        var i=invite();store.removeMember("one",owner,member,now);
        assertThrows(IllegalArgumentException.class,()->store.acceptInvitation(i.token(),member,now));
    }
    @Test void onlyRecipientCanDecline()throws Exception {
        var i=invite();assertThrows(IllegalArgumentException.class,()->store.declineInvitation(i.token(),other,now));
        store.declineInvitation(i.token(),member,now);assertThrows(IllegalArgumentException.class,()->store.acceptInvitation(i.token(),member,now));
    }
    @Test void expiryBlocksAcceptanceBeforeBillingTaskRuns()throws Exception {
        long late=now+NookStore.WEEK-1000;
        var i=store.invite("one",owner,member,"BUILD",late);
        assertThrows(IllegalArgumentException.class,()->store.acceptInvitation(i.token(),member,now+NookStore.WEEK));
        assertThrows(IllegalArgumentException.class,()->store.invite("one",owner,other,"BUILD",now+NookStore.WEEK));
    }
    @Test void differentOwnersCannotRaceToReserveSameMember()throws Exception {
        store.rent("two",other,now);var one=invite();var two=store.invite("two",other,member,"BUILD",now);
        try(ExecutorService pool=Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results=new ArrayList<>();
            for(var i:List.of(one,two))results.add(pool.submit(()->{try{store.acceptInvitation(i.token(),member,now);return true;}catch(IllegalArgumentException e){return false;}}));
            int accepted=0;for(var result:results)if(result.get())accepted++;
            assertEquals(1,accepted);
        }
    }
    @Test void onePlotConflictIsExpectedErrorAndDoesNotDebit()throws Exception {
        var i=invite();store.rent("two",member,now);
        assertThrows(IllegalArgumentException.class,()->store.acceptInvitation(i.token(),member,now));
        assertThrows(IllegalArgumentException.class,()->store.rent("two",owner,now));
        assertEquals(3000,store.account(member).orElseThrow().cents());assertEquals("NONE",store.role("one",member));
    }
    @Test void onlyOwnerCanChangeRolesAndCannotGiveAwayOwnership()throws Exception {
        store.acceptInvitation(invite().token(),member,now);
        assertThrows(IllegalArgumentException.class,()->store.changeRole("one",member,member,"BUILD_STOCK",now));
        assertThrows(IllegalArgumentException.class,()->store.changeRole("one",owner,owner,"BUILD",now));
        assertThrows(IllegalArgumentException.class,()->store.changeRole("one",owner,member,"OWNER",now));
        store.changeRole("one",owner,member,"BUILD_STOCK",now);assertEquals("BUILD_STOCK",store.role("one",member));
        assertEquals("OWNER",store.role("one",owner));assertEquals(owner,store.plot("one").owner());
    }
    @Test void clearedPlotInvalidatesPreviousLeaseOffers()throws Exception {
        var i=invite();store.tick(now+2*NookStore.WEEK);store.confirmCleared("one","admin","archive/one",now+2*NookStore.WEEK);
        store.rent("one",other,now+2*NookStore.WEEK);
        assertThrows(IllegalArgumentException.class,()->store.acceptInvitation(i.token(),member,now+2*NookStore.WEEK));
        assertEquals("NONE",store.role("one",member));
    }
}
