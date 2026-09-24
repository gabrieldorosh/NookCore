package net.nikosnook.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LaunchQuestTest {
    @TempDir Path dir;NookStore store;UUID player=UUID.randomUUID();
    long now=Instant.parse("2026-09-25T18:00:00Z").toEpochMilli();String week=QuestPlan.week(now).id();
    @BeforeEach void setup()throws Exception{
        store=new NookStore(dir.resolve("test.db"));store.join(player,"Tester",now,6000);
        store.questRotation(week,List.of(new QuestPlan.Goal("mine","Mine","MINE","COAL",2,1500),new QuestPlan.Goal("community","Build","DEPOSIT","COBBLESTONE",10,3000)));
    }
    @AfterEach void close()throws Exception{store.close();}
    @Test void miningLocationsCannotRepeatAndChangedPositionsPersist()throws Exception{
        store.advanceQuests(player,week,"MINE","COAL","world:1:2:3",now);
        store.advanceQuests(player,week,"MINE","COAL","world:1:2:3",now);
        assertEquals(1,store.questProgress(player,week,"mine"));
        store.markQuestBlock("world:3:2:1");store.close();store=new NookStore(dir.resolve("test.db"));
        assertTrue(store.changedQuestBlock("world:3:2:1"));
        store.advanceQuests(player,week,"MINE","COAL","world:2:2:3",now);assertEquals(7500,store.account(player).orElseThrow().cents());
    }
    UUID deposit(int amount)throws Exception{UUID id=UUID.randomUUID();store.beginQuestDeposit(id,player,week,"COBBLESTONE",amount,"before","after",now);return id;}
    @Test void depositsPayOnceAndCanBeCollectedOnlyOnce()throws Exception{
        UUID id=deposit(10);assertEquals(6000,store.account(player).orElseThrow().cents());
        assertTrue(store.finishQuestDeposit(id,now).getFirst().paid());assertEquals(9000,store.account(player).orElseThrow().cents());
        assertThrows(IllegalArgumentException.class,()->store.finishQuestDeposit(id,now));
        UUID claim=UUID.randomUUID();store.beginQuestClaim(claim,"COBBLESTONE",10,"staff");
        assertThrows(IllegalArgumentException.class,()->store.beginQuestClaim(UUID.randomUUID(),"COBBLESTONE",1,"staff"));
        store.finishQuestClaim(claim,true);assertTrue(store.questDepositReport().stream().anyMatch(s->s.contains("Collected/reserved: 10")));
    }
    @Test void pendingDepositSurvivesRestartAndBlocksAnotherWithoutPayout()throws Exception{
        deposit(4);store.close();store=new NookStore(dir.resolve("test.db"));
        assertThrows(IllegalArgumentException.class,()->deposit(4));assertEquals(0,store.questProgress(player,week,"community"));
        assertTrue(store.questDepositReport().stream().anyMatch(s->s.startsWith("REVIEW")));
    }
    @Test void capacityFailurePreservesPendingProgressAndCanBeCancelled()throws Exception{
        UUID id=deposit(10);store.adjust(player,Money.MAX-6000,"staff","cap",now);
        assertThrows(IllegalArgumentException.class,()->store.finishQuestDeposit(id,now));assertEquals(0,store.questProgress(player,week,"community"));
        store.cancelQuestDeposit(id);store.adjust(player,-3000,"staff","space",now);store.finishQuestDeposit(deposit(10),now);
        assertEquals(Money.MAX,store.account(player).orElseThrow().cents());
    }
    @Test void overDonationAndWrongMaterialAreRejected()throws Exception{
        assertThrows(IllegalArgumentException.class,()->deposit(11));
        assertThrows(IllegalArgumentException.class,()->store.beginQuestDeposit(UUID.randomUUID(),player,week,"DIAMOND",1,"a","b",now));
    }
    @Test void adminPlotRoutingKeepsLegacyCommands(){
        assertArrayEquals(new String[]{"plotclear","willow-way","note"},NookCorePlugin.adminArgs(new String[]{"plot","clear","willow-way","note"}));
        assertArrayEquals(new String[]{"plotclear","willow-way","note"},NookCorePlugin.adminArgs(new String[]{"plotclear","willow-way","note"}));
    }
    @Test void stockContainersAndOreVariantsAreExplicit(){
        assertTrue(ShopSetup.stockContainer(org.bukkit.Material.BARREL));assertTrue(ShopSetup.stockContainer(org.bukkit.Material.BLUE_SHULKER_BOX));
        assertFalse(ShopSetup.stockContainer(org.bukkit.Material.HOPPER));
        assertEquals("COAL",WeeklyQuests.ore(org.bukkit.Material.DEEPSLATE_COAL_ORE));assertNull(WeeklyQuests.ore(org.bukkit.Material.STONE));
    }
}
