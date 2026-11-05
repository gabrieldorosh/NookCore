package net.nikosnook.core;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

final class WordleUi {
    static final String TITLE="niko's wordle";

    static Component row(WordleStore.Guess guess){
        var row=Component.text();
        String letters=guess.word().toUpperCase(Locale.ROOT);
        for(int i=0;i<letters.length();i++){
            if(i>0)row.append(Component.space());
            row.append(Component.text(letters.charAt(i),colour(guess.marks().get(i))));
        }
        return row.build();
    }

    static Component share(String playerName,WordleStore.Board board){
        String score=board.state()==WordleStore.State.LOST?"X":Integer.toString(board.guesses().size());
        var result=Component.text()
            .append(NookUi.heading(TITLE))
            .append(Component.text(" · "+playerName+" · "+board.day()+" · "+score+"/6",NookUi.BODY));
        for(var guess:board.guesses()){
            result.append(Component.newline());
            for(var mark:guess.marks())result.append(Component.text("\u25A0",colour(mark)));
        }
        return result.build();
    }

    private static NamedTextColor colour(WordleScore.Mark mark){
        return switch(mark){
            case EXACT -> NamedTextColor.GREEN;
            case PRESENT -> NamedTextColor.YELLOW;
            case ABSENT -> NamedTextColor.GRAY;
        };
    }
}
