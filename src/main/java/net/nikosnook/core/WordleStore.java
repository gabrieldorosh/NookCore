package net.nikosnook.core;

import java.sql.*;
import java.util.*;
import java.util.function.IntUnaryOperator;

/** Puzzle attempts and their issuance share NookStore's balance transaction. */
final class WordleStore {
    enum State { ACTIVE, WON, LOST }
    record Guess(String word,List<WordleScore.Mark> marks) { Guess {marks=List.copyOf(marks);} }
    record Board(String day,long nextReset,List<Guess> guesses,State state,long reward,boolean rewardPaid,String revealedAnswer) {
        Board {guesses=List.copyOf(guesses);}
    }
    record Result(Board board,boolean consumed,long paidNow,boolean anySolved) {}
    static final class StaleBoardException extends IllegalArgumentException {StaleBoardException(){super("a fresh puzzle is here! take a little look before guessing <3");}}
    private record Puzzle(String answer,long reward) {}
    private record Round(State state,boolean paid) {}
    private final GameLedger ledger;
    private final WordleWords words;
    private final long reward;
    private final IntUnaryOperator draw;

    WordleStore(GameLedger ledger,WordleWords words,long reward,IntUnaryOperator draw)throws SQLException {
        this.ledger=Objects.requireNonNull(ledger);this.words=Objects.requireNonNull(words);this.draw=Objects.requireNonNull(draw);
        if(reward<0 || reward>Money.MAX)throw new IllegalArgumentException("Invalid daily word reward.");this.reward=reward;
        ledger.transact(tx->{try(var s=tx.connection().createStatement()){
            s.execute("CREATE TABLE IF NOT EXISTS wordle_days(day TEXT PRIMARY KEY,answer TEXT NOT NULL CHECK(length(answer)=5),reward INTEGER NOT NULL CHECK(reward BETWEEN 0 AND 100000000000),created INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS wordle_rounds(uuid TEXT NOT NULL REFERENCES accounts(uuid),day TEXT NOT NULL REFERENCES wordle_days(day),state TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(state IN ('ACTIVE','WON','LOST')),reward_paid INTEGER NOT NULL DEFAULT 0 CHECK(reward_paid IN (0,1)),PRIMARY KEY(uuid,day),CHECK(reward_paid=0 OR state='WON'))");
            s.execute("CREATE TABLE IF NOT EXISTS wordle_guesses(uuid TEXT NOT NULL,day TEXT NOT NULL,ordinal INTEGER NOT NULL CHECK(ordinal BETWEEN 1 AND 6),word TEXT NOT NULL CHECK(length(word)=5),marks TEXT NOT NULL CHECK(length(marks)=5),PRIMARY KEY(uuid,day,ordinal),UNIQUE(uuid,day,word),FOREIGN KEY(uuid,day) REFERENCES wordle_rounds(uuid,day))");
            s.execute("CREATE TABLE IF NOT EXISTS wordle_views(uuid TEXT PRIMARY KEY REFERENCES accounts(uuid),day TEXT NOT NULL REFERENCES wordle_days(day))");
        }return null;});
    }
    Result open(UUID player,long now)throws SQLException {return operate(player,null,null,now,true);}
    Result forShare(UUID player,long now)throws SQLException {return operate(player,null,null,now,false);}
    Result submit(UUID player,String shownDay,String word,long now)throws SQLException {return operate(player,shownDay,WordleScore.normalize(word),now,true);}
    private Result operate(UUID player,String shownDay,String word,long now,boolean rememberView)throws SQLException {
        return ledger.transact(tx->{
            tx.balance(player);var day=WordleDay.at(now);var db=tx.connection();
            if(word!=null){String expected=shownDay==null?lastShown(db,player):shownDay;if(!day.id().equals(expected))throw new StaleBoardException();}
            var puzzle=puzzle(db,day.id(),now);ensureRound(db,player,day.id());
            var round=round(db,player,day.id());var guesses=guesses(db,player,day.id());boolean consumed=false;
            validateSaved(round,guesses,puzzle);
            if(word!=null && round.state()==State.ACTIVE){
                if(!word.equals(puzzle.answer()) && !words.accepts(word))throw new IllegalArgumentException("that word isn't in our dictionary! try again");
                if(guesses.stream().anyMatch(guess->guess.word().equals(word)))throw new IllegalArgumentException("you've tried that one already!");
                int ordinal=guesses.size()+1;var marks=WordleScore.score(puzzle.answer(),word);
                update(db,"INSERT INTO wordle_guesses(uuid,day,ordinal,word,marks) VALUES(?,?,?,?,?)",player,day.id(),ordinal,word,WordleScore.encode(marks));
                State state=word.equals(puzzle.answer())?State.WON:ordinal==6?State.LOST:State.ACTIVE;
                update(db,"UPDATE wordle_rounds SET state=? WHERE uuid=? AND day=?",state.name(),player,day.id());consumed=true;
            }
            long paid=claimPending(tx,player,now);
            if(rememberView)update(db,"INSERT INTO wordle_views(uuid,day) VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET day=excluded.day",player,day.id());
            round=round(db,player,day.id());guesses=guesses(db,player,day.id());
            var board=new Board(day.id(),day.nextReset(),guesses,round.state(),puzzle.reward(),round.paid(),round.state()==State.ACTIVE?null:puzzle.answer());
            boolean anySolved;try(var q=prepare(db,"SELECT 1 FROM wordle_rounds WHERE uuid=? AND state='WON' LIMIT 1",player);var r=q.executeQuery()){anySolved=r.next();}
            return new Result(board,consumed,paid,anySolved);
        });
    }
    static long claimPending(GameLedger.Tx tx,UUID player,long now)throws SQLException {
        if(!hasSavedRounds(tx.connection()))return 0;
        var db=tx.connection();var pending=new ArrayList<Map.Entry<String,Long>>();
        try(var q=prepare(db,"SELECT r.day,d.reward FROM wordle_rounds r JOIN wordle_days d ON d.day=r.day WHERE r.uuid=? AND r.state='WON' AND r.reward_paid=0 ORDER BY r.day",player);var r=q.executeQuery()){
            while(r.next())pending.add(Map.entry(r.getString(1),r.getLong(2)));
        }
        long paid=0;
        for(var entry:pending){
            long amount=entry.getValue();if(amount>0 && !tx.canCredit(player,amount))continue;
            boolean fresh=amount>0 && tx.award(player,"wordle:"+entry.getKey(),amount,"game-wordle","wordle:"+entry.getKey(),now);
            update(db,"UPDATE wordle_rounds SET reward_paid=1 WHERE uuid=? AND day=?",player,entry.getKey());
            if(fresh)paid=Math.addExact(paid,amount);
        }
        return paid;
    }
    static long pendingRewards(GameLedger.Tx tx,UUID player)throws SQLException {
        var db=tx.connection();if(!hasSavedRounds(db))return 0;
        try(var q=prepare(db,"SELECT COALESCE(SUM(d.reward),0) FROM wordle_rounds r JOIN wordle_days d ON d.day=r.day WHERE r.uuid=? AND r.state='WON' AND r.reward_paid=0",player);var rows=q.executeQuery()){return rows.next()?rows.getLong(1):0;}
    }
    private static boolean hasSavedRounds(Connection db)throws SQLException {
        try(var q=prepare(db,"SELECT 1 FROM sqlite_master WHERE type='table' AND name='wordle_rounds'");var rows=q.executeQuery()){return rows.next();}
    }
    private Puzzle puzzle(Connection db,String day,long now)throws SQLException {
        try(var q=prepare(db,"SELECT answer,reward FROM wordle_days WHERE day=?",day);var r=q.executeQuery()){if(r.next())return new Puzzle(r.getString(1),r.getLong(2));}
        var recent=new ArrayList<String>();try(var q=prepare(db,"SELECT answer FROM wordle_days WHERE day<? ORDER BY day DESC LIMIT 30",day);var r=q.executeQuery()){while(r.next())recent.add(r.getString(1));}
        var pool=words.answers().stream().filter(answer->!recent.contains(answer)).toList();
        if(pool.isEmpty()){String last=recent.isEmpty()?null:recent.getFirst();pool=words.answers().stream().filter(answer->!answer.equals(last)).toList();}
        if(pool.isEmpty())pool=words.answers();int chosen=draw.applyAsInt(pool.size());if(chosen<0 || chosen>=pool.size())throw new IllegalArgumentException("Invalid puzzle draw.");
        String answer=pool.get(chosen);update(db,"INSERT INTO wordle_days(day,answer,reward,created) VALUES(?,?,?,?)",day,answer,reward,now);return new Puzzle(answer,reward);
    }
    private static void ensureRound(Connection db,UUID player,String day)throws SQLException {update(db,"INSERT OR IGNORE INTO wordle_rounds(uuid,day) VALUES(?,?)",player,day);}
    private static String lastShown(Connection db,UUID player)throws SQLException {try(var q=prepare(db,"SELECT day FROM wordle_views WHERE uuid=?",player);var r=q.executeQuery()){return r.next()?r.getString(1):null;}}
    private static Round round(Connection db,UUID player,String day)throws SQLException {try(var q=prepare(db,"SELECT state,reward_paid FROM wordle_rounds WHERE uuid=? AND day=?",player,day);var r=q.executeQuery()){if(!r.next())throw new SQLException("Missing word round.");return new Round(State.valueOf(r.getString(1)),r.getInt(2)==1);}}
    private static List<Guess> guesses(Connection db,UUID player,String day)throws SQLException {
        var out=new ArrayList<Guess>();try(var q=prepare(db,"SELECT word,marks FROM wordle_guesses WHERE uuid=? AND day=? ORDER BY ordinal",player,day);var r=q.executeQuery()){while(r.next())out.add(new Guess(r.getString(1),WordleScore.decode(r.getString(2))));}return List.copyOf(out);
    }
    private static void validateSaved(Round round,List<Guess> guesses,Puzzle puzzle)throws SQLException {
        boolean solved=!guesses.isEmpty() && guesses.getLast().word().equals(puzzle.answer());
        if(guesses.size()>6 || round.state()==State.ACTIVE && (guesses.size()==6 || solved) || round.state()==State.WON && !solved || round.state()==State.LOST && (guesses.size()!=6 || solved))
            throw new SQLException("Invalid saved word-game outcome; review before changing attempts.");
    }
    private static PreparedStatement prepare(Connection db,String sql,Object...args)throws SQLException {var q=db.prepareStatement(sql);for(int i=0;i<args.length;i++)q.setObject(i+1,args[i] instanceof UUID id?id.toString():args[i]);return q;}
    private static int update(Connection db,String sql,Object...args)throws SQLException {try(var q=prepare(db,sql,args)){return q.executeUpdate();}}
}
