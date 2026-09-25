package net.nikosnook.core;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SharedIncomeTest {
    @TempDir Path dir;NookStore store;
    UUID owner=UUID.randomUUID(),member=UUID.randomUUID(),buyer=UUID.randomUUID();NativeShop.Offer offer;
    @BeforeEach void setup()throws Exception{
        store=new NookStore(dir.resolve("db"));for(UUID id:List.of(owner,member,buyer))store.join(id,id.toString(),0,10000);
        store.definePlot("one",3000);store.rent("one",owner,0);var invite=store.invite("one",owner,member,"BUILD",1);store.acceptInvitation(invite.token(),member,1);
        offer=store.createNativeOffer("one",owner,new byte[]{1},1,251,1);store.creditNativeStock(UUID.randomUUID(),offer.id(),owner,10,1);
    }
    @AfterEach void close()throws Exception{store.close();}
    long balance(UUID id)throws Exception{return store.account(id).orElseThrow().cents();}
    void buy()throws Exception{store.settleNativePurchase(UUID.randomUUID(),offer.id(),buyer,1,3);}
    @Test void defaultSharesNooksAndRoundingConservesEveryPenny()throws Exception{
        assertTrue(store.incomeSharing("one"));buy();assertEquals(7126,balance(owner));assertEquals(10125,balance(member));assertEquals(9749,balance(buyer));
        assertEquals(126,store.offlineIncome(owner).sales());assertEquals(125,store.offlineIncome(member).sales());
    }
    @Test void onlyOwnerCanOptOutAndPreferenceSurvivesRestart()throws Exception{
        assertThrows(IllegalArgumentException.class,()->store.incomeSharing("one",member,false,2));store.incomeSharing("one",owner,false,2);
        store.close();store=new NookStore(dir.resolve("db"));assertFalse(store.incomeSharing("one"));buy();assertEquals(7251,balance(owner));assertEquals(10000,balance(member));
    }
    @Test void RemovedMemberReceivesNoFutureSales()throws Exception{
        store.removeMember("one",owner,member,2);buy();assertEquals(7251,balance(owner));assertEquals(10000,balance(member));
    }
    @Test void RecipientCapacityFailureRollsBackAllBalancesStockAndReports()throws Exception{
        store.adjust(member,Money.MAX-10000,"test","capacity",2);UUID receipt=UUID.randomUUID();
        assertThrows(NookStore.BalanceCapacityException.class,()->store.settleNativePurchase(receipt,offer.id(),buyer,1,3));
        assertEquals(7000,balance(owner));assertEquals(10000,balance(buyer));assertEquals(10,store.nativeOffer(offer.id()).stock());assertEquals(0,store.offlineIncome(owner).through());assertTrue(store.nativeReceipt(receipt).isEmpty());
    }
    @Test void NewLeaseStartsWithSharingEnabled()throws Exception{
        store.incomeSharing("one",owner,false,2);store.abandon(store.abandonmentQuote("one",owner,3),owner,3);store.confirmCleared("one","staff","box",4);store.rent("one",owner,5);assertTrue(store.incomeSharing("one"));
    }
    @Test void ThreeMembersSplitEquallyAndReceiptRetryDoesNotPayTwice()throws Exception{
        UUID third=UUID.randomUUID();store.join(third,"Third",1,10000);var invite=store.invite("one",owner,third,"STOCK",2);store.acceptInvitation(invite.token(),third,2);
        UUID receipt=UUID.randomUUID();store.settleNativePurchase(receipt,offer.id(),buyer,1,3);store.settleNativePurchase(receipt,offer.id(),buyer,1,4);
        assertEquals(7084,balance(owner));assertEquals(20167,balance(member)+balance(third));assertEquals(9,store.nativeOffer(offer.id()).stock());
    }
}
