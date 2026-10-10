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
    // Only the block supporting the label may act as its shop target.
    static org.bukkit.block.Block target(org.bukkit.block.Block clicked){
        if(clicked.getState() instanceof Container direct && nativeContainer(direct))return clicked;
        if(!(clicked.getState() instanceof org.bukkit.block.Sign sign)
            || com.Acrobot.ChestShop.Signs.ChestShopSign.isValid(sign))return null;
        var data=clicked.getBlockData();
        org.bukkit.block.Block support;
        if(data instanceof org.bukkit.block.data.type.WallSign wall)support=clicked.getRelative(wall.getFacing().getOppositeFace());
        else if(data instanceof org.bukkit.block.data.type.Sign)support=clicked.getRelative(org.bukkit.block.BlockFace.DOWN);
        else if(data instanceof org.bukkit.block.data.type.HangingSign)support=clicked.getRelative(org.bukkit.block.BlockFace.UP);
        else return null;
        return support.getState() instanceof Container container && nativeContainer(container)?support:null;
    }
    static boolean nativeContainer(InventoryHolder holder){
        if(holder instanceof DoubleChest pair)return nativeContainer(pair.getLeftSide()) || nativeContainer(pair.getRightSide());
        return holder instanceof Container c && c.getPersistentDataContainer().has(ANCHOR,PersistentDataType.STRING);
    }
    static void rejectNative(InventoryHolder holder){
        if(nativeContainer(holder))throw new IllegalArgumentException("This is a NookShop. Ordinary labels are allowed, but a ChestShop trading sign cannot share its container.");
    }
}
