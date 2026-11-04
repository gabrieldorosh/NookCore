package net.nikosnook.core;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

final class WordleUi {
    static Component row(WordleStore.Guess guess){
        Component out=Component.empty();String letters=guess.word().toUpperCase(Locale.ROOT);
        for(int i=0;i<5;i++){
            var mark=guess.marks().get(i);TextColor colour=switch(mark){case EXACT->NookUi.GOOD;case PRESENT->NookUi.WARNING;case ABSENT->NookUi.MUTED;};
            out=out.append(Component.text("["+letters.charAt(i)+symbol(mark)+"] ",colour));
        }
        return out;
    }
    static String symbol(WordleScore.Mark mark){return switch(mark){case EXACT->"=";case PRESENT->"~";case ABSENT->"-";};}
    static String share(WordleStore.Board board){
        var result=new StringBuilder("niko's nook wordle · ").append(board.day()).append(" · ")
            .append(board.state()==WordleStore.State.LOST?"X":board.guesses().size()).append("/6");
        for(var guess:board.guesses()){result.append("\n");for(var mark:guess.marks())result.append('[').append(symbol(mark)).append(']');}
        return result.toString();
    }
}
