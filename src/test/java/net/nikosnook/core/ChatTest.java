package net.nikosnook.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import static org.junit.jupiter.api.Assertions.*;

class ChatTest {
    @Test void pronounChoicesRejectRankLabelsAndAllowConfiguredOptions(){
        for(String text:new String[]{"admin","that/guy"})assertThrows(IllegalArgumentException.class,()->NookChat.selectedPronouns(text,NookChat.PRONOUNS));
        assertEquals("he/him",NookChat.selectedPronouns("He/Him",NookChat.PRONOUNS));
        assertEquals("",NookChat.selectedPronouns("clear",NookChat.PRONOUNS));
        assertEquals("ze/zir",NookChat.selectedPronouns("ze/zir",java.util.List.of("ze/zir")));
    }
    @Test void completionsIgnoreCaseAndDoNotReturnUnrelatedOptions(){
        assertEquals(java.util.List.of("pronouns"),NookUi.complete("PR",java.util.List.of("colour","pronouns","help")));
    }
    @TempDir Path dir;
    private String plain(Component c){return PlainTextComponentSerializer.plainText().serialize(c);}
    @Test void preferencesSurviveRestartAndRemainAccountScoped()throws Exception {
        Path path=dir.resolve("chat.properties");UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        var store=new ChatPreferences(path);store.set(a,new ChatPreferences.Preference("#AABBDD","he/him"));
        var restored=new ChatPreferences(path);
        assertEquals(new ChatPreferences.Preference("#aabbdd","he/him"),restored.get(a));
        assertEquals(ChatPreferences.DEFAULT,restored.get(b));
    }
    @Test void rejectsMalformedColoursAndFormattingInPronouns(){
        for(String colour:new String[]{"<red>","#ff00", "&c", "admin"})assertThrows(IllegalArgumentException.class,()->new ChatPreferences.Preference(colour,""));
        for(String pronouns:new String[]{"he/him\nadmin", "<red>she/her", "&lthey/them", "a".repeat(33),"he\u202Ehim", "\uE001"})assertThrows(IllegalArgumentException.class,()->new ChatPreferences.Preference("white",pronouns));
    }
    @Test void supportsClearAndUnicodeLetters(){
        assertEquals("",new ChatPreferences.Preference("aqua"," ").pronouns());
        assertEquals("él/elle",new ChatPreferences.Preference("aqua","él/elle").pronouns());
    }
    @Test void failedSaveLeavesPreviousPreferencesInMemory()throws Exception {
        Path path=dir.resolve("preferences");var store=new ChatPreferences(path);UUID id=UUID.randomUUID();
        Files.createDirectory(path);Files.writeString(path.resolve("block"),"preserve");
        assertThrows(java.io.IOException.class,()->store.set(id,new ChatPreferences.Preference("red","he/him")));
        assertEquals(ChatPreferences.DEFAULT,store.get(id));
        assertEquals("preserve",Files.readString(path.resolve("block")));
    }
    @Test void corruptPreferenceFileIsPreserved()throws Exception {
        Path path=dir.resolve("preferences");String data=UUID.randomUUID()+".colour=<red>";Files.writeString(path,data);
        assertThrows(java.io.IOException.class,()->new ChatPreferences(path));assertEquals(data,Files.readString(path));
    }
    @Test void onlyAllowedStylesAreParsed(){
        Component c=ChatStyle.message("&lBold&r normal &oitalic &nunderlined &mstruck &aGreen &#aabbccHex");
        assertEquals("Bold normal italic underlined struck Green Hex",plain(c));
        assertEquals(TextDecoration.State.TRUE,c.children().get(1).decoration(TextDecoration.BOLD));
        assertEquals(NamedTextColor.GREEN,c.children().get(6).color());
        assertEquals(TextColor.fromHexString("#aabbcc"),c.children().get(7).color());
    }
    @Test void commandAndFontTagsRemainLiteral(){
        String input="<click:run_command:'/op me'><font:custom:rank>hello</font></click> &khidden";
        Component c=ChatStyle.message(input);assertEquals(input,plain(c));
        assertNull(c.clickEvent());for(Component child:c.children()){assertNull(child.clickEvent());assertNull(child.font());}
    }
    @Test void lineBreaksPrivateGlyphsAndDirectionOverridesCannotForgePrefix(){
        assertEquals("hello [admin]   test",plain(ChatStyle.message("hello\n[admin]\uE001\u202E test")));
    }
    @Test void prefixIsSeparateFromBodyFormatting(){
        Component c=ChatStyle.render("nookling","DrunkeUnicorn",new ChatPreferences.Preference("aqua","he/him"),ChatStyle.message("&lHello!"));
        assertEquals("[nookling] DrunkeUnicorn · he/him: Hello!",plain(c));
        assertNotEquals(TextDecoration.State.TRUE,c.decoration(TextDecoration.BOLD));
    }
    @Test void absentPronounsLeaveNoExtraSpace(){
        assertEquals("[supporter] Alex: Hello",plain(ChatStyle.render("supporter","Alex",ChatPreferences.DEFAULT,ChatStyle.message("Hello"))));
    }
    @Test void cosmeticRanksDoNotConsultOperationalPermissions(){
        assertEquals("[Mr. Niko] DrunkeUnicorn: Hello",plain(ChatStyle.render("owner","DrunkeUnicorn",ChatPreferences.DEFAULT,ChatStyle.message("Hello"))));
        assertEquals("owner",NookChat.displayRank(node->{assertTrue(node.startsWith("nookcore.chat.rank."));return node.endsWith("owner") || node.endsWith("supporter");}));
        assertEquals("supporter",NookChat.displayRank(node->node.equals("nookcore.chat.rank.supporter")));
        assertEquals("nookling",NookChat.displayRank(node->node.equals("nookcore.admin")));
    }
}
