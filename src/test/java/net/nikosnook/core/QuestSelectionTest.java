package net.nikosnook.core;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class QuestSelectionTest {
    @TempDir Path folder;
    NookStore store;UUID player=UUID.randomUUID();
    long now=Instant.parse("2026-11-04T03:00:00Z").toEpochMilli();String week="2026-11-04";
    List<QuestPlan.Goal> board(){
        String[] targets={"ZOMBIE","SKELETON","SPIDER","CREEPER","HUSK","STRAY","DROWNED","WITCH","WARDEN","WITHER"};
        var goals=new ArrayList<QuestPlan.Goal>();for(int n=0;n<10;n++)goals.add(new QuestPlan.Goal("goal"+n,"Defeat "+targets[n],"KILL",targets[n],1,n<8?1500:3000));return goals;
    }
    Set<String> choices(){return Set.of("goal0","goal1","goal2","goal3","goal4","goal5","goal8");}
    @BeforeEach void start()throws Exception{store=new NookStore(folder.resolve("nooks.db"));store.join(player,"Player",now,0);store.questRotation(week,board());}
    @AfterEach void end()throws Exception{store.close();}
    @Test void unacceptedBoardCannotProgressEvenWithoutLegacyNpcGate()throws Exception{
        assertFalse(store.questAccepted(player,week));assertTrue(store.playerQuestGoals(player,week).isEmpty());
        assertTrue(store.advanceQuests(player,week,"KILL","ZOMBIE",null,now).isEmpty());
        assertThrows(IllegalArgumentException.class,()->store.acceptNiko(player,week,"admin",now));assertFalse(store.questAccepted(player,week));
    }
    @Test void onlyChosenTasksPayAndMaximumRemains120Nooks()throws Exception{
        assertTrue(store.acceptNiko(player,week,choices(),"niko",now));
        assertEquals(7,store.playerQuestGoals(player,week).size());
        for(var goal:board())store.advanceQuests(player,week,"KILL",goal.target(),null,now);
        assertEquals(12000,store.account(player).orElseThrow().cents());assertEquals(0,store.questProgress(player,week,"goal6"));assertEquals(0,store.questProgress(player,week,"goal9"));
        for(var goal:board())assertTrue(store.advanceQuests(player,week,"KILL",goal.target(),null,now).isEmpty());
        assertEquals(12000,store.account(player).orElseThrow().cents());
    }
    @Test void playersCanChooseDifferentGoalsFromTheSameBoard()throws Exception{
        UUID other=UUID.randomUUID();store.join(other,"Other",now,0);store.acceptNiko(player,week,choices(),"niko",now);
        var second=Set.of("goal2","goal3","goal4","goal5","goal6","goal7","goal9");store.acceptNiko(other,week,second,"niko",now);
        assertTrue(store.advanceQuests(other,week,"KILL","ZOMBIE",null,now).isEmpty());assertEquals(1,store.advanceQuests(player,week,"KILL","ZOMBIE",null,now).size());
        assertEquals(choices(),store.questChoices(player,week));assertEquals(second,store.questChoices(other,week));
    }
    @Test void validationRejectsUnknownDuplicateAndWrongChallengeCounts()throws Exception{
        var duplicate=new ArrayList<>(choices());duplicate.set(0,duplicate.get(1));assertThrows(IllegalArgumentException.class,()->store.acceptNiko(player,week,duplicate,"niko",now));
        var unknown=new HashSet<>(choices());unknown.remove("goal0");unknown.add("unknown");assertThrows(IllegalArgumentException.class,()->store.acceptNiko(player,week,unknown,"niko",now));
        assertThrows(IllegalArgumentException.class,()->store.acceptNiko(player,week,Set.of("goal0","goal1","goal2","goal3","goal4","goal8","goal9"),"niko",now));
        assertFalse(store.questAccepted(player,week));assertTrue(store.questChoices(player,week).isEmpty());
    }
    @Test void choicesCannotBeChangedAndDuplicateConfirmationIsHarmless()throws Exception{
        store.acceptNiko(player,week,choices(),"niko",now);assertFalse(store.acceptNiko(player,week,choices(),"niko",now));
        var changed=new HashSet<>(choices());changed.remove("goal0");changed.add("goal6");assertThrows(IllegalArgumentException.class,()->store.acceptNiko(player,week,changed,"admin",now));
        assertEquals(choices(),store.questChoices(player,week));
    }
    @Test void restartPreservesChoicesAndProgress()throws Exception{
        store.acceptNiko(player,week,choices(),"niko",now);store.advanceQuests(player,week,"KILL","ZOMBIE",null,now);store.close();store=new NookStore(folder.resolve("nooks.db"));
        assertTrue(store.questAccepted(player,week));assertEquals(choices(),store.questChoices(player,week));assertEquals(1,store.questProgress(player,week,"goal0"));assertEquals(1500,store.account(player).orElseThrow().cents());
    }
    @Test void staleWeekDoesNotWriteAnAcceptance()throws Exception{
        assertThrows(IllegalArgumentException.class,()->store.acceptNiko(player,week,choices(),"niko",now+7*86400000L));assertFalse(store.questAccepted(player,week));
    }
    @Test void oldSharedRotationAndAcceptedProgressSurviveInstallation()throws Exception{
        String old="2026-10-28";long then=now-7*86400000L;var legacy=board().stream().filter(g->choices().contains(g.id())).toList();
        store.questRotation(old,legacy);store.configureNikoGate(true,true,then);store.acceptNiko(player,old,"niko",then);store.advanceQuests(player,old,"KILL","ZOMBIE",null,then);
        assertEquals(legacy,store.questRotation(old,board()));assertTrue(store.questAccepted(player,old));assertEquals(7,store.playerQuestGoals(player,old).size());assertEquals(1,store.questProgress(player,old,"goal0"));
    }
    @Test void unselectedContributionRejectedBeforeCustodyAndSelectedOneCompletes()throws Exception{
        String next="2026-11-11";long later=now+7*86400000L;var offers=new ArrayList<>(board());offers.set(0,new QuestPlan.Goal("goal0","Contribute stone","DEPOSIT","STONE",1,1500));offers.set(6,new QuestPlan.Goal("goal6","Contribute tuff","DEPOSIT","TUFF",1,1500));store.questRotation(next,offers);store.acceptNiko(player,next,choices(),"niko",later);
        assertThrows(IllegalArgumentException.class,()->store.beginQuestDeposit(UUID.randomUUID(),player,next,"TUFF",1,"a","b",later));assertTrue(store.questDepositReport().isEmpty());
        UUID receipt=UUID.randomUUID();store.beginQuestDeposit(receipt,player,next,"STONE",1,"a","b",later);assertTrue(store.finishQuestDeposit(receipt,later).getFirst().paid());assertEquals(1500,store.account(player).orElseThrow().cents());
    }
    @Test void menuToggleEnforcesSixAndSwapsChallengeWithoutAnExtraSlot(){
        var selected=new HashSet<>(choices());assertThrows(IllegalArgumentException.class,()->QuestSelection.toggle(board(),selected,"goal6"));
        var swapped=QuestSelection.toggle(board(),selected,"goal9");assertFalse(swapped.contains("goal8"));assertTrue(swapped.contains("goal9"));assertEquals(7,swapped.size());
        assertEquals(6,QuestSelection.toggle(board(),swapped,"goal0").size());
    }
    @Test void offerBoardsStayVariedAndAvoidPreviousTargetsForTwoYears(){
        var bank=QuestRotationBankTest.bundled();List<QuestPlan.Goal> prior=List.of();
        for(int i=0;i<104;i++){
            String date=java.time.LocalDate.of(2026,11,4).plusWeeks(i).toString();var offers=bank.offers(date,prior);assertTrue(QuestSelection.board(offers),date);
            assertEquals(10,offers.stream().map(QuestRotationBank::target).distinct().count());assertTrue(offers.stream().filter(g->g.kind().equals("DEPOSIT")).count()<=2);
            var old=prior.stream().map(QuestRotationBank::target).collect(java.util.stream.Collectors.toSet());assertTrue(offers.stream().noneMatch(g->old.contains(QuestRotationBank.target(g))),date);
            assertEquals(offers,bank.offers(date,prior));prior=offers;
        }
    }
}
