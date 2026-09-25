package net.nikosnook.core;
import org.junit.jupiter.api.Test;
import org.bukkit.command.CommandSender;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class FeatureRoutesTest {
 CommandSender sender(boolean admin){return (CommandSender)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{CommandSender.class},(o,m,a)->m.getName().equals("hasPermission")?admin:null);}
 @Test void moneyRoutingKeepsSelfBalancePublicAndAdministrationDistinct(){assertFalse(FeatureRoutes.moneyAdmin(new String[]{"balance"}));assertTrue(FeatureRoutes.moneyAdmin(new String[]{"balance","Alex"}));assertFalse(FeatureRoutes.moneyAdmin(new String[]{"pay","Alex","1"}));assertTrue(FeatureRoutes.moneyAdmin(new String[]{"give","Alex","1","gift"}));assertArrayEquals(new String[]{"awards","off"},FeatureRoutes.money(new String[]{"advancements","off"}));}
 @Test void plotRoutingPreservesAllStorageNoteWordsAndRejectsOtherActions(){assertArrayEquals(new String[]{"plotclear","willow-way","staff","chest","A3"},FeatureRoutes.plot(new String[]{"clear","willow-way","staff","chest","A3"}));assertThrows(IllegalArgumentException.class,()->FeatureRoutes.plot(new String[]{"give","Alex","1"}));}
 @Test void unprivilegedPlotAdministrationCannotReachDelegate(){AtomicInteger calls=new AtomicInteger();var routes=new PlotRoutes(null,null,(s,c,l,a)->{calls.incrementAndGet();return true;},(s,c,l,a)->List.of());routes.onCommand(sender(false),null,"plots",new String[]{"admin","clear","one","note"});assertEquals(0,calls.get());assertEquals(List.of(),routes.onTabComplete(sender(false),null,"plots",new String[]{"admin",""}));}
 @Test void staffRouteDoesNotDependOnRentalReadiness(){var received=new ArrayList<String>();var routes=new PlotRoutes(null,null,(s,c,l,a)->{received.addAll(List.of(a));return true;},(s,c,l,a)->List.of("willow-way"));routes.onCommand(sender(true),null,"plots",new String[]{"admin","clear","one","note"});assertEquals(List.of("plotclear","one","note"),received);assertEquals(List.of("willow-way"),routes.onTabComplete(sender(true),null,"plots",new String[]{"admin","clear",""}));}
 @Test void setupPermissionIsCheckedBeforeMissingPluginFeedback(){var messages=new ArrayList<String>();CommandSender denied=(CommandSender)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{CommandSender.class},(o,m,a)->{if(m.getName().equals("hasPermission"))return false;if(m.getName().equals("sendMessage") && a[0] instanceof net.kyori.adventure.text.Component c)messages.add(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c));return null;});new PlotRoutes(null,null,null,null).onCommand(denied,null,"plots",new String[]{"setup","district"});assertTrue(messages.getFirst().contains("for admins"));}
}
