import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.*;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

/** Exact Paper adapter/lifecycle checks with real entities and an unregistered server-backed rider.
 * Controlled entity positions and clocks exercise sampling; this does not prove client steering. */
public final class TravelMilestoneChecks extends JavaPlugin implements Listener {
    private static final UUID ID=UUID.fromString("20261104-0000-4000-8000-000000000022");
    private JavaPlugin core;private Object travel,store,state;private Connection db;private World world;
    private Rider rider;private Horse wetHorse;private Horse horse;private RideableMinecart cart;
    private long initialLedger;private long tick=100,nanos=5_000_000_000L;private int checks,announcements,celebrations;
    private final Path checkpoint=Path.of("travel-checkpoint.properties");
    private Object field(Object target,String name)throws Exception{
        for(var type=target.getClass();type!=null;type=type.getSuperclass())try{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private Class<?> boxed(Class<?> t){if(t==long.class)return Long.class;if(t==int.class)return Integer.class;if(t==boolean.class)return Boolean.class;if(t==double.class)return Double.class;return t;}
    private Object invoke(Class<?> type,Object target,String name,Object...args)throws Exception{
        for(var m:type.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){
            boolean match=true;for(int i=0;i<args.length;i++)if(args[i]!=null&&!boxed(m.getParameterTypes()[i]).isInstance(args[i]))match=false;
            if(!match)continue;m.setAccessible(true);try{return m.invoke(target,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;if(e.getCause() instanceof Error x)throw x;throw e;}
        }throw new NoSuchMethodException(type.getName()+"."+name);
    }
    private Object call(Object target,String name,Object...args)throws Exception{return invoke(target.getClass(),target,name,args);}
    private Class<?> type(String name)throws Exception{return Class.forName("net.nikosnook.core."+name,true,core.getClass().getClassLoader());}
    private void check(boolean condition,String description){if(!condition)throw new AssertionError(description);checks++;getLogger().info("TRAVEL_PASS "+description);}
    private void fail(Throwable error){try{if(rider!=null)rider.real.leaveVehicle();}catch(Throwable ignored){}getLogger().log(java.util.logging.Level.SEVERE,"TRAVEL_FAILED checks="+checks,error);Bukkit.shutdown();}
    @EventHandler(priority=EventPriority.MONITOR) public void earned(PlayerAdvancementDoneEvent e){if(e.getAdvancement().getKey().getKey().startsWith("travel/")){check(e.message()!=null,"travel advancement has one visible announcement");announcements++;}}
    private long scalar(String sql)throws Exception{try(var s=db.createStatement();var r=s.executeQuery(sql)){if(!r.next())throw new AssertionError(sql);return r.getLong(1);}}
    private long total(String column)throws Exception{return scalar("SELECT "+column+" FROM travel_progress WHERE uuid='"+ID+"'");}
    private Object progress()throws Exception{return field(state,"progress");}
    private long pending(String kind)throws Exception{return (long)call(call(progress(),"pending"),kind+"Millimetres");}
    private long mileage()throws Exception{return pending("road")+pending("rail");}
    private void reset()throws Exception{call(progress(),"reset");}
    private void sample()throws Exception{tick+=5;nanos+=250_000_000;call(travel,"samplePlayer",rider.proxy,tick,nanos);}
    private void at(Entity entity,double x,double y,double z){var h=((CraftEntity)entity).getHandle();h.setPos(x,y,z);h.setOnGround(true);entity.setVelocity(new org.bukkit.util.Vector());}
    private boolean surface(String name,Location at,Object...extra)throws Exception{Object[] args=new Object[extra.length+1];args[0]=at;System.arraycopy(extra,0,args,1,extra.length);return (boolean)invoke(type("TravelSurfaces"),null,name,args);}
    private boolean road(double x,double y,double z,double half)throws Exception{return surface("roadAt",new Location(world,x,y,z),half,(Predicate<Material>)(m->m==Material.COBBLESTONE||m==Material.DIRT_PATH||m==Material.COBBLESTONE_SLAB||m==Material.COBBLESTONE_STAIRS));}
    private boolean rail(double x,double y,double z)throws Exception{return surface("railAt",new Location(world,x,y,z));}
    private void block(int x,int y,int z,Material m){world.getBlockAt(x,y,z).setType(m,false);}
    private void flat(int x0,int x1,int z0,int z1,int y,Material m){for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){block(x,y,z,m);for(int h=1;h<=4;h++)block(x,y+h,z,Material.AIR);}}
    private void track(int x,int y,int z,Rail.Shape shape){block(x,y-1,z,Material.COBBLESTONE);block(x,y,z,Material.RAIL);var rail=(Rail)world.getBlockAt(x,y,z).getBlockData();rail.setShape(shape);world.getBlockAt(x,y,z).setBlockData(rail,false);}
    private ServerPlayer player(UUID id,String name){
        var server=((CraftServer)getServer()).getServer();var profile=new GameProfile(id,name);
        var player=new ServerPlayer(server,((CraftWorld)world).getHandle(),profile,ClientInformation.createDefault());
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player,net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false));
        return player;
    }
    private final class Rider {
        final ServerPlayer handle;final Player real,proxy;GameMode mode=GameMode.SURVIVAL;boolean online=true,visible=true,dead;World overrideWorld;
        Rider(){
            handle=player(ID,"TravelRider");real=handle.getBukkitEntity();
            proxy=(Player)Proxy.newProxyInstance(getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
                case "isOnline","isConnected"->online;case "isDead"->dead;case "getGameMode"->mode;case "isVisibleByDefault"->visible;
                case "getWorld"->overrideWorld==null?world:overrideWorld;
                case "sendMessage","playSound","sendActionBar","showTitle","sendTitle","resetTitle"->null;
                case "hashCode"->ID.hashCode();case "equals"->o==a[0];case "toString"->"TravelMilestoneChecks[TravelRider]";
                default->{try{yield m.invoke(real,a);}catch(InvocationTargetException e){throw e.getCause();}}
            });
        }
    }
    private void loadState()throws Exception{
        state=call(travel,"rider",rider.proxy);var future=(Future<?>)field(state,"loading");set(state,"saved",future.get(3,TimeUnit.SECONDS));
    }
    private void attach(Entity vehicle)throws Exception{
        rider.real.leaveVehicle();check(vehicle.addPassenger(rider.real),"real Paper vehicle accepts server-backed passenger");
        check(vehicle.getPassengers().getFirst().getUniqueId().equals(ID)&&rider.real.getVehicle().equals(vehicle),"actual first-seat passenger and vehicle agree");reset();
    }
    private void spyCelebrations()throws Exception{
        var milestones=field(travel,"milestones");set(milestones,"celebration",(BiConsumer<Player,Boolean>)(p,c)->{celebrations++;try{invoke(type("QuestCelebrations"),null,"play",p,c);}catch(Exception error){throw new RuntimeException(error);}});
    }
    private void setup()throws Exception{
        check(Bukkit.isPrimaryThread()&&Bukkit.getMinecraftVersion().equals("26.3"),"exact Paper 26.3 main-thread runtime");
        core=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NookCore");check(core!=null&&core.isEnabled(),"candidate core enabled");
        store=field(core,"store");db=(Connection)field(store,"db");travel=field(core,"travelMilestones");
        check(!(boolean)field(travel,"disabled"),"local-only travel milestones enabled");
        ((org.bukkit.scheduler.BukkitTask)field(travel,"task")).cancel();
        world=Bukkit.getWorlds().getFirst();rider=new Rider();loadState();spyCelebrations();Bukkit.getPluginManager().registerEvents(this,this);
        check(Bukkit.getOnlinePlayers().isEmpty()&&Bukkit.getPlayer(ID)==null,"synthetic rider is never registered as global online player");
        if(!Files.exists(checkpoint)){check(scalar("SELECT COUNT(*) FROM accounts")==0,"fresh generated fixture database");call(store,"join",ID,"TravelRider",System.currentTimeMillis(),0L);}
        initialLedger=scalar("SELECT COUNT(*) FROM ledger");
        for(int x=0;x<=6;x++)for(int z=0;z<=3;z++)world.setChunkForceLoaded(x,z,true);
        flat(2,95,2,18,70,Material.COBBLESTONE);
        horse=world.spawn(new Location(world,8.5,71,8.5),Horse.class);horse.setAI(false);horse.setGravity(false);horse.setTamed(true);horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        for(int x=4;x<=85;x++)track(x,71,24,Rail.Shape.EAST_WEST);
        cart=world.spawn(new Location(world,8.5,71.0625,24.5),RideableMinecart.class);cart.setGravity(false);
        for(int x=38;x<=43;x++)for(int z=16;z<=18;z++)for(int y=71;y<=73;y++)block(x,y,z,Material.WATER);
        wetHorse=world.spawn(new Location(world,40.5,71,17.5),Horse.class);wetHorse.setAI(false);wetHorse.setGravity(false);wetHorse.setTamed(true);wetHorse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
    }
    private void shapes()throws Exception{
        check(road(8.5,71,8.5,.698),"full cobblestone supports complete real horse footprint");
        flat(4,12,12,16,70,Material.DIRT_PATH);check(road(8.5,70.9375,14.5,.698),"Paper dirt-path 15/16 collision top supports full footprint");
        flat(18,24,12,16,70,Material.COBBLESTONE_SLAB);check(road(20.5,70.5,14.5,.698),"Paper bottom-slab half-height supports full footprint");
        var top=(Slab)Bukkit.createBlockData(Material.COBBLESTONE_SLAB);top.setType(Slab.Type.TOP);for(int x=18;x<=24;x++)for(int z=12;z<=16;z++)world.getBlockAt(x,70,z).setBlockData(top,false);
        check(road(20.5,71,14.5,.698),"Paper top-slab top supports full footprint");
        block(9,70,8,Material.AIR);check(!road(8.5,71,8.5,.698),"missing edge column rejects full mount footprint");block(9,70,8,Material.COBBLESTONE);
        flat(28,34,12,16,70,Material.COBBLESTONE_STAIRS);check(!road(30.5,71,14.5,.698),"mixed stair heights conservatively reject unsupported footprint halves");
        check(!road(20.5,70.5,14.5,.698),"wrong partial-block height rejected");
        check(rail(8.5,71.0625,24.5),"cart flat rail height/path and collision support accepted");
        check(!rail(8.5,71.95,24.5)&&!rail(8.5,72.0625,24.5),"hovering cart above rail rejected");
        check(!rail(8.5,71.0625,24.95),"off-track cart in same rail column rejected");
        block(8,70,24,Material.AIR);check(!rail(8.5,71.0625,24.5),"rail retained without collision support rejected");block(8,70,24,Material.COBBLESTONE);
        track(12,71,28,Rail.Shape.SOUTH_EAST);check(rail(12.75,71.0625,28.75),"actual Paper curved rail diagonal accepted");check(!rail(12.05,71.0625,28.05),"wrong corner of curved rail rejected");
        for(var shape:List.of(Rail.Shape.ASCENDING_EAST,Rail.Shape.ASCENDING_WEST,Rail.Shape.ASCENDING_NORTH,Rail.Shape.ASCENDING_SOUTH)){
            track(16,71,28,shape);check(rail(16.5,71.5625,28.5),"actual Paper sloping rail midpoint accepted "+shape);
        }
        int loaded=world.getLoadedChunks().length;int chunk=10000;check(!world.isChunkLoaded(chunk,chunk),"unloaded boundary starts unloaded");
        check(!road(chunk*16+.5,71,chunk*16+.5,.698)&&!rail(chunk*16+.5,71,chunk*16+.5),"unloaded road and rail rejected without lookup");
        check(world.getLoadedChunks().length==loaded&&!world.isChunkLoaded(chunk,chunk),"surface checks load or generate no terrain");
    }
    private void ride(Entity vehicle,double y,double z,String kind,int blocks)throws Exception{
        attach(vehicle);at(vehicle,8.5,y,z);sample();long before=pending(kind);for(int i=1;i<=blocks;i++){at(vehicle,8.5+i,y,z);sample();}
        check(pending(kind)-before==blocks*1000L,"occupied "+kind+" route counts horizontal millimetres precisely");
    }
    private void excluded(String name,Runnable action,Runnable restore)throws Exception{
        reset();at(horse,8.5,71,8.5);sample();long before=mileage();action.run();at(horse,10.5,71,8.5);sample();restore.run();at(horse,12.5,71,8.5);sample();
        check(mileage()==before,name+" and return discard connector");
    }
    @SuppressWarnings("unchecked") private void exclusions()throws Exception{
        attach(horse);excluded("Creative",()->rider.mode=GameMode.CREATIVE,()->rider.mode=GameMode.SURVIVAL);
        excluded("Spectator",()->rider.mode=GameMode.SPECTATOR,()->rider.mode=GameMode.SURVIVAL);
        excluded("hidden-by-default",()->rider.visible=false,()->rider.visible=true);
        excluded("vanished metadata",()->rider.real.setMetadata("vanished",new FixedMetadataValue(this,true)),()->rider.real.removeMetadata("vanished",this));
        var f=type("StaffMode").getDeclaredField("ACTIVE");f.setAccessible(true);var active=(Set<UUID>)f.get(null);
        excluded("active hidden staff",()->active.add(ID),()->active.remove(ID));
        World nether=(World)Proxy.newProxyInstance(getClassLoader(),new Class[]{World.class},(o,m,a)->m.getName().equals("getEnvironment")?World.Environment.NETHER:m.invoke(world,a));
        excluded("other dimension",()->rider.overrideWorld=nether,()->rider.overrideWorld=null);
        excluded("offline rider",()->rider.online=false,()->rider.online=true);
        excluded("dead rider",()->rider.dead=true,()->rider.dead=false);
        excluded("untamed horse",()->horse.setTamed(false),()->horse.setTamed(true));
        excluded("unsaddled horse",()->horse.getInventory().setSaddle(null),()->horse.getInventory().setSaddle(new ItemStack(Material.SADDLE)));
        reset();at(horse,8.5,71,8.5);sample();long before=mileage();at(horse,10.5,74,8.5);((CraftEntity)horse).getHandle().setOnGround(false);sample();at(horse,12.5,71,8.5);sample();check(mileage()==before,"airborne/jumping mount cannot count or connect");
        reset();at(horse,8.5,71,8.5);sample();before=mileage();block(10,70,8,Material.AIR);at(horse,10.5,71,8.5);sample();block(10,70,8,Material.COBBLESTONE);at(horse,12.5,71,8.5);sample();check(mileage()==before,"unsupported ground breaks ride continuity");
        rider.real.leaveVehicle();before=mileage();sample();at(horse,20.5,71,8.5);sample();check(mileage()==before,"walking rider and empty horse cannot count");
        attach(cart);at(cart,8.5,71.0625,24.5);sample();before=mileage();rider.real.leaveVehicle();at(cart,9.5,71.0625,24.5);sample();check(mileage()==before,"empty rideable minecart excluded");
        var camel=world.spawn(new Location(world,50.5,71,8.5),Camel.class);camel.setAI(false);camel.setGravity(false);camel.setTamed(true);camel.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        var first=player(UUID.randomUUID(),"FrontRider").getBukkitEntity();
        check(camel.addPassenger(first)&&camel.addPassenger(rider.real),"actual camel accepts front and rear passengers");before=mileage();sample();at(camel,51.5,71,8.5);sample();check(mileage()==before&&camel.getPassengers().get(1).getUniqueId().equals(ID),"rear camel seat excluded");camel.eject();camel.remove();
        attach(horse);
    }
    private void waterAndRailShapes()throws Exception{
        check(wetHorse.isInWater(),"Paper engine marks actual submerged horse in water");attach(wetHorse);long before=mileage();sample();at(wetHorse,41.5,71,17.5);sample();check(mileage()==before,"occupied saddled horse in water is excluded");
        track(12,71,28,Rail.Shape.SOUTH_EAST);attach(cart);at(cart,12.6,71.0625,28.9);sample();long rail=pending("rail");at(cart,12.9,71.0625,28.6);sample();check(pending("rail")-rail>=424&&pending("rail")-rail<=425,"occupied cart curve counts horizontal curve chord");
        track(16,71,28,Rail.Shape.ASCENDING_EAST);reset();at(cart,16.2,71.2625,28.5);sample();rail=pending("rail");at(cart,16.8,71.8625,28.5);sample();check(pending("rail")-rail>=600&&pending("rail")-rail<=601,"occupied cart slope counts horizontal distance only");
        attach(horse);
    }
    private void transitions()throws Exception{
        at(horse,8.5,71,8.5);sample();long before=mileage();
        Bukkit.getPluginManager().callEvent(new PlayerTeleportEvent(rider.proxy,new Location(world,8.5,71,8.5),new Location(world,9,71,8.5)));
        at(horse,9,71,8.5);sample();check(mileage()==before,"small player teleport event resets connector");
        Bukkit.getPluginManager().callEvent(new EntityTeleportEvent(horse,horse.getLocation(),new Location(world,9.5,71,8.5)));at(horse,9.5,71,8.5);sample();check(mileage()==before,"small vehicle teleport event resets connector");
        check(horse.teleport(new Location(world,10.5,71,8.5),PlayerTeleportEvent.TeleportCause.PLUGIN,io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS),"actual occupied horse teleport succeeds");sample();check(mileage()==before,"actual vehicle teleport resets connector");
        Bukkit.getPluginManager().callEvent(new EntityDismountEvent(rider.real,horse));at(horse,11.5,71,8.5);sample();check(mileage()==before,"dismount event resets connector");
        Bukkit.getPluginManager().callEvent(new PlayerChangedWorldEvent(rider.proxy,world));at(horse,12.5,71,8.5);sample();check(mileage()==before,"world-change event resets connector");
        Bukkit.getPluginManager().callEvent(new PlayerRespawnEvent(rider.proxy,horse.getLocation(),false,false));at(horse,13.5,71,8.5);sample();check(mileage()==before,"respawn event resets connector");
        tick+=40;at(horse,14.5,71,8.5);sample();check(mileage()==before,"delayed tick sample has no catch-up credit");
        nanos+=2_000_000_000L;at(horse,15.5,71,8.5);sample();check(mileage()==before,"delayed wall-clock sample has no catch-up credit");
        at(horse,70.5,71,8.5);sample();check(mileage()==before,"large movement beyond bounded envelope rejected");
    }
    private void finishFirst()throws Exception{
        // Both threshold writes already queued; wait off-thread work via completed Future, then poll on main thread.
        var writer=(ExecutorService)field(travel,"writer");writer.submit(()->{}).get(3,TimeUnit.SECONDS);call(travel,"poll");sample();writer.submit(()->{}).get(3,TimeUnit.SECONDS);call(travel,"poll");call(travel,"grantSaved",state,rider.proxy);
        check(total("road_mm")>=20_000&&total("rail_mm")>=20_000,"both 20-block goals durably committed");
        check(announcements==2&&celebrations==2,"exactly one advancement announcement and real celebration per goal");
        call(travel,"grantSaved",state,rider.proxy);check(announcements==2&&celebrations==2,"repeat saved-threshold check is idempotent");
        check(scalar("SELECT cents FROM accounts WHERE uuid='"+ID+"'")==0&&scalar("SELECT COUNT(*) FROM ledger")==initialLedger,"both cosmetic awards leave zero balance and no added ledger entries");
        rider.handle.getAdvancements().save();
        attach(horse);at(horse,80.5,71,8.5);sample();at(horse,81.5,71,8.5);sample();
        long expectedRoad=total("road_mm")+pending("road"),expectedRail=total("rail_mm")+pending("rail");
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);writer.submit(()->{entered.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});entered.await(1,TimeUnit.SECONDS);
        Bukkit.getPluginManager().callEvent(new PlayerQuitEvent(rider.proxy,(net.kyori.adventure.text.Component)null));
        check((boolean)field(state,"writing"),"quit queues asynchronous save while writer is occupied");
        var p=new Properties();p.setProperty("road",Long.toString(expectedRoad));p.setProperty("rail",Long.toString(expectedRail));try(var out=Files.newOutputStream(checkpoint)){p.store(out,"clean-shutdown exact totals");}
        release.countDown();call(travel,"close");check(total("road_mm")==expectedRoad&&total("rail_mm")==expectedRail,"quit plus immediate close commits absolute snapshots once");
        rider.real.leaveVehicle();getLogger().info("TRAVEL_PHASE1_COMPLETE checks="+checks+" road="+expectedRoad+" rail="+expectedRail);Bukkit.shutdown();
    }
    private void restart()throws Exception{
        var p=new Properties();try(var in=Files.newInputStream(checkpoint)){p.load(in);}long road=Long.parseLong(p.getProperty("road")),rail=Long.parseLong(p.getProperty("rail"));
        check(total("road_mm")==road&&total("rail_mm")==rail,"full process restart preserves exact monotonic totals");
        check(field(progress(),"anchor")==null,"restart has no persisted position connector");
        for(String path:List.of("travel/a_familiar_road","travel/on_the_rails")){var a=Bukkit.getAdvancement(new NamespacedKey("nikosnook",path));check(a!=null&&rider.real.getAdvancementProgress(a).isDone(),"Minecraft saved criterion survives restart "+path);}
        call(travel,"grantSaved",state,rider.proxy);check(announcements==0&&celebrations==0,"restart rejoin creates no repeat announcement or celebration");
        attach(horse);at(horse,60.5,71,8.5);sample();check(mileage()==0,"first post-restart sample grants no connector");
        rider.real.leaveVehicle();call(travel,"close");check(total("road_mm")==road&&total("rail_mm")==rail,"repeat clean close cannot duplicate totals");
        check(scalar("SELECT cents FROM accounts WHERE uuid='"+ID+"'")==0&&scalar("SELECT COUNT(*) FROM ledger")==initialLedger,"restart leaves balance and ledger untouched");
        check((boolean)field(core,"healthy"),"economy remains healthy after travel checks");getLogger().info("TRAVEL_RESTART_COMPLETE checks="+checks);Bukkit.shutdown();
    }
    private void runChecks(){try{if(Files.exists(checkpoint)){restart();return;}shapes();ride(horse,71,8.5,"road",21);ride(cart,71.0625,24.5,"rail",21);check(announcements==0&&celebrations==0,"unsaved/unchecked threshold cannot celebrate early");exclusions();waterAndRailShapes();transitions();finishFirst();}catch(Throwable e){fail(e);}}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{setup();Bukkit.getScheduler().runTaskLater(this,this::runChecks,10);}catch(Throwable e){fail(e);}},60);}
}
