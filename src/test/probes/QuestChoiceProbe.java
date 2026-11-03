import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.time.Instant;

public class QuestChoiceProbe extends JavaPlugin {
 Object npcs,store,quests;JavaPlugin core;Player player;Inventory top;PlayerInventory inventory;InventoryView view;World world;UUID uuid;Location location;
 AtomicLong clock=new AtomicLong(Instant.parse("2026-11-04T03:00:00Z").toEpochMilli());String week;int stage,checks;List<String> messages=new ArrayList<>();Set<String> accepted;
 Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 void set(Object o,String n,Object value)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,value);}
 Object call(Object o,String name,Object...a)throws Exception{for(var m:o.getClass().getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==a.length){m.setAccessible(true);try{return m.invoke(o,a);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;throw e;}}throw new NoSuchMethodException(name);}
 void check(boolean b,String name){if(!b)throw new AssertionError(name);checks++;getLogger().info("CHOICE_PASS "+name);}
 void click(int slot,ClickType kind)throws Exception{var e=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,slot,kind,InventoryAction.PICKUP_ALL);call(npcs,"click",e);check(e.isCancelled(),"inventory transfer cancelled for "+kind+" slot "+slot);}
 @SuppressWarnings("unchecked") Set<String> choices()throws Exception{return new HashSet<>((Set<String>)field(top.getHolder(),"choices"));}
 @SuppressWarnings("unchecked") void setup()throws Exception{
  core=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NookCore");check(core!=null&&core.isEnabled(),"candidate NookCore enabled");
  npcs=field(core,"spawnNpcs");store=field(core,"store");quests=field(core,"weeklyQuests");check(npcs!=null,"FancyNpcs adapter initialized");
  set(quests,"clock",(LongSupplier)clock::get);set(quests,"week",null);week=(String)call(quests,"nikoWeek");
  world=Bukkit.getWorlds().getFirst();location=new Location(world,0.5,121,0.5);world.getBlockAt(0,120,0).setType(Material.STONE,false);
  ((Map<String,Location>)field(npcs,"locations")).put("niko",location.clone());
  ((Map<String,Location>)field(npcs,"locations")).put("pip",location.clone());
  var handle=new ServerPlayer(((CraftServer)getServer()).getServer(),((CraftWorld)world).getHandle(),new GameProfile(UUID.randomUUID(),"ChoiceTester"),ClientInformation.createDefault());var real=handle.getBukkitEntity();uuid=real.getUniqueId();inventory=real.getInventory();top=Bukkit.createInventory(null,27);
  player=(Player)Proxy.newProxyInstance(getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
   case "getUniqueId"->uuid;case "getName"->"ChoiceTester";case "getLocation"->location.clone();case "getWorld"->world;case "isOnline"->true;case "isDead"->false;case "getGameMode"->GameMode.SURVIVAL;case "hasPermission"->false;case "getOpenInventory"->view;
   case "openInventory"->{top=(Inventory)a[0];yield view;}case "closeInventory"->{top=Bukkit.createInventory(null,27);yield null;}case "sendMessage"->{messages.add(Arrays.toString(a));yield null;}
   default->{try{yield m.invoke(real,a);}catch(InvocationTargetException e){throw e.getCause();}}
  });
  view=(InventoryView)Proxy.newProxyInstance(getClassLoader(),new Class[]{InventoryView.class},(o,m,a)->switch(m.getName()){
   case "getTopInventory"->top;case "getBottomInventory"->inventory;case "getPlayer"->player;case "getType"->InventoryType.CHEST;case "convertSlot"->(int)a[0]<27?(int)a[0]:(int)a[0]-27;case "getItem"->(int)a[0]<27?top.getItem((int)a[0]):inventory.getItem((int)a[0]-27);case "getInventory"->(int)a[0]<27?top:inventory;default->null;
  });
  call(store,"join",uuid,"ChoiceTester",clock.get(),6000L);
  for(String id:new String[]{"quests/a_little_choice","community/just_browsing","community/a_helping_hand","quests/all_in_a_weeks_work"})check(Bukkit.getAdvancement(new NamespacedKey("nikosnook",id))!=null,"registered milestone "+id);
  var pip=Bukkit.getAdvancement(new NamespacedKey("nikosnook","community/just_browsing"));
  call(npcs,"openPip",player,true);check(!player.getAdvancementProgress(pip).isDone(),"admin catalogue preview does not grant Pip visit");
  call(npcs,"openPip",player,false);check(player.getAdvancementProgress(pip).isDone(),"ordinary Pip visit grants milestone without a purchase");
  check((long)call(((Optional<?>)call(store,"account",uuid)).orElseThrow(),"cents")==6000L,"Pip visit milestone pays no Nooks");
  call(quests,"onCommand",Bukkit.getConsoleSender(),core.getCommand("quests"),"quests",new String[]{"validate"});
  call(quests,"onCommand",Bukkit.getConsoleSender(),core.getCommand("quests"),"quests",new String[]{"reload"});
  check(((Optional<?>)call(store,"savedQuestRotation","2026-11-11")).isEmpty(),"validate and reload do not publish future snapshots");
  var bankFile=new java.io.File(core.getDataFolder(),"quest-rotation.yml");String bankBefore=java.nio.file.Files.readString(bankFile.toPath());Object loaded=field(quests,"automatic");
  try{
   var broken=new org.bukkit.configuration.file.YamlConfiguration();broken.load(bankFile);String first=broken.getConfigurationSection("regular").getKeys(false).iterator().next();broken.set("regular."+first+".amount",-1);broken.save(bankFile);
   call(quests,"onCommand",Bukkit.getConsoleSender(),core.getCommand("quests"),"quests",new String[]{"reload"});check(field(quests,"automatic")==loaded,"invalid bank reload retains last loaded configuration");
  }finally{java.nio.file.Files.writeString(bankFile.toPath(),bankBefore);}
  check(((List<?>)call(quests,"nikoGoals")).size()==10,"invalid bank leaves current choice board available");
  call(npcs,"openNiko",player);
  check(((List<?>)field(top.getHolder(),"offers")).size()==10,"eight regular offers and two challenges displayed");
  check(((Map<?,?>)field(top.getHolder(),"questSlots")).size()==10,"every offer has its own distinct slot");
  check(top.getItem(25).getType()==Material.GRAY_DYE,"confirmation begins disabled-looking");
  check(!(boolean)call(store,"questAccepted",uuid,week),"opening board does not accept");
  check(Bukkit.getAdvancement(new NamespacedKey("nikosnook","quests/a_little_choice"))!=null,"themed advancement registered on Paper237");
  click(25,ClickType.LEFT);
 }
 @SuppressWarnings("unchecked") void tick(){try{
  switch(stage++){
   case 0->{setup();}
   case 1->{check(!(boolean)call(store,"questAccepted",uuid,week),"incomplete confirmation cannot accept");click(10,ClickType.SHIFT_LEFT);}
   case 2->{check(choices().isEmpty(),"shift click cannot select or extract");for(int slot:new int[]{10,11,12,13,14,15})click(slot,ClickType.LEFT);}
   case 3->{check(choices().size()==6,"six regular choices retained across deferred clicks");check(top.getItem(10).getItemMeta().getEnchantmentGlintOverride(),"selected state is visibly highlighted");click(19,ClickType.LEFT);click(21,ClickType.LEFT);click(23,ClickType.LEFT);}
   case 4->{check(choices().size()==7,"seventh regular refused and one challenge retained");String first=(String)((Map<?,?>)field(top.getHolder(),"questSlots")).get(21);String second=(String)((Map<?,?>)field(top.getHolder(),"questSlots")).get(23);check(!choices().contains(first)&&choices().contains(second),"choosing second challenge replaces first");check(top.getItem(25).getType()==Material.LIME_DYE,"complete selection enables confirmation");accepted=choices();messages.clear();click(25,ClickType.LEFT);click(25,ClickType.LEFT);}
   case 5->{check((boolean)call(store,"questAccepted",uuid,week),"confirmation accepted once");check(accepted.equals(call(store,"questChoices",uuid,week)),"saved choices match selected menu");check(((List<?>)call(store,"playerQuestGoals",uuid,week)).size()==7,"remote progress has only seven personal tasks");check((long)call(((Optional<?>)call(store,"account",uuid)).orElseThrow(),"cents")==6000L,"acceptance and milestone mint no Nooks");call(npcs,"openNiko",player);check(top.getItem(25).getType()==Material.MAP,"accepted board offers progress instead of another confirmation");click(10,ClickType.LEFT);}
   case 6->{check(choices().equals(accepted),"accepted choices cannot be toggled");uuid=UUID.randomUUID();call(store,"join",uuid,"SecondChoiceTester",clock.get(),0L);call(npcs,"openNiko",player);check(choices().isEmpty(),"other player gets an independent blank selection");location=location.clone().add(20,0,0);click(10,ClickType.LEFT);}
   case 7->{check(choices().isEmpty(),"moving away prevents choice changes");location=location.clone().add(-20,0,0);clock.addAndGet(7*86400000L);messages.clear();click(25,ClickType.LEFT);}
   case 8->{check(messages.toString().contains("refreshed"),"stale menu explains weekly rollover");check(!(boolean)call(store,"questAccepted",uuid,week),"stale menu does not accept old week");String next=(String)call(quests,"nikoWeek");check(!next.equals(week)&&!(boolean)call(store,"questAccepted",uuid,next),"stale menu does not accept next week");getLogger().info("CHOICE_COMPLETE checks="+checks);Bukkit.shutdown();return;}
  }
  Bukkit.getScheduler().runTaskLater(this,this::tick,3L);
 }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"CHOICE_FAILED",e);Bukkit.shutdown();}}
 public void onEnable(){Bukkit.getScheduler().runTaskLater(this,this::tick,80L);}
}
