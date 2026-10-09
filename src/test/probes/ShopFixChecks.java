import java.lang.reflect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

final class ShopFixChecks {
 static int checks;
 static void check(boolean ok,String name,JavaPlugin log){if(!ok)throw new AssertionError(name);checks++;log.getLogger().info("PASS "+name);}
 static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static Object call(Object o,String n,Class<?>[] types,Object...args)throws Exception{var m=o.getClass().getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(o,args);}
 static boolean rejected(Object o,String n,Class<?>[] types,Object...args)throws Exception{try{call(o,n,types,args);return false;}catch(InvocationTargetException e){if(e.getCause() instanceof IllegalArgumentException)return true;throw e;}}
 static void run(JavaPlugin log)throws Exception{
  var core=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NookCore");Object store=field(core,"store");
  check(Bukkit.getPluginManager().isPluginEnabled("ChestShop"),"real ChestShop plugin enabled",log);
  Object shops=HandlerList.getRegisteredListeners(core).stream().map(r->r.getListener()).filter(l->l.getClass().getSimpleName().equals("NativeShops")).findFirst().orElseThrow();
  var world=Bukkit.getWorlds().getFirst();var chestBlock=world.getBlockAt(0,100,0);chestBlock.setType(Material.CHEST,false);
  var signBlock=world.getBlockAt(1,100,0);signBlock.setType(Material.OAK_WALL_SIGN,false);Sign sign=(Sign)signBlock.getState();sign.setLine(0,"Building supplies");sign.update(true,false);
  call(shops,"noChestShop",new Class[]{Block.class},chestBlock);check(true,"ordinary nearby label does not disable NookShop",log);
  sign=(Sign)signBlock.getState();sign.setLine(0,"TestPlayer");sign.setLine(1,"1");sign.setLine(2,"B 1");sign.setLine(3,"Cobblestone");sign.update(true,false);
  check(rejected(shops,"noChestShop",new Class[]{Block.class},chestBlock),"actual ChestShop-format sign remains blocked",log);
  signBlock.setType(Material.AIR,false);
  Container chest=(Container)chestBlock.getState();chest.getPersistentDataContainer().set(new NamespacedKey("nookcore","native-shop"),PersistentDataType.STRING,UUID.randomUUID().toString());chest.update(true,false);
  var labelClass=core.getClass().getClassLoader().loadClass("net.nikosnook.core.ShopLabels");var reject=labelClass.getDeclaredMethod("rejectNative",InventoryHolder.class);reject.setAccessible(true);
  boolean blocked=false;try{reject.invoke(null,chestBlock.getState());}catch(InvocationTargetException e){blocked=e.getCause() instanceof IllegalArgumentException;}
  check(blocked,"ChestShop create/trade guard rejects marked native containers",log);
  Player player=(Player)Proxy.newProxyInstance(log.getClass().getClassLoader(),new Class[]{Player.class},(o,m,a)->m.getName().equals("isSneaking")?true:null);
  core.getConfig().set("native-shops-enabled",true);
  var placement=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,new ItemStack(Material.OAK_SIGN),chestBlock,org.bukkit.block.BlockFace.EAST,EquipmentSlot.HAND);
  call(shops,"interact",new Class[]{PlayerInteractEvent.class},placement);check(!placement.isCancelled(),"sneaking sign placement bypasses the shop menu",log);
  var denied=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,new ItemStack(Material.OAK_SIGN),chestBlock,org.bukkit.block.BlockFace.EAST,EquipmentSlot.HAND);denied.setCancelled(true);
  call(shops,"interact",new Class[]{PlayerInteractEvent.class},denied);check(denied.isCancelled(),"existing protection cancellation stays cancelled",log);
  for(var pair:Map.of("small",3000L,"medium",5000L,"large",9000L).entrySet())call(store,"definePlot",new Class[]{String.class,long.class},pair.getKey(),pair.getValue());
  UUID owner=UUID.randomUUID();call(store,"join",new Class[]{UUID.class,String.class,long.class,long.class},owner,"Renter",0L,100000L);
  call(store,"rent",new Class[]{String.class,UUID.class,long.class},"small",owner,0L);
  Object before=call(store,"plot",new Class[]{String.class},"small");
  Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"plots admin rent-cut");
  check((long)call(call(store,"plot",new Class[]{String.class},"small"),"weekly",new Class[]{})==3000,"console preview does not change prices",log);
  Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"plots admin rent-cut confirm");
  for(var pair:Map.of("small",600L,"medium",1000L,"large",1800L).entrySet())check((long)call(call(store,"plot",new Class[]{String.class},pair.getKey()),"weekly",new Class[]{})==pair.getValue(),"confirmed reduced price "+pair.getKey(),log);
  Object after=call(store,"plot",new Class[]{String.class},"small");check(call(before,"owner",new Class[]{}).equals(call(after,"owner",new Class[]{})) && call(before,"paidUntil",new Class[]{}).equals(call(after,"paidUntil",new Class[]{})),"existing owner and paid-through date preserved",log);
  Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"plots admin rent-cut confirm");check((long)call(call(store,"plot",new Class[]{String.class},"small"),"weekly",new Class[]{})==600,"second confirm cannot discount again",log);
  check((long)call(store,"defaultPlotRent",new Class[]{long.class},81L)==600,"new automatic setup quotes use lower price",log);
  try(var files=java.nio.file.Files.list(core.getDataFolder().toPath().resolve("backups"))){check(files.anyMatch(p->p.getFileName().toString().startsWith("before-rent-cut-")),"database backup saved before repricing",log);}
  log.getLogger().info("SHOP_FIX_PASS checks="+checks);
 }
}
