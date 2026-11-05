package net.nikosnook.core;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;

class QuestBankToolsTest {
 @TempDir Path folder;
 NookStore store;AtomicLong clock=new AtomicLong(Instant.parse("2026-11-03T12:00:00Z").toEpochMilli());List<Exception> failures=new ArrayList<>();CommandSender admin;
 CommandSender sender(boolean allowed){return (CommandSender)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{CommandSender.class},(o,m,a)->m.getName().equals("hasPermission")?allowed:null);}
 @BeforeEach void setup()throws Exception{store=new NookStore(folder.resolve("nooks.db"));admin=sender(true);}
 @AfterEach void close()throws Exception{store.close();}
 WeeklyQuests quests(AutomaticQuests settings){var bank=QuestRotationBankTest.bundled();return new WeeklyQuests(store,()->true,failures::add,bank.select("2026-10-28",List.of()),clock::get,settings);}
 AutomaticQuests automatic(){return new AutomaticQuests(true,"2026-11-04",QuestRotationBankTest.bundled());}
 @Test void currentBoardReviewReadsOnlyTheSavedSnapshot()throws Exception{
  var q=quests(automatic());clock.set(Instant.parse("2026-11-04T03:00:00Z").toEpochMilli());
  var lines=new ArrayList<String>();
  var reviewer=(CommandSender)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{CommandSender.class},(o,m,a)->{
   if(m.getName().equals("hasPermission"))return true;
   if(m.getName().equals("sendMessage") && a[0] instanceof net.kyori.adventure.text.Component c)
    lines.add(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c));
   return null;
  });
  q.onCommand(reviewer,null,"quests",new String[]{"current"});
  assertTrue(store.savedQuestRotation("2026-11-04").isEmpty());
  assertTrue(lines.toString().contains("hasn't been published yet"));
  var board=q.nikoGoals();assertEquals(11,board.size());lines.clear();
  q.onCommand(reviewer,null,"quests",new String[]{"current"});
  assertEquals(12,lines.size());
  for(var goal:board)assertTrue(lines.stream().anyMatch(line->line.contains(goal.title())),goal.title());
  assertEquals(board,store.savedQuestRotation("2026-11-04").orElseThrow());
  assertTrue(failures.isEmpty());
 }
 @Test void validationReadsCandidateWithoutReplacingLoadedBankOrSavingFutureSnapshots()throws Exception{
  var q=quests(AutomaticQuests.disabled());var current=q.nikoGoals();var calls=new AtomicInteger();q.automaticLoader(()->{calls.incrementAndGet();return automatic();});
  q.onCommand(admin,null,"quests",new String[]{"validate"});assertEquals(1,calls.get());assertEquals(current,q.nikoGoals());assertTrue(store.savedQuestRotation("2026-11-04").isEmpty());
  clock.set(Instant.parse("2026-11-04T03:00:00Z").toEpochMilli());assertEquals(7,q.nikoGoals().size());assertTrue(failures.isEmpty());
 }
 @Test void reloadAppliesOnlyToUnsavedFutureWeeks()throws Exception{
  var q=quests(AutomaticQuests.disabled());var current=q.nikoGoals();q.automaticLoader(this::automatic);q.onCommand(admin,null,"quests",new String[]{"reload"});
  assertEquals(current,q.nikoGoals());assertTrue(store.savedQuestRotation("2026-11-04").isEmpty());clock.set(Instant.parse("2026-11-04T03:00:00Z").toEpochMilli());assertTrue(QuestSelection.board(q.nikoGoals()));assertEquals(current,store.savedQuestRotation("2026-10-28").orElseThrow());
 }
 @Test void badReloadKeepsCurrentBankAndDoesNotPauseEconomy()throws Exception{
  var q=quests(automatic());var current=q.nikoGoals();q.automaticLoader(()->{throw new IllegalArgumentException("regular.new-quest has a bad amount");});q.onCommand(admin,null,"quests",new String[]{"reload"});
  assertEquals(current,q.nikoGoals());assertTrue(failures.isEmpty());clock.set(Instant.parse("2026-11-04T03:00:00Z").toEpochMilli());assertTrue(QuestSelection.board(q.nikoGoals()));
 }
 @Test void nonAdminCannotReadOrReloadThePrivateBank()throws Exception{
  var q=quests(automatic());var calls=new AtomicInteger();q.automaticLoader(()->{calls.incrementAndGet();return automatic();});var player=sender(false);q.onCommand(player,null,"quests",new String[]{"reload"});assertEquals(0,calls.get());assertTrue(failures.isEmpty());
 }
 @Test void disabledMalformedEntriesAreIgnoredAndOtherErrorsAreCollected()throws Exception{
  var config=new YamlConfiguration();config.loadFromString(new String(Objects.requireNonNull(getClass().getResourceAsStream("/quest-rotation.yml")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
  config.set("regular.todo.enabled",false);config.set("regular.todo.amount",-1);assertEquals(46,AutomaticQuests.parse(config,goal->{}).bank().regularCount());
  config.set("regular.bad-one.kind","BOGUS");config.set("regular.bad-two.kind","BOGUS");String message=assertThrows(IllegalArgumentException.class,()->AutomaticQuests.parse(config,goal->{})).getMessage();assertTrue(message.contains("bad-one"));assertTrue(message.contains("bad-two"));
 }
 @Test void sharedWeekStillChecksCompletionWithoutPayingAnotherReward()throws Exception{
  var bank=QuestRotationBankTest.bundled();var q=quests(AutomaticQuests.disabled());var goals=q.nikoGoals();UUID playerId=UUID.randomUUID();store.join(playerId,"Player",clock.get(),0);var player=(org.bukkit.entity.Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{org.bukkit.entity.Player.class},(o,m,a)->switch(m.getName()){case "getUniqueId"->playerId;case "getGameMode"->org.bukkit.GameMode.SURVIVAL;default->null;});
  List<String> events=new ArrayList<>();q.setMilestones((event,p)->events.add(event));q.nikoMilestone(player);assertFalse(events.contains("WEEK_COMPLETE"));assertEquals(0,store.account(playerId).orElseThrow().cents());
 }
 @Test void completedWeekMilestoneIsCosmeticAndCanRecoverOnProgressView()throws Exception{
  UUID id=UUID.randomUUID();store.join(id,"Player",clock.get(),0);var goal=new QuestPlan.Goal("hunt","Defeat a zombie","KILL","ZOMBIE",1,1500);
  var q=new WeeklyQuests(store,()->true,failures::add,List.of(goal),clock::get);var player=(org.bukkit.entity.Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{org.bukkit.entity.Player.class},(o,m,a)->switch(m.getName()){case "getUniqueId"->id;case "getGameMode"->org.bukkit.GameMode.SURVIVAL;default->null;});
  List<String> events=new ArrayList<>();q.setMilestones((event,p)->events.add(event));assertTrue(q.record(player,"KILL","ZOMBIE",null));assertTrue(events.contains("WEEK_COMPLETE"));assertEquals(1500,store.account(id).orElseThrow().cents());
  events.clear();q.nikoMilestone(player);assertTrue(events.contains("WEEK_COMPLETE"));assertEquals(1500,store.account(id).orElseThrow().cents());assertTrue(failures.isEmpty());
 }

}
