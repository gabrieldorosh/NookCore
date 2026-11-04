package net.nikosnook.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NikoRouteTest {
    private static NikoRoute.Point p(double x,double z){return new NikoRoute.Point("world",x,65,z);}
    private static NikoRoute loop(double length){return new NikoRoute(p(0,0),List.of(p(0,0),p(length,0)));}
    private static final NikoRoute.Safety SAFE=(a,b)->null;
    private static final NikoRoute.Move MOVE=(a,b)->{};
    @Test void gentleConstantPaceAndNoElapsedTimeCatchup(){
        var route=loop(3);for(int i=0;i<20;i++)route.tick(false,SAFE,MOVE);
        assertEquals(1.2,route.position().x(),1e-9);
        for(int i=0;i<200;i++)route.tick(true,SAFE,MOVE);
        assertEquals(1.2,route.position().x(),1e-9);route.tick(false,SAFE,MOVE);assertEquals(1.26,route.position().x(),1e-9);
    }
    @Test void pausesTwoToFourSecondsAndClosesTheLoop(){
        var route=loop(.06);var pauses=new ArrayList<Integer>();
        for(int point=0;point<3;point++){
            route.tick(false,SAFE,MOVE);pauses.add(route.pauseTicks());var at=route.position();
            for(int i=0;i<pauses.getLast();i++){route.tick(false,SAFE,MOVE);assertEquals(at,route.position());}
        }
        assertEquals(List.of(40,60,80),pauses);assertEquals(p(.06,0),route.position());
    }
    @Test void playerAndMenuPauseComesBeforeValidationOrMovement(){
        var route=loop(1);AtomicInteger checks=new AtomicInteger(),moves=new AtomicInteger();
        for(int i=0;i<200;i++)route.tick(true,(a,b)->{checks.incrementAndGet();return "wall";},(a,b)->moves.incrementAndGet());
        assertEquals(0,checks.get());assertEquals(0,moves.get());assertEquals(p(0,0),route.position());
        route.interact();for(int i=0;i<40;i++)route.tick(false,SAFE,(a,b)->moves.incrementAndGet());assertEquals(0,moves.get());
        route.tick(false,SAFE,(a,b)->moves.incrementAndGet());assertEquals(1,moves.get());
    }
    @Test void busyPlayerDoesNotConsumePointPause(){
        var route=loop(.06);route.tick(false,SAFE,MOVE);assertEquals(40,route.pauseTicks());
        for(int i=0;i<100;i++)route.tick(true,SAFE,MOVE);assertEquals(40,route.pauseTicks());
    }
    @Test void obstructionHoldsThenRechecksWithoutSkippingProgress(){
        var route=loop(2);route.tick(false,SAFE,MOVE);var old=route.position();AtomicInteger checks=new AtomicInteger();
        for(int i=0;i<20;i++)route.tick(false,(a,b)->{checks.incrementAndGet();return "wall";},MOVE);
        assertEquals(old,route.position());assertEquals(1,checks.get());
        route.tick(false,SAFE,MOVE);assertEquals(.12,route.position().x(),1e-9);assertNull(route.disabledReason());
    }
    @Test void persistentObstructionDisablesAtLastPointWithoutHomeTeleport(){
        var route=loop(3);for(int i=0;i<20;i++)route.tick(false,SAFE,MOVE);var old=route.position();
        for(int i=0;i<100;i++)route.tick(false,(a,b)->"unloaded or off-road",MOVE);
        assertNotNull(route.disabledReason());assertEquals(old,route.position());
        route.tick(false,SAFE,(a,b)->fail("disabled movement"));assertEquals(old,route.position());
    }
    @Test void nearbyPlayerAlsoFreezesObstructionDeadline(){
        var route=loop(2);route.tick(false,(a,b)->"wall",MOVE);
        for(int i=0;i<1000;i++)route.tick(true,SAFE,MOVE);assertNull(route.disabledReason());
        for(int i=0;i<20;i++)route.tick(false,SAFE,MOVE);assertEquals(.06,route.position().x(),1e-9);
    }
    @Test void failedProviderDoesNotCommitProgress(){
        var route=loop(2);assertThrows(IllegalStateException.class,()->route.tick(false,SAFE,(a,b)->{throw new IllegalStateException("move failed");}));
        assertEquals(p(0,0),route.position());
    }
    @Test void validatesEveryClosingSegmentBeforeStarting(){
        var route=new NikoRoute(p(0,0),List.of(p(0,0),p(2,0),p(2,2)));AtomicInteger checks=new AtomicInteger();
        assertEquals("segment 3: wall",route.initialProblem((a,b)->checks.incrementAndGet()==3?"wall":null));assertEquals(3,checks.get());
    }
    @Test void boundsWorldHeightHomeRadiusLengthAndPointCount(){
        assertThrows(IllegalArgumentException.class,()->new NikoRoute(p(0,0),List.of(p(1,0),p(2,0))));
        assertThrows(IllegalArgumentException.class,()->new NikoRoute(p(0,0),List.of(p(0,0))));
        assertThrows(IllegalArgumentException.class,()->new NikoRoute(p(0,0),List.of(p(0,0),p(32.01,0))));
        assertDoesNotThrow(()->new NikoRoute(p(0,0),List.of(p(0,0),p(32,0))));
        assertThrows(IllegalArgumentException.class,()->new NikoRoute(p(0,0),List.of(p(0,0),new NikoRoute.Point("nether",1,65,0))));
        assertThrows(IllegalArgumentException.class,()->new NikoRoute(p(0,0),List.of(p(0,0),new NikoRoute.Point("world",1,65.0625,0))));
        assertThrows(IllegalArgumentException.class,()->new NikoRoute(p(0,0),List.of(p(0,0),p(0,0))));
        var tooMany=new ArrayList<NikoRoute.Point>();for(int i=0;i<33;i++)tooMany.add(p(i%2,0));
        assertThrows(IllegalArgumentException.class,()->new NikoRoute(p(0,0),tooMany));
        var tooLong=new ArrayList<NikoRoute.Point>();tooLong.add(p(0,0));for(int i=0;i<10;i++)tooLong.add(p(i%2==0?32:-32,0));
        assertThrows(IllegalArgumentException.class,()->new NikoRoute(p(0,0),tooLong));
        assertThrows(IllegalArgumentException.class,()->new NikoRoute.Point("world",Double.NaN,65,0));
        assertThrows(IllegalArgumentException.class,()->new NikoRoute.Point("world",1e10,65,0));
    }
    @Test void recreationStartsAtHomeWithoutSavingPreviousProgress(){
        var old=loop(3);for(int i=0;i<20;i++)old.tick(false,SAFE,MOVE);assertEquals(1.2,old.position().x(),1e-9);
        var replacement=loop(3);assertEquals(p(0,0),replacement.position());replacement.tick(false,SAFE,MOVE);assertEquals(.06,replacement.position().x(),1e-9);
    }
}
