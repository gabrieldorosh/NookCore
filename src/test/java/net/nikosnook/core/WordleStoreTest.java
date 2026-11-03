package net.nikosnook.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class WordleStoreTest {
 @TempDir Path temp;NookStore money;WordleStore game;UUID a=UUID.randomUUID(),b=UUID.randomUUID();
 long now=Instant.parse("2026-11-04T12:00:00Z").toEpochMilli();
 WordleWords words(){return new WordleWords(List.of("apple","flame"),List.of("apple","flame","crane","stare","brick","stone","grass","caper","allee"));}
 @BeforeEach void setup()throws Exception{money=new NookStore(temp.resolve("nooks.db"));money.join(a,"Alice",now,0);money.join(b,"Bob",now,0);game=new WordleStore(new GameLedger(money),words(),100,bound->0);}
 @AfterEach void close()throws Exception{money.close();}
 long balance(UUID player)throws Exception{return money.account(player).orElseThrow().cents();}
 @Test void bareGuessNeedsTheDisplayedDayAndUnknownWordsConsumeNothing()throws Exception{
  assertThrows(WordleStore.StaleBoardException.class,()->game.submit(a,null,"apple",now));
  var board=game.open(a,now).board();assertEquals(0,board.guesses().size());assertNull(board.revealedAnswer());
  assertThrows(IllegalArgumentException.class,()->game.submit(a,null,"zzzzz",now));assertEquals(0,game.open(a,now).board().guesses().size());assertEquals(0,balance(a));
 }
 @Test void repeatsAreRejectedWithoutAnotherAttempt()throws Exception{
  game.open(a,now);game.submit(a,null,"crane",now);assertThrows(IllegalArgumentException.class,()->game.submit(a,null,"CRANE",now));assertEquals(1,game.open(a,now).board().guesses().size());
 }
 @Test void sixthGuessCanWinAndNoMoreAttemptsOrAwardsAreCreated()throws Exception{
  String day=game.open(a,now).board().day();for(String word:List.of("crane","stare","brick","stone","grass"))assertEquals(WordleStore.State.ACTIVE,game.submit(a,day,word,now).board().state());
  var won=game.submit(a,day,"apple",now);assertEquals(WordleStore.State.WON,won.board().state());assertEquals(6,won.board().guesses().size());assertEquals(100,won.paidNow());assertEquals(100,balance(a));
  var repeat=game.submit(a,day,"apple",now);assertFalse(repeat.consumed());assertEquals(0,repeat.paidNow());assertEquals(6,repeat.board().guesses().size());assertEquals(100,balance(a));
 }
 @Test void sixthWrongGuessLosesAndOnlyThenRevealsTheAnswer()throws Exception{
  game.open(a,now);for(String word:List.of("crane","stare","brick","stone","grass","caper"))game.submit(a,null,word,now);
  var lost=game.open(a,now);assertEquals(WordleStore.State.LOST,lost.board().state());assertEquals("apple",lost.board().revealedAnswer());assertEquals(0,balance(a));
  assertFalse(game.submit(a,null,"apple",now).consumed());assertEquals(0,balance(a));
 }
 @Test void playersShareThePuzzleButHaveIndependentAttemptsAndRewards()throws Exception{
  var first=game.open(a,now);game.open(b,now);game.submit(a,null,"crane",now);assertTrue(game.open(b,now).board().guesses().isEmpty());
  assertEquals(WordleStore.State.WON,game.submit(a,null,"apple",now).board().state());assertEquals(WordleStore.State.WON,game.submit(b,null,"apple",now).board().state());
  assertEquals(100,balance(a));assertEquals(100,balance(b));
 }
 @Test void attemptsPuzzleAndRewardSurviveRestartAndChangedConfiguration()throws Exception{
  game.open(a,now);game.submit(a,null,"crane",now);money.close();money=new NookStore(temp.resolve("nooks.db"));
  game=new WordleStore(new GameLedger(money),new WordleWords(List.of("flame"),List.of("flame","crane")),900,bound->0);
  var loaded=game.open(a,now);assertEquals(1,loaded.board().guesses().size());assertEquals(100,loaded.board().reward());
  assertEquals(WordleStore.State.WON,game.submit(a,null,"apple",now).board().state());assertEquals(100,balance(a));
  money.close();money=new NookStore(temp.resolve("nooks.db"));game=new WordleStore(new GameLedger(money),words(),999,bound->0);assertEquals(0,game.open(a,now).paidNow());assertEquals(100,balance(a));
 }
 @Test void staleBareAndExplicitGuessesCannotLandInTheNextDaysPuzzle()throws Exception{
  var old=game.open(a,now).board();long next=old.nextReset();
  assertThrows(WordleStore.StaleBoardException.class,()->game.submit(a,null,"flame",next));
  assertThrows(WordleStore.StaleBoardException.class,()->game.submit(a,old.day(),"flame",next));
  var fresh=game.open(a,next);assertNotEquals(old.day(),fresh.board().day());assertTrue(fresh.board().guesses().isEmpty());assertEquals(WordleStore.State.WON,game.submit(a,null,"flame",next).board().state());
 }
 @Test void aFullBalanceKeepsTheSolveAndClaimSurvivesEvenTheNextDailyReset()throws Exception{
  money.adjust(a,Money.MAX,"console","test capacity",now);game.open(a,now);var won=game.submit(a,null,"apple",now);
  assertEquals(WordleStore.State.WON,won.board().state());assertFalse(won.board().rewardPaid());assertEquals(0,won.paidNow());assertEquals(Money.MAX,balance(a));
  long next=won.board().nextReset();money.transfer(a,b,100,next);var claimed=game.open(a,next);
  assertEquals(100,claimed.paidNow());assertTrue(claimed.anySolved());assertEquals(Money.MAX,balance(a));assertEquals(0,game.open(a,next).paidNow());
 }
 @Test void simultaneousWinningRequestsPayAndConsumeOnlyOnce()throws Exception{
  String day=game.open(a,now).board().day();try(var pool=Executors.newFixedThreadPool(4)){
   var tasks=new ArrayList<Future<WordleStore.Result>>();for(int i=0;i<8;i++)tasks.add(pool.submit(()->game.submit(a,day,"apple",now)));
   int attempts=0;long paid=0;for(var task:tasks){var result=task.get();if(result.consumed())attempts++;paid+=result.paidNow();}assertEquals(1,attempts);assertEquals(100,paid);
  }assertEquals(100,balance(a));assertEquals(1,game.open(a,now).board().guesses().size());
 }
 @Test void openingManyPlayersDrawsTheSharedAnswerOnlyOnce()throws Exception{
  var draws=new AtomicInteger();game=new WordleStore(new GameLedger(money),words(),100,bound->{draws.incrementAndGet();return 0;});
  game.open(a,now);game.open(b,now);game.open(a,now);assertEquals(1,draws.get());
 }
 @Test void bundledSizedPoolsAvoidTheLastThirtyPublishedAnswers()throws Exception{
  var answers=new ArrayList<String>();for(int i=0;i<40;i++)answers.add("aaa"+(char)('a'+i/26)+(char)('a'+i%26));
  game=new WordleStore(new GameLedger(money),new WordleWords(answers,answers),0,bound->0);
  for(int i=0;i<31;i++){long day=now+i*86_400_000L;game.open(a,day);assertEquals(WordleStore.State.WON,game.submit(a,null,answers.get(i),day).board().state());}
  long next=now+31*86_400_000L;game.open(a,next);assertEquals(WordleStore.State.WON,game.submit(a,null,answers.getFirst(),next).board().state());
 }
 @Test void zeroRewardAndDailyRenewalDoNotInventMoney()throws Exception{
  game=new WordleStore(new GameLedger(money),words(),0,bound->0);var first=game.open(a,now);var won=game.submit(a,null,"apple",now);assertTrue(won.board().rewardPaid());assertEquals(0,balance(a));
  var next=game.open(a,first.board().nextReset());assertTrue(next.board().guesses().isEmpty());assertEquals(WordleStore.State.ACTIVE,next.board().state());assertEquals(0,balance(a));
 }
}
