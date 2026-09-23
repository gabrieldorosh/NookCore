package net.nikosnook.core;

import org.junit.jupiter.api.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class SmitePrankTest {
    UUID id=UUID.randomUUID();AtomicLong time=new AtomicLong(100000);int hits,effects,visualFailures;double damage;boolean dead;
    Player caller;SmitePrank prank;
    @BeforeEach void setup(){
        caller=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
            case "getUniqueId"->id;case "isDead"->dead;case "damage"->{hits++;damage=(double)a[0];yield null;}default->null;
        });
        prank=new SmitePrank(time::get,p->{assertSame(caller,p);effects++;},e->visualFailures++);
    }
    void run(String... args){prank.onCommand(caller,null,"smite",args);}
    @Test void suppliedTargetIsNeverUsedForDamage(){run("SomeoneElse");assertEquals(1,hits);assertEquals(4,damage);assertEquals(1,effects);}
    @Test void malformedOrDeadCallerHasNoEffect(){run();run("A","B");dead=true;run("A");assertEquals(0,hits);assertEquals(0,effects);}
    @Test void cooldownCannotBeAvoidedByChangingTarget(){run("A");run("B");time.addAndGet(29999);run("C");assertEquals(1,hits);time.incrementAndGet();run("D");assertEquals(2,hits);}
    @Test void consoleCannotCauseDamage(){var console=(CommandSender)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{CommandSender.class},(o,m,a)->null);prank.onCommand(console,null,"smite",new String[]{"A"});assertEquals(0,hits);}
    @Test void brokenCosmeticDoesNotRedirectDamage(){prank=new SmitePrank(time::get,p->{throw new IllegalStateException();},e->visualFailures++);run("Target");assertEquals(1,visualFailures);assertEquals(1,hits);}
    @Test void essentialsAliasIsRoutedToSafePrank(){
        var event=new PlayerCommandPreprocessEvent(caller,"/essentials:smite SomeoneElse",Set.of());prank.command(event);assertTrue(event.isCancelled());assertEquals(1,hits);
    }
}
