package net.nikosnook.core;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WordleWordsTest {
    private InputStream text(String value){return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));}
    @Test void normalisationIsAsciiOnlyAndIndependentOfDefaultLocale(){
        Locale before=Locale.getDefault();
        try{Locale.setDefault(Locale.forLanguageTag("tr"));assertEquals("shine",WordleWords.normalize(" SHINE "));}
        finally{Locale.setDefault(before);}
        for(String word:List.of("four","longer","cafés","ab-cd","abc12","İiiii"))
            assertThrows(IllegalArgumentException.class,()->WordleWords.normalize(word));
        assertThrows(IllegalArgumentException.class,()->WordleWords.normalize(null));
    }
    @Test void everyAnswerMustBeGuessableAndCatalogueCannotHaveDuplicates(){
        assertThrows(IllegalArgumentException.class,()->new WordleWords(List.of("stone"),List.of("apple")));
        assertThrows(IllegalArgumentException.class,()->new WordleWords(List.of("stone","STONE"),List.of("stone")));
        assertThrows(IllegalArgumentException.class,()->new WordleWords(List.of("stone"),List.of("stone","stone")));
        assertThrows(IllegalArgumentException.class,()->new WordleWords(List.of(),List.of("stone")));
    }
    @Test void answerOrderIsDeterministicAndReturnedPoolIsImmutable(){
        var words=new WordleWords(List.of("stone","apple"),List.of("apple","stone","torch"));
        assertEquals(List.of("stone","apple"),words.answers());
        assertThrows(UnsupportedOperationException.class,()->words.answers().add("wheat"));
        assertTrue(words.accepts(" TORCH "));assertFalse(words.accepts(null));assertFalse(words.accepts("xxxxx"));
        assertEquals(2,words.answerCount());assertEquals(3,words.guessCount());
    }
    @Test void utf8InputsAllowBomCommentsAndBlankLines()throws Exception{
        var words=WordleWords.load(text("\uFEFF# answers\n\nSTONE\r\napple\n"),text("# permitted\napple\nstone\ntorch\n"));
        assertEquals(List.of("stone","apple"),words.answers());assertTrue(words.accepts("torch"));
    }
    @Test void malformedListsReportFileAndLineWithoutSilentlyDroppingEntries(){
        var exception=assertThrows(IOException.class,()->WordleWords.load(text("# answer\nlonger\n"),text("stone\n")));
        assertTrue(exception.getMessage().contains("answers.txt line 2"));
        assertThrows(IOException.class,()->WordleWords.load(text("stone\nstone\n"),text("stone\n")));
        assertTrue(assertThrows(IOException.class,()->WordleWords.load(null,text("stone"))).getMessage().contains("answers.txt"));
        assertTrue(assertThrows(IOException.class,()->WordleWords.load(text("stone"),null)).getMessage().contains("guesses.txt"));
    }
    @Test void malformedEncodingAndOversizedFilesAreRejected(){
        assertThrows(IOException.class,()->WordleWords.load(new ByteArrayInputStream(new byte[]{(byte)0xc3,0x28}),text("stone\n")));
        assertThrows(IOException.class,()->WordleWords.load(new ByteArrayInputStream(new byte[1_000_001]),text("stone\n")));
    }
    @Test void bundledPoolIsMixedValidAndFullyGuessable()throws Exception{
        var words=WordleWords.bundled(getClass().getClassLoader());
        assertEquals(377,words.answerCount());assertTrue(words.guessCount()>14_000);
        for(String themed:List.of("allay","blaze","ghast","lapis","torch","stone","wheat","chest","ingot")){
            assertTrue(words.answers().contains(themed));assertTrue(words.accepts(themed));
        }
        for(String ordinary:List.of("quilt","peach","jolly","dough","tulip"))assertTrue(words.answers().contains(ordinary));
        for(String answer:words.answers()){assertTrue(answer.matches("[a-z]{5}"));assertTrue(words.accepts(answer));}
        assertFalse(words.accepts("bitch"));assertFalse(words.answers().contains("shark"));
        assertNotNull(getClass().getResourceAsStream("/wordle/LICENSE-CMUdict.txt"));
    }
}
