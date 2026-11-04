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
        for(int by=y;by>=Math.max(world.getMinHeight()+1,y-1);by--){
            var block=world.getBlockAt(x,by,z);if(!(block.getBlockData() instanceof Rail data))continue;
            double dx=at.getX()-x,dz=at.getZ()-z,height=.0625;
            double ax=.5,az=0,bx=.5,bz=1;
            switch(data.getShape()){
                case EAST_WEST -> {ax=0;az=.5;bx=1;bz=.5;}
                case ASCENDING_EAST -> {ax=0;az=.5;bx=1;bz=.5;height+=dx;}
                case ASCENDING_WEST -> {ax=0;az=.5;bx=1;bz=.5;height+=1-dx;}
                case ASCENDING_NORTH -> height+=1-dz;
                case ASCENDING_SOUTH -> height+=dz;
                case NORTH_EAST -> {ax=.5;az=0;bx=1;bz=.5;}
                case NORTH_WEST -> {ax=0;az=.5;bx=.5;bz=0;}
                case SOUTH_EAST -> {ax=.5;az=1;bx=1;bz=.5;}
                case SOUTH_WEST -> {ax=0;az=.5;bx=.5;bz=1;}
                default -> {}
            }
            double vx=bx-ax,vz=bz-az,t=Math.max(0,Math.min(1,((dx-ax)*vx+(dz-az)*vz)/(vx*vx+vz*vz)));
            if(Math.hypot(dx-ax-t*vx,dz-az-t*vz)>.20 || Math.abs(at.getY()-by-height)>.20)continue;
            // A retained rail block with its support removed is not supported travel.
            for(var box:world.getBlockAt(x,by-1,z).getCollisionShape().getBoundingBoxes())
                if(Math.abs(box.getMaxY()-1)<1e-6 && box.getMinX()<=.5 && box.getMaxX()>=.5 && box.getMinZ()<=.5 && box.getMaxZ()>=.5)return true;
        }
        return false;
    }
    static boolean roadAt(Location at,double halfWidth,Predicate<Material> roads){
        if(!bounded(at) || !Double.isFinite(halfWidth) || halfWidth<.1 || halfWidth>2)return false;
        World world=at.getWorld();double y=at.getY(),minX=at.getX()-halfWidth,maxX=at.getX()+halfWidth,minZ=at.getZ()-halfWidth,maxZ=at.getZ()+halfWidth;
        int x0=(int)Math.floor(minX+1e-6),x1=(int)Math.floor(maxX-1e-6),z0=(int)Math.floor(minZ+1e-6),z1=(int)Math.floor(maxZ-1e-6);
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(!world.isChunkLoaded(x>>4,z>>4))return false;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
            var tops=new java.util.ArrayList<org.bukkit.util.BoundingBox>();
            for(int by=Math.max(world.getMinHeight(),(int)Math.floor(y-.06));by<=Math.min(world.getMaxHeight()-1,(int)Math.floor(y));by++){
                var block=world.getBlockAt(x,by,z);if(!roads.test(block.getType()))continue;
                for(var box:block.getCollisionShape().getBoundingBoxes())if(Math.abs(by+box.getMaxY()-y)<=.06)tops.add(box.clone().shift(x,by,z));
            }
            double left=Math.max(minX,x),right=Math.min(maxX,x+1),near=Math.max(minZ,z),far=Math.min(maxZ,z+1);
            if(!covered(tops,left,right,near,far))return false;
        }
        return true;
    }
    /** Exact union of collision tops over the footprint rectangle, including stair sub-shapes. */
    private static boolean covered(java.util.List<org.bukkit.util.BoundingBox> tops,double left,double right,double near,double far){
        var xs=new java.util.TreeSet<Double>();var zs=new java.util.TreeSet<Double>();xs.add(left);xs.add(right);zs.add(near);zs.add(far);
        for(var box:tops){xs.add(Math.max(left,Math.min(right,box.getMinX())));xs.add(Math.max(left,Math.min(right,box.getMaxX())));
            zs.add(Math.max(near,Math.min(far,box.getMinZ())));zs.add(Math.max(near,Math.min(far,box.getMaxZ())));}
        var xv=xs.toArray(Double[]::new);var zv=zs.toArray(Double[]::new);
        for(int i=1;i<xv.length;i++)for(int j=1;j<zv.length;j++){
            if(xv[i]-xv[i-1]<=1e-6 || zv[j]-zv[j-1]<=1e-6)continue;
            double x=(xv[i]+xv[i-1])/2,z=(zv[j]+zv[j-1])/2;boolean hit=false;
            for(var box:tops)if(x>=box.getMinX() && x<=box.getMaxX() && z>=box.getMinZ() && z<=box.getMaxZ()){hit=true;break;}
            if(!hit)return false;
        }
        return true;
    }

}
