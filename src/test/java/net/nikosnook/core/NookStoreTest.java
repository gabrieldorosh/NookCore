package net.nikosnook.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class NookStoreTest {
    @TempDir Path dir;
    NookStore s; UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
    long now=Instant.parse("2026-09-25T18:00:00Z").toEpochMilli();
    @BeforeEach void init()throws Exception {s=new NookStore(dir.resolve("nooks.db"));s.join(a,"Alice",now,6000);s.join(b,"Bob",now,6000);s.join(c,"Charlie",now,6000);s.definePlot("s1",3000);s.definePlot("s2",3000);}
    @AfterEach void close()throws Exception {s.close();}
    long balance(UUID id)throws Exception{return s.account(id).orElseThrow().cents();}
    void accept(String plot,UUID owner,UUID member,String role,long time)throws Exception {s.acceptInvitation(s.invite(plot,owner,member,role,time).token(),member,time);}
    @Test void joiningIsOnceAcrossRestartAndRename()throws Exception{s.close();s=new NookStore(dir.resolve("nooks.db"));assertFalse(s.join(a,"NewAlice",now+1,6000));assertEquals(6000,balance(a));assertEquals(a,s.byName("newalice").orElseThrow().id());}
    @Test void awardIsOnceAfterRegrantAndRestart()throws Exception{assertTrue(s.award(a,"adv:egg",1500,now));s.close();s=new NookStore(dir.resolve("nooks.db"));assertFalse(s.award(a,"adv:egg",1500,now));assertEquals(7500,balance(a));}
    @Test void sameEggPaysEachPlayer()throws Exception{s.award(a,"adv:egg",1500,now);s.award(b,"adv:egg",1500,now);assertEquals(7500,balance(a));assertEquals(7500,balance(b));}
    @Test void exactDecimalTransfersConserveSupply()throws Exception{s.transfer(a,b,1,now);assertEquals(5999,balance(a));assertEquals(6001,balance(b));}
    @Test void missingRecipientRollsBackDebit()throws Exception{assertThrows(IllegalArgumentException.class,()->s.transfer(a,UUID.randomUUID(),100,now));assertEquals(6000,balance(a));assertEquals(1,s.history(a).size());}
    @Test void insufficientFundsDoNotAlterEitherBalance()throws Exception{assertThrows(IllegalArgumentException.class,()->s.transfer(a,b,6001,now));assertEquals(6000,balance(a));assertEquals(6000,balance(b));}
    @Test void fullRecipientRollsBackDebit()throws Exception{s.adjust(b,Money.MAX-6000,"console","test",now);assertThrows(IllegalArgumentException.class,()->s.transfer(a,b,1,now));assertEquals(6000,balance(a));}
    @Test void concurrentTransfersCannotOverspend()throws Exception{
        try(ExecutorService pool=Executors.newFixedThreadPool(8)){List<Future<Boolean>> futures=new ArrayList<>();for(int i=0;i<20;i++)futures.add(pool.submit(()->{try{s.transfer(a,b,1000,now);return true;}catch(IllegalArgumentException e){return false;}}));int success=0;for(Future<Boolean> f:futures)if(f.get())success++;assertEquals(6,success);}
        assertEquals(0,balance(a));assertEquals(12000,balance(b));
    }
    @Test void failedAwardCanBeRetriedAfterBalanceSpaceExists()throws Exception{s.adjust(a,Money.MAX-6000,"console","test",now);assertThrows(IllegalArgumentException.class,()->s.award(a,"adv:test",1,now));s.transfer(a,b,1,now);assertTrue(s.award(a,"adv:test",1,now));}
    @Test void plotRentalDebitsOnceAndReservesMembership()throws Exception{s.rent("s1",a,now);assertEquals(3000,balance(a));assertThrows(Exception.class,()->s.rent("s2",a,now));assertEquals("AVAILABLE",s.plot("s2").state());assertEquals(3000,balance(a));}
    @Test void coOwnerCannotRentAnotherPlot()throws Exception{s.rent("s1",a,now);accept("s1",a,b,"STOCK",now);assertThrows(Exception.class,()->s.rent("s2",b,now));assertEquals(6000,balance(b));assertEquals("STOCK",s.role("s1",b));}
    @Test void nonOwnerCannotChangeMembership()throws Exception{s.rent("s1",a,now);assertThrows(IllegalArgumentException.class,()->s.invite("s1",b,c,"BUILD",now));}
    @Test void insufficientRentalRollsBackReservation()throws Exception{s.transfer(a,b,4000,now);assertThrows(IllegalArgumentException.class,()->s.rent("s1",a,now));assertEquals("NONE",s.role("s1",a));assertEquals("AVAILABLE",s.plot("s1").state());}
    @Test void recentCoOwnerAllowsAutomaticPaymentByOriginalRenter()throws Exception{s.rent("s1",a,now);accept("s1",a,b,"BUILD",now);s.touch(b,now+NookStore.WEEK);s.tick(now+NookStore.WEEK+1);assertEquals("ACTIVE",s.plot("s1").state());assertEquals(0,balance(a));assertEquals(6000,balance(b));}
    @Test void inactivityClosesDespiteBalance()throws Exception{s.rent("s1",a,now);s.adjust(a,30000,"console","test",now);s.tick(now+NookStore.WEEK+1);assertEquals("GRACE",s.plot("s1").state());assertFalse(s.canTrade("s1",now+NookStore.WEEK+1));assertEquals(33000,balance(a));}
    @Test void closedDaysAreFreeAndReopenRetainsBillingDate()throws Exception{s.rent("s1",a,now);s.tick(now+NookStore.WEEK+1);long t=now+NookStore.WEEK+Duration.ofDays(4).toMillis();assertEquals(1286,s.reopen("s1",a,t));assertEquals(now+2*NookStore.WEEK,s.plot("s1").paidUntil());assertTrue(s.canTrade("s1",t));assertEquals(1714,balance(a));}
    @Test void graceDoesNotSilentlyReopenWhenBalanceArrives()throws Exception{s.rent("s1",a,now);s.transfer(a,b,3000,now);s.touch(a,now+NookStore.WEEK);s.tick(now+NookStore.WEEK);s.transfer(b,a,3000,now+NookStore.WEEK);s.tick(now+NookStore.WEEK+1);assertEquals("GRACE",s.plot("s1").state());}
    @Test void graceExpiryRequiresStaffCollectionBeforeReleasingPlot()throws Exception{s.rent("s1",a,now);s.tick(now+2*NookStore.WEEK);assertEquals("RECLAIM",s.plot("s1").state());assertThrows(IllegalArgumentException.class,()->s.rent("s1",b,now));assertThrows(IllegalArgumentException.class,()->s.confirmCleared("s1","staff","",now));s.confirmCleared("s1","staff","archive/alice-001",now);s.rent("s1",b,now);assertEquals(b,s.plot("s1").owner());}
    @Test void prepaymentIsHonouredWhileInactiveAndBounded()throws Exception{s.adjust(a,50000,"console","test",now);s.rent("s1",a,now);s.prepay("s1",a,4,now);assertThrows(IllegalArgumentException.class,()->s.prepay("s1",a,1,now));s.tick(now+4*NookStore.WEEK);assertTrue(s.canTrade("s1",now+4*NookStore.WEEK));s.tick(now+5*NookStore.WEEK);assertEquals("GRACE",s.plot("s1").state());}
    @Test void membershipRemovalRestoresEligibility()throws Exception{s.rent("s1",a,now);accept("s1",a,b,"BUILD",now);s.removeMember("s1",a,b,now);s.rent("s2",b,now);assertEquals(b,s.plot("s2").owner());}
    @Test void expiryBlocksSalesEvenBeforeSchedulerRuns()throws Exception{s.rent("s1",a,now);assertFalse(s.canTrade("s1",now+NookStore.WEEK));}
    @Test void delayedRenewalChargesOnlyRemainingTime()throws Exception{s.rent("s1",a,now);s.transfer(a,b,1000,now);long late=now+NookStore.WEEK+Duration.ofDays(4).toMillis();s.touch(a,late);s.tick(late);assertEquals("ACTIVE",s.plot("s1").state());assertEquals(714,balance(a));}
    @Test void repeatedTickCannotChargeAgain()throws Exception{s.rent("s1",a,now);s.touch(a,now+NookStore.WEEK);s.tick(now+NookStore.WEEK);s.tick(now+NookStore.WEEK);assertEquals(0,balance(a));assertEquals(now+2*NookStore.WEEK,s.plot("s1").paidUntil());}
    @Test void backupIncludesWalAndRestoresIndependently()throws Exception{s.transfer(a,b,1234,now);Path backup=dir.resolve("backup.db");s.backup(backup);try(NookStore restored=new NookStore(backup)){assertEquals(4766,restored.account(a).orElseThrow().cents());assertFalse(restored.join(a,"Alice",now,6000));}}
    @Test void moneyRejectsAmbiguousOrUnsafeInputs(){for(String bad:List.of("NaN","Infinity","-1","0","1e4","1.001","1,000"," 1","999999999999999999999999999"))assertThrows(IllegalArgumentException.class,()->Money.parse(bad),bad);assertEquals(123,Money.parse("1.23"));}
    @Test void prorationRoundsOnce(){assertEquals(1286,Money.prorate(3000,3,7));assertEquals(2143,Money.prorate(5000,3,7));assertEquals(3857,Money.prorate(9000,3,7));}
    @Test void questWeekChangesAtTwoAmUkTime(){assertEquals(Instant.parse("2026-09-30T01:00:00Z"),WeekSchedule.next(Instant.parse("2026-09-25T18:00:00Z")));assertEquals(Instant.parse("2026-09-30T01:00:00Z"),WeekSchedule.start(Instant.parse("2026-09-30T01:00:00Z")));}
    @Test void questWeekTracksDstRatherThanFixed168Hours(){assertEquals(Duration.ofHours(169),Duration.between(WeekSchedule.start(Instant.parse("2026-10-22T12:00:00Z")),WeekSchedule.next(Instant.parse("2026-10-22T12:00:00Z"))));assertEquals(Instant.parse("2026-10-28T02:00:00Z"),WeekSchedule.next(Instant.parse("2026-10-22T12:00:00Z")));}
}
