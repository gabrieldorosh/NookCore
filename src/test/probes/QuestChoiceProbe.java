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

public class QuestChoiceProbe extends JavaPlugin implements org.bukkit.event.Listener {
 Map<String,Integer> announcements=new HashMap<>();
 @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR) public void announced(org.bukkit.event.player.PlayerAdvancementDoneEvent e){if(e.getAdvancement().getKey().getNamespace().equals("nikosnook") && e.message()!=null)announcements.merge(e.getAdvancement().getKey().toString(),1,Integer::sum);}
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
  getServer().getPluginManager().registerEvents(this,this);
  npcs=field(core,"spawnNpcs");store=field(core,"store");quests=field(core,"weeklyQuests");check(npcs!=null,"FancyNpcs adapter initialized");
  set(quests,"clock",(LongSupplier)clock::get);set(quests,"week",null);week=(String)call(quests,"nikoWeek");
  world=Bukkit.getWorlds().getFirst();location=new Location(world,0.5,121,0.5);world.getBlockAt(0,120,0).setType(Material.STONE,false);
  ((Map<String,Location>)field(npcs,"locations")).put("niko",location.clone());
  ((Map<String,Location>)field(npcs,"locations")).put("pip",location.clone());
  var handle=new ServerPlayer(((CraftServer)getServer()).getServer(),((CraftWorld)world).getHandle(),new GameProfile(UUID.randomUUID(),"ChoiceTester"),ClientInformation.createDefault());var real=handle.getBukkitEntity();uuid=real.getUniqueId();inventory=real.getInventory();top=Bukkit.createInventory(null,27);
  player=(Player)Proxy.newProxyInstance(getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
   case "getUniqueId"->uuid;case "getName"->"ChoiceTester";case "getLocation"->location.clone();case "getWorld"->world;case "isOnline"->true;case "isDead"->false;case "getGameMode"->GameMode.SURVIVAL;case "hasPermission"->false;case "getOpenInventory"->view;
   case "openInventory"->{top=(Inventory)a[0];yield view;}case "closeInventory"->{top=Bukkit.createInventory(null,27);yield null;}case "sendMessage"->{for(Object value:a){
    if(value instanceof net.kyori.adventure.text.Component component)messages.add(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component));
    else messages.add(String.valueOf(value));
   }yield null;}
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
  check((long)call(((Optional<?>)call(store,"account",uuid)).orElseThrow(),"cents")==6075L,"Pip visit milestone pays approved one-time reward");
  check(announcements.getOrDefault("nikosnook:community/just_browsing",0)==1,"Pip milestone has one ordinary advancement chat event");
  check(pip.getDisplay().doesAnnounceToChat(),"fresh custom advancements permit normal chat and Discord relay");
  int hello=(int)messages.stream().filter(message->message.contains("hi there,")).count();call(npcs,"openPip",player,false);
  check(hello==1 && messages.stream().filter(message->message.contains("hi there,")).count()==1,"reopening Pip does not spam the private hello");
  check((long)call(((Optional<?>)call(store,"account",uuid)).orElseThrow(),"cents")==6075L,"Pip reopen cannot repeat the custom reward");
  Object shop=top.getHolder(),visit=call(npcs,"visit",System.currentTimeMillis());
  Object offer=((List<?>)call(store,"vendorOffers",call(visit,"id"))).getFirst();
  call(npcs,"checkout",player,shop,call(offer,"design"),1);
  var button=top.getItem(22);check(button.getType()==Material.PLAYER_HEAD,"checkout uses the money bag head for the labeled buy action");
  var buttonMeta=(org.bukkit.inventory.meta.SkullMeta)button.getItemMeta();
  check(buttonMeta.getOwnerProfile()!=null && buttonMeta.getOwnerProfile().getTextures().getSkin()!=null,"buy button carries a vanilla client head texture");
  check(buttonMeta.getPersistentDataContainer().isEmpty(),"checkout icon has no sale-item identity tags");
  check(buttonMeta.displayName().toString().contains("Buy 1 for"),"custom button retains the action and price label");
  check((long)call(((Optional<?>)call(store,"account",uuid)).orElseThrow(),"cents")==6075L,"showing the icon charges nothing");
  messages.clear();core.getCommand("baltop").execute(player,"baltop",new String[0]);
  check(messages.toString().contains("niko's top earners! 1/1"),"leaderboard uses the requested title and page count");
  call(quests,"onCommand",Bukkit.getConsoleSender(),core.getCommand("quests"),"quests",new String[]{"validate"});
  call(quests,"onCommand",Bukkit.getConsoleSender(),core.getCommand("quests"),"quests",new String[]{"reload"});
  check(((Optional<?>)call(store,"savedQuestRotation","2026-11-11")).isEmpty(),"validate and reload do not publish future snapshots");
  var bankFile=new java.io.File(core.getDataFolder(),"quest-rotation.yml");String bankBefore=java.nio.file.Files.readString(bankFile.toPath());Object loaded=field(quests,"automatic");
  try{
   var broken=new org.bukkit.configuration.file.YamlConfiguration();broken.load(bankFile);String first=broken.getConfigurationSection("regular").getKeys(false).iterator().next();broken.set("regular."+first+".amount",-1);broken.save(bankFile);
   call(quests,"onCommand",Bukkit.getConsoleSender(),core.getCommand("quests"),"quests",new String[]{"reload"});check(field(quests,"automatic")==loaded,"invalid bank reload retains last loaded configuration");
  }finally{java.nio.file.Files.writeString(bankFile.toPath(),bankBefore);}
  check(((List<?>)call(quests,"nikoGoals")).size()==11,"invalid bank leaves current choice board available");
  call(npcs,"openNiko",player);
  check(((List<?>)field(top.getHolder(),"offers")).size()==11,"nine regular offers and two challenges displayed");
  check(((Map<?,?>)field(top.getHolder(),"questSlots")).size()==11,"every offer has its own distinct slot");
  check(top.getItem(25).getType()==Material.GRAY_DYE,"confirmation begins disabled-looking");
  check(!(boolean)call(store,"questAccepted",uuid,week),"opening board does not accept");
  check(Bukkit.getAdvancement(new NamespacedKey("nikosnook","quests/a_little_choice"))!=null,"themed advancement registered on Paper237");
  click(25,ClickType.LEFT);
 }
 @SuppressWarnings("unchecked") void tick(){try{
  switch(stage++){
   case 0->{setup();}
   case 1->{check(!(boolean)call(store,"questAccepted",uuid,week),"incomplete confirmation cannot accept");click(10,ClickType.SHIFT_LEFT);}
   case 2->{check(choices().isEmpty(),"shift click cannot select or extract");for(int slot:new int[]{9,10,11,12,13,14})click(slot,ClickType.LEFT);}
   case 3->{check(choices().size()==6,"six regular choices retained across deferred clicks");check(top.getItem(9).getType()==Material.ENCHANTED_BOOK && (!top.getItem(9).getItemMeta().hasEnchantmentGlintOverride() || top.getItem(9).getItemMeta().getEnchantmentGlintOverride()),"selected state is visibly highlighted");check(top.getItem(9).getType()==Material.ENCHANTED_BOOK,"selected regular quests are enchanted books");click(15,ClickType.LEFT);click(21,ClickType.LEFT);click(23,ClickType.LEFT);}
   case 4->{check(choices().size()==7,"seventh regular refused and one challenge retained");String first=(String)((Map<?,?>)field(top.getHolder(),"questSlots")).get(21);String second=(String)((Map<?,?>)field(top.getHolder(),"questSlots")).get(23);check(!choices().contains(first)&&choices().contains(second),"choosing second challenge replaces first");check(top.getItem(25).getType()==Material.WRITABLE_BOOK,"complete selection uses a book and quill for confirmation");accepted=choices();messages.clear();click(25,ClickType.LEFT);click(25,ClickType.LEFT);}
   case 5->{check(messages.toString().contains("thanks for stopping by! get questing!"),"Niko thanks the player after accepting their quests");
    check(!messages.toString().contains("NookQuests - Refresh:"),"accepting quests does not dump progress into chat");
    check((boolean)call(store,"questAccepted",uuid,week),"confirmation accepted once");check(accepted.equals(call(store,"questChoices",uuid,week)),"saved choices match selected menu");check(((List<?>)call(store,"playerQuestGoals",uuid,week)).size()==7,"remote progress has only seven personal tasks");check((long)call(((Optional<?>)call(store,"account",uuid)).orElseThrow(),"cents")==6150L,"choice milestone pays approved one-time reward");check(announcements.getOrDefault("nikosnook:quests/a_little_choice",0)==1,"choice milestone has one advancement chat event");
    check(!(boolean)call(quests,"nikoComplete",uuid,week),"accepted incomplete personal week is not complete");
    var chosen=(List<?>)call(store,"playerQuestGoals",uuid,week);
    for(var goal:chosen){
     String kind=(String)call(goal,"kind"),target=(String)call(goal,"target");int amount=(int)call(goal,"amount");
     if(kind.equals("DEPOSIT")){UUID receipt=UUID.randomUUID();call(store,"beginQuestDeposit",receipt,uuid,week,target,amount,"probe-before","probe-after",clock.get());call(store,"finishQuestDeposit",receipt,clock.get());}
     else for(int i=0;i<amount;i++)call(store,"advanceQuests",uuid,week,kind,target,"probe:"+call(goal,"id")+":"+i,clock.get());
    }
    check((boolean)call(quests,"nikoComplete",uuid,week),"completion uses the seven selected quests only");
    call(quests,"milestone","COMMUNITY_CONTRIBUTION",player);
    check(announcements.getOrDefault("nikosnook:community/a_helping_hand",0)==1,"community milestone uses the normal advancement event");
    messages.clear();call(npcs,"openNiko",player);check(top.getItem(25).getType()==Material.MAP,"accepted board offers progress instead of another confirmation");
    check(messages.toString().contains("you've completed all of this week's quests!"),"Niko recognises a completed personal week");
    check((long)call(((Optional<?>)call(store,"account",uuid)).orElseThrow(),"cents")==18525L,"six quests challenge and once-only completion milestone pay exact total");
    call(npcs,"openNiko",player);check((long)call(((Optional<?>)call(store,"account",uuid)).orElseThrow(),"cents")==18525L,"reopening completed Niko does not repeat rewards");
    check(announcements.getOrDefault("nikosnook:quests/all_in_a_weeks_work",0)==1,"completed week announces once");
    messages.clear();call(quests,"onCommand",player,core.getCommand("quests"),"quests",new String[]{});
    check(messages.toString().contains("NookQuests - Refresh:"),"quest header includes refresh date");
    check(!messages.toString().contains("no weekly refund") && !messages.toString().contains("donated materials stay"),"quest summary uses concise contribution copy");
    click(10,ClickType.LEFT);}
   case 6->{check(choices().equals(accepted),"accepted choices cannot be toggled");
    call(npcs,"tick");var actual=(de.oliver.fancynpcs.api.Npc)((Map<?,?>)field(npcs,"npcs")).get("pip");check(actual!=null,"real FancyNpcs Pip adapter is available for idle pose");
    var idle=field(npcs,"pipIdle");long now=System.nanoTime();call(idle,"update",actual,false,now);call(idle,"update",actual,false,now+10_000_000_000L);
    check(actual.getData().getAttributes().containsValue("sitting") && !(boolean)field(idle,"disabled"),"real Paper26.3 NPC accepts the optional sitting pose");
    call(idle,"standOnClick",actual,now+11_000_000_000L);check(actual.getData().getAttributes().containsValue("standing"),"real NPC stands again without changing shop data");
    uuid=UUID.randomUUID();call(store,"join",uuid,"SecondChoiceTester",clock.get(),0L);call(npcs,"openNiko",player);check(choices().isEmpty(),"other player gets an independent blank selection");location=location.clone().add(20,0,0);click(10,ClickType.LEFT);}
   case 7->{check(choices().isEmpty(),"moving away prevents choice changes");location=location.clone().add(-20,0,0);clock.addAndGet(7*86400000L);messages.clear();click(25,ClickType.LEFT);}
   case 8->{check(messages.toString().contains("refreshed"),"stale menu explains weekly rollover");check(!(boolean)call(store,"questAccepted",uuid,week),"stale menu does not accept old week");String next=(String)call(quests,"nikoWeek");check(!next.equals(week)&&!(boolean)call(store,"questAccepted",uuid,next),"stale menu does not accept next week");getLogger().info("CHOICE_COMPLETE checks="+checks);Bukkit.shutdown();return;}
  }
  Bukkit.getScheduler().runTaskLater(this,this::tick,3L);
 }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"CHOICE_FAILED",e);Bukkit.shutdown();}}
 public void onEnable(){Bukkit.getScheduler().runTaskLater(this,this::tick,180L);}
}
