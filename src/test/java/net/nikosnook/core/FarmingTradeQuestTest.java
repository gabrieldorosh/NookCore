package net.nikosnook.core;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Ageable;
import org.bukkit.inventory.*;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import net.kyori.adventure.text.Component;
import static org.junit.jupiter.api.Assertions.*;

class FarmingTradeQuestTest {
    @TempDir Path dir;NookStore store;WeeklyQuests q;Player player;
    final UUID id=UUID.randomUUID();final long now=Instant.parse("2026-10-07T01:00:00Z").toEpochMilli();
    GameMode mode=GameMode.SURVIVAL;World.Environment environment=World.Environment.NORMAL;
    Merchant merchant;List<Component> messages=new ArrayList<>();List<QuestPlan.Goal> goals;
    @SuppressWarnings("unchecked") <T>T proxy(Class<T> type,InvocationHandler f){return (T)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{type},f);}
    @BeforeEach void setup()throws Exception{
        store=new NookStore(dir.resolve("quests.db"));store.join(id,"Player",now,6000);
        goals=List.of(new QuestPlan.Goal("crops","Green fingers","HARVEST","MATURE_CROPS",64,1500),new QuestPlan.Goal("trades","Local trade","TRADE","VILLAGER",8,1500));
        World world=proxy(World.class,(o,m,a)->m.getName().equals("getEnvironment")?environment:null);
        merchant=proxy(Villager.class,(o,m,a)->null);
        MerchantInventory inventory=proxy(MerchantInventory.class,(o,m,a)->m.getName().equals("getMerchant")?merchant:null);
        InventoryView view=proxy(InventoryView.class,(o,m,a)->m.getName().equals("getTopInventory")?inventory:null);
        player=proxy(Player.class,(o,m,a)->switch(m.getName()){
            case "getUniqueId"->id;case "getGameMode"->mode;case "getWorld"->world;case "getOpenInventory"->view;
            case "sendMessage"->{if(a[0] instanceof Component c)messages.add(c);yield null;}default->null;
        });
        q=new WeeklyQuests(store,()->true,e->{throw new AssertionError(e);},goals,()->now);
    }
    @AfterEach void close()throws Exception{store.close();}
    BlockDropItemEvent crop(Material material,int age,int maximum){
        Ageable data=proxy(Ageable.class,(o,m,a)->switch(m.getName()){case "getAge"->age;case "getMaximumAge"->maximum;default->null;});
        BlockState state=proxy(BlockState.class,(o,m,a)->switch(m.getName()){case "getType"->material;case "getBlockData"->data;default->null;});
        Block block=proxy(Block.class,(o,m,a)->null);
        return new BlockDropItemEvent(block,state,player,new ArrayList<>());
    }
    PlayerStatisticIncrementEvent trade(){return new PlayerStatisticIncrementEvent(player,Statistic.TRADED_WITH_VILLAGER,100,101);}
    int progress(String goal)throws Exception{return store.questProgress(id,QuestPlan.week(now).id(),goal);}
    @Test void onlyFourMatureCropTypesCount()throws Exception{
        q.harvest(crop(Material.WHEAT,6,7));q.harvest(crop(Material.NETHER_WART,3,3));q.harvest(crop(Material.COCOA,2,2));
        for(Material m:List.of(Material.WHEAT,Material.CARROTS,Material.POTATOES,Material.BEETROOTS))q.harvest(crop(m,7,7));
        assertEquals(4,progress("crops"));
    }
    @Test void cancelledCreativeAndNetherHarvestsDoNotCount()throws Exception{
        var e=crop(Material.WHEAT,7,7);e.setCancelled(true);q.harvest(e);
        mode=GameMode.CREATIVE;q.harvest(crop(Material.WHEAT,7,7));mode=GameMode.SURVIVAL;environment=World.Environment.NETHER;q.harvest(crop(Material.WHEAT,7,7));
        assertEquals(0,progress("crops"));
    }
    @Test void cropDropsDoNotMultiplyProgressAndRepeatedGrowingAtSamePositionCounts()throws Exception{
        for(int n=0;n<64;n++){var e=crop(Material.CARROTS,7,7);for(int i=0;i<5;i++)e.getItems().add(proxy(Item.class,(o,m,a)->null));q.harvest(e);}
        q.harvest(crop(Material.WHEAT,7,7));assertEquals(64,progress("crops"));assertEquals(7500,store.account(id).orElseThrow().cents());assertEquals(2,messages.size());
    }
    @Test void onlySingleSuccessfulVillagerStatisticIncrementsCount()throws Exception{
        var e=trade();e.setCancelled(true);q.trade(e);
        q.trade(new PlayerStatisticIncrementEvent(player,Statistic.TALKED_TO_VILLAGER,0,1));
        q.trade(new PlayerStatisticIncrementEvent(player,Statistic.TRADED_WITH_VILLAGER,0,8));
        merchant=proxy(WanderingTrader.class,(o,m,a)->null);q.trade(trade());
        merchant=proxy(Villager.class,(o,m,a)->null);q.trade(trade());assertEquals(1,progress("trades"));
    }
    @Test void tradeSurvivalAndOverworldRestrictionsRemain()throws Exception{
        mode=GameMode.CREATIVE;q.trade(trade());mode=GameMode.SURVIVAL;environment=World.Environment.NETHER;q.trade(trade());assertEquals(0,progress("trades"));
    }
    @Test void batchOfEightPaysOnceAndQuietProgressPersists()throws Exception{
        for(int n=0;n<12;n++)q.trade(trade());assertEquals(8,progress("trades"));assertEquals(7500,store.account(id).orElseThrow().cents());assertEquals(2,messages.size());
        store.close();store=new NookStore(dir.resolve("quests.db"));assertEquals(8,progress("trades"));assertEquals(7500,store.account(id).orElseThrow().cents());
    }
    @Test void newKindsRoundTripWithoutChangingExistingDefinitions(){assertEquals(goals,QuestPlan.decode(QuestPlan.encode(goals)));}
}
