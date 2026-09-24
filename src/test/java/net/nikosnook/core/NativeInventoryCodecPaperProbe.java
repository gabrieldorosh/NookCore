package net.nikosnook.core;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import java.util.*;
import java.nio.file.*;

/** Temporary local-server probe, packaged separately by reference/check_native_codec.py. */
public final class NativeInventoryCodecPaperProbe extends JavaPlugin {
    private Object call(Class<?> owner,String name,Class<?>[] types,Object... args)throws Exception {
        var method=owner.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(null,args);
    }
    private void checkLandings(ClassLoader loader)throws Exception {
        var admin=Class.forName("net.nikosnook.core.AdminTeleports",true,loader);
        var world=getServer().getWorlds().getFirst();var spawn=world.getSpawnLocation();
        int x=(spawn.getBlockX()>>4)*16+8,z=(spawn.getBlockZ()>>4)*16+8,y=world.getMaxHeight()-10;
        // Only use empty air in an existing local rehearsal chunk, then restore it.
        if(!world.isChunkGenerated(x>>4,z>>4) || !world.loadChunk(x>>4,z>>4,false))throw new AssertionError("Spawn test chunk is unavailable");
        for(int bx=x-1;bx<=x+1;bx++)for(int bz=z-1;bz<=z+1;bz++)for(int by=y-2;by<=y+3;by++)if(!world.getBlockAt(bx,by,bz).getType().isAir())throw new AssertionError("Landing probe space is occupied; preserved");
        var floor=world.getBlockAt(x,y,z);var head=world.getBlockAt(x,y+1,z);
        try{
            Material[] materials={Material.DIRT_PATH,Material.FARMLAND,Material.STONE_SLAB,Material.GLASS,Material.WHITE_CARPET};double[] heights={.9375,.9375,.5,1,.0625};
            for(int i=0;i<materials.length;i++){
                floor.setType(materials[i],false);var point=new org.bukkit.Location(world,x+.5,y+heights[i],z+.5);
                if(!(Boolean)call(admin,"safeReturn",new Class[]{org.bukkit.Location.class},point))throw new AssertionError("Valid landing rejected: "+materials[i]);
            }
            floor.setType(Material.DIRT_PATH,false);head.setType(Material.STONE,false);
            if((Boolean)call(admin,"safeReturn",new Class[]{org.bukkit.Location.class},new org.bukkit.Location(world,x+.5,y+.9375,z+.5)))throw new AssertionError("Obstructed path landing accepted");
            head.setType(Material.AIR,false);floor.setType(Material.MAGMA_BLOCK,false);
            if((Boolean)call(admin,"safeReturn",new Class[]{org.bukkit.Location.class},new org.bukkit.Location(world,x+.5,y+1,z+.5)))throw new AssertionError("Magma landing accepted");
        }finally{floor.setType(Material.AIR,false);head.setType(Material.AIR,false);}
    }
    @Override public void onEnable(){
        try{
            var loader=Objects.requireNonNull(getServer().getPluginManager().getPlugin("NookCore")).getClass().getClassLoader();
            checkLandings(loader);
            var codec=Class.forName("net.nikosnook.core.NativeInventoryCodec",true,loader);
            var named=new ItemStack(Material.DIAMOND,5);var meta=named.getItemMeta();meta.displayName(Component.text("Niko's diamonds"));named.setItemMeta(meta);
            var sword=new ItemStack(Material.DIAMOND_SWORD);sword.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS,5);
            var swordMeta=(org.bukkit.inventory.meta.Damageable)sword.getItemMeta();swordMeta.setDamage(17);swordMeta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(this,"probe"),org.bukkit.persistence.PersistentDataType.STRING,"marker");sword.setItemMeta(swordMeta);
            var box=new ItemStack(Material.SHULKER_BOX);var boxMeta=(org.bukkit.inventory.meta.BlockStateMeta)box.getItemMeta();var state=(org.bukkit.block.ShulkerBox)boxMeta.getBlockState();state.getInventory().setItem(0,named.clone());boxMeta.setBlockState(state);box.setItemMeta(boxMeta);
            var original=new ItemStack[]{named,null,new ItemStack(Material.DIAMOND,3),sword,box};
            @SuppressWarnings("unchecked") var snapshot=(List<Object>)call(codec,"capture",new Class[]{ItemStack[].class},(Object)original);
            var restored=(ItemStack[])call(codec,"restore",new Class[]{List.class},snapshot);
            if(!restored[0].isSimilar(named) || restored[0].getAmount()!=5 || restored[1]!=null || !restored[2].isSimilar(original[2]) || restored[3].getMaxStackSize()!=1)throw new AssertionError("Item round-trip lost metadata, amount, empty slot or stack limit");
            for(int i:new int[]{0,2,3,4})if(!restored[i].isSimilar(original[i]))throw new AssertionError("Complex metadata changed at slot "+i);
            if(!snapshot.equals(call(codec,"capture",new Class[]{ItemStack[].class},(Object)restored)))throw new AssertionError("Serialized keys changed after round-trip");
            restored[0].setAmount(1);if(named.getAmount()!=5)throw new AssertionError("Restored stack aliases original");
            byte[] data=(byte[])call(codec,"saleItem",new Class[]{ItemStack.class},named);
            var unit=ItemStack.deserializeBytes(data);if(unit.getAmount()!=1 || !unit.isSimilar(named))throw new AssertionError("Sale identity not normalized");
            var plan=Class.forName("net.nikosnook.core.NativeInventoryPlan",true,loader);
            var keyMethod=snapshot.get(0).getClass().getDeclaredMethod("key");keyMethod.setAccessible(true);String key=(String)keyMethod.invoke(snapshot.get(0));
            Object deposit=call(plan,"deposit",new Class[]{List.class,String.class,int.class},snapshot,key,4);
            var afterMethod=deposit.getClass().getDeclaredMethod("after");afterMethod.setAccessible(true);
            var after=(ItemStack[])call(codec,"restore",new Class[]{List.class},afterMethod.invoke(deposit));
            if(after[0].getAmount()!=1 || after[2].getAmount()!=3 || named.getAmount()!=5)throw new AssertionError("Exact-metadata removal changed the wrong items");
            Files.writeString(Path.of("native-codec-probe-result.txt"),"PASS: path/farmland/slab/glass/carpet landings, blocked/hazardous landings rejected; metadata round-trip, amounts, empty slots, unstackables, defensive copies, normalized sale identity, deterministic keys and exact-metadata deposit\n");
        }catch(Throwable e){
            getLogger().log(java.util.logging.Level.SEVERE,"Native inventory codec probe failed",e);
            try{Files.writeString(Path.of("native-codec-probe-result.txt"),"FAIL: "+e);}catch(Exception ignored){}
        }
    }
}
