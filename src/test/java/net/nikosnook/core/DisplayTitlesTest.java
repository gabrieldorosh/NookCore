package net.nikosnook.core;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import static org.junit.jupiter.api.Assertions.*;
class DisplayTitlesTest {
    @Test void titleOverridesOnlyTheAssignedAccountAndKeepsNameAndPronouns(){
        var c=new YamlConfiguration();UUID id=UUID.randomUUID();c.set(id.toString(),"mr-duck");
        var titles=new DisplayTitles(c);assertEquals("mr-duck",titles.rank(id,"admin"));
        assertEquals("admin",titles.rank(UUID.randomUUID(),"admin"));
        var rendered=ChatStyle.render(titles.rank(id,"admin"),"qahar0",new ChatPreferences.Preference("aqua","he/him"),Component.text("Hello"));
        assertEquals("[mr. duck] qahar0 · he/him: Hello",PlainTextComponentSerializer.plainText().serialize(rendered));
        assertEquals(net.kyori.adventure.text.format.TextColor.fromHexString("#EF8585"),rendered.children().getFirst().color());
    }
    @Test void rejectsUnrecognisedTitleAndMalformedAccount(){
        var c=new YamlConfiguration();c.set(UUID.randomUUID().toString(),"admin");assertThrows(IllegalArgumentException.class,()->new DisplayTitles(c));
        var d=new YamlConfiguration();d.set("username","mrs-bee");assertThrows(IllegalArgumentException.class,()->new DisplayTitles(d));
    }
}
