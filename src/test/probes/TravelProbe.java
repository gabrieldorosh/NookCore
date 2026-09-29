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
    private boolean grounded;
    private Object travel; private Class<?> type; private Horse horse; private World world; private int checks;
    private Object call(String name,Class<?>[] types,Object... args)throws Exception {Method m=type.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(travel,args);}
    private Object field(String name)throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(travel);}
    private void tick(long value)throws Exception {Field f=type.getDeclaredField("tick");f.setAccessible(true);f.setLong(travel,value);}
    private void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;getLogger().info("PASS "+name);}
    @Override public void onEnable(){getServer().getScheduler().runTaskLater(this,()->{
        try{
            var core=getServer().getPluginManager().getPlugin("NookCore");Field f=core.getClass().getDeclaredField("groundedTravel");f.setAccessible(true);travel=f.get(core);type=travel.getClass();world=Bukkit.getWorlds().getFirst();
            for(int x=0;x<=1;x++)for(int z=0;z<=1;z++)world.setChunkForceLoaded(x,z,true);
            for(int x=4;x<=28;x++)for(int z=4;z<=12;z++){world.getBlockAt(x,64,z).setType(Material.COBBLESTONE);world.getBlockAt(x,65,z).setType(Material.AIR);world.getBlockAt(x,66,z).setType(Material.AIR);}
            horse=world.spawn(new Location(world,8.5,65,8.5),Horse.class);Bukkit.getMobGoals().removeAllGoals(horse);
            getServer().getScheduler().runTaskLater(this,()->{try{runChecks();}catch(Throwable e){failed(e);}finally{Bukkit.shutdown();}},30);
        }catch(Throwable e){failed(e);Bukkit.shutdown();}
    },40);}
    private Player mockPlayer(UUID id,World inWorld,GameMode mode){
        return (Player)Proxy.newProxyInstance(getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
            case "getUniqueId"->id;case "getWorld"->inWorld;case "getGameMode"->mode;case "isDead"->false;case "isOnGround"->grounded;case "isGliding","isSwimming","isInsideVehicle"->false;default->null;
        });
    }
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
        UUID id=UUID.randomUUID();Player player=(Player)Proxy.newProxyInstance(getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
            case "getUniqueId"->id;case "getWorld"->world;case "getGameMode"->GameMode.SURVIVAL;case "isDead"->false;case "isOnGround"->grounded;case "isGliding","isSwimming","isInsideVehicle"->false;default->null;
        });
        Firework firework=world.spawn(new Location(world,24,68,8),Firework.class);tick(100);
        var denied=new PlayerElytraBoostEvent(player,new ItemStack(Material.FIREWORK_ROCKET),firework,EquipmentSlot.HAND);call("rocket",new Class[]{PlayerElytraBoostEvent.class},denied);
        check(denied.isCancelled() && !denied.shouldConsume(),"airborne entry denies rocket without consumption");
        grounded=true;tick(39);call("updateTakeoff",new Class[]{Player.class},player);
        tick(99);call("updateTakeoff",new Class[]{Player.class},player);
        grounded=false;tick(100);call("updateTakeoff",new Class[]{Player.class},player);tick(160);
        var allowed=new PlayerElytraBoostEvent(player,new ItemStack(Material.FIREWORK_ROCKET),firework,EquipmentSlot.HAND);call("rocket",new Class[]{PlayerElytraBoostEvent.class},allowed);check(!allowed.isCancelled(),"three-second boundary allows takeoff rocket");
        tick(161);var expired=new PlayerElytraBoostEvent(player,new ItemStack(Material.FIREWORK_ROCKET),firework,EquipmentSlot.HAND);call("rocket",new Class[]{PlayerElytraBoostEvent.class},expired);check(expired.isCancelled(),"boost does not extend window");
        for(World.Environment environment:new World.Environment[]{World.Environment.NETHER,World.Environment.THE_END}){
            World exemptWorld=(World)Proxy.newProxyInstance(getClassLoader(),new Class[]{World.class},(o,m,a)->m.getName().equals("getEnvironment")?environment:null);
            Player exempt=mockPlayer(UUID.randomUUID(),exemptWorld,GameMode.SURVIVAL);
            var event=new PlayerElytraBoostEvent(exempt,new ItemStack(Material.FIREWORK_ROCKET),firework,EquipmentSlot.OFF_HAND);
            call("rocket",new Class[]{PlayerElytraBoostEvent.class},event);
            check(!event.isCancelled() && event.shouldConsume(),"rocket exemption remains "+environment);
        }
        var creative=new PlayerElytraBoostEvent(mockPlayer(UUID.randomUUID(),world,GameMode.CREATIVE),new ItemStack(Material.FIREWORK_ROCKET),firework,EquipmentSlot.HAND);
        call("rocket",new Class[]{PlayerElytraBoostEvent.class},creative);check(!creative.isCancelled(),"creative rockets remain unrestricted");
        var offhand=new PlayerElytraBoostEvent(player,new ItemStack(Material.FIREWORK_ROCKET),firework,EquipmentSlot.OFF_HAND);
        call("rocket",new Class[]{PlayerElytraBoostEvent.class},offhand);check(offhand.isCancelled() && !offhand.shouldConsume(),"expired offhand rocket is not consumed");
        grounded=true;tick(170);call("updateTakeoff",new Class[]{Player.class},player);
        grounded=false;tick(180);call("updateTakeoff",new Class[]{Player.class},player);
        var bounce=new PlayerElytraBoostEvent(player,new ItemStack(Material.FIREWORK_ROCKET),firework,EquipmentSlot.HAND);
        call("rocket",new Class[]{PlayerElytraBoostEvent.class},bounce);check(bounce.isCancelled() && !bounce.shouldConsume(),"short landing cannot re-arm rocket window");
        grounded=true;tick(181);call("updateTakeoff",new Class[]{Player.class},player);tick(241);call("updateTakeoff",new Class[]{Player.class},player);
        grounded=false;tick(242); // Rocket precedes the next contact-sampling tick.
        var rearmed=new PlayerElytraBoostEvent(player,new ItemStack(Material.FIREWORK_ROCKET),firework,EquipmentSlot.OFF_HAND);
        call("rocket",new Class[]{PlayerElytraBoostEvent.class},rearmed);check(!rearmed.isCancelled(),"three seconds continuously grounded re-arms offhand rocket");
        call("forget",new Class[]{Player.class},player);
        var forgotten=new PlayerElytraBoostEvent(player,new ItemStack(Material.FIREWORK_ROCKET),firework,EquipmentSlot.HAND);
        call("rocket",new Class[]{PlayerElytraBoostEvent.class},forgotten);check(forgotten.isCancelled(),"relog teleport and mode cleanup removes armed flight");
        call("close",new Class[]{});
        cart.setMaxSpeed(1.6);cart.getPersistentDataContainer().set(new NamespacedKey("nookcore","travel-original-cart-speed"),PersistentDataType.DOUBLE,.6);
        var core=(JavaPlugin)getServer().getPluginManager().getPlugin("NookCore");
        core.getConfig().set("travel.grounded.enabled",false);
        Constructor<?> constructor=type.getDeclaredConstructor(JavaPlugin.class);constructor.setAccessible(true);
        travel=constructor.newInstance(core);
        check(Math.abs(cart.getMaxSpeed()-.6)<1e-6 && !cart.getPersistentDataContainer().has(new NamespacedKey("nookcore","travel-original-cart-speed"),PersistentDataType.DOUBLE),"disabled feature startup recovers saved cart limit");
        var disabledRocket=new PlayerElytraBoostEvent(player,new ItemStack(Material.FIREWORK_ROCKET),firework,EquipmentSlot.HAND);
        call("rocket",new Class[]{PlayerElytraBoostEvent.class},disabledRocket);check(!disabledRocket.isCancelled(),"disabled feature does not restrict rockets");
        call("close",new Class[]{});core.getConfig().set("travel.grounded.enabled",true);
        horse.remove();cart.remove();firework.remove();
        try{Class<?> release=Class.forName("ReleasePresentationChecks");var run=release.getDeclaredMethod("run",JavaPlugin.class);run.setAccessible(true);run.invoke(null,this);}catch(ClassNotFoundException ignored){}
        TravelCourse.build(world);getLogger().info("TRAVEL_COURSE_READY spawn=8,181,3 markers=50,150");getLogger().info("TRAVEL_PROBE_PASS checks="+checks);
    }
}
