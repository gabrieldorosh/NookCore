package net.nikosnook.core;

import java.sql.*;
import java.util.*;

/** Game state and money share NookStore's serialized, durable SQLite transaction. */
final class GameLedger {
    @FunctionalInterface interface Work<T> { T run(Tx tx)throws SQLException; }
    static final class CapacityException extends IllegalArgumentException {
        CapacityException(){super("make a little room in your balance first; your reward is still safe.");}
    }
    private final NookStore store;
    GameLedger(NookStore store){this.store=Objects.requireNonNull(store);}
    <T>T transact(Work<T> work)throws SQLException{return store.gameTransaction(work);}

    static void install(Statement statement)throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS game_holds(id TEXT PRIMARY KEY,game_id TEXT NOT NULL,owner TEXT NOT NULL REFERENCES accounts(uuid),cents INTEGER NOT NULL CHECK(cents>0 AND cents<=100000000000),state TEXT NOT NULL CHECK(state IN ('HELD','RELEASED','PAID')),created INTEGER NOT NULL,closed INTEGER NOT NULL DEFAULT 0)");
        statement.execute("CREATE INDEX IF NOT EXISTS game_holds_game ON game_holds(game_id,state)");
        statement.execute("CREATE TABLE IF NOT EXISTS game_credits(id TEXT PRIMARY KEY,game_id TEXT NOT NULL,owner TEXT NOT NULL REFERENCES accounts(uuid),cents INTEGER NOT NULL CHECK(cents>0 AND cents<=100000000000),kind TEXT NOT NULL CHECK(kind IN ('game-payout','game-refund')),state TEXT NOT NULL CHECK(state IN ('READY','CLAIMED')),created INTEGER NOT NULL,claimed INTEGER NOT NULL DEFAULT 0)");
        statement.execute("CREATE INDEX IF NOT EXISTS game_credits_owner ON game_credits(owner,state)");
    }

    static final class Tx {
        private final Connection db;
        private boolean active=true;
        Tx(Connection db){this.db=db;}
        void close(){active=false;}
        private void check(){if(!active)throw new IllegalStateException("The game transaction is closed.");}
        Connection connection(){check();return db;}
        private static void amount(long cents,boolean zero){if(cents<(zero?0:1)||cents>Money.MAX)throw new IllegalArgumentException("Choose a valid Nooks amount.");}
        private static void id(String id){if(id==null||id.isBlank()||id.length()>200)throw new IllegalArgumentException("Invalid game record ID.");}
        private static void audit(String kind,String reference){if(kind==null||!kind.matches("game-[a-z0-9-]{1,40}")||reference==null||reference.isBlank()||reference.length()>300)throw new IllegalArgumentException("Invalid game audit record.");}
        private int update(String sql,Object...args)throws SQLException {
            check();try(var statement=db.prepareStatement(sql)){bind(statement,args);return statement.executeUpdate();}
        }
        private static void bind(PreparedStatement statement,Object...args)throws SQLException {for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i] instanceof UUID?args[i].toString():args[i]);}
        long balance(UUID player)throws SQLException {
            check();Objects.requireNonNull(player);
            try(var statement=db.prepareStatement("SELECT cents FROM accounts WHERE uuid=?")){bind(statement,player);try(var rows=statement.executeQuery()){if(!rows.next())throw new IllegalArgumentException("That player's Nooks account is not ready yet.");return rows.getLong(1);}}
        }
        boolean canCredit(UUID player,long cents)throws SQLException {amount(cents,true);return balance(player)<=Money.MAX-cents;}
        void debit(UUID player,long cents,String kind,String reference,long now)throws SQLException {
            check();amount(cents,false);audit(kind,reference);balance(player);
            if(update("UPDATE accounts SET cents=cents-? WHERE uuid=? AND cents>=?",cents,player,cents)!=1)throw new IllegalArgumentException("you don't have enough Nooks for that.");
            update("INSERT INTO ledger(time,uuid,delta,kind,reference) VALUES(?,?,?,?,?)",now,player,-cents,kind,reference);
        }
        void credit(UUID player,long cents,String kind,String reference,long now)throws SQLException {
            check();amount(cents,false);audit(kind,reference);
            if(!canCredit(player,cents))throw new CapacityException();
            if(update("UPDATE accounts SET cents=cents+? WHERE uuid=? AND cents<=?",cents,player,Money.MAX-cents)!=1)throw new CapacityException();
            update("INSERT INTO ledger(time,uuid,delta,kind,reference) VALUES(?,?,?,?,?)",now,player,cents,kind,reference);
        }
        boolean award(UUID player,String key,long cents,String kind,String reference,long now)throws SQLException {
            check();id(key);amount(cents,true);audit(kind,reference);balance(player);
            try(var statement=db.prepareStatement("SELECT 1 FROM awards WHERE uuid=? AND key=?")){bind(statement,player,key);try(var rows=statement.executeQuery()){if(rows.next())return false;}}
            if(!canCredit(player,cents))throw new CapacityException();
            if(update("INSERT OR IGNORE INTO awards(uuid,key) VALUES(?,?)",player,key)!=1)return false;
            if(cents>0)credit(player,cents,kind,reference,now);
            return true;
        }
        boolean reserve(String holdId,String gameId,UUID owner,long cents,long now)throws SQLException {
            check();id(holdId);id(gameId);amount(cents,false);
            try(var statement=db.prepareStatement("SELECT game_id,owner,cents FROM game_holds WHERE id=?")){bind(statement,holdId);try(var rows=statement.executeQuery()){
                if(rows.next()){
                    if(!rows.getString(1).equals(gameId)||!rows.getString(2).equals(owner.toString())||rows.getLong(3)!=cents)throw new IllegalArgumentException("That game reservation has different saved terms.");
                    return false;
                }
            }}
            debit(owner,cents,"game-escrow",holdId,now);
            update("INSERT INTO game_holds(id,game_id,owner,cents,state,created) VALUES(?,?,?,?,'HELD',?)",holdId,gameId,owner,cents,now);
            return true;
        }
        private record Hold(String id,String game,UUID owner,long cents) {}
        private record Credit(String id,String game,UUID owner,long cents,String kind) {}
        private boolean createCredit(String creditId,String gameId,UUID owner,long cents,String kind,long now)throws SQLException {
            if(creditId==null||creditId.isBlank()||creditId.length()>240)throw new IllegalArgumentException("Invalid game credit ID.");
            id(gameId);amount(cents,false);audit(kind,creditId);
            try(var statement=db.prepareStatement("SELECT game_id,owner,cents,kind FROM game_credits WHERE id=?")){bind(statement,creditId);try(var rows=statement.executeQuery()){
                if(rows.next()){
                    if(!rows.getString(1).equals(gameId)||!rows.getString(2).equals(owner.toString())||rows.getLong(3)!=cents||!rows.getString(4).equals(kind))throw new IllegalArgumentException("That game result has different saved terms.");
                    return false;
                }
            }}
            update("INSERT INTO game_credits(id,game_id,owner,cents,kind,state,created) VALUES(?,?,?,?,?,'READY',?)",creditId,gameId,owner,cents,kind,now);
            return true;
        }
        boolean refundHold(String holdId,long now)throws SQLException {
            check();id(holdId);Hold hold;
            try(var statement=db.prepareStatement("SELECT game_id,owner,cents,state FROM game_holds WHERE id=?")){bind(statement,holdId);try(var rows=statement.executeQuery()){
                if(!rows.next())throw new IllegalArgumentException("Unknown game reservation.");
                if(!rows.getString(4).equals("HELD"))return false;
                hold=new Hold(holdId,rows.getString(1),UUID.fromString(rows.getString(2)),rows.getLong(3));
            }}
            update("UPDATE game_holds SET state='RELEASED',closed=? WHERE id=? AND state='HELD'",now,holdId);
            createCredit("refund:"+hold.id(),hold.game(),hold.owner(),hold.cents(),"game-refund",now);
            claimCredits(hold.owner(),now);
            return true;
        }
        long refundGame(String gameId,long now)throws SQLException {
            check();id(gameId);var held=new ArrayList<Hold>();
            try(var statement=db.prepareStatement("SELECT id,owner,cents FROM game_holds WHERE game_id=? AND state='HELD' ORDER BY id")){bind(statement,gameId);try(var rows=statement.executeQuery()){while(rows.next())held.add(new Hold(rows.getString(1),gameId,UUID.fromString(rows.getString(2)),rows.getLong(3)));}}
            long total=0;for(var hold:held)if(refundHold(hold.id(),now))total=Math.addExact(total,hold.cents());return total;
        }
        boolean settleHolds(String gameId,long expectedTotal,UUID winner,long now)throws SQLException {
            check();id(gameId);amount(expectedTotal,false);balance(winner);
            try(var statement=db.prepareStatement("SELECT owner,cents FROM game_credits WHERE id=?")){bind(statement,"payout:"+gameId);try(var rows=statement.executeQuery()){
                if(rows.next()){
                    if(!rows.getString(1).equals(winner.toString())||rows.getLong(2)!=expectedTotal)throw new IllegalArgumentException("The game's winner and pot are already saved.");
                    return false;
                }
            }}
            long held;
            try(var statement=db.prepareStatement("SELECT COALESCE(SUM(cents),0) FROM game_holds WHERE game_id=? AND state='HELD'")){bind(statement,gameId);try(var rows=statement.executeQuery()){rows.next();held=rows.getLong(1);}}
            if(held!=expectedTotal)throw new IllegalArgumentException("The saved game stakes do not match its pot.");
            update("UPDATE game_holds SET state='PAID',closed=? WHERE game_id=? AND state='HELD'",now,gameId);
            createCredit("payout:"+gameId,gameId,winner,expectedTotal,"game-payout",now);
            claimCredits(winner,now);
            return true;
        }
        long claimCredits(UUID player,long now)throws SQLException {
            check();balance(player);var credits=new ArrayList<Credit>();
            try(var statement=db.prepareStatement("SELECT id,game_id,cents,kind FROM game_credits WHERE owner=? AND state='READY' ORDER BY created,id")){bind(statement,player);try(var rows=statement.executeQuery()){while(rows.next())credits.add(new Credit(rows.getString(1),rows.getString(2),player,rows.getLong(3),rows.getString(4)));}}
            long paid=0;
            for(var pending:credits){
                if(!canCredit(player,pending.cents()))continue;
                if(update("UPDATE game_credits SET state='CLAIMED',claimed=? WHERE id=? AND state='READY'",now,pending.id())!=1)throw new SQLException("The pending game credit changed during settlement.");
                credit(player,pending.cents(),pending.kind(),pending.id(),now);paid=Math.addExact(paid,pending.cents());
            }
            return paid;
        }
        long pendingCredits(UUID player)throws SQLException {
            check();try(var statement=db.prepareStatement("SELECT COALESCE(SUM(cents),0) FROM game_credits WHERE owner=? AND state='READY'")){bind(statement,player);try(var rows=statement.executeQuery()){rows.next();return rows.getLong(1);}}
        }
        long heldTotal()throws SQLException {
            check();try(var statement=db.createStatement();var rows=statement.executeQuery("SELECT (SELECT COALESCE(SUM(cents),0) FROM game_holds WHERE state='HELD')+(SELECT COALESCE(SUM(cents),0) FROM game_credits WHERE state='READY')")){rows.next();return rows.getLong(1);}
        }
    }
}
