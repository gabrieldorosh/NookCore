package net.nikosnook.core;
import org.bukkit.configuration.file.YamlConfiguration;
record WordleSettings(boolean enabled,long reward) {
    static WordleSettings parse(YamlConfiguration config){
        for(String key:config.getKeys(false))if(!java.util.Set.of("enabled","reward-cents").contains(key))throw new IllegalArgumentException("Unknown wordle.yml setting: "+key);
        if(!config.isBoolean("enabled") || !config.isLong("reward-cents") && !config.isInt("reward-cents"))throw new IllegalArgumentException("wordle.yml needs enabled: true/false and integer reward-cents.");
        long reward=config.getLong("reward-cents");if(reward<0 || reward>Money.MAX)throw new IllegalArgumentException("Wordle reward-cents is out of range.");
        return new WordleSettings(config.getBoolean("enabled"),reward);
    }
}
