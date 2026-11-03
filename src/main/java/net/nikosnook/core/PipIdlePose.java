package net.nikosnook.core;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcAttribute;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.entity.EntityType;

/** Optional cosmetic pose only. Call on the main thread with a monotonic clock. */
final class PipIdlePose<T> {
    static final long IDLE_NANOS=10_000_000_000L;
    interface Gateway<T> {
        boolean available();
        void pose(T npc,boolean sitting);
    }
    private final Gateway<T> gateway;
    private final Consumer<String> warning;
    private T current;
    private boolean sitting,idleStarted,checked,disabled;
    private long idleSince;

    PipIdlePose(Gateway<T> gateway,Consumer<String> warning){
        this.gateway=Objects.requireNonNull(gateway);this.warning=Objects.requireNonNull(warning);
    }
    static PipIdlePose<Npc> fancy(Consumer<String> warning){
        return new PipIdlePose<>(new Gateway<>(){
            private NpcAttribute pose;
            public boolean available(){
                var manager=FancyNpcsPlugin.get().getAttributeManager();
                pose=manager==null?null:manager.getAttributeByName(EntityType.PLAYER,"pose");
                return pose!=null && pose.isValidValue("sitting") && pose.isValidValue("standing");
            }
            public void pose(Npc npc,boolean sitting){
                npc.getData().addAttribute(pose,sitting?"sitting":"standing");
                npc.updateForAll(false);
            }
        },warning);
    }
    void update(T npc,boolean nearby,long nowNanos){
        if(disabled)return;
        if(npc==null){reset(null);return;}
        if(current!=npc)reset(npc);
        if(!ready())return;
        if(nearby){idleStarted=false;setPose(false);return;}
        if(!idleStarted){idleStarted=true;idleSince=nowNanos;return;}
        long elapsed=nowNanos-idleSince;
        if(elapsed<0){idleSince=nowNanos;return;}
        if(elapsed>=IDLE_NANOS)setPose(true);
    }
    void standOnClick(T npc,long nowNanos){
        if(disabled || npc==null)return;
        if(current!=npc)reset(npc);
        if(!ready())return;
        idleStarted=true;idleSince=nowNanos;setPose(false);
    }
    private void reset(T npc){current=npc;sitting=false;idleStarted=false;}
    private boolean ready(){
        if(checked)return !disabled;
        checked=true;
        try{if(!gateway.available())disable("the sitting/standing pose provider is missing");}
        catch(RuntimeException|LinkageError error){disable(error.getClass().getSimpleName());}
        return !disabled;
    }
    private void setPose(boolean value){
        if(sitting==value)return;
        try{gateway.pose(current,value);sitting=value;}
        catch(RuntimeException|LinkageError error){disable(error.getClass().getSimpleName());}
    }
    private void disable(String reason){
        if(disabled)return;
        disabled=true;
        try{warning.accept("Pip's optional idle animation is disabled: "+reason+". Her shop remains available.");}
        catch(RuntimeException ignored){/* A cosmetic warning must not affect shopping. */}
    }
}
