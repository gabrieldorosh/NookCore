package net.nikosnook.core;



import java.nio.file.Path;

import java.sql.*;

import java.time.Duration;

import java.util.*;



/** Serialized SQLite transactions; every balance mutation and its audit entry commit together. */

public final class NookStore implements AutoCloseable {

    public static final long WEEK = Duration.ofDays(7).toMillis();

    private final Connection db;
    private final Set<UUID> onlineAccounts=new HashSet<>();
    public record IncomeSender(UUID player,String name,long cents) {}
    public record IncomeReport(long through,long payments,long sales,List<IncomeSender> senders) {}
    public record Account(UUID id, String name, long cents, long seen) {}

    public record Plot(String id, long weekly, UUID owner, long paidUntil, String state) {}

    public record Entry(long id, long time, long delta, String kind, String reference) {}

    public record Invitation(UUID token, String plot, UUID inviter, UUID member, String role, long expires) {}

    public record AbandonmentQuote(String plot, UUID owner, UUID lease, long paidUntil, long weekly, String state, long refund) {}
    public static final long INVITATION_LIFETIME = Duration.ofDays(1).toMillis();

    @FunctionalInterface private interface Work<T> { T run() throws SQLException; }



    public NookStore(Path file) throws SQLException {

        db = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());

        try (Statement s = db.createStatement()) {

            s.execute("PRAGMA foreign_keys=ON"); s.execute("PRAGMA journal_mode=WAL"); s.execute("PRAGMA synchronous=FULL"); s.execute("PRAGMA busy_timeout=5000");

            s.execute("CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)");

            s.execute("INSERT OR IGNORE INTO metadata VALUES ('schema','1')");

            try (ResultSet r = s.executeQuery("SELECT value FROM metadata WHERE key='schema'")) {

                if (!r.next() || !r.getString(1).equals("1")) throw new SQLException("Unsupported NookCore schema; refusing to open.");

            }

            s.execute("CREATE TABLE IF NOT EXISTS accounts (uuid TEXT PRIMARY KEY, name TEXT NOT NULL, cents INTEGER NOT NULL DEFAULT 0 CHECK(cents>=0 AND cents<=100000000000), seen INTEGER NOT NULL)");

            s.execute("CREATE TABLE IF NOT EXISTS awards (uuid TEXT NOT NULL REFERENCES accounts(uuid), key TEXT NOT NULL, PRIMARY KEY(uuid,key))");

            s.execute("CREATE TABLE IF NOT EXISTS ledger (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, uuid TEXT NOT NULL REFERENCES accounts(uuid), delta INTEGER NOT NULL, kind TEXT NOT NULL, reference TEXT NOT NULL)");

            s.execute("CREATE INDEX IF NOT EXISTS ledger_account ON ledger(uuid,id)");
            s.execute("CREATE TABLE IF NOT EXISTS offline_income (ledger_id INTEGER PRIMARY KEY REFERENCES ledger(id))");
            s.execute("CREATE TABLE IF NOT EXISTS bunny_time (uuid TEXT PRIMARY KEY REFERENCES accounts(uuid), seconds INTEGER NOT NULL CHECK(seconds BETWEEN 0 AND 360000))");
            s.execute("CREATE TABLE IF NOT EXISTS bunny_grants (id INTEGER PRIMARY KEY, uuid TEXT NOT NULL, seconds INTEGER NOT NULL, actor TEXT NOT NULL, time INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS income_sharing (lease TEXT PRIMARY KEY, enabled INTEGER NOT NULL CHECK(enabled IN (0,1)))");
            s.execute("CREATE TABLE IF NOT EXISTS plots (id TEXT PRIMARY KEY, weekly INTEGER NOT NULL CHECK(weekly>0), owner TEXT REFERENCES accounts(uuid), paid_until INTEGER NOT NULL DEFAULT 0, state TEXT NOT NULL DEFAULT 'AVAILABLE' CHECK(state IN ('AVAILABLE','ACTIVE','GRACE','RECLAIM')))");

            s.execute("CREATE TABLE IF NOT EXISTS members (uuid TEXT PRIMARY KEY REFERENCES accounts(uuid), plot TEXT NOT NULL REFERENCES plots(id), role TEXT NOT NULL)");

            s.execute("CREATE TABLE IF NOT EXISTS plot_events (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, plot TEXT NOT NULL, kind TEXT NOT NULL, detail TEXT NOT NULL)");

            s.execute("CREATE TABLE IF NOT EXISTS plot_invitations (token TEXT PRIMARY KEY, plot TEXT NOT NULL REFERENCES plots(id), inviter TEXT NOT NULL REFERENCES accounts(uuid), member TEXT NOT NULL REFERENCES accounts(uuid), role TEXT NOT NULL CHECK(role IN ('BUILD','STOCK','BUILD_STOCK')), expires INTEGER NOT NULL, UNIQUE(plot,member))");

            s.execute("CREATE TABLE IF NOT EXISTS quest_changed_blocks (position TEXT PRIMARY KEY)");
            s.execute("CREATE TABLE IF NOT EXISTS quest_material_claims (id TEXT PRIMARY KEY, target TEXT NOT NULL, amount INTEGER NOT NULL, staff TEXT NOT NULL, state TEXT NOT NULL DEFAULT 'REVIEW')");
            s.execute("CREATE TABLE IF NOT EXISTS quest_deposits (id TEXT PRIMARY KEY, uuid TEXT NOT NULL, week TEXT NOT NULL, target TEXT NOT NULL, amount INTEGER NOT NULL, before_hash TEXT NOT NULL, after_hash TEXT NOT NULL, state TEXT NOT NULL DEFAULT 'REVIEW', time INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS plot_addresses (plot TEXT PRIMARY KEY REFERENCES plots(id), address TEXT NOT NULL UNIQUE COLLATE NOCASE)");
            s.execute("CREATE TABLE IF NOT EXISTS plot_names (plot TEXT PRIMARY KEY REFERENCES plots(id), display TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS plot_absences (plot TEXT PRIMARY KEY REFERENCES plots(id), expires INTEGER NOT NULL)");

            s.execute("CREATE TABLE IF NOT EXISTS plot_leases (plot TEXT PRIMARY KEY REFERENCES plots(id), lease TEXT NOT NULL UNIQUE)");
            s.execute("CREATE TABLE IF NOT EXISTS plot_abandonments (lease TEXT PRIMARY KEY, plot TEXT NOT NULL REFERENCES plots(id), owner TEXT NOT NULL REFERENCES accounts(uuid), time INTEGER NOT NULL, refund INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS native_offers (id TEXT PRIMARY KEY, plot TEXT NOT NULL REFERENCES plots(id), lease TEXT NOT NULL, owner TEXT NOT NULL REFERENCES accounts(uuid), item BLOB NOT NULL, bundle INTEGER NOT NULL CHECK(bundle BETWEEN 1 AND 64), cents INTEGER NOT NULL CHECK(cents BETWEEN 1 AND 100000000000), stock INTEGER NOT NULL DEFAULT 0 CHECK(stock BETWEEN 0 AND 1000000), revision INTEGER NOT NULL DEFAULT 1, closed INTEGER NOT NULL DEFAULT 0)");
            s.execute("CREATE TABLE IF NOT EXISTS native_stock_receipts (id TEXT PRIMARY KEY, offer TEXT NOT NULL REFERENCES native_offers(id), actor TEXT NOT NULL REFERENCES accounts(uuid), quantity INTEGER NOT NULL, time INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS native_trade_receipts (id TEXT PRIMARY KEY, offer TEXT NOT NULL REFERENCES native_offers(id), buyer TEXT NOT NULL REFERENCES accounts(uuid), seller TEXT NOT NULL REFERENCES accounts(uuid), quantity INTEGER NOT NULL, cents INTEGER NOT NULL, revision INTEGER NOT NULL, item BLOB NOT NULL, time INTEGER NOT NULL, delivery TEXT NOT NULL DEFAULT 'PENDING' CHECK(delivery IN ('PENDING','REVIEW','DELIVERED')))");
            s.execute("CREATE TABLE IF NOT EXISTS native_stock_intents (receipt TEXT PRIMARY KEY, offer TEXT NOT NULL REFERENCES native_offers(id), actor TEXT NOT NULL REFERENCES accounts(uuid), quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 64), before_hash BLOB NOT NULL, after_hash BLOB NOT NULL, state TEXT NOT NULL DEFAULT 'REVIEW' CHECK(state IN ('REVIEW','COMPLETE')), time INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS native_stock_resolutions (receipt TEXT PRIMARY KEY REFERENCES native_stock_intents(receipt), resolution TEXT NOT NULL CHECK(resolution IN ('CREDIT','NO_CREDIT')), staff TEXT NOT NULL, reason TEXT NOT NULL, time INTEGER NOT NULL)");
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS native_one_deposit_per_actor ON native_stock_intents(actor) WHERE state='REVIEW'");
            s.execute("CREATE TABLE IF NOT EXISTS native_payment_terms (offer TEXT PRIMARY KEY REFERENCES native_offers(id), item BLOB NOT NULL, quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 64))");
            s.execute("CREATE TABLE IF NOT EXISTS native_payment_balances (actor TEXT NOT NULL REFERENCES accounts(uuid), offer TEXT NOT NULL REFERENCES native_offers(id), quantity INTEGER NOT NULL CHECK(quantity BETWEEN 0 AND 1000000), PRIMARY KEY(actor,offer))");
            s.execute("CREATE TABLE IF NOT EXISTS native_payment_intents (receipt TEXT PRIMARY KEY, offer TEXT NOT NULL REFERENCES native_offers(id), actor TEXT NOT NULL REFERENCES accounts(uuid), quantity INTEGER NOT NULL, before_hash BLOB NOT NULL, after_hash BLOB NOT NULL, state TEXT NOT NULL DEFAULT 'REVIEW', time INTEGER NOT NULL)");
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS native_one_payment_per_actor ON native_payment_intents(actor) WHERE state='REVIEW'");
            s.execute("CREATE TABLE IF NOT EXISTS native_locations (location TEXT PRIMARY KEY, offer TEXT NOT NULL UNIQUE REFERENCES native_offers(id))");
            s.execute("CREATE TABLE IF NOT EXISTS native_stock_returns (receipt TEXT PRIMARY KEY REFERENCES native_trade_receipts(id))");
            s.execute("CREATE TABLE IF NOT EXISTS native_delivery_plans (receipt TEXT PRIMARY KEY REFERENCES native_trade_receipts(id), buyer TEXT NOT NULL REFERENCES accounts(uuid), before_hash BLOB NOT NULL, after_hash BLOB NOT NULL)");
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS native_one_delivery_per_buyer ON native_trade_receipts(buyer) WHERE delivery='REVIEW'");
            s.execute("CREATE TABLE IF NOT EXISTS quest_rotations (week TEXT PRIMARY KEY, definitions TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS quest_progress (uuid TEXT NOT NULL REFERENCES accounts(uuid), week TEXT NOT NULL REFERENCES quest_rotations(week), goal TEXT NOT NULL, progress INTEGER NOT NULL, PRIMARY KEY(uuid,week,goal))");
            s.execute("CREATE TABLE IF NOT EXISTS quest_visits (uuid TEXT NOT NULL REFERENCES accounts(uuid), week TEXT NOT NULL REFERENCES quest_rotations(week), goal TEXT NOT NULL, biome TEXT NOT NULL, PRIMARY KEY(uuid,week,goal,biome))");
            s.execute("CREATE TABLE IF NOT EXISTS niko_acceptance(uuid TEXT NOT NULL REFERENCES accounts(uuid),week TEXT NOT NULL REFERENCES quest_rotations(week),time INTEGER NOT NULL,actor TEXT NOT NULL,PRIMARY KEY(uuid,week))");
            s.execute("CREATE TABLE IF NOT EXISTS pip_visits(id TEXT PRIMARY KEY,arrival INTEGER NOT NULL,restock INTEGER NOT NULL,departure INTEGER NOT NULL,season TEXT NOT NULL,restocked INTEGER NOT NULL DEFAULT 0)");
            s.execute("CREATE TABLE IF NOT EXISTS pip_offers(visit TEXT NOT NULL REFERENCES pip_visits(id),design TEXT NOT NULL,name TEXT NOT NULL,item BLOB NOT NULL,price INTEGER NOT NULL CHECK(price>0),allocation INTEGER NOT NULL CHECK(allocation>0),stock INTEGER NOT NULL CHECK(stock>=0),cap INTEGER NOT NULL CHECK(cap>=0),PRIMARY KEY(visit,design))");
            s.execute("CREATE TABLE IF NOT EXISTS pip_receipts(id TEXT PRIMARY KEY,buyer TEXT NOT NULL REFERENCES accounts(uuid),visit TEXT NOT NULL,design TEXT NOT NULL,quantity INTEGER NOT NULL CHECK(quantity>0),cents INTEGER NOT NULL CHECK(cents>0),before_hash BLOB NOT NULL,after_hash BLOB NOT NULL,time INTEGER NOT NULL,state TEXT NOT NULL DEFAULT 'REVIEW' CHECK(state IN ('REVIEW','DELIVERED','REFUNDED')),resolution TEXT NOT NULL DEFAULT '',FOREIGN KEY(visit,design) REFERENCES pip_offers(visit,design))");
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS pip_one_pending_per_buyer ON pip_receipts(buyer) WHERE state='REVIEW'");

            // Give existing leases an identity once; a new rental always replaces it.
            for(Plot plot:plots())if(plot.owner()!=null)
                update("INSERT OR IGNORE INTO plot_leases(plot,lease) VALUES(?,?)",plot.id(),UUID.randomUUID());
        }

    }

    public synchronized List<QuestPlan.Goal> activateQuestConfig(String week,List<QuestPlan.Goal> proposed,long now)throws SQLException {
        return tx(()->{
            if(!QuestPlan.week(now).id().equals(week))throw new IllegalArgumentException("The week changed; preview activation again.");
            String previous;
            try(var p=db.prepareStatement("SELECT definitions FROM quest_rotations WHERE week=?")){
                p.setString(1,week);try(var rows=p.executeQuery()){if(!rows.next())throw new IllegalArgumentException("No saved week to replace.");previous=rows.getString(1);}
            }
            try(var p=db.prepareStatement("SELECT 1 FROM metadata WHERE key=?")){
                p.setString(1,"quest-activation:"+week);try(var rows=p.executeQuery()){if(rows.next())throw new IllegalArgumentException("The configured quests have already been activated this week.");}
            }
            if(previous.equals(QuestPlan.encode(proposed)))throw new IllegalArgumentException("The configured quests are already active.");
            try(var p=db.prepareStatement("SELECT 1 FROM quest_deposits WHERE state='REVIEW' UNION ALL SELECT 1 FROM quest_material_claims WHERE state='REVIEW' LIMIT 1");var rows=p.executeQuery()){
                if(rows.next())throw new IllegalArgumentException("Resolve pending contribution records before changing quests.");
            }
            if(proposed.size()!=7 || proposed.stream().filter(g->g.reward()==1500).count()!=6 || proposed.stream().filter(g->g.reward()==3000).count()!=1)throw new IllegalArgumentException("Expected six quests and one challenge.");
            var activated=new ArrayList<QuestPlan.Goal>();
            String prefix="launch_"+UUID.randomUUID().toString().replace("-","").substring(0,20)+"_";
            for(int i=0;i<proposed.size();i++){var g=proposed.get(i);activated.add(new QuestPlan.Goal(prefix+i,g.title(),g.kind(),g.target(),g.amount(),g.reward()));}
            update("INSERT INTO metadata VALUES(?,?)","quest-activation:"+week,previous);
            update("UPDATE quest_rotations SET definitions=? WHERE week=?",QuestPlan.encode(activated),week);
            return List.copyOf(activated);
        });
    }

    public List<QuestPlan.Goal> questRotation(String week,List<QuestPlan.Goal> proposed)throws SQLException {
        if(proposed.isEmpty() || proposed.stream().map(QuestPlan.Goal::id).distinct().count()!=proposed.size())throw new IllegalArgumentException("Quest IDs must be unique and rotation nonempty.");
        return tx(()->{
            update("INSERT OR IGNORE INTO quest_rotations VALUES(?,?)",week,QuestPlan.encode(proposed));
            try(var p=db.prepareStatement("SELECT definitions FROM quest_rotations WHERE week=?")){
                bind(p,week);try(var rows=p.executeQuery()){rows.next();return QuestPlan.decode(rows.getString(1));}
            }
        });
    }
    public synchronized int questProgress(UUID player,String week,String goal)throws SQLException {
        try(var p=db.prepareStatement("SELECT progress FROM quest_progress WHERE uuid=? AND week=? AND goal=?")){
            bind(p,player,week,goal);try(var rows=p.executeQuery()){return rows.next()?rows.getInt(1):0;}
        }
    }
    public static final class BalanceCapacityException extends IllegalArgumentException {
        BalanceCapacityException(){super("Balance limit reached.");}
    }
    public record QuestUpdate(QuestPlan.Goal goal,int progress,boolean paid) {}
    public List<QuestPlan.Goal> progressQuests(UUID player,String week,String kind,String target,String unique,long now)throws SQLException {
        return advanceQuests(player,week,kind,target,unique,now).stream().filter(QuestUpdate::paid).map(QuestUpdate::goal).toList();
    }
    public List<QuestUpdate> advanceQuests(UUID player,String week,String kind,String target,String unique,long now)throws SQLException {
        return tx(()->advanceQuestBatch(player,week,kind,target,unique,1,now));
    }
    private List<QuestUpdate> advanceQuestBatch(UUID player,String week,String kind,String target,String unique,int amount,long now)throws SQLException {
        // Caller supplies observed progress only; rewards come from the frozen snapshot.

            List<QuestPlan.Goal> goals;
            try(var p=db.prepareStatement("SELECT definitions FROM quest_rotations WHERE week=?")){
                bind(p,week);try(var rows=p.executeQuery()){if(!rows.next())throw new IllegalArgumentException("Unknown quest rotation");goals=QuestPlan.decode(rows.getString(1));}
            }
            if(!QuestPlan.week(now).id().equals(week))throw new IllegalArgumentException("Quest week has changed");
            if(!questAccepted(player,week))return List.of();
            var updates=new ArrayList<QuestUpdate>();
            for(var g:goals){
                if(!g.kind().equals(kind) || !(g.target().equals(target) || g.target().equals("ANY")))continue;
                int before=questProgress(player,week,g.id());if(before>=g.amount())continue;
                if(kind.equals("BIOME") || kind.equals("MINE")){
                    if(unique==null || unique.isBlank())throw new IllegalArgumentException("Missing biome identity");
                    if(update("INSERT OR IGNORE INTO quest_visits VALUES(?,?,?,?)",player,week,g.id(),unique)==0)continue;
                }
                int after=Math.min(g.amount(),before+amount);
                update("INSERT INTO quest_progress VALUES(?,?,?,?) ON CONFLICT(uuid,week,goal) DO UPDATE SET progress=excluded.progress",player,week,g.id(),after);
                boolean paid=after==g.amount() && awardInternal(player,"quest:"+week+":"+g.id(),g.reward(),now);
                updates.add(new QuestUpdate(g,after,paid));
            }
            return updates;
    }

    public synchronized void markQuestBlock(String position)throws SQLException {update("INSERT OR IGNORE INTO quest_changed_blocks VALUES(?)",position);}
    public synchronized boolean changedQuestBlock(String position)throws SQLException {
        try(var p=db.prepareStatement("SELECT 1 FROM quest_changed_blocks WHERE position=?")){bind(p,position);try(var r=p.executeQuery()){return r.next();}}
    }
    public synchronized void beginQuestDeposit(UUID receipt,UUID player,String week,String target,int amount,String before,String after,long now)throws SQLException {
        if(!questAccepted(player,week))throw new IllegalArgumentException("Speak to Niko at spawn before contributing this week.");
        if(amount<1 || amount>100000)throw new IllegalArgumentException("Invalid contribution amount.");
        if(!QuestPlan.week(now).id().equals(week))throw new IllegalArgumentException("Quest week has changed.");
        try(var query=db.prepareStatement("SELECT definitions FROM quest_rotations WHERE week=?")){
            bind(query,week);try(var rows=query.executeQuery()){
                if(!rows.next())throw new IllegalArgumentException("Unknown quest rotation.");
                var goal=QuestPlan.decode(rows.getString(1)).stream().filter(g->g.kind().equals("DEPOSIT") && g.target().equals(target)).findFirst().orElseThrow(()->new IllegalArgumentException("This material is not requested this week."));
                if(amount>goal.amount()-questProgress(player,week,goal.id()))throw new IllegalArgumentException("That exceeds your remaining contribution target.");
            }
        }
        try(var p=db.prepareStatement("SELECT 1 FROM quest_deposits WHERE uuid=? AND state='REVIEW'")){bind(p,player);try(var r=p.executeQuery()){if(r.next())throw new IllegalArgumentException("An interrupted contribution needs staff review before another deposit.");}}
        update("INSERT INTO quest_deposits(id,uuid,week,target,amount,before_hash,after_hash,time) VALUES(?,?,?,?,?,?,?,?)",receipt,player,week,target,amount,before,after,now);
    }
    public List<QuestUpdate> finishQuestDeposit(UUID receipt,long now)throws SQLException {
        return tx(()->{
            try(var p=db.prepareStatement("SELECT * FROM quest_deposits WHERE id=?")){
                bind(p,receipt);try(var r=p.executeQuery()){
                    if(!r.next() || !r.getString("state").equals("REVIEW"))throw new IllegalArgumentException("Contribution is no longer pending.");
                    var updates=advanceQuestBatch(UUID.fromString(r.getString("uuid")),r.getString("week"),"DEPOSIT",r.getString("target"),null,r.getInt("amount"),now);
                    if(updates.isEmpty())throw new IllegalArgumentException("This contribution no longer advances a quest.");
                    update("UPDATE quest_deposits SET state='COMPLETE' WHERE id=?",receipt);return updates;
                }
            }
        });
    }
    public synchronized void cancelQuestDeposit(UUID receipt)throws SQLException {update("UPDATE quest_deposits SET state='CANCELLED' WHERE id=? AND state='REVIEW'",receipt);}
    public void beginQuestClaim(UUID id,String target,int amount,String staff)throws SQLException {
        if(amount<1 || amount>64)throw new IllegalArgumentException("Collect between 1 and 64 items.");
        tx(()->{
            long available=0;
            try(var p=db.prepareStatement("SELECT COALESCE(SUM(amount),0) FROM quest_deposits WHERE target=? AND state='COMPLETE'")){bind(p,target);try(var r=p.executeQuery()){r.next();available=r.getLong(1);}}
            try(var p=db.prepareStatement("SELECT COALESCE(SUM(amount),0) FROM quest_material_claims WHERE target=? AND state IN ('REVIEW','COMPLETE')")){bind(p,target);try(var r=p.executeQuery()){r.next();available-=r.getLong(1);}}
            if(available<amount)throw new IllegalArgumentException("Only "+available+" contributed items are available to collect.");
            update("INSERT INTO quest_material_claims(id,target,amount,staff) VALUES(?,?,?,?)",id,target,amount,staff);return null;
        });
    }
    public synchronized void finishQuestClaim(UUID id,boolean complete)throws SQLException {update("UPDATE quest_material_claims SET state=? WHERE id=? AND state='REVIEW'",complete?"COMPLETE":"CANCELLED",id);}
    public synchronized List<String> questDepositReport()throws SQLException {
        var lines=new ArrayList<String>();
        try(var p=db.prepareStatement("SELECT id,uuid,target,amount,state FROM quest_deposits WHERE state='REVIEW' ORDER BY time");var r=p.executeQuery()){while(r.next())lines.add("REVIEW "+r.getString(1)+" player="+r.getString(2)+" "+r.getInt(4)+" "+r.getString(3));}
        try(var p=db.prepareStatement("SELECT week,target,SUM(amount) FROM quest_deposits WHERE state='COMPLETE' GROUP BY week,target ORDER BY week DESC");var r=p.executeQuery()){while(r.next())lines.add(r.getString(1)+" · "+r.getLong(3)+" "+r.getString(2)+" contributed");}
        try(var p=db.prepareStatement("SELECT id,target,amount,staff,state FROM quest_material_claims WHERE state='REVIEW'");var r=p.executeQuery()){while(r.next())lines.add("REVIEW collection "+r.getString(1)+" "+r.getInt(3)+" "+r.getString(2)+" staff="+r.getString(4));}
        try(var p=db.prepareStatement("SELECT target,SUM(amount) FROM quest_material_claims WHERE state IN ('REVIEW','COMPLETE') GROUP BY target");var r=p.executeQuery()){while(r.next())lines.add("Collected/reserved: "+r.getLong(2)+" "+r.getString(1));}
        return lines;
    }
    private synchronized <T> T tx(Work<T> work) throws SQLException {

        db.setAutoCommit(false);

        try { T result = work.run(); db.commit(); return result; }

        catch (SQLException | RuntimeException e) { db.rollback(); throw e; }

        finally { db.setAutoCommit(true); }

    }

    private int update(String sql, Object... args) throws SQLException {

        try (PreparedStatement p = db.prepareStatement(sql)) { bind(p,args); return p.executeUpdate(); }

    }

    private static void bind(PreparedStatement p, Object... args) throws SQLException {

        for (int i=0;i<args.length;i++) p.setObject(i+1,args[i] instanceof UUID ? args[i].toString() : args[i]);

    }

    synchronized long bunnySeconds(UUID id)throws SQLException {
        try(var p=db.prepareStatement("SELECT seconds FROM bunny_time WHERE uuid=?")){
            bind(p,id);try(var r=p.executeQuery()){return r.next()?r.getLong(1):0;}
        }
    }
    private void creditBunnyTime(UUID id,int minutes)throws SQLException {
        if(minutes<1 || minutes>60)throw new IllegalArgumentException("Choose 1 to 60 minutes.");
        long seconds=Math.addExact(bunnySeconds(id),minutes*60L);
        if(seconds>360000)throw new IllegalArgumentException("You can hold at most 100 hours of bunny time.");
        update("INSERT INTO bunny_time(uuid,seconds) VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET seconds=excluded.seconds",id,seconds);
    }
    void buyBunnyTime(UUID id,int minutes,long price,long now)throws SQLException {
        if(price<1 || price>Money.MAX)throw new IllegalArgumentException("Bunny time purchases are not open yet.");
        tx(()->{creditBunnyTime(id,minutes);mutate(id,-Math.multiplyExact(price,minutes),"bunny-time",minutes+" minutes",now);return null;});
    }
    void grantBunnyTime(UUID id,int minutes,String actor,long now)throws SQLException {
        tx(()->{creditBunnyTime(id,minutes);update("INSERT INTO bunny_grants(uuid,seconds,actor,time) VALUES(?,?,?,?)",id,minutes*60L,actor,now);return null;});
    }
    boolean consumeBunnySecond(UUID id)throws SQLException {
        return tx(()->update("UPDATE bunny_time SET seconds=seconds-1 WHERE uuid=? AND seconds>0",id)==1);
    }

    synchronized void online(UUID id,boolean value){if(value)onlineAccounts.add(id);else onlineAccounts.remove(id);}
    synchronized IncomeReport offlineIncome(UUID id)throws SQLException {
        long through=0,payments=0,sales=0;
        var amounts=new LinkedHashMap<UUID,Long>();
        try(var p=db.prepareStatement("SELECT l.id,l.delta,l.kind,(SELECT uuid FROM ledger t WHERE t.kind='transfer' AND t.reference=l.reference AND t.delta<0 LIMIT 1) sender FROM offline_income o JOIN ledger l ON l.id=o.ledger_id WHERE l.uuid=? ORDER BY l.id")){
            bind(p,id);try(var r=p.executeQuery()){while(r.next()){
                through=r.getLong("id");long cents=r.getLong("delta");
                if(r.getString("kind").equals("native-shop-sale"))sales=Math.addExact(sales,cents);
                else {payments=Math.addExact(payments,cents);String sender=r.getString("sender");if(sender!=null)amounts.merge(UUID.fromString(sender),cents,Math::addExact);}
            }}
        }
        var senders=new ArrayList<IncomeSender>();
        for(var entry:amounts.entrySet())senders.add(new IncomeSender(entry.getKey(),account(entry.getKey()).map(Account::name).orElse(entry.getKey().toString()),entry.getValue()));
        return new IncomeReport(through,payments,sales,List.copyOf(senders));
    }
    void acknowledgeIncome(UUID id,long through)throws SQLException {
        tx(()->{update("DELETE FROM offline_income WHERE ledger_id IN (SELECT id FROM ledger WHERE uuid=? AND id<=?)",id,through);return null;});
    }

    public synchronized Optional<Account> account(UUID id) throws SQLException {

        try (PreparedStatement p=db.prepareStatement("SELECT * FROM accounts WHERE uuid=?")) {

            bind(p,id); try(ResultSet r=p.executeQuery()) { return r.next()?Optional.of(new Account(id,r.getString("name"),r.getLong("cents"),r.getLong("seen"))):Optional.empty(); }

        }

    }

    public synchronized Optional<Account> byName(String name) throws SQLException {

        // Refuse ambiguous old/current names rather than sending money to the wrong UUID.

        try(PreparedStatement p=db.prepareStatement("SELECT uuid FROM accounts WHERE name=? COLLATE NOCASE")) {

            bind(p,name); try(ResultSet r=p.executeQuery()) {

                if(!r.next()) return Optional.empty(); UUID id=UUID.fromString(r.getString(1));

                if(r.next()) throw new IllegalArgumentException("Name is ambiguous; use the player's UUID.");

                return account(id);

            }

        }

    }

    public synchronized List<Account> accounts() throws SQLException {

        List<Account> result=new ArrayList<>();

        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT * FROM accounts ORDER BY name")) {

            while(r.next()) result.add(new Account(UUID.fromString(r.getString("uuid")),r.getString("name"),r.getLong("cents"),r.getLong("seen")));

        } return result;

    }

    public boolean join(UUID id, String name, long now, long bonus) throws SQLException {

        if(bonus<0 || bonus>Money.MAX) throw new IllegalArgumentException("Invalid joining bonus.");

        return tx(()-> {

            update("INSERT INTO accounts(uuid,name,seen) VALUES(?,?,?) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,seen=excluded.seen",id,name,now);

            return awardInternal(id,"joining",bonus,now);

        });

    }

    public synchronized void touch(UUID id,long now) throws SQLException { update("UPDATE accounts SET seen=? WHERE uuid=?",now,id); }

    public boolean award(UUID id,String key,long cents,long now) throws SQLException {

        if(cents<0 || cents>Money.MAX || key.isBlank()) throw new IllegalArgumentException("Invalid award.");

        return tx(()->awardInternal(id,key,cents,now));

    }

    private boolean awardInternal(UUID id,String key,long cents,long now) throws SQLException {

        if(update("INSERT OR IGNORE INTO awards(uuid,key) VALUES(?,?)",id,key)==0) return false;

        mutate(id,cents,"award",key,now); return true;

    }

    private void mutate(UUID id,long delta,String kind,String ref,long now) throws SQLException {

        Account a=account(id).orElseThrow(()->new IllegalArgumentException("That player has not joined this season."));

        long next=Math.addExact(a.cents(),delta);

        if(next<0) throw new IllegalArgumentException("Not enough Nooks. Cost: "+Money.format(-delta)+" · Your balance: "+Money.format(a.cents())+".");

        if(next>Money.MAX) throw new BalanceCapacityException();

        update("UPDATE accounts SET cents=? WHERE uuid=?",next,id);

        update("INSERT INTO ledger(time,uuid,delta,kind,reference) VALUES(?,?,?,?,?)",now,id,delta,kind,ref);
        if(delta>0 && !onlineAccounts.contains(id) && Set.of("transfer","native-shop-sale").contains(kind))
            update("INSERT INTO offline_income(ledger_id) VALUES(last_insert_rowid())");
    }

    public void transfer(UUID from,UUID to,long cents,long now) throws SQLException {

        if(cents<=0 || cents>Money.MAX || from.equals(to)) throw new IllegalArgumentException("Choose another player and a positive amount.");

        tx(()-> { String ref=UUID.randomUUID().toString(); mutate(from,-cents,"transfer",ref,now); mutate(to,cents,"transfer",ref,now); return null; });

    }

    synchronized Map<UUID,Long> metricEarnings(long since,long until)throws SQLException {
        if(since<0 || until<=since)throw new IllegalArgumentException("Invalid metrics interval.");
        var result=new HashMap<UUID,Long>();
        try(var p=db.prepareStatement("SELECT uuid,SUM(delta) FROM ledger WHERE time>=? AND time<? AND kind='award' AND delta>0 AND (reference LIKE 'minecraft:%' OR reference LIKE 'quest:%') GROUP BY uuid")){
            p.setLong(1,since);p.setLong(2,until);try(var r=p.executeQuery()){while(r.next())result.put(UUID.fromString(r.getString(1)),r.getLong(2));}
        }return Map.copyOf(result);
    }

    public void adjust(UUID id,long delta,String actor,String reason,long now) throws SQLException {

        if(delta==0 || delta>Money.MAX || delta < -Money.MAX || reason.isBlank()) throw new IllegalArgumentException("Provide an amount and reason.");

        tx(()-> { mutate(id,delta,"staff",actor+": "+reason,now); return null; });

    }

    public void integrationAdjustment(UUID id,long delta,String reference,long now)throws SQLException {

        if(delta>Money.MAX || delta < -Money.MAX)throw new IllegalArgumentException("Amount out of range.");

        tx(()->{mutate(id,delta,"integration",reference,now);return null;});

    }

    public synchronized List<Entry> history(UUID id) throws SQLException {

        List<Entry> result=new ArrayList<>();

        try(PreparedStatement p=db.prepareStatement("SELECT * FROM ledger WHERE uuid=? ORDER BY id DESC LIMIT 10")) {

            bind(p,id);try(ResultSet r=p.executeQuery()){while(r.next())result.add(new Entry(r.getLong("id"),r.getLong("time"),r.getLong("delta"),r.getString("kind"),r.getString("reference")));}

        } return result;

    }

    public synchronized String plotLabel(String id)throws SQLException {
        plot(id);
        try(var p=db.prepareStatement("SELECT display FROM plot_names WHERE plot=?")){
            bind(p,id);try(var rows=p.executeQuery()){return rows.next()?rows.getString(1):plotAddress(id);}
        }
    }
    public synchronized String plotAddress(String id)throws SQLException {
        plot(id);
        try(var p=db.prepareStatement("SELECT address FROM plot_addresses WHERE plot=?")){
            bind(p,id);try(var rows=p.executeQuery()){return rows.next()?rows.getString(1):id;}
        }
    }
    public synchronized void addressPlot(String id,String address)throws SQLException {
        plot(id);update("INSERT INTO plot_addresses(plot,address) VALUES(?,?)",id,PlotNames.clean(address));
    }
    public void namePlot(String id,UUID actor,String name,long now)throws SQLException {
        String cleaned=name==null?null:PlotNames.clean(name);
        tx(()->{
            Plot p=plot(id);owner(p,actor);
            if(!p.state().equals("ACTIVE") || now>=p.paidUntil())throw new IllegalArgumentException("Reopen the shop before changing its name.");
            if(cleaned==null)update("DELETE FROM plot_names WHERE plot=?",id);
            else update("INSERT INTO plot_names(plot,display) VALUES(?,?) ON CONFLICT(plot) DO UPDATE SET display=excluded.display",id,cleaned);
            event(id,"NAME",actor+" name="+(cleaned==null?"reset":cleaned),now);return null;
        });
    }
    // Internal native-shop settlement only. Physical stock custody and delivery are not wired yet.
    synchronized List<NativeShop.Offer> nativeOffers(UUID owner)throws SQLException {
        List<NativeShop.Offer> offers=new ArrayList<>();
        try(var p=db.prepareStatement("SELECT id FROM native_offers WHERE owner=? ORDER BY rowid DESC")){
            bind(p,owner);try(var r=p.executeQuery()){while(r.next())offers.add(nativeOffer(UUID.fromString(r.getString(1))));}
        }return offers;
    }
    synchronized Optional<NativeShop.Offer> nativeAt(String location)throws SQLException {
        try(var p=db.prepareStatement("SELECT offer FROM native_locations WHERE location=?")){
            bind(p,location);try(var r=p.executeQuery()){return r.next()?Optional.of(nativeOffer(UUID.fromString(r.getString(1)))):Optional.empty();}
        }
    }
    NativeShop.Offer publishNativeOffer(String location,String plot,UUID owner,byte[] item,int bundle,long cents,long now)throws SQLException {
        return publishNativeOffer(location,plot,owner,item,bundle,cents,null,0,now);
    }
    NativeShop.Offer publishNativeOffer(String location,String plot,UUID owner,byte[] item,int bundle,long cents,byte[] paymentItem,int paymentQuantity,long now)throws SQLException {
        if(paymentItem!=null)NativeShop.terms(paymentItem,paymentQuantity,1);
        if(location==null || location.isBlank())throw new IllegalArgumentException("A shop location is required.");
        return tx(()->{
            var old=nativeAt(location);
            if(old.isPresent() && old.get().closed() && !nativeFullyCollected(old.get().id()))throw new IllegalArgumentException("Collect the previous shop's stock and payments before creating a new shop here.");
            if(old.isPresent() && !old.get().closed()){
                var previous=old.get();
                if(previous.lease().equals(nativeLease(plot)))throw new IllegalArgumentException("There is already a shop here. Close it before creating another.");
                update("UPDATE native_offers SET closed=1,revision=revision+1 WHERE id=?",previous.id());
            }
            var offer=createNativeOfferInternal(plot,owner,item,bundle,cents,now);
            if(paymentItem!=null)update("INSERT INTO native_payment_terms(offer,item,quantity) VALUES(?,?,?)",offer.id(),paymentItem.clone(),paymentQuantity);
            update("INSERT INTO native_locations(location,offer) VALUES(?,?) ON CONFLICT(location) DO UPDATE SET offer=excluded.offer",location,offer.id());
            return offer;
        });
    }
    synchronized List<NativeShop.Receipt> nativeDeliveries(UUID buyer)throws SQLException {
        List<NativeShop.Receipt> result=new ArrayList<>();
        try(var p=db.prepareStatement("SELECT id FROM native_trade_receipts WHERE buyer=? AND delivery<>'DELIVERED' ORDER BY time,id LIMIT 100")){
            bind(p,buyer);try(var r=p.executeQuery()){while(r.next())result.add(nativeReceipt(UUID.fromString(r.getString(1))).orElseThrow());}
        }return result;
    }
    synchronized boolean nativeNeedsReview(UUID player)throws SQLException {return nativeInventoryPending(player);}

    record NativePayment(byte[] item,int quantity){
        NativePayment {item=item.clone();}
        @Override public byte[] item(){return item.clone();}
    }
    synchronized Optional<NativePayment> nativePayment(UUID offer)throws SQLException {
        try(var p=db.prepareStatement("SELECT item,quantity FROM native_payment_terms WHERE offer=?")){
            bind(p,offer);try(var r=p.executeQuery()){return r.next()?Optional.of(new NativePayment(r.getBytes(1),r.getInt(2))):Optional.empty();}
        }
    }
    synchronized int nativePaymentBalance(UUID actor,UUID offer)throws SQLException {
        try(var p=db.prepareStatement("SELECT quantity FROM native_payment_balances WHERE actor=? AND offer=?")){
            bind(p,actor,offer);try(var r=p.executeQuery()){return r.next()?r.getInt(1):0;}
        }
    }
    private void changeNativePayment(UUID actor,UUID offer,int delta)throws SQLException {
        long next=(long)nativePaymentBalance(actor,offer)+delta;
        if(next<0)throw new IllegalArgumentException("Not enough deposited payment items.");
        if(next>NativeShop.MAX_STOCK)throw new IllegalArgumentException("This shop's payment storage is full. Ask the owner to collect it.");
        update("INSERT INTO native_payment_balances(actor,offer,quantity) VALUES(?,?,?) ON CONFLICT(actor,offer) DO UPDATE SET quantity=excluded.quantity",actor,offer,next);
    }
    synchronized List<UUID> nativePaymentCollections(UUID actor)throws SQLException {
        List<UUID> ids=new ArrayList<>();try(var p=db.prepareStatement("SELECT offer FROM native_payment_balances WHERE actor=? AND quantity>0 ORDER BY offer")){
            bind(p,actor);try(var r=p.executeQuery()){while(r.next())ids.add(UUID.fromString(r.getString(1)));}
        }return ids;
    }
    boolean planNativePayment(UUID receipt,UUID offerId,UUID actor,int quantity,byte[] before,byte[] after,long now)throws SQLException {
        if(receipt==null || quantity<1 || quantity>64 || before==null || after==null || before.length!=32 || after.length!=32 || Arrays.equals(before,after))throw new IllegalArgumentException("Invalid payment inventory plan.");
        return tx(()->{
            try(var p=db.prepareStatement("SELECT offer,actor,quantity,before_hash,after_hash FROM native_payment_intents WHERE receipt=?")){
                bind(p,receipt);try(var r=p.executeQuery()){if(r.next()){
                    if(!r.getString(1).equals(offerId.toString()) || !r.getString(2).equals(actor.toString()) || r.getInt(3)!=quantity || !Arrays.equals(r.getBytes(4),before) || !Arrays.equals(r.getBytes(5),after))throw new IllegalArgumentException("Payment intent was reused with different details.");return false;
                }}
            }
            var offer=nativeOffer(offerId);nativeActive(offer,now);
            if(nativePayment(offerId).isEmpty())throw new IllegalArgumentException("This shop accepts Nooks.");
            if(offer.owner().equals(actor) || members(offer.plot()).containsKey(actor))throw new IllegalArgumentException("Members cannot buy from their own plot.");
            if(nativeInventoryPending(actor))throw new IllegalArgumentException("An earlier inventory operation needs review first.");
            if((long)nativePaymentBalance(actor,offerId)+quantity>NativeShop.MAX_STOCK)throw new IllegalArgumentException("Payment storage capacity reached.");
            update("INSERT INTO native_payment_intents(receipt,offer,actor,quantity,before_hash,after_hash,time) VALUES(?,?,?,?,?,?,?)",receipt,offerId,actor,quantity,before.clone(),after.clone(),now);return true;
        });
    }
    void confirmNativePayment(UUID receipt,UUID actor,byte[] saved)throws SQLException {
        tx(()->{
            try(var p=db.prepareStatement("SELECT * FROM native_payment_intents WHERE receipt=?")){
                bind(p,receipt);try(var r=p.executeQuery()){
                    if(!r.next() || !r.getString("actor").equals(actor.toString()) || !Arrays.equals(saved,r.getBytes("after_hash")))throw new IllegalArgumentException("Payment save does not match; staff review required.");
                    if(r.getString("state").equals("COMPLETE"))return null;
                    changeNativePayment(actor,UUID.fromString(r.getString("offer")),r.getInt("quantity"));
                }
            }
            update("UPDATE native_payment_intents SET state='COMPLETE' WHERE receipt=?",receipt);return null;
        });
    }
    NativeShop.Receipt reserveNativePaymentReturn(UUID receipt,UUID offerId,UUID actor,int quantity,long now)throws SQLException {
        if(quantity<1 || quantity>64)throw new IllegalArgumentException("Collect between 1 and 64 payment items.");
        return tx(()->{
            if(nativeReceipt(receipt).isPresent())throw new IllegalArgumentException("That collection receipt has already been used.");
            var offer=nativeOffer(offerId);var payment=nativePayment(offerId).orElseThrow(()->new IllegalArgumentException("This shop has no item payment."));
            changeNativePayment(actor,offerId,-quantity);
            update("INSERT INTO native_trade_receipts(id,offer,buyer,seller,quantity,cents,revision,item,time) VALUES(?,?,?,?,?,0,?,?,?)",receipt,offerId,actor,actor,quantity,offer.revision(),payment.item(),now);
            update("INSERT INTO native_stock_returns(receipt) VALUES(?)",receipt);return nativeReceipt(receipt).orElseThrow();
        });
    }
    synchronized List<String> nativeReviews()throws SQLException {
        List<String> result=new ArrayList<>();
        try(var s=db.createStatement();var r=s.executeQuery("SELECT 'stock' AS kind,receipt,actor FROM native_stock_intents WHERE state='REVIEW' UNION ALL SELECT 'payment',receipt,actor FROM native_payment_intents WHERE state='REVIEW' UNION ALL SELECT 'delivery',id,buyer FROM native_trade_receipts WHERE delivery='REVIEW' LIMIT 50")){
            while(r.next())result.add(r.getString(1)+" · "+r.getString(2)+" · player "+r.getString(3));
        }return result;
    }
    synchronized NativeStockReview nativeStockReview(UUID receipt)throws SQLException {
        if(receipt==null)throw new IllegalArgumentException("A stock receipt UUID is required.");
        try(var p=db.prepareStatement("SELECT i.receipt,i.offer,i.actor,a.name,i.quantity,i.time,i.state,o.plot,o.item,i.before_hash,i.after_hash,c.quantity AS credited_quantity,o.stock,x.resolution,x.staff,x.reason,x.time AS resolved_time FROM native_stock_intents i JOIN native_offers o ON o.id=i.offer JOIN accounts a ON a.uuid=i.actor LEFT JOIN native_stock_receipts c ON c.id=i.receipt LEFT JOIN native_stock_resolutions x ON x.receipt=i.receipt WHERE i.receipt=?")){
            bind(p,receipt);try(var r=p.executeQuery()){
                if(!r.next())throw new IllegalArgumentException("Unknown stock receipt.");
                int credited=r.getInt("credited_quantity");Integer creditedQuantity=r.wasNull()?null:credited;
                String recovery=r.getString("resolution");
                var resolution=recovery==null?null:new NativeStockReview.Resolution(NativeStockReview.Recovery.valueOf(recovery),r.getString("staff"),r.getString("reason"),r.getLong("resolved_time"));
                return new NativeStockReview(receipt,UUID.fromString(r.getString("offer")),UUID.fromString(r.getString("actor")),r.getString("name"),r.getInt("quantity"),r.getLong("time"),r.getString("state"),r.getString("plot"),r.getBytes("item"),r.getBytes("before_hash"),r.getBytes("after_hash"),creditedQuantity,r.getInt("stock"),resolution);
            }
        }
    }
    // Explicit staff decision only: never reads, removes or restores player inventory.
    boolean resolveNativeStock(NativeStockReview expected,NativeStockReview.Recovery recovery,String staff,String reason,long now)throws SQLException {
        if(expected==null || recovery==null || !"REVIEW".equals(expected.state()) || expected.creditedQuantity()!=null || expected.resolution()!=null)throw new IllegalArgumentException("An unresolved stock receipt preview is required.");
        if(staff==null || staff.isBlank() || staff.length()>128 || staff.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("A staff identifier is required.");
        if(reason==null || reason.isBlank() || reason.length()>500 || reason.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Record a single-line recovery reason of up to 500 characters.");
        if(now<expected.time())throw new IllegalArgumentException("Recovery cannot precede the stock receipt.");
        return tx(()->{
            var current=nativeStockReview(expected.receipt());
            if(current.resolution()!=null){
                var old=current.resolution();
                boolean credited=old.recovery()==NativeStockReview.Recovery.CREDIT?Objects.equals(current.creditedQuantity(),current.quantity()):current.creditedQuantity()==null;
                if(current.sameIntent(expected) && "COMPLETE".equals(current.state()) && credited && old.recovery()==recovery && old.staff().equals(staff) && old.reason().equals(reason))return false;
                throw new IllegalArgumentException("This stock receipt already has a different resolution.");
            }
            if(!current.sameSnapshot(expected) || !"REVIEW".equals(current.state()) || current.creditedQuantity()!=null)throw new IllegalArgumentException("The stock receipt or shop stock changed. Inspect it and create a fresh preview.");
            if(recovery==NativeStockReview.Recovery.CREDIT){
                if((long)current.currentStock()+current.quantity()>NativeShop.MAX_STOCK)throw new IllegalArgumentException("The shop cannot hold the recovered stock. Leave the receipt for review.");
                update("UPDATE native_offers SET stock=stock+? WHERE id=?",current.quantity(),current.offer());
                update("INSERT INTO native_stock_receipts(id,offer,actor,quantity,time) VALUES(?,?,?,?,?)",current.receipt(),current.offer(),current.actor(),current.quantity(),now);
            }
            update("INSERT INTO native_stock_resolutions(receipt,resolution,staff,reason,time) VALUES(?,?,?,?,?)",current.receipt(),recovery.name(),staff,reason,now);
            update("UPDATE native_stock_intents SET state='COMPLETE' WHERE receipt=?",current.receipt());
            return true;
        });
    }
    private UUID nativeLease(String plot)throws SQLException {
        try(var p=db.prepareStatement("SELECT lease FROM plot_leases WHERE plot=?")){
            bind(p,plot);try(var rows=p.executeQuery()){if(!rows.next())throw new IllegalArgumentException("No current rental lease.");return UUID.fromString(rows.getString(1));}
        }
    }
    NativeShop.Offer createNativeOffer(String plot,UUID actor,byte[] item,int bundle,long cents,long now)throws SQLException {
        return tx(()->createNativeOfferInternal(plot,actor,item,bundle,cents,now));
    }
    private NativeShop.Offer createNativeOfferInternal(String plot,UUID actor,byte[] item,int bundle,long cents,long now)throws SQLException {
        NativeShop.terms(item,bundle,cents);byte[] snapshot=item.clone();
            Plot p=plot(plot);owner(p,actor);if(!canTrade(plot,now))throw new IllegalArgumentException("Shop rent must be active.");
            UUID lease=nativeLease(plot),id=UUID.randomUUID();
            try(var query=db.prepareStatement("SELECT COUNT(*) FROM native_offers WHERE lease=? AND closed=0")){
                bind(query,lease);try(var rows=query.executeQuery()){if(rows.next() && rows.getInt(1)>=32)throw new IllegalArgumentException("At most 32 open offers per lease.");}
            }
            update("INSERT INTO native_offers(id,plot,lease,owner,item,bundle,cents) VALUES(?,?,?,?,?,?,?)",id,plot,lease,actor,snapshot,bundle,cents);
            return nativeOffer(id);
    }
    synchronized NativeShop.Offer nativeOffer(UUID id)throws SQLException {
        try(var p=db.prepareStatement("SELECT * FROM native_offers WHERE id=?")){
            bind(p,id);try(var r=p.executeQuery()){
                if(!r.next())throw new IllegalArgumentException("Unknown native offer.");
                return new NativeShop.Offer(id,r.getString("plot"),UUID.fromString(r.getString("lease")),UUID.fromString(r.getString("owner")),r.getBytes("item"),r.getInt("bundle"),r.getLong("cents"),r.getInt("stock"),r.getLong("revision"),r.getBoolean("closed"));
            }
        }
    }
    private void nativeActive(NativeShop.Offer offer,long now)throws SQLException {
        if(offer.closed() || !canTrade(offer.plot(),now) || !offer.lease().equals(nativeLease(offer.plot())) || !offer.owner().equals(plot(offer.plot()).owner()))throw new IllegalArgumentException("This offer's rental is no longer active.");
    }
    // Only a future custody adapter may call this AFTER durably securing the exact matching items.
    // A receipt retry cannot mint the same stock twice. This does not itself remove player/chest items.
    void creditNativeStock(UUID receipt,UUID offerId,UUID actor,int quantity,long now)throws SQLException {
        if(receipt==null)throw new IllegalArgumentException("A stock receipt ID is required.");
        if(quantity<1 || quantity>NativeShop.MAX_STOCK)throw new IllegalArgumentException("Invalid stock quantity.");
        tx(()->{
            try(var p=db.prepareStatement("SELECT offer,actor,quantity FROM native_stock_receipts WHERE id=?")){
                bind(p,receipt);try(var r=p.executeQuery()){if(r.next()){
                    if(!r.getString(1).equals(offerId.toString()) || !r.getString(2).equals(actor.toString()) || r.getInt(3)!=quantity)throw new IllegalArgumentException("Stock receipt was reused with different details.");
                    return null;
                }}
            }
            var offer=nativeOffer(offerId);nativeActive(offer,now);owner(plot(offer.plot()),actor);
            if((long)offer.stock()+quantity>NativeShop.MAX_STOCK)throw new IllegalArgumentException("Stock capacity reached.");
            update("UPDATE native_offers SET stock=stock+? WHERE id=?",quantity,offerId);
            update("INSERT INTO native_stock_receipts(id,offer,actor,quantity,time) VALUES(?,?,?,?,?)",receipt,offerId,actor,quantity,now);return null;
        });
    }
    void priceNativeOffer(UUID offerId,UUID actor,long cents,long now)throws SQLException {
        if(cents<1 || cents>Money.MAX)throw new IllegalArgumentException("Invalid price.");
        tx(()->{var offer=nativeOffer(offerId);nativeActive(offer,now);owner(plot(offer.plot()),actor);update("UPDATE native_offers SET cents=?,revision=revision+1 WHERE id=?",cents,offerId);return null;});
    }
    synchronized Optional<NativeShop.Receipt> nativeReceipt(UUID id)throws SQLException {
        try(var p=db.prepareStatement("SELECT * FROM native_trade_receipts WHERE id=?")){
            bind(p,id);try(var r=p.executeQuery()){
                if(!r.next())return Optional.empty();
                return Optional.of(new NativeShop.Receipt(id,UUID.fromString(r.getString("offer")),UUID.fromString(r.getString("buyer")),UUID.fromString(r.getString("seller")),r.getInt("quantity"),r.getLong("cents"),r.getLong("revision"),r.getBytes("item")));
            }
        }
    }
    synchronized boolean incomeSharing(String plot)throws SQLException {
        try(var p=db.prepareStatement("SELECT enabled FROM income_sharing WHERE lease=?")){
            bind(p,nativeLease(plot));try(var r=p.executeQuery()){return !r.next() || r.getInt(1)==1;}
        }
    }
    void incomeSharing(String plot,UUID actor,boolean enabled,long now)throws SQLException {
        tx(()->{var p=plot(plot);owner(p,actor);requireOpen(p,now);
            update("INSERT INTO income_sharing(lease,enabled) VALUES(?,?) ON CONFLICT(lease) DO UPDATE SET enabled=excluded.enabled",nativeLease(plot),enabled?1:0);
            event(plot,"INCOME_SHARING",actor+" enabled="+enabled,now);return null;
        });
    }
    private void shareNativeIncome(NativeShop.Offer offer,String reference,long now)throws SQLException {
        var recipients=new ArrayList<UUID>();recipients.add(offer.owner());
        if(incomeSharing(offer.plot()))members(offer.plot()).keySet().stream().filter(id->!id.equals(offer.owner())).sorted().forEach(recipients::add);
        long share=offer.cents()/recipients.size(),remainder=offer.cents()%recipients.size();
        for(int i=0;i<recipients.size();i++){
            long amount=share+(i<remainder?1:0);
            if(amount>0)mutate(recipients.get(i),amount,"native-shop-sale",reference,now);
        }
    }
    NativeShop.Receipt settleNativePurchase(UUID receipt,UUID offerId,UUID buyer,long expectedRevision,long now)throws SQLException {
        return tx(()->{
            var previous=nativeReceipt(receipt);
            if(previous.isPresent()){
                var r=previous.get();if(nativeStockReturn(receipt) || !r.offer().equals(offerId) || !r.buyer().equals(buyer) || r.revision()!=expectedRevision)throw new IllegalArgumentException("Trade receipt was reused with different details.");return r;
            }
            var offer=nativeOffer(offerId);nativeActive(offer,now);
            if(offer.revision()!=expectedRevision)throw new IllegalArgumentException("Shop terms changed. Review the offer again.");
            if(offer.owner().equals(buyer) || members(offer.plot()).containsKey(buyer))throw new IllegalArgumentException("Members cannot buy from their own plot.");
            if(offer.stock()<offer.bundle())throw new IllegalArgumentException("Not enough stock.");
            String reference="receipt="+receipt+" offer="+offerId+" quantity="+offer.bundle();
            var payment=nativePayment(offerId);long price=payment.isPresent()?0:offer.cents();
            if(payment.isPresent()){
                int amount=payment.get().quantity();
                changeNativePayment(buyer,offerId,-amount);changeNativePayment(offer.owner(),offerId,amount);
            }else {
                mutate(buyer,-offer.cents(),"native-shop-buy",reference,now);
                shareNativeIncome(offer,reference,now);
            }
            update("UPDATE native_offers SET stock=stock-? WHERE id=?",offer.bundle(),offerId);
            update("INSERT INTO native_trade_receipts(id,offer,buyer,seller,quantity,cents,revision,item,time) VALUES(?,?,?,?,?,?,?,?,?)",receipt,offerId,buyer,offer.owner(),offer.bundle(),price,offer.revision(),offer.item(),now);
            return nativeReceipt(receipt).orElseThrow();
        });
    }

    boolean planNativeStockDeposit(UUID receipt,UUID offerId,UUID actor,int quantity,byte[] before,byte[] after,long now)throws SQLException {
        if(receipt==null || quantity<1 || quantity>64 || before==null || after==null || before.length!=32 || after.length!=32 || Arrays.equals(before,after))throw new IllegalArgumentException("A receipt, item count and distinct inventory snapshots are required.");
        byte[] original=before.clone(),result=after.clone();
        return tx(()->{
            try(var p=db.prepareStatement("SELECT * FROM native_stock_intents WHERE receipt=?")){
                bind(p,receipt);try(var r=p.executeQuery()){if(r.next()){
                    if(!r.getString("offer").equals(offerId.toString()) || !r.getString("actor").equals(actor.toString()) || r.getInt("quantity")!=quantity || !Arrays.equals(original,r.getBytes("before_hash")) || !Arrays.equals(result,r.getBytes("after_hash")))throw new IllegalArgumentException("Stock intent already exists with different details.");return false;
                }}
            }
            var offer=nativeOffer(offerId);nativeActive(offer,now);if(!new PlotPermissions(this).allowed(offer.plot(),actor,PlotPermissions.Action.STOCK))throw new IllegalArgumentException("Stock permission is required.");
            if((long)offer.stock()+quantity>NativeShop.MAX_STOCK)throw new IllegalArgumentException("Stock capacity reached.");
            if(nativeInventoryPending(actor))throw new IllegalArgumentException("An earlier inventory operation needs review first.");
            try(var p=db.prepareStatement("SELECT 1 FROM native_stock_receipts WHERE id=?")){
                bind(p,receipt);try(var r=p.executeQuery()){if(r.next())throw new IllegalArgumentException("That stock receipt already exists.");}
            }
            update("INSERT INTO native_stock_intents(receipt,offer,actor,quantity,before_hash,after_hash,time) VALUES(?,?,?,?,?,?,?)",receipt,offerId,actor,quantity,original,result,now);return true;
        });
    }
    private boolean nativeInventoryPending(UUID actor)throws SQLException {
        try(var p=db.prepareStatement("SELECT 1 FROM native_stock_intents WHERE actor=? AND state='REVIEW' UNION ALL SELECT 1 FROM native_trade_receipts WHERE buyer=? AND delivery='REVIEW' UNION ALL SELECT 1 FROM native_payment_intents WHERE actor=? AND state='REVIEW'")){
            bind(p,actor,actor,actor);try(var r=p.executeQuery()){return r.next();}
        }
    }
    // Confirm only against a durably persisted inventory, not an in-memory API snapshot.
    void confirmNativeStockDeposit(UUID receipt,UUID actor,byte[] persistedAfter,long now)throws SQLException {
        tx(()->{
            UUID offerId;int quantity;
            try(var p=db.prepareStatement("SELECT * FROM native_stock_intents WHERE receipt=?")){
                bind(p,receipt);try(var r=p.executeQuery()){
                    if(!r.next() || !r.getString("actor").equals(actor.toString()) || !Arrays.equals(r.getBytes("after_hash"),persistedAfter))throw new IllegalArgumentException("Stock deposit snapshot does not match; review is required.");
                    if(r.getString("state").equals("COMPLETE"))return null;
                    offerId=UUID.fromString(r.getString("offer"));quantity=r.getInt("quantity");
                }
            }
            var offer=nativeOffer(offerId);
            if((long)offer.stock()+quantity>NativeShop.MAX_STOCK)throw new IllegalArgumentException("Stock deposit needs staff review.");
            // Items may have been removed just before closure/expiry. Preserve them for the original owner.
            update("UPDATE native_offers SET stock=stock+? WHERE id=?",quantity,offerId);
            update("INSERT INTO native_stock_receipts(id,offer,actor,quantity,time) VALUES(?,?,?,?,?)",receipt,offerId,actor,quantity,now);
            update("UPDATE native_stock_intents SET state='COMPLETE' WHERE receipt=?",receipt);return null;
        });
    }
    synchronized String nativeStockIntentState(UUID receipt)throws SQLException {
        try(var p=db.prepareStatement("SELECT state FROM native_stock_intents WHERE receipt=?")){
            bind(p,receipt);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Unknown stock intent.");return r.getString(1);}
        }
    }

    synchronized List<String> nativeLocations(UUID id)throws SQLException {var result=new ArrayList<String>();try(var p=db.prepareStatement("SELECT location FROM native_locations WHERE offer=?")){bind(p,id);try(var rows=p.executeQuery()){while(rows.next())result.add(rows.getString(1));}}return result;}
    synchronized boolean nativeFullyCollected(UUID id)throws SQLException {var o=nativeOffer(id);if(o.stock()>0)return false;
        try(var p=db.prepareStatement("SELECT (SELECT COUNT(*) FROM native_payment_balances WHERE offer=? AND quantity>0)+(SELECT COUNT(*) FROM native_trade_receipts WHERE offer=? AND delivery<>'DELIVERED')")){bind(p,id,id);try(var row=p.executeQuery()){row.next();return row.getInt(1)==0;}}
    }
    void retireEmptyNativeLocation(UUID id)throws SQLException {tx(()->{var o=nativeOffer(id);if(!o.closed() || o.stock()!=0)throw new IllegalArgumentException("Collect the shop stock first.");
        try(var p=db.prepareStatement("SELECT COUNT(*) FROM native_payment_balances WHERE offer=? AND quantity>0")){bind(p,id);try(var r=p.executeQuery()){r.next();if(r.getInt(1)>0)return null;}}
        try(var p=db.prepareStatement("SELECT COUNT(*) FROM native_trade_receipts WHERE offer=? AND delivery<>'DELIVERED'")){bind(p,id);try(var r=p.executeQuery()){r.next();if(r.getInt(1)>0)return null;}}
        update("DELETE FROM native_locations WHERE offer=?",id);return null;});}
    synchronized Map<UUID,Long> nativeSaleShares(UUID receipt)throws SQLException {var result=new LinkedHashMap<UUID,Long>();try(var p=db.prepareStatement("SELECT uuid,delta FROM ledger WHERE kind='native-shop-sale' AND reference LIKE ?")){bind(p,"receipt="+receipt+" %");try(var r=p.executeQuery()){while(r.next())result.put(UUID.fromString(r.getString(1)),r.getLong(2));}}return result;}
    void closeNativeOffer(UUID offerId,UUID actor)throws SQLException {
        tx(()->{
            var offer=nativeOffer(offerId);if(!offer.owner().equals(actor))throw new IllegalArgumentException("Only the original shop owner can close this offer.");
            if(!offer.closed())update("UPDATE native_offers SET closed=1,revision=revision+1 WHERE id=?",offerId);return null;
        });
    }
    NativeShop.Receipt reserveNativeStockWithdrawal(UUID receipt,UUID offerId,UUID actor,int quantity,long now)throws SQLException {
        if(quantity<1 || quantity>64)throw new IllegalArgumentException("Withdraw between 1 and 64 items at a time.");
        return tx(()->{
            var previous=nativeReceipt(receipt);
            if(previous.isPresent()){
                var r=previous.get();if(!nativeStockReturn(receipt) || !r.offer().equals(offerId) || !r.buyer().equals(actor) || r.quantity()!=quantity)throw new IllegalArgumentException("Withdrawal receipt was reused with different details.");return r;
            }
            var offer=nativeOffer(offerId);nativeActive(offer,now);
            if(!new PlotPermissions(this).allowed(offer.plot(),actor,PlotPermissions.Action.STOCK))throw new IllegalArgumentException("Stock permission is required.");
            if(nativeInventoryPending(actor))throw new IllegalArgumentException("An earlier inventory operation needs review first.");
            if(offer.stock()<quantity)throw new IllegalArgumentException("Not enough stock to withdraw that amount.");
            update("UPDATE native_offers SET stock=stock-? WHERE id=?",quantity,offerId);
            update("INSERT INTO native_trade_receipts(id,offer,buyer,seller,quantity,cents,revision,item,time) VALUES(?,?,?,?,?,0,?,?,?)",receipt,offerId,actor,actor,quantity,offer.revision(),offer.item(),now);
            update("INSERT INTO native_stock_returns(receipt) VALUES(?)",receipt);return nativeReceipt(receipt).orElseThrow();
        });
    }

    synchronized boolean nativeStockReturn(UUID receipt)throws SQLException {
        try(var p=db.prepareStatement("SELECT 1 FROM native_stock_returns WHERE receipt=?")){
            bind(p,receipt);try(var rows=p.executeQuery()){return rows.next();}
        }
    }
    // Preserve former owners' stock as delivery entitlements after closure/eviction, without refunding sales.
    NativeShop.Receipt reserveNativeStockReturn(UUID receipt,UUID offerId,UUID actor,int quantity,long now)throws SQLException {
        if(quantity<1 || quantity>64)throw new IllegalArgumentException("Collect between 1 and 64 items per request.");
        return tx(()->{
            var previous=nativeReceipt(receipt);
            if(previous.isPresent()){
                var r=previous.get();if(!nativeStockReturn(receipt) || !r.offer().equals(offerId) || !r.buyer().equals(actor) || r.quantity()!=quantity)throw new IllegalArgumentException("Return receipt was reused with different details.");return r;
            }
            var offer=nativeOffer(offerId);
            if(!offer.owner().equals(actor) || !offer.closed())throw new IllegalArgumentException("The original owner must close the offer before collecting stock.");
            if(offer.stock()<quantity)throw new IllegalArgumentException("Not enough remaining stock.");
            update("UPDATE native_offers SET stock=stock-? WHERE id=?",quantity,offerId);
            update("INSERT INTO native_trade_receipts(id,offer,buyer,seller,quantity,cents,revision,item,time) VALUES(?,?,?,?,?,0,?,?,?)",receipt,offerId,actor,actor,quantity,offer.revision(),offer.item(),now);
            update("INSERT INTO native_stock_returns(receipt) VALUES(?)",receipt);
            return nativeReceipt(receipt).orElseThrow();
        });
    }

    // Persist intent before changing any inventory. REVIEW blocks automatic replay after a crash.
    // Future adapter must lock inventory actions, apply the exact plan and persist player data
    // before confirming. Hashes alone are not permission to overwrite or reconstruct inventories.
    boolean planNativeDelivery(UUID receipt,UUID buyer,byte[] before,byte[] after)throws SQLException {
        if(before==null || after==null || before.length!=32 || after.length!=32 || Arrays.equals(before,after))throw new IllegalArgumentException("Distinct SHA-256 inventory snapshots are required.");
        byte[] original=before.clone(),result=after.clone();
        return tx(()->{
            var trade=nativeReceipt(receipt).orElseThrow(()->new IllegalArgumentException("Unknown trade receipt."));
            if(!trade.buyer().equals(buyer))throw new IllegalArgumentException("That delivery belongs to another player.");
            try(var p=db.prepareStatement("SELECT before_hash,after_hash FROM native_delivery_plans WHERE receipt=?")){
                bind(p,receipt);try(var r=p.executeQuery()){if(r.next()){
                    if(!Arrays.equals(original,r.getBytes(1)) || !Arrays.equals(result,r.getBytes(2)))throw new IllegalArgumentException("Delivery intent already exists with different snapshots.");return false;
                }}
            }
            if(nativeInventoryPending(buyer))throw new IllegalArgumentException("An earlier inventory operation needs review first.");
            if(!nativeDeliveryState(receipt).equals("PENDING"))throw new IllegalArgumentException("Delivery requires review.");
            try(var p=db.prepareStatement("SELECT 1 FROM native_trade_receipts WHERE buyer=? AND delivery='REVIEW'")){
                bind(p,buyer);try(var r=p.executeQuery()){if(r.next())throw new IllegalArgumentException("An earlier delivery needs review first.");}
            }
            update("INSERT INTO native_delivery_plans(receipt,buyer,before_hash,after_hash) VALUES(?,?,?,?)",receipt,buyer,original,result);
            update("UPDATE native_trade_receipts SET delivery='REVIEW' WHERE id=?",receipt);return true;
        });
    }
    synchronized String nativeDeliveryState(UUID receipt)throws SQLException {
        try(var p=db.prepareStatement("SELECT delivery FROM native_trade_receipts WHERE id=?")){
            bind(p,receipt);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Unknown trade receipt.");return r.getString(1);}
        }
    }
    void confirmNativeDelivery(UUID receipt,UUID buyer,byte[] persistedInventoryHash)throws SQLException {
        tx(()->{
            try(var p=db.prepareStatement("SELECT buyer,after_hash FROM native_delivery_plans WHERE receipt=?")){
                bind(p,receipt);try(var r=p.executeQuery()){
                    if(!r.next() || !r.getString(1).equals(buyer.toString()) || !Arrays.equals(r.getBytes(2),persistedInventoryHash))throw new IllegalArgumentException("Delivery snapshot does not match; staff review is required.");
                }
            }
            update("UPDATE native_trade_receipts SET delivery='DELIVERED' WHERE id=?",receipt);return null;
        });
    }

    private static final String RENT_REDUCTION="plot-rent-reduction:2026-10-09";
    public synchronized boolean rentReductionApplied()throws SQLException {
        try(var p=db.prepareStatement("SELECT 1 FROM metadata WHERE key=?")){bind(p,RENT_REDUCTION);try(var r=p.executeQuery()){return r.next();}}
    }
    static long reducedRent(long cents){if(cents<1)throw new IllegalArgumentException("Invalid rent");return Math.max(1,cents/5+(cents%5>=3?1:0));}
    public synchronized long defaultPlotRent(long area)throws SQLException {
        long original=PlotPricing.weekly(area);return rentReductionApplied()?reducedRent(original):original;
    }
    public synchronized void reducePlotRents(List<Plot> expected,String actor,long now)throws SQLException {
        tx(()->{
            if(rentReductionApplied())throw new IllegalArgumentException("The 80% rent reduction has already been applied.");
            if(expected.isEmpty())throw new IllegalArgumentException("There are no plots to reprice.");
            if(!plots().equals(expected))throw new IllegalArgumentException("Plot records changed. Preview the rent cut again.");
            StringBuilder audit=new StringBuilder(actor+" at "+now);
            for(var plot:expected){
                long reduced=reducedRent(plot.weekly());
                update("UPDATE plots SET weekly=? WHERE id=?",reduced,plot.id());
                audit.append("\n").append(plot.id()).append(": ").append(plot.weekly()).append(" -> ").append(reduced);
            }
            update("INSERT INTO metadata(key,value) VALUES(?,?)",RENT_REDUCTION,audit.toString());
            return null;
        });
    }

    public synchronized void definePlot(String id,long weekly) throws SQLException {

        if(!id.matches("[a-z0-9_-]{1,32}") || weekly<=0 || weekly>Money.MAX/4) throw new IllegalArgumentException("Invalid plot definition.");

        // Existing lease prices are intentionally not silently rewritten by config reloads.

        update("INSERT OR IGNORE INTO plots(id,weekly) VALUES(?,?)",id,weekly);

    }

    public synchronized Plot plot(String id) throws SQLException {

        try(PreparedStatement p=db.prepareStatement("SELECT * FROM plots WHERE id=?")) {

            bind(p,id);try(ResultSet r=p.executeQuery()){

                if(!r.next())throw new IllegalArgumentException("Unknown plot.");String owner=r.getString("owner");

                return new Plot(id,r.getLong("weekly"),owner==null?null:UUID.fromString(owner),r.getLong("paid_until"),r.getString("state"));

            }

        }

    }

    public synchronized List<Plot> plots() throws SQLException {

        List<String> ids=new ArrayList<>();try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT id FROM plots ORDER BY id")){while(r.next())ids.add(r.getString(1));}

        List<Plot> result=new ArrayList<>();for(String id:ids)result.add(plot(id));return result;

    }

    private void event(String id,String kind,String detail,long now)throws SQLException { update("INSERT INTO plot_events(time,plot,kind,detail) VALUES(?,?,?,?)",now,id,kind,detail); }

    private void owner(Plot p,UUID actor){ if(!actor.equals(p.owner()))throw new IllegalArgumentException("Only the original renter can do that."); }

    public void rent(String id,UUID actor,long now)throws SQLException {

        tx(()-> {

            Plot p=plot(id);if(!p.state().equals("AVAILABLE"))throw new IllegalArgumentException("Plot is unavailable.");

            requireNoMembership(actor,"You are at the maximum plot capacity: 1. Leave or abandon your current plot first.");

            update("INSERT INTO members(uuid,plot,role) VALUES(?,?,'OWNER')",actor,id);

            mutate(actor,-p.weekly(),"rent",id,now);

            update("UPDATE plots SET owner=?,paid_until=?,state='ACTIVE' WHERE id=?",actor,Math.addExact(now,WEEK),id);

            update("INSERT INTO plot_leases(plot,lease) VALUES(?,?) ON CONFLICT(plot) DO UPDATE SET lease=excluded.lease",id,UUID.randomUUID());
            event(id,"RENT",actor.toString(),now);return null;

        });

    }

    private void requireNoMembership(UUID member)throws SQLException {requireNoMembership(member,"That player already belongs to a plot.");}
    private void requireNoMembership(UUID member,String message)throws SQLException {

        try(PreparedStatement p=db.prepareStatement("SELECT 1 FROM members WHERE uuid=?")) {

            bind(p,member);try(ResultSet r=p.executeQuery()){if(r.next())throw new IllegalArgumentException(message);}

        }

    }

    private static void memberRole(String role) {

        if(!Set.of("BUILD","STOCK","BUILD_STOCK").contains(role))throw new IllegalArgumentException("Choose build, stock or both for plot permissions.");
    }

    private void requireOpen(Plot plot,long now) {

        if(!plot.state().equals("ACTIVE") || now>=plot.paidUntil())throw new IllegalArgumentException("Reopen the plot first.");

    }

    public Invitation invite(String id,UUID actor,UUID member,String role,long now)throws SQLException {

        memberRole(role);

        return tx(()->{

            Plot p=plot(id);owner(p,actor);requireOpen(p,now);requireNoMembership(member);

            if(account(member).isEmpty())throw new IllegalArgumentException("That player has not joined this season.");

            Invitation invitation=new Invitation(UUID.randomUUID(),id,actor,member,role,Math.addExact(now,INVITATION_LIFETIME));

            update("INSERT INTO plot_invitations(token,plot,inviter,member,role,expires) VALUES(?,?,?,?,?,?) ON CONFLICT(plot,member) DO UPDATE SET token=excluded.token,inviter=excluded.inviter,role=excluded.role,expires=excluded.expires",invitation.token(),id,actor,member,role,invitation.expires());

            event(id,"INVITE",actor+" invited="+member+" role="+role+" expires="+invitation.expires(),now);

            return invitation;

        });

    }

    private Invitation invitation(UUID token)throws SQLException {

        try(PreparedStatement p=db.prepareStatement("SELECT * FROM plot_invitations WHERE token=?")) {

            bind(p,token);try(ResultSet r=p.executeQuery()) {

                if(!r.next())throw new IllegalArgumentException("Invitation no longer exists.");

                return new Invitation(token,r.getString("plot"),UUID.fromString(r.getString("inviter")),UUID.fromString(r.getString("member")),r.getString("role"),r.getLong("expires"));

            }

        }

    }

    public synchronized List<Invitation> invitations(UUID member,long now)throws SQLException {

        List<UUID> tokens=new ArrayList<>();

        try(PreparedStatement p=db.prepareStatement("SELECT token FROM plot_invitations WHERE member=? AND expires>? ORDER BY expires")) {

            bind(p,member,now);try(ResultSet r=p.executeQuery()){while(r.next())tokens.add(UUID.fromString(r.getString(1)));}

        }

        List<Invitation> result=new ArrayList<>();for(UUID token:tokens)result.add(invitation(token));return List.copyOf(result);

    }

    public void acceptInvitation(UUID token,UUID actor,long now)throws SQLException {

        tx(()->{

            Invitation i=invitation(token);

            if(!actor.equals(i.member()))throw new IllegalArgumentException("This invitation is for another player.");

            if(now>=i.expires())throw new IllegalArgumentException("Invitation has expired.");

            Plot p=plot(i.plot());owner(p,i.inviter());requireOpen(p,now);requireNoMembership(actor);

            update("INSERT INTO members(uuid,plot,role) VALUES(?,?,?)",actor,i.plot(),i.role());

            update("DELETE FROM plot_invitations WHERE member=?",actor);

            event(i.plot(),"MEMBER",actor+":"+i.role()+" accepted="+token,now);return null;

        });

    }

    public void declineInvitation(UUID token,UUID actor,long now)throws SQLException {

        tx(()->{Invitation i=invitation(token);if(!actor.equals(i.member()))throw new IllegalArgumentException("This invitation is for another player.");

            update("DELETE FROM plot_invitations WHERE token=?",token);event(i.plot(),"DECLINE_INVITE",actor.toString(),now);return null;});

    }

    public void changeRole(String id,UUID actor,UUID member,String role,long now)throws SQLException {

        memberRole(role);

        tx(()->{Plot p=plot(id);owner(p,actor);requireOpen(p,now);

            if(actor.equals(member))throw new IllegalArgumentException("The original renter retains ownership.");

            if(update("UPDATE members SET role=? WHERE uuid=? AND plot=?",role,member,id)!=1)throw new IllegalArgumentException("That player is not a member of this plot.");

            event(id,"ROLE",actor+" member="+member+" role="+role,now);return null;});

    }

    public void leavePlot(String id,UUID actor,long now)throws SQLException {
        tx(()->{Plot p=plot(id);
            if(actor.equals(p.owner()))throw new IllegalArgumentException("You are the renter. Use /plots abandon "+id+" to review closure and any prepaid-week refund.");
            if(update("DELETE FROM members WHERE uuid=? AND plot=?",actor,id)!=1)throw new IllegalArgumentException("You do not belong to this plot.");
            update("DELETE FROM plot_invitations WHERE member=? AND plot=?",actor,id);
            event(id,"LEAVE",actor.toString(),now);return null;});
    }
    public synchronized AbandonmentQuote abandonmentQuote(String id,UUID actor,long now)throws SQLException {
        Plot p=plot(id);owner(p,actor);
        if(!role(id,actor).equals("OWNER") || p.state().equals("AVAILABLE"))
            throw new IllegalArgumentException("That lease has already ended. Contact staff to collect your belongings.");
        UUID lease;
        try(PreparedStatement query=db.prepareStatement("SELECT lease FROM plot_leases WHERE plot=?")){
            bind(query,id);try(ResultSet result=query.executeQuery()){
                if(!result.next())throw new SQLException("Missing lease identity for "+id);
                lease=UUID.fromString(result.getString(1));
            }
        }
        // At an exact rental anniversary the new current week has begun: no refund for it.
        long remaining=Math.max(0,p.paidUntil()-now);
        long weeks=p.state().equals("ACTIVE") && remaining>0?(remaining-1)/WEEK:0;
        return new AbandonmentQuote(id,actor,lease,p.paidUntil(),p.weekly(),p.state(),Math.multiplyExact(weeks,p.weekly()));
    }
    public long abandon(AbandonmentQuote expected,UUID actor,long now)throws SQLException {
        return tx(()->{
            AbandonmentQuote current=abandonmentQuote(expected.plot(),actor,now);
            if(!current.equals(expected))throw new IllegalArgumentException("The lease or refund changed. Review /plots abandon "+expected.plot()+" again before confirming.");
            // Refund, audit, membership removal and closure commit together, or not at all.
            if(current.refund()>0)mutate(actor,current.refund(),"rent-refund",current.plot()+" lease="+current.lease(),now);
            update("INSERT INTO plot_abandonments(lease,plot,owner,time,refund) VALUES(?,?,?,?,?)",current.lease(),current.plot(),actor,now,current.refund());
            event(current.plot(),"ABANDON",actor+" lease="+current.lease()+" refund="+current.refund()+" members="+members(current.plot()),now);
            update("DELETE FROM plot_invitations WHERE plot=?",current.plot());
            update("DELETE FROM plot_absences WHERE plot=?",current.plot());
            update("DELETE FROM members WHERE plot=?",current.plot());
            // Keep the former renter for collection/audit, but remove their direct access.
            update("UPDATE plots SET state='RECLAIM',paid_until=? WHERE id=?",now,current.plot());
            return current.refund();
        });
    }
    public synchronized AbandonmentQuote evictionQuote(String id,long now)throws SQLException {
        Plot p=plot(id);if(p.owner()==null)throw new IllegalArgumentException("This plot has no renter to evict.");
        var base=abandonmentQuote(id,p.owner(),now);
        long refund=evictionRefund(p,now);
        return new AbandonmentQuote(id,base.owner(),base.lease(),base.paidUntil(),base.weekly(),base.state(),refund);
    }
    static long evictionRefund(Plot p,long now){
        if(!p.state().equals("ACTIVE") || p.paidUntil()<=now)return 0;
        var zone=java.time.ZoneId.of("Europe/London");
        var tomorrow=java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1);
        var end=java.time.Instant.ofEpochMilli(p.paidUntil()).atZone(zone).toLocalDate();
        long days=Math.max(0,java.time.temporal.ChronoUnit.DAYS.between(tomorrow,end));
        return java.math.BigDecimal.valueOf(p.weekly()).multiply(java.math.BigDecimal.valueOf(days)).divide(java.math.BigDecimal.valueOf(7),0,java.math.RoundingMode.HALF_UP).longValueExact();
    }
    public long evict(AbandonmentQuote expected,String staff,String reason,long now)throws SQLException {
        if(reason.isBlank())throw new IllegalArgumentException("Give a reason for the eviction.");
        return tx(()->{
            var current=evictionQuote(expected.plot(),now);
            if(!current.equals(expected))throw new IllegalArgumentException("The lease or refund changed. Preview the eviction again.");
            if(current.refund()>0)mutate(current.owner(),current.refund(),"rent-refund",current.plot()+" eviction lease="+current.lease(),now);
            update("INSERT INTO plot_abandonments(lease,plot,owner,time,refund) VALUES(?,?,?,?,?)",current.lease(),current.plot(),current.owner(),now,current.refund());
            event(current.plot(),"EVICT",staff+" reason="+reason+" refund="+current.refund()+" members="+members(current.plot()),now);
            update("DELETE FROM plot_invitations WHERE plot=?",current.plot());
            update("DELETE FROM plot_absences WHERE plot=?",current.plot());
            update("DELETE FROM members WHERE plot=?",current.plot());
            update("UPDATE plots SET state='RECLAIM',paid_until=? WHERE id=?",now,current.plot());
            return current.refund();
        });
    }
    public synchronized boolean abandoned(String id)throws SQLException {
        try(PreparedStatement query=db.prepareStatement("SELECT 1 FROM plot_abandonments a JOIN plot_leases l ON a.lease=l.lease WHERE l.plot=?")){
            bind(query,id);try(ResultSet result=query.executeQuery()){return result.next();}
        }
    }
    public void removeMember(String id,UUID actor,UUID member,long now)throws SQLException {
        tx(()->{Plot p=plot(id);owner(p,actor);if(actor.equals(member))throw new IllegalArgumentException("The renter cannot remove themselves. Use /plots abandon to review closing the shop.");
            update("DELETE FROM members WHERE uuid=? AND plot=?",member,id);

            update("DELETE FROM plot_invitations WHERE member=? AND plot=?",member,id);

            event(id,"REMOVE_MEMBER",member.toString(),now);return null;});

    }

    public synchronized String role(String id,UUID member)throws SQLException {

        try(PreparedStatement p=db.prepareStatement("SELECT role FROM members WHERE uuid=? AND plot=?")){bind(p,member,id);try(ResultSet r=p.executeQuery()){return r.next()?r.getString(1):"NONE";}}

    }

    public synchronized Map<UUID,String> members(String id)throws SQLException {

        Map<UUID,String> result=new HashMap<>();

        try(PreparedStatement p=db.prepareStatement("SELECT uuid,role FROM members WHERE plot=?")) {

            bind(p,id);try(ResultSet r=p.executeQuery()){while(r.next())result.put(UUID.fromString(r.getString(1)),r.getString(2));}

        }

        return Map.copyOf(result);

    }

    private boolean activeMember(String id,long now)throws SQLException {

        try(PreparedStatement p=db.prepareStatement("SELECT 1 FROM members m JOIN accounts a ON a.uuid=m.uuid WHERE m.plot=? AND a.seen>=? LIMIT 1")){

            bind(p,id,now-WEEK);try(ResultSet r=p.executeQuery()){return r.next();}

        }

    }

    public void setAbsence(String id,long expires,String staff,String reason,long now)throws SQLException {

        if(reason.isBlank() || staff.isBlank() || expires!=0 && expires<=now)throw new IllegalArgumentException("Supply a future expiry and an audit reason, or clear the exception.");

        tx(()->{Plot p=plot(id);if(p.owner()==null || p.state().equals("RECLAIM"))throw new IllegalArgumentException("Absence exceptions apply only to rented plots that are not awaiting staff clearance.");
            if(expires==0)update("DELETE FROM plot_absences WHERE plot=?",id);

            else update("INSERT INTO plot_absences(plot,expires) VALUES(?,?) ON CONFLICT(plot) DO UPDATE SET expires=excluded.expires",id,expires);

            event(id,"ABSENCE",staff+" expires="+expires+" reason="+reason,now);return null;

        });

    }

    private boolean approvedAbsence(String id,long now)throws SQLException {

        try(PreparedStatement p=db.prepareStatement("SELECT 1 FROM plot_absences WHERE plot=? AND expires>?")){

            bind(p,id,now);try(ResultSet r=p.executeQuery()){return r.next();}

        }

    }

    public void tick(long now)throws SQLException {

        tick(now,null);

    }

    public void tick(long now,Set<String> managedPlots)throws SQLException {

        tx(()-> {for(Plot p:plots()) {

            if(managedPlots!=null && !managedPlots.contains(p.id()))continue;

            if(p.state().equals("ACTIVE") && now>=p.paidUntil()) {

                // Long downtime never charges for weeks when the shop could not trade.

                if(now>=p.paidUntil()+WEEK){update("UPDATE plots SET state='RECLAIM' WHERE id=?",p.id());event(p.id(),"RECLAIM","Grace expired during downtime",now);continue;}

                long due=Money.prorate(p.weekly(),p.paidUntil()+WEEK-now,WEEK);

                if((activeMember(p.id(),now) || approvedAbsence(p.id(),now)) && account(p.owner()).orElseThrow().cents()>=due){

                    mutate(p.owner(),-due,"rent",p.id(),now);update("UPDATE plots SET paid_until=? WHERE id=?",p.paidUntil()+WEEK,p.id());event(p.id(),"RENEW","Automatic",now);

                }else{update("UPDATE plots SET state='GRACE' WHERE id=?",p.id());event(p.id(),"GRACE","Renewal failed or members inactive",now);}

            }else if(p.state().equals("GRACE") && now>=p.paidUntil()+WEEK){update("UPDATE plots SET state='RECLAIM' WHERE id=?",p.id());event(p.id(),"RECLAIM","Grace expired",now);}

        }return null;});

    }

    public long reopen(String id,UUID actor,long now)throws SQLException {

        return tx(()->{Plot p=plot(id);owner(p,actor);

            if(p.state().equals("ACTIVE") && now<p.paidUntil())throw new IllegalArgumentException("This shop is already open. Use /plots info "+id+" to check its rent.");
            if(!p.state().equals("GRACE") || now>=p.paidUntil()+WEEK)throw new IllegalArgumentException("This plot cannot be reopened; contact staff.");
            long cost=Money.prorate(p.weekly(),p.paidUntil()+WEEK-now,WEEK);

            mutate(actor,-cost,"rent-reopen",id,now);update("UPDATE plots SET state='ACTIVE',paid_until=? WHERE id=?",p.paidUntil()+WEEK,id);event(id,"REOPEN",Long.toString(cost),now);return cost;

        });

    }

    public static int prepaidWeeks(Plot plot,long now){
        long remaining=plot.paidUntil()-now;
        return plot.state().equals("ACTIVE") && remaining>0?(int)((remaining-1)/WEEK):0;
    }
    public record StorageNote(long time,String detail) {}
    public synchronized List<StorageNote> storageNotes(String id)throws SQLException {
        plot(id);List<StorageNote> notes=new ArrayList<>();
        try(var p=db.prepareStatement("SELECT time,detail FROM plot_events WHERE plot=? AND kind='CLEARED' ORDER BY id DESC LIMIT 10")){
            bind(p,id);try(var rows=p.executeQuery()){while(rows.next())notes.add(new StorageNote(rows.getLong(1),rows.getString(2)));}
        }
        return notes;
    }
    public void prepay(String id,UUID actor,int weeks,long now)throws SQLException {

        if(weeks<1 || weeks>4)throw new IllegalArgumentException("Prepay one to four weeks.");

        tx(()->{Plot p=plot(id);owner(p,actor);

            long until=Math.addExact(p.paidUntil(),WEEK*weeks);

            if(!p.state().equals("ACTIVE") || now>=p.paidUntil())throw new IllegalArgumentException("Reopen the plot first.");

            // Four additional weeks beyond the current rental week, not unlimited repeated calls.

            if(until-now>WEEK*5)throw new IllegalArgumentException("You already have "+prepaidWeeks(p,now)+" future prepaid weeks. The limit is four; use /plots info "+id+" for your paid-through date.");
            mutate(actor,-Math.multiplyExact(p.weekly(),weeks),"rent-prepay",id,now);update("UPDATE plots SET paid_until=? WHERE id=?",until,id);event(id,"PREPAY",Integer.toString(weeks),now);return null;

        });

    }

    public void confirmCleared(String id,String staff,String collectionReference,long now)throws SQLException {

        if(collectionReference.isBlank())throw new IllegalArgumentException("Describe where the former renter's belongings are stored, for example: staff storage, chest A3.");
        tx(()->{Plot p=plot(id);if(!p.state().equals("RECLAIM"))throw new IllegalArgumentException("This plot is not awaiting staff clearance.");
            event(id,"CLEARED",staff+" owner="+p.owner()+" collection="+collectionReference,now);
            update("DELETE FROM plot_names WHERE plot=?",id);
            update("DELETE FROM plot_invitations WHERE plot=?",id);

            update("DELETE FROM plot_absences WHERE plot=?",id);

            update("DELETE FROM members WHERE plot=?",id);update("UPDATE plots SET owner=NULL,paid_until=0,state='AVAILABLE' WHERE id=?",id);return null;});

    }

    public synchronized boolean canTrade(String id,long now)throws SQLException {Plot p=plot(id);return p.state().equals("ACTIVE")&&now<p.paidUntil();}

    EconomyReports.Snapshot economyReport(long since,long until)throws SQLException {
        return tx(()->EconomyReports.read(db,since,until));
    }

    public synchronized void backup(Path destination)throws SQLException {

        // SQLite creates a standalone consistent snapshot, including committed WAL data.

        if(java.nio.file.Files.exists(destination))throw new IllegalArgumentException("Backup file already exists.");

        try(PreparedStatement p=db.prepareStatement("VACUUM INTO ?")){p.setString(1,destination.toAbsolutePath().toString());p.execute();}

    }

    private boolean nikoGateEnabled;
    private String nikoGateWeek;
    synchronized void configureNikoGate(boolean enabled,boolean testImmediately,long now)throws SQLException {
        nikoGateEnabled=enabled;
        if(!enabled)return;
        tx(()->{
            String start=QuestPlan.week(testImmediately?now:QuestPlan.week(now).nextReset()).id();
            update("INSERT OR IGNORE INTO metadata VALUES('niko-gate-start',?)",start);
            try(var s=db.createStatement();var r=s.executeQuery("SELECT value FROM metadata WHERE key='niko-gate-start'")){r.next();nikoGateWeek=r.getString(1);}
            return null;
        });
    }
    synchronized boolean questAccepted(UUID player,String week)throws SQLException {
        if(!nikoGateEnabled || week.compareTo(nikoGateWeek)<0)return true;
        try(var p=db.prepareStatement("SELECT 1 FROM niko_acceptance WHERE uuid=? AND week=?")){
            bind(p,player,week);try(var r=p.executeQuery()){return r.next();}
        }
    }
    boolean acceptNiko(UUID player,String week,String actor,long now)throws SQLException {
        return tx(()->{
            if(!QuestPlan.week(now).id().equals(week))throw new IllegalArgumentException("The quests have refreshed. Speak to Niko again.");
            return update("INSERT OR IGNORE INTO niko_acceptance(uuid,week,time,actor) VALUES(?,?,?,?)",player,week,now,actor)==1;
        });
    }
    record VendorOffer(String visit,String design,String name,byte[] item,long price,int allocation,int stock,int cap) {}
    record VendorReceipt(UUID id,UUID buyer,String visit,String design,int quantity,long cents,String state,long time,String resolution) {}
    void openVendorVisit(VendorSchedule.Visit visit,List<VendorOffer> offers,String season)throws SQLException {
        if(offers.size()!=6 || offers.stream().map(VendorOffer::design).distinct().count()!=6)throw new IllegalArgumentException("Expected six unique products.");
        tx(()->{
            if(update("INSERT OR IGNORE INTO pip_visits(id,arrival,restock,departure,season) VALUES(?,?,?,?,?)",visit.id(),visit.arrival(),visit.restock(),visit.departure(),season)==0)return null;
            for(var o:offers){
                if(!o.visit().equals(visit.id()) || o.price()<1 || o.price()>Money.MAX || o.allocation()<1 || o.cap()<0 || o.item().length==0)throw new IllegalArgumentException("Invalid vendor offer.");
                update("INSERT INTO pip_offers(visit,design,name,item,price,allocation,stock,cap) VALUES(?,?,?,?,?,?,?,?)",visit.id(),o.design(),o.name(),o.item(),o.price(),o.allocation(),o.allocation(),o.cap());
            }
            return null;
        });
    }
    synchronized List<VendorOffer> vendorOffers(String visit)throws SQLException {
        var list=new ArrayList<VendorOffer>();
        try(var p=db.prepareStatement("SELECT * FROM pip_offers WHERE visit=? ORDER BY rowid")){
            bind(p,visit);try(var r=p.executeQuery()){while(r.next())list.add(new VendorOffer(visit,r.getString("design"),r.getString("name"),r.getBytes("item"),r.getLong("price"),r.getInt("allocation"),r.getInt("stock"),r.getInt("cap")));}
        }return List.copyOf(list);
    }
    synchronized Set<String> previousVendorDesigns(String visit)throws SQLException {
        var ids=new HashSet<String>();
        try(var p=db.prepareStatement("SELECT design FROM pip_offers WHERE visit=(SELECT MAX(id) FROM pip_visits WHERE id<?)")){
            bind(p,visit);try(var r=p.executeQuery()){while(r.next())ids.add(r.getString(1));}
        }return ids;
    }
    void restockVendor(String visit,long now)throws SQLException {
        tx(()->{
            if(update("UPDATE pip_visits SET restocked=1 WHERE id=? AND restocked=0 AND restock<=? AND departure>?",visit,now,now)==1)
                update("UPDATE pip_offers SET stock=stock+allocation WHERE visit=?",visit);
            return null;
        });
    }
    synchronized int vendorBought(UUID player,String visit,String design)throws SQLException {
        try(var p=db.prepareStatement("SELECT COALESCE(SUM(quantity),0) FROM pip_receipts WHERE buyer=? AND visit=? AND design=? AND state!='REFUNDED'")){
            bind(p,player,visit,design);try(var r=p.executeQuery()){r.next();return r.getInt(1);}
        }
    }
    UUID beginVendorPurchase(UUID buyer,String visit,String design,int quantity,byte[] before,byte[] after,long now)throws SQLException {
        if(quantity<1 || quantity>64 || before.length!=32 || after.length!=32)throw new IllegalArgumentException("Invalid purchase plan.");
        return tx(()->{
            try(var p=db.prepareStatement("SELECT 1 FROM pip_visits WHERE id=? AND arrival<=? AND departure>?")){
                bind(p,visit,now,now);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Pip has left. Come back for her next visit.");}
            }
            restockVendorInternal(visit,now);
            try(var p=db.prepareStatement("SELECT 1 FROM pip_receipts WHERE buyer=? AND state='REVIEW'")){
                bind(p,buyer);try(var r=p.executeQuery()){if(r.next())throw new IllegalArgumentException("An earlier purchase needs staff review before you buy again.");}
            }
            var offer=vendorOffers(visit).stream().filter(o->o.design().equals(design)).findFirst().orElseThrow(()->new IllegalArgumentException("That product is no longer available."));
            if(quantity>offer.stock())throw new IllegalArgumentException("There aren't that many left. Reopen Pip's shop.");
            if(offer.cap()>0 && vendorBought(buyer,visit,design)+quantity>offer.cap())throw new IllegalArgumentException("That exceeds your allowance for this visit.");
            long total=Math.multiplyExact(offer.price(),quantity);UUID id=UUID.randomUUID();
            mutate(buyer,-total,"vendor",id.toString(),now);
            update("UPDATE pip_offers SET stock=stock-? WHERE visit=? AND design=?",quantity,visit,design);
            update("INSERT INTO pip_receipts(id,buyer,visit,design,quantity,cents,before_hash,after_hash,time) VALUES(?,?,?,?,?,?,?,?,?)",id,buyer,visit,design,quantity,total,before,after,now);
            return id;
        });
    }
    private void restockVendorInternal(String visit,long now)throws SQLException {
        if(update("UPDATE pip_visits SET restocked=1 WHERE id=? AND restocked=0 AND restock<=? AND departure>?",visit,now,now)==1)
            update("UPDATE pip_offers SET stock=stock+allocation WHERE visit=?",visit);
    }
    void finishVendorPurchase(UUID receipt)throws SQLException {
        tx(()->{if(update("UPDATE pip_receipts SET state='DELIVERED' WHERE id=? AND state='REVIEW'",receipt)!=1)throw new IllegalArgumentException("Purchase is not awaiting delivery.");return null;});
    }
    synchronized List<VendorReceipt> vendorReviews()throws SQLException {
        var list=new ArrayList<VendorReceipt>();
        try(var s=db.createStatement();var r=s.executeQuery("SELECT * FROM pip_receipts WHERE state='REVIEW' ORDER BY time LIMIT 50")){
            while(r.next())list.add(new VendorReceipt(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("buyer")),r.getString("visit"),r.getString("design"),r.getInt("quantity"),r.getLong("cents"),r.getString("state"),r.getLong("time"),r.getString("resolution")));
        }return list;
    }
    void resolveVendor(UUID receipt,boolean delivered,String actor,String reason,long now)throws SQLException {
        if(reason.isBlank())throw new IllegalArgumentException("Record why delivery or non-delivery is certain.");
        tx(()->{
            var r=vendorReviews().stream().filter(x->x.id().equals(receipt)).findFirst().orElseThrow(()->new IllegalArgumentException("Receipt is not awaiting review."));
            if(!delivered){
                mutate(r.buyer(),r.cents(),"vendor-refund",receipt.toString(),now);
                update("UPDATE pip_offers SET stock=stock+? WHERE visit=? AND design=?",r.quantity(),r.visit(),r.design());
            }
            update("UPDATE pip_receipts SET state=?,resolution=? WHERE id=?",delivered?"DELIVERED":"REFUNDED",actor+": "+reason+" @ "+now,receipt);return null;
        });
    }

    @Override public synchronized void close()throws SQLException { db.close(); }

}
