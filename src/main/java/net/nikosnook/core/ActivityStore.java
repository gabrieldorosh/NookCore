package net.nikosnook.core;

import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Separate measurement database: never writes balances, rewards or shop receipts. */
final class ActivityStore implements AutoCloseable {
    record Key(String day, UUID player, String metric) {}
    record Row(String day, UUID player, String metric, long amount) {}
    private final Connection db;
    ActivityStore(Path path) throws SQLException {
        db=DriverManager.getConnection("jdbc:sqlite:"+path.toAbsolutePath());
        try(var s=db.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");s.execute("PRAGMA busy_timeout=1000");
            s.execute("CREATE TABLE IF NOT EXISTS activity(day TEXT NOT NULL,player TEXT NOT NULL,metric TEXT NOT NULL,amount INTEGER NOT NULL CHECK(amount>=0),PRIMARY KEY(day,player,metric))");
        }
    }
    synchronized void add(Map<Key,Long> batch, LocalDate today) throws SQLException {
        for(var e:batch.entrySet()) {
            LocalDate.parse(e.getKey().day());Objects.requireNonNull(e.getKey().player());
            if(!e.getKey().metric().matches("[a-z_]+(?::[A-Z_]+)?") || e.getValue()<0)throw new IllegalArgumentException("Invalid activity counter");
        }
        db.setAutoCommit(false);
        try {
            try(var p=db.prepareStatement("INSERT INTO activity(day,player,metric,amount) VALUES(?,?,?,?) ON CONFLICT(day,player,metric) DO UPDATE SET amount=amount+excluded.amount")) {
                for(var e:batch.entrySet()){p.setString(1,e.getKey().day());p.setString(2,e.getKey().player().toString());p.setString(3,e.getKey().metric());p.setLong(4,e.getValue());p.addBatch();}p.executeBatch();
            }
            try(var p=db.prepareStatement("DELETE FROM activity WHERE day<?")){p.setString(1,today.minusDays(89).toString());p.executeUpdate();}
            db.commit();
        } catch(SQLException|RuntimeException e) {db.rollback();throw e;} finally {db.setAutoCommit(true);}
    }
    synchronized List<Row> read(LocalDate first,LocalDate last) throws SQLException {
        if(last.isBefore(first) || first.plusDays(89).isBefore(last))throw new IllegalArgumentException("Choose 1 to 90 calendar days.");
        var rows=new ArrayList<Row>();
        try(var p=db.prepareStatement("SELECT day,player,metric,amount FROM activity WHERE day>=? AND day<=? ORDER BY day,player,metric")) {
            p.setString(1,first.toString());p.setString(2,last.toString());
            try(var r=p.executeQuery()){while(r.next())rows.add(new Row(r.getString(1),UUID.fromString(r.getString(2)),r.getString(3),r.getLong(4)));}
        }return List.copyOf(rows);
    }
    @Override public synchronized void close() throws SQLException {db.close();}
}
