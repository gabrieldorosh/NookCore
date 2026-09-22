package net.nikosnook.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;
import java.sql.SQLException;
import java.util.*;

final class NookUi {
    static volatile ChatPreferences preferences;
    static final TextColor BODY=TextColor.color(0xddd6df), ACCENT=TextColor.color(0xd8b4df), COMMAND=TextColor.color(0xf1cfaf), MUTED=TextColor.color(0xaaa2b1), GOOD=TextColor.color(0xb5d9bd), WARNING=TextColor.color(0xe9c49d), BAD=TextColor.color(0xe5adb3);
    static String date(long time){return java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm z",Locale.UK).withZone(java.time.ZoneId.of("Europe/London")).format(java.time.Instant.ofEpochMilli(time));}
    static Component text(String text){return Component.text(text,BODY);}
    static Component name(UUID id,String fallback){return Component.text(fallback,preferences==null?NamedTextColor.WHITE:preferences.get(id).textColour());}
    static Component name(NookStore store,UUID id)throws SQLException {var a=store.account(id);return name(id,a.map(NookStore.Account::name).orElse("Unknown player"));}
    static Component status(NookStore.Plot plot,long now){
        String state=plot.state();if(state.equals("ACTIVE") && now>=plot.paidUntil())state="GRACE";
        return switch(state){case "AVAILABLE"->Component.text("Available to rent",GOOD);case "ACTIVE"->Component.text("Open",GOOD);case "GRACE"->Component.text("Closed · rent overdue",WARNING);default->Component.text("Closed · awaiting clearance",BAD);};
    }
    static Component plot(NookStore store,NookStore.Plot plot)throws SQLException {
        Component result=text(plot.id()).append(Component.text(" · ",MUTED)).append(status(plot,System.currentTimeMillis()));
        if(plot.owner()!=null)result=result.append(Component.text(store.abandoned(plot.id())?" · Former renter: ":" · Owner: ",MUTED)).append(name(store,plot.owner()));
        return plot.owner()==null?result.append(Component.text(" · "+Money.format(plot.weekly())+"/week",COMMAND)):result;
    }
    static void help(CommandSender sender,String heading,String... lines){sender.sendMessage(Component.text(heading,ACCENT).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));for(String line:lines){int split=line.indexOf(" — ");String command=split<0?line:line.substring(0,split);Component row=Component.text("  "+command,COMMAND).clickEvent(ClickEvent.suggestCommand(command));if(split>=0)row=row.append(Component.text(line.substring(split),MUTED));sender.sendMessage(row);}}
    static List<String> complete(String typed,Collection<String> candidates){return candidates.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(typed.toLowerCase(Locale.ROOT))).distinct().sorted().toList();}
}
