import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;

/** Builds only the disposable travel-local course; never shipped in NookCore. */
final class TravelCourse {
    private static final String TAG="nook-travel-course";
    static void build(World world){
        for(Entity entity:world.getEntities())if(entity.getScoreboardTags().contains(TAG))entity.remove();
        lane(world,0,210,0,54,Material.GRASS_BLOCK);
        for(int x=-1;x<=211;x++)for(int z:new int[]{-1,55})world.getBlockAt(x,181,z).setType(Material.OAK_FENCE,false);
        for(int z=-1;z<=55;z++)for(int x:new int[]{-1,211})world.getBlockAt(x,181,z).setType(Material.OAK_FENCE,false);
        lane(world,0,18,0,12,Material.STONE_BRICKS);
        lane(world,0,210,18,22,Material.COBBLESTONE);
        lane(world,0,210,28,32,Material.GRASS_BLOCK);
        lane(world,0,210,38,42,Material.BRICKS);
        lane(world,0,210,48,52,Material.STONE_BRICKS);
        Material[] palette={Material.BRICKS,Material.COARSE_DIRT,Material.GRAVEL,Material.COBBLESTONE,Material.MOSSY_COBBLESTONE,Material.STONE_BRICKS,Material.CRACKED_STONE_BRICKS,Material.DIRT_PATH,Material.PODZOL};
        for(int x=0;x<=210;x++)for(int z=38;z<=42;z++)world.getBlockAt(x,180,z).setType(palette[(x/20)%palette.length],false);
        // Short grass gaps and fractional-height surfaces on the mixed lane.
        for(int x=48;x<=49;x++)for(int z=38;z<=42;z++)world.getBlockAt(x,180,z).setType(Material.GRASS_BLOCK,false);
        for(int x=108;x<=110;x++)for(int z=38;z<=42;z++)world.getBlockAt(x,180,z).setType(Material.GRASS_BLOCK,false);
        for(int x=180;x<=189;x++)for(int z=38;z<=42;z++)world.getBlockAt(x,180,z).setType(Material.BRICK_SLAB,false);
        for(int x=0;x<=205;x++){
            boolean powered=x<190;
            world.getBlockAt(x,180,50).setType(powered?Material.REDSTONE_BLOCK:Material.STONE,false);
            Block rail=world.getBlockAt(x,181,50);rail.setType(Material.POWERED_RAIL,false);
            var data=rail.getBlockData();((Rail)data).setShape(Rail.Shape.EAST_WEST);((Powerable)data).setPowered(powered);rail.setBlockData(data,false);
        }
        world.getBlockAt(-1,181,50).setType(Material.STONE_BRICKS,false);
        world.getBlockAt(206,181,50).setType(Material.STONE_BRICKS,false);
        for(int x:new int[]{0,50,150,190,200}){
            for(int z:new int[]{17,27,37,47}){
                world.getBlockAt(x,180,z).setType(Material.GOLD_BLOCK,false);
                label(world,x+.5,183,z+.5,x==190?"190 - rail brakes":x+" blocks");
            }
        }
        label(world,4,184,4,"Travel tests - survival to measure; 50 to 150 = 100 blocks");
        label(world,2,184,20,"Cobblestone road");label(world,2,184,30,"Grass baseline");
        label(world,2,184,40,"Mixed paths, grass gaps and brick slabs");label(world,2,184,50,"Powered rail; brakes begin at 190");
        mount(world,Horse.class,3,8,"Test horse - slower",.15);
        mount(world,Horse.class,8,8,"Test horse - faster",.30);
        mount(world,Donkey.class,13,8,"Test donkey",null);
        mount(world,Camel.class,3,3,"Test camel",null);
        mount(world,Mule.class,13,3,"Test mule",null);
        world.setSpawnLocation(8,181,3);
    }
    private static void lane(World world,int x1,int x2,int z1,int z2,Material floor){
        for(int x=x1;x<=x2;x++)for(int z=z1;z<=z2;z++){
            world.getBlockAt(x,180,z).setType(floor,false);
            for(int y=181;y<=190;y++)world.getBlockAt(x,y,z).setType(Material.AIR,false);
        }
    }
    private static void label(World world,double x,double y,double z,String text){
        TextDisplay display=world.spawn(new Location(world,x,y,z),TextDisplay.class);
        display.addScoreboardTag(TAG);display.text(Component.text(text));display.setBillboard(Display.Billboard.CENTER);
    }
    private static <T extends AbstractHorse> void mount(World world,Class<T> type,double x,double z,String name,Double speed){
        T mount=world.spawn(new Location(world,x,181,z),type);mount.addScoreboardTag(TAG);
        mount.customName(Component.text(name));mount.setCustomNameVisible(true);mount.setTamed(true);
        mount.setAdult();mount.setRemoveWhenFarAway(false);mount.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        Bukkit.getMobGoals().removeAllGoals(mount);
        if(speed!=null)mount.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).setBaseValue(speed);
    }
}
