package net.nikosnook.core;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Persist the return point before changing mode or visibility. */
final class StaffState {
    record Return(UUID world,double x,double y,double z,float yaw,float pitch,String mode,boolean flight,boolean flying,boolean visible,String presence){
        Return(UUID world,double x,double y,double z,float yaw,float pitch,String mode,boolean flight,boolean flying,boolean visible){this(world,x,y,z,yaw,pitch,mode,flight,flying,visible,"disconnect");}
        Return {Objects.requireNonNull(world);if(!Set.of("disconnect","legacy").contains(presence))throw new IllegalArgumentException("Invalid staff presence");if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw)||!Float.isFinite(pitch)||!Set.of("SURVIVAL","CREATIVE","ADVENTURE","SPECTATOR").contains(mode))throw new IllegalArgumentException("Invalid staff return point");}
        String encode(){return world+","+x+","+y+","+z+","+yaw+","+pitch+","+mode+","+flight+","+flying+","+visible+","+presence;}
        static Return decode(String value){String[] p=value.split(",",-1);if(p.length!=10 && p.length!=11)throw new IllegalArgumentException("Invalid staff state");for(int i=7;i<10;i++)if(!Set.of("true","false").contains(p[i]))throw new IllegalArgumentException("Invalid staff flag");return new Return(UUID.fromString(p[0]),Double.parseDouble(p[1]),Double.parseDouble(p[2]),Double.parseDouble(p[3]),Float.parseFloat(p[4]),Float.parseFloat(p[5]),p[6],Boolean.parseBoolean(p[7]),Boolean.parseBoolean(p[8]),Boolean.parseBoolean(p[9]),p.length==11?p[10]:"legacy");}
    }
    private final Path file;private Map<UUID,Return> states=new HashMap<>();
    StaffState(Path file)throws IOException{this.file=file;Properties p=new Properties();if(Files.exists(file))try(var in=Files.newInputStream(file)){p.load(in);}try{for(String key:p.stringPropertyNames())states.put(UUID.fromString(key),Return.decode(p.getProperty(key)));}catch(RuntimeException e){throw new IOException("Invalid staff state; file preserved for repair",e);}}
    Return get(UUID id){return states.get(id);}
    Set<UUID> ids(){return Set.copyOf(states.keySet());}
    void begin(UUID id,Return point)throws IOException{if(states.containsKey(id))throw new IllegalArgumentException("Staff mode is already active; return point kept.");var next=new HashMap<>(states);next.put(id,point);save(next);}
    void finish(UUID id)throws IOException{var next=new HashMap<>(states);next.remove(id);save(next);}
    private void save(Map<UUID,Return> next)throws IOException{
        Path parent=file.toAbsolutePath().getParent();Files.createDirectories(parent);Path temp=Files.createTempFile(parent,"staff-",".tmp");
        try{Properties p=new Properties();next.forEach((id,point)->p.setProperty(id.toString(),point.encode()));try(var out=Files.newOutputStream(temp)){p.store(out,"NookCore staff return points");}try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE)){channel.force(true);}Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);states=next;}finally{Files.deleteIfExists(temp);}
    }
}
