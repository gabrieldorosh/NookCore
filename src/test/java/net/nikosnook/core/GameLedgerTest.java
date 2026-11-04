package net.nikosnook.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class GameLedgerTest {
    @TempDir Path folder;
    NookStore store;GameLedger games;UUID a=UUID.randomUUID(),b=UUID.randomUUID();
    @BeforeEach void setup()throws Exception{store=new NookStore(folder.resolve("nooks.db"));games=new GameLedger(store);store.join(a,"Alice",1,1000);store.join(b,"Bob",1,1000);}
    @AfterEach void close()throws Exception{store.close();}
    long cash(UUID player)throws Exception{return store.account(player).orElseThrow().cents();}
    long supply()throws Exception{return Math.addExact(Math.addExact(cash(a),cash(b)),store.gameHeldFunds());}
    void reopen()throws Exception{store.close();store=new NookStore(folder.resolve("nooks.db"));games=new GameLedger(store);}
    void reservePair()throws Exception{games.transact(tx->{tx.reserve("a","match",a,200,2);tx.reserve("b","match",b,200,2);return null;});}
    @Test void equalStakesAndWinnerRedistributeTheSameMoney()throws Exception{
        reservePair();assertEquals(1600,cash(a)+cash(b));assertEquals(400,store.gameHeldFunds());assertEquals(2000,supply());
        assertTrue(games.<Boolean>transact(tx->tx.settleHolds("match",400,a,3)));
        assertEquals(1200,cash(a));assertEquals(800,cash(b));assertEquals(0,store.gameHeldFunds());assertEquals(2000,supply());
    }
    @Test void failedSecondStakeRollsBackModuleStateAndFirstDebit()throws Exception{
        games.transact(tx->{try(var s=tx.connection().createStatement()){s.execute("CREATE TABLE probe_game(id TEXT PRIMARY KEY)");}return null;});
        assertThrows(IllegalArgumentException.class,()->games.transact(tx->{
            tx.reserve("first","bad-match",a,200,2);
            try(var s=tx.connection().createStatement()){s.execute("INSERT INTO probe_game VALUES('bad-match')");}
            tx.reserve("second","bad-match",b,2000,2);return null;
        }));
        assertEquals(2000,supply());assertEquals(1000,cash(a));assertEquals(0,store.gameHeldFunds());
        assertEquals(0L,(long)games.<Long>transact(tx->{try(var s=tx.connection().createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM probe_game")){r.next();return r.getLong(1);}}));
    }
    @Test void reservationsAndSettlementStayIdempotentAcrossRestart()throws Exception{
        reservePair();assertFalse(games.<Boolean>transact(tx->tx.reserve("a","match",a,200,3)));
        assertTrue(games.<Boolean>transact(tx->tx.settleHolds("match",400,a,4)));reopen();
        assertFalse(games.<Boolean>transact(tx->tx.settleHolds("match",400,a,5)));
        assertFalse(games.<Boolean>transact(tx->tx.reserve("a","match",a,200,5)));
        assertThrows(IllegalArgumentException.class,()->games.transact(tx->tx.settleHolds("match",400,b,5)));
        assertEquals(2000,supply());assertEquals(1200,cash(a));
    }
    @Test void refundedStakesCannotPayAgainOrBecomeAPot()throws Exception{
        reservePair();assertEquals(400L,(long)games.<Long>transact(tx->tx.refundGame("match",3)));
        assertEquals(0L,(long)games.<Long>transact(tx->tx.refundGame("match",4)));
        assertFalse(games.<Boolean>transact(tx->tx.refundHold("a",4)));
        assertThrows(IllegalArgumentException.class,()->games.transact(tx->tx.settleHolds("match",400,a,5)));
        assertEquals(1000,cash(a));assertEquals(1000,cash(b));assertEquals(2000,supply());
    }
    @Test void fullBalanceRefundBecomesADurableClaimableCredit()throws Exception{
        store.adjust(a,Money.MAX-1000,"Owner","capacity fixture",2);
        games.transact(tx->tx.reserve("a","match",a,200,3));store.adjust(a,200,"Owner","incoming while reserved",4);
        assertTrue(games.<Boolean>transact(tx->tx.refundHold("a",5)));assertEquals(Money.MAX,cash(a));
        assertEquals(200L,(long)games.<Long>transact(tx->tx.pendingCredits(a)));long total=supply();reopen();
        assertEquals(200L,(long)games.<Long>transact(tx->tx.pendingCredits(a)));assertEquals(0L,(long)games.<Long>transact(tx->tx.claimCredits(a,6)));
        store.transfer(a,b,200,7);assertEquals(200L,(long)games.<Long>transact(tx->tx.claimCredits(a,8)));
        assertEquals(0L,(long)games.<Long>transact(tx->tx.pendingCredits(a)));assertEquals(total,supply());assertEquals(Money.MAX,cash(a));
    }
    @Test void cappedWinnerKeepsARecordedPotRatherThanRerollingOrLosingIt()throws Exception{
        store.adjust(a,Money.MAX-1000,"Owner","capacity fixture",2);reservePair();long total=supply();
        assertTrue(games.<Boolean>transact(tx->tx.settleHolds("match",400,a,3)));assertEquals(400L,(long)games.<Long>transact(tx->tx.pendingCredits(a)));
        assertEquals(total,supply());reopen();assertFalse(games.<Boolean>transact(tx->tx.settleHolds("match",400,a,4)));
        store.transfer(a,b,200,5);assertEquals(400L,(long)games.<Long>transact(tx->tx.claimCredits(a,6)));assertEquals(total,supply());
    }
    @Test void reservationTermsCannotBeChangedByReusingAnId()throws Exception{
        games.transact(tx->tx.reserve("a","match",a,200,2));
        assertThrows(IllegalArgumentException.class,()->games.transact(tx->tx.reserve("a","other",b,200,3)));
        assertThrows(IllegalArgumentException.class,()->games.transact(tx->tx.reserve("a","match",a,300,3)));
        assertEquals(2000,supply());assertEquals(800,cash(a));assertEquals(1000,cash(b));
    }
    @Test void awardAndGameStateCommitOrRollBackTogether()throws Exception{
        games.transact(tx->{try(var s=tx.connection().createStatement()){s.execute("CREATE TABLE probe_win(id TEXT PRIMARY KEY)");}return null;});
        assertThrows(SQLException.class,()->games.transact(tx->{tx.award(a,"wordle:day",100,"game-wordle","day",2);try(var s=tx.connection().createStatement()){s.execute("INSERT INTO probe_win VALUES('day')");}throw new SQLException("simulated disk-path failure");}));
        assertEquals(1000,cash(a));
        assertTrue(games.<Boolean>transact(tx->tx.award(a,"wordle:day",100,"game-wordle","day",3)));reopen();
        assertFalse(games.<Boolean>transact(tx->tx.award(a,"wordle:day",100,"game-wordle","day",4)));assertEquals(1100,cash(a));
    }
    @Test void aFullBalanceDoesNotConsumeAnUnpaidAwardKey()throws Exception{
        store.adjust(a,Money.MAX-1000,"Owner","capacity fixture",2);
        assertThrows(GameLedger.CapacityException.class,()->games.transact(tx->tx.award(a,"wordle:day",100,"game-wordle","day",3)));
        store.transfer(a,b,100,4);assertTrue(games.<Boolean>transact(tx->tx.award(a,"wordle:day",100,"game-wordle","day",5)));
        assertEquals(Money.MAX,cash(a));
    }
    @Test void zeroAwardsAreDurableAndTransactionsCannotEscapeTheirLifetime()throws Exception{
        assertTrue(games.<Boolean>transact(tx->tx.award(a,"wordle:free",0,"game-wordle","day",2)));
        assertFalse(games.<Boolean>transact(tx->tx.award(a,"wordle:free",100,"game-wordle","day",3)));
        GameLedger.Tx[] escaped=new GameLedger.Tx[1];games.transact(tx->{escaped[0]=tx;return null;});
        assertThrows(IllegalStateException.class,()->escaped[0].balance(a));assertEquals(1000,cash(a));
    }
    @Test void nestedGameTransactionsCannotCommitAnOuterPartialOperation()throws Exception{
        assertThrows(SQLException.class,()->games.transact(tx->{tx.debit(a,200,"game-escrow","probe",2);return games.transact(inner->inner.balance(a));}));
        assertEquals(1000,cash(a));
    }
    @Test void fatalCallbackErrorsRollBackStakesAndModuleRows()throws Exception{
        games.transact(tx->{try(var q=tx.connection().createStatement()){q.execute("CREATE TABLE error_game(id TEXT PRIMARY KEY)");}return null;});
        assertThrows(AssertionError.class,()->games.transact(tx->{tx.reserve("fatal","bad-game",a,200,2);try(var q=tx.connection().createStatement()){q.execute("INSERT INTO error_game VALUES('bad-game')");}throw new AssertionError("simulated provider failure");}));
        assertEquals(1000,cash(a));assertEquals(0,store.gameHeldFunds());
        long rows=games.<Long>transact(tx->{try(var q=tx.connection().createStatement();var r=q.executeQuery("SELECT COUNT(*) FROM error_game")){r.next();return r.getLong(1);}});assertEquals(0,rows);
    }
    @Test void nestedOrdinaryAwardsCannotCommitAnOuterGameOperation()throws Exception{
        assertThrows(SQLException.class,()->games.transact(tx->{tx.reserve("outer","nested",a,200,2);store.award(a,"wrong-nesting",100,2);return null;}));
        assertEquals(1000,cash(a));assertEquals(0,store.gameHeldFunds());
        assertTrue(store.award(a,"wrong-nesting",100,3));assertEquals(1100,cash(a));
    }
    @Test void failedRollbackClosesConnectionInsteadOfCommittingPartialMoney()throws Exception{
        var field=NookStore.class.getDeclaredField("db");field.setAccessible(true);var actual=(java.sql.Connection)field.get(store);
        var restored=new java.util.concurrent.atomic.AtomicInteger();
        var wrapped=(java.sql.Connection)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{java.sql.Connection.class},(object,method,args)->{
            if(method.getName().equals("rollback"))throw new SQLException("simulated rollback I/O failure");
            if(method.getName().equals("setAutoCommit") && Boolean.TRUE.equals(args[0]))restored.incrementAndGet();
            try{return method.invoke(actual,args);}catch(java.lang.reflect.InvocationTargetException error){throw error.getCause();}
        });
        field.set(store,wrapped);
        var failure=assertThrows(AssertionError.class,()->games.transact(tx->{tx.reserve("fatal-io","failed-rollback",a,200,2);throw new AssertionError("provider failed");}));
        assertTrue(actual.isClosed());assertEquals(0,restored.get());assertEquals(1,failure.getSuppressed().length);
        store=new NookStore(folder.resolve("nooks.db"));games=new GameLedger(store);
        assertEquals(1000,cash(a));assertEquals(0,store.gameHeldFunds());
    }
    @Test void longestSupportedIdsCanStillBeRefunded()throws Exception{
        String hold="h".repeat(200),game="g".repeat(200);
        games.transact(tx->tx.reserve(hold,game,a,200,2));assertTrue(games.<Boolean>transact(tx->tx.refundHold(hold,3)));assertEquals(2000,supply());
    }
    @Test void simultaneousSettlementRetriesCreditThePotOnce()throws Exception{
        reservePair();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            Callable<Boolean> action=()->{start.await();return games.transact(tx->tx.settleHolds("match",400,a,3));};
            var one=pool.submit(action);var two=pool.submit(action);start.countDown();
            assertNotEquals(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS));assertEquals(1200,cash(a));assertEquals(2000,supply());
        }finally{pool.shutdownNow();}
    }
}
