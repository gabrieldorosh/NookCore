package net.nikosnook.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.TextColor;
import java.util.regex.Pattern;

final class PlayerPresentation {
    static Component colourName(Component message,String name,TextColor colour){
        if(message==null)return null;
        return message.replaceText(TextReplacementConfig.builder()
            .match(Pattern.compile("(?<![A-Za-z0-9_])"+Pattern.quote(name)+"(?![A-Za-z0-9_])"))
            .replacement((match,text)->text.color(colour)).build());
    }
}
