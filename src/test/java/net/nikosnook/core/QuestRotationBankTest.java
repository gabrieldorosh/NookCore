package net.nikosnook.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.junit.jupiter.api.Assertions.*;

class QuestRotationBankTest {
    @TempDir Path folder;
    @SuppressWarnings("unchecked") static QuestRotationBank bundled(){
        Map<String,Object> config=new Yaml(new SafeConstructor(new LoaderOptions())).load(QuestRotationBankTest.class.getResourceAsStream("/quest-rotation.yml"));
        List<QuestPlan.Goal> regular=new ArrayList<>(),challenges=new ArrayList<>();
        for(String group:List.of("regular","challenges"))for(var item:((Map<String,Map<String,Object>>)config.get(group)).entrySet()){
            var d=item.getValue();var g=new QuestPlan.Goal(item.getKey(),(String)d.get("title"),(String)d.get("kind"),(String)d.get("target"),((Number)d.get("amount")).intValue(),group.equals("regular")?1500:3000);
            (group.equals("regular")?regular:challenges).add(g);
        }
        return new QuestRotationBank(regular,challenges);
    }
    @Test void expandedBankMakesVariedWeeksWithoutRepeatingTargets(){
        var bank=bundled();assertTrue(bank.regularCount()>=40);assertTrue(bank.challengeCount()>=12);List<QuestPlan.Goal> previous=List.of();
        for(int n=0;n<104;n++){
            String week=java.time.LocalDate.of(2026,11,4).plusWeeks(n).toString();var chosen=bank.select(week,previous);
            assertEquals(7,chosen.size());assertEquals(6,chosen.stream().filter(g->g.reward()==1500).count());assertEquals(1,chosen.stream().filter(g->g.reward()==3000).count());
            assertEquals(12000,chosen.stream().mapToLong(QuestPlan.Goal::reward).sum());assertEquals(7,chosen.stream().map(QuestRotationBank::target).distinct().count());
            assertTrue(chosen.stream().filter(g->g.kind().equals("DEPOSIT")).count()<=2);assertTrue(chosen.stream().map(QuestPlan.Goal::kind).distinct().count()>=3);
            var targets=previous.stream().map(QuestRotationBank::target).collect(java.util.stream.Collectors.toSet());assertTrue(chosen.stream().noneMatch(g->targets.contains(QuestRotationBank.target(g))));
            previous=chosen;
        }
    }
    @Test void choiceIsDeterministicAcrossRestart(){var bank=bundled();var prior=bank.select("2026-10-28",List.of());assertEquals(bank.select("2026-11-04",prior),bundled().select("2026-11-04",prior));}
    @Test void savedActiveWeekWinsOverANewBank()throws Exception{
        var bank=bundled();var clock=new AtomicLong(Instant.parse("2026-11-01T12:00:00Z").toEpochMilli());
        try(var store=new NookStore(folder.resolve("nooks.db"))){
            var current=bank.select("2026-10-28",List.of());store.questRotation("2026-10-28",current);
            var q=new WeeklyQuests(store,()->true,e->{throw new AssertionError(e);},bank.select("2026-10-21",List.of()),clock::get,new AutomaticQuests(true,"2026-10-28",bank));
            assertEquals(current,q.nikoGoals());assertTrue(store.accounts().isEmpty());
        }
    }
    @Test void rolloverSelectsFreshGoalsAndRestartKeepsThem()throws Exception{
        var bank=bundled();var clock=new AtomicLong(Instant.parse("2026-11-04T01:59:59Z").toEpochMilli());
        try(var store=new NookStore(folder.resolve("nooks.db"))){
            var manual=bank.select("2026-10-28",List.of());var settings=new AutomaticQuests(true,"2026-11-04",bank);
            var q=new WeeklyQuests(store,()->true,e->{throw new AssertionError(e);},manual,clock::get,settings);
            assertEquals(manual,q.nikoGoals());assertEquals("2026-10-28",q.nikoWeek());
            clock.set(Instant.parse("2026-11-04T02:00:00Z").toEpochMilli());var next=q.nikoGoals();assertEquals("2026-11-04",q.nikoWeek());
            assertFalse(QuestPlan.sameDefinitions(manual,next));assertEquals(manual,store.savedQuestRotation("2026-10-28").orElseThrow());
            var restarted=new WeeklyQuests(store,()->true,e->{throw new AssertionError(e);},manual,clock::get,settings);assertEquals(next,restarted.nikoGoals());assertTrue(store.accounts().isEmpty());
        }
    }
    @Test void snapshotQueriesAreReadOnly()throws Exception{
        try(var store=new NookStore(folder.resolve("nooks.db"))){assertTrue(store.savedQuestRotation("2026-11-04").isEmpty());assertTrue(store.previousQuestRotation("2026-11-04").isEmpty());assertTrue(store.savedQuestRotation("2026-11-04").isEmpty());}
    }
    @Test void incompleteBankIsRejected(){assertThrows(IllegalArgumentException.class,()->new QuestRotationBank(List.of(),List.of()));}
}
