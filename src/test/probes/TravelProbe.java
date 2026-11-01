import java.lang.reflect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.Powerable;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;
import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;

/** Disposable Paper-world adapter checks, not a substitute for rider/client tests. */
public final class TravelProbe extends JavaPlugin {
    private final java.util.concurrent.atomic.AtomicLong clock=new java.util.concurrent.atomic.AtomicLong(100_000);
    private Object travel; private Class<?> type; private Horse horse; private World world; private int checks;
    private Object call(String name,Class<?>[] types,Object... args)throws Exception {Method m=type.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(travel,args);}
    private Object field(String name)throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(travel);}
    private void tick(long value)throws Exception {Field f=type.getDeclaredField("tick");f.setAccessible(true);f.setLong(travel,value);}
    private void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;getLogger().info("PASS "+name);}
    @Override public void onEnable(){getServer().getScheduler().runTaskLater(this,()->{
        try{
            var core=getServer().getPluginManager().getPlugin("NookCore");Field f=core.getClass().getDeclaredField("groundedTravel");f.setAccessible(true);travel=f.get(core);type=travel.getClass();world=Bukkit.getWorlds().getFirst();
            call("close",new Class[]{});
            Constructor<?> constructor=type.getDeclaredConstructor(JavaPlugin.class,java.util.function.LongSupplier.class);constructor.setAccessible(true);
            travel=constructor.newInstance(core,(java.util.function.LongSupplier)clock::get);f.set(core,travel);
            for(int x=0;x<=1;x++)for(int z=0;z<=1;z++)world.setChunkForceLoaded(x,z,true);
            for(int x=4;x<=28;x++)for(int z=4;z<=12;z++){world.getBlockAt(x,64,z).setType(Material.COBBLESTONE);world.getBlockAt(x,65,z).setType(Material.AIR);world.getBlockAt(x,66,z).setType(Material.AIR);}
            horse=world.spawn(new Location(world,8.5,65,8.5),Horse.class);Bukkit.getMobGoals().removeAllGoals(horse);
            getServer().getScheduler().runTaskLater(this,()->{try{runChecks();}catch(Throwable e){failed(e);}finally{Bukkit.shutdown();}},30);
        }catch(Throwable e){failed(e);Bukkit.shutdown();}
    },40);}
    private static Object defaultValue(Class<?> type){
        if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;if(type==double.class)return 0.0;if(type==float.class)return 0f;return null;
    }
    private final class FlightPlayer {
        final UUID id;final org.bukkit.persistence.PersistentDataContainer data;final Player player;
        Location location=new Location(world,8.5,80,8.5);World inWorld=world;GameMode mode=GameMode.SURVIVAL;
        boolean water;Entity vehicle;
        FlightPlayer(){this(UUID.randomUUID());}
        FlightPlayer(UUID id){
            this.id=id;data=world.getPersistentDataContainer().getAdapterContext().newPersistentDataContainer();
            player=(Player)Proxy.newProxyInstance(getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
                case "getLocation"->location.clone();case "getUniqueId"->id;case "getWorld"->inWorld;case "getGameMode"->mode;
                case "getPersistentDataContainer"->data;case "getName"->"RocketTester";case "getVehicle"->vehicle;
                case "isOnGround"->false;case "isGliding"->true;case "isInWater","isSwimming"->water;case "isInsideVehicle"->vehicle!=null;
                case "equals"->o==a[0];case "hashCode"->System.identityHashCode(o);default->defaultValue(m.getReturnType());
            });
        }
        void at(double x,double y,double z){location=new Location(inWorld,x,y,z);}
    }
    private Object observe(FlightPlayer player)throws Exception{return call("observeRocket",new Class[]{Player.class,Location.class},player.player,player.location);}
    private PlayerElytraBoostEvent boost(FlightPlayer player,Firework firework,EquipmentSlot hand){
        var event=new PlayerElytraBoostEvent(player.player,new ItemStack(Material.FIREWORK_ROCKET),firework,hand);
        getServer().getPluginManager().callEvent(event);return event;
    }
    private long deadline(FlightPlayer player){return player.data.getOrDefault(new NamespacedKey("nookcore","rocket-ready-at"),PersistentDataType.LONG,-1L);}
    private void failed(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"TRAVEL_PROBE_FAILED",e);}
    @SuppressWarnings("unchecked") private void runChecks()throws Exception {
        check((boolean)call("roadUnder",new Class[]{Location.class},new Location(world,8.5,65,8.5)),"cobblestone surface");
        world.getBlockAt(9,64,8).setType(Material.DIRT_PATH);
        check((boolean)call("roadUnder",new Class[]{Location.class},new Location(world,9.5,64.9375,8.5)),"path fractional height");
        world.getBlockAt(10,64,8).setType(Material.COBBLESTONE_SLAB);
        check((boolean)call("roadUnder",new Class[]{Location.class},new Location(world,10.5,64.5,8.5)),"bottom slab fractional height");
        world.getBlockAt(11,64,8).setType(Material.GRASS_BLOCK);
        check(!(boolean)call("roadUnder",new Class[]{Location.class},new Location(world,11.5,65,8.5)),"grass stays ordinary");
        world.getBlockAt(12,64,8).setType(Material.COBBLESTONE_STAIRS);
        var stairs=(org.bukkit.block.data.type.Stairs)world.getBlockAt(12,64,8).getBlockData();stairs.setFacing(org.bukkit.block.BlockFace.NORTH);world.getBlockAt(12,64,8).setBlockData(stairs,false);
        check((boolean)call("roadUnder",new Class[]{Location.class},new Location(world,12.5,64.5,8.8)),"lower stair tread supports road boost");
        for(Material material:new Material[]{Material.BRICKS,Material.GRAVEL,Material.MOSSY_COBBLESTONE,Material.CRACKED_STONE_BRICKS,Material.COARSE_DIRT,Material.PODZOL}){
            world.getBlockAt(13,64,8).setType(material);
            check((boolean)call("roadUnder",new Class[]{Location.class},new Location(world,13.5,65,8.5)),"mixed road surface "+material);
        }
        world.getBlockAt(13,64,8).setType(Material.BRICK_SLAB);
        check((boolean)call("roadUnder",new Class[]{Location.class},new Location(world,13.5,64.5,8.5)),"brick slab supports road boost");
        var speed=horse.getAttribute(Attribute.MOVEMENT_SPEED);
        double unmodifiedBase=speed.getBaseValue();
        var existingKey=new NamespacedKey(this,"existing-speed");
        speed.addTransientModifier(new AttributeModifier(existingKey,.25,AttributeModifier.Operation.ADD_SCALAR));
        check(horse.isOnGround(),"horse settled on course");
        double base=horse.getAttribute(Attribute.MOVEMENT_SPEED).getValue();
        for(int i=1;i<=20;i++){tick(i);call("updateMount",new Class[]{AbstractHorse.class},horse);}
        check(Math.abs(horse.getAttribute(Attribute.MOVEMENT_SPEED).getValue()-base*3)<1e-6,"mount reaches multiplier without replacing base");
        world.getBlockAt(8,64,8).setType(Material.GRASS_BLOCK);tick(40);call("updateMount",new Class[]{AbstractHorse.class},horse);
        check(Math.abs(horse.getAttribute(Attribute.MOVEMENT_SPEED).getValue()-base*3)<1e-6,"road gap retains boost");
        for(int i=51;i<=80;i++){tick(i);call("updateMount",new Class[]{AbstractHorse.class},horse);}
        check(Math.abs(horse.getAttribute(Attribute.MOVEMENT_SPEED).getValue()-base)<1e-6,"off-road returns to original attribute");
        check(Math.abs(speed.getBaseValue()-unmodifiedBase)<1e-6 && speed.getModifier(existingKey)!=null,"road boost preserves base and existing percentage modifier");
        speed.removeModifier(existingKey);
        check(Math.abs(speed.getValue()-unmodifiedBase)<1e-6,"removing independent modifier leaves original speed");
        for(int x=14;x<=26;x++){world.getBlockAt(x,64,8).setType(Material.REDSTONE_BLOCK);var b=world.getBlockAt(x,65,8);b.setType(Material.POWERED_RAIL);var data=b.getBlockData();((Rail)data).setShape(Rail.Shape.EAST_WEST);((Powerable)data).setPowered(true);b.setBlockData(data,false);}
        RideableMinecart cart=world.spawn(new Location(world,15.5,65.1,8.5),RideableMinecart.class);cart.setMaxSpeed(.6);cart.setVelocity(new org.bukkit.util.Vector(.3,0,0));
        call("updateCart",new Class[]{RideableMinecart.class},cart);
        check(Math.abs(cart.getMaxSpeed()-1.6)<1e-6,"straight track raises cart cap");check(cart.getVelocity().getX()>.3,"powered rail accelerates cart");
        world.getBlockAt(18,64,8).setType(Material.STONE);
        var brake=world.getBlockAt(18,65,8);var brakeData=brake.getBlockData();((Powerable)brakeData).setPowered(false);brake.setBlockData(brakeData,false);
        cart.setVelocity(new org.bukkit.util.Vector(1.6,0,0));call("updateCart",new Class[]{RideableMinecart.class},cart);
        check(Math.abs(cart.getMaxSpeed()-.6)<1e-6 && cart.getVelocity().getX()<=.600001,"unpowered braking rail ahead slows before entry");
        cart.teleport(new Location(world,18.5,65.1,8.5));cart.setVelocity(new org.bukkit.util.Vector(1.6,0,0));call("updateCart",new Class[]{RideableMinecart.class},cart);
        check(Math.abs(cart.getMaxSpeed()-.6)<1e-6 && cart.getVelocity().getX()<=.600001,"braking rail itself retains original cap");
        cart.teleport(new Location(world,15.5,65.1,8.5));
        world.getBlockAt(18,65,8).setType(Material.AIR);cart.setVelocity(new org.bukkit.util.Vector(1.6,0,0));call("updateCart",new Class[]{RideableMinecart.class},cart);
        check(Math.abs(cart.getMaxSpeed()-.6)<1e-6 && cart.getVelocity().getX()<=.600001,"track gap ahead restores original cap and slows");
        call("restore",new Class[]{Entity.class},cart);
        check(!cart.getPersistentDataContainer().has(new NamespacedKey("nookcore","travel-original-cart-speed"),PersistentDataType.DOUBLE),"restoration removes recovery marker");
        cart.setMaxSpeed(1.6);cart.getPersistentDataContainer().set(new NamespacedKey("nookcore","travel-original-cart-speed"),PersistentDataType.DOUBLE,.6);call("restore",new Class[]{Entity.class},cart);
        check(Math.abs(cart.getMaxSpeed()-.6)<1e-6,"interrupted-run saved cart speed recovered");
        check((boolean)call("groundContact",new Class[]{Location.class},new Location(world,9.5,64.9375,8.5)),"path fractional height counts as landed");
        check((boolean)call("groundContact",new Class[]{Location.class},new Location(world,10.5,64.5,8.5)),"bottom slab counts as landed");
        world.getBlockAt(6,64,10).setType(Material.CHEST);
        check((boolean)call("groundContact",new Class[]{Location.class},new Location(world,6.5,64.875,10.5)),"chest fractional top counts as landed");
        check(!(boolean)call("groundContact",new Class[]{Location.class},new Location(world,8.5,65.2,8.5)),"air above a block does not count as landed");
        Firework firework=world.spawn(new Location(world,24,68,8),Firework.class);tick(100);
        FlightPlayer player=new FlightPlayer();observe(player);clock.addAndGet(600_000);
        check(!boost(player,firework,EquipmentSlot.HAND).isCancelled(),"first rocket works after a long unboosted glide with no takeoff window");
        var denied=boost(player,firework,EquipmentSlot.OFF_HAND);
        check(denied.isCancelled() && !denied.shouldConsume(),"second rocket in the same flight is denied without consuming offhand stock");
        clock.addAndGet(10_000);check(boost(player,firework,EquipmentSlot.HAND).isCancelled(),"long airtime after using a rocket does not rearm it");
        player.at(4.5,65,8.5);observe(player);long firstLanding=clock.get();
        for(int step=1;step<=10;step++){
            clock.addAndGet(100);player.at(4.5+step*.05,step%2==0?65:66,8.5);observe(player);
        }
        check(deadline(player)==firstLanding+3000,"walking jumping and repeated landings preserve the first landing deadline");
        clock.set(firstLanding+2999);player.at(7.5,80,8.5);
        check(boost(player,firework,EquipmentSlot.HAND).isCancelled(),"rocket remains blocked one millisecond before the landing deadline");
        clock.incrementAndGet();check(!boost(player,firework,EquipmentSlot.OFF_HAND).isCancelled(),"rocket works at exactly three seconds even while airborne again");
        check(boost(player,firework,EquipmentSlot.HAND).isCancelled(),"using the rearmed rocket spends the new flight allowance");
        var spentKey=new NamespacedKey("nookcore","rocket-flight-spent");
        check(Byte.valueOf((byte)1).equals(player.data.get(spentKey,PersistentDataType.BYTE)),"accepted boost writes the durable player-data marker");
        byte[] saved=player.data.serializeToBytes();call("forget",new Class[]{Player.class},player.player);
        FlightPlayer rejoined=new FlightPlayer(player.id);rejoined.data.readFromBytes(saved,true);
        check(boost(rejoined,firework,EquipmentSlot.HAND).isCancelled(),"serialized player data prevents an airborne relog granting another rocket");
        var teleport=new org.bukkit.event.player.PlayerTeleportEvent(rejoined.player,rejoined.location,new Location(world,12.5,80,11.5));
        call("teleport",new Class[]{org.bukkit.event.player.PlayerTeleportEvent.class},teleport);rejoined.location=teleport.getTo();
        check(boost(rejoined,firework,EquipmentSlot.HAND).isCancelled(),"teleporting into air does not erase the spent flight");
        var modeEvent=new org.bukkit.event.player.PlayerGameModeChangeEvent(rejoined.player,GameMode.CREATIVE);
        call("mode",new Class[]{org.bukkit.event.player.PlayerGameModeChangeEvent.class},modeEvent);rejoined.mode=GameMode.CREATIVE;
        check(!boost(rejoined,firework,EquipmentSlot.HAND).isCancelled(),"creative remains exempt even with an outstanding Survival boost");
        rejoined.mode=GameMode.SURVIVAL;
        check(boost(rejoined,firework,EquipmentSlot.HAND).isCancelled(),"changing game modes cannot grant another Survival rocket");
        rejoined.at(6.5,64.875,10.5);observe(rejoined);long chestLanding=clock.get();saved=rejoined.data.serializeToBytes();
        call("forget",new Class[]{Player.class},rejoined.player);clock.addAndGet(1500);
        FlightPlayer cooling=new FlightPlayer(rejoined.id);cooling.data.readFromBytes(saved,true);cooling.at(8.5,80,8.5);observe(cooling);
        check(deadline(cooling)==chestLanding+3000,"reconnecting halfway through cooldown preserves the original absolute deadline");
        clock.set(chestLanding+3000);check(!boost(cooling,firework,EquipmentSlot.HAND).isCancelled(),"cooldown time elapsed offline still counts");
        FlightPlayer jumper=new FlightPlayer();clock.addAndGet(10_000);
        jumper.at(5.5,65,8.5);observe(jumper);jumper.at(5.5,66,8.5);observe(jumper);jumper.at(6.5,64.875,10.5);observe(jumper);jumper.at(6.5,80,10.5);
        check(!boost(jumper,firework,EquipmentSlot.HAND).isCancelled(),"ordinary jumps and a chest-top fall do not spend an unused rocket");
        FlightPlayer launch=new FlightPlayer();launch.at(5.5,65,8.5);
        check(!boost(launch,firework,EquipmentSlot.HAND).isCancelled(),"rocket on a launch frame with lingering support is allowed");
        observe(launch);launch.at(5.5,80,8.5);observe(launch);clock.addAndGet(10_000);
        check(boost(launch,firework,EquipmentSlot.HAND).isCancelled() && deadline(launch)<0,"launch frame contact cannot start an in-flight rearm timer");
        FlightPlayer swimmer=new FlightPlayer();boost(swimmer,firework,EquipmentSlot.HAND);swimmer.water=true;observe(swimmer);long waterLanding=clock.get();
        swimmer.water=false;clock.addAndGet(3000);
        check(!boost(swimmer,firework,EquipmentSlot.HAND).isCancelled() && clock.get()==waterLanding+3000,"water starts the timer and leaving water does not pause it");
        Entity boat=world.spawnEntity(new Location(world,23,68,10),EntityType.OAK_BOAT);
        FlightPlayer sailor=new FlightPlayer();boost(sailor,firework,EquipmentSlot.HAND);sailor.vehicle=boat;observe(sailor);long boatLanding=clock.get();
        sailor.vehicle=null;clock.addAndGet(3000);
        check(!boost(sailor,firework,EquipmentSlot.HAND).isCancelled() && clock.get()==boatLanding+3000,"boarding a boat starts the timer even after dismounting again");boat.remove();
        FlightPlayer airborneMount=new FlightPlayer();boost(airborneMount,firework,EquipmentSlot.HAND);
        airborneMount.vehicle=(Entity)Proxy.newProxyInstance(getClassLoader(),new Class[]{Entity.class},(o,m,a)->defaultValue(m.getReturnType()));observe(airborneMount);
        check(deadline(airborneMount)<0,"boarding an airborne non-boat mount does not count as touching down");
        airborneMount.vehicle=horse;observe(airborneMount);
        check(deadline(airborneMount)==clock.get()+3000,"a grounded horse counts as a landing even above the block collision surface");
        FlightPlayer brief=new FlightPlayer();boost(brief,firework,EquipmentSlot.HAND);
        Location chest=new Location(world,6.5,64.875,10.5);
        call("move",new Class[]{org.bukkit.event.player.PlayerMoveEvent.class},new org.bukkit.event.player.PlayerMoveEvent(brief.player,brief.location,chest));
        long briefLanding=clock.get();brief.at(6.5,80,10.5);clock.addAndGet(3000);
        check(deadline(brief)==briefLanding+3000 && !boost(brief,firework,EquipmentSlot.HAND).isCancelled(),"a brief chest-top movement landing between scheduler samples starts the timer");
        FlightPlayer cancelled=new FlightPlayer();
        org.bukkit.event.Listener cancelLater=new org.bukkit.event.Listener(){
            @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.HIGHEST) public void cancel(PlayerElytraBoostEvent event){if(event.getPlayer()==cancelled.player)event.setCancelled(true);}
        };
        getServer().getPluginManager().registerEvents(cancelLater,this);
        check(boost(cancelled,firework,EquipmentSlot.HAND).isCancelled() && !cancelled.data.has(spentKey),"a later protection cancellation does not spend the rocket allowance");
        org.bukkit.event.HandlerList.unregisterAll(cancelLater);
        check(!boost(cancelled,firework,EquipmentSlot.HAND).isCancelled(),"the first successful rocket still works after a cancelled attempt");
        FlightPlayer preCancelled=new FlightPlayer();var alreadyCancelled=new PlayerElytraBoostEvent(preCancelled.player,new ItemStack(Material.FIREWORK_ROCKET),firework,EquipmentSlot.HAND);alreadyCancelled.setCancelled(true);
        getServer().getPluginManager().callEvent(alreadyCancelled);
        check(!preCancelled.data.has(spentKey) && !boost(preCancelled,firework,EquipmentSlot.HAND).isCancelled(),"an already cancelled event leaves the rocket available");
        for(World.Environment environment:new World.Environment[]{World.Environment.NETHER,World.Environment.THE_END}){
            World exemptWorld=(World)Proxy.newProxyInstance(getClassLoader(),new Class[]{World.class},(o,m,a)->m.getName().equals("getEnvironment")?environment:defaultValue(m.getReturnType()));
            FlightPlayer exempt=new FlightPlayer();exempt.inWorld=exemptWorld;
            check(!boost(exempt,firework,EquipmentSlot.OFF_HAND).isCancelled() && !boost(exempt,firework,EquipmentSlot.HAND).isCancelled(),"repeated rockets remain unrestricted in "+environment);
            check(!exempt.data.has(spentKey),"exempt dimension rockets do not spend the Overworld allowance "+environment);
        }
        FlightPlayer dimensional=new FlightPlayer();boost(dimensional,firework,EquipmentSlot.HAND);
        World endWorld=(World)Proxy.newProxyInstance(getClassLoader(),new Class[]{World.class},(o,m,a)->m.getName().equals("getEnvironment")?World.Environment.THE_END:defaultValue(m.getReturnType()));
        var intoEnd=new org.bukkit.event.player.PlayerTeleportEvent(dimensional.player,dimensional.location,new Location(endWorld,8.5,80,8.5));
        call("teleport",new Class[]{org.bukkit.event.player.PlayerTeleportEvent.class},intoEnd);dimensional.inWorld=endWorld;dimensional.location=intoEnd.getTo();
        check(!boost(dimensional,firework,EquipmentSlot.HAND).isCancelled() && !boost(dimensional,firework,EquipmentSlot.HAND).isCancelled(),"an outstanding Overworld flight does not restrict End rockets");
        var outOfEnd=new org.bukkit.event.player.PlayerTeleportEvent(dimensional.player,dimensional.location,new Location(world,8.5,80,8.5));
        call("teleport",new Class[]{org.bukkit.event.player.PlayerTeleportEvent.class},outOfEnd);dimensional.inWorld=world;dimensional.location=outOfEnd.getTo();
        check(boost(dimensional,firework,EquipmentSlot.HAND).isCancelled(),"returning from an airborne End visit does not erase the Overworld flight allowance");
        FlightPlayer creativePlayer=new FlightPlayer();creativePlayer.mode=GameMode.CREATIVE;
        check(!boost(creativePlayer,firework,EquipmentSlot.HAND).isCancelled() && !creativePlayer.data.has(spentKey),"creative rockets remain unrestricted and untracked");
        FlightPlayer adventure=new FlightPlayer();adventure.mode=GameMode.ADVENTURE;boost(adventure,firework,EquipmentSlot.HAND);
        check(boost(adventure,firework,EquipmentSlot.HAND).isCancelled(),"Adventure follows the same one-rocket flight policy");
        // Reconstruct the adapter: the player's tags, not this Java object's map, preserve the flight.
        call("close",new Class[]{});var corePlugin=(JavaPlugin)getServer().getPluginManager().getPlugin("NookCore");
        Constructor<?> timedConstructor=type.getDeclaredConstructor(JavaPlugin.class,java.util.function.LongSupplier.class);timedConstructor.setAccessible(true);
        travel=timedConstructor.newInstance(corePlugin,(java.util.function.LongSupplier)clock::get);
        check(boost(adventure,firework,EquipmentSlot.HAND).isCancelled(),"recreating the travel service preserves spent player data");
        call("close",new Class[]{});corePlugin.getConfig().set("travel.grounded.elytra.landing-cooldown-seconds",null);corePlugin.getConfig().set("travel.grounded.elytra.grounded-rearm-ticks",80);
        travel=timedConstructor.newInstance(corePlugin,(java.util.function.LongSupplier)clock::get);FlightPlayer legacy=new FlightPlayer();boost(legacy,firework,EquipmentSlot.HAND);legacy.at(5.5,65,8.5);observe(legacy);
        check(deadline(legacy)==clock.get()+4000,"existing rearm-ticks setting remains an elapsed-time cooldown fallback");
        call("close",new Class[]{});
        cart.setMaxSpeed(1.6);cart.getPersistentDataContainer().set(new NamespacedKey("nookcore","travel-original-cart-speed"),PersistentDataType.DOUBLE,.6);
        var core=(JavaPlugin)getServer().getPluginManager().getPlugin("NookCore");
        core.getConfig().set("travel.grounded.enabled",false);
        Constructor<?> constructor=type.getDeclaredConstructor(JavaPlugin.class);constructor.setAccessible(true);
        travel=constructor.newInstance(core);
        check(Math.abs(cart.getMaxSpeed()-.6)<1e-6 && !cart.getPersistentDataContainer().has(new NamespacedKey("nookcore","travel-original-cart-speed"),PersistentDataType.DOUBLE),"disabled feature startup recovers saved cart limit");
        var disabledRocket=new PlayerElytraBoostEvent(player.player,new ItemStack(Material.FIREWORK_ROCKET),firework,EquipmentSlot.HAND);
        call("rocket",new Class[]{PlayerElytraBoostEvent.class},disabledRocket);check(!disabledRocket.isCancelled(),"disabled feature does not restrict rockets");
        call("close",new Class[]{});core.getConfig().set("travel.grounded.enabled",true);
        horse.remove();cart.remove();firework.remove();
        try{Class<?> release=Class.forName("ReleasePresentationChecks");var run=release.getDeclaredMethod("run",JavaPlugin.class);run.setAccessible(true);run.invoke(null,this);}catch(ClassNotFoundException ignored){}
        TravelCourse.build(world);getLogger().info("TRAVEL_COURSE_READY spawn=8,181,3 markers=50,150");getLogger().info("TRAVEL_PROBE_PASS checks="+checks);
    }
}
