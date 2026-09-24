package net.nikosnook.core;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

class ExactWorldSpawnTest {
    World world(){return (World)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{World.class},(o,m,a)->switch(m.getName()){
        case "getEnvironment"->World.Environment.NORMAL;case "getSpawnLocation"->new Location((World)o,-335,68,-307);case "getName"->"world";default->null;});}
    Player player(boolean returning){return (Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(o,m,a)->m.getName().equals("hasPlayedBefore")?returning:null);}
    @Test void firstJoinUsesSafeInteriorButReturningLoginStaysPut(){
        var listener=new ExactWorldSpawn(p->true,s->{});var world=world();
        var first=new PlayerSpawnLocationEvent(player(false),new Location(world,0,90,0));listener.firstJoin(first);
        assertEquals(68,first.getSpawnLocation().getY());assertEquals(-334.5,first.getSpawnLocation().getX());
        var returning=new PlayerSpawnLocationEvent(player(true),new Location(world,0,90,0));listener.firstJoin(returning);
        assertEquals(90,returning.getSpawnLocation().getY());
    }
    @Test void worldDeathRespawnUsesInteriorButBedAnchorAndEndExitStayPut(){
        var listener=new ExactWorldSpawn(p->true,s->{});var world=world();var player=player(true);
        for(int kind=0;kind<4;kind++){
            var event=new PlayerRespawnEvent(player,new Location(world,0,90,0),kind==1,kind==2,false,kind==3?PlayerRespawnEvent.RespawnReason.END_PORTAL:PlayerRespawnEvent.RespawnReason.DEATH);
            listener.respawn(event);assertEquals(kind==0?68:90,event.getRespawnLocation().getY());
        }
    }
    @Test void unsafeInteriorKeepsServerSpawn(){
        var event=new PlayerSpawnLocationEvent(player(false),new Location(world(),0,90,0));
        new ExactWorldSpawn(p->false,s->{}).firstJoin(event);assertEquals(90,event.getSpawnLocation().getY());
    }
}
