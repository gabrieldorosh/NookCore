package net.nikosnook.core;

import org.junit.jupiter.api.Test;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import static org.junit.jupiter.api.Assertions.*;

class HistoryPresentationTest {
    String plain(Component c){return PlainTextComponentSerializer.plainText().serialize(c);}
    @Test void oldVaultEntriesKeepAuditDetailsWithoutInventingShopReceipt(){
        var entry=new NookStore.Entry(16,1800000000000L,-100,"integration","vault:withdraw");
        var line=NookUi.history(entry);String displayed=plain(line);
        assertTrue(displayed.contains("₦-1.00"));assertTrue(displayed.contains("Payment sent through a plugin"));
        assertFalse(displayed.toLowerCase().contains("shop"));
        var audit=plain((Component)line.hoverEvent().value());
        assertTrue(audit.contains("Record #16"));assertTrue(audit.contains("vault:withdraw"));assertTrue(audit.contains("integration"));
    }
    @Test void refundsShowPlotAndRetainLeaseIdentityInAudit(){
        var line=NookUi.history(new NookStore.Entry(8,1800000000000L,6000,"rent-refund","one lease=abc"));
        assertTrue(plain(line).contains("+₦60.00 · Unused rent refunded · one"));
        assertFalse(plain(line).contains("lease=abc"));assertTrue(plain((Component)line.hoverEvent().value()).contains("lease=abc"));
    }
}
