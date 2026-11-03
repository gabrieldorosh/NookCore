package net.nikosnook.core;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PipGreetingsTest {
 @Test void visitsArePrivateToEachPlayerAndRapidReopeningDoesNotRepeat(){
  var greetings=new PipGreetings();var a=UUID.randomUUID();var b=UUID.randomUUID();
  assertTrue(greetings.greet(a,"Niko",1000).orElseThrow().contains("hi there, Niko!"));
  assertTrue(greetings.greet(a,"Niko",1001).isEmpty());
  assertTrue(greetings.greet(b,"Pip",1001).isPresent());
  assertTrue(greetings.greet(a,"Niko",300999).isEmpty());
  assertTrue(greetings.greet(a,"Niko",301000).orElseThrow().contains("lovely to see you"));
  assertTrue(greetings.greet(a,"Niko",601000).orElseThrow().contains("a few lovely things"));
 }
}
