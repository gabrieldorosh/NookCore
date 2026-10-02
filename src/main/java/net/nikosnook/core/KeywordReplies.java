package net.nikosnook.core;

import java.util.*;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;

/** Literal, bounded phrase matching. Called only on the server thread. */
final class KeywordReplies {
    private record Rule(String id, Pattern pattern, String reply) {}
    private static final class Seen {
        final Map<String,Long> topics=new HashMap<>();
        Long last;
    }
    private final List<Rule> rules=new ArrayList<>();
    private final Map<UUID,Seen> players=new HashMap<>();
    private final long cooldown;
    KeywordReplies(ConfigurationSection config) {
        cooldown=1000L*(config==null?300:config.getInt("cooldown-seconds",300));
        if(cooldown<10000 || cooldown>86400000)throw new IllegalArgumentException("help-replies cooldown must be 10 to 86400 seconds.");
        if(config==null || !config.getBoolean("enabled",true))return;
        var topics=config.getConfigurationSection("topics");if(topics==null)return;
        if(topics.getKeys(false).size()>32)throw new IllegalArgumentException("At most 32 help topics are supported.");
        for(String id:topics.getKeys(false)) {
            var topic=topics.getConfigurationSection(id);
            if(topic==null)throw new IllegalArgumentException("Invalid help topic: "+id);
            var phrases=topic.getStringList("phrases");String reply=topic.getString("reply","").strip();
            if(phrases.isEmpty() || phrases.size()>32 || reply.isEmpty() || reply.length()>1000)throw new IllegalArgumentException("Invalid phrases or reply for help topic: "+id);
            List<String> escaped=new ArrayList<>();
            for(String phrase:phrases){
                phrase=phrase.strip();
                if(phrase.isEmpty() || phrase.length()>128)throw new IllegalArgumentException("Invalid help phrase in "+id);
                escaped.add(Pattern.quote(phrase));
            }
            rules.add(new Rule(id,Pattern.compile("(?<![\\p{L}\\p{N}_])(?:"+String.join("|",escaped)+")(?![\\p{L}\\p{N}_])",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE),reply));
        }
    }
    Optional<String> reply(UUID id,String message,long now) {
        Seen seen=players.get(id);
        if(seen!=null && seen.last!=null && now-seen.last<10000)return Optional.empty();
        for(Rule rule:rules) {
            if(!rule.pattern.matcher(message).find())continue;
            Long previous=seen==null?null:seen.topics.get(rule.id);
            if(previous!=null && now-previous<cooldown)continue;
            if(seen==null){seen=new Seen();players.put(id,seen);}
            seen.topics.put(rule.id,now);seen.last=now;
            return Optional.of(rule.reply);
        }
        return Optional.empty();
    }
    void forget(UUID id){players.remove(id);}
    void clear(){players.clear();}
}
