package net.nikosnook.core;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class WordleScoreTest {
 @Test void exactLettersAreReservedBeforeMisplacedDuplicates(){
  assertEquals("21002",WordleScore.encode(WordleScore.score("apple","allee")));
  assertEquals("12011",WordleScore.encode(WordleScore.score("belle","level")));
 }
 @Test void repeatedGuessLettersCannotInventExtraAnswerLetters(){assertEquals("00200",WordleScore.encode(WordleScore.score("crane","aaaaa")));}
 @Test void feedbackRoundTripsAndOnlyFiveExactCellsSolve(){
  var marks=WordleScore.score("stone","stone");assertTrue(WordleScore.solved(marks));assertEquals(marks,WordleScore.decode(WordleScore.encode(marks)));
  assertFalse(WordleScore.solved(WordleScore.score("stone","stove")));assertThrows(IllegalArgumentException.class,()->WordleScore.decode("2222"));
 }
 @Test void normalisationUsesPlainFiveLetterAscii(){
  assertEquals("stone",WordleScore.normalize(" STONE "));for(String invalid:List.of("four","stones","caféx","12345","ab cd"))assertThrows(IllegalArgumentException.class,()->WordleScore.normalize(invalid));
 }
}
