package net.nikosnook.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class NpcConfigWriterTest {
    @TempDir Path dir;
    @Test void stalledStorageDoesNotBlockCallerOrShutdown()throws Exception {
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);
        var writer=new NpcConfigWriter((p,s)->{started.countDown();try{release.await();}catch(InterruptedException e){throw new IOException(e);}});
        try{
            var save=writer.save(dir.resolve("settings.yml"),"snapshot");
            assertTrue(started.await(2,TimeUnit.SECONDS));assertFalse(save.isDone());
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),writer::close);
            assertFalse(save.isDone());release.countDown();save.get(2,TimeUnit.SECONDS);
        }finally{release.countDown();writer.close();}
    }
    @Test void savesStayOrderedAndReplaceCompleteFiles()throws Exception {
        Path file=dir.resolve("settings.yml");Files.writeString(file,"old");
        try(var writer=new NpcConfigWriter()){
            var first=writer.save(file,"niko: first");var second=writer.save(file,"niko: first\npip: second\n");
            second.get(3,TimeUnit.SECONDS);assertTrue(first.isDone());assertEquals("niko: first\npip: second\n",Files.readString(file));
            try(var files=Files.list(dir)){assertEquals(1,files.count());}
        }
    }
    @Test void failedSaveReportsFailureAndRetainsPreviousFile()throws Exception {
        Path file=dir.resolve("settings.yml");Files.writeString(file,"previous");
        try(var writer=new NpcConfigWriter((p,s)->{throw new IOException("storage unavailable");})){
            var ex=assertThrows(ExecutionException.class,()->writer.save(file,"new").get(2,TimeUnit.SECONDS));
            assertInstanceOf(IOException.class,ex.getCause());assertEquals("previous",Files.readString(file));
        }
    }
}
