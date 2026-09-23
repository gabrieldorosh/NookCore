package net.nikosnook.core;

import org.junit.jupiter.api.Test;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import static org.junit.jupiter.api.Assertions.*;

class PlayerPresentationTest {
    @Test void hiddenDeathMessageStaysHidden(){assertNull(PlayerPresentation.colourName(null,"Alex",NamedTextColor.AQUA));}
    @Test void namesAreNotReplacedInsideOtherUsernames(){
        var message=Component.text("Alexia watched Alex fall");var result=PlayerPresentation.colourName(message,"Alex",NamedTextColor.AQUA);
        assertEquals("Alexia watched Alex fall",PlainTextComponentSerializer.plainText().serialize(result));
        assertTrue(result.children().stream().anyMatch(c->c instanceof TextComponent t && t.content().equals("Alex") && c.color()==NamedTextColor.AQUA));
    }
    @Test void translationAndItemArgumentsArePreserved(){
        var item=Component.text("Sword").hoverEvent(HoverEvent.showText(Component.text("Enchanted")));
        var original=Component.translatable("death.attack.player.item",Component.text("Alex"),Component.text("Sam"),item);
        var result=(TranslatableComponent)PlayerPresentation.colourName(original,"Alex",NamedTextColor.AQUA);
        assertEquals(original.key(),result.key());assertEquals(NamedTextColor.AQUA,((Component)result.arguments().getFirst().value()).color());
        assertEquals(item,result.arguments().get(2).value());assertEquals(original.arguments().get(1),result.arguments().get(1));
    }
}
