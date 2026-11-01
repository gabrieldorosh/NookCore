package net.nikosnook.core;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.time.LocalDate;
import java.util.*;

record AutomaticQuests(boolean enabled,String starts,QuestRotationBank bank) {
    static AutomaticQuests disabled(){return new AutomaticQuests(false,"9999-12-31",null);}
    boolean applies(String week){return enabled && week.compareTo(starts)>=0;}
    static AutomaticQuests load(JavaPlugin plugin)throws Exception{
        var file=new java.io.File(plugin.getDataFolder(),"quest-rotation.yml");
        if(!file.exists())plugin.saveResource("quest-rotation.yml",false);
        var config=new YamlConfiguration();config.load(file);if(!config.getBoolean("enabled",false))return disabled();
        String starts=LocalDate.parse(config.getString("starts","2026-11-04")).toString();
        List<QuestPlan.Goal> regular=new ArrayList<>(),challenges=new ArrayList<>();
        for(String name:List.of("regular","challenges")){
            var section=Objects.requireNonNull(config.getConfigurationSection(name),"Missing automatic quest bank: "+name);
            for(String id:section.getKeys(false)){
                var c=Objects.requireNonNull(section.getConfigurationSection(id));
                var goal=new QuestPlan.Goal(id,c.getString("title",""),c.getString("kind",""),c.getString("target",""),c.getInt("amount"),name.equals("regular")?1500:3000);
                WeeklyQuests.validateGoal(goal);(name.equals("regular")?regular:challenges).add(goal);
            }
        }
        return new AutomaticQuests(true,starts,new QuestRotationBank(regular,challenges));
    }
    String description(){return enabled?"automatic selection from "+starts+" ("+bank.regularCount()+" regular / "+bank.challengeCount()+" challenges)":"manual selection";}
}
