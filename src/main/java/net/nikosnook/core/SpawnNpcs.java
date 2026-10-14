package net.nikosnook.core;

import de.oliver.fancynpcs.api.*;
import de.oliver.fancynpcs.api.skins.SkinData;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

/** Niko acceptance and Pip's stationary first release. All transactions run on the main thread. */
final class SpawnNpcs implements Listener,AutoCloseable {
    private final JavaPlugin plugin;
    private final NookStore store;
    private final WeeklyQuests quests;
    private final BooleanSupplier healthy;
    private final Path file;
    private final YamlConfiguration config;
    private final List<VendorCatalogue.Design> catalogue=new ArrayList<>();
    private final Map<String,Npc> npcs=new HashMap<>();
    private final Map<String,Location> locations=new HashMap<>();
    private final Map<String,SkinData> skins=new HashMap<>();
    private final Map<UUID,Recovery> recoveries=new HashMap<>();
    private boolean closed, vendorPaused, initializing;
    private final NpcConfigWriter configWriter=new NpcConfigWriter();
    private java.util.concurrent.CompletableFuture<Void> pendingSave;
    private Runnable afterSave;
    private CommandSender saveRequester;
    private long saveStarted;
    private boolean saveWarning;
    private int tickCounter;
    private org.bukkit.scheduler.BukkitTask ticker;
    private String problem="",lastCatalogueError="";
    private record Recovery(UUID receipt,boolean delivered,String reason,long expiry) {}
    private static final class Menu implements InventoryHolder {
        final UUID player;final String kind,rotation,design;final int quantity;final boolean preview;
        Inventory inventory;
        Menu(UUID player,String kind,String rotation,String design,int quantity,boolean preview){this.player=player;this.kind=kind;this.rotation=rotation;this.design=design;this.quantity=quantity;this.preview=preview;}
        public Inventory getInventory(){return inventory;}
    }
    SpawnNpcs(JavaPlugin plugin,NookStore store,WeeklyQuests quests,BooleanSupplier healthy)throws Exception {
        this.plugin=plugin;this.store=store;this.quests=quests;this.healthy=healthy;
        file=plugin.getDataFolder().toPath().resolve("spawn-npcs.yml");
        if(!Files.exists(file))plugin.saveResource("spawn-npcs.yml",false);
        config=new YamlConfiguration();config.load(file.toFile());
        if(!config.getBoolean("enabled",false))return;
        if(!Bukkit.getPluginManager().isPluginEnabled("FancyNpcs"))throw new IllegalStateException("Install FancyNpcs 2.12.1 to enable Niko and Pip.");
        var products=Objects.requireNonNull(config.getConfigurationSection("catalogue"),"Missing Pip catalogue");
        for(String id:products.getKeys(false)){
            var c=products.getConfigurationSection(id);if(!c.getBoolean("enabled",true))continue;
            catalogue.add(new VendorCatalogue.Design(id,c.getString("name"),c.getString("texture"),VendorCatalogue.Rarity.valueOf(c.getString("rarity")),c.getString("category"),new HashSet<>(c.getStringList("seasons")),c.getBoolean("year-round",true),c.getBoolean("modern",false),c.getLong("price-cents"),c.getInt("stock-per-allocation"),c.getInt("cap-per-visit",0),c.getString("source","")));
        }
        if(config.getBoolean("test-mode") && config.getLong("test-start",0)==0){config.set("test-start",System.currentTimeMillis());initializing=true;save(config.saveToString(),()->initializing=false,null);}
        for(String role:List.of("niko","pip")){Location loc=config.getLocation("locations."+role);if(loc!=null)locations.put(role,loc);}
        store.configureNikoGate(true,config.getBoolean("test-mode"),System.currentTimeMillis());
        quests.nikoDirections(()->directions("niko"));
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(String role:List.of("niko","pip"))loadSkin(role);
        ticker=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            if(closed)return;
            pollSave();
            if(tickCounter++ % 100 != 0)return;
            try{tick();}catch(Exception ex){problem=ex.getMessage();plugin.getLogger().log(Level.SEVERE,"NPC update failed; Pip purchases paused",ex);vendorPaused=true;}
        },1,1);
    }
    private boolean enabled(){return config.getBoolean("enabled",false) && !closed && !initializing;}
    private void save(String snapshot,Runnable success,CommandSender requester){
        if(pendingSave!=null)throw new IllegalArgumentException("An NPC position is still being saved. Please wait.");
        afterSave=success;saveRequester=requester;saveStarted=System.nanoTime();saveWarning=false;
        pendingSave=configWriter.save(file,snapshot);
    }
    // Only immutable text crosses threads; all Bukkit state and messages stay on the server thread.
    private void pollSave(){
        if(pendingSave==null)return;
        if(!pendingSave.isDone()){
            if(!saveWarning && System.nanoTime()-saveStarted>java.util.concurrent.TimeUnit.SECONDS.toNanos(10)){
                saveWarning=true;plugin.getLogger().warning("NPC settings save is taking longer than expected. Check storage; placement is pending.");
                if(saveRequester!=null)saveRequester.sendMessage(NookUi.problem("NPCs","Saving is taking longer than expected. The new position has not been applied yet."));
            }
            return;
        }
        var completed=pendingSave;var success=afterSave;var requester=saveRequester;
        pendingSave=null;afterSave=null;saveRequester=null;
        try{completed.join();}
        catch(java.util.concurrent.CompletionException ex){
            problem="NPC settings could not be saved";
            plugin.getLogger().log(Level.SEVERE,problem+"; previous positions retained",ex.getCause());
            if(requester!=null)requester.sendMessage(NookUi.problem("NPCs","Could not save the NPC position. The previous position is unchanged; check the server log."));
            return;
        }
        try{success.run();}catch(Exception ex){plugin.getLogger().log(Level.SEVERE,"NPC position saved but could not be applied",ex);if(requester!=null)requester.sendMessage(NookUi.problem("NPCs","Position saved, but the NPC could not be placed. Check the server log."));}
    }
    private void loadSkin(String role){
        String name=config.getString("skins."+role,role+".png");
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
            try{
                var skin=FancyNpcsPlugin.get().getSkinManager().getByFile(name,SkinData.SkinVariant.valueOf(config.getString("skin-models."+role,"SLIM")));
                Bukkit.getScheduler().runTask(plugin,()->applySkin(role,skin));
            }catch(Exception e){plugin.getLogger().warning("Could not load "+role+" skin; check FancyNpcs/skins/"+name+". "+e.getMessage());}
        });
    }
    private void applySkin(String role,SkinData skin){
        if(closed || skin==null || !skin.hasTexture() && skins.containsKey(role) && skins.get(role).hasTexture())return;
        skins.put(role,skin);remove(role);
    }
    @EventHandler public void skinGenerated(de.oliver.fancynpcs.api.skins.SkinGeneratedEvent e){
        for(String role:List.of("niko","pip"))if(config.getString("skins."+role,role+".png").equals(e.getSkin().getIdentifier()))
            Bukkit.getScheduler().runTask(plugin,()->applySkin(role,e.getSkin()));
    }
    VendorSchedule.Visit visit(long now){
        if(!config.getBoolean("test-mode"))return VendorSchedule.at(now);
        long start=config.getLong("test-start");
        return new VendorSchedule.Visit("test-"+start,start,start+3600000,start+7*86400000L,start+8*86400000L);
    }
    private String season(long now){
        var date=Instant.ofEpochMilli(now).atZone(WeekSchedule.ZONE).toLocalDate();
        for(String key:Objects.requireNonNull(config.getConfigurationSection("seasons")).getKeys(false)){
            var s=config.getConfigurationSection("seasons."+key);
            var from=MonthDay.parse(s.getString("from"));var to=MonthDay.parse(s.getString("until"));var today=MonthDay.from(date);
            if(from.compareTo(to)<0?today.compareTo(from)>=0&&today.compareTo(to)<0:today.compareTo(from)>=0||today.compareTo(to)<0)return key;
        }
        return "none";
    }
    private List<NookStore.VendorOffer> offers(VendorSchedule.Visit v,long now)throws Exception {
        var saved=store.vendorOffers(v.id());
        if(saved.isEmpty()){
            String season=season(v.arrival());
            Set<String> pins=new HashSet<>();
            if(store.previousVendorDesigns(v.id()).isEmpty())pins.addAll(config.getStringList("first-visit-pins"));
            var chosen=VendorCatalogue.select(catalogue,season,pins,store.previousVendorDesigns(v.id()),v.id().hashCode());
            var items=chosen.stream().map(d->new NookStore.VendorOffer(v.id(),d.id(),d.name(),PipHeads.make(d).serializeAsBytes(),d.price(),d.allocation(),d.allocation(),d.cap())).toList();
            store.openVendorVisit(v,items,season);
        }
        if(saved.size()==6){
            var pins=new HashSet<>(saved.stream().map(NookStore.VendorOffer::design).toList());
            var chosen=VendorCatalogue.select(catalogue,season(v.arrival()),pins,Set.of(),v.id().hashCode());
            var d=chosen.stream().filter(x->!pins.contains(x.id())).findFirst().orElseThrow();
            store.expandVendorVisit(new NookStore.VendorOffer(v.id(),d.id(),d.name(),PipHeads.make(d).serializeAsBytes(),d.price(),d.allocation(),d.allocation(),d.cap()));
        }
        store.restockVendor(v.id(),now);return store.vendorOffers(v.id());
    }
    private void tick()throws Exception {
        if(!enabled() || !healthy.getAsBoolean())return;
        ensure("niko");var v=visit(System.currentTimeMillis());
        if(v.open(System.currentTimeMillis()) && !vendorPaused){
            try{offers(v,System.currentTimeMillis());lastCatalogueError="";ensure("pip");}
            catch(IllegalArgumentException ex){remove("pip");problem=ex.getMessage();if(!problem.equals(lastCatalogueError)){lastCatalogueError=problem;plugin.getLogger().warning("Pip catalogue unavailable: "+problem);}}
        }else remove("pip");
    }
    private void ensure(String role){
        var existing=npcs.get(role);
        if(existing!=null && FancyNpcsPlugin.get().getNpcManager().getNpcById(existing.getData().getId())!=existing)remove(role);
        if(npcs.containsKey(role) || !locations.containsKey(role) || !FancyNpcsPlugin.get().getNpcManager().isLoaded())return;
        var loc=locations.get(role);if(loc.getWorld()==null)return;
        var data=new NpcData("nookcore_"+role,UUID.nameUUIDFromBytes("NookCore NPCs".getBytes(java.nio.charset.StandardCharsets.UTF_8)),loc.clone());
        data.setDisplayName(role.equals("niko")?"<color:#fff0ad>Niko</color>":"<color:#fff0ad>Pip</color>").setCollidable(false).setShowInTab(false).setTurnToPlayer(false);
        if(skins.containsKey(role))data.setSkinData(skins.get(role));
        data.setInteractionCooldown(0.5f).setOnClick(player->Bukkit.getScheduler().runTask(plugin,()->{
            if(closed || !near(player,role))return;
            try{if(role.equals("niko"))openNiko(player);else openPip(player,false);}catch(Exception e){showError(player,e);}
        }));
        var npc=FancyNpcsPlugin.get().getNpcAdapter().apply(data);npc.setSaveToFile(false);
        npc.create();FancyNpcsPlugin.get().getNpcManager().registerNpc(npc);npcs.put(role,npc);npc.spawnForAll();
    }
    private void remove(String role){var npc=npcs.remove(role);if(npc!=null){npc.removeForAll();FancyNpcsPlugin.get().getNpcManager().removeNpc(npc);}}
    private boolean near(Player p,String role){var loc=locations.get(role);return enabled() && healthy.getAsBoolean() && p.isOnline() && !p.isDead() && !StaffMode.active(p.getUniqueId()) && p.getGameMode()==GameMode.SURVIVAL && loc!=null && p.getWorld().equals(loc.getWorld()) && p.getLocation().distanceSquared(loc)<=36;}
    private String directions(String role){var loc=locations.get(role);return loc==null?"Niko is being set up at spawn. Ask an admin for help with this week's quests.":"Speak to Niko at spawn: "+loc.getBlockX()+", "+loc.getBlockY()+", "+loc.getBlockZ()+" ("+loc.getWorld().getName()+").";}
    private static ItemStack icon(Material type,String title,String...lore){
        var item=new ItemStack(type);var meta=item.getItemMeta();meta.displayName(NookUi.text(title).decoration(TextDecoration.ITALIC,false));meta.lore(Arrays.stream(lore).map(s->NookUi.text(s).decoration(TextDecoration.ITALIC,false)).toList());item.setItemMeta(meta);return item;
    }
    private Menu menu(Player p,String kind,String rotation,String design,int quantity,boolean preview,String title){var m=new Menu(p.getUniqueId(),kind,rotation,design,quantity,preview);m.inventory=Bukkit.createInventory(m,27,Component.text(title));return m;}
    void openNiko(Player p)throws Exception {
        if(!near(p,"niko"))throw new IllegalArgumentException(directions("niko"));
        var week=quests.nikoWeek();boolean accepted=store.questAccepted(p.getUniqueId(),week);var m=menu(p,"niko",week,"",0,false,accepted?"Niko · Quests accepted":"Niko · Accept your quests");int slot=10;
        for(var goal:quests.nikoGoals())m.inventory.setItem(slot++,icon(goal.reward()==3000?Material.NETHER_STAR:Material.BOOK,goal.title(),store.questProgress(p.getUniqueId(),week,goal.id())+" / "+goal.amount(),"Reward: "+Money.format(goal.reward())));
        m.inventory.setItem(4,icon(accepted?Material.LIME_DYE:Material.YELLOW_DYE,accepted?"Quests accepted":"Quests not accepted",accepted?"Your weekly quests are active.":"Accept below to start this week's quests.",accepted?"Use /quests to check your progress anywhere.":"All six quests and the challenge."));
        m.inventory.setItem(22,icon(accepted?Material.MAP:Material.LIME_DYE,accepted?"View my progress":"Accept this week's quests",accepted?"Use /quests anywhere.":"All six quests and the challenge.","Rewards arrive automatically."));p.openInventory(m.inventory);
        p.sendMessage(NookUi.message("Niko",accepted?"Your quests are already active for this week. You can check your progress here or with /quests.":"Ready for this week's quests? Click Accept this week's quests below to get started."));
    }
    void openPip(Player p,boolean preview)throws Exception {
        if(!preview && !near(p,"pip"))throw new IllegalArgumentException("Visit Pip at her stall to shop.");
        if(!enabled())throw new IllegalArgumentException("NPC testing is disabled in spawn-npcs.yml.");
        var v=visit(System.currentTimeMillis());
        if(!preview && (!v.open(System.currentTimeMillis()) || vendorPaused))throw new IllegalArgumentException("Pip is away. Next arrival: "+NookUi.date(v.nextArrival()));
        if(!v.open(System.currentTimeMillis()))throw new IllegalArgumentException("Pip is away. Preview her catalogue during a visit, or use the isolated test-mode.");
        var m=menu(p,"pip",v.id(),"",0,preview,preview?"Pip · Catalogue preview":"Pip's Pop-up");int slot=10;
        for(var o:preview?store.vendorOffers(v.id()):offers(v,System.currentTimeMillis())){
            var item=ItemStack.deserializeBytes(o.item());var meta=item.getItemMeta();var lore=new ArrayList<Component>(meta.lore()==null?List.of():meta.lore());
            lore.add(NookUi.text(Money.format(o.price())+" each"));
            lore.add(NookUi.text(o.stock()==0?"Sold out":"Available stock: "+o.stock()));
            meta.displayName(NookUi.text(o.name()+" · "+(o.stock()==0?"Sold out":o.stock()+" in stock")).decoration(TextDecoration.ITALIC,false));
            if(o.cap()>0)lore.add(NookUi.text("Your remaining allowance: "+Math.max(0,o.cap()-store.vendorBought(p.getUniqueId(),v.id(),o.design()))));
            lore.add(NookUi.text(preview?"Preview only":o.stock()==0?"Check back after the next restock":"Click to choose a quantity"));meta.lore(lore);item.setItemMeta(meta);m.inventory.setItem(slot++,item);
        }
        m.inventory.setItem(22,icon(Material.CLOCK,"Pip's visit",(System.currentTimeMillis()<v.restock()?"Restock: "+NookUi.date(v.restock()):"This visit's final stock is out."),"Leaves: "+NookUi.date(v.departure()),config.getBoolean("test-mode")?"LOCAL TEST · trial prices and stock":"Prices stay fixed for this visit."));p.openInventory(m.inventory);
    }
    private void cataloguePreview(Player p){
        var m=new Menu(p.getUniqueId(),"catalogue","","",0,true);
        m.inventory=Bukkit.createInventory(m,54,Component.text("Pip � All test designs"));int slot=0;
        for(var d:catalogue.stream().limit(54).toList()){
            var item=PipHeads.make(d);var meta=item.getItemMeta();var lore=new ArrayList<Component>(meta.lore());
            lore.add(NookUi.text("Trial price: "+Money.format(d.price())));lore.add(NookUi.text("Preview only � "+d.id()));meta.lore(lore);item.setItemMeta(meta);m.inventory.setItem(slot++,item);
        }
        p.openInventory(m.inventory);
    }
    private void checkout(Player p,Menu from,String design,int quantity)throws Exception {
        var o=store.vendorOffers(from.rotation).stream().filter(x->x.design().equals(design)).findFirst().orElseThrow();
        var m=menu(p,"checkout",from.rotation,design,quantity,false,"Pip · Confirm purchase");
        if(o.stock()==0)throw new IllegalArgumentException(NookStore.vendorStockMessage(0));
        var head=ItemStack.deserializeBytes(o.item());head.setAmount(quantity);
        var meta=head.getItemMeta();meta.displayName(NookUi.text(o.name()+" × "+quantity).decoration(TextDecoration.ITALIC,false));
        var lore=new ArrayList<Component>(meta.lore()==null?List.of():meta.lore());lore.add(NookUi.text("Available stock: "+o.stock()));meta.lore(lore);head.setItemMeta(meta);
        m.inventory.setItem(13,head);
        m.inventory.setItem(11,icon(Material.RED_DYE,"Buy fewer"));m.inventory.setItem(15,icon(Material.LIME_DYE,"Buy more"));
        m.inventory.setItem(22,icon(Material.EMERALD,"Buy "+quantity+" for "+Money.format(Math.multiplyExact(o.price(),quantity)),"From your Nooks balance","You will receive "+quantity+" × "+o.name()));
        m.inventory.setItem(18,icon(Material.ARROW,"Back"));p.openInventory(m.inventory);
    }
    private void buy(Player p,Menu m)throws Exception {
        long now=System.currentTimeMillis();var v=visit(now);
        if(vendorPaused || !near(p,"pip") || !v.id().equals(m.rotation) || !v.open(now))throw new IllegalArgumentException("This visit has ended, or you have moved away from Pip.");
        var offer=store.vendorOffers(v.id()).stream().filter(o->o.design().equals(m.design)).findFirst().orElseThrow();
        var inv=new NativePlayerInventory(p);var plan=NativeInventoryPlan.deliver(inv.capture(),NativePlayerInventory.item(offer.item()),m.quantity);
        UUID receipt=store.beginVendorPurchase(p.getUniqueId(),v.id(),m.design,m.quantity,plan.beforeHash(),plan.afterHash(),now);
        try{inv.apply(plan.after());inv.persist(plan.after(),receipt);store.finishVendorPurchase(receipt);}
        catch(Exception ex){vendorPaused=true;plugin.getLogger().log(Level.SEVERE,"Pip purchase "+receipt+" needs review. Do not automatically replay or refund.",ex);throw new IllegalStateException("Purchase needs staff review. Do not retry; receipt "+receipt,ex);}
        p.sendMessage(NookUi.message("Pip","Thank you! "+m.quantity+" × "+offer.name()+" for "+Money.format(offer.price()*m.quantity)+"."));openPip(p,false);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof Menu m))return;e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p) || !m.player.equals(p.getUniqueId()) || e.getClickedInventory()!=m.inventory || !e.isLeftClick() || e.isShiftClick())return;
        int slot=e.getRawSlot();
        Bukkit.getScheduler().runTask(plugin,()->{
            if(closed || p.getOpenInventory().getTopInventory()!=m.inventory)return;
            try{
                if(m.kind.equals("niko") && slot==22){
                    if(!near(p,"niko"))throw new IllegalArgumentException(directions("niko"));
                    quests.nikoAccept(p,m.rotation);p.closeInventory();quests.onCommand(p,plugin.getCommand("quests"),"quests",new String[0]);
                }else if(m.kind.equals("pip") && !m.preview && slot>=10 && slot<17){
                    if(!near(p,"pip"))throw new IllegalArgumentException("Visit Pip at her stall to shop.");
                    var offers=store.vendorOffers(m.rotation);checkout(p,m,offers.get(slot-10).design(),1);
                }else if(m.kind.equals("checkout")){
                    if(!near(p,"pip"))throw new IllegalArgumentException("Visit Pip at her stall to shop.");
                    if(slot==22)buy(p,m);else if(slot==18)openPip(p,false);else if(slot==11 || slot==15)checkout(p,m,m.design,Math.clamp(m.quantity+(slot==15?1:-1),1,64));
                }
            }catch(Exception ex){showError(p,ex);}
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
    private void showError(Player p,Exception e){
        p.sendMessage(NookUi.problem("Niko's Nook",e instanceof IllegalArgumentException?e.getMessage():"This action needs staff attention. Nothing will be retried automatically."));
        if(!(e instanceof IllegalArgumentException))plugin.getLogger().log(Level.SEVERE,"NPC action failed",e);
    }
    String status(){return !enabled()?"NPCs: disabled":"NPCs: "+npcs.keySet()+"; skins ready "+skins.values().stream().filter(SkinData::hasTexture).count()+"/2; Pip "+(vendorPaused?"PAUSED":visit(System.currentTimeMillis()).open(System.currentTimeMillis())?"visiting":"away")+(problem.isBlank()?"":"; "+problem);}
    boolean command(CommandSender s,String[] a){
        if(!s.hasPermission("nookcore.admin")){s.sendMessage(NookUi.problem("NookAdmin","This command is for admins."));return true;}
        try{
            if(a.length==2 && a[1].equals("status")){s.sendMessage(NookUi.text(status()));return true;}
            if(!enabled())throw new IllegalArgumentException("Enable spawn-npcs.yml and restart the test server first.");
            if(a[0].equals("npc")){
                if(a.length==3 && a[1].equals("place") && Set.of("niko","pip").contains(a[2]) && s instanceof Player p){
                    String role=a[2];Location position=p.getLocation().clone();
                    var snapshot=new YamlConfiguration();snapshot.loadFromString(config.saveToString());snapshot.set("locations."+role,position);
                    save(snapshot.saveToString(),()->{
                        config.set("locations."+role,position);locations.put(role,position);remove(role);
                        try{tick();}catch(Exception ex){throw new IllegalStateException(ex);}
                        s.sendMessage(NookUi.message("NPCs","Placed "+role+" at your position."));
                    },s);
                    s.sendMessage(NookUi.message("NPCs","Saving "+role+"'s position…"));return true;
                }
                if(a.length==3 && a[1].equals("accept")){
                    var player=store.byName(a[2]).orElseThrow(()->new IllegalArgumentException("Unknown player."));String week=quests.nikoWeek();
                    store.acceptNiko(player.id(),week,"admin:"+s.getName(),System.currentTimeMillis());plugin.getLogger().info(s.getName()+" accepted Niko quests for "+player.name()+" in "+week);s.sendMessage(NookUi.message("NPCs","Accepted this week's quests for "+player.name()+". Existing progress is unchanged."));return true;
                }
                NookUi.help(s,"NPCs","/nookadmin npc place <niko|pip> — use your position","/nookadmin npc status","/nookadmin npc accept <player> — logged fallback if Niko is unavailable");return true;
            }
            if(a.length==2 && a[1].equals("catalogue") && s instanceof Player p){cataloguePreview(p);return true;}
            if(a.length==2 && a[1].equals("preview") && s instanceof Player p){openPip(p,true);return true;}
            if(a.length==2 && a[1].equals("review")){for(var r:store.vendorReviews())s.sendMessage(NookUi.text(r.id()+" · "+r.buyer()+" · "+r.quantity()+" × "+r.design()+" · "+Money.format(r.cents())+" · "+NookUi.date(r.time())));return true;}
            if(a.length>=4 && a[1].equals("recover")){
                UUID id=UUID.fromString(a[2]);
                if(a.length==4 && a[3].equals("confirm")){
                    var pending=recoveries.remove(actor(s));if(pending==null || !pending.receipt.equals(id) || pending.expiry<System.currentTimeMillis())throw new IllegalArgumentException("Preview the recovery again; confirmation expired.");
                    store.resolveVendor(id,pending.delivered,s.getName(),pending.reason,System.currentTimeMillis());vendorPaused=false;s.sendMessage(NookUi.message("Pip","Recovery recorded. No items were replayed."));return true;
                }
                if(a.length<5 || !Set.of("delivered","refund").contains(a[3]))throw new IllegalArgumentException("/nookadmin vendor recover <receipt> <delivered|refund> <reason>");
                var record=store.vendorReviews().stream().filter(r->r.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("No pending receipt with that ID."));
                String reason=String.join(" ",Arrays.copyOfRange(a,4,a.length));recoveries.put(actor(s),new Recovery(id,a[3].equals("delivered"),reason,System.currentTimeMillis()+60000));
                s.sendMessage(NookUi.text("Review "+record.quantity()+" × "+record.design()+" for "+Money.format(record.cents())+". Confirm only after checking whether delivery occurred. Refund restores Nooks and stock but does not remove an item."));s.sendMessage(NookUi.command("/nookadmin vendor recover "+id+" confirm"));return true;
            }
            NookUi.help(s,"Pip","/nookadmin vendor preview — read-only local catalogue","/nookadmin vendor status","/nookadmin vendor review","/nookadmin vendor recover <receipt> <delivered|refund> <reason>");
        }catch(Exception ex){s.sendMessage(NookUi.problem("NPCs",ex.getMessage()==null?"Action failed; check the log.":ex.getMessage()));if(!(ex instanceof IllegalArgumentException))plugin.getLogger().log(Level.SEVERE,"NPC administration failed",ex);}
        return true;
    }
    private static UUID actor(CommandSender s){return s instanceof Player p?p.getUniqueId():new UUID(0,0);}
    @Override public void close(){closed=true;configWriter.close();if(ticker!=null)ticker.cancel();HandlerList.unregisterAll(this);for(var role:List.copyOf(npcs.keySet()))remove(role);for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu)p.closeInventory();}
}
