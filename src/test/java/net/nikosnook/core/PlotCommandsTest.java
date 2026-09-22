package net.nikosnook.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.entity.Player;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PlotCommandsTest {
    @TempDir Path dir;NookStore store;Player player;UUID id=UUID.randomUUID();AtomicInteger failures=new AtomicInteger(),reconciles=new AtomicInteger();
    @BeforeEach void setup()throws Exception {
        store=new NookStore(dir.resolve("nooks.db"));store.join(id,"Player",System.currentTimeMillis(),6000);store.definePlot("one",3000);
        player=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){case "getUniqueId"->id;case "getName"->"Player";default->null;});
    }
    @AfterEach void close()throws Exception {store.close();}
    PlotCommands commands(boolean enabled,boolean mapped,Runnable validate,Runnable reconcile){return new PlotCommands(store,()->enabled,reconcile,e->failures.incrementAndGet(),p->mapped,validate);}
    void rent(PlotCommands commands){commands.onCommand(player,null,"nookplots",new String[]{"rent","one"});}
    @Test void malformedRentalCommandsCannotSpendMoneyOrGrantMembership()throws Exception {
        var c=commands(true,true,()->{},()->reconciles.incrementAndGet());
        for(String[] args:new String[][]{{"rent"},{"rent","one","extra"},{"prepay","one","1.5"},{"invite","one","Player"}})
            c.onCommand(player,null,"nookplots",args);
        assertEquals(6000,store.account(id).orElseThrow().cents());assertEquals("AVAILABLE",store.plot("one").state());
        assertTrue(store.members("one").isEmpty());assertEquals(0,reconciles.get());assertEquals(0,failures.get());
    }
    UUID inviter(String name,String plot)throws Exception {UUID owner=UUID.randomUUID();long now=System.currentTimeMillis();store.join(owner,name,now,6000);store.definePlot(plot,3000);store.rent(plot,owner,now);store.invite(plot,owner,id,"STOCK",now);return owner;}
    @Test void acceptsNamedInvitationWithoutTypingToken()throws Exception {
        inviter("Owner","other");var c=commands(true,true,()->{},()->{});
        c.onCommand(player,null,"nookplots",new String[]{"accept","owner"});assertEquals("STOCK",store.role("other",id));
    }
    @Test void requiresChoiceWhenSeveralInvitationsExist()throws Exception {
        inviter("First","first");inviter("Second","second");var c=commands(true,true,()->{},()->{});
        assertThrows(IllegalArgumentException.class,()->c.resolveInvitation(id,"",System.currentTimeMillis()));
        assertEquals("second",c.resolveInvitation(id,"Second",System.currentTimeMillis()).plot());
    }
    @Test void cannotResolveAnotherRecipientsInvitation()throws Exception {
        inviter("Owner","other");var c=commands(true,true,()->{},()->{});
        assertThrows(IllegalArgumentException.class,()->c.resolveInvitation(UUID.randomUUID(),"Owner",System.currentTimeMillis()));
    }
    @Test void staleClickableInvitationCannotAcceptReplacement()throws Exception {
        UUID owner=inviter("Owner","other");long now=System.currentTimeMillis();var old=store.invitations(id,now).getFirst();store.invite("other",owner,id,"BUILD",now);
        var c=commands(true,true,()->{},()->{});assertThrows(IllegalArgumentException.class,()->c.resolveInvitation(id,old.token().toString(),now));
        assertEquals("BUILD",c.resolveInvitation(id,"Owner",now).role());
    }
    @Test void invitingExistingMemberUpdatesRoleWithoutRemovingMembership()throws Exception {
        long now=System.currentTimeMillis();store.rent("one",id,now);UUID member=UUID.randomUUID();store.join(member,"Member",now,6000);
        store.acceptInvitation(store.invite("one",id,member,"STOCK",now).token(),member,now);
        var c=commands(true,true,()->{},()->reconciles.incrementAndGet());c.onCommand(player,null,"nookplots",new String[]{"invite","one","Member","build"});
        assertEquals("BUILD",store.role("one",member));assertEquals(1,reconciles.get());assertEquals(0,failures.get());
    }
    @Test void invitationCompletionOffersNamesAndNoTokens()throws Exception {
        inviter("Owner","other");var c=commands(true,true,()->{},()->{});
        assertEquals(List.of("Owner"),c.onTabComplete(player,null,"nookplots",new String[]{"accept","O"}));
    }
    @Test void disabledOrUnmappedPlotCannotChargeRent()throws Exception {
        rent(commands(false,true,()->{},()->reconciles.incrementAndGet()));rent(commands(true,false,()->{},()->reconciles.incrementAndGet()));
        assertEquals(6000,store.account(id).orElseThrow().cents());assertEquals("AVAILABLE",store.plot("one").state());assertEquals(0,reconciles.get());
    }
    @Test void brokenProtectionPreflightPreventsDebit()throws Exception {
        rent(commands(true,true,()->{throw new IllegalStateException("Missing region");},()->reconciles.incrementAndGet()));
        assertEquals(6000,store.account(id).orElseThrow().cents());assertEquals(1,failures.get());assertEquals(0,reconciles.get());
    }
    @Test void successfulRentReconcilesAndRetryCannotChargeAgain()throws Exception {
        var commands=commands(true,true,()->{},()->reconciles.incrementAndGet());rent(commands);rent(commands);
        assertEquals(3000,store.account(id).orElseThrow().cents());assertEquals(1,reconciles.get());assertEquals(0,failures.get());
    }
    @Test void postCommitRegionFailureIsReportedWithoutRepeatingPayment()throws Exception {
        rent(commands(true,true,()->{},()->{throw new IllegalStateException("Region save failed");}));
        assertEquals(3000,store.account(id).orElseThrow().cents());assertEquals("ACTIVE",store.plot("one").state());assertEquals(1,failures.get());
        // The plugin failure callback pauses all further commands. The committed lease is recoverable.
        rent(commands(false,true,()->{},()->{}));assertEquals(3000,store.account(id).orElseThrow().cents());
    }
    @Test void billingOnlyProcessesValidatedMappedPlots()throws Exception {
        long now=System.currentTimeMillis();store.rent("one",id,now);store.touch(id,now+NookStore.WEEK);
        store.tick(now+NookStore.WEEK,Set.of());assertEquals(3000,store.account(id).orElseThrow().cents());assertFalse(store.canTrade("one",now+NookStore.WEEK));
        store.tick(now+NookStore.WEEK,Set.of("one"));assertEquals(0,store.account(id).orElseThrow().cents());
    }
}
