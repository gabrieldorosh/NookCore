package net.nikosnook.core;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.zip.GZIPInputStream;
import java.nio.charset.StandardCharsets;

/** Main-thread save acknowledgement for the exact inventory applied in this operation. */
final class NativePlayerInventory implements NativeCustody.Inventory {
    private final Player player;
    NativePlayerInventory(Player player){this.player=player;}
    private void check(){
        if(!Bukkit.isPrimaryThread() || !player.isOnline() || player.isDead())throw new IllegalStateException("Shop inventory operation requires a live player on the server thread.");
        if(player.getGameMode()!=GameMode.SURVIVAL)throw new IllegalArgumentException("Use shops in survival mode.");
    }
    public List<NativeInventoryPlan.Stack> capture(){check();return NativeInventoryCodec.capture(player.getInventory().getStorageContents());}
    public void apply(List<NativeInventoryPlan.Stack> slots){check();player.getInventory().setStorageContents(NativeInventoryCodec.restore(slots));}
    public byte[] persist(List<NativeInventoryPlan.Stack> expected,UUID receipt)throws Exception {
        check();byte[] hash=NativeInventoryPlan.hash(expected);
        if(!Arrays.equals(hash,NativeInventoryPlan.hash(capture())))throw new IllegalStateException("Inventory changed before save.");
        String marker="shop-save-"+receipt+"-"+UUID.randomUUID()+"-"+HexFormat.of().formatHex(hash);
        player.getPersistentDataContainer().set(new NamespacedKey("nookcore","shop-save"),PersistentDataType.STRING,marker);
        player.saveData();
        Path file=Bukkit.getServer().getLevelDirectory().resolve("players/data").resolve(player.getUniqueId()+".dat");
        try(var channel=FileChannel.open(file,StandardOpenOption.WRITE)){channel.force(true);}
        byte[] data;
        try(var stream=new GZIPInputStream(Files.newInputStream(file))){data=stream.readNBytes(16*1024*1024+1);}
        if(data.length>16*1024*1024 || !new String(data,StandardCharsets.ISO_8859_1).contains(marker))throw new IllegalStateException("Saved player data did not acknowledge the shop operation. Staff review required.");
        if(!Arrays.equals(hash,NativeInventoryPlan.hash(capture())))throw new IllegalStateException("Inventory changed during save.");
        return hash;
    }
    static NativeInventoryPlan.Stack item(byte[] encoded){
        var item=org.bukkit.inventory.ItemStack.deserializeBytes(encoded);
        return new NativeInventoryPlan.Stack(Base64.getEncoder().encodeToString(NativeInventoryCodec.saleItem(item)),1,item.getMaxStackSize());
    }
}
