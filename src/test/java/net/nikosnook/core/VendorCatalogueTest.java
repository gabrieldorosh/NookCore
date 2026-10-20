package net.nikosnook.core;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class VendorCatalogueTest {
    VendorCatalogue.Design design(String id,VendorCatalogue.Rarity rarity,boolean season,boolean modern){return new VendorCatalogue.Design(id,id,"a".repeat(64),rarity,id,season?Set.of("halloween"):Set.of(),!season,modern,400,10,0,"");}
    List<VendorCatalogue.Design> pool(){var list=new ArrayList<VendorCatalogue.Design>();for(var rarity:VendorCatalogue.Rarity.values())for(int i=0;i<8;i++)list.add(design(rarity.name().toLowerCase()+i,rarity,i<3,rarity==VendorCatalogue.Rarity.RARE&&i==7));return list;}
    @Test void selectionMeetsCombinedConstraintsAcrossSeeds(){for(int i=0;i<250;i++){var chosen=VendorCatalogue.select(pool(),"halloween",Set.of("rare7"),Set.of(),i);assertTrue(VendorCatalogue.valid(chosen,"halloween"));assertTrue(chosen.stream().anyMatch(x->x.id().equals("rare7")));}}
    @Test void deterministicSelectionDoesNotDependOnRestart(){assertEquals(VendorCatalogue.select(pool(),"halloween",Set.of(),Set.of(),42),VendorCatalogue.select(pool(),"halloween",Set.of(),Set.of(),42));}
    @Test void impossibleSeasonAndMissingPinsFailClosed(){assertThrows(IllegalArgumentException.class,()->VendorCatalogue.select(pool(),"spring",Set.of(),Set.of(),1));assertThrows(IllegalArgumentException.class,()->VendorCatalogue.select(pool(),"halloween",Set.of("missing"),Set.of(),1));}
    @Test void repeatedDesignsAreDeprioritised(){var old=VendorCatalogue.select(pool(),"halloween",Set.of(),Set.of(),42);var seen=new HashSet<>(old.stream().map(VendorCatalogue.Design::id).toList());var next=VendorCatalogue.select(pool(),"halloween",Set.of(),seen,42);assertTrue(next.stream().noneMatch(x->seen.contains(x.id())));}
    @Test void dateBoundariesUseUkTimeAndHalfOpenWindows(){
        long before=Instant.parse("2026-10-14T07:59:59Z").toEpochMilli(),start=before+1000;var v=VendorSchedule.at(start);
        assertEquals("2026-10-14",v.id());assertFalse(VendorSchedule.at(before).open(before));assertTrue(v.open(start));assertFalse(v.open(v.departure()));assertEquals(Instant.parse("2026-10-16T18:00:00Z").toEpochMilli(),v.restock());
    }
    @Test void dstChangePreservesLocalRestockAndDeparture(){
        var v=VendorSchedule.at(Instant.parse("2026-10-21T01:00:00Z").toEpochMilli());assertEquals(Duration.ofHours(169).toMillis(),v.nextArrival()-v.arrival());assertEquals(Instant.parse("2026-10-26T19:00:00Z").toEpochMilli(),v.departure());
        var spring=VendorSchedule.at(Instant.parse("2026-03-25T02:00:00Z").toEpochMilli());assertEquals(Duration.ofHours(167).toMillis(),spring.nextArrival()-spring.arrival());
    }
    @Test void fridayRestockAndMondayDepartureHaveExactBoundaries(){
        var v=VendorSchedule.at(Instant.parse("2026-10-21T08:00:00Z").toEpochMilli());
        assertEquals(1,v.allocation(v.restock()-1));assertEquals(2,v.allocation(v.restock()));
        assertTrue(VendorSchedule.at(v.departure()-1).open(v.departure()-1));assertFalse(VendorSchedule.at(v.departure()).open(v.departure()));
        assertEquals(Instant.parse("2026-10-23T18:00:00Z").toEpochMilli(),v.restock());assertEquals(Instant.parse("2026-10-28T09:00:00Z").toEpochMilli(),v.nextArrival());
    }
    @Test void wednesdayQuestResetDoesNotBringPipEarly(){
        long reset=Instant.parse("2026-10-21T01:00:00Z").toEpochMilli();var v=VendorSchedule.at(reset);
        assertEquals("2026-10-21",v.id());assertFalse(v.open(reset));assertEquals(Instant.parse("2026-10-21T08:00:00Z").toEpochMilli(),v.arrival());
        long tuesday=Instant.parse("2026-10-20T20:00:00Z").toEpochMilli();assertEquals(v.arrival(),VendorSchedule.at(tuesday).nextArrival());
    }
}
