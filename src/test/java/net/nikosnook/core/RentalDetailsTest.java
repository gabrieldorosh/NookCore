package net.nikosnook.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RentalDetailsTest {
    @TempDir Path dir;
    @Test void prepaidWeeksExcludeCurrentWeekAtExactBoundaries(){
        var plot=new NookStore.Plot("one",3000,UUID.randomUUID(),5*NookStore.WEEK,"ACTIVE");
        assertEquals(4,NookStore.prepaidWeeks(plot,0));
        assertEquals(4,NookStore.prepaidWeeks(plot,NookStore.WEEK-1));
        assertEquals(3,NookStore.prepaidWeeks(plot,NookStore.WEEK));
        assertEquals(0,NookStore.prepaidWeeks(plot,5*NookStore.WEEK));
    }
    @Test void prepayLimitExplainsExistingWeeksWithoutCharging()throws Exception {
        try(var store=new NookStore(dir.resolve("test.db"))){
            UUID owner=UUID.randomUUID();store.join(owner,"Owner",0,100000);store.definePlot("one",3000);store.rent("one",owner,0);store.prepay("one",owner,4,0);
            long balance=store.account(owner).orElseThrow().cents();
            var error=assertThrows(IllegalArgumentException.class,()->store.prepay("one",owner,1,100));
            assertTrue(error.getMessage().contains("4 future prepaid weeks"));assertEquals(balance,store.account(owner).orElseThrow().cents());
        }
    }
    @Test void reopeningAnOpenShopGivesAccurateFeedback()throws Exception {
        try(var store=new NookStore(dir.resolve("test.db"))){
            UUID owner=UUID.randomUUID();store.join(owner,"Owner",0,6000);store.definePlot("one",3000);store.rent("one",owner,0);
            var error=assertThrows(IllegalArgumentException.class,()->store.reopen("one",owner,1));
            assertTrue(error.getMessage().contains("already open"));assertEquals(3000,store.account(owner).orElseThrow().cents());
        }
    }
    @Test void clearanceNotesSurviveRerentalAndRestartAndStayPlotScoped()throws Exception {
        Path db=dir.resolve("test.db");UUID owner=UUID.randomUUID();
        try(var store=new NookStore(db)){
            store.join(owner,"Owner",0,100000);store.definePlot("one",3000);store.definePlot("two",3000);store.rent("one",owner,0);
            store.abandon(store.abandonmentQuote("one",owner,1),owner,1);
            store.confirmCleared("one","Admin","staff storage A3",2);store.rent("one",owner,3);
        }
        try(var store=new NookStore(db)){
            assertEquals(1,store.storageNotes("one").size());assertTrue(store.storageNotes("one").getFirst().detail().contains("staff storage A3"));
            assertTrue(store.storageNotes("two").isEmpty());
        }
    }
    @Test void protectionMessageRetainsPastelHexInsteadOfLiteralCodes(){
        String legacy=NookUi.protectionMessage("&#e5b95cNookPlots » &#ddd6dfProtected.");
        assertTrue(legacy.contains("§x§e§5§b§9§5§c"));assertFalse(legacy.contains("&#"));
    }
}
