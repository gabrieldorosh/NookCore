package net.nikosnook.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

/** Read-only staff summaries. Receipt snapshots, not current shop terms, determine prices. */
final class EconomyReports {
    private static final Set<String> INTERNAL=Set.of("transfer","native-shop-buy","native-shop-sale");
    private static final Set<String> SOURCES=Set.of("award","staff","rent-refund");
    private static final Set<String> SINKS=Set.of("rent","rent-prepay","rent-reopen","bunny-time","staff");
    record Flow(String kind,long added,long removed) { long net(){return added-removed;} }
    record Market(byte[] item,long sales,long items,long cents) {
        Market { item=item.clone(); }
        @Override public byte[] item(){return item.clone();}
        String unitPrice(){return "\u20a6"+BigDecimal.valueOf(cents,2).divide(BigDecimal.valueOf(items),4,RoundingMode.HALF_UP).toPlainString();}
    }
    record Snapshot(long since,long until,long accounts,long supply,List<Flow> flows,List<Market> market,long variants,long pending,long review,long barter) {
        Snapshot { flows=List.copyOf(flows);market=List.copyOf(market); }
        long issued(){return flows.stream().filter(f->SOURCES.contains(f.kind())).mapToLong(Flow::added).sum();}
        long sunk(){return flows.stream().filter(f->SINKS.contains(f.kind())).mapToLong(Flow::removed).sum();}
        long net(){return flows.stream().mapToLong(Flow::net).sum();}
        long internalNet(){return flows.stream().filter(f->INTERNAL.contains(f.kind())).mapToLong(Flow::net).sum();}
    }
    private static final String SALES=" FROM native_trade_receipts t WHERE t.time>=? AND t.time<? AND t.buyer<>t.seller AND NOT EXISTS (SELECT 1 FROM native_stock_returns r WHERE r.receipt=t.id)";
    static Snapshot read(Connection db,long since,long until)throws SQLException {
        if(since<0 || until<=since)throw new IllegalArgumentException("Invalid report interval.");
        long accounts,supply;
        try(var p=db.createStatement();var r=p.executeQuery("SELECT COUNT(*),COALESCE(SUM(cents),0) FROM accounts")){r.next();accounts=r.getLong(1);supply=r.getLong(2);}
        List<Flow> flows=new ArrayList<>();
        try(var p=period(db,"SELECT kind,SUM(CASE WHEN delta>0 THEN delta ELSE 0 END),SUM(CASE WHEN delta<0 THEN -delta ELSE 0 END) FROM ledger WHERE time>=? AND time<? GROUP BY kind ORDER BY kind",since,until);var r=p.executeQuery()){
            while(r.next())flows.add(new Flow(r.getString(1),r.getLong(2),r.getLong(3)));
        }
        List<Market> market=new ArrayList<>();
        try(var p=period(db,"SELECT item,COUNT(*),SUM(quantity),SUM(cents)"+SALES+" AND t.delivery='DELIVERED' AND t.cents>0 GROUP BY item ORDER BY SUM(cents) DESC,hex(item) LIMIT 10",since,until);var r=p.executeQuery()){
            while(r.next())market.add(new Market(r.getBytes(1),r.getLong(2),r.getLong(3),r.getLong(4)));
        }
        long variants=count(db,"SELECT COUNT(*) FROM (SELECT item"+SALES+" AND t.delivery='DELIVERED' AND t.cents>0 GROUP BY item)",since,until);
        long pending=count(db,"SELECT COUNT(*)"+SALES+" AND t.delivery='PENDING'",since,until);
        long review=count(db,"SELECT COUNT(*)"+SALES+" AND t.delivery='REVIEW'",since,until);
        long barter=count(db,"SELECT COUNT(*)"+SALES+" AND t.delivery='DELIVERED' AND t.cents=0",since,until);
        return new Snapshot(since,until,accounts,supply,flows,market,variants,pending,review,barter);
    }
    private static PreparedStatement period(Connection db,String sql,long since,long until)throws SQLException {
        var p=db.prepareStatement(sql);p.setLong(1,since);p.setLong(2,until);return p;
    }
    private static long count(Connection db,String sql,long since,long until)throws SQLException {
        try(var p=period(db,sql,since,until);var r=p.executeQuery()){r.next();return r.getLong(1);}
    }
    static int days(String[] args){
        if(args.length<1 || args.length>2)throw new IllegalArgumentException("Use /nooks report [days], from 1 to 90.");
        try{int days=args.length==1?7:Integer.parseInt(args[1]);if(days<1 || days>90)throw new NumberFormatException();return days;}
        catch(NumberFormatException e){throw new IllegalArgumentException("Choose a whole number of days from 1 to 90.");}
    }
    static boolean execute(CommandSender sender,String[] args,NookStore store,boolean healthy,long now)throws SQLException {
        if(!sender.hasPermission("nookcore.admin")){sender.sendMessage(NookUi.problem("Nooks","This report is for admins."));return true;}
        if(!healthy){sender.sendMessage(NookUi.problem("Nooks","The economy is paused. Check the server log before reporting."));return true;}
        try{
            int days=days(args);var report=store.economyReport(Math.max(0,now-Duration.ofDays(days).toMillis()),now+1);
            sender.sendMessage(NookUi.heading("Nooks - Economy report"));
            var format=DateTimeFormatter.ofPattern("dd MMM HH:mm z",Locale.UK).withZone(ZoneId.of("Europe/London"));
            sender.sendMessage(NookUi.text(format.format(Instant.ofEpochMilli(report.since()))+" to "+format.format(Instant.ofEpochMilli(now))+" (rolling "+days+" days)"));
            sender.sendMessage(NookUi.text("Nooks in circulation: "+Money.format(report.supply())+" across "+report.accounts()+" accounts."));
            sender.sendMessage(NookUi.text("Created / refunded: "+Money.format(report.issued())));
            sender.sendMessage(NookUi.text("Spent / removed: "+Money.format(report.sunk())));
            sender.sendMessage(NookUi.text("Net change: "+Money.format(report.net())));
            for(var flow:report.flows())if(!INTERNAL.contains(flow.kind()))sender.sendMessage(NookUi.text(flow.kind().replace('-',' ')+": +"+Money.format(flow.added())+" / -"+Money.format(flow.removed())));
            sender.sendMessage(NookUi.text("Transfers and native-shop payments are excluded from issuance. External integration credits/debits are shown separately."));
            if(report.internalNet()!=0)sender.sendMessage(NookUi.problem("Nooks","Transfer/shop ledger entries do not net to zero in this interval: "+Money.format(report.internalNet())+". Investigate before drawing conclusions."));
            sender.sendMessage(NookUi.heading("Shop sales — top 10 by Nooks value"));
            int index=0;
            for(var item:report.market())sender.sendMessage(NookUi.text((++index)+". "+itemName(item.item())+": "+item.items()+" items, "+item.sales()+" sales, "+Money.format(item.cents())+", weighted "+item.unitPrice()+"/item."));
            if(report.market().isEmpty())sender.sendMessage(NookUi.text("No delivered Nooks sales in this interval."));
            sender.sendMessage(NookUi.text(report.variants()+" exact item variants; "+report.pending()+" pending and "+report.review()+" review sales excluded; "+report.barter()+" delivered item-payment sales excluded from prices."));
            sender.sendMessage(NookUi.text("Separate variants may share a material name. Withdrawals/returns and generic ChestShop trades are excluded. This report changes no balances or receipts."));
        }catch(IllegalArgumentException e){sender.sendMessage(NookUi.problem("Nooks",e.getMessage()));}
        return true;
    }
    private static String itemName(byte[] snapshot){
        try{return NativeShops.name(snapshot);}
        catch(RuntimeException e){return "unreadable item variant";}
    }
}
