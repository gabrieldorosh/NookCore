import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.metadata.*;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

public class RecoveryModeProbe extends JavaPlugin {
 Object mode,state;ClassLoader loader;int checks;JavaPlugin core;List<Player> online=new ArrayList<>();World world;
 Class<?> type(String n)throws Exception{return Class.forName("net.nikosnook.core."+n,true,loader);}
 Object make(Class<?> t,Class<?>[] types,Object...a)throws Exception{var c=t.getDeclaredConstructor(types);c.setAccessible(true);return c.newInstance(a);}
 Object call(Object o,String name,Object...a)throws Exception{Class<?> t=o instanceof Class<?> c?c:o.getClass();for(var m:t.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==a.length){m.setAccessible(true);try{return m.invoke(o instanceof Class<?>?null:o,a);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;throw e;}}throw new NoSuchMethodException(name);}
 void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;getLogger().info("STAFF_PASS "+name);}
 boolean active(User u)throws Exception{return (boolean)call(type("StaffMode"),"active",u.id);}
 boolean rejected(Object o,String method,Object...args)throws Exception{try{call(o,method,args);return false;}catch(IllegalArgumentException e){return true;}}
 class User {
  UUID id=UUID.randomUUID();String name;boolean staff,visible=true,flight,flying,denyTeleport,cancelMode,kicked;GameMode game=GameMode.SURVIVAL;Location location;Player player;Inventory opened;Map<String,List<MetadataValue>> meta=new HashMap<>();Set<UUID> hidden=new HashSet<>();List<String> order=new ArrayList<>();List<Object> messages=new ArrayList<>();
  ItemStack[] contents=new ItemStack[41];PlayerInventory inv;
  User(String name,boolean staff){this.name=name;this.staff=staff;location=new Location(world,.5,121,.5);
   inv=(PlayerInventory)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PlayerInventory.class},(o,m,a)->switch(m.getName()){case "getSize"->41;case "getItem"->contents[(int)a[0]];default->null;});
   player=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
    case "getServer"->Bukkit.getServer();case "getUniqueId"->id;case "getName"->name;case "getWorld"->world;case "getLocation"->location.clone();case "getGameMode"->game;
    case "setGameMode"->{order.add("mode");if(!cancelMode)game=(GameMode)a[0];yield null;}
    case "isOnline"->true;case "isVisibleByDefault"->visible;case "setVisibleByDefault"->{visible=(boolean)a[0];order.add("visibility");yield null;}
    case "getAllowFlight"->flight;case "isFlying"->flying;case "setAllowFlight"->{flight=(boolean)a[0];yield null;}case "setFlying"->{flying=(boolean)a[0];yield null;}
    case "hasPermission"->staff && ((String)a[0]).startsWith("nookcore.staff.");case "getInventory"->inv;
    case "setMetadata"->{meta.put((String)a[0],List.of((MetadataValue)a[1]));yield null;}case "hasMetadata"->meta.containsKey(a[0]);case "getMetadata"->meta.getOrDefault(a[0],List.of());case "removeMetadata"->{meta.remove(a[0]);yield null;}
    case "hidePlayer"->{hidden.add(((Player)a[1]).getUniqueId());yield null;}case "showPlayer"->{hidden.remove(((Player)a[1]).getUniqueId());yield null;}case "canSee"->!hidden.contains(((Player)a[0]).getUniqueId());
    case "teleport"->{if(denyTeleport)yield false;location=((Location)a[0]).clone();yield true;}
    case "openInventory"->{opened=(Inventory)a[0];yield null;}case "closeInventory"->{opened=null;yield null;}case "sendMessage","sendActionBar"->{messages.add(a[0]);yield null;}
    case "getIdleDuration"->java.time.Duration.ZERO;case "getStatistic"->0;case "kick"->{kicked=true;yield null;}case "hashCode"->id.hashCode();case "equals"->o==a[0];
    default->{var t=m.getReturnType();if(t==boolean.class)yield false;if(t==int.class)yield 0;if(t==float.class)yield 0F;if(t==double.class)yield 0D;if(t==long.class)yield 0L;yield null;}
   });online.add(player);
  }
 }
 void run()throws Exception{
  core=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NookCore");check(core!=null&&core.isEnabled(),"NookCore starts with staff mode");loader=core.getClass().getClassLoader();world=Bukkit.getWorlds().getFirst();world.getBlockAt(0,120,0).setType(Material.STONE,false);
  for(var r:HandlerList.getRegisteredListeners(core))if(r.getListener().getClass().getSimpleName().equals("StaffMode"))HandlerList.unregisterAll(r.getListener());
  Files.createDirectories(getDataFolder().toPath());Path file=getDataFolder().toPath().resolve("state-"+UUID.randomUUID()+".properties");state=make(type("StaffState"),new Class[]{Path.class},file);
  mode=make(type("StaffMode"),new Class[]{JavaPlugin.class,type("StaffState"),Supplier.class},core,state,(Supplier<Collection<? extends Player>>)()->online);
  User helper=new User("Helper",true),observer=new User("StaffViewer",true),regular=new User("Regular",false),spectator=new User("Spectator",false);spectator.game=GameMode.SPECTATOR;
  check(rejected(mode,"enter",regular.player)&&!active(regular),"regular player cannot enter staff mode");Location original=helper.location.clone();call(mode,"enter",helper.player);
  check(active(helper)&&helper.game==GameMode.SPECTATOR&&!helper.visible,"entry is spectator and hidden by default");check(helper.order.indexOf("visibility")<helper.order.indexOf("mode"),"visibility changes before spectator mode");
  check(regular.hidden.contains(helper.id)&&spectator.hidden.contains(helper.id)&&!observer.hidden.contains(helper.id),"regular players including spectators hidden; staff see colleague");check(helper.meta.get("vanished").getFirst().asBoolean(),"standard vanished metadata available to TAB and integrations");
  var ping=new com.destroystokyo.paper.event.server.PaperServerListPingEvent((com.destroystokyo.paper.network.StatusClient)Proxy.newProxyInstance(getClassLoader(),new Class[]{com.destroystokyo.paper.network.StatusClient.class},(o,m,a)->switch(m.getName()){case "getAddress"->new java.net.InetSocketAddress("127.0.0.1",12345);case "getProtocolVersion"->1;case "isLegacy"->false;default->null;}),Component.empty(),4,20,"test",1,null){public java.util.Iterator<Player> iterator(){return new ArrayList<>(online).iterator();}};
  ping.getPlayerSample().add(Bukkit.createProfile(helper.id,"Helper"));ping.getPlayerSample().add(Bukkit.createProfile(regular.id,"Regular"));call(mode,"ping",ping);check(ping.getNumPlayers()==3&&ping.getPlayerSample().stream().noneMatch(x->x.getId().equals(helper.id)),"public ping count and sample omit hidden staff");
  Path discordJar=getDataFolder().toPath().resolve("DiscordSRV.jar.reference");
  try(var cl=new java.net.URLClassLoader(new java.net.URL[]{discordJar.toUri().toURL()},getClassLoader())){var api=cl.loadClass("github.scarsz.discordsrv.hooks.vanish.VanishHook");Object hook=call(type("StaffMode"),"discordHook",api,core);check((boolean)api.getMethod("isVanished",Player.class).invoke(hook,helper.player)&&!(boolean)api.getMethod("isVanished",Player.class).invoke(hook,regular.player),"installed DiscordSRV vanish API identifies hidden staff");}
  check(regular.messages.size()==1 && regular.messages.getFirst().toString().contains("left the game"),"disconnect presence sends one departure message without staff details");
  check(observer.messages.toString().contains("left the game"),"helpers also receive simulated departure");
  var formatted=(Component)call(type("StaffMode"),"presenceMessage",helper.player,false);
  check(formatted.children().getFirst().color()!=null,"departure uses configured player name colour");
  for(String alias:List.of("/w","/msg","/tell","/minecraft:w","/minecraft:msg","/minecraft:tell")){
    var whisper=new PlayerCommandPreprocessEvent(regular.player,alias+" Helper test");call(mode,"commands",whisper);check(whisper.isCancelled(),alias+" cannot target hidden staff");
  }
  regular.messages.clear();var hiddenWhisper=new PlayerCommandPreprocessEvent(regular.player,"/w Helper test");call(mode,"commands",hiddenWhisper);String hiddenReply=regular.messages.toString();
  regular.messages.clear();var missingWhisper=new PlayerCommandPreprocessEvent(regular.player,"/w NobodyHere test");call(mode,"commands",missingWhisper);check(hiddenReply.equals(regular.messages.toString()),"hidden and absent whisper targets give identical responses");
  var visibleWhisper=new PlayerCommandPreprocessEvent(regular.player,"/w StaffViewer test");call(mode,"commands",visibleWhisper);check(!visibleWhisper.isCancelled(),"normal whispers to visible players still pass through");
  var outgoingWhisper=new PlayerCommandPreprocessEvent(helper.player,"/w Regular test");call(mode,"commands",outgoingWhisper);check(outgoingWhisper.isCancelled(),"hidden staff cannot reveal themselves through outgoing whispers");
  var legacyChat=new AsyncPlayerChatEvent(false,helper.player,"hidden text",new HashSet<>());call(mode,"legacyChat",legacyChat);check(legacyChat.isCancelled(),"legacy chat relay source blocked while hidden");
  check(call(make(type("StaffState"),new Class[]{Path.class},file),"get",helper.id)!=null,"return state survives reload");check(rejected(mode,"enter",helper.player),"repeat entry preserves original return");
  var gm=new PlayerGameModeChangeEvent(helper.player,GameMode.CREATIVE);call(mode,"gameMode",gm);check(gm.isCancelled(),"active staff cannot change mode outside safe exit");
  var joined=new PlayerJoinEvent(helper.player,Component.text("joined"));call(mode,"join",joined);check(joined.joinMessage()==null&&active(helper),"rejoin preserves invisibility and suppresses public join");
  var quit=new PlayerQuitEvent(helper.player,Component.text("quit"));call(mode,"quit",quit);check(quit.quitMessage()==null&&call(state,"get",helper.id)!=null,"quit keeps saved state and suppresses message");
  var list=new PlayerCommandPreprocessEvent(regular.player,"/list");regular.messages.clear();call(mode,"commands",list);check(list.isCancelled()&&!regular.messages.toString().contains("Helper"),"public player list excludes hidden staff");
  var tab=new org.bukkit.event.server.TabCompleteEvent(regular.player,"/msg H",new ArrayList<>(List.of("Helper","Regular")));call(mode,"tab",tab);check(tab.getCompletions().equals(List.of("Regular")),"public command completions remove hidden name");
  var atab=new com.destroystokyo.paper.event.server.AsyncTabCompleteEvent(regular.player,new ArrayList<>(List.of("Helper","Regular")),"/msg H",true,null);call(mode,"asyncTab",atab);check(atab.getCompletions().equals(List.of("Regular")),"async completions use hidden-name snapshot");
  var pub=new PlayerCommandPreprocessEvent(helper.player,"/me hello");call(mode,"commands",pub);check(pub.isCancelled(),"public action messages blocked while hidden");
  var text=Component.text("hello");var publicChat=new io.papermc.paper.event.player.AsyncChatEvent(false,helper.player,new HashSet<>(),io.papermc.paper.chat.ChatRenderer.defaultRenderer(),text,text,net.kyori.adventure.chat.SignedMessage.system("hello",text));call(mode,"chat",publicChat);check(publicChat.isCancelled(),"public chat blocked while hidden");
  var field=core.getClass().getDeclaredField("chat");field.setAccessible(true);Object chat=field.get(core);call(chat,"refresh",helper.player);check(call(chat,"placeholder",helper.id,"rank").toString().contains("on duty"),"staff TAB rank carries on-duty marker");
  helper.messages.clear();call(mode,"onCommand",helper.player,core.getCommand("staffmode"),"staffmode",new String[]{"inspect","Regular"});check(helper.messages.toString().contains("/invsee"),"retired inspection directs to independent InvSee++");
  helper.location=new Location(world,10,130,10);helper.denyTeleport=true;check(rejected(mode,"leave",helper.player)&&active(helper)&&helper.game==GameMode.SPECTATOR,"cancelled return remains hidden with saved point");helper.denyTeleport=false;
  helper.staff=false;call(mode,"leave",helper.player);check(!active(helper)&&helper.location.equals(original)&&helper.game==GameMode.SURVIVAL,"revoked staff permission still permits safe return and exit");check(helper.visible&&!regular.hidden.contains(helper.id)&&!helper.meta.containsKey("vanished"),"exit restores visibility and removes owned vanish metadata");check(call(make(type("StaffState"),new Class[]{Path.class},file),"get",helper.id)==null,"completed exit clears durable state");
  check(!helper.player.hasPermission("nookcore.admin")&&!helper.player.hasPermission("nookcore.travel.bypass"),"helper mode does not require broad admin or dimension bypass");
  User blocked=new User("BlockedHelper",true);blocked.cancelMode=true;check(rejected(mode,"enter",blocked.player)&&blocked.kicked&&active(blocked)&&call(state,"get",blocked.id)!=null,"cancelled spectator entry disconnects staff and preserves return state");
  check(core.getCommand("staff")!=null && core.getCommand("staff").getAliases().contains("sc") && core.getCommand("staff").getExecutor().getClass().getSimpleName().equals("StaffChat"),"staff chat and sc alias registered with executor");
  check(Boolean.TRUE.equals(Bukkit.getPluginManager().getPermission("nookcore.staff.use").getChildren().get("nookcore.staff.chat")) && Boolean.TRUE.equals(Bukkit.getPluginManager().getPermission("nookcore.admin").getChildren().get("nookcore.staff.chat")),"existing helper and admin permissions inherit staff chat");
  helper.staff=true;helper.messages.clear();observer.messages.clear();regular.messages.clear();
  Object staffChat=make(type("StaffChat"),new Class[]{Supplier.class},(Supplier<Collection<? extends Player>>)()->online);
  call(staffChat,"onCommand",helper.player,core.getCommand("staff"),"sc",new String[]{"Private","check"});
  check(helper.messages.size()==1 && observer.messages.size()==1 && regular.messages.isEmpty(),"off-duty staff chat reaches only staff once");
  call(mode,"enter",helper.player);helper.messages.clear();observer.messages.clear();regular.messages.clear();
  call(staffChat,"onCommand",helper.player,core.getCommand("staff"),"staff",new String[]{"Hidden","check"});
  check(active(helper) && helper.messages.size()==1 && observer.messages.size()==1 && regular.messages.isEmpty(),"hidden staff can use private chat without public delivery");
  helper.messages.clear();observer.messages.clear();
  call(staffChat,"onCommand",regular.player,core.getCommand("staff"),"sc",new String[]{"Denied"});
  check(helper.messages.isEmpty() && observer.messages.isEmpty(),"ordinary player cannot send staff messages");
  call(mode,"leave",helper.player);
  var sf=core.getClass().getDeclaredField("store");sf.setAccessible(true);check(sf.get(core)==null,"recovery never opens the economy database");
  check(Files.readString(core.getDataFolder().toPath().resolve("nooks.db")).equals("RECOVERY-SENTINEL-NOT-A-DATABASE"),"existing database bytes remain untouched");
  check(!Bukkit.getPluginManager().isPluginEnabled("ChestShop"),"ChestShop is disabled before transactions");
  var econ=Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class).getProvider();
  check(!econ.depositPlayer("Regular",1).transactionSuccess() && !econ.withdrawPlayer("Regular",1).transactionSuccess(),"external Vault writes fail without a database");
  for(String command:List.of("nooks","baltop","plots","quests","nookshops","bunny")){
      regular.messages.clear();core.getCommand(command).getExecutor().onCommand(regular.player,core.getCommand(command),command,new String[0]);
      check(regular.messages.toString().contains("Temporarily disabled"),command+" reports temporary recovery pause");
  }
  var block=world.getBlockAt(5,121,0);block.setType(Material.CHEST,false);var chest=(Chest)block.getState();
  chest.getPersistentDataContainer().set(new NamespacedKey("nookcore","native-shop"),org.bukkit.persistence.PersistentDataType.STRING,"preserved-shop");chest.update();
  var broken=new org.bukkit.event.block.BlockBreakEvent(block,regular.player);Bukkit.getPluginManager().callEvent(broken);check(broken.isCancelled(),"marked shop chest cannot be broken during recovery");
  var interaction=new PlayerInteractEvent(regular.player,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,null,block,BlockFace.UP,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(interaction);check(interaction.isCancelled(),"marked shop chest reports pause instead of opening");
  check(call(chat,"placeholder",helper.id,"balance_whole").equals("Paused"),"TAB display distinguishes paused economy from zero balance");
  getLogger().info("STAFF_COMPLETE checks="+checks);
 }
 public void onEnable(){Bukkit.getScheduler().runTask(this,()->{try{run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"STAFF_FAILED",e);}finally{Bukkit.shutdown();}});}
}
