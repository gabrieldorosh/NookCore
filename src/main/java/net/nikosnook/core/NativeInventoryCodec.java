package net.nikosnook.core;

import org.bukkit.inventory.ItemStack;
import java.util.*;

/** Paper serialization bridge for the pure slot planner; does not mutate inventories. */
final class NativeInventoryCodec {
    static byte[] saleItem(ItemStack item){
        if(item==null || item.getType().isAir())throw new IllegalArgumentException("Choose a real sale item.");
        var single=item.clone();single.setAmount(1);return single.serializeAsBytes();
    }
    static List<NativeInventoryPlan.Stack> capture(ItemStack[] contents){
        List<NativeInventoryPlan.Stack> slots=new ArrayList<>();
        for(var item:contents)slots.add(item==null || item.getType().isAir()?null:new NativeInventoryPlan.Stack(Base64.getEncoder().encodeToString(saleItem(item)),item.getAmount(),item.getMaxStackSize()));
        return Collections.unmodifiableList(slots);
    }
    static ItemStack[] restore(List<NativeInventoryPlan.Stack> slots){
        ItemStack[] result=new ItemStack[slots.size()];
        for(int i=0;i<slots.size();i++){
            var slot=slots.get(i);if(slot==null)continue;
            var item=ItemStack.deserializeBytes(Base64.getDecoder().decode(slot.key()));
            if(item.getType().isAir() || item.getMaxStackSize()!=slot.limit() || slot.count()>item.getMaxStackSize())throw new IllegalArgumentException("Stored item no longer has the expected stack properties.");
            item.setAmount(slot.count());result[i]=item;
        }
        return result;
    }
}
