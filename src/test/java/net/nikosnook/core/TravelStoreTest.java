package net.nikosnook.core;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TravelStoreTest {
    @TempDir Path dir;
    @Test void lifetimeTotalsPersistAndDuplicateOrStaleSnapshotsNeverAddAgain()throws Exception {
        UUID id=UUID.randomUUID();Path file=dir.resolve("travel.db");
        try(var store=new NookStore(file)){
            store.join(id,"Traveller",1,6000);assertEquals(new NookStore.TravelTotals(0,0),store.travelTotals(id));
            var snapshot=Map.of(id,new NookStore.TravelTotals(999_500,2_000_000));
            store.saveTravelTotals(snapshot);store.saveTravelTotals(snapshot);
            store.saveTravelTotals(Map.of(id,new NookStore.TravelTotals(1000,1000)));
            assertEquals(snapshot.get(id),store.travelTotals(id));
            assertEquals(6000,store.account(id).orElseThrow().cents());assertEquals(1,store.history(id).size(),"no travel money/award ledger entries");
        }
        try(var store=new NookStore(file)){
            assertEquals(new NookStore.TravelTotals(999_500,2_000_000),store.travelTotals(id));
            store.saveTravelTotals(Map.of(id,new NookStore.TravelTotals(1_000_250,2_000_000)));
            assertEquals(1_000_250,store.travelTotals(id).roadMillimetres());
        }
    }
    @Test void unknownAccountRollsBackTheWholeBoundedBatch()throws Exception {
        UUID id=UUID.randomUUID();try(var store=new NookStore(dir.resolve("rollback.db"))){
            store.join(id,"Traveller",1,6000);var batch=new LinkedHashMap<UUID,NookStore.TravelTotals>();
            batch.put(id,new NookStore.TravelTotals(1000,2000));batch.put(UUID.randomUUID(),new NookStore.TravelTotals(1,0));
            assertThrows(SQLException.class,()->store.saveTravelTotals(batch));assertEquals(new NookStore.TravelTotals(0,0),store.travelTotals(id));
            store.saveTravelTotals(Map.of(id,new NookStore.TravelTotals(1000,2000)));assertEquals(2000,store.travelTotals(id).railMillimetres());
        }
    }
    @Test void snapshotBoundsAndGoalsRejectInvalidInputsBeforeWriting()throws Exception {
        assertThrows(IllegalArgumentException.class,()->new NookStore.TravelTotals(-1,0));
        assertThrows(IllegalArgumentException.class,()->new NookStore.TravelTotals(NookStore.MAX_TRAVEL_MM+1,0));
        for(double bad:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,1_000_000_001})assertThrows(IllegalArgumentException.class,()->TravelMilestones.goal(bad));
        assertEquals(1_000_000,TravelMilestones.goal(1000));assertEquals(1250,TravelMilestones.goal(1.25));
        try(var store=new NookStore(dir.resolve("bounded.db"))){
            var tooLarge=new LinkedHashMap<UUID,NookStore.TravelTotals>();for(int i=0;i<65;i++)tooLarge.put(UUID.randomUUID(),new NookStore.TravelTotals(0,0));
            assertThrows(IllegalArgumentException.class,()->store.saveTravelTotals(tooLarge));assertEquals(Map.of(),store.saveTravelTotals(Map.of()));
        }
    }
}
