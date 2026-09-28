package net.nikosnook.core;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class EconomyReportsTest {
    @TempDir Path dir;NookStore store;UUID seller=UUID.randomUUID(),buyer=UUID.randomUUID();
    @BeforeEach void setup()throws Exception {store=new NookStore(dir.resolve("db"));}
    @AfterEach void close()throws Exception {store.close();}
    void setupShop()throws Exception {
        store.join(seller,"Seller",0,10000);store.join(buyer,"Buyer",0,10000);store.definePlot("one",100);store.rent("one",seller,1);
    }
    NativeShop.Offer offer(byte[] item,int bundle,long cents)throws Exception {
        var offer=store.createNativeOffer("one",seller,item,bundle,cents,2);store.creditNativeStock(UUID.randomUUID(),offer.id(),seller,64,2);return offer;
    }
    UUID buy(NativeShop.Offer offer,long time,boolean delivered)throws Exception {
        UUID id=UUID.randomUUID();store.settleNativePurchase(id,offer.id(),buyer,offer.revision(),time);
        if(delivered)deliver(id,buyer);return id;
    }
    void deliver(UUID receipt,UUID recipient)throws Exception {
        byte[] before=new byte[32],after=new byte[32];after[0]=1;
        store.planNativeDelivery(receipt,recipient,before,after);store.confirmNativeDelivery(receipt,recipient,after);
    }
    @Test void emptyEconomyHasZeroTotals()throws Exception {
        var report=store.economyReport(0,100);assertEquals(0,report.supply());assertEquals(0,report.accounts());assertEquals(0,report.issued());assertTrue(report.market().isEmpty());
    }
    @Test void transfersAndSharedShopProceedsAreNotNewMoney()throws Exception {
        setupShop();UUID co=UUID.randomUUID();store.join(co,"CoOwner",0,10000);var invitation=store.invite("one",seller,co,"STOCK",2);store.acceptInvitation(invitation.token(),co,2);
        store.transfer(buyer,seller,500,3);buy(offer(new byte[]{1},4,250),4,true);
        var report=store.economyReport(0,10);assertEquals(30000,report.issued());assertEquals(100,report.sunk());assertEquals(29900,report.supply());assertEquals(29900,report.net());assertEquals(0,report.internalNet());
    }
    @Test void weightedPriceUsesItemQuantitiesAndHistoricalReceipts()throws Exception {
        setupShop();var first=offer(new byte[]{1},4,250);var second=offer(new byte[]{1},16,640);
        buy(first,3,true);buy(second,4,true);store.priceNativeOffer(first.id(),seller,900,5);
        var row=store.economyReport(0,10).market().getFirst();assertEquals(2,row.sales());assertEquals(20,row.items());assertEquals(890,row.cents());assertEquals("\u20a60.4450",row.unitPrice());
    }
    @Test void pendingReviewAndWithdrawalsNeverBecomeSalesPrices()throws Exception {
        setupShop();var offer=offer(new byte[]{1},4,250);
        UUID pending=buy(offer,3,false);UUID review=buy(offer,4,false);byte[] before=new byte[32],after=new byte[32];after[0]=2;store.planNativeDelivery(review,buyer,before,after);
        var withdrawal=store.reserveNativeStockWithdrawal(UUID.randomUUID(),offer.id(),seller,3,5);deliver(withdrawal.id(),seller);
        var report=store.economyReport(0,10);assertEquals(1,report.pending());assertEquals(1,report.review());assertEquals(0,report.barter());assertTrue(report.market().isEmpty());
        assertEquals("PENDING",store.nativeDeliveryState(pending));assertEquals("REVIEW",store.nativeDeliveryState(review));
    }
    @Test void exactVariantsRemainSeparateAndSnapshotArraysAreDefensive()throws Exception {
        setupShop();buy(offer(new byte[]{1},4,250),3,true);buy(offer(new byte[]{2},4,250),4,true);
        var report=store.economyReport(0,10);assertEquals(2,report.variants());assertEquals(2,report.market().size());
        byte[] bytes=report.market().getFirst().item();bytes[0]=99;assertNotEquals(99,report.market().getFirst().item()[0]);
    }
    @Test void reportingUsesInclusiveStartAndExclusiveEnd()throws Exception {
        setupShop();buy(offer(new byte[]{1},4,250),100,true);
        assertTrue(store.economyReport(0,100).market().isEmpty());assertEquals(1,store.economyReport(100,101).market().size());assertTrue(store.economyReport(101,102).market().isEmpty());
    }
    @Test void externalCreditsAreShownWithoutClaimingTheyAreIssuance()throws Exception {
        setupShop();store.integrationAdjustment(buyer,123,"external-provider",3);store.adjust(seller,50,"staff","gift",4);store.adjust(buyer,-20,"staff","sink",4);
        var report=store.economyReport(0,10);assertEquals(20050,report.issued());assertEquals(120,report.sunk());assertEquals(20053,report.net());assertTrue(report.flows().stream().anyMatch(f->f.kind().equals("integration") && f.added()==123));
    }
    @Test void repeatedReportsDoNotWriteDatabaseOrChangeIncomeNotifications()throws Exception {
        setupShop();buy(offer(new byte[]{1},4,250),3,true);long before=store.offlineIncome(seller).sales();
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("db"));var s=c.createStatement()){
            long version;try(var r=s.executeQuery("PRAGMA data_version")){r.next();version=r.getLong(1);}
            store.economyReport(0,10);store.economyReport(0,10);
            try(var r=s.executeQuery("PRAGMA data_version")){r.next();assertEquals(version,r.getLong(1));}
        }
        assertEquals(before,store.offlineIncome(seller).sales());
    }
    @Test void reportRangeAndArgumentShapeAreBounded(){
        assertEquals(7,EconomyReports.days(new String[]{"report"}));assertEquals(90,EconomyReports.days(new String[]{"report","90"}));
        for(String value:List.of("0","91","-1","1.5","tomorrow"))assertThrows(IllegalArgumentException.class,()->EconomyReports.days(new String[]{"report",value}));
        assertThrows(IllegalArgumentException.class,()->EconomyReports.days(new String[]{"report","7","extra"}));
    }
    @Test void nonAdminCannotReadReportAndPausedEconomyIsHandled()throws Exception {
        List<String> messages=new ArrayList<>();
        for(boolean admin:new boolean[]{false,true}){
            CommandSender sender=(CommandSender)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{CommandSender.class},(o,m,a)->{
                if(m.getName().equals("hasPermission"))return admin;
                if(m.getName().equals("sendMessage") && a[0] instanceof net.kyori.adventure.text.Component text)messages.add(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(text));
                return null;
            });
            assertTrue(EconomyReports.execute(sender,new String[]{"report"},null,!admin,100));
        }
        assertTrue(messages.get(0).contains("for admins"));assertTrue(messages.get(1).contains("paused"));
    }
    @Test void barterSalesAreCountedWithoutInventingNooksPrices()throws Exception {
        setupShop();var offer=store.publishNativeOffer("world:1:2:3","one",seller,new byte[]{1},4,250,new byte[]{2},8,2);
        store.creditNativeStock(UUID.randomUUID(),offer.id(),seller,8,2);
        byte[] before=new byte[32],after=new byte[32];after[0]=1;UUID payment=UUID.randomUUID();
        store.planNativePayment(payment,offer.id(),buyer,8,before,after,3);store.confirmNativePayment(payment,buyer,after);
        buy(offer,4,true);var report=store.economyReport(0,10);assertEquals(1,report.barter());assertEquals(0,report.variants());assertTrue(report.market().isEmpty());
    }
    @Test void marketIsBoundedToTopTenButCountsAllVariants()throws Exception {
        setupShop();for(int i=1;i<=11;i++)buy(offer(new byte[]{(byte)i},1,i),3,true);
        var report=store.economyReport(0,10);assertEquals(11,report.variants());assertEquals(10,report.market().size());assertEquals(11,report.market().getFirst().cents());assertEquals(2,report.market().getLast().cents());
    }
}