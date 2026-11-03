package net.nikosnook.core;

import java.util.*;

/** A private hello on a real visit, never on previews or quantity-menu returns. */
final class PipGreetings {
    private static final long COOLDOWN=5*60*1000L;
    private record Visit(long time,int next) {}
    private final Map<UUID,Visit> visits=new HashMap<>();
    Optional<String> greet(UUID player,String name,long now){
        var previous=visits.get(player);
        if(previous!=null && now<previous.time()+COOLDOWN)return Optional.empty();
        int line=previous==null?0:previous.next();
        visits.put(player,new Visit(now,(line+1)%3));
        return Optional.of(switch(line){
            case 0->"hi there, "+name+"! have a little look around <3";
            case 1->"lovely to see you, "+name+"! find anything you fancy? :3";
            default->"oh, hi "+name+"! i've brought a few lovely things with me!";
        });
    }
    void forget(UUID player){visits.remove(player);}
}
