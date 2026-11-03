package net.nikosnook.core;
import org.junit.jupiter.api.Test;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import static org.junit.jupiter.api.Assertions.*;
class WordleUiTest {
 @Test void shareContainsFeedbackButNoGuessOrAnswerLetters(){
  var guess=new WordleStore.Guess("crane",WordleScore.score("apple","crane"));
  var board=new WordleStore.Board("2026-11-04",1,List.of(guess),WordleStore.State.ACTIVE,100,false,null);
  String share=WordleUi.share(board);assertTrue(share.contains("2026-11-04"));assertTrue(share.contains("1/6"));assertFalse(share.contains("crane"));assertFalse(share.contains("apple"));assertEquals("[x][x][x][x][x]",share.split("\n")[1].replaceAll("[=~-]","x"));
 }
 @Test void settingsAreExplicitAndPreserveZeroReward()throws Exception{
  var config=new YamlConfiguration();config.loadFromString("enabled: false\nreward-cents: 0\n");assertFalse(WordleSettings.parse(config).enabled());assertEquals(0,WordleSettings.parse(config).reward());
  config.set("reward-cents",-1);assertThrows(IllegalArgumentException.class,()->WordleSettings.parse(config));
 }
}
