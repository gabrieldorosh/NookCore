package net.nikosnook.core;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MilestoneRewardsTest {
 @TempDir Path temp;
 @Test void existingConfigurationsGainOnlyTheFourApprovedCustomDefaults(){
  var config=new YamlConfiguration();config.set("minecraft:story/mine_stone",75);
  var rewards=MilestoneRewards.load(config);assertEquals(5,rewards.size());
  assertEquals(300L,rewards.get("nikosnook:quests/all_in_a_weeks_work"));
  assertEquals(75L,rewards.get("minecraft:story/mine_stone"));
 }
 @Test void explicitZeroAndCustomRatesArePreserved(){
  var config=new YamlConfiguration();config.set("nikosnook:community/just_browsing",0);
  config.set("nikosnook:quests/all_in_a_weeks_work",150);
  assertEquals(0L,MilestoneRewards.load(config).get("nikosnook:community/just_browsing"));
  assertEquals(150L,MilestoneRewards.load(config).get("nikosnook:quests/all_in_a_weeks_work"));
  config.set("foreign:bad",75);assertThrows(IllegalArgumentException.class,()->MilestoneRewards.load(config));
 }
 @Test void eachCustomRewardIsDurableAndCannotPayAgainAfterRestart()throws Exception{
  UUID player=UUID.randomUUID();Path file=temp.resolve("nooks.db");
  try(var store=new NookStore(file)){store.join(player,"Player",1,0);
   for(var entry:MilestoneRewards.DEFAULTS.entrySet())assertTrue(store.award(player,"adv:"+entry.getKey(),entry.getValue(),2));
   assertEquals(525,store.account(player).orElseThrow().cents());
  }
  try(var store=new NookStore(file)){
   for(var entry:MilestoneRewards.DEFAULTS.entrySet())assertFalse(store.award(player,"adv:"+entry.getKey(),entry.getValue(),3));
   assertEquals(525,store.account(player).orElseThrow().cents());
  }
 }
}
