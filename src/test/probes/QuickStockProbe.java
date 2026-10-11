import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import com.mojang.authlib.GameProfile;

public class QuickStockProbe extends ShopInteractionProbe {
 Inventory top;InventoryView view;Player player;PlayerInventory inventory;UUID owner,actor,id;Block chest;Object offer;int stage;
 void setup()throws Exception{
  var core=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NookCore");loader=core.getClass().getClassLoader();core.getConfig().set("native-shops-enabled",true);
  for(var r:HandlerList.getRegisteredListeners(core))if(r.getListener().getClass().getSimpleName().equals("NativeShops"))HandlerList.unregisterAll(r.getListener());
  Files.createDirectories(getDataFolder().toPath());store=make(type("NookStore"),new Class[]{Path.class},getDataFolder().toPath().resolve(UUID.randomUUID()+".db"));
  var world=Bukkit.getWorlds().getFirst();chest=world.getBlockAt(0,120,0);chest.setType(Material.CHEST,false);target=chest;
  shops=make(type("NativeShops"),new Class[]{JavaPlugin.class,type("NookStore"),BooleanSupplier.class,Function.class,Consumer.class},core,store,(BooleanSupplier)()->true,(Function<Block,String>)(b->"test"),(Consumer<Exception>)errors::add);
  var handle=new ServerPlayer(((CraftServer)getServer()).getServer(),((CraftWorld)world).getHandle(),new GameProfile(UUID.randomUUID(),"StockTest"),ClientInformation.createDefault());var real=handle.getBukkitEntity();inventory=real.getInventory();owner=real.getUniqueId();actor=owner;
  player=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
   case "getUniqueId"->actor;case "isOnline"->true;case "getGameMode"->GameMode.SURVIVAL;case "isDead"->false;case "getTargetBlockExact"->target;case "getOpenInventory"->view;
   case "openInventory"->{top=(Inventory)a[0];yield view;}case "closeInventory"->{top=Bukkit.createInventory(null,27);yield null;}case "sendMessage"->{messages.add(Arrays.toString(a));yield null;}
   default->{try{yield m.invoke(real,a);}catch(InvocationTargetException e){throw e.getCause();}}
  });
  view=(InventoryView)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{InventoryView.class},(o,m,a)->switch(m.getName()){
   case "getTopInventory"->top;case "getBottomInventory"->inventory;case "getPlayer"->player;case "getType"->InventoryType.CHEST;case "convertSlot"->0;case "getItem"->inventory.getItem(0);case "getInventory"->inventory;default->null;
  });
  long now=System.currentTimeMillis();call(store,"join",owner,"StockTest",now,10000L);call(store,"definePlot","test",600L);call(store,"rent","test",owner,now);
  offer=call(store,"publishNativeOffer",call(type("NativeShops"),"location",chest),"test",owner,new ItemStack(Material.STONE).serializeAsBytes(),1,100L,now);id=(UUID)call(offer,"id");
  var c=(Container)chest.getState();c.getPersistentDataContainer().set(new NamespacedKey("nookcore","native-shop"),PersistentDataType.STRING,id.toString());c.update(true,false);
 }
 int stock()throws Exception{return (int)call(call(store,"nativeOffer",id),"stock");}
 void shift(ClickType type,int amount,Material material,boolean duplicate)throws Exception{
  inventory.clear();inventory.setItem(0,new ItemStack(material,amount));call(shops,"open",player,call(store,"nativeOffer",id),actor.equals(owner));
  var e=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,27,type,InventoryAction.MOVE_TO_OTHER_INVENTORY);call(shops,"click",e);if(duplicate)call(shops,"click",e);check(e.isCancelled(),"physical transfer cancelled for case "+stage);
 }
 void tick(){try{
  switch(stage++){
   case 0->{setup();shift(ClickType.SHIFT_LEFT,32,Material.STONE,true);}
   case 1->{check(stock()==32 && inventory.getItem(0)==null && !(boolean)call(store,"nativeNeedsReview",owner),"first-screen shift-left saves 32 once despite duplicate click");shift(ClickType.SHIFT_RIGHT,12,Material.STONE,false);}
   case 2->{check(stock()==44 && inventory.getItem(0)==null,"first-screen shift-right deposits full stack");shift(ClickType.SHIFT_LEFT,5,Material.DIRT,false);}
   case 3->{check(stock()==44 && inventory.getItem(0).getAmount()==5,"wrong item unchanged");actor=UUID.randomUUID();shift(ClickType.SHIFT_LEFT,8,Material.STONE,false);}
   case 4->{check(stock()==44 && inventory.getItem(0).getAmount()==8,"customer cannot stock another player's shop");actor=owner;shift(ClickType.SHIFT_LEFT,7,Material.STONE,false);target=null;}
   case 5->{check(stock()==44 && inventory.getItem(0).getAmount()==7,"moving away before deposit preserves inventory");target=chest;inventory.clear();call(shops,"open",player,call(store,"nativeOffer",id),true);var close=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,22,ClickType.LEFT,InventoryAction.PICKUP_ALL);call(shops,"click",close);}
   case 6->{check(!(boolean)call(call(store,"nativeOffer",id),"closed"),"stock prevents immediate closure");check(top.getItem(13)!=null,"close screen offers collection");var collect=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,13,ClickType.LEFT,InventoryAction.PICKUP_ALL);call(shops,"click",collect);}
   case 7->{check(stock()==0 && (boolean)call(call(store,"nativeOffer",id),"closed"),"last collection closes shop");check(((Optional<?>)call(store,"nativeAt",call(type("NativeShops"),"location",chest))).isEmpty(),"empty closed shop releases location");check(!((Container)chest.getState()).getPersistentDataContainer().has(new NamespacedKey("nookcore","native-shop"),PersistentDataType.STRING),"retired container marker removed");check(inventory.getItem(0).getAmount()==44,"exact remaining stock saved to owner");check(errors.isEmpty(),"no storage failures");call(store,"close");getLogger().info("QUICK_COMPLETE checks="+checks);getServer().shutdown();return;}
  }
  getServer().getScheduler().runTaskLater(this,this::tick,2L);
 }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"QUICK_FAILED",e);getServer().shutdown();}}
 public void onEnable(){getServer().getScheduler().runTask(this,this::tick);}
}
