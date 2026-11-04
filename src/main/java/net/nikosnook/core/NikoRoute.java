package net.nikosnook.core;

import java.util.List;
import java.util.Objects;

/** Tick-driven flat loop. No Bukkit state, elapsed-time catch-up or saved movement. */
final class NikoRoute {
    static final double STEP=1.2/20,EPS=0.000001,MAX_RADIUS=32,MAX_LENGTH=256;
    static final int MAX_POINTS=32,BLOCKED_LIMIT=100,RECHECK_TICKS=20;
    record Point(String world,double x,double y,double z) {
        Point {
            if(world==null || world.isBlank() || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Each waypoint needs a world and finite x/y/z coordinates");
            if(Math.abs(x)>29_999_984 || Math.abs(z)>29_999_984)
                throw new IllegalArgumentException("Waypoints must stay inside Minecraft's horizontal coordinate limits");
        }
        double distance(Point other){return Math.sqrt(squared(other));}
        double squared(Point other){return Math.pow(x-other.x,2)+Math.pow(y-other.y,2)+Math.pow(z-other.z,2);}
    }
    interface Safety { String problem(Point from,Point to); }
    interface Move { void apply(Point from,Point to); }
    private final List<Point> points;
    private Point position;
    private int target=1,pauseTicks,visits,interactionTicks,blockedTicks,recheckTicks;
    private String disabledReason;

    NikoRoute(Point home,List<Point> points){
        Objects.requireNonNull(home);this.points=List.copyOf(points);
        if(points.size()<2 || points.size()>MAX_POINTS)throw new IllegalArgumentException("Use 2–32 waypoints, with HOME first");
        if(!home.world().equals(points.getFirst().world()) || home.distance(points.getFirst())>EPS)
            throw new IllegalArgumentException("The first waypoint must exactly match Niko's saved HOME position");
        double length=0;
        for(int i=0;i<points.size();i++){
            var p=points.get(i);var next=points.get((i+1)%points.size());
            if(!home.world().equals(p.world()))throw new IllegalArgumentException("All waypoints must use HOME's world");
            if(Math.abs(home.y()-p.y())>EPS)throw new IllegalArgumentException("Only flat routes at HOME's exact foot height are supported");
            if(home.distance(p)>MAX_RADIUS+EPS)throw new IllegalArgumentException("Waypoints must stay within 32 blocks of HOME");
            double segment=p.distance(next);
            if(segment<0.01)throw new IllegalArgumentException("Adjacent waypoints must be distinct; HOME closes the loop automatically");
            length+=segment;
        }
        if(length>MAX_LENGTH+EPS)throw new IllegalArgumentException("The complete loop must be at most 256 blocks long");
        position=home;
    }
    String initialProblem(Safety safety){
        for(int i=0;i<points.size();i++){
            var problem=safety.problem(points.get(i),points.get((i+1)%points.size()));
            if(problem!=null)return "segment "+(i+1)+": "+problem;
        }
        return null;
    }
    Point position(){return position;}
    int pauseTicks(){return pauseTicks;}
    String disabledReason(){return disabledReason;}
    void interact(){interactionTicks=40;}
    void tick(boolean busy,Safety safety,Move move){
        if(disabledReason!=null)return;
        boolean interaction=interactionTicks>0;if(interaction)interactionTicks--;
        // Players and menus take priority over movement and every obstruction retry.
        if(busy || interaction)return;
        if(blockedTicks>0){
            if(++blockedTicks>=BLOCKED_LIMIT){disabledReason="road remained obstructed or unloaded for five active seconds; holding the last position";return;}
            if(recheckTicks>0){recheckTicks--;return;}
        }
        if(pauseTicks>0){pauseTicks--;return;}
        var destination=points.get(target);double distance=position.distance(destination);
        double ratio=Math.min(1,STEP/distance);
        var next=new Point(position.world(),position.x()+(destination.x()-position.x())*ratio,position.y(),position.z()+(destination.z()-position.z())*ratio);
        String problem=safety.problem(position,next);
        if(problem!=null){if(blockedTicks==0)blockedTicks=1;recheckTicks=RECHECK_TICKS-1;return;}
        // Progress is committed only after the provider accepted the move.
        move.apply(position,next);position=next;blockedTicks=0;recheckTicks=0;
        if(distance<=STEP+EPS){position=destination;target=(target+1)%points.size();pauseTicks=40+(visits++%3)*20;}
    }
}
