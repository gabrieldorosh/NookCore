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
    Player caller;SmitePrank prank;Runnable duringDamage=()->{};
    @BeforeEach void setup(){
        caller=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
            case "getUniqueId"->id;case "getName"->"Caller";case "isDead"->dead;case "damage"->{hits++;damage=(double)a[0];duringDamage.run();yield null;}default->null;
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
    @Test void usesOnlyPapersVisualLightningApi(){
        AtomicInteger bolts=new AtomicInteger();
        var world=(org.bukkit.World)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{org.bukkit.World.class},(o,m,a)->{
            assertEquals("strikeLightningEffect",m.getName());bolts.incrementAndGet();return null;
        });
        var player=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
            case "getWorld"->world;case "getLocation"->new org.bukkit.Location(world,0,70,0);default->throw new AssertionError(m.getName());
        });
        SmitePrank.lightning(player);assertEquals(1,bolts.get());
    }
    @Test void jokeDeathOnlyAppliesDuringSelfDamageAndRespectsSuppression(){
        var original=net.kyori.adventure.text.Component.text("ordinary death");
        duringDamage=()->{
            assertNull(prank.deathMessage(caller,null));
            String text=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(prank.deathMessage(caller,original));
            assertEquals("Caller tried to play god and missed.",text);
        };
        run("Target");assertEquals(original,prank.deathMessage(caller,original));
    }
    @Test void failedDamageCannotMarkLaterDeathAsSmite(){
        duringDamage=()->{throw new IllegalStateException();};assertThrows(IllegalStateException.class,()->run("Target"));
        var original=net.kyori.adventure.text.Component.text("later death");assertEquals(original,prank.deathMessage(caller,original));
    }
}
