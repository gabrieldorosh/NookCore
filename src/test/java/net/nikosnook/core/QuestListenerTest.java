package net.nikosnook.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.damage.DamageSource;
import org.bukkit.event.entity.EntityDeathEvent;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import static org.junit.jupiter.api.Assertions.*;

class QuestListenerTest {
    @TempDir Path dir;NookStore store;
    UUID id=UUID.randomUUID();AtomicLong now=new AtomicLong(Instant.parse("2026-09-25T18:00:00Z").toEpochMilli());
    AtomicBoolean healthy=new AtomicBoolean(true);AtomicInteger failures=new AtomicInteger();
    GameMode mode=GameMode.SURVIVAL;World.Environment environment=World.Environment.NORMAL;
    List<String> messages=new ArrayList<>();Player player;
    @SuppressWarnings("unchecked") <T>T proxy(Class<T> type,InvocationHandler handler){return (T)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{type},handler);}
    @BeforeEach void setup()throws Exception{
        store=new NookStore(dir.resolve("quests.db"));store.join(id,"Player",now.get(),6000);
        World world=proxy(World.class,(o,m,a)->m.getName().equals("getEnvironment")?environment:null);
        player=proxy(Player.class,(o,m,a)->switch(m.getName()){
            case "getUniqueId"->id;case "getName"->"Player";case "getGameMode"->mode;case "getWorld"->world;
            case "sendMessage"->{if(a[0] instanceof Component c)messages.add(PlainTextComponentSerializer.plainText().serialize(c));yield null;}
            default->null;
        });
    }
    @AfterEach void close()throws Exception{store.close();}
    WeeklyQuests quests(QuestPlan.Goal... goals){return new WeeklyQuests(store,healthy::get,e->{failures.incrementAndGet();healthy.set(false);},List.of(goals),now::get);}
    QuestPlan.Goal goal(String id,String kind,String target){return new QuestPlan.Goal(id,id,kind,target,1,1500);}
    @Test void capacityFailureDoesNotPauseEconomyAndNotificationIsNotRepeated()throws Exception{
        var q=quests(goal("hunt","KILL","SKELETON"));store.adjust(id,Money.MAX-6000,"staff","capacity test",now.get());
        assertFalse(q.record(player,"KILL","SKELETON",null));assertFalse(q.record(player,"KILL","SKELETON",null));
        assertTrue(healthy.get());assertEquals(0,failures.get());assertEquals(1,messages.size());
        assertEquals(0,store.questProgress(id,QuestPlan.week(now.get()).id(),"hunt"));
        store.adjust(id,-1500,"staff","room",now.get());assertTrue(q.record(player,"KILL","SKELETON",null));assertEquals(Money.MAX,store.account(id).orElseThrow().cents());
    }
    @Test void rejectedBiomeCanRetryWithoutLeavingBiome()throws Exception{
        var q=quests(goal("visit","BIOME","ANY"));store.adjust(id,Money.MAX-6000,"staff","capacity test",now.get());
        q.sampleBiome(player,"minecraft:plains");assertEquals(0,store.questProgress(id,QuestPlan.week(now.get()).id(),"visit"));
        store.adjust(id,-1500,"staff","room",now.get());q.sampleBiome(player,"minecraft:plains");
        assertEquals(1,store.questProgress(id,QuestPlan.week(now.get()).id(),"visit"));assertEquals(Money.MAX,store.account(id).orElseThrow().cents());
        q.sampleBiome(player,"minecraft:plains");assertEquals(0,failures.get());assertEquals(2,messages.size());
    }
    @Test void sameBiomeCountsAgainAtExactNewWeek()throws Exception{
        var q=quests(goal("visit","BIOME","ANY"));q.sampleBiome(player,"minecraft:plains");
        now.set(WeekSchedule.next(Instant.ofEpochMilli(now.get())).toEpochMilli());q.sampleBiome(player,"minecraft:plains");
        assertEquals(9000,store.account(id).orElseThrow().cents());assertEquals(1,store.questProgress(id,QuestPlan.week(now.get()).id(),"visit"));
    }
    @Test void storageFailureStillInvokesGlobalFailure()throws Exception{
        var q=quests(goal("hunt","KILL","SKELETON"));store.close();assertFalse(q.record(player,"KILL","SKELETON",null));assertFalse(healthy.get());assertEquals(1,failures.get());
    }
    EntityDeathEvent death(Player killer){
        LivingEntity entity=proxy(LivingEntity.class,(o,m,a)->switch(m.getName()){case "getKiller"->killer;case "getType"->EntityType.SKELETON;default->null;});
        DamageSource source=proxy(DamageSource.class,(o,m,a)->null);
        return new EntityDeathEvent(entity,source,new ArrayList<>());
    }
    @Test void actualDeathListenerTracksKillAndChallenge()throws Exception{
        var q=quests(goal("hunt","KILL","SKELETON"),goal("challenge","KILL","HOSTILE"));q.death(death(player));
        assertEquals(9000,store.account(id).orElseThrow().cents());assertEquals(2,messages.size());assertEquals(0,failures.get());
    }
    @Test void actualDeathListenerRejectsCreativeSpectatorAndUnattributedKills()throws Exception{
        var q=quests(goal("hunt","KILL","SKELETON"));mode=GameMode.CREATIVE;q.death(death(player));mode=GameMode.SPECTATOR;q.death(death(player));
        mode=GameMode.SURVIVAL;q.death(death(null));assertEquals(6000,store.account(id).orElseThrow().cents());assertTrue(messages.isEmpty());
    }
    @Test void survivalKillsCountInEveryDimensionAndPayOnlyOnce()throws Exception{
        var q=quests(new QuestPlan.Goal("hunt","Hunt","KILL","SKELETON",3,1500));
        for(var dimension:List.of(World.Environment.NORMAL,World.Environment.NETHER,World.Environment.THE_END)){environment=dimension;q.death(death(player));}
        q.death(death(player));assertEquals(3,store.questProgress(id,QuestPlan.week(now.get()).id(),"hunt"));assertEquals(7500,store.account(id).orElseThrow().cents());assertEquals(0,failures.get());
    }
    @Test void endShulkersAndNetherBlazesAlsoAdvanceHostileChallenges()throws Exception{
        var q=quests(goal("shulkers","KILL","SHULKER"),new QuestPlan.Goal("challenge","Challenge","KILL","HOSTILE",2,3000));
        for(var type:List.of(EntityType.SHULKER,EntityType.BLAZE)){
            environment=type==EntityType.SHULKER?World.Environment.THE_END:World.Environment.NETHER;
            LivingEntity entity=proxy(LivingEntity.class,(o,m,a)->switch(m.getName()){case "getKiller"->player;case "getType"->type;default->null;});
            q.death(new EntityDeathEvent(entity,proxy(DamageSource.class,(o,m,a)->null),new ArrayList<>()));
        }
        assertEquals(10500,store.account(id).orElseThrow().cents());assertEquals(1,store.questProgress(id,QuestPlan.week(now.get()).id(),"shulkers"));assertEquals(2,store.questProgress(id,QuestPlan.week(now.get()).id(),"challenge"));
    }
    @Test void nikoAcceptanceIsRequiredInNetherAndEnd()throws Exception{
        store.configureNikoGate(true,true,now.get());var q=quests(goal("hunt","KILL","SKELETON"));
        for(var dimension:List.of(World.Environment.NETHER,World.Environment.THE_END)){environment=dimension;q.death(death(player));}
        assertEquals(6000,store.account(id).orElseThrow().cents());store.acceptNiko(id,QuestPlan.week(now.get()).id(),"niko",now.get());
        q.death(death(player));assertEquals(7500,store.account(id).orElseThrow().cents());
    }

    @Test void cosmeticFailureCannotPauseEconomyOrRepeatReward()throws Exception{
        var q=quests(goal("hunt","KILL","SKELETON"));AtomicInteger effects=new AtomicInteger(),cosmeticErrors=new AtomicInteger();
        q.setCelebration((p,challenge)->{effects.incrementAndGet();throw new IllegalStateException("visual unavailable");},e->cosmeticErrors.incrementAndGet());
        q.record(player,"KILL","SKELETON",null);q.record(player,"KILL","SKELETON",null);
        assertEquals(1,effects.get());assertEquals(1,cosmeticErrors.get());assertTrue(healthy.get());assertEquals(0,failures.get());assertEquals(7500,store.account(id).orElseThrow().cents());
    }

    @Test void rapidKillProgressAndOverlappingGoalsDoNotFloodChat()throws Exception{
        var q=quests(new QuestPlan.Goal("witch","Witches","KILL","WITCH",8,1500),new QuestPlan.Goal("challenge","Hostiles","KILL","HOSTILE",200,3000));
        for(int n=0;n<200;n++){if(n<8)q.record(player,"KILL","WITCH",null);q.record(player,"KILL","HOSTILE",null);}
        assertEquals(4,messages.size()); // first + completion for each goal, never one message per kill
        assertEquals(10500,store.account(id).orElseThrow().cents());assertEquals(200,store.questProgress(id,QuestPlan.week(now.get()).id(),"challenge"));
    }
    @Test void unrelatedPartialProgressDoesNotResetCapacityWarning()throws Exception{
        var q=quests(goal("hunt","KILL","SKELETON"),new QuestPlan.Goal("other","Other","KILL","ZOMBIE",2,1500));
        store.adjust(id,Money.MAX-6000,"staff","capacity test",now.get());
        q.record(player,"KILL","SKELETON",null);q.record(player,"KILL","ZOMBIE",null);q.record(player,"KILL","SKELETON",null);
        assertEquals(1,messages.stream().filter(m->m.contains("no room for this reward")).count());assertTrue(healthy.get());
    }
}
