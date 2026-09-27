package net.nikosnook.core;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TravelRulesTest {
    @Test void roadAcceleratesRatherThanJumpingToFullSpeed(){
        double boost=TravelRules.roadBoost(0,1,1,30,20,true);assertEquals(.05,boost,1e-9);
        for(int i=2;i<=20;i++)boost=TravelRules.roadBoost(boost,i,i,30,20,true);
        assertEquals(1,boost,1e-9);
    }
    @Test void neverOnRoadMeansNoBoost(){assertEquals(0,TravelRules.roadBoost(0,500,-1,30,20,false));}
    @Test void shortGapsDoNotFlicker(){assertEquals(1,TravelRules.roadBoost(1,130,100,30,20,false));}
    @Test void boostDecaysAfterGraceAndStopsAtBaseline(){
        double boost=TravelRules.roadBoost(1,131,100,30,20,false);assertEquals(.95,boost,1e-9);
        for(int i=132;i<180;i++)boost=TravelRules.roadBoost(boost,i,100,30,20,false);
        assertEquals(0,boost);
    }
    @Test void returningToRoadReversesDecaySmoothly(){assertEquals(.55,TravelRules.roadBoost(.5,150,100,30,20,true),1e-9);}
    @Test void freshAirborneLoginDoesNotAllowRocket(){assertFalse(TravelRules.rocketAllowed(1,null,60));}
    @Test void takeoffWindowExpiresAndDoesNotResetOnBoost(){
        assertTrue(TravelRules.rocketAllowed(101,100L,60));assertTrue(TravelRules.rocketAllowed(160,100L,60));assertFalse(TravelRules.rocketAllowed(161,100L,60));
    }
    @Test void freshLandingAllowsAnotherTakeoff(){assertTrue(TravelRules.rocketAllowed(501,500L,60));}
    @Test void invalidTuningIsRejected(){for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,41})assertThrows(IllegalArgumentException.class,()->TravelRules.bounded(value,8,40,"speed"));}
    @Test void onlyLivingSurvivalOrAdventureRidersQualify(){
        for(GameMode mode:GameMode.values())for(boolean dead:new boolean[]{false,true}){
            Player player=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){case "isDead"->dead;case "getGameMode"->mode;default->null;});
            assertEquals(!dead && (mode==GameMode.SURVIVAL || mode==GameMode.ADVENTURE),GroundedTravel.rider(player));
        }
    }
    @Test void cartSlowdownKeepsDirectionAndVerticalVelocity(){
        AtomicReference<Vector> velocity=new AtomicReference<>(new Vector(1.2,.2,1.6));AtomicReference<Double> maximum=new AtomicReference<>();
        RideableMinecart cart=(RideableMinecart)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{RideableMinecart.class},(o,m,a)->switch(m.getName()){
            case "getVelocity"->velocity.get().clone();case "setVelocity"->{velocity.set((Vector)a[0]);yield null;}case "setMaxSpeed"->{maximum.set((Double)a[0]);yield null;}default->null;
        });
        GroundedTravel.limitCart(cart,.4);assertEquals(.4,maximum.get());assertEquals(.24,velocity.get().getX(),1e-9);assertEquals(.32,velocity.get().getZ(),1e-9);assertEquals(.2,velocity.get().getY(),1e-9);
    }
}
