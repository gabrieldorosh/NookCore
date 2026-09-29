package net.nikosnook.core;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

/** Quiet local counters; collection failures do not pause the economy. */
final class EconomyMetrics implements Listener,AutoCloseable {
    private final JavaPlugin plugin;
    private final NookStore economy;
    private final ActivityStore activity;
    private final ExecutorService writer=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"NookMetrics");t.setDaemon(true);return t;});
    private final Map<ActivityStore.Key,Long> pending=new HashMap<>();
    private final Map<UUID,Long> action=new HashMap<>();
    private final Set<UUID> inFlight=new HashSet<>();
    private volatile String error;
    private volatile long lastFlush;
    private Future<?> writing;
    private int samples;
    private final boolean enabled;
    private static final Set<String> ORES=Set.of("COAL_ORE","IRON_ORE","COPPER_ORE","GOLD_ORE","REDSTONE_ORE","LAPIS_ORE","DIAMOND_ORE","EMERALD_ORE","NETHER_GOLD_ORE","NETHER_QUARTZ_ORE","ANCIENT_DEBRIS");
    EconomyMetrics(JavaPlugin plugin,NookStore economy) throws Exception {
        this.plugin=plugin;this.economy=economy;
        enabled=plugin.getConfig().getBoolean("economy-metrics.enabled",true);
        activity=enabled?new ActivityStore(plugin.getDataFolder().toPath().resolve("activity.db")):null;
        if(enabled){plugin.getServer().getPluginManager().registerEvents(this,plugin);Bukkit.getScheduler().runTaskTimer(plugin,this::sample,100,100);}
    }
    static boolean ore(Material type){String n=type.name();return ORES.contains(n.startsWith("DEEPSLATE_")?n.substring(10):n);}
    static boolean log(Material type){String n=type.name();return !n.startsWith("STRIPPED_") && (n.endsWith("_LOG") || n.equals("CRIMSON_STEM") || n.equals("WARPED_STEM"));}
    static boolean eligible(Player p){return p.getGameMode()==GameMode.SURVIVAL && !p.isDead() && p.getWorld().getEnvironment()==World.Environment.NORMAL;}
    static boolean active(long now,Long last){return last!=null && now>=last && now-last<=60_000;}
    private void touch(Player p){if(eligible(p))action.put(p.getUniqueId(),System.currentTimeMillis());}
    private void count(UUID id,String metric,long amount){if(amount>0)pending.merge(new ActivityStore.Key(LocalDate.now(ZoneOffset.UTC).toString(),id,metric),amount,Math::addExact);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void move(PlayerMoveEvent e){
        if(e instanceof PlayerTeleportEvent)return;
        if(e.getTo()!=null && e.getFrom().getWorld().equals(e.getTo().getWorld()) && e.getFrom().distanceSquared(e.getTo())>=0.0001)touch(e.getPlayer());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void interact(PlayerInteractEvent e){touch(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void place(BlockPlaceEvent e){touch(e.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent e){action.remove(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void drops(BlockDropItemEvent e){
        if(!eligible(e.getPlayer()))return;
        Material type=e.getBlockState().getType();String category=ore(type)?"ore":log(type)?"log":null;
        if(category==null)return;touch(e.getPlayer());
        // Keep the event until next tick so a later cancellation cannot count as a harvest.
        UUID id=e.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(plugin,()->{
            if(e.isCancelled())return;
            count(id,category+"_blocks:"+type.name(),1);
            for(var item:e.getItems())count(id,"drops:"+item.getItemStack().getType().name(),item.getItemStack().getAmount());
        });
    }
    private void sample(){
        long now=System.currentTimeMillis();
        for(var p:Bukkit.getOnlinePlayers())if(eligible(p)){
            count(p.getUniqueId(),"survival_seconds",5);
            if(active(now,action.get(p.getUniqueId())))count(p.getUniqueId(),"active_seconds",5);
        }
        if(++samples%12==0)flush();
    }
    private void flush(){
        if(activity==null || writing!=null && !writing.isDone() || pending.isEmpty())return;
        var batch=Map.copyOf(pending);pending.clear();
        writing=writer.submit(()->{
            try{activity.add(batch,LocalDate.now(ZoneOffset.UTC));lastFlush=System.currentTimeMillis();error=null;}
            catch(Exception ex){error="A measurement batch was lost; check the server log.";plugin.getLogger().log(Level.WARNING,"Economy metrics batch failed; gameplay is unaffected",ex);}
        });
    }
    static int days(String[] args){
        if(args.length<1 || args.length>3 || args.length==3 && !args[1].equalsIgnoreCase("export"))throw new IllegalArgumentException("Use /nooks metrics [days] or /nooks metrics export [days].");
        String value=args.length==1 || args.length==2 && args[1].equalsIgnoreCase("export")?"7":args[args.length-1];
        try{int n=Integer.parseInt(value);if(n<1 || n>90)throw new NumberFormatException();return n;}catch(NumberFormatException ex){throw new IllegalArgumentException("Choose 1 to 90 calendar days.");}
    }
    boolean command(CommandSender sender,String[] args){
        if(!sender.hasPermission("nookcore.admin")){sender.sendMessage(NookUi.problem("Nooks","These metrics are for admins."));return true;}
        try {
            int days=days(args);boolean export=args.length>=2 && args[1].equalsIgnoreCase("export");
            if(!enabled){sender.sendMessage(NookUi.text("Economy activity collection is disabled."));return true;}
            UUID actor=sender instanceof Player p?p.getUniqueId():new UUID(0,0);
            if(!inFlight.add(actor))throw new IllegalArgumentException("Your metrics report is still being prepared.");
            flush();LocalDate today=LocalDate.now(ZoneOffset.UTC),first=today.minusDays(days-1);
            sender.sendMessage(NookUi.text("Preparing private metrics; daily buckets use UTC and include today's partial day."));
            writer.submit(()->{
                try {
                    var rows=activity.read(first,today);long since=first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),until=today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                    var earnings=economy.metricEarnings(since,until);
                    List<String> lines=summarise(rows,earnings,days);
                    if(export){Path folder=plugin.getDataFolder().toPath().resolve("reports");Files.createDirectories(folder);Path file=folder.resolve("activity-"+first+"-"+today+"-"+UUID.randomUUID()+".csv");Files.writeString(file,csv(rows,earnings),StandardOpenOption.CREATE_NEW);lines.add("Saved private export: plugins/NookCore/reports/"+file.getFileName());}
                    if(error!=null)lines.add(error);
                    Bukkit.getScheduler().runTask(plugin,()->{inFlight.remove(actor);if(sender instanceof Player p && !p.isOnline())return;if(!sender.hasPermission("nookcore.admin"))return;for(String line:lines)sender.sendMessage(NookUi.text(line));});
                }catch(Exception ex){plugin.getLogger().log(Level.WARNING,"Metrics report failed",ex);Bukkit.getScheduler().runTask(plugin,()->{inFlight.remove(actor);sender.sendMessage(NookUi.problem("Nooks","Metrics unavailable; check the server log."));});}
            });
        }catch(IllegalArgumentException ex){sender.sendMessage(NookUi.problem("Nooks",ex.getMessage()));}
        return true;
    }
    static List<String> summarise(List<ActivityStore.Row> rows,Map<UUID,Long> earnings,int days){
        long active=0,online=0,ores=0,logs=0;Set<UUID> players=new HashSet<>();
        for(var r:rows){players.add(r.player());if(r.metric().equals("active_seconds"))active+=r.amount();if(r.metric().equals("survival_seconds"))online+=r.amount();if(r.metric().startsWith("ore_blocks:"))ores+=r.amount();if(r.metric().startsWith("log_blocks:"))logs+=r.amount();}
        long earned=players.stream().mapToLong(id->earnings.getOrDefault(id,0L)).sum();
        var lines=new ArrayList<String>();lines.add("Nooks - Private activity metrics ("+days+" UTC calendar days)");
        lines.add(players.size()+" measured players; "+String.format(Locale.ROOT,"%.2f",online/3600.0)+" sampled Survival hours; "+String.format(Locale.ROOT,"%.2f",active/3600.0)+" active-proxy hours.");
        lines.add("Harvested ore blocks: "+ores+"; logs/stems: "+logs+". Overworld Survival only; placed blocks can count.");
        lines.add("Measured players' quest/advancement rewards: "+Money.format(earned)+". Joining bonuses, transfers, shop proceeds and staff grants excluded.");
        lines.add("Activity begins when installed; rewards can predate collection. Do not divide historical rewards by partial observation time. Use /nooks report for money flows and market prices.");
        lines.add("Active means movement/interaction within 60 seconds; AFK machines and lag can bias it. Flushes about once per minute; use /nooks metrics export [days] for daily detail.");return lines;
    }
    static String csv(List<ActivityStore.Row> rows,Map<UUID,Long> earnings){
        StringBuilder out=new StringBuilder("day_utc,player_uuid,metric,amount\n");
        for(var r:rows)out.append(r.day()).append(',').append(r.player()).append(',').append(r.metric()).append(',').append(r.amount()).append('\n');
        // A distinct interval row avoids duplicating whole-period earnings across daily rows.
        for(var e:new TreeMap<>(earnings).entrySet())out.append("interval,").append(e.getKey()).append(",quest_advancement_cents,").append(e.getValue()).append('\n');
        return out.toString();
    }
    @Override public void close()throws Exception {
        if(activity==null){writer.shutdown();return;}
        var batch=Map.copyOf(pending);pending.clear();writer.submit(()->{try{activity.add(batch,LocalDate.now(ZoneOffset.UTC));}catch(Exception ex){plugin.getLogger().log(Level.WARNING,"Final activity flush failed",ex);}});
        writer.shutdown();if(!writer.awaitTermination(10,TimeUnit.SECONDS)){plugin.getLogger().warning("Metrics writer is still closing; recent counters may be missing.");return;}activity.close();
    }
}
