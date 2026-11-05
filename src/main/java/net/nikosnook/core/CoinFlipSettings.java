package net.nikosnook.core;

import org.bukkit.configuration.ConfigurationSection;

/** Trial settings. The game stays closed until the owner explicitly enables it. */
record CoinFlipSettings(boolean enabled,long minimum,long maximum,long invitationMillis,long cooldownMillis,int incomingLimit) {
    CoinFlipSettings {
        if(minimum<1 || maximum<minimum || maximum>Money.MAX/2
            || invitationMillis<10_000 || invitationMillis>600_000
            || cooldownMillis<1_000 || cooldownMillis>60_000
            || incomingLimit<1 || incomingLimit>10)throw new IllegalArgumentException("Invalid coin-flip settings.");
    }
    static CoinFlipSettings load(ConfigurationSection config){
        try{return new CoinFlipSettings(config!=null && config.getBoolean("enabled",false),
            integer(config,"minimum-cents",100),integer(config,"maximum-cents",10_000),
            Math.multiplyExact(integer(config,"invitation-seconds",120),1_000),
            Math.multiplyExact(integer(config,"cooldown-seconds",5),1_000),
            Math.toIntExact(integer(config,"incoming-limit",5)));
        }catch(ArithmeticException error){throw new IllegalArgumentException("Coin-flip settings are out of range.");}
    }
    private static long integer(ConfigurationSection config,String key,long fallback){
        Object value=config==null?null:config.get(key);if(value==null)return fallback;
        if(value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte)return ((Number)value).longValue();
        throw new IllegalArgumentException("Use a whole number for coinflip."+key+".");
    }
}
