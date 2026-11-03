package net.nikosnook.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PipIdlePoseTest {
    static final class Memory implements PipIdlePose.Gateway<Object> {
        final List<Boolean> poses=new ArrayList<>();final List<Object> actors=new ArrayList<>();
        boolean available=true,fail;int checks;
        public boolean available(){checks++;return available;}
        public void pose(Object npc,boolean sitting){
            if(fail)throw new IllegalStateException("test pose failure");
            actors.add(npc);poses.add(sitting);
        }
    }
    @Test void waitsTenSecondsAndSendsOnlyStateChanges(){
        var gateway=new Memory();var warnings=new ArrayList<String>();var idle=new PipIdlePose<>(gateway,warnings::add);var npc=new Object();
        idle.update(npc,false,100);idle.update(npc,false,100+PipIdlePose.IDLE_NANOS-1);assertTrue(gateway.poses.isEmpty());
        idle.update(npc,false,100+PipIdlePose.IDLE_NANOS);idle.update(npc,false,200+PipIdlePose.IDLE_NANOS);
        assertEquals(List.of(true),gateway.poses);
        idle.update(npc,true,300+PipIdlePose.IDLE_NANOS);idle.update(npc,true,400+PipIdlePose.IDLE_NANOS);
        assertEquals(List.of(true,false),gateway.poses);assertEquals(1,gateway.checks);assertTrue(warnings.isEmpty());
    }
    @Test void aNearbyPlayerRestartsTheFullIdleDelay(){
        var gateway=new Memory();var idle=new PipIdlePose<>(gateway,s->{});var npc=new Object();
        idle.update(npc,false,0);idle.update(npc,true,PipIdlePose.IDLE_NANOS-1);
        idle.update(npc,false,PipIdlePose.IDLE_NANOS);idle.update(npc,false,2*PipIdlePose.IDLE_NANOS-1);
        assertTrue(gateway.poses.isEmpty());idle.update(npc,false,2*PipIdlePose.IDLE_NANOS);assertEquals(List.of(true),gateway.poses);
    }
    @Test void aRealClickStandsImmediatelyAndResetsTheDelay(){
        var gateway=new Memory();var idle=new PipIdlePose<>(gateway,s->{});var npc=new Object();
        idle.update(npc,false,0);idle.update(npc,false,PipIdlePose.IDLE_NANOS);
        idle.standOnClick(npc,PipIdlePose.IDLE_NANOS+1);
        idle.standOnClick(npc,PipIdlePose.IDLE_NANOS+2);
        idle.update(npc,false,2*PipIdlePose.IDLE_NANOS+1);
        assertEquals(List.of(true,false),gateway.poses);
        idle.update(npc,false,2*PipIdlePose.IDLE_NANOS+2);assertEquals(List.of(true,false,true),gateway.poses);
    }
    @Test void aReplacementNpcStartsStandingWithItsOwnIdleDelay(){
        var gateway=new Memory();var idle=new PipIdlePose<>(gateway,s->{});var old=new Object();var replacement=new Object();
        idle.update(old,false,0);idle.update(old,false,PipIdlePose.IDLE_NANOS);
        idle.update(replacement,false,PipIdlePose.IDLE_NANOS+1);assertEquals(List.of(true),gateway.poses);
        idle.update(replacement,false,2*PipIdlePose.IDLE_NANOS+1);
        assertEquals(List.of(true,true),gateway.poses);assertSame(replacement,gateway.actors.getLast());
    }
    @Test void removalResetsIdleStateWithoutSendingToTheRemovedNpc(){
        var gateway=new Memory();var idle=new PipIdlePose<>(gateway,s->{});var npc=new Object();
        idle.update(npc,false,0);idle.update(npc,false,PipIdlePose.IDLE_NANOS);idle.update(null,false,PipIdlePose.IDLE_NANOS+1);
        idle.update(npc,true,PipIdlePose.IDLE_NANOS+2);assertEquals(List.of(true),gateway.poses);
    }
    @Test void missingProviderWarnsOnceAndDoesNotRetryOrThrow(){
        var gateway=new Memory();gateway.available=false;var warnings=new ArrayList<String>();var idle=new PipIdlePose<>(gateway,warnings::add);var npc=new Object();
        assertDoesNotThrow(()->{idle.update(npc,false,0);idle.update(npc,false,PipIdlePose.IDLE_NANOS);idle.standOnClick(npc,20);});
        assertEquals(1,gateway.checks);assertEquals(1,warnings.size());assertTrue(gateway.poses.isEmpty());
    }
    @Test void cosmeticUpdateFailureWarnsOnceAndDoesNotAffectCallers(){
        var gateway=new Memory();var warnings=new ArrayList<String>();var idle=new PipIdlePose<>(gateway,warnings::add);var npc=new Object();
        idle.update(npc,false,0);gateway.fail=true;
        assertDoesNotThrow(()->{idle.update(npc,false,PipIdlePose.IDLE_NANOS);idle.update(npc,false,2*PipIdlePose.IDLE_NANOS);idle.standOnClick(npc,30);});
        assertEquals(1,warnings.size());assertTrue(gateway.poses.isEmpty());
    }
    @Test void unavailableApiOrWarningCallbackCannotBreakShopping(){
        var gateway=new PipIdlePose.Gateway<Object>(){
            public boolean available(){throw new NoSuchMethodError("test unavailable API");}
            public void pose(Object npc,boolean sitting){fail("must not call");}
        };
        var idle=new PipIdlePose<>(gateway,s->{throw new IllegalStateException("test logger failure");});
        assertDoesNotThrow(()->idle.update(new Object(),false,0));
    }
    @Test void aClockResetDoesNotCreateAnInstantSit(){
        var gateway=new Memory();var idle=new PipIdlePose<>(gateway,s->{});var npc=new Object();
        idle.update(npc,false,1000);idle.update(npc,false,-1000);assertTrue(gateway.poses.isEmpty());
        idle.update(npc,false,PipIdlePose.IDLE_NANOS-1000);assertEquals(List.of(true),gateway.poses);
    }
}
