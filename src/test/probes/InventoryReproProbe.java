import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import com.mojang.authlib.GameProfile;
import java.util.*;
import java.lang.reflect.*;
import java.nio.file.*;

public class InventoryReproProbe extends JavaPlugin {
    Class<?> codec;
    PlayerInventory inventory;
    int checked, failures, semanticFailures;
    int comparatorChecks;
    Method mismatch;
    Object call(String method,Class<?> type,Object value)throws Exception{
        Method m=codec.getDeclaredMethod(method,type);m.setAccessible(true);return m.invoke(null,value);
    }
    @SuppressWarnings("unchecked") List<Object> capture(ItemStack[] slots)throws Exception{return (List<Object>)call("capture",ItemStack[].class,slots);}
    ItemStack[] restore(List<Object> slots)throws Exception{return (ItemStack[])call("restore",List.class,slots);}
    void check(String name,ItemStack item)throws Exception{
        ItemStack[] before=new ItemStack[36];before[0]=new ItemStack(Material.COBBLESTONE,5);before[1]=item.clone();
        inventory.setStorageContents(before);
        List<Object> expected=capture(inventory.getStorageContents());
        for(int turn=0;turn<3;turn++){
            ItemStack[] restored=restore(expected);
            inventory.setStorageContents(restored);
            List<Object> actual=capture(inventory.getStorageContents());
            checked++;
            if(mismatch!=null){
                Object reason=mismatch.invoke(null,expected,inventory.getStorageContents());
                if(reason!=null)throw new AssertionError("Semantic guard rejected unchanged inventory: "+name+": "+reason);
                comparatorChecks++;
            }
            if(!expected.equals(actual)){
                failures++;
                boolean similar=true;
                ItemStack[] observed=inventory.getStorageContents();
                for(int i=0;i<restored.length;i++){
                    if(restored[i]==null?observed[i]!=null:observed[i]==null || !restored[i].isSimilar(observed[i]) || restored[i].getAmount()!=observed[i].getAmount())similar=false;
                }
                if(!similar)semanticFailures++;
                getLogger().warning("REPRO_DIFFER name="+name+" iteration="+turn+" semantically_equal="+similar);
                Path dir=Path.of("repro-differences");Files.createDirectories(dir);
                for(int i=0;i<expected.size();i++)if(!Objects.equals(expected.get(i),actual.get(i))){
                    Object a=expected.get(i),b=actual.get(i);
                    if(a!=null && b!=null){
                        Method key=a.getClass().getDeclaredMethod("key");key.setAccessible(true);
                        Files.write(dir.resolve("failure-"+failures+"-slot-"+i+"-before.bin"),Base64.getDecoder().decode((String)key.invoke(a)));
                        Files.write(dir.resolve("failure-"+failures+"-slot-"+i+"-after.bin"),Base64.getDecoder().decode((String)key.invoke(b)));
                        getLogger().warning("REPRO_SLOT "+i+" expected="+restored[i].getType()+" similar="+restored[i].isSimilar(observed[i]));
                    }
                }
                return;
            }
            expected=actual;
        }
    }
    void rejected(String name,List<Object> expected,ItemStack[] actual)throws Exception{
        if(mismatch.invoke(null,expected,actual)==null)throw new AssertionError("Semantic guard accepted "+name);
        comparatorChecks++;getLogger().info("REPRO_GUARD_PASS "+name);
    }
    void guardChecks()throws Exception{
        ItemStack[] before=new ItemStack[36];before[0]=new ItemStack(Material.COBBLESTONE,5);before[1]=rich(Material.DIAMOND_SWORD,4);
        var book=new ItemStack(Material.ENCHANTED_BOOK);var em=(EnchantmentStorageMeta)book.getItemMeta();em.addStoredEnchant(org.bukkit.enchantments.Enchantment.MENDING,1,true);book.setItemMeta(em);before[2]=book;
        List<Object> expected=capture(before);ItemStack[] changed;
        changed=restore(expected);changed[0].setAmount(4);rejected("quantity change",expected,changed);
        changed=restore(expected);changed[0]=null;rejected("missing item",expected,changed);
        changed=restore(expected);changed[3]=new ItemStack(Material.DIAMOND);rejected("unexpected item",expected,changed);
        changed=restore(expected);var swap=changed[0];changed[0]=changed[1];changed[1]=swap;rejected("swapped slots",expected,changed);
        changed=restore(expected);var meta=changed[1].getItemMeta();meta.displayName(Component.text("Different"));changed[1].setItemMeta(meta);rejected("changed display name",expected,changed);
        changed=restore(expected);var damage=(Damageable)changed[1].getItemMeta();damage.setDamage(18);changed[1].setItemMeta(damage);rejected("changed damage",expected,changed);
        changed=restore(expected);meta=changed[1].getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey(this,"marker_0"),PersistentDataType.STRING,"changed");changed[1].setItemMeta(meta);rejected("changed PDC",expected,changed);
        changed=restore(expected);em=(EnchantmentStorageMeta)changed[2].getItemMeta();em.addStoredEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING,3,true);changed[2].setItemMeta(em);rejected("changed stored enchantments",expected,changed);
        changed=restore(expected);meta=changed[0].getItemMeta();meta.setMaxStackSize(32);changed[0].setItemMeta(meta);rejected("changed max stack",expected,changed);
        rejected("slot array size",expected,new ItemStack[35]);
    }
    Object invoke(Object object,String name,Object...args)throws Exception{
        Class<?> type=object instanceof Class<?> c?c:object.getClass();
        for(Method m:type.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);try{return m.invoke(object instanceof Class<?>?null:object,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception cause)throw cause;throw e;}}
        throw new NoSuchMethodException(name);
    }
    Object construct(Class<?> type,Class<?> argType,Object arg)throws Exception{var constructor=type.getDeclaredConstructor(argType);constructor.setAccessible(true);return constructor.newInstance(arg);}
    void custodyCheck(ServerPlayer handle)throws Exception{
        ClassLoader loader=codec.getClassLoader();Class<?> storeType=Class.forName("net.nikosnook.core.NookStore",true,loader);
        Object store=construct(storeType,Path.class,Path.of("custody-probe-"+UUID.randomUUID()+".db"));
        var real=handle.getBukkitEntity();UUID actor=real.getUniqueId();long now=System.currentTimeMillis();
        var p=(org.bukkit.entity.Player)Proxy.newProxyInstance(org.bukkit.entity.Player.class.getClassLoader(),new Class[]{org.bukkit.entity.Player.class},(proxy,m,args)->{
            if(m.getName().equals("isOnline"))return true;
            if(m.getName().equals("getGameMode"))return GameMode.SURVIVAL;
            if(m.getName().equals("isDead"))return false;
            try{return m.invoke(real,args);}catch(InvocationTargetException e){throw e.getCause();}
        });
        try{
            invoke(store,"join",actor,"ReproOnly",now,10000L);invoke(store,"definePlot","repro",600L);invoke(store,"rent","repro",actor,now);
            var book=new ItemStack(Material.ENCHANTED_BOOK);var em=(EnchantmentStorageMeta)book.getItemMeta();em.addStoredEnchant(org.bukkit.enchantments.Enchantment.MENDING,1,true);book.setItemMeta(em);
            byte[] item=(byte[])call("saleItem",ItemStack.class,book);
            Object offer=invoke(store,"publishNativeOffer","probe:1:2:3","repro",actor,item,1,100L,now);
            UUID offerId=(UUID)invoke(offer,"id");
            ItemStack[] contents=new ItemStack[36];contents[0]=book;contents[1]=book.clone();contents[2]=rich(Material.DIAMOND_SWORD,4);inventory.setStorageContents(contents);
            Class<?> inventoryType=Class.forName("net.nikosnook.core.NativePlayerInventory",true,loader);
            Object adapter=construct(inventoryType,org.bukkit.entity.Player.class,p);
            String key=(String)invoke(invoke(inventoryType,"item",item),"key");
            Class<?> custodyType=Class.forName("net.nikosnook.core.NativeCustody",true,loader);
            Object custody=construct(custodyType,storeType,store);
            invoke(custody,"deposit",offerId,actor,1,key,adapter,now);
            if((Integer)invoke(invoke(store,"nativeOffer",offerId),"stock")!=1)throw new AssertionError("Stock not credited once");
            if((Boolean)invoke(store,"nativeNeedsReview",actor))throw new AssertionError("Successful deposit left REVIEW");
            if(inventory.getItem(0)!=null || !inventory.getItem(1).isSimilar(book) || !inventory.getItem(2).isSimilar(contents[2]))throw new AssertionError("Unexpected inventory after deposit");
            Path data=getServer().getLevelDirectory().resolve("players/data").resolve(actor+".dat");
            if(!Files.isRegularFile(data))throw new AssertionError("Player save missing");
            comparatorChecks++;getLogger().info("REPRO_CUSTODY_PASS real CraftInventoryPlayer, enchanted-book deposit, metadata preserved, player saved and receipt complete");
        }finally{invoke(store,"close");}
    }
    ItemStack rich(Material material,int size)throws Exception{
        ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text("A named item "+size));
        meta.lore(List.of(Component.text("First line"),Component.text("Second line")));
        for(int j=0;j<size;j++)meta.getPersistentDataContainer().set(new NamespacedKey(this,"marker_"+j),PersistentDataType.STRING,"value "+j);
        meta.setUnbreakable(true);meta.setCustomModelData(1234);
        item.setItemMeta(meta);
        if(item.getItemMeta() instanceof Damageable d){d.setDamage(17);item.setItemMeta(d);}
        if(material.name().contains("SWORD") || material.name().contains("PICKAXE"))for(var e:Registry.ENCHANTMENT)item.addUnsafeEnchantment(e,2);
        return item;
    }
    void run()throws Exception{
        codec=Class.forName("net.nikosnook.core.NativeInventoryCodec",true,getServer().getPluginManager().getPlugin("NookCore").getClass().getClassLoader());
        try{mismatch=codec.getDeclaredMethod("mismatch",List.class,ItemStack[].class);mismatch.setAccessible(true);}catch(NoSuchMethodException oldVersion){}
        var player=new ServerPlayer(((CraftServer)getServer()).getServer(),((CraftWorld)getServer().getWorlds().getFirst()).getHandle(),new GameProfile(UUID.randomUUID(),"ReproOnly"),ClientInformation.createDefault());
        inventory=player.getBukkitEntity().getInventory();
        var enchantments=new ArrayList<org.bukkit.enchantments.Enchantment>();for(var enchant:Registry.ENCHANTMENT)enchantments.add(enchant);
        for(int size:new int[]{1,2,3,4,6,8,12,20,40})for(int seed=0;seed<30;seed++){
            Collections.shuffle(enchantments,new Random(seed));
            var enchanted=new ItemStack(Material.ENCHANTED_BOOK);var em=(EnchantmentStorageMeta)enchanted.getItemMeta();
            for(int i=0;i<Math.min(size,enchantments.size());i++)em.addStoredEnchant(enchantments.get(i),1+i%3,true);
            enchanted.setItemMeta(em);check("enchanted_book_"+size+"_seed_"+seed,enchanted);
        }
        for(Material material:Material.values())if(!material.isLegacy() && material.isItem() && !material.isAir()){
            try{check("plain_"+material,new ItemStack(material));}catch(IllegalArgumentException e){getLogger().info("REPRO_SKIP "+material+": "+e.getMessage());}
        }
        for(int size:new int[]{0,1,4,8,16,32})for(Material material:new Material[]{Material.DIAMOND_SWORD,Material.DIAMOND_PICKAXE,Material.POTION,Material.WRITTEN_BOOK,Material.SHULKER_BOX,Material.BUNDLE,Material.FILLED_MAP,Material.PLAYER_HEAD,Material.FIREWORK_ROCKET})check("rich_"+material+"_"+size,rich(material,size));
        var box=new ItemStack(Material.SHULKER_BOX);var boxMeta=(BlockStateMeta)box.getItemMeta();var state=(org.bukkit.block.ShulkerBox)boxMeta.getBlockState();for(int i=0;i<27;i++)state.getInventory().setItem(i,rich(Material.DIAMOND_SWORD,i));boxMeta.setBlockState(state);box.setItemMeta(boxMeta);check("full_shulker",box);
        var bundle=new ItemStack(Material.BUNDLE);var bm=(BundleMeta)bundle.getItemMeta();bm.setItems(List.of(rich(Material.DIAMOND_SWORD,16)));bundle.setItemMeta(bm);check("full_bundle",bundle);
        var potion=new ItemStack(Material.POTION);var pm=(PotionMeta)potion.getItemMeta();pm.setBasePotionType(org.bukkit.potion.PotionType.LONG_FIRE_RESISTANCE);pm.addCustomEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED,700,2),true);pm.setColor(Color.FUCHSIA);potion.setItemMeta(pm);check("potion_effects",potion);
        var book=new ItemStack(Material.WRITTEN_BOOK);var bk=(BookMeta)book.getItemMeta();bk.setAuthor("Probe");bk.setTitle("Probe book");bk.addPages(Component.text("Page one"),Component.text("Page two"));book.setItemMeta(bk);check("written_book",book);
        if(mismatch!=null){guardChecks();custodyCheck(player);}
        getLogger().info("REPRO_COMPLETE checks="+checked+" byte_differences="+failures+" semantic_differences="+semanticFailures+" comparator_checks="+comparatorChecks);
    }
    public void onEnable(){getServer().getScheduler().runTask(this,()->{try{run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"REPRO_FAILED",e);}finally{getServer().shutdown();}});}
}
