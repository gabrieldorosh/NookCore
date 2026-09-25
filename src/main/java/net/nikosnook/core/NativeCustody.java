package net.nikosnook.core;

import java.util.*;

/** The journal must exist before inventory writes. An uncertain write is never replayed. */
final class NativeCustody {
    interface Inventory {
        List<NativeInventoryPlan.Stack> capture();
        void apply(List<NativeInventoryPlan.Stack> slots)throws Exception;
        byte[] persist(List<NativeInventoryPlan.Stack> expected,UUID receipt)throws Exception;
    }
    private final NookStore store;
    NativeCustody(NookStore store){this.store=store;}
    void payment(UUID offer,UUID actor,int quantity,String exactKey,Inventory inventory,long now)throws Exception {
        var plan=NativeInventoryPlan.deposit(inventory.capture(),exactKey,quantity);UUID receipt=UUID.randomUUID();
        if(!store.planNativePayment(receipt,offer,actor,quantity,plan.beforeHash(),plan.afterHash(),now))throw new IllegalStateException("Payment intent was already used.");
        try{inventory.apply(plan.after());store.confirmNativePayment(receipt,actor,inventory.persist(plan.after(),receipt));}
        catch(Exception e){throw new java.io.IOException("Native payment receipt "+receipt+" needs review",e);}
    }
    void deposit(UUID offer,UUID actor,int quantity,String exactKey,Inventory inventory,long now)throws Exception {
        var plan=NativeInventoryPlan.deposit(inventory.capture(),exactKey,quantity);
        UUID receipt=UUID.randomUUID();
        if(!store.planNativeStockDeposit(receipt,offer,actor,quantity,plan.beforeHash(),plan.afterHash(),now))throw new IllegalStateException("Stock intent was already used.");
        try{inventory.apply(plan.after());store.confirmNativeStockDeposit(receipt,actor,inventory.persist(plan.after(),receipt),now);}
        catch(Exception e){throw new java.io.IOException("Native stock receipt "+receipt+" needs review",e);}
    }
    void deliver(UUID receipt,UUID actor,NativeInventoryPlan.Stack item,Inventory inventory)throws Exception {
        var trade=store.nativeReceipt(receipt).orElseThrow();
        if(!trade.buyer().equals(actor))throw new IllegalArgumentException("That collection belongs to another player.");
        if(!store.nativeDeliveryState(receipt).equals("PENDING"))throw new IllegalArgumentException("This collection needs staff review or has already been delivered.");
        var plan=NativeInventoryPlan.deliver(inventory.capture(),item,trade.quantity());
        if(!store.planNativeDelivery(receipt,actor,plan.beforeHash(),plan.afterHash()))throw new IllegalArgumentException("This delivery has already been started. Ask staff to review it.");
        try{inventory.apply(plan.after());store.confirmNativeDelivery(receipt,actor,inventory.persist(plan.after(),receipt));}
        catch(Exception e){throw new java.io.IOException("Native delivery receipt "+receipt+" needs review",e);}
    }
}
