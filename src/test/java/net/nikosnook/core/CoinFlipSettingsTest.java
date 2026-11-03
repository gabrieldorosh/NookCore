package net.nikosnook.core;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CoinFlipSettingsTest {
    @Test void defaultGameIsClosedWithSmallPublishedTrialLimits(){
        var settings=CoinFlipSettings.load(null);assertFalse(settings.enabled());assertEquals(100,settings.minimum());assertEquals(10_000,settings.maximum());assertEquals(90_000,settings.invitationMillis());
    }
    @Test void configuredValuesAreValidated(){
        var config=new YamlConfiguration();config.set("enabled",true);config.set("maximum-cents",500);config.set("invitation-seconds",120);
        assertEquals(120_000,CoinFlipSettings.load(config).invitationMillis());assertTrue(CoinFlipSettings.load(config).enabled());
        config.set("minimum-cents",501);assertThrows(IllegalArgumentException.class,()->CoinFlipSettings.load(config));
        assertThrows(IllegalArgumentException.class,()->new CoinFlipSettings(true,100,Money.MAX,90_000,5_000,5));
        assertThrows(IllegalArgumentException.class,()->new CoinFlipSettings(true,100,1_000,1_000,5_000,5));
    }
    @Test void fractionalAndOverflowedSettingsAreRejectedInsteadOfSilentlyChangingTheStake(){
        var config=new YamlConfiguration();config.set("maximum-cents",500.5);
        assertThrows(IllegalArgumentException.class,()->CoinFlipSettings.load(config));
        config.set("maximum-cents",500);config.set("invitation-seconds",Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class,()->CoinFlipSettings.load(config));
    }
}
