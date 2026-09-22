package net.nikosnook.core;

import java.nio.file.Path;
import java.sql.*;
import java.time.Duration;
import java.util.*;

/** Serialized SQLite transactions; every balance mutation and its audit entry commit together. */
public final class NookStore implements AutoCloseable {
    public static final long WEEK = Duration.ofDays(7).toMillis();
    private final Connection db;
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
            s.execute("CREATE TABLE IF NOT EXISTS plots (id TEXT PRIMARY KEY, weekly INTEGER NOT NULL CHECK(weekly>0), owner TEXT REFERENCES accounts(uuid), paid_until INTEGER NOT NULL DEFAULT 0, state TEXT NOT NULL DEFAULT 'AVAILABLE' CHECK(state IN ('AVAILABLE','ACTIVE','GRACE','RECLAIM')))");
            s.execute("CREATE TABLE IF NOT EXISTS members (uuid TEXT PRIMARY KEY REFERENCES accounts(uuid), plot TEXT NOT NULL REFERENCES plots(id), role TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS plot_events (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, plot TEXT NOT NULL, kind TEXT NOT NULL, detail TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS plot_invitations (token TEXT PRIMARY KEY, plot TEXT NOT NULL REFERENCES plots(id), inviter TEXT NOT NULL REFERENCES accounts(uuid), member TEXT NOT NULL REFERENCES accounts(uuid), role TEXT NOT NULL CHECK(role IN ('BUILD','STOCK','BUILD_STOCK')), expires INTEGER NOT NULL, UNIQUE(plot,member))");
            s.execute("CREATE TABLE IF NOT EXISTS plot_absences (plot TEXT PRIMARY KEY REFERENCES plots(id), expires INTEGER NOT NULL)");

            s.execute("CREATE TABLE IF NOT EXISTS plot_leases (plot TEXT PRIMARY KEY REFERENCES plots(id), lease TEXT NOT NULL UNIQUE)");
            s.execute("CREATE TABLE IF NOT EXISTS plot_abandonments (lease TEXT PRIMARY KEY, plot TEXT NOT NULL REFERENCES plots(id), owner TEXT NOT NULL REFERENCES accounts(uuid), time INTEGER NOT NULL, refund INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS quest_rotations (week TEXT PRIMARY KEY, definitions TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS quest_progress (uuid TEXT NOT NULL REFERENCES accounts(uuid), week TEXT NOT NULL REFERENCES quest_rotations(week), goal TEXT NOT NULL, progress INTEGER NOT NULL, PRIMARY KEY(uuid,week,goal))");
            s.execute("CREATE TABLE IF NOT EXISTS quest_visits (uuid TEXT NOT NULL REFERENCES accounts(uuid), week TEXT NOT NULL REFERENCES quest_rotations(week), goal TEXT NOT NULL, biome TEXT NOT NULL, PRIMARY KEY(uuid,week,goal,biome))");
            // Give existing leases an identity once; a new rental always replaces it.
            for(Plot plot:plots())if(plot.owner()!=null)
                update("INSERT OR IGNORE INTO plot_leases(plot,lease) VALUES(?,?)",plot.id(),UUID.randomUUID());
        }
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
    public record QuestUpdate(QuestPlan.Goal goal,int progress,boolean paid) {}
    public List<QuestPlan.Goal> progressQuests(UUID player,String week,String kind,String target,String unique,long now)throws SQLException {
        return advanceQuests(player,week,kind,target,unique,now).stream().filter(QuestUpdate::paid).map(QuestUpdate::goal).toList();
    }
    public List<QuestUpdate> advanceQuests(UUID player,String week,String kind,String target,String unique,long now)throws SQLException {
        // Load the authoritative snapshot, never accept caller-supplied payout amounts.
        return tx(()->{
            List<QuestPlan.Goal> goals;
            try(var p=db.prepareStatement("SELECT definitions FROM quest_rotations WHERE week=?")){
                bind(p,week);try(var rows=p.executeQuery()){if(!rows.next())throw new IllegalArgumentException("Unknown quest rotation");goals=QuestPlan.decode(rows.getString(1));}
            }
            if(!QuestPlan.week(now).id().equals(week))throw new IllegalArgumentException("Quest week has changed");
            var updates=new ArrayList<QuestUpdate>();
            for(var g:goals){
                if(!g.kind().equals(kind) || !(g.target().equals(target) || g.target().equals("ANY")))continue;
                int before=questProgress(player,week,g.id());if(before>=g.amount())continue;
                if(kind.equals("BIOME")){
                    if(unique==null || unique.isBlank())throw new IllegalArgumentException("Missing biome identity");
                    if(update("INSERT OR IGNORE INTO quest_visits VALUES(?,?,?,?)",player,week,g.id(),unique)==0)continue;
                }
                int after=before+1;
                update("INSERT INTO quest_progress VALUES(?,?,?,?) ON CONFLICT(uuid,week,goal) DO UPDATE SET progress=excluded.progress",player,week,g.id(),after);
                boolean paid=after==g.amount() && awardInternal(player,"quest:"+week+":"+g.id(),g.reward(),now);
                updates.add(new QuestUpdate(g,after,paid));
            }
            return updates;
        });
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
        if(next>Money.MAX) throw new IllegalArgumentException("Balance limit reached.");
        update("UPDATE accounts SET cents=? WHERE uuid=?",next,id);
        update("INSERT INTO ledger(time,uuid,delta,kind,reference) VALUES(?,?,?,?,?)",now,id,delta,kind,ref);
    }
    public void transfer(UUID from,UUID to,long cents,long now) throws SQLException {
        if(cents<=0 || cents>Money.MAX || from.equals(to)) throw new IllegalArgumentException("Choose another player and a positive amount.");
        tx(()-> { String ref=UUID.randomUUID().toString(); mutate(from,-cents,"transfer",ref,now); mutate(to,cents,"transfer",ref,now); return null; });
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
            requireNoMembership(actor);
            update("INSERT INTO members(uuid,plot,role) VALUES(?,?,'OWNER')",actor,id);
            mutate(actor,-p.weekly(),"rent",id,now);
            update("UPDATE plots SET owner=?,paid_until=?,state='ACTIVE' WHERE id=?",actor,Math.addExact(now,WEEK),id);

            update("INSERT INTO plot_leases(plot,lease) VALUES(?,?) ON CONFLICT(plot) DO UPDATE SET lease=excluded.lease",id,UUID.randomUUID());
            event(id,"RENT",actor.toString(),now);return null;
        });
    }
    private void requireNoMembership(UUID member)throws SQLException {
        try(PreparedStatement p=db.prepareStatement("SELECT 1 FROM members WHERE uuid=?")) {
            bind(p,member);try(ResultSet r=p.executeQuery()){if(r.next())throw new IllegalArgumentException("That player already belongs to a plot.");}
        }
    }
    private static void memberRole(String role) {
        if(!Set.of("BUILD","STOCK","BUILD_STOCK").contains(role))throw new IllegalArgumentException("Unknown member role.");
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
            if(actor.equals(p.owner()))throw new IllegalArgumentException("You are the renter. Use /nookplots abandon "+id+" to review closure and any prepaid-week refund.");
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
            if(!current.equals(expected))throw new IllegalArgumentException("The lease or refund changed. Review /nookplots abandon "+expected.plot()+" again before confirming.");
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
    public synchronized boolean abandoned(String id)throws SQLException {
        try(PreparedStatement query=db.prepareStatement("SELECT 1 FROM plot_abandonments a JOIN plot_leases l ON a.lease=l.lease WHERE l.plot=?")){
            bind(query,id);try(ResultSet result=query.executeQuery()){return result.next();}
        }
    }
    public void removeMember(String id,UUID actor,UUID member,long now)throws SQLException {
        tx(()->{Plot p=plot(id);owner(p,actor);if(actor.equals(member))throw new IllegalArgumentException("Owner cannot leave without staff-assisted closure.");
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
        tx(()->{Plot p=plot(id);if(p.owner()==null || p.state().equals("RECLAIM"))throw new IllegalArgumentException("An absence exception requires a current lease before reclamation.");
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
            if(!p.state().equals("GRACE") || now>=p.paidUntil()+WEEK)throw new IllegalArgumentException("This plot cannot be reopened; contact staff.");
            long cost=Money.prorate(p.weekly(),p.paidUntil()+WEEK-now,WEEK);
            mutate(actor,-cost,"rent-reopen",id,now);update("UPDATE plots SET state='ACTIVE',paid_until=? WHERE id=?",p.paidUntil()+WEEK,id);event(id,"REOPEN",Long.toString(cost),now);return cost;
        });
    }
    public void prepay(String id,UUID actor,int weeks,long now)throws SQLException {
        if(weeks<1 || weeks>4)throw new IllegalArgumentException("Prepay one to four weeks.");
        tx(()->{Plot p=plot(id);owner(p,actor);
            long until=Math.addExact(p.paidUntil(),WEEK*weeks);
            if(!p.state().equals("ACTIVE") || now>=p.paidUntil())throw new IllegalArgumentException("Reopen the plot first.");
            // Four additional weeks beyond the current rental week, not unlimited repeated calls.
            if(until-now>WEEK*5)throw new IllegalArgumentException("At most four future weeks may be prepaid.");
            mutate(actor,-Math.multiplyExact(p.weekly(),weeks),"rent-prepay",id,now);update("UPDATE plots SET paid_until=? WHERE id=?",until,id);event(id,"PREPAY",Integer.toString(weeks),now);return null;
        });
    }
    public void confirmCleared(String id,String staff,String collectionReference,long now)throws SQLException {
        if(collectionReference.isBlank())throw new IllegalArgumentException("Supply the indefinite-storage collection reference.");
        tx(()->{Plot p=plot(id);if(!p.state().equals("RECLAIM"))throw new IllegalArgumentException("Plot is not awaiting reclamation.");
            event(id,"CLEARED",staff+" owner="+p.owner()+" collection="+collectionReference,now);
            update("DELETE FROM plot_invitations WHERE plot=?",id);
            update("DELETE FROM plot_absences WHERE plot=?",id);
            update("DELETE FROM members WHERE plot=?",id);update("UPDATE plots SET owner=NULL,paid_until=0,state='AVAILABLE' WHERE id=?",id);return null;});
    }
    public synchronized boolean canTrade(String id,long now)throws SQLException {Plot p=plot(id);return p.state().equals("ACTIVE")&&now<p.paidUntil();}
    public synchronized void backup(Path destination)throws SQLException {
        // SQLite creates a standalone consistent snapshot, including committed WAL data.
        if(java.nio.file.Files.exists(destination))throw new IllegalArgumentException("Backup file already exists.");
        try(PreparedStatement p=db.prepareStatement("VACUUM INTO ?")){p.setString(1,destination.toAbsolutePath().toString());p.execute();}
    }
    @Override public synchronized void close()throws SQLException { db.close(); }
}
