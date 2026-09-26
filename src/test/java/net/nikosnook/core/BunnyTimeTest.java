package net.nikosnook.core;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BunnyTimeTest {
    @TempDir Path dir;NookStore store;UUID player=UUID.randomUUID();
    @BeforeEach void setup()throws Exception{store=new NookStore(dir.resolve("db"));store.join(player,"Player",0,6000);}
    @AfterEach void close()throws Exception{store.close();}
    @Test void PurchaseDebitsOnceAndTimeSurvivesRestart()throws Exception{
        store.buyBunnyTime(player,2,25,1);assertEquals(5950,store.account(player).orElseThrow().cents());assertEquals(120,store.bunnySeconds(player));
        assertEquals("bunny-time",store.history(player).getFirst().kind());store.close();store=new NookStore(dir.resolve("db"));assertEquals(120,store.bunnySeconds(player));
    }
    @Test void InsufficientBalanceRollsBackTimeCredit()throws Exception{
        assertThrows(IllegalArgumentException.class,()->store.buyBunnyTime(player,60,1000,1));assertEquals(0,store.bunnySeconds(player));assertEquals(6000,store.account(player).orElseThrow().cents());
    }
    @Test void ClosedPurchasesAndInvalidAmountsCannotGrantTime()throws Exception{
        assertThrows(IllegalArgumentException.class,()->store.buyBunnyTime(player,1,0,1));
        for(int amount:new int[]{0,-1,61})assertThrows(IllegalArgumentException.class,()->store.buyBunnyTime(player,amount,1,1));assertEquals(0,store.bunnySeconds(player));
    }
    @Test void ConsumptionCannotGoNegativeOrConsumeAnotherPlayersTime()throws Exception{
        store.grantBunnyTime(player,1,"test",1);for(int i=0;i<60;i++)assertTrue(store.consumeBunnySecond(player));assertFalse(store.consumeBunnySecond(player));assertEquals(0,store.bunnySeconds(player));assertFalse(store.consumeBunnySecond(UUID.randomUUID()));assertEquals(6000,store.account(player).orElseThrow().cents());
    }
    @Test void TimeCapRollsBackPurchaseAndGrant()throws Exception{
        for(int i=0;i<100;i++)store.grantBunnyTime(player,60,"test",1);
        assertThrows(IllegalArgumentException.class,()->store.buyBunnyTime(player,1,1,1));assertThrows(IllegalArgumentException.class,()->store.grantBunnyTime(player,1,"test",1));assertEquals(360000,store.bunnySeconds(player));assertEquals(6000,store.account(player).orElseThrow().cents());
    }
    @Test void OnlyHealthyEnabledLivingSurvivalWearersSpendTime(){
        assertTrue(BunnyBoots.eligible(true,true,true,GameMode.SURVIVAL,false));
        assertFalse(BunnyBoots.eligible(false,true,true,GameMode.SURVIVAL,false));assertFalse(BunnyBoots.eligible(true,false,true,GameMode.SURVIVAL,false));assertFalse(BunnyBoots.eligible(true,true,false,GameMode.SURVIVAL,false));assertFalse(BunnyBoots.eligible(true,true,true,GameMode.SURVIVAL,true));
        for(var mode:List.of(GameMode.CREATIVE,GameMode.SPECTATOR,GameMode.ADVENTURE))assertFalse(BunnyBoots.eligible(true,true,true,mode,false));
    }
    @Test void OnlyBootsCanReceiveTheAbility(){
        assertTrue(BunnyBoots.boots(Material.LEATHER_BOOTS));assertTrue(BunnyBoots.boots(Material.NETHERITE_BOOTS));assertTrue(BunnyBoots.boots(Material.COPPER_BOOTS));assertFalse(BunnyBoots.boots(Material.DIAMOND_HELMET));assertFalse(BunnyBoots.boots(Material.AIR));
    }
}
