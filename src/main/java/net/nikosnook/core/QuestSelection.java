package net.nikosnook.core;

import java.util.*;

/** Shared offers are immutable; each confirmed player chooses exactly six plus one. */
final class QuestSelection {
    static boolean board(List<QuestPlan.Goal> goals){
        long regular=goals.stream().filter(g->g.reward()==1500).count();
        // Frozen eight-offer boards remain valid until their normal rollover.
        return (regular==8 || regular==9) && goals.size()==regular+2
            && goals.stream().filter(g->g.reward()==3000).count()==2;
    }
    static Set<String> validate(List<QuestPlan.Goal> offers,Collection<String> ids){
        if(!board(offers))throw new IllegalArgumentException("This week uses the shared quest set.");
        if(ids==null || ids.size()!=7 || new HashSet<>(ids).size()!=7)throw new IllegalArgumentException("pick six quests and one challenge first!");
        Set<String> chosen=Set.copyOf(ids);var goals=offers.stream().filter(g->chosen.contains(g.id())).toList();
        if(goals.size()!=7 || goals.stream().filter(g->g.reward()==1500).count()!=6 || goals.stream().filter(g->g.reward()==3000).count()!=1)
            throw new IllegalArgumentException("pick six quests and one challenge from this week's board!");
        return chosen;
    }
    static Set<String> toggle(List<QuestPlan.Goal> offers,Set<String> current,String id){
        var goal=offers.stream().filter(g->g.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Speak to Niko again for this week's quests."));
        var chosen=new LinkedHashSet<>(current);
        if(chosen.remove(id))return chosen;
        if(goal.reward()==3000)chosen.removeIf(key->offers.stream().anyMatch(g->g.id().equals(key)&&g.reward()==3000));
        else if(offers.stream().filter(g->g.reward()==1500 && chosen.contains(g.id())).count()>=6)
            throw new IllegalArgumentException("you've picked six quests! tap one to make room for another.");
        chosen.add(id);return chosen;
    }
}
