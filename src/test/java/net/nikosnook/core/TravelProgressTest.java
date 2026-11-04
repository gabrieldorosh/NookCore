package net.nikosnook.core;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TravelProgressTest {
    private static final UUID WORLD=new UUID(0,1),VEHICLE=new UUID(0,2);
    private static TravelProgress.Sample sample(double x,double y,double z,long tick){return sample(WORLD,VEHICLE,TravelProgress.Kind.ROAD_MOUNT,x,y,z,tick,tick*50_000_000L,true);}
    private static TravelProgress.Sample sample(UUID world,UUID vehicle,TravelProgress.Kind kind,double x,double y,double z,long tick,long nanos,boolean eligible){
        return new TravelProgress.Sample(world,vehicle,kind,x,y,z,tick,nanos,eligible);
    }
    @Test void firstSampleOnlyAnchorsAndDistanceIsHorizontal(){
        var progress=new TravelProgress();progress.observe(sample(0,65,0,0));assertEquals(0,progress.pending().roadMillimetres());
        progress.observe(sample(3,66,4,5));assertEquals(5000,progress.pending().roadMillimetres());assertEquals(0,progress.pending().railMillimetres());
        progress.observe(sample(3,67,4,10));assertEquals(5000,progress.pending().roadMillimetres());
    }
    @Test void vehicleChangeDiscardsConnectorDistance(){
        var progress=new TravelProgress();progress.observe(sample(0,65,0,0));
        UUID other=new UUID(0,3);progress.observe(sample(WORLD,other,TravelProgress.Kind.ROAD_MOUNT,5,65,0,5,250_000_000,true));
        assertEquals(0,progress.pending().roadMillimetres());
        progress.observe(sample(WORLD,other,TravelProgress.Kind.ROAD_MOUNT,6,65,0,10,500_000_000,true));assertEquals(1000,progress.pending().roadMillimetres());
    }
    @Test void worldChangeDiscardsConnectorDistance(){
        var progress=new TravelProgress();progress.observe(sample(0,65,0,0));
        progress.observe(sample(new UUID(0,4),VEHICLE,TravelProgress.Kind.ROAD_MOUNT,4,65,0,5,250_000_000,true));
        assertEquals(0,progress.pending().roadMillimetres());
    }
    @Test void kindChangeDoesNotCountConnectorAndCountersRemainSeparate(){
        var progress=new TravelProgress();progress.observe(sample(0,65,0,0));progress.observe(sample(1,65,0,5));
        progress.observe(sample(WORLD,VEHICLE,TravelProgress.Kind.RAIL,3,65,0,10,500_000_000,true));
        progress.observe(sample(WORLD,VEHICLE,TravelProgress.Kind.RAIL,5,65,0,15,750_000_000,true));
        assertEquals(new TravelProgress.Totals(1000,2000),progress.pending());
    }
    @Test void explicitTeleportResetDiscardsEvenSmallTeleport(){
        var progress=new TravelProgress();progress.observe(sample(0,65,0,0));progress.observe(sample(1,65,0,5));progress.reset();
        progress.observe(sample(2,65,0,10));assertEquals(1000,progress.pending().roadMillimetres());
        progress.observe(sample(3,65,0,15));assertEquals(2000,progress.pending().roadMillimetres());
    }
    @Test void ineligibleStaffWrongModeEmptyOrOffSurfaceSampleBreaksContinuity(){
        var progress=new TravelProgress();progress.observe(sample(0,65,0,0));
        progress.observe(sample(WORLD,VEHICLE,TravelProgress.Kind.ROAD_MOUNT,1,65,0,5,250_000_000,false));
        assertNull(progress.anchor());progress.observe(sample(3,65,0,10));assertEquals(0,progress.pending().roadMillimetres());
        progress.observe(sample(4,65,0,15));assertEquals(1000,progress.pending().roadMillimetres());
    }
    @Test void lagGapDoesNotCatchUpAndOnlyNextNormalSegmentCounts(){
        var progress=new TravelProgress();progress.observe(sample(0,65,0,0));progress.observe(sample(5,65,0,21));
        assertEquals(0,progress.pending().roadMillimetres());progress.observe(sample(6,65,0,26));assertEquals(1000,progress.pending().roadMillimetres());
    }
    @Test void longWallClockGapIsRejectedEvenWithNormalTickGap(){
        var progress=new TravelProgress();progress.observe(sample(0,65,0,0));
        progress.observe(sample(WORLD,VEHICLE,TravelProgress.Kind.ROAD_MOUNT,1,65,0,5,1_000_000_001L,true));
        assertEquals(0,progress.pending().roadMillimetres());
    }
    @Test void bothClocksBoundSpeedAndOversizeVerticalJumpsAreRejected(){
        var progress=new TravelProgress();progress.observe(sample(0,65,0,0));
        progress.observe(sample(WORLD,VEHICLE,TravelProgress.Kind.ROAD_MOUNT,2,65,0,5,10_000_000,true));assertEquals(0,progress.pending().roadMillimetres());
        progress.observe(sample(WORLD,VEHICLE,TravelProgress.Kind.ROAD_MOUNT,3,68.01,0,10,260_000_000,true));assertEquals(0,progress.pending().roadMillimetres());
        progress.observe(sample(WORLD,VEHICLE,TravelProgress.Kind.ROAD_MOUNT,20,68.01,0,15,510_000_000,true));assertEquals(0,progress.pending().roadMillimetres());
    }
    @Test void railUsesItsOwnSpeedEnvelope(){
        var progress=new TravelProgress();progress.observe(sample(WORLD,VEHICLE,TravelProgress.Kind.RAIL,0,65,0,0,0,true));
        progress.observe(sample(WORLD,VEHICLE,TravelProgress.Kind.RAIL,10,65,0,5,250_000_000,true));assertEquals(10000,progress.pending().railMillimetres());
        progress.observe(sample(WORLD,VEHICLE,TravelProgress.Kind.RAIL,20.01,65,0,10,500_000_000,true));assertEquals(10000,progress.pending().railMillimetres());
    }
    @Test void duplicateBackwardAndOverflowingClocksNeverCount(){
        for(long tick:new long[]{0,-1,Long.MIN_VALUE}){
            var progress=new TravelProgress();progress.observe(sample(0,65,0,0));progress.observe(sample(WORLD,VEHICLE,TravelProgress.Kind.ROAD_MOUNT,1,65,0,tick,250_000_000,true));
            assertEquals(0,progress.pending().roadMillimetres());
        }
        var progress=new TravelProgress();progress.observe(sample(WORLD,VEHICLE,TravelProgress.Kind.ROAD_MOUNT,0,65,0,0,Long.MAX_VALUE,true));
        progress.observe(sample(WORLD,VEHICLE,TravelProgress.Kind.ROAD_MOUNT,1,65,0,5,Long.MIN_VALUE,true));assertEquals(0,progress.pending().roadMillimetres());
    }
    @Test void malformedOrUnavailableSamplesClearAnchor(){
        for(var bad:new TravelProgress.Sample[]{null,sample(Double.NaN,65,0,5),sample(0,Double.POSITIVE_INFINITY,0,5),sample(30_000_000,65,0,5),
            sample(null,VEHICLE,TravelProgress.Kind.ROAD_MOUNT,1,65,0,5,250_000_000,true),sample(WORLD,null,TravelProgress.Kind.ROAD_MOUNT,1,65,0,5,250_000_000,true)}){
            var progress=new TravelProgress();progress.observe(sample(0,65,0,0));progress.observe(bad);assertNull(progress.anchor());assertEquals(0,progress.pending().roadMillimetres());
        }
    }
    @Test void fractionalMillimetresCarryWithoutRoundingEachSample(){
        var progress=new TravelProgress();progress.observe(sample(0,65,0,0));
        progress.observe(sample(.0004,65,0,5));progress.observe(sample(.0008,65,0,10));assertNull(progress.snapshot());
        progress.observe(sample(.0012,65,0,15));assertEquals(1,progress.pending().roadMillimetres());
    }
    @Test void failedFlushKeepsSameBatchAndNewDistanceRemainsAfterAcknowledgement(){
        var progress=new TravelProgress();progress.observe(sample(0,65,0,0));progress.observe(sample(1,65,0,5));var batch=progress.snapshot();
        assertNotNull(batch);progress.observe(sample(2,65,0,10));assertEquals(batch,progress.snapshot());assertEquals(2000,progress.pending().roadMillimetres());
        assertTrue(progress.acknowledge(batch));assertEquals(1000,progress.pending().roadMillimetres());assertFalse(progress.acknowledge(batch));
        var next=progress.snapshot();assertNotEquals(batch.sequence(),next.sequence());assertEquals(1000,next.roadMillimetres());
    }
    @Test void staleOrForeignAcknowledgementsCannotConsumeCurrentBatch(){
        var progress=new TravelProgress();progress.observe(sample(0,65,0,0));progress.observe(sample(1,65,0,5));var batch=progress.snapshot();
        assertFalse(progress.acknowledge(null));assertFalse(progress.acknowledge(new TravelProgress.Batch(batch.sequence(),1001,0)));
        assertEquals(1000,progress.pending().roadMillimetres());assertTrue(progress.acknowledge(batch));assertNull(progress.snapshot());
    }
    @Test void resetPreservesPendingDistanceAndRestartHasNoStaleAnchor(){
        var progress=new TravelProgress();progress.observe(sample(0,65,0,0));progress.observe(sample(1,65,0,5));progress.reset();
        assertEquals(1000,progress.pending().roadMillimetres());assertNull(progress.anchor());
        var restarted=new TravelProgress();restarted.observe(sample(10,65,0,10));assertEquals(0,restarted.pending().roadMillimetres());
    }
    @Test void invalidLimitsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->new TravelProgress.Limits(Double.NaN,40,10,1_000_000_000));
        assertThrows(IllegalArgumentException.class,()->new TravelProgress.Limits(50,0,10,1_000_000_000));
        assertThrows(IllegalArgumentException.class,()->new TravelProgress.Limits(50,40,21,1_000_000_000));
        assertThrows(IllegalArgumentException.class,()->new TravelProgress.Limits(50,40,10,3_000_000_000L));
    }
}
