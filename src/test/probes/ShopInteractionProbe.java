import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.concurrent.atomic.*;

public class ShopInteractionProbe extends JavaPlugin implements Listener {
 ClassLoader loader;int checks,menus,delegates;Object store,shops;boolean sneaking;Block target;List<String> messages=new ArrayList<>();List<Exception> errors=new ArrayList<>();
 Class<?> type(String name)throws Exception{return Class.forName("net.nikosnook.core."+name,true,loader);}
 Object call(Object obj,String name,Object...args)throws Exception{Class<?> c=obj instanceof Class<?> t?t:obj.getClass();for(var m:c.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);try{return m.invoke(obj instanceof Class<?>?null:obj,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;throw e;}}throw new NoSuchMethodException(name);}
 Object make(Class<?> t,Class<?>[] types,Object...args)throws Exception{var c=t.getDeclaredConstructor(types);c.setAccessible(true);return c.newInstance(args);}
 void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;getLogger().info("INTERACTION_PASS "+name);}
 @EventHandler(priority=EventPriority.LOWEST) public void use(com.sk89q.worldguard.bukkit.event.block.UseBlockEvent e){delegates++;}
 PlayerInteractEvent click(Player player,Block block,EquipmentSlot hand,Material item){return new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,item==null?null:new ItemStack(item),block,BlockFace.EAST,hand);}
 void menu(Player p,Block b,String label)throws Exception{target=b;messages.clear();int before=menus,d=delegates;var e=click(p,b,EquipmentSlot.HAND,null);Bukkit.getPluginManager().callEvent(e);check(menus==before+1 && messages.isEmpty(),label+" opens exactly one menu without warning");check(e.useInteractedBlock()==Event.Result.DENY && e.useItemInHand()==Event.Result.DENY && delegates==d,label+" blocks physical access before WorldGuard");}
 void run()throws Exception{
  var core=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NookCore");loader=core.getClass().getClassLoader();core.getConfig().set("native-shops-enabled",true);
  // Replace only the isolated test's native listener; use a fresh receipt store and deterministic plot map.
  for(var r:HandlerList.getRegisteredListeners(core))if(r.getListener().getClass().getSimpleName().equals("NativeShops"))HandlerList.unregisterAll(r.getListener());
  Files.createDirectories(getDataFolder().toPath());store=make(type("NookStore"),new Class[]{Path.class},getDataFolder().toPath().resolve(UUID.randomUUID()+".db"));
  var world=Bukkit.getWorlds().getFirst();Block chest=world.getBlockAt(0,120,0);chest.setType(Material.CHEST,false);
  shops=make(type("NativeShops"),new Class[]{JavaPlugin.class,type("NookStore"),BooleanSupplier.class,Function.class,Consumer.class},core,store,(BooleanSupplier)()->true,(Function<Block,String>)(b->b.getX()<0?"outside":"test"),(Consumer<Exception>)errors::add);
  Bukkit.getPluginManager().registerEvents(this,this);
  UUID owner=UUID.randomUUID(),buyer=UUID.randomUUID();long now=System.currentTimeMillis();call(store,"join",owner,"Owner",now,10000L);call(store,"definePlot","test",600L);call(store,"rent","test",owner,now);
  Object offer=call(store,"publishNativeOffer",call(type("NativeShops"),"location",chest),"test",owner,new ItemStack(Material.STONE).serializeAsBytes(),1,100L,now);UUID id=(UUID)call(offer,"id");
  Container c=(Container)chest.getState();c.getPersistentDataContainer().set(new NamespacedKey("nookcore","native-shop"),PersistentDataType.STRING,id.toString());c.update(true,false);
  Player p=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
   case "getUniqueId"->buyer;case "getName"->"Customer";case "getWorld"->world;case "getLocation"->new Location(world,1,120,1);case "getGameMode"->GameMode.SURVIVAL;
   case "isOnline"->true;case "isSneaking"->sneaking;case "getTargetBlockExact"->target;case "openInventory"->{menus++;yield null;}case "sendMessage"->{messages.add(Arrays.toString(a));yield null;}
   case "getMetadata"->List.of();case "hashCode"->buyer.hashCode();case "equals"->o==a[0];case "toString"->"Customer";
   default->{Class<?> t=m.getReturnType();if(t==boolean.class)yield false;if(t==int.class)yield 0;if(t==long.class)yield 0L;if(t==double.class)yield 0D;if(t==float.class)yield 0F;yield null;}
  });
  menu(p,chest,"customer chest click");
  var wall=world.getBlockAt(1,120,0);wall.setType(Material.OAK_WALL_SIGN,false);var data=(org.bukkit.block.data.type.WallSign)wall.getBlockData();data.setFacing(BlockFace.EAST);wall.setBlockData(data,false);
  menu(p,wall,"customer wall-label click");check(call(call(shops,"nearby",p,id),"id").equals(id),"trade revalidation accepts targeted attached label");
  var standing=world.getBlockAt(0,121,0);standing.setType(Material.OAK_SIGN,false);menu(p,standing,"standing-label click");
  var hanging=world.getBlockAt(0,119,0);hanging.setType(Material.OAK_HANGING_SIGN,false);menu(p,hanging,"ceiling-hanging-label click");
  int before=menus;var denied=click(p,wall,EquipmentSlot.HAND,null);denied.setCancelled(true);call(shops,"interact",denied);check(menus==before && denied.isCancelled(),"pre-existing cancellation never opens a menu");
  sneaking=true;var edit=click(p,wall,EquipmentSlot.HAND,null);call(shops,"interact",edit);check(!edit.isCancelled() && menus==before,"sneak-edit left to normal protection");
  var placement=click(p,chest,EquipmentSlot.HAND,Material.OAK_SIGN);call(shops,"interact",placement);check(!placement.isCancelled() && menus==before,"sneak-placement left to normal protection");sneaking=false;
  var offhand=click(p,wall,EquipmentSlot.OFF_HAND,null);call(shops,"interact",offhand);check(menus==before,"offhand does not duplicate menu");
  var ordinary=world.getBlockAt(4,120,0);ordinary.setType(Material.CHEST,false);check(call(type("ShopLabels"),"target",ordinary)==null,"unmarked chest is never intercepted");
  data.setFacing(BlockFace.NORTH);wall.setBlockData(data,false);check(call(type("ShopLabels"),"target",wall)==null,"nearby label not attached to shop is ignored");data.setFacing(BlockFace.EAST);wall.setBlockData(data,false);
  Sign sign=(Sign)wall.getState();sign.setLine(0,"Customer");sign.setLine(1,"1");sign.setLine(2,"B 1");sign.setLine(3,"Stone");sign.update(true,false);check(call(type("ShopLabels"),"target",wall)==null,"ChestShop trading sign is never forwarded");wall.setType(Material.AIR,false);
  var outside=world.getBlockAt(-1,120,0);outside.setType(Material.OAK_WALL_SIGN,false);var west=(org.bukkit.block.data.type.WallSign)outside.getBlockData();west.setFacing(BlockFace.WEST);outside.setBlockData(west,false);messages.clear();var cross=click(p,outside,EquipmentSlot.HAND,null);call(shops,"interact",cross);check(menus==before && messages.size()==1 && cross.isCancelled(),"cross-plot label denied without opening physical block");
  check(errors.isEmpty(),"no storage or protection failures");call(store,"close");getLogger().info("INTERACTION_COMPLETE checks="+checks);
 }
 public void onEnable(){getServer().getScheduler().runTask(this,()->{try{run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"INTERACTION_FAILED",e);}finally{getServer().shutdown();}});}
}
