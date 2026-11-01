package net.nikosnook.core;

import java.util.*;
import java.util.function.LongSupplier;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;

/** Local surface checks only; no chunk loading or persistent mount attribute changes. */
final class GroundedTravel implements Listener {
    private final NamespacedKey speedKey,cartOriginalKey,rocketSpentKey,rocketReadyKey;
    private final boolean enabled,roadsEnabled,cartsEnabled,elytraEnabled;
    private final double multiplier,cartSpeed,cartAcceleration;
    private final int grace,ramp;
    private final long rocketCooldownMillis;
    private final LongSupplier clock;
    private boolean closed;
    private org.bukkit.scheduler.BukkitTask task;
    private final Set<Material> roads=new HashSet<>();
    private final Set<String> mounts;
    private final Map<UUID,MountState> activeMounts=new HashMap<>();
    private final Map<UUID,RideableMinecart> activeCarts=new HashMap<>();
    private final Map<UUID,TravelRules.RocketFlight> flights=new HashMap<>();
    private final Map<UUID,Long> lastNotice=new HashMap<>();
    private long tick;
    private static final class MountState {
        final AbstractHorse horse; long lastRoad=-1; double boost;
        MountState(AbstractHorse horse){this.horse=horse;}
    }
    GroundedTravel(JavaPlugin plugin){this(plugin,System::currentTimeMillis);}
    GroundedTravel(JavaPlugin plugin,LongSupplier clock){
        this.clock=clock;
        var c=plugin.getConfig();String root="travel.grounded.";
        enabled=c.getBoolean(root+"enabled",false);
        roadsEnabled=c.getBoolean(root+"roads.enabled",true);
        cartsEnabled=c.getBoolean(root+"minecarts.enabled",true);
        elytraEnabled=c.getBoolean(root+"elytra.enabled",true);
        multiplier=TravelRules.bounded(c.getDouble(root+"roads.multiplier",3),1,5,"Road multiplier");
        cartSpeed=TravelRules.bounded(c.getDouble(root+"minecarts.max-blocks-per-second",32),8,40,"Cart speed")/20;
        cartAcceleration=TravelRules.bounded(c.getDouble(root+"minecarts.acceleration-per-tick",0.04),0.005,0.1,"Cart acceleration");
        grace=(int)TravelRules.bounded(c.getInt(root+"roads.grace-ticks",30),0,60,"Road grace");
        ramp=(int)TravelRules.bounded(c.getInt(root+"roads.ramp-ticks",20),1,100,"Road ramp");
        double seconds=c.isSet(root+"elytra.landing-cooldown-seconds")?c.getDouble(root+"elytra.landing-cooldown-seconds"):c.getInt(root+"elytra.grounded-rearm-ticks",60)/20.0;
        rocketCooldownMillis=Math.round(1000*TravelRules.bounded(seconds,.05,10,"Rocket landing cooldown"));
        mounts=new HashSet<>(c.getStringList(root+"roads.mounts"));
        if(mounts.isEmpty())mounts.addAll(List.of("HORSE","DONKEY","MULE","CAMEL","SKELETON_HORSE","ZOMBIE_HORSE"));
        var materials=c.getStringList(root+"roads.materials");
        if(materials.isEmpty())materials=List.of("DIRT_PATH","COARSE_DIRT","PODZOL","MUD","GRAVEL","BRICKS","BRICK_SLAB","BRICK_STAIRS","COBBLESTONE","COBBLESTONE_SLAB","COBBLESTONE_STAIRS","MOSSY_COBBLESTONE","MOSSY_COBBLESTONE_SLAB","MOSSY_COBBLESTONE_STAIRS","STONE_BRICKS","STONE_BRICK_SLAB","STONE_BRICK_STAIRS","CRACKED_STONE_BRICKS");
        for(String name:materials){Material material=Material.matchMaterial(name);if(material==null || !material.isBlock())throw new IllegalArgumentException("Unknown road block: "+name);roads.add(material);}
        speedKey=new NamespacedKey(plugin,"road-speed");cartOriginalKey=new NamespacedKey(plugin,"travel-original-cart-speed");
        rocketSpentKey=new NamespacedKey(plugin,"rocket-flight-spent");rocketReadyKey=new NamespacedKey(plugin,"rocket-ready-at");
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        // Recover saved cart settings after an interrupted run, even when this feature is disabled.
        for(World world:Bukkit.getWorlds())for(Entity entity:world.getEntities())restore(entity);
        if(enabled){task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);plugin.getLogger().info("Grounded travel enabled: road mounts, occupied carts and landing-cooldown rockets.");}
    }
    String status(){return !enabled?"Travel: disabled":"Travel: roads="+roadsEnabled+", minecarts="+cartsEnabled+", rocket restriction="+elytraEnabled;}
    static boolean rider(Player player){return !player.isDead() && (player.getGameMode()==GameMode.SURVIVAL || player.getGameMode()==GameMode.ADVENTURE);}
    private void tick(){
        if(closed)return;
        tick++;Set<UUID> seenMounts=new HashSet<>(),seenCarts=new HashSet<>();
        for(Player player:Bukkit.getOnlinePlayers()){
            if(!rider(player))continue;
            // Landings in another dimension still end an outstanding Overworld rocket-assisted flight.
            if(elytraEnabled)observeRocket(player,player.getLocation());
            if(player.getWorld().getEnvironment()!=World.Environment.NORMAL)continue;
            Entity vehicle=player.getVehicle();
            if(roadsEnabled && vehicle instanceof AbstractHorse horse && mounts.contains(horse.getType().name()) && seenMounts.add(horse.getUniqueId()))updateMount(horse);
            if(cartsEnabled && vehicle instanceof RideableMinecart cart && seenCarts.add(cart.getUniqueId()))updateCart(cart);
        }
        for(UUID id:new HashSet<>(activeMounts.keySet()))if(!seenMounts.contains(id))restore(activeMounts.remove(id).horse);
        for(UUID id:new HashSet<>(activeCarts.keySet()))if(!seenCarts.contains(id))restore(activeCarts.remove(id));
    }
    private TravelRules.RocketFlight flight(Player player){
        return flights.computeIfAbsent(player.getUniqueId(),id->{
            var data=player.getPersistentDataContainer();
            boolean spent=Byte.valueOf((byte)1).equals(data.get(rocketSpentKey,PersistentDataType.BYTE));
            return new TravelRules.RocketFlight(spent,data.getOrDefault(rocketReadyKey,PersistentDataType.LONG,-1L));
        });
    }
    private void persistFlight(Player player,TravelRules.RocketFlight flight){
        var data=player.getPersistentDataContainer();
        if(!flight.spent()){data.remove(rocketSpentKey);data.remove(rocketReadyKey);return;}
        data.set(rocketSpentKey,PersistentDataType.BYTE,(byte)1);
        if(flight.readyAt()<0)data.remove(rocketReadyKey);else data.set(rocketReadyKey,PersistentDataType.LONG,flight.readyAt());
    }
    private boolean landed(Player player,Location location){
        Entity vehicle=player.getVehicle();
        return player.isInWater() || player.isSwimming() || vehicle instanceof Boat
                || vehicle!=null && (vehicle.isOnGround() || vehicle.isInWater()) || groundContact(location);
    }
    private TravelRules.RocketFlight observeRocket(Player player,Location location){
        var state=flight(player);boolean before=state.spent();long deadline=state.readyAt();
        state.observe(clock.getAsLong(),landed(player,location),rocketCooldownMillis);
        if(before!=state.spent() || deadline!=state.readyAt())persistFlight(player,state);
        return state;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void move(PlayerMoveEvent event){
        if(closed || !enabled || !elytraEnabled || !rider(event.getPlayer()) || event.getTo()==null)return;
        // Catch brief landings between scheduled samples, including fractional-height chest/slab tops.
        var state=flight(event.getPlayer());if(state.spent() && state.readyAt()<0)observeRocket(event.getPlayer(),event.getTo());
    }
    /** Measure the feet against block collisions; client ground/gliding flags can lag a moving landing. */
    static boolean groundContact(Location feet){
        World world=feet.getWorld();if(world==null || !Double.isFinite(feet.getX()) || !Double.isFinite(feet.getY()) || !Double.isFinite(feet.getZ()))return false;
        double x=feet.getX(),y=feet.getY(),z=feet.getZ();
        for(int bx=(int)Math.floor(x-.299);bx<=(int)Math.floor(x+.299);bx++)for(int bz=(int)Math.floor(z-.299);bz<=(int)Math.floor(z+.299);bz++){
            if(!world.isChunkLoaded(bx>>4,bz>>4))continue;
            for(int by=Math.max(world.getMinHeight(),(int)Math.floor(y-.06));by<=Math.min(world.getMaxHeight()-1,(int)Math.floor(y));by++){
                var block=world.getBlockAt(bx,by,bz);
                for(var local:block.getCollisionShape().getBoundingBoxes()){
                    var box=local.clone().shift(bx,by,bz);
                    if(Math.abs(box.getMaxY()-y)<=.06 && box.getMaxX()>x-.299 && box.getMinX()<x+.299 && box.getMaxZ()>z-.299 && box.getMinZ()<z+.299)return true;
                }
            }
        }
        return false;
    }
    private void updateMount(AbstractHorse horse){
        var state=activeMounts.computeIfAbsent(horse.getUniqueId(),id->new MountState(horse));
        boolean road=horse.isOnGround() && !horse.isInWater() && roadUnder(horse.getLocation());
        if(road)state.lastRoad=tick;
        state.boost=TravelRules.roadBoost(state.boost,tick,state.lastRoad,grace,ramp,road);
        if(horse.isInWater())state.boost=0;
        var attribute=horse.getAttribute(Attribute.MOVEMENT_SPEED);if(attribute==null)return;
        var existing=attribute.getModifier(speedKey);
        double amount=(multiplier-1)*state.boost;
        if(existing!=null && Math.abs(existing.getAmount()-amount)<0.000001)return;
        if(existing!=null)attribute.removeModifier(existing);
        if(amount>0)attribute.addTransientModifier(new AttributeModifier(speedKey,amount,AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }
    boolean roadUnder(Location feet){
        World world=feet.getWorld();
        for(double dx:new double[]{0,-0.35,0.35})for(double dz:new double[]{0,-0.35,0.35}){
            int x=(int)Math.floor(feet.getX()+dx),z=(int)Math.floor(feet.getZ()+dz),y=(int)Math.floor(feet.getY()-0.05);
            if(!world.isChunkLoaded(x>>4,z>>4))continue;
            Block block=world.getBlockAt(x,y,z);
            if(roads.contains(block.getType())){
                var hit=block.rayTrace(new Location(world,feet.getX()+dx,feet.getY()+0.1,feet.getZ()+dz),new org.bukkit.util.Vector(0,-1,0),0.3,FluidCollisionMode.NEVER);
                if(hit!=null)return true;
            }
        }
        return false;
    }
    private void updateCart(RideableMinecart cart){
        activeCarts.put(cart.getUniqueId(),cart);
        var data=cart.getPersistentDataContainer();
        if(!data.has(cartOriginalKey,PersistentDataType.DOUBLE))data.set(cartOriginalKey,PersistentDataType.DOUBLE,cart.getMaxSpeed());
        double original=data.get(cartOriginalKey,PersistentDataType.DOUBLE);
        Block block=railAt(cart.getLocation());
        if(block==null){limitCart(cart,original);return;}
        Rail rail=(Rail)block.getBlockData();
        var velocity=cart.getVelocity();
        boolean straight=rail.getShape()==Rail.Shape.NORTH_SOUTH || rail.getShape()==Rail.Shape.EAST_WEST;
        boolean powered=block.getType()==Material.POWERED_RAIL && block.getBlockData() instanceof Powerable p && p.isPowered();
        if(!straight || brakingRail(block) || !clearTrackAhead(block,rail.getShape(),velocity)){limitCart(cart,original);return;}
        cart.setMaxSpeed(Math.max(original,cartSpeed));
        if(powered){
            double horizontal=Math.hypot(velocity.getX(),velocity.getZ());
            // Vanilla provides the initial direction and unpowered rails still brake normally.
            if(horizontal>0.02 && horizontal<cartSpeed){double scale=Math.min(cartSpeed,horizontal+cartAcceleration)/horizontal;cart.setVelocity(velocity.clone().setX(velocity.getX()*scale).setZ(velocity.getZ()*scale));}
        }
    }
    private static boolean brakingRail(Block block){
        return block.getType()==Material.POWERED_RAIL && block.getBlockData() instanceof Powerable p && !p.isPowered();
    }
    private boolean clearTrackAhead(Block block,Rail.Shape shape,org.bukkit.util.Vector velocity){
        boolean eastWest=shape==Rail.Shape.EAST_WEST;
        double component=eastWest?velocity.getX():velocity.getZ();
        if(Math.abs(component)<0.01)return false;
        int direction=component>0?1:-1;
        for(int step=1;step<=4;step++){
            int x=block.getX()+(eastWest?direction*step:0),z=block.getZ()+(eastWest?0:direction*step);
            if(!block.getWorld().isChunkLoaded(x>>4,z>>4))return false;
            Block ahead=block.getWorld().getBlockAt(x,block.getY(),z);
            var next=ahead.getBlockData();
            if(!(next instanceof Rail r) || r.getShape()!=shape || brakingRail(ahead))return false;
        }
        return true;
    }
    private Block railAt(Location location){
        Block block=location.getBlock();
        if(block.getBlockData() instanceof Rail)return block;
        block=block.getRelative(0,-1,0);return block.getBlockData() instanceof Rail?block:null;
    }
    static void limitCart(RideableMinecart cart,double speed){
        cart.setMaxSpeed(speed);var velocity=cart.getVelocity();double horizontal=Math.hypot(velocity.getX(),velocity.getZ());
        if(horizontal>speed && horizontal>0)cart.setVelocity(velocity.clone().setX(velocity.getX()*speed/horizontal).setZ(velocity.getZ()*speed/horizontal));
    }
    private void restore(Entity entity){
        if(entity instanceof AbstractHorse horse){var attribute=horse.getAttribute(Attribute.MOVEMENT_SPEED);if(attribute!=null && attribute.getModifier(speedKey)!=null)attribute.removeModifier(speedKey);}
        if(entity instanceof RideableMinecart cart){var data=cart.getPersistentDataContainer();Double original=data.get(cartOriginalKey,PersistentDataType.DOUBLE);if(original!=null){limitCart(cart,Double.isFinite(original)&&original>=0?original:0.4);data.remove(cartOriginalKey);}}
    }
    private boolean restricted(Player player){return !closed && enabled && elytraEnabled && rider(player) && player.getWorld().getEnvironment()==World.Environment.NORMAL;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void rocket(PlayerElytraBoostEvent event){
        Player player=event.getPlayer();if(!restricted(player))return;
        var state=observeRocket(player,player.getLocation());long now=clock.getAsLong();
        if(state.allowed(now))return;
        event.setCancelled(true);event.setShouldConsume(false);
        if(tick-lastNotice.getOrDefault(player.getUniqueId(),-100L)>=40){
            lastNotice.put(player.getUniqueId(),tick);long remaining=state.remainingMillis(now);
            String seconds=java.math.BigDecimal.valueOf(rocketCooldownMillis,3).stripTrailingZeros().toPlainString();
            String message=remaining<0?"Your next rocket will be ready "+seconds+" seconds after you land. Water and boats count too!":"Your next rocket will be ready in "+((remaining+999)/1000)+"s. You can keep moving!";
            player.sendMessage(NookUi.message("NookTravel",message));
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void boosted(PlayerElytraBoostEvent event){
        Player player=event.getPlayer();if(!restricted(player) || event.isCancelled())return;
        // Record actual accepted boosts only, after other plugins have had their cancellation checks.
        var state=flight(player);state.used(landed(player,player.getLocation()));persistFlight(player,state);
    }
    private void forget(Player player){
        // Durable player tags retain the outstanding boost/deadline across reconnects and plugin restarts.
        flights.remove(player.getUniqueId());lastNotice.remove(player.getUniqueId());
        releaseVehicle(player.getVehicle(),player.getUniqueId());
    }
    static boolean hasOtherRider(Entity vehicle,UUID leaving){
        return vehicle.getPassengers().stream().anyMatch(e->!e.getUniqueId().equals(leaving) && e instanceof Player p && rider(p));
    }
    private void releaseVehicle(Entity vehicle,UUID leaving){
        if(vehicle==null || hasOtherRider(vehicle,leaving))return;
        restore(vehicle);activeMounts.remove(vehicle.getUniqueId());activeCarts.remove(vehicle.getUniqueId());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void dismount(EntityDismountEvent event){
        releaseVehicle(event.getDismounted(),event.getEntity().getUniqueId());
    }
    @EventHandler public void quit(PlayerQuitEvent event){forget(event.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent event){
        Player player=event.getPlayer();releaseVehicle(player.getVehicle(),player.getUniqueId());
        if(!closed && enabled && elytraEnabled && rider(player) && event.getTo()!=null)observeRocket(player,event.getTo());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void mode(PlayerGameModeChangeEvent event){releaseVehicle(event.getPlayer().getVehicle(),event.getPlayer().getUniqueId());}
    @EventHandler public void load(EntitiesLoadEvent event){for(Entity entity:event.getEntities())restore(entity);}
    @EventHandler public void unload(EntitiesUnloadEvent event){for(Entity entity:event.getEntities()){restore(entity);activeMounts.remove(entity.getUniqueId());activeCarts.remove(entity.getUniqueId());}}
    void close(){closed=true;HandlerList.unregisterAll(this);if(task!=null)task.cancel();for(var state:activeMounts.values())restore(state.horse);for(var cart:activeCarts.values())restore(cart);activeMounts.clear();activeCarts.clear();flights.clear();lastNotice.clear();}
}
