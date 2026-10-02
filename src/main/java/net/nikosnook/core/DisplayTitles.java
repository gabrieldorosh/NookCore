package net.nikosnook.core;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;

/** Account-bound presentation only: never grants permissions or changes group inheritance. */
final class DisplayTitles {
    private final Map<UUID,String> assignments=new HashMap<>();
    DisplayTitles(ConfigurationSection section){
        if(section==null)return;
        for(String key:section.getKeys(false)){
            String title=section.getString(key,"");
            if(!Set.of("mr-duck","mrs-bee","miss-bee","mrs-niko").contains(title))throw new IllegalArgumentException("Unknown display title: "+title);
            assignments.put(UUID.fromString(key),title);
        }
    }
    String rank(UUID id,String fallback){return assignments.getOrDefault(id,fallback);}
}
