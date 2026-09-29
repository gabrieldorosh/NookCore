package net.nikosnook.core;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import org.bukkit.Material;
class ActivityStoreTest {
 @TempDir Path dir;
 @Test void aggregatesAndPersistsWithoutMixingPlayersOrDays()throws Exception {
  UUID a=UUID.randomUUID(),b=UUID.randomUUID();var day=LocalDate.of(2026,9,30);
  var key=new ActivityStore.Key(day.toString(),a,"active_seconds");
  try(var s=new ActivityStore(dir.resolve("activity.db"))){s.add(Map.of(key,5L),day);s.add(Map.of(key,10L,new ActivityStore.Key(day.toString(),b,"active_seconds"),20L,new ActivityStore.Key(day.minusDays(1).toString(),a,"ore_blocks:DIAMOND_ORE"),2L),day);}
  try(var s=new ActivityStore(dir.resolve("activity.db"))){var rows=s.read(day,day);assertEquals(2,rows.size());assertEquals(15,rows.stream().filter(r->r.player().equals(a)).findFirst().orElseThrow().amount());assertEquals(3,s.read(day.minusDays(1),day).size());}
 }
 @Test void badBatchCannotPartiallyWrite()throws Exception {
  try(var s=new ActivityStore(dir.resolve("activity.db"))){var day=LocalDate.now();UUID id=UUID.randomUUID();assertThrows(IllegalArgumentException.class,()->s.add(Map.of(new ActivityStore.Key(day.toString(),id,"active_seconds"),5L,new ActivityStore.Key(day.toString(),id,"bad,metric"),1L),day));assertTrue(s.read(day,day).isEmpty());}
 }
 @Test void retentionKeepsExactlyNinetyCalendarDays()throws Exception {
  var day=LocalDate.of(2026,9,30);UUID id=UUID.randomUUID();
  try(var s=new ActivityStore(dir.resolve("activity.db"))){s.add(Map.of(new ActivityStore.Key(day.minusDays(90).toString(),id,"active_seconds"),5L,new ActivityStore.Key(day.minusDays(89).toString(),id,"active_seconds"),10L),day);assertEquals(1,s.read(day.minusDays(89),day).size());assertThrows(IllegalArgumentException.class,()->s.read(day.minusDays(90),day));}
 }
 @Test void activityWindowRejectsFutureAndIdleSamples(){assertTrue(EconomyMetrics.active(60_000,0L));assertFalse(EconomyMetrics.active(60_001,0L));assertFalse(EconomyMetrics.active(0,1L));assertFalse(EconomyMetrics.active(0,null));}
 @Test void materialCountersDistinguishLogsFromWoodAndStrippedBuilds(){assertTrue(EconomyMetrics.ore(Material.DEEPSLATE_DIAMOND_ORE));assertTrue(EconomyMetrics.ore(Material.ANCIENT_DEBRIS));assertFalse(EconomyMetrics.ore(Material.DIAMOND_BLOCK));assertTrue(EconomyMetrics.log(Material.OAK_LOG));assertFalse(EconomyMetrics.log(Material.STRIPPED_OAK_LOG));assertFalse(EconomyMetrics.log(Material.OAK_WOOD));}
 @Test void daysAndExportSyntaxAreBounded(){assertEquals(7,EconomyMetrics.days(new String[]{"metrics"}));assertEquals(7,EconomyMetrics.days(new String[]{"metrics","export"}));assertEquals(1,EconomyMetrics.days(new String[]{"metrics","export","1"}));for(var args:List.of(new String[]{"metrics","0"},new String[]{"metrics","91"},new String[]{"metrics","1","extra"},new String[]{"metrics","export","1","extra"}))assertThrows(IllegalArgumentException.class,()->EconomyMetrics.days(args));}
 @Test void reportsDoNotPretendHistoricalIncomeIsObservedHourlyEarnings(){var id=UUID.randomUUID();var rows=List.of(new ActivityStore.Row("2026-09-30",id,"active_seconds",3600));var text=String.join("\n",EconomyMetrics.summarise(rows,Map.of(id,1000L),1));assertTrue(text.contains("Do not divide"));String csv=EconomyMetrics.csv(rows,Map.of(id,1000L));assertTrue(csv.contains("interval,"+id+",quest_advancement_cents,1000"));assertEquals(3,csv.lines().count());}
 @Test void pricingEarningsExcludeJoiningStaffTransfersAndIntegrations()throws Exception {
  try(var store=new NookStore(dir.resolve("nooks.db"))){UUID a=UUID.randomUUID(),b=UUID.randomUUID();store.join(a,"A",1,6000);store.join(b,"B",1,6000);store.award(a,"minecraft:story/root",50,2);store.award(a,"quest:week:diamonds",1500,3);store.adjust(a,100,"staff","test",4);store.transfer(b,a,20,5);store.integrationAdjustment(a,30,"test",6);assertEquals(1550L,store.metricEarnings(1,7).get(a));assertFalse(store.metricEarnings(1,7).containsKey(b));assertEquals(1500L,store.metricEarnings(3,4).get(a));}
 }
}
