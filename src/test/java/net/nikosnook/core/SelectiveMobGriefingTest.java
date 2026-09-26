package net.nikosnook.core;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SelectiveMobGriefingTest {
    private final SelectiveMobGriefing protection=new SelectiveMobGriefing(true,true);
    @SuppressWarnings("unchecked") private <T> T stub(Class<T> type){return (T)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{type},(p,m,a)->null);}
    private EntityExplodeEvent explode(Entity entity){return new EntityExplodeEvent(entity,new Location(null,0,64,0),new ArrayList<>(List.of(stub(Block.class))),1,ExplosionResult.DESTROY);}
    private EntityChangeBlockEvent change(Entity entity){return new EntityChangeBlockEvent(entity,stub(Block.class),stub(BlockData.class));}
    @Test void CreeperLosesBlockDamageWithoutCancellingExplosion(){
        var event=explode(stub(Creeper.class));protection.explosion(event);assertTrue(event.blockList().isEmpty());assertFalse(event.isCancelled());assertEquals(1,event.getYield());
    }
    @Test void OtherExplosionsAreUnchanged(){
        for(Entity entity:List.of(stub(TNTPrimed.class),stub(Wither.class),stub(Fireball.class))){var event=explode(entity);protection.explosion(event);assertEquals(1,event.blockList().size());assertFalse(event.isCancelled());}
    }
    @Test void EndermanPickupAndPlacementAreBlockedButVillagersAndOtherMobsAreNot(){
        var event=change(stub(Enderman.class));protection.changeBlock(event);assertTrue(event.isCancelled());
        for(Entity entity:List.of(stub(Villager.class),stub(Sheep.class),stub(Zombie.class))){event=change(entity);protection.changeBlock(event);assertFalse(event.isCancelled());}
    }
    @Test void DisabledTogglesLeaveEventsUnchanged(){
        var off=new SelectiveMobGriefing(false,false);var explosion=explode(stub(Creeper.class));off.explosion(explosion);assertEquals(1,explosion.blockList().size());
        var change=change(stub(Enderman.class));off.changeBlock(change);assertFalse(change.isCancelled());
    }
    @Test void CreeperCannotBreakHangingDecorationsButPlayerCan(){
        var decoration=stub(Hanging.class);var event=new HangingBreakByEntityEvent(decoration,stub(Creeper.class),null,HangingBreakEvent.RemoveCause.EXPLOSION);protection.hanging(event);assertTrue(event.isCancelled());
        var player=new HangingBreakByEntityEvent(decoration,stub(Player.class),null,HangingBreakEvent.RemoveCause.ENTITY);protection.hanging(player);assertFalse(player.isCancelled());
    }
}
