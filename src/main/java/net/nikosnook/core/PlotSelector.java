package net.nikosnook.core;

import java.util.*;

/** Stable IDs always win; readable street addresses are convenient aliases. */
final class PlotSelector {
    static String alias(String address){return address.toLowerCase(Locale.ROOT).replace(' ','-');}
    static String resolve(String input,Map<String,String> addresses){
        if(addresses.containsKey(input))return input;
        var matches=addresses.entrySet().stream().filter(e->alias(e.getValue()).equalsIgnoreCase(input)).map(Map.Entry::getKey).toList();
        if(matches.size()>1)throw new IllegalArgumentException("That address is ambiguous. Use the plot ID from /plots info.");
        return matches.isEmpty()?input:matches.getFirst();
    }
}
