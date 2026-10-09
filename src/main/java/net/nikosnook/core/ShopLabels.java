package net.nikosnook.core;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;
final class ShopLabels {
    private static final NamespacedKey ANCHOR=new NamespacedKey("nookcore","native-shop");
    static boolean placingLabel(boolean sneaking,Material item){return sneaking && item.name().endsWith("_SIGN");}
    static boolean nativeContainer(InventoryHolder holder){
        if(holder instanceof DoubleChest pair)return nativeContainer(pair.getLeftSide()) || nativeContainer(pair.getRightSide());
        return holder instanceof Container c && c.getPersistentDataContainer().has(ANCHOR,PersistentDataType.STRING);
    }
    static void rejectNative(InventoryHolder holder){
        if(nativeContainer(holder))throw new IllegalArgumentException("This is a NookShop. Ordinary labels are allowed, but a ChestShop trading sign cannot share its container.");
    }
}
