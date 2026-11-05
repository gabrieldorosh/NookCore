package net.nikosnook.core;

import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Player-funded matches. Participants consent; only the trusted game decides a result. */
final class WagerService {
    static final String RULES="coinflip-v1|equal-stakes|50/50|full-pot|no-fee";
    private static final String CREATOR_TAILS=RULES+"|creator-side=tails";
    enum State { INVITED, SETTLED, DECLINED, CANCELLED, EXPIRED }
    enum Side {
        HEADS, TAILS;
        String label(){return name().toLowerCase(Locale.ROOT);}
    }
    record Match(UUID id,UUID creator,UUID target,long stake,String rules,Side creatorSide,long created,long expires,
                 State state,Side side,UUID winner,long finished) {
        long pot(){return Math.multiplyExact(stake,2);}
        boolean participant(UUID player){return creator.equals(player)||target.equals(player);}
        String gameId(){return "coinflip:"+id;}
    }
    private final GameLedger ledger;
    private final CoinFlipSettings settings;
    private final BooleanSupplier heads;

    WagerService(GameLedger ledger,CoinFlipSettings settings)throws SQLException {this(ledger,settings,new SecureRandom()::nextBoolean);}
    WagerService(GameLedger ledger,CoinFlipSettings settings,BooleanSupplier heads)throws SQLException {
        this.ledger=Objects.requireNonNull(ledger);this.settings=Objects.requireNonNull(settings);this.heads=Objects.requireNonNull(heads);
        ledger.transact(tx->{
            try(var sql=tx.connection().createStatement()){
                sql.execute("CREATE TABLE IF NOT EXISTS game_wagers (id TEXT PRIMARY KEY,creator TEXT NOT NULL REFERENCES accounts(uuid),target TEXT NOT NULL REFERENCES accounts(uuid),stake INTEGER NOT NULL CHECK(stake>0),rules TEXT NOT NULL,created INTEGER NOT NULL,expires INTEGER NOT NULL,state TEXT NOT NULL CHECK(state IN ('INVITED','SETTLED','DECLINED','CANCELLED','EXPIRED')),side TEXT CHECK(side IN ('HEADS','TAILS')),winner TEXT REFERENCES accounts(uuid),finished INTEGER NOT NULL DEFAULT 0,creator_notified INTEGER NOT NULL DEFAULT 0,target_notified INTEGER NOT NULL DEFAULT 0,CHECK(creator<>target))");
                sql.execute("CREATE UNIQUE INDEX IF NOT EXISTS game_wagers_open_creator ON game_wagers(creator) WHERE state='INVITED'");
                sql.execute("CREATE INDEX IF NOT EXISTS game_wagers_target ON game_wagers(target,state)");
            }
            return null;
        });
    }

    Match invite(UUID creator,UUID target,long stake,long now)throws SQLException {
        return invite(creator,target,stake,Side.HEADS,now);
    }

    Match invite(UUID creator,UUID target,long stake,Side creatorSide,long now)throws SQLException {
        Objects.requireNonNull(creator);Objects.requireNonNull(target);Objects.requireNonNull(creatorSide);validateTime(now);
        if(!settings.enabled())throw new IllegalArgumentException("coin flips aren't open just yet.");
        if(creator.equals(target))throw new IllegalArgumentException("pick another player to flip with!");
        if(stake<settings.minimum() || stake>settings.maximum())throw new IllegalArgumentException("choose a stake from "+Money.format(settings.minimum())+" to "+Money.format(settings.maximum())+".");
        expireDue(now);
        return ledger.transact(tx->{
            tx.balance(creator);tx.balance(target);
            if(outgoing(tx.connection(),creator).isPresent())throw new IllegalArgumentException("you already have a coin flip waiting! use /coinflip cancel first.");
            try(var query=tx.connection().prepareStatement("SELECT MAX(created) FROM game_wagers WHERE creator=?")){
                query.setString(1,creator.toString());try(var rows=query.executeQuery()){
                    if(rows.next()){long previous=rows.getLong(1);if(!rows.wasNull() && now-previous<settings.cooldownMillis())throw new IllegalArgumentException("give the coin a moment! try again in a few seconds.");}
                }
            }
            try(var query=tx.connection().prepareStatement("SELECT COUNT(*) FROM game_wagers WHERE target=? AND state='INVITED'")){
                query.setString(1,target.toString());try(var rows=query.executeQuery()){rows.next();if(rows.getInt(1)>=settings.incomingLimit())throw new IllegalArgumentException("that player has a few invitations waiting. try again shortly!");}
            }
            UUID id=UUID.randomUUID();String game="coinflip:"+id;long expires=Math.addExact(now,settings.invitationMillis());
            try(var insert=tx.connection().prepareStatement("INSERT INTO game_wagers(id,creator,target,stake,rules,created,expires,state) VALUES(?,?,?,?,?,?,?,'INVITED')")){
                bind(insert,id,creator,target,stake,creatorSide==Side.HEADS?RULES:CREATOR_TAILS,now,expires);insert.executeUpdate();
            }
            if(!tx.reserve(game+":creator",game,creator,stake,now))throw new SQLException("The new invitation already has a stake.");
            return required(tx.connection(),id);
        });
    }

    Match accept(UUID id,UUID actor,long now)throws SQLException {
        Objects.requireNonNull(id);Objects.requireNonNull(actor);validateTime(now);
        if(!settings.enabled())throw new IllegalArgumentException("coin flips are paused. you can still cancel or decline.");
        return ledger.transact(tx->{
            var match=required(tx.connection(),id);
            if(!match.target().equals(actor))throw new IllegalArgumentException("that invitation is for another player.");
            if(match.state()!=State.INVITED)return match;
            if(now>=match.expires())return close(tx,match,State.EXPIRED,now);
            String game=match.gameId();
            if(!tx.reserve(game+":target",game,actor,match.stake(),now))throw new SQLException("The accepting stake already exists.");
            Side side=heads.getAsBoolean()?Side.HEADS:Side.TAILS;
            UUID winner=side==match.creatorSide()?match.creator():match.target();
            // The outcome, both stakes and the payout entitlement commit before any UI.
            if(!tx.settleHolds(game,match.pot(),winner,now))throw new SQLException("This match was already settled without its result.");
            try(var update=tx.connection().prepareStatement("UPDATE game_wagers SET state='SETTLED',side=?,winner=?,finished=? WHERE id=? AND state='INVITED'")){
                bind(update,side.name(),winner,now,id);if(update.executeUpdate()!=1)throw new SQLException("The match changed during settlement.");
            }
            return required(tx.connection(),id);
        });
    }

    Match decline(UUID id,UUID actor,long now)throws SQLException {return closeByPlayer(id,actor,false,now);}
    Match cancel(UUID id,UUID actor,long now)throws SQLException {return closeByPlayer(id,actor,true,now);}
    private Match closeByPlayer(UUID id,UUID actor,boolean creator,long now)throws SQLException {
        Objects.requireNonNull(id);Objects.requireNonNull(actor);validateTime(now);
        return ledger.transact(tx->{
            var match=required(tx.connection(),id);
            if(!(creator?match.creator():match.target()).equals(actor))throw new IllegalArgumentException(creator?"only the player who invited can cancel this flip.":"that invitation is for another player.");
            if(match.state()!=State.INVITED)return match;
            return close(tx,match,now>=match.expires()?State.EXPIRED:creator?State.CANCELLED:State.DECLINED,now);
        });
    }
    private Match close(GameLedger.Tx tx,Match match,State state,long now)throws SQLException {
        if(tx.refundGame(match.gameId(),now)!=match.stake())throw new SQLException("The saved invitation stake does not match its refund.");
        try(var update=tx.connection().prepareStatement("UPDATE game_wagers SET state=?,finished=? WHERE id=? AND state='INVITED'")){
            bind(update,state.name(),now,match.id());if(update.executeUpdate()!=1)throw new SQLException("The invitation changed during its refund.");
        }
        return required(tx.connection(),match.id());
    }
    List<Match> expireDue(long now)throws SQLException {
        validateTime(now);
        return ledger.transact(tx->{
            List<UUID> ids=new ArrayList<>();
            try(var query=tx.connection().prepareStatement("SELECT id FROM game_wagers WHERE state='INVITED' AND expires<=? ORDER BY expires,id LIMIT 100")){
                query.setLong(1,now);try(var rows=query.executeQuery()){while(rows.next())ids.add(UUID.fromString(rows.getString(1)));}
            }
            List<Match> expired=new ArrayList<>();for(var id:ids)expired.add(close(tx,required(tx.connection(),id),State.EXPIRED,now));return List.copyOf(expired);
        });
    }
    Optional<Match> outgoing(UUID player)throws SQLException {return ledger.transact(tx->outgoing(tx.connection(),player));}
    private Optional<Match> outgoing(Connection db,UUID player)throws SQLException {
        try(var query=db.prepareStatement("SELECT * FROM game_wagers WHERE creator=? AND state='INVITED'")){
            query.setString(1,player.toString());try(var rows=query.executeQuery()){return rows.next()?Optional.of(read(rows)):Optional.empty();}
        }
    }
    Optional<Match> invitation(UUID creator,UUID target)throws SQLException {
        return ledger.transact(tx->{
            try(var query=tx.connection().prepareStatement("SELECT * FROM game_wagers WHERE creator=? AND target=? AND state='INVITED'")){
                bind(query,creator,target);try(var rows=query.executeQuery()){return rows.next()?Optional.of(read(rows)):Optional.empty();}
            }
        });
    }
    List<Match> incoming(UUID player)throws SQLException {
        return ledger.transact(tx->queryPlayer(tx.connection(),"SELECT * FROM game_wagers WHERE target=? AND state='INVITED' ORDER BY created,id LIMIT 10",player));
    }
    List<Match> notices(UUID player)throws SQLException {
        return ledger.transact(tx->{
            try(var query=tx.connection().prepareStatement("SELECT * FROM game_wagers WHERE state<>'INVITED' AND ((creator=? AND creator_notified=0) OR (target=? AND target_notified=0)) ORDER BY finished,id LIMIT 20")){
                bind(query,player,player);try(var rows=query.executeQuery()){List<Match> matches=new ArrayList<>();while(rows.next())matches.add(read(rows));return List.copyOf(matches);}
            }
        });
    }
    void acknowledge(UUID id,UUID player)throws SQLException {
        ledger.transact(tx->{
            var match=required(tx.connection(),id);if(!match.participant(player))throw new IllegalArgumentException("that flip belongs to another player.");
            if(match.state()==State.INVITED)return null;
            String column=match.creator().equals(player)?"creator_notified":"target_notified";
            try(var update=tx.connection().prepareStatement("UPDATE game_wagers SET "+column+"=1 WHERE id=?")){update.setString(1,id.toString());update.executeUpdate();}return null;
        });
    }
    List<Match> history(UUID player)throws SQLException {
        return ledger.transact(tx->{
            try(var query=tx.connection().prepareStatement("SELECT * FROM game_wagers WHERE creator=? OR target=? ORDER BY created DESC,id DESC LIMIT 5")){
                bind(query,player,player);try(var rows=query.executeQuery()){List<Match> matches=new ArrayList<>();while(rows.next())matches.add(read(rows));return List.copyOf(matches);}
            }
        });
    }
    Match inspect(UUID id,UUID player)throws SQLException {
        return ledger.transact(tx->{var match=required(tx.connection(),id);if(!match.participant(player))throw new IllegalArgumentException("that flip belongs to another player.");return match;});
    }
    boolean hasPlayed(UUID player)throws SQLException {
        return ledger.transact(tx->{
            try(var query=tx.connection().prepareStatement("SELECT 1 FROM game_wagers WHERE state='SETTLED' AND (creator=? OR target=?) LIMIT 1")){
                bind(query,player,player);try(var rows=query.executeQuery()){return rows.next();}
            }
        });
    }
    long collect(UUID player,long now)throws SQLException {validateTime(now);return ledger.transact(tx->tx.claimCredits(player,now));}
    long waiting(UUID player)throws SQLException {return ledger.transact(tx->tx.pendingCredits(player));}
    private static List<Match> queryPlayer(Connection db,String sql,UUID player)throws SQLException {
        try(var query=db.prepareStatement(sql)){query.setString(1,player.toString());try(var rows=query.executeQuery()){List<Match> matches=new ArrayList<>();while(rows.next())matches.add(read(rows));return List.copyOf(matches);}}
    }
    private static Match required(Connection db,UUID id)throws SQLException {
        try(var query=db.prepareStatement("SELECT * FROM game_wagers WHERE id=?")){query.setString(1,id.toString());try(var rows=query.executeQuery()){if(!rows.next())throw new IllegalArgumentException("that coin flip is no longer available.");return read(rows);}}
    }
    private static Match read(ResultSet rows)throws SQLException {
        String side=rows.getString("side"),winner=rows.getString("winner"),rules=rows.getString("rules");
        // Old invitations froze the creator as heads without a side suffix.
        Side creatorSide=switch(rules){
            case RULES->Side.HEADS;
            case CREATOR_TAILS->Side.TAILS;
            default->throw new SQLException("Unknown saved coin-flip rules.");
        };
        return new Match(UUID.fromString(rows.getString("id")),UUID.fromString(rows.getString("creator")),UUID.fromString(rows.getString("target")),rows.getLong("stake"),rules,creatorSide,rows.getLong("created"),rows.getLong("expires"),State.valueOf(rows.getString("state")),side==null?null:Side.valueOf(side),winner==null?null:UUID.fromString(winner),rows.getLong("finished"));
    }
    private void validateTime(long now){if(now<0 || now>Long.MAX_VALUE-settings.invitationMillis())throw new IllegalArgumentException("Invalid game time.");}
    private static void bind(PreparedStatement statement,Object...values)throws SQLException {for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i] instanceof UUID id?id.toString():values[i]);}
}
