package net.nikosnook.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;

/** Serial atomic saves, without letting a stalled disk block the server thread. */
final class NpcConfigWriter implements AutoCloseable {
    @FunctionalInterface interface Write { void write(Path path,String text) throws IOException; }
    private final Write write;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"NookNpcSettings");t.setDaemon(true);return t;});
    NpcConfigWriter(){this(NpcConfigWriter::atomicWrite);}
    NpcConfigWriter(Write write){this.write=write;}
    CompletableFuture<Void> save(Path path,String text){
        return CompletableFuture.runAsync(()->{try{write.write(path,text);}catch(IOException ex){throw new CompletionException(ex);}},worker);
    }
    static void atomicWrite(Path path,String text)throws IOException {
        Path temp=Files.createTempFile(path.toAbsolutePath().getParent(),"npc-settings-",".tmp");
        try{
            Files.writeString(temp,text);
            // Do not fall back to truncating/replacing the last good configuration non-atomically.
            Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
    @Override public void close(){worker.shutdown();} // No main-thread wait, including when storage is stalled.
}
