package net.nikosnook.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WeeklyQuestsTest {
    @TempDir Path dir;
    long now=Instant.parse("2026-09-25T18:00:00Z").toEpochMilli();
    UUID id=UUID.randomUUID();NookStore store;
    QuestPlan.Goal goal=new QuestPlan.Goal("one","Skeleton hunt","KILL","SKELETON",2,1500);
    String week=QuestPlan.week(now).id();
    @BeforeEach void setup()throws Exception{store=new NookStore(dir.resolve("test.db"));store.join(id,"Player",now,6000);store.questRotation(week,List.of(goal));}
    @AfterEach void close()throws Exception{store.close();}
    @Test void resetUsesUkWednesdayBoundary(){
        long reset=Instant.parse("2026-09-23T01:00:00Z").toEpochMilli();
        assertEquals("2026-09-16",QuestPlan.week(reset-1).id());assertEquals("2026-09-23",QuestPlan.week(reset).id());
    }
    @Test void daylightSavingWeeksHaveCalendarLength(){
        long march=Instant.parse("2026-03-25T02:00:00Z").toEpochMilli();
        long october=Instant.parse("2026-10-21T01:00:00Z").toEpochMilli();
        assertEquals(Duration.ofHours(167).toMillis(),QuestPlan.week(march).nextReset()-march);
        assertEquals(Duration.ofHours(169).toMillis(),QuestPlan.week(october).nextReset()-october);
    }
    @Test void payoutOccursExactlyOnceAndSurvivesRestart()throws Exception{
        assertTrue(store.progressQuests(id,week,"KILL","SKELETON",null,now).isEmpty());
        assertEquals(List.of(goal),store.progressQuests(id,week,"KILL","SKELETON",null,now));
        store.close();store=new NookStore(dir.resolve("test.db"));
        assertTrue(store.progressQuests(id,week,"KILL","SKELETON",null,now).isEmpty());
        assertEquals(7500,store.account(id).orElseThrow().cents());assertEquals(2,store.questProgress(id,week,"one"));
    }
    @Test void wrongActionAndOtherPlayerDoNotShareProgress()throws Exception{
        UUID other=UUID.randomUUID();store.join(other,"Other",now,6000);
        store.progressQuests(id,week,"FISH","COD",null,now);store.progressQuests(other,week,"KILL","SKELETON",null,now);
        assertEquals(0,store.questProgress(id,week,"one"));assertEquals(1,store.questProgress(other,week,"one"));
    }
    @Test void definitionsFreezeWithinWeek()throws Exception{
        var changed=new QuestPlan.Goal("one","Changed","KILL","SKELETON",1,3000);
        assertEquals(List.of(goal),store.questRotation(week,List.of(changed)));
    }
    @Test void newRotationStartsFreshButKeepsOldAudit()throws Exception{
        for(int i=0;i<2;i++)store.progressQuests(id,week,"KILL","SKELETON",null,now);
        long later=QuestPlan.week(now).nextReset();String next=QuestPlan.week(later).id();store.questRotation(next,List.of(goal));
        assertEquals(0,store.questProgress(id,next,"one"));
        assertThrows(IllegalArgumentException.class,()->store.progressQuests(id,week,"KILL","SKELETON",null,later));
        for(int i=0;i<2;i++)store.progressQuests(id,next,"KILL","SKELETON",null,later);
        assertEquals(9000,store.account(id).orElseThrow().cents());assertEquals(2,store.questProgress(id,week,"one"));
    }
    @Test void biomeRevisitsCountOnlyOnce()throws Exception{
        long next=QuestPlan.week(now).nextReset();String w=QuestPlan.week(next).id();
        store.questRotation(w,List.of(new QuestPlan.Goal("biomes","Explore","BIOME","ANY",2,1500)));
        store.progressQuests(id,w,"BIOME","ANY","minecraft:plains",next);store.progressQuests(id,w,"BIOME","ANY","minecraft:plains",next);
        assertEquals(1,store.questProgress(id,w,"biomes"));
        store.progressQuests(id,w,"BIOME","ANY","minecraft:forest",next);assertEquals(7500,store.account(id).orElseThrow().cents());
    }
    @Test void failedPayoutRollsBackCompletionAndCanRetry()throws Exception{
        store.adjust(id,Money.MAX-6000,"staff","limit test",now);store.progressQuests(id,week,"KILL","SKELETON",null,now);
        assertThrows(IllegalArgumentException.class,()->store.progressQuests(id,week,"KILL","SKELETON",null,now));
        assertEquals(1,store.questProgress(id,week,"one"));store.adjust(id,-1500,"staff","space",now);
        assertEquals(List.of(goal),store.progressQuests(id,week,"KILL","SKELETON",null,now));assertEquals(Money.MAX,store.account(id).orElseThrow().cents());
    }

    @Test void progressNotificationsReflectOnlyCommittedChanges()throws Exception{
        var first=store.advanceQuests(id,week,"KILL","SKELETON",null,now);
        assertEquals(List.of(new NookStore.QuestUpdate(goal,1,false)),first);
        assertTrue(store.advanceQuests(id,week,"KILL","ZOMBIE",null,now).isEmpty());
        assertEquals(List.of(new NookStore.QuestUpdate(goal,2,true)),store.advanceQuests(id,week,"KILL","SKELETON",null,now));
        assertTrue(store.advanceQuests(id,week,"KILL","SKELETON",null,now).isEmpty());
    }
}
