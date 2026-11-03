package net.nikosnook.core;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.time.LocalDate;
import java.util.*;

record AutomaticQuests(boolean enabled,String starts,QuestRotationBank bank) {
    @FunctionalInterface interface Loader { AutomaticQuests load()throws Exception; }
    static AutomaticQuests disabled(){return new AutomaticQuests(false,"9999-12-31",null);}
    boolean applies(String week){return enabled && week.compareTo(starts)>=0;}
    static AutomaticQuests load(JavaPlugin plugin)throws Exception{
        var file=new java.io.File(plugin.getDataFolder(),"quest-rotation.yml");
        if(!file.exists())plugin.saveResource("quest-rotation.yml",false);
        var config=new YamlConfiguration();config.load(file);return parse(config);
    }
    static AutomaticQuests parse(YamlConfiguration config){return parse(config,WeeklyQuests::validateGoal);}
    static AutomaticQuests parse(YamlConfiguration config,java.util.function.Consumer<QuestPlan.Goal> validate){
        if(!config.getBoolean("enabled",false))return disabled();
        String starts=LocalDate.parse(config.getString("starts","2026-11-04")).toString();
        List<QuestPlan.Goal> regular=new ArrayList<>(),challenges=new ArrayList<>();
        var errors=new ArrayList<String>();
        for(String name:List.of("regular","challenges")){
            var section=Objects.requireNonNull(config.getConfigurationSection(name),"Missing automatic quest bank: "+name);
            for(String id:section.getKeys(false)){
                try{
                var c=Objects.requireNonNull(section.getConfigurationSection(id),"Expected a quest section");
                if(!c.getBoolean("enabled",true))continue;
                var goal=new QuestPlan.Goal(id,c.getString("title",""),c.getString("kind",""),c.getString("target",""),c.getInt("amount"),name.equals("regular")?1500:3000);
                validate.accept(goal);(name.equals("regular")?regular:challenges).add(goal);
                }catch(RuntimeException e){errors.add(name+"."+id+": "+e.getMessage());}
            }
        }
        if(!errors.isEmpty())throw new IllegalArgumentException("Quest bank errors: "+String.join("; ",errors));
        var bank=new QuestRotationBank(regular,challenges);
        var first=bank.offers(starts,List.of());bank.offers(LocalDate.parse(starts).plusWeeks(1).toString(),first);
        return new AutomaticQuests(true,starts,bank);
    }
    String description(){return enabled?"automatic selection from "+starts+" ("+bank.regularCount()+" regular / "+bank.challengeCount()+" challenges)":"manual selection";}
}
