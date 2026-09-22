package net.nikosnook.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PlotAbsenceTest {
    @TempDir Path dir;NookStore store;UUID id=UUID.randomUUID();long now=1_800_000_000_000L;
    @BeforeEach void setup()throws Exception {store=new NookStore(dir.resolve("nooks.db"));store.join(id,"Away",now,30000);store.definePlot("one",3000);store.rent("one",id,now);}
    @AfterEach void close()throws Exception {store.close();}
    @Test void absenceSurvivesRestartAndWaivesActivityButNotRent()throws Exception {
        store.setAbsence("one",now+3*NookStore.WEEK,"admin","Holiday",now);store.close();store=new NookStore(dir.resolve("nooks.db"));
        store.tick(now+NookStore.WEEK+1);assertEquals("ACTIVE",store.plot("one").state());assertEquals(24000,store.account(id).orElseThrow().cents());
    }
    @Test void expiredOrRevokedExceptionNoLongerAllowsRenewal()throws Exception {
        store.setAbsence("one",now+NookStore.WEEK,"admin","Holiday",now);store.tick(now+NookStore.WEEK+1);assertEquals("GRACE",store.plot("one").state());
        store.reopen("one",id,now+NookStore.WEEK+1);store.setAbsence("one",now+4*NookStore.WEEK,"admin","Extension",now+NookStore.WEEK+1);
        store.setAbsence("one",0,"admin","Returned",now+NookStore.WEEK+2);store.tick(now+2*NookStore.WEEK);assertEquals("GRACE",store.plot("one").state());
    }
    @Test void absenceNeverReopensGraceOrCoversMissingFunds()throws Exception {
        store.adjust(id,-27000,"admin","Test",now);store.setAbsence("one",now+4*NookStore.WEEK,"admin","Holiday",now);
        store.tick(now+NookStore.WEEK+1);assertEquals("GRACE",store.plot("one").state());
        store.adjust(id,6000,"admin","Funding",now+NookStore.WEEK+2);store.tick(now+NookStore.WEEK+2);assertEquals("GRACE",store.plot("one").state());
    }
    @Test void clearedLeaseDoesNotPassAbsenceToNextRenter()throws Exception {
        store.setAbsence("one",now+10*NookStore.WEEK,"admin","Holiday",now);store.tick(now+2*NookStore.WEEK);
        store.confirmCleared("one","admin","archive/one",now+2*NookStore.WEEK);store.rent("one",id,now+2*NookStore.WEEK);
        store.tick(now+3*NookStore.WEEK+1);assertEquals("GRACE",store.plot("one").state());
    }
}
