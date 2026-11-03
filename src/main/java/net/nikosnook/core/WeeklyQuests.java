package net.nikosnook.core;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.block.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.*;
import net.kyori.adventure.text.Component;

final class WeeklyQuests implements Listener,CommandExecutor,TabCompleter {
    private Supplier<String> nikoDirections=()->"Speak to Niko at spawn to get your quests!";
    void nikoDirections(Supplier<String> directions){nikoDirections=directions;}
    String nikoWeek()throws Exception{if(!enabled || !healthy.getAsBoolean())throw new IllegalArgumentException("Quests are unavailable.");rotation(clock.getAsLong());return week.id();}
    List<QuestPlan.Goal> nikoGoals()throws Exception{nikoWeek();return goals;}
    void nikoAccept(Player player,String shownWeek)throws Exception{if(!eligible(player))throw new IllegalArgumentException("Speak to Niko while playing Survival.");nikoWeek();store.acceptNiko(player.getUniqueId(),shownWeek,"niko",clock.getAsLong());}
    void nikoAccept(Player player,String shownWeek,Collection<String> choices)throws Exception{
        if(!eligible(player))throw new IllegalArgumentException("Speak to Niko while playing Survival.");
        nikoWeek();store.acceptNiko(player.getUniqueId(),shownWeek,choices,"niko",clock.getAsLong());nikoMilestone(player);
    }
    private BiConsumer<String,Player> milestones=(event,player)->{};
    void milestone(String event,Player player){if(!eligible(player))return;try{milestones.accept(event,player);}catch(RuntimeException e){cosmeticFailure.accept(e);}}
    void nikoMilestone(Player player){milestone("QUESTS_CHOSEN",player);checkWeekMilestone(player);}
    void setMilestones(BiConsumer<String,Player> callback){milestones=callback;}
    private void checkWeekMilestone(Player player){
        if(!eligible(player) || week==null)return;
        try{
            if(nikoComplete(player.getUniqueId(),week.id()))milestone("WEEK_COMPLETE",player);
        }catch(Exception e){cosmeticFailure.accept(e);}
    }
    boolean nikoComplete(UUID player,String shownWeek)throws Exception {
        if(!store.questAccepted(player,shownWeek))return false;
        var chosen=store.playerQuestGoals(player,shownWeek);if(chosen.isEmpty())return false;
        for(var goal:chosen)if(store.questProgress(player,shownWeek,goal.id())<goal.amount())return false;
        return true;
    }
    private final NookStore store;
    private final BooleanSupplier healthy;
    private final Consumer<Exception> failure;
    private final List<QuestPlan.Goal> proposed;
    private AutomaticQuests automatic=AutomaticQuests.disabled();
    private AutomaticQuests.Loader automaticLoader=()->automatic;
    void automaticLoader(AutomaticQuests.Loader loader){automaticLoader=loader;}
    private static final Set<String> MINE_TARGETS=Set.of("COAL","IRON","DIAMOND","COPPER","GOLD","REDSTONE","LAPIS","EMERALD","QUARTZ","ANCIENT_DEBRIS");
    private final boolean enabled;
    private final LongSupplier clock;
    private BiConsumer<Player,Boolean> celebration=(player,challenge)->{};
    private Consumer<Exception> cosmeticFailure=error->{};
    private final Set<UUID> capacityNotified=new HashSet<>();
    private final QuestProgressNotices notices=new QuestProgressNotices();
    private QuestPlan.Week week;
    private java.nio.file.Path backupFolder;
    private record Activation(String week,long expires) {}
    private final Map<String,Activation> activations=new HashMap<>();
    private List<QuestPlan.Goal> goals=List.of();
    private final Map<UUID,String> lastBiome=new HashMap<>();
    private static final Set<EntityType> HOSTILES=Set.of(EntityType.ZOMBIE,EntityType.SKELETON,EntityType.SPIDER,EntityType.CAVE_SPIDER,EntityType.CREEPER,EntityType.HUSK,EntityType.STRAY,EntityType.DROWNED,EntityType.WITCH,EntityType.PHANTOM,EntityType.PILLAGER,EntityType.BLAZE,EntityType.GHAST,EntityType.MAGMA_CUBE,EntityType.WITHER_SKELETON,EntityType.PIGLIN_BRUTE,EntityType.HOGLIN,EntityType.ZOGLIN,EntityType.SHULKER,EntityType.ENDERMITE,EntityType.SILVERFISH,EntityType.WITHER,EntityType.ENDER_DRAGON,EntityType.WARDEN,EntityType.BOGGED,EntityType.BREEZE,EntityType.CREAKING);
    WeeklyQuests(JavaPlugin plugin,NookStore store,BooleanSupplier healthy,Consumer<Exception> failure)throws Exception {
        this.store=store;this.healthy=healthy;this.failure=failure;this.clock=System::currentTimeMillis;backupFolder=plugin.getDataFolder().toPath().resolve("backups");
        var file=new java.io.File(plugin.getDataFolder(),"quests.yml");if(!file.exists())plugin.saveResource("quests.yml",false);
        var config=new YamlConfiguration();config.load(file);enabled=config.getBoolean("enabled",false);
        if(config.getBoolean("celebrations-enabled",true))celebration=QuestCelebrations::play;
        cosmeticFailure=e->plugin.getLogger().warning("Quest celebration failed: "+e.getClass().getSimpleName());
        milestones=new NookMilestones(plugin)::trigger;
        var section=Objects.requireNonNull(config.getConfigurationSection("goals"),"Missing quest goals");
        var parsed=new ArrayList<QuestPlan.Goal>();
        for(String id:section.getKeys(false)){
            var c=section.getConfigurationSection(id);var g=new QuestPlan.Goal(id,c.getString("title",""),c.getString("kind",""),c.getString("target",""),c.getInt("amount"),c.getLong("reward-cents"));
            validateGoal(g);
            parsed.add(g);
        }
        if(parsed.size()!=7 || parsed.stream().filter(g->g.reward()==1500).count()!=6 || parsed.stream().filter(g->g.reward()==3000).count()!=1)throw new IllegalArgumentException("Configure six quests at 1500 cents and one challenge at 3000 cents");
        validateContributions(parsed);
        proposed=List.copyOf(parsed);
        automatic=AutomaticQuests.load(plugin);automaticLoader=()->AutomaticQuests.load(plugin);
        var command=Objects.requireNonNull(plugin.getCommand("quests"));command.setExecutor(this);command.setTabCompleter(this);
        if(enabled){
            rotation(clock.getAsLong());plugin.getServer().getPluginManager().registerEvents(this,plugin);
            Bukkit.getScheduler().runTaskTimer(plugin,()->{
                if(!healthy.getAsBoolean())return;
                try{
                    rotation(clock.getAsLong());
                    for(Player p:Bukkit.getOnlinePlayers())if(eligible(p)){
                        String biome=p.getWorld().getBiome(p.getLocation()).getKey().toString();
                        sampleBiome(p,biome);
                    }
                }catch(Exception e){failure.accept(e);}
            },100,100);
        }
        plugin.getLogger().info("Weekly quests "+(enabled?"enabled":"disabled; enable in quests.yml for testing")+".");
    }
    static void validateGoal(QuestPlan.Goal g){
            if(g.kind().equals("KILL") && !g.target().equals("HOSTILE"))EntityType.valueOf(g.target());
            if(g.kind().equals("FISH") && !Set.of("COD","SALMON","TROPICAL_FISH","PUFFERFISH").contains(g.target()))throw new IllegalArgumentException("Invalid fish target");
            if(g.kind().equals("BIOME") && !g.target().equals("ANY"))throw new IllegalArgumentException("Biome quests use ANY");
            if(g.kind().equals("MINE") && !MINE_TARGETS.contains(g.target()))throw new IllegalArgumentException("Unsupported natural ore target");
            if(g.kind().equals("DEPOSIT") && (Material.matchMaterial(g.target())==null || !Material.valueOf(g.target()).isItem()))throw new IllegalArgumentException("Invalid contribution item");
            if(g.kind().equals("HARVEST") && !g.target().equals("MATURE_CROPS"))throw new IllegalArgumentException("Harvest quests use MATURE_CROPS");
            if(g.kind().equals("TRADE") && !g.target().equals("VILLAGER"))throw new IllegalArgumentException("Trade quests use VILLAGER");
    }
    WeeklyQuests(NookStore store,BooleanSupplier healthy,Consumer<Exception> failure,List<QuestPlan.Goal> proposed,LongSupplier clock){
        this(store,healthy,failure,proposed,clock,AutomaticQuests.disabled());
    }
    WeeklyQuests(NookStore store,BooleanSupplier healthy,Consumer<Exception> failure,List<QuestPlan.Goal> proposed,LongSupplier clock,AutomaticQuests automatic){
        this.store=store;this.healthy=healthy;this.failure=failure;this.proposed=List.copyOf(proposed);this.clock=clock;this.enabled=true;this.automatic=automatic;
    }
    void sampleBiome(Player player,String biome){
        if(!eligible(player))return;
        try{
            rotation(clock.getAsLong());
            if(!biome.equals(lastBiome.get(player.getUniqueId())) && record(player,"BIOME","ANY",biome))lastBiome.put(player.getUniqueId(),biome);
        }catch(Exception e){failure.accept(e);}
    }
    String status(long now){
        if(!enabled)return "Quests: disabled";
        var due=QuestPlan.week(now);
        return "Quests: active week "+(week==null?"not loaded":week.id())+"; next reset "+NookUi.date(due.nextReset())+
            (automatic.enabled()?"; "+automatic.description():"; configured goals "+(QuestPlan.sameDefinitions(goals,proposed)?"match active":"DIFFER from active"))+
            (week!=null&&!week.id().equals(due.id())?"; rollover due (not activated by status)":"");
    }
    private void rotation(long now)throws Exception {
        var current=QuestPlan.week(now);
        if(week==null || !week.id().equals(current.id())){
            goals=store.questRotation(current.id(),selectionFor(current.id()));week=current;lastBiome.clear();capacityNotified.clear();notices.clear();
        }
    }
    private List<QuestPlan.Goal> selectionFor(String targetWeek)throws Exception {
        var saved=store.savedQuestRotation(targetWeek);if(saved.isPresent())return saved.get();
        return automatic.applies(targetWeek)?automatic.bank().offers(targetWeek,store.previousQuestRotation(targetWeek)):proposed;
    }
    private boolean eligible(Player p){return enabled && healthy.getAsBoolean() && p.getGameMode()==GameMode.SURVIVAL;}
    boolean record(Player p,String kind,String target,String unique){
        if(!eligible(p))return false;
        try{
            long now=clock.getAsLong();rotation(now);
            if(!store.questAccepted(p.getUniqueId(),week.id()))return false;
            var updates=store.advanceQuests(p.getUniqueId(),week.id(),kind,target,unique,now);
            if(updates.stream().anyMatch(NookStore.QuestUpdate::paid)){capacityNotified.remove(p.getUniqueId());checkWeekMilestone(p);}
            for(var update:updates){
                if(notices.immediate(p.getUniqueId(),update,now))p.sendMessage(QuestUi.progress(update));
                if(update.paid())celebrate(p,update.goal().reward()==3000);
            }
            return true;
        }catch(NookStore.BalanceCapacityException e){
            if(capacityNotified.add(p.getUniqueId()))p.sendMessage(NookUi.prefix("NookQuests").append(NookUi.text("Your balance has no room for this reward. Spend or send some Nooks, then repeat the action. Your earlier progress is safe.")));
            return false;
        }catch(Exception e){failure.accept(e);return false;}
    }
    void setCelebration(BiConsumer<Player,Boolean> celebration,Consumer<Exception> error){this.celebration=celebration;this.cosmeticFailure=error;}
    private void celebrate(Player player,boolean challenge){try{celebration.accept(player,challenge);}catch(RuntimeException e){cosmeticFailure.accept(e);}}
    private static String position(org.bukkit.block.Block block){return block.getWorld().getUID()+":"+block.getX()+":"+block.getY()+":"+block.getZ();}
    static String ore(Material type){
        String name=type.name();if(name.equals("ANCIENT_DEBRIS"))return name;
        if(name.startsWith("DEEPSLATE_"))name=name.substring(10);
        if(name.equals("NETHER_GOLD_ORE"))return "GOLD";if(name.equals("NETHER_QUARTZ_ORE"))return "QUARTZ";
        if(!name.endsWith("_ORE"))return null;String target=name.substring(0,name.length()-4);return MINE_TARGETS.contains(target)?target:null;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void place(BlockPlaceEvent e){
        if(ore(e.getBlockPlaced().getType())!=null)try{store.markQuestBlock(position(e.getBlockPlaced()));}catch(Exception error){failure.accept(error);}
    }
    private void moved(List<org.bukkit.block.Block> blocks,org.bukkit.block.BlockFace direction){
        try{for(var block:blocks)if(ore(block.getType())!=null){store.markQuestBlock(position(block));store.markQuestBlock(position(block.getRelative(direction)));store.markQuestBlock(position(block.getRelative(direction.getOppositeFace())));}}catch(Exception e){failure.accept(e);}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void piston(BlockPistonExtendEvent e){moved(e.getBlocks(),e.getDirection());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void retract(BlockPistonRetractEvent e){moved(e.getBlocks(),e.getDirection().getOppositeFace());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void mine(BlockBreakEvent e){
        String target=ore(e.getBlock().getType());if(target==null || !eligible(e.getPlayer()))return;
        try{String pos=position(e.getBlock());if(!store.changedQuestBlock(pos))record(e.getPlayer(),"MINE",target,pos);}catch(Exception error){failure.accept(error);}
    }
    static boolean matureCrop(org.bukkit.block.BlockState state){
        if(!Set.of(Material.WHEAT,Material.CARROTS,Material.POTATOES,Material.BEETROOTS).contains(state.getType()))return false;
        return state.getBlockData() instanceof org.bukkit.block.data.Ageable age && age.getAge()==age.getMaximumAge();
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void harvest(BlockDropItemEvent event){
        // The state is from before the completed break; cancelled breaks never reach this event.
        // Count blocks, not item drops, so Fortune and extra drops do not multiply progress.
        if(!event.isCancelled() && eligible(event.getPlayer()) && matureCrop(event.getBlockState()))
            record(event.getPlayer(),"HARVEST","MATURE_CROPS",null);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void trade(PlayerStatisticIncrementEvent event){
        // Paper awards this statistic only after MerchantOffer.take has consumed payment.
        // Each completed trade emits one increment, including each trade within shift-click.
        if(event.isCancelled() || event.getStatistic()!=Statistic.TRADED_WITH_VILLAGER
            || (long)event.getNewValue()-event.getPreviousValue()!=1 || !eligible(event.getPlayer()))return;
        var top=event.getPlayer().getOpenInventory().getTopInventory();
        if(top instanceof org.bukkit.inventory.MerchantInventory merchant && merchant.getMerchant() instanceof Villager)
            record(event.getPlayer(),"TRADE","VILLAGER",null);
    }
    private void persistContributionInventory(Player player)throws Exception {
        // A void saveData return is not proof of persistence. Verify a fresh marker in the saved NBT.
        String marker="quest-save-"+UUID.randomUUID();
        player.getPersistentDataContainer().set(new NamespacedKey("nookcore","quest-save"),org.bukkit.persistence.PersistentDataType.STRING,marker);
        player.saveData();
        var file=Bukkit.getServer().getLevelDirectory().resolve("players").resolve("data").resolve(player.getUniqueId()+".dat");
        try(var channel=java.nio.channels.FileChannel.open(file,java.nio.file.StandardOpenOption.WRITE)){channel.force(true);}
        byte[] data;
        try(var stream=new java.util.zip.GZIPInputStream(java.nio.file.Files.newInputStream(file))){data=stream.readAllBytes();}
        if(!new String(data,java.nio.charset.StandardCharsets.ISO_8859_1).contains(marker))throw new IllegalStateException("Could not verify saved contribution inventory; review the pending receipt.");
    }
    private void collect(Player player,String material,int amount)throws Exception {
        Material type=Material.matchMaterial(material);if(type==null || !type.isItem() || type.isAir())throw new IllegalArgumentException("Choose a real contributed item.");
        ItemStack[] before=player.getInventory().getStorageContents();
        var example=new ItemStack(type);String key=Base64.getEncoder().encodeToString(NativeInventoryCodec.saleItem(example));
        var plan=NativeInventoryPlan.deliver(NativeInventoryCodec.capture(before),new NativeInventoryPlan.Stack(key,1,example.getMaxStackSize()),amount);
        UUID receipt=UUID.randomUUID();store.beginQuestClaim(receipt,type.name(),amount,player.getUniqueId().toString());
        try{player.getInventory().setStorageContents(NativeInventoryCodec.restore(plan.after()));persistContributionInventory(player);store.finishQuestClaim(receipt,true);}
        catch(Exception e){player.getInventory().setStorageContents(before);persistContributionInventory(player);store.finishQuestClaim(receipt,false);throw e;}
        player.sendMessage(NookUi.message("NookQuests","Collected "+amount+" "+type.name().toLowerCase(Locale.ROOT).replace('_',' ')+" for the community project. Recorded in the contribution ledger."));
    }
    static void validateContributions(List<QuestPlan.Goal> goals){
        var deposits=goals.stream().filter(g->g.kind().equals("DEPOSIT")).toList();
        if(deposits.stream().map(QuestPlan.Goal::target).distinct().count()!=deposits.size())throw new IllegalArgumentException("Each contribution goal must request a different material");
    }
    static QuestPlan.Goal contribution(List<QuestPlan.Goal> goals,String requested){
        var deposits=goals.stream().filter(g->g.kind().equals("DEPOSIT")).toList();
        if(deposits.isEmpty())throw new IllegalArgumentException("There is no material contribution this week.");
        if(requested==null && deposits.size()==1)return deposits.getFirst();
        if(requested==null)throw new IllegalArgumentException("Choose which material to contribute.");
        return deposits.stream().filter(g->g.target().equalsIgnoreCase(requested)).findFirst().orElseThrow(()->new IllegalArgumentException("That material is not requested this week. Use /quests contribute to see the choices."));
    }
    private void contribute(Player player,String requested)throws Exception {
        if(!eligible(player))throw new IllegalArgumentException("Contribute while playing Survival.");
        long now=clock.getAsLong();rotation(now);
        if(!store.questAccepted(player.getUniqueId(),week.id()))throw new IllegalArgumentException(nikoDirections.get());
        var personal=store.playerQuestGoals(player.getUniqueId(),week.id());
        var deposits=personal.stream().filter(g->g.kind().equals("DEPOSIT")).toList();
        if(requested==null && deposits.size()>1){
            player.sendMessage(NookUi.heading("NookQuests · Choose a contribution"));
            for(var g:deposits)player.sendMessage(NookUi.command("/quests contribute "+g.target().toLowerCase(Locale.ROOT)).append(NookUi.text(" · "+g.title()+" ("+store.questProgress(player.getUniqueId(),week.id(),g.id())+"/"+g.amount()+")")));
            player.sendMessage(NookUi.text("Only the material you choose will be taken. Donated items stay with the community."));return;
        }
        var goal=contribution(personal,requested);
        int remaining=goal.amount()-store.questProgress(player.getUniqueId(),week.id(),goal.id());
        if(remaining<=0)throw new IllegalArgumentException("You have completed this contribution. Other goals are listed in /quests.");
        ItemStack[] before=player.getInventory().getStorageContents();ItemStack[] after=Arrays.stream(before).map(item->item==null?null:item.clone()).toArray(ItemStack[]::new);
        int removed=0;Material material=Material.valueOf(goal.target());
        for(int i=0;i<after.length && removed<remaining;i++){
            var item=after[i];if(item==null || item.getType()!=material || item.hasItemMeta())continue;
            int take=Math.min(item.getAmount(),remaining-removed);removed+=take;item.setAmount(item.getAmount()-take);if(item.getAmount()==0)after[i]=null;
        }
        if(removed==0)throw new IllegalArgumentException("Carry plain "+goal.target().toLowerCase(Locale.ROOT).replace('_',' ')+" in your main inventory. Named or modified items are not accepted.");
        UUID receipt=UUID.randomUUID();
        String beforeHash=HexFormat.of().formatHex(NativeInventoryPlan.hash(NativeInventoryCodec.capture(before))),afterHash=HexFormat.of().formatHex(NativeInventoryPlan.hash(NativeInventoryCodec.capture(after)));
        store.beginQuestDeposit(receipt,player.getUniqueId(),week.id(),goal.target(),removed,beforeHash,afterHash,now);
        List<NookStore.QuestUpdate> updates;
        try{
            player.getInventory().setStorageContents(after);persistContributionInventory(player);
            updates=store.finishQuestDeposit(receipt,clock.getAsLong());
        }catch(Exception error){
            player.getInventory().setStorageContents(before);persistContributionInventory(player);store.cancelQuestDeposit(receipt);throw error;
        }
        milestone("COMMUNITY_CONTRIBUTION",player);checkWeekMilestone(player);
        player.sendMessage(NookUi.message("NookQuests",removed+" "+goal.target().toLowerCase(Locale.ROOT).replace('_',' ')+" contributed! Thanks for adding to the community supplies!"));
        for(var update:updates){player.sendMessage(QuestUi.progress(update));if(update.paid())celebrate(player,update.goal().reward()==3000);}
    }
    @EventHandler public void death(EntityDeathEvent event){
        Player p=event.getEntity().getKiller();if(p==null || !eligible(p))return;
        EntityType type=event.getEntityType();record(p,"KILL",type.name(),null);
        if(HOSTILES.contains(type))record(p,"KILL","HOSTILE",null);
        if(Set.of(EntityType.COD,EntityType.SALMON,EntityType.TROPICAL_FISH,EntityType.PUFFERFISH).contains(type))record(p,"FISH",type.name(),null);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void fish(PlayerFishEvent event){
        if(event.getState()==PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() instanceof Item item){
            String type=item.getItemStack().getType().name();if(Set.of("COD","SALMON","TROPICAL_FISH","PUFFERFISH").contains(type))record(event.getPlayer(),"FISH",type,null);
        }
    }
    @EventHandler public void quit(PlayerQuitEvent event){notices.forget(event.getPlayer().getUniqueId());lastBiome.remove(event.getPlayer().getUniqueId());capacityNotified.remove(event.getPlayer().getUniqueId());}
    void validateFutureBank(AutomaticQuests candidate,long now)throws Exception {
        String next=QuestPlan.week(QuestPlan.week(now).nextReset()).id();var previous=store.previousQuestRotation(next);
        for(int n=0;n<8;n++){
            var saved=store.savedQuestRotation(next);
            previous=saved.isPresent()?saved.get():candidate.applies(next)?candidate.bank().offers(next,previous):proposed;
            next=java.time.LocalDate.parse(next).plusWeeks(1).toString();
        }
    }
    private void refreshBank(CommandSender sender,boolean apply){
        try{
            var candidate=automaticLoader.load();validateFutureBank(candidate,clock.getAsLong());
            if(apply)automatic=candidate;
            sender.sendMessage(NookUi.message("NookQuests",(apply?"quest bank reloaded! ":"quest bank looks good! ")+candidate.description()+". Checked the next eight weeks; saved weeks and progress are unchanged."));
        }catch(Exception e){sender.sendMessage(NookUi.problem("NookQuests","The quest bank was not changed: "+e.getMessage()));}
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!enabled){sender.sendMessage(NookUi.message("NookQuests","Weekly quests are not open yet."));return true;}
        if(!healthy.getAsBoolean()){sender.sendMessage(NookUi.problem("NookQuests","Quests are paused; please contact staff."));return true;}
        try{
            if(args.length>=1 && Set.of("validate","reload").contains(args[0].toLowerCase(Locale.ROOT))){
                if(!sender.hasPermission("nookcore.admin"))throw new IllegalArgumentException("Only admins can change the quest bank.");
                if(args.length!=1)throw new IllegalArgumentException("/nookadmin quests "+args[0].toLowerCase(Locale.ROOT));
                refreshBank(sender,args[0].equalsIgnoreCase("reload"));return true;
            }
            if(args.length>=1 && args[0].equalsIgnoreCase("next")){
                if(!sender.hasPermission("nookcore.admin"))throw new IllegalArgumentException("Only admins can preview next week's quests.");
                if(args.length!=1)throw new IllegalArgumentException("/nookadmin quests next");
                var next=QuestPlan.week(QuestPlan.week(clock.getAsLong()).nextReset());
                sender.sendMessage(NookUi.heading("Quests · Next week"));
                sender.sendMessage(NookUi.text("Starts "+next.id()+" at 02:00 UK · "+automatic.description()));
                for(var goal:selectionFor(next.id()))sender.sendMessage(NookUi.text(goal.title()+" · "+Money.format(goal.reward())));
                sender.sendMessage(NookUi.text("Preview only. Current progress and the saved rotation are unchanged."));return true;
            }
            if(args.length>=1 && args[0].equalsIgnoreCase("activate-config")){
                if(!sender.hasPermission("nookcore.admin"))throw new IllegalArgumentException("Only admins can activate configured quests.");
                long now=clock.getAsLong();rotation(now);
                String actor=sender instanceof Player p?p.getUniqueId().toString():"console";
                if(args.length==1){
                    sender.sendMessage(NookUi.heading("NookQuests · Activate configured goals"));
                    for(var goal:proposed)sender.sendMessage(NookUi.text(goal.title()+" · "+Money.format(goal.reward())));
                    sender.sendMessage(NookUi.message("NookQuests","This replaces this week's goals for everyone with fresh progress. Old progress, rewards, balances and contributed stock are retained. New goals can pay again. Use only for the prelaunch switch; once per week."));
                    sender.sendMessage(NookUi.command("/nookadmin quests activate-config confirm"));
                    activations.put(actor,new Activation(week.id(),now+60000));return true;
                }
                if(args.length!=2 || !args[1].equalsIgnoreCase("confirm"))throw new IllegalArgumentException("Use /nookadmin quests activate-config to preview the change.");
                var pending=activations.remove(actor);
                if(pending==null || pending.expires()<now || !pending.week().equals(week.id()))throw new IllegalArgumentException("Confirmation expired. Run /nookadmin quests activate-config again.");
                java.nio.file.Files.createDirectories(backupFolder);
                var backup=backupFolder.resolve("quests-before-activation-"+UUID.randomUUID()+".db");store.backup(backup);
                goals=store.activateQuestConfig(week.id(),proposed,now);lastBiome.clear();capacityNotified.clear();notices.clear();
                sender.sendMessage(NookUi.message("NookQuests","Configured goals are now active. Backup: "+backup.getFileName()));
                Bukkit.broadcast(NookUi.message("NookQuests","The launch quests are now available. View them with ").append(NookUi.command("/quests")));return true;
            }
            if((args.length==1 || args.length==2) && args[0].equalsIgnoreCase("contribute")){
                if(!(sender instanceof Player donor))throw new IllegalArgumentException("Contribute in game.");
                contribute(donor,args.length==2?args[1]:null);return true;
            }
            if(args.length==1 && args[0].equalsIgnoreCase("contributions") && sender.hasPermission("nookcore.admin")){
                sender.sendMessage(NookUi.heading("NookQuests · Contribution ledger"));for(String line:store.questDepositReport())sender.sendMessage(NookUi.text(line));return true;
            }
            if((args.length==2 || args.length==3) && args[0].equalsIgnoreCase("collect") && sender.hasPermission("nookcore.admin")){
                if(!(sender instanceof Player collector))throw new IllegalArgumentException("Collect community materials in game.");
                collect(collector,args[1],args.length==3?Integer.parseInt(args[2]):64);return true;
            }
            UUID player;
            if(args.length==2 && args[0].equalsIgnoreCase("inspect") && sender.hasPermission("nookcore.admin"))player=store.byName(args[1]).orElseThrow(()->new IllegalArgumentException("Unknown player")).id();
            else if(args.length==0 || args.length==1 && args[0].equalsIgnoreCase("list")){
                if(!(sender instanceof Player p)){sender.sendMessage(NookUi.message("NookQuests","Use quests inspect <player> from console."));return true;}player=p.getUniqueId();
            }else{
                NookUi.help(sender,"Weekly quests","/quests — view your progress and rewards");
                if(sender.hasPermission("nookcore.admin"))NookUi.help(sender,"Staff","/nookadmin quests inspect <player> — view progress without changing it","/nookadmin quests validate — check the edited bank","/nookadmin quests reload — use a valid bank for future unsaved weeks","/nookadmin quests next — preview next week");return true;
            }
            rotation(clock.getAsLong());
            if(sender instanceof Player p && player.equals(p.getUniqueId()) && !store.questAccepted(player,week.id())){sender.sendMessage(NookUi.message("Niko",nikoDirections.get()));return true;}
            sender.sendMessage(Component.empty());sender.sendMessage(Component.text("NookQuests",NookUi.ACCENT).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD).append(Component.text(" - Refresh: "+NookUi.date(week.nextReset()),NookUi.MUTED).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD,false)));
            var personal=store.playerQuestGoals(player,week.id());
            if(sender instanceof Player p && player.equals(p.getUniqueId()))checkWeekMilestone(p);
            for(var g:personal)if(g.reward()!=3000)sender.sendMessage(QuestUi.row(g,store.questProgress(player,week.id(),g.id())));
            for(var g:personal)if(g.reward()==3000)sender.sendMessage(QuestUi.row(g,store.questProgress(player,week.id(),g.id())));
            if(personal.stream().anyMatch(g->g.kind().equals("DEPOSIT")))sender.sendMessage(NookUi.command("/quests contribute").append(NookUi.text(" · contribute materials")));
        }catch(IllegalArgumentException e){sender.sendMessage(NookUi.problem("NookQuests",e.getMessage()));}
        catch(Exception e){failure.accept(e);sender.sendMessage(NookUi.problem("NookQuests","Could not read quests; please contact staff."));}
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length==1)return NookUi.complete(args[0],sender.hasPermission("nookcore.admin")?List.of("list","help","inspect","next","validate","reload","contribute","contributions","collect","activate-config"):List.of("list","help","contribute"));
        if(args.length==2 && args[0].equalsIgnoreCase("contribute")){
            if(!enabled || !healthy.getAsBoolean())return List.of();
            try{rotation(clock.getAsLong());return NookUi.complete(args[1],(sender instanceof Player p?store.playerQuestGoals(p.getUniqueId(),week.id()):goals).stream().filter(g->g.kind().equals("DEPOSIT")).map(g->g.target().toLowerCase(Locale.ROOT)).toList());}catch(Exception e){failure.accept(e);return List.of();}
        }
        if(args.length==2 && args[0].equalsIgnoreCase("activate-config") && sender.hasPermission("nookcore.admin"))return NookUi.complete(args[1],List.of("confirm"));
        if(args.length==2 && args[0].equalsIgnoreCase("inspect") && sender.hasPermission("nookcore.admin"))try{return NookUi.complete(args[1],store.accounts().stream().map(NookStore.Account::name).toList());}catch(Exception e){failure.accept(e);}
        return List.of();
    }
}
