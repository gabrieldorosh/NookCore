package net.nikosnook.core;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WagerServiceTest {
    @TempDir Path folder;
    NookStore store;GameLedger ledger;WagerService wagers;
    UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
    AtomicInteger draws=new AtomicInteger();
    CoinFlipSettings settings=new CoinFlipSettings(true,100,10_000,90_000,5_000,5);
    @BeforeEach void setup()throws Exception {
        store=new NookStore(folder.resolve("nooks.db"));ledger=new GameLedger(store);
        store.join(a,"Alice",0,10_000);store.join(b,"Bob",0,10_000);store.join(c,"Charlie",0,0);
        wagers=new WagerService(ledger,settings,()->{draws.incrementAndGet();return true;});
    }
    @AfterEach void close()throws Exception {store.close();}
    long balance(UUID id)throws Exception {return store.account(id).orElseThrow().cents();}
    long held()throws Exception {return ledger.transact(GameLedger.Tx::heldTotal);}
    long supply()throws Exception {return store.accounts().stream().mapToLong(NookStore.Account::cents).sum()+ledger.transact(GameLedger.Tx::heldTotal);}
    WagerService.Match invite()throws Exception {return wagers.invite(a,b,1_000,10_000);}
    void reopen()throws Exception {
        store.close();store=new NookStore(folder.resolve("nooks.db"));ledger=new GameLedger(store);
        wagers=new WagerService(ledger,settings,()->{draws.incrementAndGet();return true;});
    }
    @Test void invitationReservesOnlyTheInvitersMoneyAndNeverIssuesCurrency()throws Exception {
        var match=invite();assertEquals(WagerService.State.INVITED,match.state());assertEquals(9_000,balance(a));assertEquals(10_000,balance(b));
        assertEquals(1_000,held());assertEquals(20_000,supply());assertEquals(0,draws.get());
        assertEquals(match,wagers.outgoing(a).orElseThrow());assertEquals(List.of(match),wagers.incoming(b));
        assertEquals(WagerService.RULES,match.rules());assertEquals(100_000,match.expires());
    }
    @Test void theResultAndFullPotAreSavedOnceWithNoHouseFee()throws Exception {
        var waiting=invite();var match=wagers.accept(waiting.id(),b,20_000);
        assertEquals(WagerService.State.SETTLED,match.state());assertEquals(WagerService.Side.HEADS,match.side());assertEquals(a,match.winner());
        assertEquals(11_000,balance(a));assertEquals(9_000,balance(b));assertEquals(0,held());assertEquals(20_000,supply());
        assertEquals(match,wagers.accept(match.id(),b,20_001));assertEquals(1,draws.get());assertEquals(11_000,balance(a));
    }
    @Test void tailsAwardsTheSameFullPotToTheAcceptingPlayer()throws Exception {
        wagers=new WagerService(ledger,settings,()->false);var match=wagers.accept(invite().id(),b,20_000);
        assertEquals(WagerService.Side.TAILS,match.side());assertEquals(b,match.winner());assertEquals(9_000,balance(a));assertEquals(11_000,balance(b));assertEquals(20_000,supply());
    }
    @Test void duplicateConcurrentAcceptsCannotDrawOrPayTwice()throws Exception {
        var match=invite();try(var pool=Executors.newFixedThreadPool(2)){
            var one=pool.submit(()->wagers.accept(match.id(),b,20_000));var two=pool.submit(()->wagers.accept(match.id(),b,20_000));
            assertEquals(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS));
        }
        assertEquals(1,draws.get());assertEquals(11_000,balance(a));assertEquals(20_000,supply());
    }
    @Test void insufficientAcceptingFundsLeaveOnlyTheOriginalHold()throws Exception {
        var match=invite();store.transfer(b,c,10_000,11_000);
        assertThrows(IllegalArgumentException.class,()->wagers.accept(match.id(),b,20_000));
        assertEquals(WagerService.State.INVITED,wagers.inspect(match.id(),a).state());assertEquals(9_000,balance(a));assertEquals(0,balance(b));assertEquals(0,draws.get());
        assertEquals(1_000,held());assertEquals(20_000,supply());
    }
    @Test void wrongActorsCannotAcceptCancelDeclineInspectOrAcknowledge()throws Exception {
        var match=invite();
        assertThrows(IllegalArgumentException.class,()->wagers.accept(match.id(),a,20_000));
        assertThrows(IllegalArgumentException.class,()->wagers.accept(match.id(),c,20_000));
        assertThrows(IllegalArgumentException.class,()->wagers.decline(match.id(),a,20_000));
        assertThrows(IllegalArgumentException.class,()->wagers.cancel(match.id(),b,20_000));
        assertThrows(IllegalArgumentException.class,()->wagers.inspect(match.id(),c));
        assertThrows(IllegalArgumentException.class,()->wagers.acknowledge(match.id(),c));
        assertEquals(20_000,supply());assertEquals(0,draws.get());
    }
    @Test void declineAndCancelRefundExactlyOnce()throws Exception {
        var first=invite();assertEquals(WagerService.State.DECLINED,wagers.decline(first.id(),b,20_000).state());
        wagers.decline(first.id(),b,20_001);assertEquals(10_000,balance(a));
        var second=wagers.invite(a,b,500,30_000);assertEquals(WagerService.State.CANCELLED,wagers.cancel(second.id(),a,31_000).state());
        wagers.cancel(second.id(),a,31_001);assertEquals(10_000,balance(a));assertEquals(20_000,supply());assertEquals(0,draws.get());
    }
    @Test void exactExpiryBoundaryRefundsInsteadOfDrawing()throws Exception {
        var match=invite();assertEquals(WagerService.State.EXPIRED,wagers.accept(match.id(),b,match.expires()).state());
        assertEquals(10_000,balance(a));assertEquals(10_000,balance(b));assertEquals(0,draws.get());assertTrue(wagers.expireDue(match.expires()+1).isEmpty());
    }
    @Test void pendingInvitationAndRefundSurviveRestart()throws Exception {
        var match=invite();reopen();assertEquals(match,wagers.invitation(a,b).orElseThrow());assertEquals(9_000,balance(a));
        assertEquals(1,wagers.expireDue(match.expires()).size());assertEquals(10_000,balance(a));reopen();
        assertTrue(wagers.expireDue(match.expires()+1).isEmpty());assertEquals(WagerService.State.EXPIRED,wagers.inspect(match.id(),a).state());assertEquals(20_000,supply());
    }
    @Test void committedResultSurvivesRestartWithoutRedrawing()throws Exception {
        var match=wagers.accept(invite().id(),b,20_000);reopen();
        assertEquals(match,wagers.accept(match.id(),b,21_000));assertEquals(1,draws.get());assertEquals(11_000,balance(a));assertEquals(20_000,supply());
    }
    @Test void drawFailureRollsBackTheAcceptingStakeAndNoResultIsPresented()throws Exception {
        var match=invite();wagers=new WagerService(ledger,settings,()->{throw new IllegalStateException("random source unavailable");});
        assertThrows(IllegalStateException.class,()->wagers.accept(match.id(),b,20_000));
        assertEquals(WagerService.State.INVITED,wagers.inspect(match.id(),a).state());assertEquals(9_000,balance(a));assertEquals(10_000,balance(b));assertEquals(20_000,supply());
        assertEquals(1_000,held());
    }
    @Test void winnerAtTheBalanceCapGetsADurableCreditRatherThanLosingThePot()throws Exception {
        store.adjust(a,Money.MAX-10_000,"staff","cap test",1);
        var match=invite();store.adjust(a,1_000,"staff","incoming money",11_000);
        wagers.accept(match.id(),b,20_000);assertEquals(Money.MAX,balance(a));assertEquals(2_000,wagers.waiting(a));
        assertEquals(Money.MAX+11_000,supply());reopen();assertEquals(2_000,wagers.waiting(a));
        assertEquals(WagerService.State.SETTLED,wagers.accept(match.id(),b,21_000).state());assertEquals(1,draws.get());
        store.transfer(a,c,3_000,22_000);assertEquals(2_000,wagers.collect(a,23_000));assertEquals(0,wagers.collect(a,24_000));
        assertEquals(Money.MAX-1_000,balance(a));assertEquals(0,wagers.waiting(a));assertEquals(Money.MAX+11_000,supply());
    }
    @Test void refundAtTheBalanceCapRemainsSafeAndCollectable()throws Exception {
        var match=invite();store.adjust(a,Money.MAX-9_000,"staff","cap test",11_000);
        assertEquals(WagerService.State.CANCELLED,wagers.cancel(match.id(),a,20_000).state());
        assertEquals(1_000,wagers.waiting(a));assertEquals(0,wagers.collect(a,20_001));
        store.transfer(a,c,1_000,21_000);assertEquals(1_000,wagers.collect(a,22_000));assertEquals(Money.MAX,balance(a));assertEquals(0,wagers.waiting(a));
    }
    @Test void closingTheFeatureStillRecoversExistingInvitations()throws Exception {
        var match=invite();var closed=new CoinFlipSettings(false,100,10_000,90_000,5_000,5);
        wagers=new WagerService(ledger,closed,()->{fail("closed feature drew a result");return true;});
        assertThrows(IllegalArgumentException.class,()->wagers.accept(match.id(),b,20_000));
        assertThrows(IllegalArgumentException.class,()->wagers.invite(c,b,100,20_000));
        assertEquals(1,wagers.expireDue(match.expires()).size());assertEquals(10_000,balance(a));assertEquals(20_000,supply());
    }
    @Test void savedInvitationTermsAreNotChangedByNewConfigLimits()throws Exception {
        var match=invite();wagers=new WagerService(ledger,new CoinFlipSettings(true,100,500,60_000,5_000,5),()->true);
        var result=wagers.accept(match.id(),b,20_000);assertEquals(1_000,result.stake());assertEquals(2_000,result.pot());assertEquals(match.expires(),result.expires());assertEquals(match.rules(),result.rules());
    }
    @Test void creatorLimitCooldownAndIncomingLimitDoNotTakeExtraMoney()throws Exception {
        var match=invite();assertThrows(IllegalArgumentException.class,()->wagers.invite(a,c,100,11_000));assertEquals(9_000,balance(a));
        wagers.cancel(match.id(),a,11_000);assertThrows(IllegalArgumentException.class,()->wagers.invite(a,b,100,12_000));
        assertEquals(10_000,balance(a));assertTrue(wagers.outgoing(a).isEmpty());
        wagers=new WagerService(ledger,new CoinFlipSettings(true,100,10_000,90_000,5_000,1),()->true);wagers.invite(a,b,100,15_000);
        store.adjust(c,1_000,"staff","starter",15_000);assertThrows(IllegalArgumentException.class,()->wagers.invite(c,b,100,20_000));assertEquals(1_000,balance(c));
    }
    @Test void noticesArePrivateAcknowledgedAndDurable()throws Exception {
        var match=wagers.accept(invite().id(),b,20_000);assertEquals(1,wagers.notices(a).size());assertEquals(1,wagers.notices(b).size());assertTrue(wagers.notices(c).isEmpty());
        wagers.acknowledge(match.id(),a);wagers.acknowledge(match.id(),a);reopen();assertTrue(wagers.notices(a).isEmpty());assertEquals(1,wagers.notices(b).size());
        wagers.acknowledge(match.id(),b);assertTrue(wagers.notices(b).isEmpty());assertEquals(20_000,supply());
    }
    @Test void selfPlayInvalidStakesAndUnknownAccountsNeverReserveMoney()throws Exception {
        assertThrows(IllegalArgumentException.class,()->wagers.invite(a,a,100,10_000));
        for(long stake:new long[]{0,99,10_001,Long.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->wagers.invite(a,b,stake,10_000));
        assertThrows(IllegalArgumentException.class,()->wagers.invite(a,UUID.randomUUID(),100,10_000));
        assertEquals(20_000,supply());assertEquals(0,held());
    }
    @Test void aNewFlipCannotSpendMoneyAlreadyReservedElsewhere()throws Exception {
        var match=invite();store.transfer(a,c,9_000,11_000);
        assertThrows(IllegalArgumentException.class,()->wagers.invite(a,c,1_000,20_000));
        wagers.cancel(match.id(),a,21_000);assertEquals(1_000,balance(a));assertEquals(20_000,supply());
    }
    @Test void playedMilestoneRequiresASettledMatchAndCanRecoverAfterRestart()throws Exception {
        var first=invite();assertFalse(wagers.hasPlayed(a));assertFalse(wagers.hasPlayed(b));
        wagers.cancel(first.id(),a,11_000);assertFalse(wagers.hasPlayed(a));
        var second=wagers.invite(a,b,100,20_000);wagers.accept(second.id(),b,21_000);
        assertTrue(wagers.hasPlayed(a));assertTrue(wagers.hasPlayed(b));assertFalse(wagers.hasPlayed(c));
        reopen();assertTrue(wagers.hasPlayed(a));assertTrue(wagers.hasPlayed(b));
    }
}
