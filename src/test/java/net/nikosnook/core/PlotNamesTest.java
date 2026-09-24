package net.nikosnook.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class PlotNamesTest {
    @TempDir Path dir;
    @Test void namesArePlainAndBounded(){assertEquals("Niko’s Nice Shop",PlotNames.clean("  Niko’s  Nice Shop "));for(String s:new String[]{"", " ","<red>Admin","§cshop","&ashop","a\nb","a".repeat(33),"/tp"})assertThrows(IllegalArgumentException.class,()->PlotNames.clean(s));}
    @Test void renameKeepsIdLeaseMoneyAndSurvivesRestart()throws Exception {
        UUID owner=UUID.randomUUID();Path db=dir.resolve("db");
        try(var store=new NookStore(db)){store.join(owner,"Owner",0,10000);store.definePlot("one",3000);store.rent("one",owner,0);var before=store.plot("one");long balance=store.account(owner).orElseThrow().cents();store.namePlot("one",owner,"Fresh Fish",1);assertEquals(before,store.plot("one"));assertEquals(balance,store.account(owner).orElseThrow().cents());}
        try(var store=new NookStore(db)){assertEquals("Fresh Fish",store.plotLabel("one"));store.namePlot("one",owner,null,2);assertEquals("one",store.plotLabel("one"));}
    }
    @Test void otherPlayersAndExpiredRentCannotRename()throws Exception {
        try(var store=new NookStore(dir.resolve("db"))){UUID owner=UUID.randomUUID(),other=UUID.randomUUID();store.join(owner,"Owner",0,10000);store.join(other,"Other",0,10000);store.definePlot("one",3000);store.rent("one",owner,0);assertThrows(IllegalArgumentException.class,()->store.namePlot("one",other,"Stolen",1));assertThrows(IllegalArgumentException.class,()->store.namePlot("one",owner,"Late",NookStore.WEEK));assertEquals("one",store.plotLabel("one"));}
    }
    @Test void clearanceRemovesOldTenantName()throws Exception {
        try(var store=new NookStore(dir.resolve("db"))){UUID owner=UUID.randomUUID();store.join(owner,"Owner",0,10000);store.definePlot("one",3000);store.rent("one",owner,0);store.namePlot("one",owner,"Old Shop",1);store.abandon(store.abandonmentQuote("one",owner,2),owner,2);assertEquals("Old Shop",store.plotLabel("one"));store.confirmCleared("one","Admin","chest A",3);assertEquals("one",store.plotLabel("one"));}
    }
    @Test void addressOutlivesTenantAndIsDefaultAfterReset()throws Exception {
        Path db=dir.resolve("db");UUID owner=UUID.randomUUID();
        try(var store=new NookStore(db)){store.join(owner,"Owner",0,10000);store.definePlot("one",3000);store.addressPlot("one","Mossbank Lane");store.rent("one",owner,0);store.namePlot("one",owner,"Fresh Fish",1);assertEquals("Mossbank Lane",store.plotAddress("one"));store.namePlot("one",owner,null,2);assertEquals("Mossbank Lane",store.plotLabel("one"));store.namePlot("one",owner,"Fresh Fish",3);store.abandon(store.abandonmentQuote("one",owner,4),owner,4);store.confirmCleared("one","Admin","A1",5);}
        try(var store=new NookStore(db)){assertEquals("Mossbank Lane",store.plotLabel("one"));assertEquals("one",store.plot("one").id());}
    }
}
