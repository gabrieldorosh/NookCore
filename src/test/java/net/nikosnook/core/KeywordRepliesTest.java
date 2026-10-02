package net.nikosnook.core;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeywordRepliesTest {
    private YamlConfiguration config(){var c=new YamlConfiguration();c.set("enabled",true);c.set("topics.lag.phrases",List.of("lag","laggy","lagging"));c.set("topics.lag.reply","Lag help");c.set("topics.staff.phrases",List.of("admin","staff"));c.set("topics.staff.reply","Staff help");return c;}
    @Test void wholeWordsCaseAndPunctuation(){var r=new KeywordReplies(config());UUID id=UUID.randomUUID();for(String text:List.of("flag","flagging","administer","lag_machine","élag","lag2"))assertTrue(r.reply(id,text,0).isEmpty(),text);assertEquals("Lag help",r.reply(id,"LAG!",0).orElseThrow());}
    @Test void cooldownIsPerPlayerAndPerTopic(){var r=new KeywordReplies(config());UUID a=UUID.randomUUID(),b=UUID.randomUUID();assertTrue(r.reply(a,"lag",0).isPresent());assertTrue(r.reply(a,"lag",299999).isEmpty());assertTrue(r.reply(b,"lag",1).isPresent());assertEquals("Staff help",r.reply(a,"admin",10000).orElseThrow());assertTrue(r.reply(a,"lag",300000).isPresent());}
    @Test void oneReplyPerMessageAndShortGlobalCooldown(){var r=new KeywordReplies(config());UUID a=UUID.randomUUID();assertEquals("Lag help",r.reply(a,"admin lag",0).orElseThrow());assertTrue(r.reply(a,"admin",9999).isEmpty());assertEquals("Staff help",r.reply(a,"admin lag",10000).orElseThrow());}
    @Test void literalPhrasesDoNotBecomeRegex(){var c=config();c.set("topics.lag.phrases",List.of("a+b","[lag]"));var r=new KeywordReplies(c);UUID a=UUID.randomUUID();assertTrue(r.reply(a,"aaab",0).isEmpty());assertTrue(r.reply(a,"a+b",0).isPresent());}
    @Test void disabledOrAbsentDoesNothing(){var c=config();c.set("enabled",false);assertTrue(new KeywordReplies(c).reply(UUID.randomUUID(),"lag",0).isEmpty());assertTrue(new KeywordReplies(null).reply(UUID.randomUUID(),"lag",0).isEmpty());}
    @Test void disconnectClearsSessionAndBadConfigRejected(){var r=new KeywordReplies(config());UUID a=UUID.randomUUID();r.reply(a,"lag",0);r.forget(a);assertTrue(r.reply(a,"lag",1).isPresent());var c=config();c.set("cooldown-seconds",-1);assertThrows(IllegalArgumentException.class,()->new KeywordReplies(c));var bad=config();bad.set("topics.lag.phrases",List.of(" "));assertThrows(IllegalArgumentException.class,()->new KeywordReplies(bad));}
    @Test void existingBeeTitleAndNewAliasKeepPastelYellow(){for(String key:List.of("mrs-bee","miss-bee")){var c=new YamlConfiguration();UUID id=UUID.randomUUID();c.set(id.toString(),key);var titles=new DisplayTitles(c);String rank=titles.rank(id,"admin");assertEquals("miss bee",ChatStyle.rankLabel(rank));assertEquals("#F1DEA0",ChatStyle.rankColour(rank).asHexString());assertEquals("admin",titles.rank(UUID.randomUUID(),"admin"));}}
}
