package net.nikosnook.core;

import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Default-off cosmetic lifetime tracking. Bukkit stays on the main thread; only immutable totals reach SQLite. */
final class TravelMilestones implements Listener,AutoCloseable {
    private final JavaPlugin plugin;
    private final NookStore store;
    private final GroundedTravel grounded;
    private final BooleanSupplier healthy;
    private final Map<UUID,Rider> riders=new HashMap<>();
    private final List<Flush> writes=new ArrayList<>();
    private ExecutorService writer;
    private org.bukkit.scheduler.BukkitTask task;
    private NookMilestones milestones;
    private long roadGoal,railGoal;
    private int samples;
    private boolean disabled,closed;
    private static final class Rider {
        final UUID id;final TravelProgress progress=new TravelProgress();
        final Future<NookStore.TravelTotals> loading;
        NookStore.TravelTotals saved;boolean writing,roadSent,railSent;
        Rider(UUID id,Future<NookStore.TravelTotals> loading){this.id=id;this.loading=loading;}
    }
    private record Pending(Rider rider,TravelProgress.Batch batch,NookStore.TravelTotals target){}
    private record Flush(List<Pending> entries,Future<Map<UUID,NookStore.TravelTotals>> future){}
    TravelMilestones(JavaPlugin plugin,NookStore store,GroundedTravel grounded,BooleanSupplier healthy){
        this.plugin=plugin;this.store=store;this.grounded=grounded;this.healthy=healthy;
        if(!Files.exists(plugin.getDataFolder().toPath().resolve("travel-advancements.yml")))plugin.saveResource("travel-advancements.yml",false);
        if(!plugin.getConfig().getBoolean("travel.lifetime-milestones.enabled",false)){disabled=true;return;}
        roadGoal=goal(plugin.getConfig().getDouble("travel.lifetime-milestones.road-mount-blocks",1000));
        railGoal=goal(plugin.getConfig().getDouble("travel.lifetime-milestones.rail-blocks",1000));
        milestones=new NookMilestones(plugin,"travel-advancements.yml");
        writer=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"NookTravelMilestones");t.setDaemon(true);return t;});
        Bukkit.getPluginManager().registerEvents(this,plugin);
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::sample,5,5);
    }
    static long goal(double blocks){
        if(!Double.isFinite(blocks) || blocks<1 || blocks>1_000_000_000)throw new IllegalArgumentException("Travel milestone goals must be 1–1,000,000,000 blocks");
        return Math.round(blocks*1000);
    }
    static boolean eligible(Player player){
        return player.isOnline() && !player.isDead() && player.getGameMode()==GameMode.SURVIVAL && player.getWorld().getEnvironment()==World.Environment.NORMAL
            && !StaffMode.active(player.getUniqueId()) && player.isVisibleByDefault()
            && (!player.hasMetadata("vanished") || player.getMetadata("vanished").stream().noneMatch(v->v.asBoolean()));
    }
    static boolean firstSeat(Player player,Entity vehicle){
        return vehicle!=null && vehicle.isValid() && !vehicle.isDead() && !vehicle.getPassengers().isEmpty()
            && vehicle.getPassengers().getFirst().getUniqueId().equals(player.getUniqueId());
    }
    private Rider rider(Player player){return riders.computeIfAbsent(player.getUniqueId(),id->new Rider(id,writer.submit(()->store.travelTotals(id))));}
    private void sample(){
        if(closed)return;
        try{
            poll();
            if(disabled || !healthy.getAsBoolean()){riders.values().forEach(r->r.progress.reset());return;}
            long tick=Bukkit.getCurrentTick(),nanos=System.nanoTime();
            for(Player player:Bukkit.getOnlinePlayers())samplePlayer(player,tick,nanos);
            if(++samples%240==0)flush(new ArrayList<>(riders.values()));
            var offline=new ArrayList<Rider>();
            for(var state:riders.values())if(Bukkit.getPlayer(state.id)==null){state.progress.reset();offline.add(state);}
            flush(offline);
            riders.values().removeIf(r->Bukkit.getPlayer(r.id)==null && !r.writing && r.progress.pending().roadMillimetres()==0 && r.progress.pending().railMillimetres()==0);
        }catch(Exception|LinkageError error){disable(error);}
    }
    private void samplePlayer(Player player,long tick,long nanos)throws Exception{
        if(!eligible(player)){reset(player.getUniqueId());return;}
        var state=rider(player);if(state.saved==null){if(!state.loading.isDone())return;state.saved=state.loading.get();}
        grantSaved(state,player);measure(state,player,tick,nanos);
        if(state.progress.thresholdSave(state.saved.roadMillimetres(),state.saved.railMillimetres(),roadGoal,railGoal,state.writing))flush(List.of(state));
    }
    private void measure(Rider state,Player player,long tick,long nanos){
        Entity vehicle=player.getVehicle();TravelProgress.Kind kind;double halfWidth;
        if(!firstSeat(player,vehicle)){state.progress.reset();return;}
        if(vehicle instanceof AbstractHorse horse){
            var saddle=horse.getInventory().getSaddle();
            if(!grounded.allowedRoadMount(horse) || !horse.isTamed() || saddle==null || saddle.getType()!=Material.SADDLE || !horse.isOnGround() || horse.isInWater() || horse.isJumping()){
                state.progress.reset();return;
            }
            kind=TravelProgress.Kind.ROAD_MOUNT;var box=horse.getBoundingBox();halfWidth=Math.max(box.getWidthX(),box.getWidthZ())/2;
        }else if(vehicle instanceof RideableMinecart){kind=TravelProgress.Kind.RAIL;halfWidth=.5;}
        else{state.progress.reset();return;}
        var at=vehicle.getLocation();if(!TravelSurfaces.bounded(at)){state.progress.reset();return;}
        var current=new TravelProgress.Sample(at.getWorld().getUID(),vehicle.getUniqueId(),kind,at.getX(),at.getY(),at.getZ(),tick,nanos,true);
        var previous=state.progress.anchor();var from=at;
        if(previous!=null && previous.world().equals(current.world()) && previous.vehicle().equals(current.vehicle()) && previous.kind()==kind){
            long gap=tick-previous.tick(),elapsed=nanos-previous.nanos();
            if(gap<1 || gap>10 || elapsed<1 || elapsed>1_000_000_000L){state.progress.reset();state.progress.observe(current);return;}
            from=new Location(at.getWorld(),previous.x(),previous.y(),previous.z());
        }
        boolean supported=TravelSurfaces.segment(from,at,halfWidth,kind==TravelProgress.Kind.RAIL,grounded::roadMaterial);
        if(!supported){state.progress.reset();return;}
        state.progress.observe(current);
    }
    private void grantSaved(Rider state,Player player){
        if(!state.roadSent && state.saved.roadMillimetres()>=roadGoal){milestones.trigger("ROAD_MOUNT",player);state.roadSent=true;}
        if(!state.railSent && state.saved.railMillimetres()>=railGoal){milestones.trigger("RAIL",player);state.railSent=true;}
    }
    private void poll()throws Exception {
        for(var iterator=writes.iterator();iterator.hasNext();){var flush=iterator.next();if(!flush.future().isDone())continue;
            try{var saved=flush.future().get();for(var entry:flush.entries()){entry.rider().saved=saved.get(entry.rider().id);entry.rider().progress.acknowledge(entry.batch());entry.rider().writing=false;}}
            finally{flush.entries().forEach(p->p.rider().writing=false);iterator.remove();}
        }
    }
    private void flush(List<Rider> states){
        if(writer==null || writer.isShutdown())return;
        var batch=new ArrayList<Pending>();
        for(var state:states){
            if(state.saved==null || state.writing)continue;var pending=state.progress.snapshot();if(pending==null)continue;
            var target=new NookStore.TravelTotals(Math.addExact(state.saved.roadMillimetres(),pending.roadMillimetres()),Math.addExact(state.saved.railMillimetres(),pending.railMillimetres()));
            state.writing=true;batch.add(new Pending(state,pending,target));
            if(batch.size()==64){submit(batch);batch=new ArrayList<>();}
        }
        if(!batch.isEmpty())submit(batch);
    }
    private void submit(List<Pending> pending){
        var entries=List.copyOf(pending);var targets=new HashMap<UUID,NookStore.TravelTotals>();entries.forEach(p->targets.put(p.rider().id,p.target()));
        writes.add(new Flush(entries,writer.submit(()->store.saveTravelTotals(Map.copyOf(targets)))));
    }
    private void reset(UUID player){var state=riders.get(player);if(state!=null)state.progress.reset();}
    private void disable(Throwable error){if(disabled)return;disabled=true;plugin.getLogger().warning("Optional travel milestones paused: "+error.getMessage()+". Other gameplay is unchanged; check the log and restart to retry.");}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){reset(e.getPlayer().getUniqueId());}
    @EventHandler public void world(PlayerChangedWorldEvent e){reset(e.getPlayer().getUniqueId());}
    @EventHandler public void respawn(PlayerRespawnEvent e){reset(e.getPlayer().getUniqueId());}
    @EventHandler public void death(PlayerDeathEvent e){reset(e.getEntity().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void mode(PlayerGameModeChangeEvent e){reset(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void mount(EntityMountEvent e){reset(e.getEntity().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void dismount(EntityDismountEvent e){reset(e.getEntity().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void vehicleTeleport(EntityTeleportEvent e){for(var state:riders.values()){var anchor=state.progress.anchor();if(anchor!=null && anchor.vehicle().equals(e.getEntity().getUniqueId()))state.progress.reset();}}
    @EventHandler public void quit(PlayerQuitEvent e){var state=riders.get(e.getPlayer().getUniqueId());if(state!=null){state.progress.reset();flush(List.of(state));}}
    @Override public void close(){
        if(closed)return;closed=true;if(task!=null)task.cancel();HandlerList.unregisterAll(this);if(writer==null)return;
        // Absolute monotonic snapshots make this safe even when an older minute/quit batch is still queued.
        var targets=new HashMap<UUID,NookStore.TravelTotals>();
        try{
            for(var state:riders.values())if(state.saved!=null){var pending=state.progress.pending();if(pending.roadMillimetres()==0 && pending.railMillimetres()==0)continue;
                targets.put(state.id,new NookStore.TravelTotals(Math.addExact(state.saved.roadMillimetres(),pending.roadMillimetres()),Math.addExact(state.saved.railMillimetres(),pending.railMillimetres())));
                if(targets.size()==64){var copy=Map.copyOf(targets);writer.submit(()->{try{store.saveTravelTotals(copy);}catch(Exception error){plugin.getLogger().warning("Final travel milestone save failed: "+error.getMessage());}});targets.clear();}
            }
            if(!targets.isEmpty()){var copy=Map.copyOf(targets);writer.submit(()->{try{store.saveTravelTotals(copy);}catch(Exception error){plugin.getLogger().warning("Final travel milestone save failed: "+error.getMessage());}});}
            writer.shutdown();if(!writer.awaitTermination(10,TimeUnit.SECONDS)){writer.shutdownNow();plugin.getLogger().warning("Travel milestone save did not finish; unflushed distance may be lost.");}
        }catch(Exception error){writer.shutdownNow();if(error instanceof InterruptedException)Thread.currentThread().interrupt();plugin.getLogger().warning("Final travel milestone save failed: "+error.getMessage());}
    }
}
