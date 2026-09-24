package net.nikosnook.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ShopDraftTest {
    @Test void defaultOfferUsesNooks(){var d=ShopDraft.initial();assertEquals(1,d.quantity());assertEquals(100,d.nooks());assertNull(d.paymentItem());}
    @Test void switchingCurrencyPreservesBundleAndDoesNotConvertValue(){
        var d=ShopDraft.initial().quantity(16).nooks(125).item("DIAMOND",3);
        assertEquals(16,d.quantity());assertEquals("3 × diamond",d.price());
        var n=d.nooks(250);assertNull(n.paymentItem());assertEquals(250,n.nooks());assertEquals(16,n.quantity());assertEquals("DIAMOND",d.paymentItem());
    }
    @Test void zeroNegativeAndOversizedBundlesRejected(){for(int q:new int[]{-1,0,65,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->ShopDraft.initial().quantity(q));}
    @Test void invalidCurrencyAmountsRejected(){
        for(long n:new long[]{-1,0,Money.MAX+1})assertThrows(IllegalArgumentException.class,()->ShopDraft.initial().nooks(n));
        for(int q:new int[]{0,-1,65})assertThrows(IllegalArgumentException.class,()->ShopDraft.initial().item("DIAMOND",q));
        assertThrows(IllegalArgumentException.class,()->ShopDraft.initial().item("DIAMOND; command",1));
    }
    @Test void changesAreImmutableAndPricesApplyToWholeBundle(){
        var old=ShopDraft.initial();var changed=old.quantity(64).item("IRON_INGOT",5);
        assertEquals(1,old.quantity());assertNull(old.paymentItem());assertEquals("5 × iron ingot",changed.price());
        assertEquals(Money.MAX,old.nooks(Money.MAX).nooks());
    }
    @Test void priceButtonsClampToValidCurrencyBounds(){
        assertEquals(1,ShopDraft.initial().nooks(1).adjustPrice(false,true).nooks());
        assertEquals(Money.MAX,ShopDraft.initial().nooks(Money.MAX).adjustPrice(true,true).nooks());
        assertEquals(125,ShopDraft.initial().adjustPrice(true,false).nooks());
        assertEquals(1,ShopDraft.initial().item("DIAMOND",1).adjustPrice(false,true).paymentQuantity());
        assertEquals(64,ShopDraft.initial().item("DIAMOND",63).adjustPrice(true,true).paymentQuantity());
    }
}
