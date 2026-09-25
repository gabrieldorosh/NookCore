package net.nikosnook.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.entity.Player;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class AbandonmentCommandsTest {
    @TempDir Path dir;
    NookStore store;UUID owner=UUID.randomUUID();Player player;
    AtomicLong time=new AtomicLong(1_800_000_000_000L);
    AtomicInteger reconciles=new AtomicInteger(),failures=new AtomicInteger();
    AtomicBoolean valid=new AtomicBoolean(true),reconcileWorks=new AtomicBoolean(true);
    PlotCommands commands;
    @BeforeEach void setup()throws Exception {
        store=new NookStore(dir.resolve("nooks.db"));store.join(owner,"Owner",time.get(),20000);store.definePlot("one",3000);store.rent("one",owner,time.get());store.prepay("one",owner,1,time.get());
        player=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){case "getUniqueId"->owner;case "getName"->"Owner";default->null;});
        commands=new PlotCommands(store,()->true,()->{if(!reconcileWorks.get())throw new IllegalStateException("save failed");reconciles.incrementAndGet();},e->failures.incrementAndGet(),id->id.equals("one"),()->{if(!valid.get())throw new IllegalStateException("invalid mapping");},time::get);
    }
    @AfterEach void close()throws Exception {store.close();}
    void command(String... args){commands.onCommand(player,null,"plots",args);}
    @Test void previewDoesNotMutateAndConfirmationRefundsExactlyOnce()throws Exception {
        long before=store.account(owner).orElseThrow().cents();command("abandon","one");
        assertEquals("ACTIVE",store.plot("one").state());assertEquals(before,store.account(owner).orElseThrow().cents());
        command("abandon","one","confirm");assertEquals(before+3000,store.account(owner).orElseThrow().cents());assertEquals(1,reconciles.get());
        command("abandon","one","confirm");assertEquals(before+3000,store.account(owner).orElseThrow().cents());assertEquals(0,failures.get());
    }
    @Test void confirmationWithoutPreviewAndExpiredPreviewDoNothing()throws Exception {
        command("abandon","one","confirm");assertEquals("ACTIVE",store.plot("one").state());
        command("abandon","one");time.addAndGet(60000);command("abandon","one","confirm");
        assertEquals("ACTIVE",store.plot("one").state());assertEquals(0,reconciles.get());
    }
    @Test void anotherPlayerCannotUseOwnersPendingConfirmation()throws Exception {
        command("abandon","one");UUID other=UUID.randomUUID();
        Player stranger=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(o,m,a)->m.getName().equals("getUniqueId")?other:null);
        commands.onCommand(stranger,null,"plots",new String[]{"abandon","one","confirm"});
        assertEquals("ACTIVE",store.plot("one").state());assertEquals(0,reconciles.get());
    }
    @Test void failedProtectionPreflightPreventsRefundAndClosure()throws Exception {
        command("abandon","one");valid.set(false);command("abandon","one","confirm");
        assertEquals(14000,store.account(owner).orElseThrow().cents());assertEquals("ACTIVE",store.plot("one").state());assertEquals(1,failures.get());
    }
    @Test void protectionFailureAfterCommitCannotDoubleRefund()throws Exception {
        command("abandon","one");reconcileWorks.set(false);command("abandon","one","confirm");
        assertEquals(17000,store.account(owner).orElseThrow().cents());assertEquals("RECLAIM",store.plot("one").state());assertEquals(1,failures.get());
        command("abandon","one","confirm");assertEquals(17000,store.account(owner).orElseThrow().cents());
    }
    @Test void completionOnlyOffersConfirmForCurrentReviewedPlot() {
        assertEquals(List.of(),commands.onTabComplete(player,null,"plots",new String[]{"abandon","one",""}));
        command("abandon","one");assertEquals(List.of("confirm"),commands.onTabComplete(player,null,"plots",new String[]{"abandon","one",""}));
        time.addAndGet(60000);assertEquals(List.of(),commands.onTabComplete(player,null,"plots",new String[]{"abandon","one",""}));
    }

    @Test void expiryNotifiesOnceAtDeadlineWithoutChangingLease()throws Exception {
        var notices=new ArrayList<String>();command("abandon","one");
        time.addAndGet(59999);commands.expireConfirmations((id,plot)->notices.add(id+":"+plot));assertTrue(notices.isEmpty());
        time.incrementAndGet();commands.expireConfirmations((id,plot)->notices.add(id+":"+plot));
        commands.expireConfirmations((id,plot)->notices.add(id+":"+plot));
        assertEquals(List.of(owner+":one"),notices);assertEquals("ACTIVE",store.plot("one").state());
        command("abandon","one","confirm");assertEquals(14000,store.account(owner).orElseThrow().cents());
    }
    @Test void renewedPreviewDoesNotExpireAtOldDeadline(){
        command("abandon","one");time.addAndGet(30000);command("abandon","one");time.addAndGet(30000);
        commands.expireConfirmations((id,plot)->fail("New review expired too early"));
        assertEquals(List.of("confirm"),commands.onTabComplete(player,null,"plots",new String[]{"abandon","one",""}));
    }
    @Test void completedConfirmationHasNoLaterExpiryNotice(){
        command("abandon","one");command("abandon","one","confirm");time.addAndGet(60000);
        commands.expireConfirmations((id,plot)->fail("Completed confirmation must not expire"));
    }
    @Test void findOwnPlotDoesNotChangeBalanceOrMembership()throws Exception {
        var found=new ArrayList<String>();commands.locator((p,id)->found.add(id));
        long balance=store.account(owner).orElseThrow().cents();command("find");
        assertEquals(List.of("one"),found);assertEquals(balance,store.account(owner).orElseThrow().cents());assertEquals(0,reconciles.get());
    }
    @Test void findRejectsUnmappedPlotsAndMalformedArguments(){
        var found=new ArrayList<String>();commands.locator((p,id)->found.add(id));
        command("find","unknown");command("find","one","extra");assertTrue(found.isEmpty());assertEquals(0,failures.get());
    }
    @Test void namedPlotLookupAndCompletionWork(){
        var found=new ArrayList<String>();commands.locator((p,id)->found.add(id));command("find","one");
        assertEquals(List.of("one"),found);assertEquals(List.of("one"),commands.onTabComplete(player,null,"plots",new String[]{"find",""}));
    }
}
