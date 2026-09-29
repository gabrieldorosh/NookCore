package net.nikosnook.core;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SelectedQuestTest {
    @TempDir Path dir;
    static QuestPlan.Goal deposit(String id,String target,int amount){return new QuestPlan.Goal(id,id,"DEPOSIT",target,amount,1500);}
    @Test void selectedMaterialRoutesWithoutAccidentallyPickingFirstGoal(){
        var rails=deposit("railway","RAIL",128);var logs=deposit("timber","OAK_LOG",256);var goals=List.of(rails,logs);
        assertEquals(logs,WeeklyQuests.contribution(goals,"oak_log"));assertEquals(rails,WeeklyQuests.contribution(goals,"RAIL"));
        assertThrows(IllegalArgumentException.class,()->WeeklyQuests.contribution(goals,null));assertThrows(IllegalArgumentException.class,()->WeeklyQuests.contribution(goals,"DIAMOND"));
        assertEquals(rails,WeeklyQuests.contribution(List.of(rails),null));assertDoesNotThrow(()->WeeklyQuests.validateContributions(goals));
        assertThrows(IllegalArgumentException.class,()->WeeklyQuests.validateContributions(List.of(rails,deposit("second","RAIL",32))));
    }
    @Test void separateMaterialsHaveIndependentReceiptsProgressRewardsAndStock()throws Exception{
        long now=Instant.parse("2026-09-30T01:00:00Z").toEpochMilli();String week=QuestPlan.week(now).id();UUID player=UUID.randomUUID();
        try(var store=new NookStore(dir.resolve("quests.db"))){
            store.join(player,"Player",now,6000);store.questRotation(week,List.of(deposit("railway","RAIL",128),deposit("timber","OAK_LOG",256)));
            UUID rail=UUID.randomUUID();store.beginQuestDeposit(rail,player,week,"RAIL",64,"before","after",now);store.finishQuestDeposit(rail,now);
            assertEquals(64,store.questProgress(player,week,"railway"));assertEquals(0,store.questProgress(player,week,"timber"));assertEquals(6000,store.account(player).orElseThrow().cents());
            UUID log=UUID.randomUUID();store.beginQuestDeposit(log,player,week,"OAK_LOG",256,"before","after",now);store.finishQuestDeposit(log,now);
            assertEquals(7500,store.account(player).orElseThrow().cents());assertThrows(IllegalArgumentException.class,()->store.finishQuestDeposit(log,now));
            UUID rest=UUID.randomUUID();store.beginQuestDeposit(rest,player,week,"RAIL",64,"before","after",now);store.finishQuestDeposit(rest,now);assertEquals(9000,store.account(player).orElseThrow().cents());
            assertThrows(IllegalArgumentException.class,()->store.beginQuestDeposit(UUID.randomUUID(),player,week,"RAIL",1,"before","after",now));
            for(int n=0;n<2;n++){UUID claim=UUID.randomUUID();store.beginQuestClaim(claim,"RAIL",64,"staff");store.finishQuestClaim(claim,true);}
            for(int n=0;n<4;n++){UUID claim=UUID.randomUUID();store.beginQuestClaim(claim,"OAK_LOG",64,"staff");store.finishQuestClaim(claim,true);}
            assertThrows(IllegalArgumentException.class,()->store.beginQuestClaim(UUID.randomUUID(),"RAIL",1,"staff"));
            assertThrows(IllegalArgumentException.class,()->store.beginQuestClaim(UUID.randomUUID(),"OAK_LOG",1,"staff"));
        }
        try(var store=new NookStore(dir.resolve("quests.db"))){assertEquals(128,store.questProgress(player,week,"railway"));assertEquals(256,store.questProgress(player,week,"timber"));assertEquals(9000,store.account(player).orElseThrow().cents());}
    }
    @Test void interruptedReceiptStillBlocksCrossMaterialDeposits()throws Exception{
        long now=Instant.parse("2026-09-30T01:00:00Z").toEpochMilli();String week=QuestPlan.week(now).id();UUID player=UUID.randomUUID();
        try(var store=new NookStore(dir.resolve("quests.db"))){
            store.join(player,"Player",now,6000);store.questRotation(week,List.of(deposit("railway","RAIL",128),deposit("timber","OAK_LOG",256)));
            UUID pending=UUID.randomUUID();store.beginQuestDeposit(pending,player,week,"RAIL",32,"before","after",now);
            assertThrows(IllegalArgumentException.class,()->store.beginQuestDeposit(UUID.randomUUID(),player,week,"OAK_LOG",32,"before","after",now));
            store.cancelQuestDeposit(pending);assertDoesNotThrow(()->store.beginQuestDeposit(UUID.randomUUID(),player,week,"OAK_LOG",32,"before","after",now));
        }
    }
    @Test void normalAndDeepslateDiamondOreShareOneBlockTarget(){
        assertEquals("DIAMOND",WeeklyQuests.ore(Material.DIAMOND_ORE));assertEquals("DIAMOND",WeeklyQuests.ore(Material.DEEPSLATE_DIAMOND_ORE));
        assertNull(WeeklyQuests.ore(Material.DIAMOND_BLOCK));assertNull(WeeklyQuests.ore(Material.DIAMOND));
    }
    @Test void selectedFileHasSixRegularAndOneChallengeAndFreezesOnlyNextWeek()throws Exception{
        var config=YamlConfiguration.loadConfiguration(Path.of("release/september-30/quests.yml").toFile());var section=config.getConfigurationSection("goals");assertNotNull(section);
        var goals=new ArrayList<QuestPlan.Goal>();for(String id:section.getKeys(false)){var c=section.getConfigurationSection(id);goals.add(new QuestPlan.Goal(id,c.getString("title"),c.getString("kind"),c.getString("target"),c.getInt("amount"),c.getLong("reward-cents")));}
        assertEquals(7,goals.size());assertEquals(6,goals.stream().filter(g->g.reward()==1500).count());assertEquals(1,goals.stream().filter(g->g.reward()==3000).count());assertEquals(12000,goals.stream().mapToLong(QuestPlan.Goal::reward).sum());WeeklyQuests.validateContributions(goals);
        long before=Instant.parse("2026-09-30T00:59:59Z").toEpochMilli(),after=before+1000;
        try(var store=new NookStore(dir.resolve("freeze.db"))){
            var old=List.of(new QuestPlan.Goal("old","Old","MINE","COAL",1,1500));String previous=QuestPlan.week(before).id(),next=QuestPlan.week(after).id();assertNotEquals(previous,next);
            store.questRotation(previous,old);assertEquals(old,store.questRotation(previous,goals));assertEquals(goals,store.questRotation(next,goals));assertEquals(goals,store.questRotation(next,old));
        }
    }
}
