package net.nikosnook.core;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.*;
import net.kyori.adventure.text.Component;

final class WeeklyQuests implements Listener,CommandExecutor,TabCompleter {
    private final NookStore store;
    private final BooleanSupplier healthy;
    private final Consumer<Exception> failure;
    private final List<QuestPlan.Goal> proposed;
    private final boolean enabled;
    private QuestPlan.Week week;
    private List<QuestPlan.Goal> goals=List.of();
    private final Map<UUID,String> lastBiome=new HashMap<>();
    private static final Set<EntityType> HOSTILES=Set.of(EntityType.ZOMBIE,EntityType.SKELETON,EntityType.SPIDER,EntityType.CAVE_SPIDER,EntityType.CREEPER,EntityType.HUSK,EntityType.STRAY,EntityType.DROWNED,EntityType.WITCH,EntityType.PHANTOM,EntityType.PILLAGER);
    WeeklyQuests(JavaPlugin plugin,NookStore store,BooleanSupplier healthy,Consumer<Exception> failure)throws Exception {
        this.store=store;this.healthy=healthy;this.failure=failure;
        var file=new java.io.File(plugin.getDataFolder(),"quests.yml");if(!file.exists())plugin.saveResource("quests.yml",false);
        var config=new YamlConfiguration();config.load(file);enabled=config.getBoolean("enabled",false);
        var section=Objects.requireNonNull(config.getConfigurationSection("goals"),"Missing quest goals");
        var parsed=new ArrayList<QuestPlan.Goal>();
        for(String id:section.getKeys(false)){
            var c=section.getConfigurationSection(id);var g=new QuestPlan.Goal(id,c.getString("title",""),c.getString("kind",""),c.getString("target",""),c.getInt("amount"),c.getLong("reward-cents"));
            if(g.kind().equals("KILL") && !g.target().equals("HOSTILE"))EntityType.valueOf(g.target());
            if(g.kind().equals("FISH") && !Set.of("COD","SALMON","TROPICAL_FISH","PUFFERFISH").contains(g.target()))throw new IllegalArgumentException("Invalid fish target");
            if(g.kind().equals("BIOME") && !g.target().equals("ANY"))throw new IllegalArgumentException("Biome quests use ANY");
            parsed.add(g);
        }
        if(parsed.size()!=7 || parsed.stream().filter(g->g.reward()==1500).count()!=6 || parsed.stream().filter(g->g.reward()==3000).count()!=1)throw new IllegalArgumentException("Configure six quests at 1500 cents and one challenge at 3000 cents");
        proposed=List.copyOf(parsed);
        var command=Objects.requireNonNull(plugin.getCommand("nookquests"));command.setExecutor(this);command.setTabCompleter(this);
        if(enabled){
            rotation(System.currentTimeMillis());plugin.getServer().getPluginManager().registerEvents(this,plugin);
            Bukkit.getScheduler().runTaskTimer(plugin,()->{
                if(!healthy.getAsBoolean())return;
                try{
                    rotation(System.currentTimeMillis());
                    for(Player p:Bukkit.getOnlinePlayers())if(eligible(p)){
                        String biome=p.getWorld().getBiome(p.getLocation()).getKey().toString();
                        if(!biome.equals(lastBiome.get(p.getUniqueId()))){record(p,"BIOME","ANY",biome);lastBiome.put(p.getUniqueId(),biome);}
                    }
                }catch(Exception e){failure.accept(e);}
            },100,100);
        }
        plugin.getLogger().info("Weekly quests "+(enabled?"enabled":"disabled; enable in quests.yml for testing")+".");
    }
    private void rotation(long now)throws Exception {
        var current=QuestPlan.week(now);
        if(week==null || !week.id().equals(current.id())){
            goals=store.questRotation(current.id(),proposed);week=current;lastBiome.clear();
        }
    }
    private boolean eligible(Player p){return enabled && healthy.getAsBoolean() && p.getGameMode()==GameMode.SURVIVAL && p.getWorld().getEnvironment()==World.Environment.NORMAL;}
    private void record(Player p,String kind,String target,String unique){
        if(!eligible(p))return;
        try{
            long now=System.currentTimeMillis();rotation(now);
            for(var goal:store.progressQuests(p.getUniqueId(),week.id(),kind,target,unique,now))
                p.sendMessage(Component.text("NookQuests » ",NookUi.ACCENT).append(NookUi.text(goal.title()+" complete · +"+Money.format(goal.reward()))));
        }catch(Exception e){failure.accept(e);}
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
    @EventHandler public void quit(PlayerQuitEvent event){lastBiome.remove(event.getPlayer().getUniqueId());}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!enabled){sender.sendMessage(NookUi.text("Weekly quests are not open yet."));return true;}
        if(!healthy.getAsBoolean()){sender.sendMessage(NookUi.error("Quests are paused; please contact staff."));return true;}
        try{
            UUID player;
            if(args.length==2 && args[0].equalsIgnoreCase("inspect") && sender.hasPermission("nookcore.admin"))player=store.byName(args[1]).orElseThrow(()->new IllegalArgumentException("Unknown player")).id();
            else if(args.length==0 || args.length==1 && args[0].equalsIgnoreCase("list")){
                if(!(sender instanceof Player p)){sender.sendMessage(NookUi.text("Use nookquests inspect <player> from console."));return true;}player=p.getUniqueId();
            }else{
                NookUi.help(sender,"Weekly quests","/nookquests — view your progress and rewards");
                if(sender.hasPermission("nookcore.admin"))NookUi.help(sender,"Staff","/nookquests inspect <player> — view progress without changing it");return true;
            }
            rotation(System.currentTimeMillis());
            sender.sendMessage(Component.empty());sender.sendMessage(Component.text("NookQuests · "+week.id(),NookUi.ACCENT));
            for(var g:goals){int n=store.questProgress(player,week.id(),g.id());sender.sendMessage(NookUi.text((n==g.amount()?"✓ ":"• ")+(g.reward()==3000?"Challenge · ":"")+g.title()+" · "+n+"/"+g.amount()+" · "+Money.format(g.reward())+(n==g.amount()?" paid":"")));}
            sender.sendMessage(Component.text("Resets "+NookUi.date(week.nextReset())+" · Overworld survival only",NookUi.MUTED));
            sender.sendMessage(Component.text("Fish count when caught or killed. Biomes count once each. Rewards are automatic.",NookUi.MUTED));
        }catch(IllegalArgumentException e){sender.sendMessage(NookUi.error(e.getMessage()));}
        catch(Exception e){failure.accept(e);sender.sendMessage(NookUi.error("Could not read quests; please contact staff."));}
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length==1)return NookUi.complete(args[0],sender.hasPermission("nookcore.admin")?List.of("list","help","inspect"):List.of("list","help"));
        if(args.length==2 && args[0].equalsIgnoreCase("inspect") && sender.hasPermission("nookcore.admin"))try{return NookUi.complete(args[1],store.accounts().stream().map(NookStore.Account::name).toList());}catch(Exception e){failure.accept(e);}
        return List.of();
    }
}
