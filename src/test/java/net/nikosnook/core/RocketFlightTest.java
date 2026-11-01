package net.nikosnook.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RocketFlightTest {
    private static final long COOLDOWN=3000;
    @Test void firstRocketWorksWithoutAnyGroundHistory(){
        var state=new TravelRules.RocketFlight();state.observe(900_000,false,COOLDOWN);assertTrue(state.allowed(900_000));
    }
    @Test void unboostedLongGlideHasNoTakeoffWindow(){
        var state=new TravelRules.RocketFlight();state.observe(0,true,COOLDOWN);state.observe(1,false,COOLDOWN);state.observe(600_000,false,COOLDOWN);assertTrue(state.allowed(600_000));
    }
    @Test void ordinaryJumpsAndFallsNeverSpendTheRocket(){
        var state=new TravelRules.RocketFlight();for(int t=0;t<20_000;t+=50){state.observe(t,t%200==0,COOLDOWN);assertTrue(state.allowed(t));}
    }
    @Test void secondRocketIsBlockedForAsLongAsTheBoostedFlightLasts(){
        var state=new TravelRules.RocketFlight();state.used(false);state.observe(3_600_000,false,COOLDOWN);assertFalse(state.allowed(3_600_000));assertEquals(-1,state.readyAt());
    }
    @Test void landingStartsOneDeadlineAndExactThreeSecondBoundaryRearms(){
        var state=new TravelRules.RocketFlight();state.used(false);state.observe(100,true,COOLDOWN);assertEquals(3100,state.readyAt());assertFalse(state.allowed(3099));assertTrue(state.allowed(3100));
    }
    @Test void jumpingAgainImmediatelyDoesNotPauseTheLandingTimer(){
        var state=new TravelRules.RocketFlight();state.used(false);state.observe(100,true,COOLDOWN);state.observe(101,false,COOLDOWN);state.observe(3100,false,COOLDOWN);assertTrue(state.allowed(3100));
    }
    @Test void repeatedShortFallsAndLandingsCannotRestartTheDeadline(){
        var state=new TravelRules.RocketFlight();state.used(false);state.observe(100,true,COOLDOWN);
        for(int t=150;t<3100;t+=50)state.observe(t,t%200==0,COOLDOWN);
        assertEquals(3100,state.readyAt());state.observe(3100,false,COOLDOWN);assertTrue(state.allowed(3100));
    }
    @Test void continuousWalkingCompletesTimerWithoutNeedingToStandStill(){
        var state=new TravelRules.RocketFlight();state.used(false);for(int t=100;t<=3100;t+=50)state.observe(t,true,COOLDOWN);assertTrue(state.allowed(3100));
    }
    @Test void launchFrameSupportDoesNotCountAsTheNextLanding(){
        var state=new TravelRules.RocketFlight();state.used(true);state.observe(1,true,COOLDOWN);state.observe(2,false,COOLDOWN);state.observe(20_000,false,COOLDOWN);assertFalse(state.allowed(20_000));
        state.observe(20_001,true,COOLDOWN);assertEquals(23_001,state.readyAt());
    }
    @Test void usingRearmedRocketStartsANewFlightNotThePreviousDeadline(){
        var state=new TravelRules.RocketFlight();state.used(false);state.observe(100,true,COOLDOWN);state.observe(3100,false,COOLDOWN);state.used(false);state.observe(10_000,false,COOLDOWN);assertFalse(state.allowed(10_000));assertEquals(-1,state.readyAt());
    }
    @Test void relogInMidairCannotGrantAnExtraRocket(){
        var before=new TravelRules.RocketFlight();before.used(false);
        var after=new TravelRules.RocketFlight(before.spent(),before.readyAt());after.observe(900_000,false,COOLDOWN);assertFalse(after.allowed(900_000));
    }
    @Test void restoredSpentFlightCanLandImmediatelyAfterLogin(){
        var state=new TravelRules.RocketFlight(true,-1);state.observe(100,true,COOLDOWN);assertEquals(3100,state.readyAt());
    }
    @Test void reconnectCannotRestartTheAlreadyRunningCountdown(){
        var before=new TravelRules.RocketFlight();before.used(false);before.observe(100,true,COOLDOWN);
        var after=new TravelRules.RocketFlight(before.spent(),before.readyAt());after.observe(2500,true,COOLDOWN);assertEquals(3100,after.readyAt());assertTrue(after.allowed(3100));
    }
    @Test void elapsedOfflineTimeCountsAfterALanding(){
        var state=new TravelRules.RocketFlight(true,3100);state.observe(30_000,false,COOLDOWN);assertTrue(state.allowed(30_000));assertFalse(state.spent());assertEquals(-1,state.readyAt());
    }
    @Test void wallClockTimerDoesNotNeedSixtyServerSamples(){
        var state=new TravelRules.RocketFlight();state.used(false);state.observe(100,true,COOLDOWN);state.observe(3100,false,COOLDOWN);assertTrue(state.allowed(3100));
    }
    @Test void coolingStateReportsRemainingTimeAndAwaitingLandingDoesNot(){
        var state=new TravelRules.RocketFlight();state.used(false);assertEquals(-1,state.remainingMillis(100));state.observe(100,true,COOLDOWN);assertEquals(2000,state.remainingMillis(1100));assertEquals(0,state.remainingMillis(5000));
    }
    @Test void observingAnUnusedRocketNeverCreatesAPersistentSpentState(){
        var state=new TravelRules.RocketFlight();state.observe(0,true,COOLDOWN);state.observe(100_000,false,COOLDOWN);assertFalse(state.spent());assertEquals(-1,state.readyAt());
    }
    @Test void configuredCooldownIsUsedRatherThanAHardcodedDuration(){
        var state=new TravelRules.RocketFlight();state.used(false);state.observe(100,true,5000);assertFalse(state.allowed(5100-1));assertTrue(state.allowed(5100));
    }
}
