package net.nikosnook.core;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;

/** Owner-approved one-time custom rewards, through the existing durable award ledger. */
final class MilestoneRewards {
    static final Map<String,Long> DEFAULTS=Map.of(
        "nikosnook:quests/a_little_choice",75L,
        "nikosnook:community/just_browsing",75L,
        "nikosnook:community/a_helping_hand",75L,
        "nikosnook:quests/all_in_a_weeks_work",300L);
    static Map<String,Long> load(ConfigurationSection section){
        if(section==null)throw new IllegalArgumentException("Missing advancement reward map.");
        var rewards=new HashMap<String,Long>(DEFAULTS);
        for(String key:section.getKeys(false)){
            long amount=section.getLong(key,-1);
            if(amount<0 || amount>Money.MAX || !key.matches("(?:minecraft|nikosnook):[a-z0-9_./-]+"))
                throw new IllegalArgumentException("Invalid reward: "+key);
            rewards.put(key,amount);
        }
        return Map.copyOf(rewards);
    }
}
