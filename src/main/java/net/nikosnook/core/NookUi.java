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
    static final TextColor BODY=TextColor.color(0xddd6df), ACCENT=TextColor.color(0xe5b95c), COMMAND=TextColor.color(0x72c7e8), PRICE=TextColor.color(0xb8d8a8), MUTED=TextColor.color(0xaaa2b1), GOOD=TextColor.color(0xb5d9bd), WARNING=TextColor.color(0xe9c49d), BAD=TextColor.color(0xe5adb3);
    static String date(long time){return java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm z",Locale.UK).withZone(java.time.ZoneId.of("Europe/London")).format(java.time.Instant.ofEpochMilli(time));}
    static Component text(String text){
        var matcher=java.util.regex.Pattern.compile("[+-]?₦-?\\d+\\.\\d{2}").matcher(text);
        Component result=Component.empty().color(BODY);int end=0;
        while(matcher.find()){
            result=result.append(Component.text(text.substring(end,matcher.start()))).append(Component.text(matcher.group(),PRICE));end=matcher.end();
        }
        return result.append(Component.text(text.substring(end)));
    }
    static Component command(String command){return command(command,command);}
    static Component command(String label,String suggestion){return Component.text(label,COMMAND).clickEvent(ClickEvent.suggestCommand(suggestion));}
    static Component prefix(String section){return Component.text(section+" » ",ACCENT);}
    static Component message(String section,String message){return prefix(section).append(text(message));}
    static Component problem(String section,String message){return prefix(section).append(error(message));}
    static Component heading(String text){return Component.text(text,ACCENT).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);}
    static String protectionMessage(String configured){
        var text=net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(configured);
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.builder().character('§')
            .hexColors().useUnusualXRepeatedCharacterHexFormat().build().serialize(text);
    }
    static Component error(String message){return Component.text(message,BAD);}
    static Component history(NookStore.Entry entry){
        String description=switch(entry.kind()){
            case "award"->entry.reference().equals("joining")?"Welcome bonus":entry.reference().startsWith("quest:")?"Weekly quest reward":"Advancement reward";
            case "transfer"->entry.delta()<0?"Payment sent to a player":"Payment received from a player";
            case "rent"->"Plot rent · "+entry.reference();
            case "rent-prepay"->"Rent paid in advance · "+entry.reference();
            case "rent-reopen"->"Plot reopened · "+entry.reference();
            case "rent-refund"->"Unused rent refunded · "+entry.reference().split(" lease=",2)[0];
            case "staff"->"Staff adjustment";
            case "integration"->entry.delta()<0?"Payment sent through a plugin":"Payment received through a plugin";
            default->"Balance adjustment";
        };
        Component line=Component.text("• ",MUTED).append(Component.text((entry.delta()>0?"+":"")+Money.format(entry.delta()),entry.delta()<0?WARNING:PRICE))
            .append(text(" · "+description)).append(Component.text(" · "+date(entry.time()),MUTED));
        return line.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
            text("Record #"+entry.id()+"\nType: "+entry.kind()+"\nReference: "+entry.reference())));
    }
    static Component name(UUID id,String fallback){return Component.text(fallback,preferences==null?NamedTextColor.WHITE:preferences.get(id).textColour());}
    static Component name(NookStore store,UUID id)throws SQLException {var a=store.account(id);return name(id,a.map(NookStore.Account::name).orElse("Unknown player"));}
    static Component status(NookStore.Plot plot,long now){
        String state=plot.state();if(state.equals("ACTIVE") && now>=plot.paidUntil())state="GRACE";
        return switch(state){case "AVAILABLE"->Component.text("Available to rent",GOOD);case "ACTIVE"->Component.text("Open",GOOD);case "GRACE"->Component.text("Closed · rent overdue",WARNING);default->Component.text("Closed · awaiting clearance",BAD);};
    }
    static Component overdue(NookStore.Plot plot){
        String command="/nookplots reopen "+plot.id();
        return prefix("NookPlots").append(Component.text(plot.id(),COMMAND))
            .append(text(" is overdue; sales are closed. The original renter can run "))
            .append(Component.text(command,COMMAND).clickEvent(ClickEvent.suggestCommand(command)))
            .append(text(" before "+date(plot.paidUntil()+NookStore.WEEK)+". Only the remaining part of the week is charged."));
    }
    static Component plot(NookStore store,NookStore.Plot plot)throws SQLException {
        Component result=Component.text(store.plotLabel(plot.id()),ACCENT).hoverEvent(Component.text("Plot ID: "+plot.id())).clickEvent(ClickEvent.runCommand("/nookplots info "+plot.id())).append(Component.text(" · ",MUTED)).append(status(plot,System.currentTimeMillis()));
        if(plot.owner()!=null)result=result.append(Component.text(store.abandoned(plot.id())?" · Former renter: ":" · Owner: ",MUTED)).append(name(store,plot.owner()));
        return plot.owner()==null?result.append(Component.text(" · ",MUTED)).append(Component.text(Money.format(plot.weekly())+"/week",PRICE)):result;
    }
    static void help(CommandSender sender,String heading,String... lines){
        sender.sendMessage(Component.empty());
        sender.sendMessage(heading(heading));
        for(String line:lines){
            int split=line.indexOf(" — ");String command=split<0?line:line.substring(0,split);
            int argument=command.indexOf(" <");int optional=command.indexOf(" [");
            if(argument<0 || optional>=0 && optional<argument)argument=optional;
            String suggestion=argument<0?command:command.substring(0,argument)+" ";
            Component row=Component.empty().append(Component.text("  "+command,COMMAND).clickEvent(ClickEvent.suggestCommand(suggestion)));
            if(split>=0)row=row.append(Component.text(line.substring(split),MUTED));
            sender.sendMessage(row);
        }
    }
    static List<String> complete(String typed,Collection<String> candidates){return candidates.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(typed.toLowerCase(Locale.ROOT))).distinct().sorted().toList();}
}
