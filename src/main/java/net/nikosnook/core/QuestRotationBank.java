package net.nikosnook.core;

import java.time.LocalDate;
import java.util.*;

/** Deterministic, bounded selection; persisted week snapshots remain authoritative. */
final class QuestRotationBank {
    private final List<QuestPlan.Goal> regular,challenges;
    QuestRotationBank(List<QuestPlan.Goal> regular,List<QuestPlan.Goal> challenges){
        this.regular=List.copyOf(regular);this.challenges=List.copyOf(challenges);
        var all=new ArrayList<>(regular);all.addAll(challenges);
        if(regular.size()<9 || challenges.size()<2 || all.stream().map(QuestPlan.Goal::id).distinct().count()!=all.size()
            || regular.stream().anyMatch(g->g.reward()!=1500) || challenges.stream().anyMatch(g->g.reward()!=3000)
            || regular.stream().map(QuestPlan.Goal::kind).distinct().count()<3)throw new IllegalArgumentException("Quest bank needs unique IDs, nine regular goals, two challenges and at least three activity types.");
    }
    int regularCount(){return regular.size();}int challengeCount(){return challenges.size();}
    static String target(QuestPlan.Goal goal){return goal.kind()+":"+goal.target();}
    List<QuestPlan.Goal> offers(String week,List<QuestPlan.Goal> previous){
        var base=select(week,previous);var first=base.getLast();
        Set<String> excluded=new HashSet<>();previous.forEach(g->excluded.add(target(g)));base.forEach(g->excluded.add(target(g)));
        long seed=LocalDate.parse(week).toEpochDay() ^ 0x4e494b4f43484f49L;
        var harder=new ArrayList<>(challenges.stream().filter(g->!excluded.contains(target(g))).toList());Collections.shuffle(harder,new Random(seed));
        for(var second:harder){
            var chosen=new ArrayList<>(base.subList(0,6));Set<String> used=new HashSet<>(excluded);used.add(target(second));
            long deposits=base.stream().filter(g->g.kind().equals("DEPOSIT")).count()+(second.kind().equals("DEPOSIT")?1:0);
            if(deposits>2)continue;
            var pool=new ArrayList<>(regular.stream().filter(g->!used.contains(target(g))).toList());Collections.shuffle(pool,new Random(seed+second.id().hashCode()));
            for(var goal:pool){
                if(chosen.stream().filter(g->g.kind().equals(goal.kind())).count()>=4 || goal.kind().equals("DEPOSIT") && deposits>=2 || !used.add(target(goal)))continue;
                chosen.add(goal);if(goal.kind().equals("DEPOSIT"))deposits++;
                if(chosen.size()==9){chosen.add(first);chosen.add(second);return List.copyOf(chosen);}
            }
        }
        throw new IllegalArgumentException("The quest bank needs more varied goals to offer nine quests and two challenges.");
    }
    List<QuestPlan.Goal> select(String week,List<QuestPlan.Goal> previous){
        long seed=LocalDate.parse(week).toEpochDay() ^ 0x4e494b4f534e4f4fL;
        Set<String> excluded=new HashSet<>();previous.forEach(g->excluded.add(target(g)));
        var available=regular.stream().filter(g->!excluded.contains(target(g))).toList();
        var harder=new ArrayList<>(challenges.stream().filter(g->!excluded.contains(target(g))).toList());
        Collections.shuffle(harder,new Random(seed));
        for(var challenge:harder){
            var choices=available.stream().filter(g->!target(g).equals(target(challenge))).toList();
            int preferredKinds=(int)Math.min(4,choices.stream().map(QuestPlan.Goal::kind).distinct().count());
            for(int minimum=preferredKinds;minimum>=3;minimum--){
                for(int attempt=0;attempt<128;attempt++){
                    var pool=new ArrayList<>(choices);Collections.shuffle(pool,new Random(seed+31L*attempt+challenge.id().hashCode()));
                    List<QuestPlan.Goal> selected=new ArrayList<>();Map<String,Integer> kinds=new HashMap<>();Set<String> targets=new HashSet<>();
                    int contributions=challenge.kind().equals("DEPOSIT")?1:0;
                    for(var goal:pool){
                        if(kinds.getOrDefault(goal.kind(),0)>=2 || targets.contains(target(goal)) || goal.kind().equals("DEPOSIT") && contributions>=2)continue;
                        selected.add(goal);targets.add(target(goal));kinds.merge(goal.kind(),1,Integer::sum);if(goal.kind().equals("DEPOSIT"))contributions++;
                        if(selected.size()==6)break;
                    }
                    if(selected.size()==6 && kinds.size()>=minimum){selected.add(challenge);return List.copyOf(selected);}
                }
            }
        }
        throw new IllegalArgumentException("The quest bank cannot make a varied week without repeating last week's targets. Add more goals or challenges.");
    }
}
