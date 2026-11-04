import java.io.InputStreamReader;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.banner.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;

/** Disposable fixture only. Dispatches real Bukkit events against server-backed blocks and players. */
public final class BannerChecks extends JavaPlugin implements Listener {
    private static final NamespacedKey SAVED=new NamespacedKey("nookcore","pip-placed-item");
    private static final NamespacedKey DESIGN=new NamespacedKey("nookcore","pip-design");
    private static final NamespacedKey TYPE=new NamespacedKey("nookcore","pip-product-type");
    private World world; private JavaPlugin core; private Player player; private ServerPlayer handle;
    private ItemStack banner,head; private int checks; private boolean cancelPlace,cancelDrop;
    private final int x=1024,z=1024,y=100;
    private void check(boolean pass,String description){if(!pass)throw new AssertionError(description);checks++;getLogger().info("BANNER_PASS "+description);}
    private void dispatch(Event event){Bukkit.getPluginManager().callEvent(event);}
    @EventHandler(priority=EventPriority.LOWEST) public void cancel(BlockPlaceEvent event){if(cancelPlace)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void cancel(BlockDropItemEvent event){if(cancelDrop)event.setCancelled(true);}
    private ItemStack product(String id,String resource)throws Exception{
        var config=YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(core.getResource(resource)),StandardCharsets.UTF_8));
        Class<?> type=Class.forName("net.nikosnook.core.PipProducts",true,core.getClass().getClassLoader());
        var parse=type.getDeclaredMethod("parse",String.class,ConfigurationSection.class);parse.setAccessible(true);
        var value=parse.invoke(null,id,config.getConfigurationSection("catalogue."+id));
        var make=type.getDeclaredMethod("make",value.getClass());make.setAccessible(true);return (ItemStack)make.invoke(null,value);
    }
    private Block block(int offset){return world.getBlockAt(x+offset,y,z);}
    private byte[] saved(BlockState state){return state instanceof TileState tile?tile.getPersistentDataContainer().get(SAVED,PersistentDataType.BYTE_ARRAY):null;}
    private void saved(Block b,byte[] bytes){var tile=(TileState)b.getState();tile.getPersistentDataContainer().set(SAVED,PersistentDataType.BYTE_ARRAY,bytes);tile.update(false,false);}
    private Block raw(int offset,Material type,ItemStack item){
        Block b=block(offset);if(type==Material.WHITE_WALL_BANNER)b.getRelative(BlockFace.SOUTH).setType(Material.STONE,false);b.setType(type,false);
        if(b.getState() instanceof Banner value && item.getItemMeta() instanceof BannerMeta meta){value.setPatterns(meta.getPatterns());value.update(false,false);}
        return b;
    }
    private BlockPlaceEvent place(int offset,Material type,ItemStack item){
        Block b=block(offset);b.setType(Material.AIR,false);BlockState replaced=b.getState();raw(offset,type,item);
        var event=new BlockPlaceEvent(b,replaced,b.getRelative(BlockFace.DOWN),item,player,true,EquipmentSlot.HAND);dispatch(event);return event;
    }
    private ItemStack vanilla(ItemStack original){
        var item=new ItemStack(original.getType());
        if(item.getItemMeta() instanceof BannerMeta meta){meta.setPatterns(((BannerMeta)original.getItemMeta()).getPatterns());item.setItemMeta(meta);}
        return item;
    }
    private void exact(ItemStack expected,ItemStack actual,String label){
        check(actual.getAmount()==1&&expected.isSimilar(actual),label+" exact unit metadata");
        check(Objects.equals(expected.getItemMeta().displayName(),actual.getItemMeta().displayName())&&Objects.equals(expected.getItemMeta().lore(),actual.getItemMeta().lore()),label+" name and lore");
        check(Objects.equals(expected.getItemMeta().getPersistentDataContainer(),actual.getItemMeta().getPersistentDataContainer()),label+" all custom data");
        if(expected.getItemMeta() instanceof BannerMeta meta)check(meta.getPatterns().equals(((BannerMeta)actual.getItemMeta()).getPatterns()),label+" ordered patterns");
    }
    private void drop(Block b,ItemStack expected,boolean restore,String label){
        BlockState state=b.getState();ItemStack plain=vanilla(expected);
        var first=world.dropItem(b.getLocation(),plain);var second=world.dropItem(b.getLocation(),plain);
        var unrelated=world.dropItem(b.getLocation(),new ItemStack(Material.DIAMOND));
        var items=new ArrayList<>(List.of(first,second,unrelated));var ids=items.stream().map(Entity::getUniqueId).toList();
        int entities=world.getEntitiesByClass(Item.class).size();
        b.setType(Material.AIR,false);
        dispatch(new BlockDropItemEvent(b,state,player,items));
        check(items.size()==3&&ids.equals(items.stream().map(Entity::getUniqueId).toList())&&entities==world.getEntitiesByClass(Item.class).size(),label+" creates no new item entity");
        if(restore)exact(expected,first.getItemStack(),label);
        else check(first.getItemStack().isSimilar(plain),label+" retains vanilla metadata");
        check(second.getItemStack().isSimilar(plain)&&unrelated.getItemStack().getType()==Material.DIAMOND,label+" modifies at most one matching existing drop");
        items.forEach(Entity::remove);
    }
    private void setup()throws Exception{
        check(Bukkit.isPrimaryThread()&&Bukkit.getMinecraftVersion().equals("26.3"),"exact Paper Minecraft 26.3 main thread");
        core=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NookCore");check(core!=null&&core.isEnabled(),"candidate core enabled");
        world=Bukkit.getWorlds().getFirst();world.getChunkAt(x>>4,z>>4).load();
        boolean restart=Files.exists(getDataFolder().toPath().resolve("restart-ready"));
        for(int i=0;i<16;i++){block(i).getRelative(BlockFace.DOWN).setType(Material.STONE,false);if(!restart||i<8||i>10)block(i).setType(Material.AIR,false);}
        var profile=new GameProfile(UUID.fromString("20261104-0000-4000-8000-000000000021"),"BannerFixture");
        handle=new ServerPlayer(((CraftServer)getServer()).getServer(),((CraftWorld)world).getHandle(),profile,ClientInformation.createDefault());
        handle.connection=new ServerGamePacketListenerImpl(((CraftServer)getServer()).getServer(),new Connection(PacketFlow.SERVERBOUND),handle,CommonListenerCookie.createInitial(profile,false));
        handle.setPos(x+0.5,y,z+0.5);player=handle.getBukkitEntity();
        check(player instanceof org.bukkit.craftbukkit.entity.CraftPlayer&&Bukkit.getPlayer(player.getUniqueId())==null,"genuine CraftPlayer fixture stays outside online player list");
        player.setGameMode(GameMode.SURVIVAL);
        Bukkit.getPluginManager().registerEvents(this,this);
        banner=product("sunny-nook-banner","pip-products-examples.yml");head=product("chick","pip-catalogue.yml");
        check("BANNER".equals(banner.getItemMeta().getPersistentDataContainer().get(TYPE,PersistentDataType.STRING)),"actual typed PipProducts banner factory");
        check(PipProductsChecks.run(core)==39,"existing 39 Paper banner journal and head factory checks");
    }
    private void placementAndDrops(){
        for(var type:List.of(Material.WHITE_BANNER,Material.WHITE_WALL_BANNER)){
            var stack=banner.clone();stack.setAmount(4);var event=place(0,type,stack);
            check(!event.isCancelled(),type+" placement allowed");
            check(Arrays.equals(banner.serializeAsBytes(),saved(event.getBlock().getState())),type+" saves exact one-unit bytes from a four-item stack");
            drop(event.getBlock(),banner,true,type+" break");
        }
        var e=place(0,Material.PLAYER_HEAD,head);check(!e.isCancelled()&&saved(e.getBlock().getState())!=null,"existing collectible head placement");drop(e.getBlock(),head,true,"head regression");
        cancelPlace=true;var denied=place(0,Material.WHITE_BANNER,banner);cancelPlace=false;
        check(denied.isCancelled()&&saved(denied.getBlock().getState())==null,"cancelled placement never stores item");
        var placed=place(0,Material.WHITE_BANNER,banner);cancelDrop=true;drop(placed.getBlock(),banner,false,"cancelled drop");cancelDrop=false;
        placed=place(0,Material.WHITE_BANNER,banner);var state=placed.getBlock().getState();placed.getBlock().setType(Material.AIR,false);
        player.setGameMode(GameMode.CREATIVE);int entities=world.getEntitiesByClass(Item.class).size();var empty=new ArrayList<Item>();dispatch(new BlockDropItemEvent(placed.getBlock(),state,player,empty));
        check(empty.isEmpty()&&entities==world.getEntitiesByClass(Item.class).size(),"Creative no-drop event creates no collectible");player.setGameMode(GameMode.SURVIVAL);
        var manager=Objects.requireNonNull(WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world)));
        // Keep protection trials at separate coordinates: WorldGuard debounces recent denials.
        var region=new ProtectedCuboidRegion("banner-probe-deny",BlockVector3.at(x+13,y-1,z-1),BlockVector3.at(x+13,y+2,z+1));
        region.setFlag(Flags.BLOCK_PLACE,StateFlag.State.DENY);region.setFlag(Flags.BLOCK_BREAK,StateFlag.State.DENY);manager.addRegion(region);
        try{
            denied=place(13,Material.WHITE_BANNER,banner);check(denied.isCancelled()&&saved(denied.getBlock().getState())==null,"WorldGuard denied placement saves no item");
            Block existing=raw(13,Material.WHITE_BANNER,banner);saved(existing,banner.serializeAsBytes());
            var broken=new BlockBreakEvent(existing,player);dispatch(broken);
            check(broken.isCancelled()&&Arrays.equals(saved(existing.getState()),banner.serializeAsBytes()),"WorldGuard denied break preserves collectible with no drop operation");
        }finally{manager.removeRegion("banner-probe-deny");}
    }
    private void invalid(){
        var plain=vanilla(banner);var event=place(0,Material.WHITE_BANNER,plain);check(saved(event.getBlock().getState())==null,"ordinary banner placement is untagged");drop(event.getBlock(),plain,false,"ordinary banner");
        var wrong=banner.clone();var meta=wrong.getItemMeta();meta.getPersistentDataContainer().set(TYPE,PersistentDataType.STRING,"HEAD");wrong.setItemMeta(meta);
        event=place(0,Material.WHITE_BANNER,wrong);check(saved(event.getBlock().getState())==null,"wrong product type placement remains vanilla");
        var missing=banner.clone();meta=missing.getItemMeta();meta.getPersistentDataContainer().remove(DESIGN);missing.setItemMeta(meta);
        event=place(0,Material.WHITE_BANNER,missing);check(saved(event.getBlock().getState())==null,"missing design placement remains vanilla");
        Block b=raw(0,Material.RED_BANNER,banner);saved(b,banner.serializeAsBytes());drop(b,new ItemStack(Material.RED_BANNER),false,"stale base-colour bytes");
        b=raw(0,Material.WHITE_BANNER,banner);var bannerState=(Banner)b.getState();var layers=new ArrayList<>(bannerState.getPatterns());Collections.reverse(layers);bannerState.setPatterns(layers);bannerState.update(false,false);saved(b,banner.serializeAsBytes());drop(b,banner,false,"reordered block patterns");
        b=raw(0,Material.WHITE_BANNER,banner);bannerState=(Banner)b.getState();layers=new ArrayList<>(bannerState.getPatterns());layers.set(0,new Pattern(DyeColor.RED,layers.getFirst().getPattern()));bannerState.setPatterns(layers);bannerState.update(false,false);saved(b,banner.serializeAsBytes());drop(b,banner,false,"changed block patterns");
        b=raw(0,Material.WHITE_BANNER,banner);saved(b,wrong.serializeAsBytes());drop(b,banner,false,"stale wrong-type bytes");
        b=raw(0,Material.WHITE_BANNER,banner);saved(b,new byte[]{0,1,2,3});drop(b,banner,false,"corrupt saved bytes");
        // A mismatched existing drop must remain vanilla even for a valid saved block.
        b=place(0,Material.WHITE_BANNER,banner).getBlock();drop(b,new ItemStack(Material.RED_BANNER),false,"different-colour drop");
        var changed=vanilla(banner);var bm=(BannerMeta)changed.getItemMeta();bm.setPatterns(List.of());changed.setItemMeta(bm);
        b=place(0,Material.WHITE_BANNER,banner).getBlock();drop(b,changed,false,"different-pattern drop");
    }
    private void policy(){
        Block valid=place(0,Material.WHITE_BANNER,banner).getBlock(),wall=place(1,Material.WHITE_WALL_BANNER,banner).getBlock(),oldHead=place(2,Material.PLAYER_HEAD,head).getBlock();
        Block plain=raw(3,Material.WHITE_BANNER,banner),stale=raw(4,Material.RED_BANNER,banner),corrupt=raw(5,Material.WHITE_BANNER,banner);
        saved(stale,banner.serializeAsBytes());saved(corrupt,new byte[]{1,2});
        var all=List.of(valid,wall,oldHead,plain,stale,corrupt);
        var blockEvent=new BlockExplodeEvent(block(15),block(15).getState(),new ArrayList<>(all),1f,ExplosionResult.DESTROY);dispatch(blockEvent);
        check(blockEvent.blockList().equals(List.of(plain,stale,corrupt)),"block explosion protects only matching banners and existing head");
        var entity=world.spawn(block(15).getLocation(),org.bukkit.entity.TNTPrimed.class);
        var entityEvent=new EntityExplodeEvent(entity,entity.getLocation(),new ArrayList<>(all),1f,ExplosionResult.DESTROY);dispatch(entityEvent);entity.remove();
        check(entityEvent.blockList().equals(List.of(plain,stale,corrupt)),"entity explosion protects only matching banners and existing head");
        for(Block b:all){
            boolean expected=List.of(valid,wall,oldHead).contains(b);
            Block piston=world.getBlockAt(b.getX(),y,z+5);piston.setType(Material.STICKY_PISTON,false);
            var extend=new BlockPistonExtendEvent(piston,List.of(b),BlockFace.EAST);dispatch(extend);
            var retract=new BlockPistonRetractEvent(piston,List.of(b),BlockFace.EAST);dispatch(retract);
            check(extend.isCancelled()==expected&&retract.isCancelled()==expected,"piston extension and retraction policy "+b.getX()+" type="+b.getType()+" saved="+(saved(b.getState())!=null)+" extend="+extend.isCancelled()+" retract="+retract.isCancelled());
        }
        var cancelled=new BlockExplodeEvent(block(15),block(15).getState(),new ArrayList<>(all),1f,ExplosionResult.DESTROY);cancelled.setCancelled(true);dispatch(cancelled);
        check(cancelled.blockList().equals(all),"cancelled explosion list stays unchanged");
    }
    private void crafting(){
        var menu=handle.inventoryMenu;
        handle.getInventory().clearContent();menu.getCraftSlots().clearContent();
        menu.getSlot(1).set(CraftItemStack.asNMSCopy(banner));menu.getSlot(2).set(CraftItemStack.asNMSCopy(new ItemStack(Material.WHITE_BANNER)));
        menu.slotsChanged(menu.getCraftSlots());
        check(!menu.getSlot(0).getItem().isEmpty(),"vanilla duplication recipe produces normal-click result");
        menu.clicked(0,0,net.minecraft.world.inventory.ContainerInput.PICKUP,handle);
        exact(banner,CraftItemStack.asBukkitCopy(menu.getCarried()),"normal-click vanilla copy");
        check(menu.getSlot(1).getItem().getCount()==1&&menu.getSlot(2).getItem().isEmpty(),"normal copy retains original and consumes one blank banner");
        menu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);menu.getCraftSlots().clearContent();handle.getInventory().clearContent();
        menu.getSlot(1).set(CraftItemStack.asNMSCopy(banner));menu.getSlot(2).set(CraftItemStack.asNMSCopy(new ItemStack(Material.WHITE_BANNER,3)));menu.slotsChanged(menu.getCraftSlots());
        menu.clicked(0,0,net.minecraft.world.inventory.ContainerInput.QUICK_MOVE,handle);
        int copies=0;
        for(var stack:handle.getInventory().getContents())if(!stack.isEmpty()){
            var item=CraftItemStack.asBukkitCopy(stack);check(item.isSimilar(banner),"shift-click vanilla copies retain complete Pip metadata");copies+=item.getAmount();
        }
        check(copies==3&&menu.getSlot(1).getItem().getCount()==1&&menu.getSlot(2).getItem().isEmpty(),"shift-click consumes three blanks and retains original");
        menu.getCraftSlots().clearContent();handle.getInventory().clearContent();
    }
    private void anchors()throws Exception{
        for(int i=8;i<=10;i++){
            ItemStack expected=i==10?head:banner;Block b=block(i);
            check(Arrays.equals(saved(b.getState()),expected.serializeAsBytes()),"restart anchor "+i+" saved bytes survive");
            drop(b,expected,true,"restart anchor "+i+" drop");
        }
    }
    private void phaseOne()throws Exception{
        placementAndDrops();invalid();policy();crafting();
        place(8,Material.WHITE_BANNER,banner);place(9,Material.WHITE_WALL_BANNER,banner);place(10,Material.PLAYER_HEAD,head);
        world.save();
        // Release fixture API tickets, including neighbours touched by entity/protection checks.
        // Paper's synchronous unload method fetches the chunk again before purging;
        // use its request API and observe the actual unload on a later tick.
        for(var chunk:world.getLoadedChunks())if(Math.abs(chunk.getX()-(x>>4))<=2&&Math.abs(chunk.getZ()-(z>>4))<=2)world.unloadChunkRequest(chunk.getX(),chunk.getZ());
        Bukkit.getScheduler().runTaskLater(this,this::reloadAndStop,80L);
    }
    private void reloadAndStop(){
      try{
        check(!world.isChunkLoaded(x>>4,z>>4),"fixture chunk is actually unloaded");world.getChunkAt(x>>4,z>>4).load();
        for(int i=8;i<=10;i++)check(Arrays.equals(saved(block(i).getState()),(i==10?head:banner).serializeAsBytes()),"chunk reload anchor "+i+" exact saved bytes");
        world.save();getDataFolder().mkdirs();Files.writeString(getDataFolder().toPath().resolve("restart-ready"),"ready\n");
        getLogger().info("BANNER_PHASE1_COMPLETE checks="+checks);
      }catch(Throwable failure){getLogger().log(Level.SEVERE,"BANNER_FAILED checks="+checks,failure);}
      finally{Bukkit.shutdown();}
    }
    private void run(){
        try{
            boolean restart=Files.exists(getDataFolder().toPath().resolve("restart-ready"));
            // Setup must preserve anchor blocks on the second process.
            setup();
            if(restart){anchors();getLogger().info("BANNER_COMPLETE checks="+checks+" full-restart=true");Bukkit.shutdown();}
            else phaseOne();
        }catch(Throwable failure){getLogger().log(Level.SEVERE,"BANNER_FAILED checks="+checks,failure);Bukkit.shutdown();}
    }
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,this::run,120L);}
}
