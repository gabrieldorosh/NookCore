package net.nikosnook.core;

import de.oliver.fancynpcs.api.Npc;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;

/** Optional main-thread packet movement, isolated from quest/economy availability. */
final class NikoRoaming<T> {
    interface Gateway<T> { void move(T npc,NikoRoute.Point from,NikoRoute.Point to); }
    private static final double HALF_WIDTH=0.3,HEIGHT=1.8,SCAN_STEP=0.125,EPS=NikoRoute.EPS;
    // Full-width road surfaces from GroundedTravel. Partial blocks/stairs are deliberately excluded.
    static final Set<Material> ROADS=Set.of(Material.DIRT_PATH,Material.COARSE_DIRT,Material.PODZOL,Material.MUD,
        Material.GRAVEL,Material.BRICKS,Material.COBBLESTONE,Material.MOSSY_COBBLESTONE,Material.STONE_BRICKS,Material.CRACKED_STONE_BRICKS);
    private static final Set<Material> HAZARDS=Set.of(Material.FIRE,Material.SOUL_FIRE,Material.COBWEB,Material.POWDER_SNOW,Material.SWEET_BERRY_BUSH,Material.WITHER_ROSE);
    private final boolean enabled;
    private final List<NikoRoute.Point> points;
    private final NikoRoute.Safety safety;
    private final Gateway<T> gateway;
    private final Consumer<String> warning;
    private T current;
    private NikoRoute route;
    private boolean disabled;

    NikoRoaming(boolean enabled,List<NikoRoute.Point> points,NikoRoute.Safety safety,Gateway<T> gateway,Consumer<String> warning){
        this.enabled=enabled;this.points=List.copyOf(points);this.safety=safety;this.gateway=gateway;this.warning=warning;
    }
    static NikoRoaming<Npc> fancy(YamlConfiguration config,Consumer<String> warning){
        boolean enabled=config.getBoolean("niko-roaming.enabled",false);
        List<NikoRoute.Point> points=List.of();String bad=null;
        if(enabled)try{points=readPoints(config.getList("niko-roaming.waypoints"));}
        catch(RuntimeException ex){bad=ex.getMessage();}
        var roaming=new NikoRoaming<Npc>(enabled,points,NikoRoaming::safeSegment,(npc,from,to)->{
            var old=npc.getData().getLocation().clone();
            if(old.getWorld()==null || !old.getWorld().getName().equals(from.world()) || point(old).distance(from)>EPS)
                throw new IllegalStateException("Niko's position was changed outside the route; holding his current position");
            float yaw=(float)Math.toDegrees(Math.atan2(-(to.x()-from.x()),to.z()-from.z()));
            var next=new Location(old.getWorld(),to.x(),to.y(),to.z(),yaw,0);
            npc.getData().setLocation(next).setCollidable(false);
            try{npc.moveForAll(false);}
            catch(RuntimeException|LinkageError ex){npc.getData().setLocation(old);throw ex;}
        },warning);
        if(bad!=null)roaming.disable(bad);
        return roaming;
    }
    static List<NikoRoute.Point> readPoints(List<?> raw){
        if(raw==null || raw.size()<2 || raw.size()>NikoRoute.MAX_POINTS)throw new IllegalArgumentException("Use 2–32 niko-roaming.waypoints, with HOME first");
        var result=new ArrayList<NikoRoute.Point>();
        for(var entry:raw){
            if(!(entry instanceof Map<?,?> p) || !(p.get("world") instanceof String world) || !(p.get("x") instanceof Number x)
                || !(p.get("y") instanceof Number y) || !(p.get("z") instanceof Number z))
                throw new IllegalArgumentException("Each waypoint needs world, x, y and z");
            if(!Set.of("world","x","y","z").equals(p.keySet()))throw new IllegalArgumentException("Waypoints only support world, x, y and z");
            result.add(new NikoRoute.Point(world,x.doubleValue(),y.doubleValue(),z.doubleValue()));
        }
        return List.copyOf(result);
    }
    void update(T npc,NikoRoute.Point home,boolean busy){
        if(!enabled || disabled)return;
        if(npc==null || home==null){forget();return;}
        try{
            if(current!=npc){
                current=npc;route=new NikoRoute(home,points);
                String problem=route.initialProblem(safety);if(problem!=null)disable(problem);
                return;
            }
            route.tick(busy,safety,(from,to)->gateway.move(npc,from,to));
            if(route.disabledReason()!=null)disable(route.disabledReason());
        }catch(RuntimeException|LinkageError ex){disable(ex.getClass().getSimpleName()+": "+ex.getMessage());}
    }
    void interact(){if(route!=null && !disabled)route.interact();}
    boolean running(){return enabled && !disabled;}
    void failed(Throwable error){disable(error.getClass().getSimpleName()+": "+error.getMessage());}
    void forget(){current=null;route=null;}
    String status(){return !enabled?"off":disabled?"disabled; holding position":route==null?"waiting for Niko":"flat loop";}
    private void disable(String reason){
        if(disabled)return;disabled=true;
        try{warning.accept("Niko's optional roaming is disabled: "+reason+". His quests remain available; check the route and restart to retry.");}
        catch(RuntimeException ignored){/* Diagnostics cannot affect Niko's quests. */}
    }
    static NikoRoute.Point point(Location location){
        return new NikoRoute.Point(location.getWorld().getName(),location.getX(),location.getY(),location.getZ());
    }
    static boolean pauses(Player player,Location at){
        return player.isOnline() && !player.isDead() && player.getGameMode()!=GameMode.SPECTATOR
            && !StaffMode.active(player.getUniqueId()) && player.isVisibleByDefault()
            && (!player.hasMetadata("vanished") || player.getMetadata("vanished").stream().noneMatch(v->v.asBoolean()))
            && player.getWorld().equals(at.getWorld()) && player.getLocation().distanceSquared(at)<=36;
    }
    static String safeSegment(NikoRoute.Point from,NikoRoute.Point to){
        World world=Bukkit.getWorld(from.world());
        if(world==null || !from.world().equals(to.world()))return "world is unavailable";
        return safeSegment(world,from,to);
    }
    static String safeSegment(World world,NikoRoute.Point from,NikoRoute.Point to){
        if(Math.abs(from.y()-to.y())>EPS)return "height changes are unsupported";
        int count=Math.max(1,(int)Math.ceil(from.distance(to)/SCAN_STEP));
        // Each short section checks its swept rectangle, including every intersected ground column.
        // This is conservative at diagonal corners and covers the spaces between validation samples.
        for(int i=0;i<count;i++){
            double a=(double)i/count,b=(double)(i+1)/count;
            String problem=safeSweep(world,from.x()+(to.x()-from.x())*a,from.z()+(to.z()-from.z())*a,
                from.x()+(to.x()-from.x())*b,from.z()+(to.z()-from.z())*b,from.y());
            if(problem!=null)return problem;
        }
        return null;
    }
    private static String safeSweep(World world,double ax,double az,double bx,double bz,double y){
        double minX=Math.min(ax,bx)-HALF_WIDTH,maxX=Math.max(ax,bx)+HALF_WIDTH;
        double minZ=Math.min(az,bz)-HALF_WIDTH,maxZ=Math.max(az,bz)+HALF_WIDTH;
        int groundY=(int)Math.floor(y-EPS),bodyMinY=(int)Math.floor(y+EPS),bodyMaxY=(int)Math.floor(y+HEIGHT-EPS);
        if(groundY<world.getMinHeight() || bodyMaxY>=world.getMaxHeight())return "route is outside the world's height limits";
        int x0=(int)Math.floor(minX+EPS),x1=(int)Math.floor(maxX-EPS),z0=(int)Math.floor(minZ+EPS),z1=(int)Math.floor(maxZ-EPS);
        // Check all chunks before fetching any block: getBlockAt must never load an unloaded chunk.
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(!world.isChunkLoaded(x>>4,z>>4))return "route touches an unloaded chunk";
        var body=new BoundingBox(minX+EPS,y+EPS,minZ+EPS,maxX-EPS,y+HEIGHT-EPS,maxZ-EPS);
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
            var ground=world.getBlockAt(x,groundY,z);
            if(!ROADS.contains(ground.getType()))return "Niko's full footprint must stay on an allowed flat road";
            boolean supported=false;
            for(var box:ground.getCollisionShape().getBoundingBoxes()){
                if(box.getMinX()<=EPS && box.getMaxX()>=1-EPS && box.getMinZ()<=EPS && box.getMaxZ()>=1-EPS
                    && Math.abs(groundY+box.getMaxY()-y)<=EPS){supported=true;break;}
            }
            if(!supported)return "road top must match Niko's exact flat foot height; slabs and stairs are unsupported";
            for(int blockY=bodyMinY;blockY<=bodyMaxY;blockY++){
                var block=world.getBlockAt(x,blockY,z);
                if(block.isLiquid() || HAZARDS.contains(block.getType()))return "route contains a liquid or unsafe block";
                for(var box:block.getCollisionShape().getBoundingBoxes())
                    if(box.clone().shift(x,blockY,z).overlaps(body))return "Niko's body or head is obstructed";
            }
        }
        return null;
    }
}
