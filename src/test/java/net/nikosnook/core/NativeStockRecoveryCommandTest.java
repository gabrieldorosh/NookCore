package net.nikosnook.core;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NativeStockRecoveryCommandTest {
    @TempDir Path dir;NookStore store;UUID owner=UUID.randomUUID(),receipt=UUID.randomUUID();NativeShop.Offer offer;
    AtomicLong now=new AtomicLong(100);AtomicBoolean ready=new AtomicBoolean(true);List<Exception> errors=new ArrayList<>();
    CommandSender sender(String name,boolean admin){return (CommandSender)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{CommandSender.class},(o,m,a)->switch(m.getName()){case "getName"->name;case "hasPermission"->admin;case "hashCode"->System.identityHashCode(o);case "equals"->o==a[0];default->null;});}
    NativeStockRecovery command;CommandSender console;
    @BeforeEach void setup()throws Exception {
        store=new NookStore(dir.resolve("db"));store.join(owner,"Moon",0,10000);store.definePlot("one",3000);store.rent("one",owner,0);
        offer=store.publishNativeOffer("world:1:2:3","one",owner,new byte[]{7},1,250,1);byte[] after=new byte[32];after[0]=1;
        store.planNativeStockDeposit(receipt,offer.id(),owner,1,new byte[32],after,2);
        command=new NativeStockRecovery(store,dir.resolve("backups"),ready::get,errors::add,now::get);console=sender("CONSOLE",true);
    }
    @AfterEach void close()throws Exception{store.close();}
    void preview(){command.command(console,new String[]{"recover-stock",receipt.toString(),"credit","Owner confirmed missing and unreplaced"});}
    void confirm(CommandSender by){command.command(by,new String[]{"recover-stock",receipt.toString(),"confirm"});}
    @Test void previewDoesNotMutateAndConfirmBacksUpBeforeCreditingOnce()throws Exception {
        preview();assertEquals(0,store.nativeOffer(offer.id()).stock());assertEquals("REVIEW",store.nativeStockIntentState(receipt));assertFalse(Files.exists(dir.resolve("backups")));
        confirm(console);assertEquals(1,store.nativeOffer(offer.id()).stock());assertEquals("COMPLETE",store.nativeStockIntentState(receipt));
        Path backup;try(var files=Files.list(dir.resolve("backups"))){backup=files.findFirst().orElseThrow();}
        try(var prior=new NookStore(backup)){assertEquals(0,prior.nativeOffer(offer.id()).stock());assertEquals("REVIEW",prior.nativeStockIntentState(receipt));}
        confirm(console);preview();confirm(console);assertEquals(1,store.nativeOffer(offer.id()).stock());assertTrue(errors.isEmpty());
    }
    @Test void adminActorAndExpiryRequired()throws Exception {
        command.command(sender("Guest",false),new String[]{"recover-stock",receipt.toString(),"credit","test"});confirm(console);assertEquals(0,store.nativeOffer(offer.id()).stock());
        preview();confirm(sender("OTHER",true));assertEquals(0,store.nativeOffer(offer.id()).stock());
        now.addAndGet(60000);confirm(console);assertEquals("REVIEW",store.nativeStockIntentState(receipt));assertFalse(Files.exists(dir.resolve("backups")));assertTrue(errors.isEmpty());
    }
    @Test void pausedAndFailedBackupCannotApply()throws Exception {
        preview();ready.set(false);confirm(console);assertEquals(0,store.nativeOffer(offer.id()).stock());ready.set(true);
        Files.writeString(dir.resolve("backups"),"not a directory");confirm(console);assertEquals("REVIEW",store.nativeStockIntentState(receipt));assertEquals(0,store.nativeOffer(offer.id()).stock());
        assertTrue(errors.isEmpty());
    }
    @Test void noCreditRequiresExplicitActionAndDoesNotInventItems()throws Exception {
        command.command(console,new String[]{"recover-stock",receipt.toString(),"no-credit","Player confirmed retaining the book"});confirm(console);
        assertEquals(0,store.nativeOffer(offer.id()).stock());assertNull(store.nativeStockReview(receipt).creditedQuantity());assertFalse(store.nativeNeedsReview(owner));
        assertEquals(NativeStockReview.Recovery.NO_CREDIT,store.nativeStockReview(receipt).resolution().recovery());assertTrue(errors.isEmpty());
    }
}
