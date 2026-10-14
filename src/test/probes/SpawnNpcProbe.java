import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.*;
import java.util.*;

public class SpawnNpcProbe extends JavaPlugin {
 ClassLoader loader;JavaPlugin core;Object npcs,store,quests;Player player;Inventory top;PlayerInventory inventory;InventoryView view;World world;UUID uuid;Location location;
 java.util.concurrent.CountDownLatch diskRelease=new java.util.concurrent.CountDownLatch(1);
 int stage,checks,skinWaits;boolean admin=true;List<Object> messages=new ArrayList<>();String visit,design,week;long balance;ItemStack purchased;
 Class<?> type(String name)throws Exception{return Class.forName("net.nikosnook.core."+name,true,loader);}
 Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 Object call(Object o,String name,Object...a)throws Exception{Class<?> t=o instanceof Class<?> c?c:o.getClass();for(var m:t.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==a.length){m.setAccessible(true);try{return m.invoke(o instanceof Class<?>?null:o,a);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;throw e;}}throw new NoSuchMethodException(name);}
 void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;getLogger().info("NPC_PASS "+name);}
 List<?> offers()throws Exception{return (List<?>)call(store,"vendorOffers",visit);}
 long money()throws Exception{return (long)call(((Optional<?>)call(store,"account",uuid)).orElseThrow(),"cents");}
 void click(int slot,ClickType kind)throws Exception{var e=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,slot,kind,InventoryAction.PICKUP_ALL);call(npcs,"click",e);check(e.isCancelled(),"menu cancels vanilla transfer "+stage+" "+kind);}
 void setup()throws Exception{
  core=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NookCore");loader=core.getClass().getClassLoader();npcs=field(core,"spawnNpcs");store=field(core,"store");quests=field(core,"weeklyQuests");
  check(npcs!=null,"NPC adapter initialises with FancyNpcs on Paper 26.2");world=Bukkit.getWorlds().getFirst();location=new Location(world,0.5,121,0.5);world.getBlockAt(0,120,0).setType(Material.STONE,false);
  var handle=new ServerPlayer(((CraftServer)getServer()).getServer(),((CraftWorld)world).getHandle(),new GameProfile(UUID.randomUUID(),"NpcTester"),ClientInformation.createDefault());var real=handle.getBukkitEntity();uuid=real.getUniqueId();inventory=real.getInventory();top=Bukkit.createInventory(null,27);
  player=(Player)Proxy.newProxyInstance(getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
   case "getUniqueId"->uuid;case "getName"->"NpcTester";case "getLocation"->location.clone();case "getWorld"->world;case "isOnline"->true;case "isDead"->false;case "getGameMode"->GameMode.SURVIVAL;case "hasPermission"->admin;case "getOpenInventory"->view;
   case "openInventory"->{top=(Inventory)a[0];yield view;}case "closeInventory"->{top=Bukkit.createInventory(null,27);yield null;}case "sendMessage"->{messages.add(Arrays.toString(a));yield null;}
   default->{try{yield m.invoke(real,a);}catch(InvocationTargetException e){throw e.getCause();}}
  });
  view=(InventoryView)Proxy.newProxyInstance(getClassLoader(),new Class[]{InventoryView.class},(o,m,a)->switch(m.getName()){
   case "getTopInventory"->top;case "getBottomInventory"->inventory;case "getPlayer"->player;case "getType"->InventoryType.CHEST;case "convertSlot"->(int)a[0]<27?(int)a[0]:(int)a[0]-27;case "getItem"->(int)a[0]<27?top.getItem((int)a[0]):inventory.getItem((int)a[0]-27);case "getInventory"->(int)a[0]<27?top:inventory;default->null;
  });
  call(store,"join",uuid,"NpcTester",System.currentTimeMillis(),6000L);
  call(npcs,"command",player,new String[]{"npc","place","niko"});
  check(!((Map<?,?>)field(npcs,"locations")).containsKey("niko"),"placement is not applied before save acknowledgement");
  call(npcs,"command",player,new String[]{"npc","place","pip"});
  check(messages.toString().contains("still being saved"),"overlapping placement waits rather than losing an earlier position");
 }
 void placed()throws Exception {
  call(npcs,"tick");
  visit=(String)call(call(npcs,"visit",System.currentTimeMillis()),"id");week=(String)call(quests,"nikoWeek");
  check(((Map<?,?>)field(npcs,"npcs")).size()==2,"both stationary NPCs register");call(npcs,"tick");check(((Map<?,?>)field(npcs,"npcs")).size()==2,"repeated tick does not duplicate NPCs");
  check(offers().size()==7,"seven persisted products");check(offers().stream().anyMatch(x->{try{return call(x,"design").equals("cctv-camera");}catch(Exception e){throw new RuntimeException(e);}}),"CCTV appears in first collection");
  check(!(boolean)call(store,"questAccepted",uuid,week),"local test begins before weekly acceptance");
  call(quests,"onCommand",player,core.getCommand("quests"),"quests",new String[0]);check(messages.toString().contains("Speak to Niko"),"remote quests provides Niko directions before acceptance");
  call(npcs,"openNiko",player);check(top.getItem(22).getType()==Material.LIME_DYE,"Niko offers explicit acceptance");check(top.getItem(4).getType()==Material.YELLOW_DYE,"Niko prominently shows quests not yet accepted");
  check(!(boolean)call(store,"questAccepted",uuid,week),"opening menu alone does not accept");click(22,ClickType.LEFT);
 }
 void heads()throws Exception{
  var item=purchased.clone();item.setAmount(1);var second=ItemStack.deserializeBytes(item.serializeAsBytes());check(item.isSimilar(second),"collectible serialisation preserves exact item");
  var before=world.getBlockAt(3,121,0).getState();var block=before.getBlock();block.setType(Material.PLAYER_HEAD,false);
  var place=new BlockPlaceEvent(block,before,block.getRelative(0,-1,0),item,player,true,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(place);
  var skull=(Skull)block.getState();check(skull.getPersistentDataContainer().has(new NamespacedKey("nookcore","pip-placed-item"),PersistentDataType.BYTE_ARRAY),"placement copies canonical item into skull state");
  var dropped=world.dropItem(location,new ItemStack(Material.PLAYER_HEAD));var drops=new BlockDropItemEvent(block,skull,player,new ArrayList<>(List.of(dropped)));Bukkit.getPluginManager().callEvent(drops);
  check(dropped.getItemStack().isSimilar(item) && dropped.getItemStack().getAmount()==1,"breaking produces exactly one matching collectible");dropped.remove();
  block.setType(Material.PLAYER_WALL_HEAD,false);var wallBefore=block.getState();Bukkit.getPluginManager().callEvent(new BlockPlaceEvent(block,wallBefore,block.getRelative(-1,0,0),item,player,true,EquipmentSlot.HAND));
  check(((Skull)block.getState()).getPersistentDataContainer().has(new NamespacedKey("nookcore","pip-placed-item"),PersistentDataType.BYTE_ARRAY),"wall placement retains metadata");
  var b=world.getBlockAt(4,121,0);var old=b.getState();b.setType(Material.PLAYER_HEAD,false);var cancelled=new BlockPlaceEvent(b,old,b.getRelative(0,-1,0),item,player,true,EquipmentSlot.HAND);cancelled.setCancelled(true);Bukkit.getPluginManager().callEvent(cancelled);
  check(!((Skull)b.getState()).getPersistentDataContainer().has(new NamespacedKey("nookcore","pip-placed-item"),PersistentDataType.BYTE_ARRAY),"cancelled placement cannot create collectible marker");
  // NookShops' actual canonical encoding must match after placement/breaking.
  var restored=ItemStack.deserializeBytes(skull.getPersistentDataContainer().get(new NamespacedKey("nookcore","pip-placed-item"),PersistentDataType.BYTE_ARRAY));
  check(Arrays.equals((byte[])call(type("NativeInventoryCodec"),"saleItem",item),(byte[])call(type("NativeInventoryCodec"),"saleItem",restored)),"placed and recovered head remains compatible with exact-item player shops");
 }
 void tick(){try{
  switch(stage++){
   case 0->{setup();stage=-2;}
   case -2->{
       if(field(npcs,"pendingSave")!=null){stage--;break;}
       check(((Map<?,?>)field(npcs,"locations")).containsKey("niko"),"Niko appears after acknowledged save");
       call(field(npcs,"configWriter"),"close");
       var writeType=type("NpcConfigWriter$Write");
       Object delayed=Proxy.newProxyInstance(loader,new Class[]{writeType},(o,m,a)->{diskRelease.await();call(type("NpcConfigWriter"),"atomicWrite",a);return null;});
       var ctor=type("NpcConfigWriter").getDeclaredConstructor(writeType);ctor.setAccessible(true);
       var f=npcs.getClass().getDeclaredField("configWriter");f.setAccessible(true);f.set(npcs,ctor.newInstance(delayed));
       call(npcs,"command",player,new String[]{"npc","place","pip"});
   }
   case -1->{
       check(!((java.util.concurrent.CompletableFuture<?>)field(npcs,"pendingSave")).isDone(),"server ticks continue while Pip disk save is deliberately stalled");
       check(!((Map<?,?>)field(npcs,"locations")).containsKey("pip"),"stalled save cannot prematurely publish Pip location");
       diskRelease.countDown();stage=-3;
   }
   case -3->{if(field(npcs,"pendingSave")!=null){stage=-3;break;}placed();stage=1;}
   case 1->{check((boolean)call(store,"questAccepted",uuid,week),"confirmation accepts current week");call(npcs,"openNiko",player);check(top.getItem(22).getType()==Material.MAP,"accepted menu shows progress");check(top.getItem(4).getType()==Material.LIME_DYE,"Niko prominently shows accepted quests");check(messages.toString().contains("already active for this week"),"Niko acknowledges returning players in chat");call(npcs,"openPip",player,true);check(top.getSize()==27,"read-only preview opens");check(top.getItem(16)!=null,"seventh product occupies centred row");check(top.getItem(10).getItemMeta().displayName().toString().contains("in stock"),"stock visible in product name");click(10,ClickType.LEFT);}
   case 2->{check(field(top.getHolder(),"kind").equals("pip"),"preview cannot buy");call(npcs,"openPip",player,false);balance=money();design=(String)call(offers().getFirst(),"design");click(10,ClickType.SHIFT_LEFT);}
   case 3->{check(money()==balance && field(top.getHolder(),"kind").equals("pip"),"shift-click cannot extract display or charge");click(10,ClickType.LEFT);stage=39;}
   case 39->{click(15,ClickType.LEFT);stage=40;}
   case 40->{check(top.getItem(13).getAmount()==2,"quantity selector shows a stack of two");check(top.getItem(13).getItemMeta().displayName().toString().contains("2"),"quantity appears in head name");click(11,ClickType.LEFT);stage=4;}
   case 4->{check(field(top.getHolder(),"kind").equals("checkout"),"selecting product opens exact-price confirmation");check(money()==balance,"opening confirmation has no charge");click(22,ClickType.LEFT);click(22,ClickType.LEFT);}
   case 5->{long price=(long)call(offers().getFirst(),"price");check(money()==balance-price,"double click charges once");check(((List<?>)call(store,"vendorReviews")).isEmpty(),"actual player-data save completes delivery receipt");purchased=inventory.getItem(0).clone();check(purchased.getType()==Material.PLAYER_HEAD && purchased.getAmount()==1,"purchase gives one actual decorative head");heads();call(npcs,"openPip",player,false);click(10,ClickType.LEFT);}
   case 6->{balance=money();for(int i=0;i<36;i++)inventory.setItem(i,new ItemStack(Material.STONE,64));click(22,ClickType.LEFT);}
   case 7->{check(money()==balance,"full inventory is not charged");check(((List<?>)call(store,"vendorReviews")).isEmpty(),"full inventory creates no pending receipt");inventory.clear();location=location.clone().add(20,0,0);click(22,ClickType.LEFT);}
   case 8->{check(money()==balance,"moving away invalidates checkout");location=location.clone().add(-20,0,0);call(npcs,"openPip",player,false);var event=new InventoryDragEvent(view,null,new ItemStack(Material.STONE),false,Map.of(10,new ItemStack(Material.STONE)));call(npcs,"drag",event);check(event.isCancelled(),"drag cannot replace product display");admin=false;var old=((Map<?,?>)field(npcs,"locations")).get("pip");location=location.clone().add(3,0,0);call(npcs,"command",player,new String[]{"npc","place","pip"});check(old.equals(((Map<?,?>)field(npcs,"locations")).get("pip")),"ordinary player cannot relocate NPCs");location=location.clone().add(-3,0,0);admin=true;}
   case 9->{
       var skinMap=(Map<?,?>)field(npcs,"skins");boolean skinsReady=skinMap.size()==2;
       for(var skin:skinMap.values())skinsReady &= (boolean)call(skin,"hasTexture");
       if(!skinsReady && skinWaits++<20){stage--;Bukkit.getScheduler().runTaskLater(this,this::tick,40L);return;}
       check(skinsReady,"both supplied PNG skins have signed textures ready");
       call(npcs,"command",player,new String[]{"vendor","catalogue"});check(top.getSize()==54 && java.util.Arrays.stream(top.getContents()).filter(java.util.Objects::nonNull).count()==24,"all 24 designs are available in read-only catalogue review");
       int stock=(int)call(offers().getFirst(),"stock");call(npcs,"close");
       var ctor=type("SpawnNpcs").getDeclaredConstructor(JavaPlugin.class,type("NookStore"),type("WeeklyQuests"),java.util.function.BooleanSupplier.class);ctor.setAccessible(true);npcs=ctor.newInstance(core,store,quests,(java.util.function.BooleanSupplier)()->true);call(npcs,"tick");
       check((boolean)call(store,"questAccepted",uuid,week),"NPC recreation preserves quest acceptance");check((int)call(offers().getFirst(),"stock")==stock,"NPC recreation preserves stock");check(((Map<?,?>)field(npcs,"npcs")).size()==2,"NPC recreation restores both persisted locations once");
       var config=(org.bukkit.configuration.file.YamlConfiguration)field(npcs,"config");config.set("test-start",System.currentTimeMillis()-8*86400000L);call(npcs,"tick");check(((Map<?,?>)field(npcs,"npcs")).size()==1,"Pip departs while Niko remains");check(money()==balance,"departure does not charge or change balance");getLogger().info("NPC_COMPLETE checks="+checks);Bukkit.shutdown();return;}
  }
  Bukkit.getScheduler().runTaskLater(this,this::tick,3L);
 }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"NPC_FAILED",e);diskRelease.countDown();Bukkit.shutdown();}}
 public void onEnable(){Bukkit.getScheduler().runTaskLater(this,this::tick,140L);}
}
