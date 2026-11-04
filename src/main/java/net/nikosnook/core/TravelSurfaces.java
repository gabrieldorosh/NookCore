package net.nikosnook.core;

import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.Rail;

/** Conservative occupied-vehicle measurement only; never fetch a block in an unloaded chunk. */
final class TravelSurfaces {
    private TravelSurfaces(){}
    static boolean bounded(Location at){return at!=null && at.getWorld()!=null && Double.isFinite(at.getX()) && Double.isFinite(at.getY()) && Double.isFinite(at.getZ())
        && Math.abs(at.getX())<=29_999_984 && Math.abs(at.getZ())<=29_999_984 && at.getY()>=at.getWorld().getMinHeight() && at.getY()<at.getWorld().getMaxHeight();}
    static boolean segment(Location from,Location to,double halfWidth,boolean rail,Predicate<Material> roads){
        if(!bounded(from) || !bounded(to) || !from.getWorld().equals(to.getWorld()) || Math.abs(from.getY()-to.getY())>2)return false;
        double horizontal=Math.hypot(to.getX()-from.getX(),to.getZ()-from.getZ());
        if(horizontal>25 || halfWidth<.1 || halfWidth>2 || !Double.isFinite(halfWidth))return false;
        int samples=Math.max(1,(int)Math.ceil(Math.max(horizontal,Math.abs(to.getY()-from.getY()))/.25));
        for(int i=0;i<=samples;i++){
            double t=(double)i/samples;
            var at=new Location(to.getWorld(),from.getX()+(to.getX()-from.getX())*t,from.getY()+(to.getY()-from.getY())*t,from.getZ()+(to.getZ()-from.getZ())*t);
            if(rail?!railAt(at):!roadAt(at,halfWidth,roads))return false;
        }
        return true;
    }
    static boolean railAt(Location at){
        if(!bounded(at))return false;World world=at.getWorld();int x=at.getBlockX(),z=at.getBlockZ(),y=at.getBlockY();
        if(!world.isChunkLoaded(x>>4,z>>4))return false;
        for(int by=y;by>=Math.max(world.getMinHeight(),y-1);by--)if(world.getBlockAt(x,by,z).getBlockData() instanceof Rail)return true;
        return false;
    }
    static boolean roadAt(Location at,double halfWidth,Predicate<Material> roads){
        if(!bounded(at) || !Double.isFinite(halfWidth) || halfWidth<.1 || halfWidth>2)return false;
        World world=at.getWorld();double y=at.getY(),minX=at.getX()-halfWidth,maxX=at.getX()+halfWidth,minZ=at.getZ()-halfWidth,maxZ=at.getZ()+halfWidth;
        int x0=(int)Math.floor(minX+1e-6),x1=(int)Math.floor(maxX-1e-6),z0=(int)Math.floor(minZ+1e-6),z1=(int)Math.floor(maxZ-1e-6);
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(!world.isChunkLoaded(x>>4,z>>4))return false;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
            boolean supported=false;
            for(int by=Math.max(world.getMinHeight(),(int)Math.floor(y-.06));by<=Math.min(world.getMaxHeight()-1,(int)Math.floor(y));by++){
                var block=world.getBlockAt(x,by,z);if(!roads.test(block.getType()))continue;
                for(var box:block.getCollisionShape().getBoundingBoxes())if(Math.abs(by+box.getMaxY()-y)<=.06
                    && x+box.getMaxX()>minX+1e-6 && x+box.getMinX()<maxX-1e-6 && z+box.getMaxZ()>minZ+1e-6 && z+box.getMinZ()<maxZ-1e-6){supported=true;break;}
            }
            if(!supported)return false;
        }
        return true;
    }
}
