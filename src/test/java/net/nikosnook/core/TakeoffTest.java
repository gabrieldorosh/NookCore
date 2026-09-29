package net.nikosnook.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TakeoffTest {
    @Test void airborneLoginAndShortLandingCannotArm(){
        var s=new TravelRules.Takeoff();s.update(0,false,60);assertFalse(s.allowed(0,60));
        s.update(1,true,60);s.update(60,true,60);s.update(61,false,60);assertFalse(s.allowed(61,60));
    }
    @Test void threeSecondsGroundedArmsWindowWhichExpiresWithoutRenewal(){
        var s=new TravelRules.Takeoff();s.update(0,true,60);s.update(60,true,60);s.update(61,false,60);
        assertTrue(s.allowed(61,60));assertTrue(s.allowed(121,60));assertFalse(s.allowed(122,60));
    }
    @Test void touchAndGoEndsOldWindowAndCannotReuseIt(){
        var s=new TravelRules.Takeoff();s.update(0,true,60);s.update(60,true,60);s.update(61,false,60);
        s.update(62,true,60);assertFalse(s.allowed(62,60));s.update(63,false,60);assertFalse(s.allowed(63,60));
        s.update(80,true,60);s.update(140,true,60);s.update(141,false,60);assertTrue(s.allowed(141,60));
    }
    @Test void separateBriefLandingsDoNotAccumulateCredit(){
        var s=new TravelRules.Takeoff();for(int t=0;t<200;t++)s.update(t,t%30!=29,60);
        s.update(201,false,60);assertFalse(s.allowed(201,60));
    }
    @Test void walkingDoesNotSpendTheWindow(){
        var s=new TravelRules.Takeoff();s.update(0,true,60);s.update(500,true,60);
        assertFalse(s.allowed(500,60));s.update(501,false,60);assertTrue(s.allowed(501,60));
    }
}
