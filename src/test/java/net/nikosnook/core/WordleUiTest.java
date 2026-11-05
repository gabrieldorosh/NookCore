package net.nikosnook.core;

import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WordleUiTest {
    private static String plain(Component component){
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test void privateRowsUseLetterColoursWithoutMarkers(){
        var guess=new WordleStore.Guess("sheep",WordleScore.score("weave","sheep"));
        var row=WordleUi.row(guess);
        assertEquals("S H E E P",plain(row));
        var letters=row.children().stream().filter(child->!((TextComponent)child).content().isBlank()).toList();
        assertEquals(List.of(NamedTextColor.GRAY,NamedTextColor.GRAY,NamedTextColor.YELLOW,NamedTextColor.YELLOW,NamedTextColor.GRAY),
            letters.stream().map(Component::color).toList());
    }

    @Test void sharedResultIncludesOnlyColouredSquaresAfterItsHeader(){
        var first=new WordleStore.Guess("crane",WordleScore.score("apple","crane"));
        var last=new WordleStore.Guess("apple",WordleScore.score("apple","apple"));
        var board=new WordleStore.Board("2026-11-04",1,List.of(first,last),WordleStore.State.WON,100,true,"apple");
        var share=WordleUi.share("Alice",board);
        String squares="\u25A0".repeat(5);
        assertEquals("niko's wordle · Alice · 2026-11-04 · 2/6\n"+squares+"\n"+squares,plain(share));
        var cells=share.children().stream().filter(child->((TextComponent)child).content().equals("\u25A0")).toList();
        assertEquals(List.of(NamedTextColor.GRAY,NamedTextColor.GRAY,NamedTextColor.YELLOW,NamedTextColor.GRAY,NamedTextColor.GREEN,
            NamedTextColor.GREEN,NamedTextColor.GREEN,NamedTextColor.GREEN,NamedTextColor.GREEN,NamedTextColor.GREEN),
            cells.stream().map(Component::color).toList());
    }

    @Test void lostSharesReportXWithoutRevealingTheAnswer(){
        var guess=new WordleStore.Guess("crane",WordleScore.score("apple","crane"));
        var board=new WordleStore.Board("2026-11-04",1,Collections.nCopies(6,guess),WordleStore.State.LOST,100,false,"apple");
        assertEquals("niko's wordle · Alice · 2026-11-04 · X/6"+("\n"+"\u25A0".repeat(5)).repeat(6),plain(WordleUi.share("Alice",board)));
    }

    @Test void settingsAreExplicitAndPreserveZeroReward()throws Exception{
        var config=new YamlConfiguration();
        config.loadFromString("enabled: false\nreward-cents: 0\n");
        assertFalse(WordleSettings.parse(config).enabled());
        assertEquals(0,WordleSettings.parse(config).reward());
        config.set("reward-cents",-1);
        assertThrows(IllegalArgumentException.class,()->WordleSettings.parse(config));
    }
}
