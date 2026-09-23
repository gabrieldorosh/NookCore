package net.nikosnook.core;

import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class PlotEntryNoticesTest {
    UUID player=UUID.randomUUID();
    PlotEntryTracker tracker=new PlotEntryTracker();
    @Test void waitsForStableEntryAndDoesNotRepeat(){
        assertFalse(tracker.entered(player,"small",0));assertFalse(tracker.entered(player,"small",499));
        assertTrue(tracker.entered(player,"small",500));assertFalse(tracker.entered(player,"small",10000));
    }
    @Test void rapidCrossingDiscardsOldPlot(){
        tracker.entered(player,"small",0);assertFalse(tracker.entered(player,"large",400));
        assertFalse(tracker.entered(player,"large",500));assertTrue(tracker.entered(player,"large",900));
    }
    @Test void leavingAndReenteringAllowsFreshNotice(){
        tracker.entered(player,"small",0);assertTrue(tracker.entered(player,"small",500));
        assertFalse(tracker.entered(player,null,600));assertFalse(tracker.entered(player,"",1200));
        assertFalse(tracker.entered(player,"small",1300));assertTrue(tracker.entered(player,"small",1800));
    }
    @Test void disconnectAndPauseDiscardTracking(){
        tracker.entered(player,"small",0);tracker.entered(player,"small",500);tracker.retain(Set.of());
        assertFalse(tracker.entered(player,"small",600));assertTrue(tracker.entered(player,"small",1100));
        tracker.clear();assertFalse(tracker.entered(player,"small",1200));
    }
    @Test void subtitlePreservesContentAndProvidesNativeFade(){
        AtomicReference<Title> sent=new AtomicReference<>();
        Player audience=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(o,m,a)->{
            if(m.getName().equals("showTitle"))sent.set((Title)a[0]);
            else fail("Unexpected player call: "+m.getName());return null;
        });
        var message=NookUi.text("Small shop · Open");PlotEntryNotices.show(audience,message,PlotEntryNotices.Display.SUBTITLE);
        assertEquals(message,sent.get().subtitle());assertEquals(Component.empty(),sent.get().title());
        assertEquals(Duration.ofMillis(500),sent.get().times().fadeIn());assertEquals(Duration.ofMillis(750),sent.get().times().fadeOut());
    }
    @Test void actionbarFallbackAndOffDoNotSendTitles(){
        List<Component> sent=new ArrayList<>();
        Player audience=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(o,m,a)->{
            assertEquals("sendActionBar",m.getName());sent.add((Component)a[0]);return null;
        });
        var message=Component.text("Available");PlotEntryNotices.show(audience,message,PlotEntryNotices.Display.ACTIONBAR);
        PlotEntryNotices.show(audience,message,PlotEntryNotices.Display.OFF);assertEquals(List.of(message),sent);
        assertEquals(PlotEntryNotices.Display.SUBTITLE,PlotEntryNotices.Display.parse("typo"));
    }
}
