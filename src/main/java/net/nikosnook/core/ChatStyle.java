package net.nikosnook.core;

import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.*;
import java.util.regex.*;

/** Only explicit colour/decorations are parsed. Never interprets click, font or hover tags. */
final class ChatStyle {
    private static final Pattern CODE=Pattern.compile("&(?i:(#[0-9a-f]{6}|[0-9a-flmnor]))");
    private static final NamedTextColor[] COLOURS={NamedTextColor.BLACK,NamedTextColor.DARK_BLUE,NamedTextColor.DARK_GREEN,NamedTextColor.DARK_AQUA,NamedTextColor.DARK_RED,NamedTextColor.DARK_PURPLE,NamedTextColor.GOLD,NamedTextColor.GRAY,NamedTextColor.DARK_GRAY,NamedTextColor.BLUE,NamedTextColor.GREEN,NamedTextColor.AQUA,NamedTextColor.RED,NamedTextColor.LIGHT_PURPLE,NamedTextColor.YELLOW,NamedTextColor.WHITE};
    static Component message(String raw) {
        StringBuilder clean=new StringBuilder();
        raw.codePoints().forEach(c->clean.appendCodePoint(Character.isISOControl(c) || Character.getType(c)==Character.PRIVATE_USE || Character.getType(c)==Character.FORMAT || c==0x2028 || c==0x2029?' ':c));
        String text=clean.toString();Matcher matcher=CODE.matcher(text);
        Component result=Component.empty();Style style=Style.style(NamedTextColor.WHITE);int start=0;
        while(matcher.find()) {
            result=result.append(Component.text(text.substring(start,matcher.start()),style));
            String code=matcher.group(1).toLowerCase(java.util.Locale.ROOT);
            if(code.startsWith("#"))style=Style.style(TextColor.fromHexString(code));
            else {
                int colour="0123456789abcdef".indexOf(code);
                if(colour>=0)style=Style.style(COLOURS[colour]);
                else style=switch(code){
                    case "l"->style.decorate(TextDecoration.BOLD);
                    case "m"->style.decorate(TextDecoration.STRIKETHROUGH);
                    case "n"->style.decorate(TextDecoration.UNDERLINED);
                    case "o"->style.decorate(TextDecoration.ITALIC);
                    default->Style.style(NamedTextColor.WHITE);
                };
            }
            start=matcher.end();
        }
        return result.append(Component.text(text.substring(start),style));
    }
    static Component render(String rank,String name,ChatPreferences.Preference preference,Component body){
        return render(Component.text("["+rank+"]",rank.equals("nookling")?NookUi.GOOD:NookUi.ACCENT),name,preference,body);
    }
    static Component render(Component rank,String name,ChatPreferences.Preference preference,Component body){
        Component prefix=Component.empty().append(rank).append(Component.text(" "))
            .append(Component.text(name,preference.textColour()));
        if(!preference.pronouns().isEmpty())prefix=prefix.append(Component.text(" · "+preference.pronouns(),NookUi.MUTED));
        return prefix.append(Component.text(": ",NamedTextColor.WHITE)).append(body);
    }
}
