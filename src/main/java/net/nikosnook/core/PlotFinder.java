package net.nikosnook.core;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.function.*;

/** Read-only directions and a short personal marker; never loads distant chunks. */
final class PlotFinder {
    record Bounds(UUID world,int minX,int minZ,int maxX,int maxZ){
        double x(){return ((double)minX+maxX+1)/2;}
        double z(){return ((double)minZ+maxZ+1)/2;}
        long width(){return (long)maxX-minX+1;}
        long depth(){return (long)maxZ-minZ+1;}
        boolean contains(double x,double z){return x>=minX && x<((double)maxX+1) && z>=minZ && z<((double)maxZ+1);}
    }
    static String direction(double dx,double dz){
        String[] names={"south","southwest","west","northwest","north","northeast","east","southeast"};
        return names[Math.floorMod((int)Math.round(Math.atan2(-dx,dz)/(Math.PI/4)),8)];
    }
    static String description(Bounds bounds,Location from){
        String coordinates="X "+(int)Math.floor(bounds.x())+", Z "+(int)Math.floor(bounds.z())+" · "+bounds.width()+" × "+bounds.depth()+" blocks";
        if(from.getWorld()==null || !bounds.world().equals(from.getWorld().getUID()))return coordinates+". Return to the shopping district's world for directions.";
        if(bounds.contains(from.getX(),from.getZ()))return coordinates+" · You are inside this plot.";
        double dx=bounds.x()-from.getX(),dz=bounds.z()-from.getZ();
        return coordinates+" · "+(long)Math.ceil(Math.hypot(dx,dz))+" blocks "+direction(dx,dz)+" to its centre (straight-line distance).";
    }
    private final JavaPlugin plugin;
    private final Function<String,Bounds> lookup;
    private final BooleanSupplier ready;
    private final Map<UUID,BukkitTask> markers=new HashMap<>();
    PlotFinder(JavaPlugin plugin,Function<String,Bounds> lookup,BooleanSupplier ready){this.plugin=plugin;this.lookup=lookup;this.ready=ready;}
    void show(Player player,String plot){
        Bounds bounds=lookup.apply(plot);
        player.sendMessage(NookUi.message("NookPlots",plot+" · "+description(bounds,player.getLocation())));
        BukkitTask previous=markers.remove(player.getUniqueId());if(previous!=null)previous.cancel();
        if(!bounds.world().equals(player.getWorld().getUID()))return;
        if(bounds.contains(player.getLocation().getX(),player.getLocation().getZ()))return;
        player.sendMessage(NookUi.message("NookPlots","A gold marker will appear over the plot for 10 seconds if it is loaded and within 128 blocks. Run ").append(NookUi.command("/plots find "+plot)).append(NookUi.text(" again as you get closer.")));
        BukkitRunnable pulse=new BukkitRunnable(){
            int remaining=10;
            public void run(){
                if(remaining--<=0 || !player.isOnline() || !ready.getAsBoolean() || !bounds.world().equals(player.getWorld().getUID())){finish();return;}
                var world=player.getWorld();int x=(int)Math.floor(bounds.x()),z=(int)Math.floor(bounds.z());
                if(Math.hypot(player.getLocation().getX()-bounds.x(),player.getLocation().getZ()-bounds.z())>128 || !world.isChunkLoaded(x>>4,z>>4))return;
                try{
                    double y=world.getHighestBlockYAt(x,z)+1.5;
                    var dust=new Particle.DustOptions(Color.fromRGB(229,185,92),1.5f);
                    for(int i=0;i<20;i++)player.spawnParticle(Particle.DUST,bounds.x(),y+i*.6,bounds.z(),2,.12,.1,.12,0,dust);
                }catch(RuntimeException e){plugin.getLogger().warning("Plot locator marker failed: "+e.getClass().getSimpleName());finish();}
            }
            private void finish(){cancel();markers.remove(player.getUniqueId());}
        };
        markers.put(player.getUniqueId(),pulse.runTaskTimer(plugin,0,20));
    }
}
