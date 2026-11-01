package net.nikosnook.core;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Durable opt-in preferences; combat locks survive disconnects within this server run. */
final class PvpSettings {
    private final Path file;private final LongSupplier clock;private final long lockMillis;
    private Set<UUID> enabled=new HashSet<>();private final Map<UUID,Long> lockedUntil=new HashMap<>();
    PvpSettings(Path file,LongSupplier clock,long lockMillis)throws IOException{
        if(lockMillis<0 || lockMillis>300_000)throw new IllegalArgumentException("Invalid PvP combat lock");
        this.file=file;this.clock=clock;this.lockMillis=lockMillis;
        Properties p=new Properties();if(Files.exists(file))try(var input=Files.newInputStream(file)){p.load(input);}
        try{for(String key:p.stringPropertyNames()){
            UUID id=UUID.fromString(key);String value=p.getProperty(key);
            if(!Set.of("true","false").contains(value))throw new IllegalArgumentException("Invalid PvP preference");
            if(value.equals("true"))enabled.add(id);
        }}catch(RuntimeException e){throw new IOException("Invalid PvP preferences; original file kept",e);}
    }
    boolean enabled(UUID player){return enabled.contains(player);}
    boolean allows(UUID attacker,UUID target){return attacker.equals(target) || enabled(attacker)&&enabled(target);}
    long secondsRemaining(UUID player){return Math.max(0,(lockedUntil.getOrDefault(player,0L)-clock.getAsLong()+999)/1000);}
    void combat(UUID attacker,UUID target){
        if(attacker.equals(target) || !allows(attacker,target))return;
        long until=clock.getAsLong()+lockMillis;lockedUntil.merge(attacker,until,Math::max);lockedUntil.merge(target,until,Math::max);
    }
    void set(UUID player,boolean value)throws IOException{
        if(enabled(player)==value)return;
        long seconds=secondsRemaining(player);if(seconds>0)throw new IllegalArgumentException("Wait "+seconds+" seconds after combat before changing PvP.");
        Set<UUID> next=new HashSet<>(enabled);if(value)next.add(player);else next.remove(player);
        Path parent=file.toAbsolutePath().getParent();Files.createDirectories(parent);Path temp=Files.createTempFile(parent,"pvp-",".tmp");
        try{
            Properties p=new Properties();for(UUID id:next)p.setProperty(id.toString(),"true");
            try(var output=Files.newOutputStream(temp)){p.store(output,"NookCore PvP opt-in preferences");}
            try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE)){channel.force(true);}
            Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);enabled=next;
        }finally{Files.deleteIfExists(temp);}
    }
}
