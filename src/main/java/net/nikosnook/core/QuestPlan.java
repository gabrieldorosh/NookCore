package net.nikosnook.core;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.nio.charset.StandardCharsets;

final class QuestPlan {
    static final ZoneId ZONE=ZoneId.of("Europe/London");
    record Week(String id,long nextReset) {}
    record Goal(String id,String title,String kind,String target,int amount,long reward) {
        Goal {
            if(!id.matches("[a-z0-9_-]{1,40}") || title.isBlank() || title.length()>100 || !Set.of("KILL","FISH","BIOME").contains(kind)
                || !target.matches("[A-Z_]+") || amount<1 || amount>100000 || reward<1 || reward>Money.MAX)throw new IllegalArgumentException("Invalid quest definition: "+id);
        }
    }
    static Week week(long time){
        var now=Instant.ofEpochMilli(time).atZone(ZONE);
        var start=now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY)).atTime(2,0).atZone(ZONE);
        if(now.isBefore(start))start=start.minusWeeks(1);
        return new Week(start.toLocalDate().toString(),start.plusWeeks(1).toInstant().toEpochMilli());
    }
    static String encode(List<Goal> goals){
        return goals.stream().map(g->String.join("|",g.id(),Base64.getEncoder().encodeToString(g.title().getBytes(StandardCharsets.UTF_8)),g.kind(),g.target(),Integer.toString(g.amount()),Long.toString(g.reward()))).collect(java.util.stream.Collectors.joining("\n"));
    }
    static List<Goal> decode(String text){
        return text.lines().map(line->{String[] p=line.split("\\|",-1);if(p.length!=6)throw new IllegalArgumentException("Invalid stored quest rotation");return new Goal(p[0],new String(Base64.getDecoder().decode(p[1]),StandardCharsets.UTF_8),p[2],p[3],Integer.parseInt(p[4]),Long.parseLong(p[5]));}).toList();
    }
}
