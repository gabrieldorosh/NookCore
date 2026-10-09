package net.nikosnook.core;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AfkDisplayTest {
    @Test void fiveMinuteBoundaryAndActivityReset(){
        assertFalse(AfkDisplay.idle(true,300,Duration.ofMillis(299999)));
        assertTrue(AfkDisplay.idle(true,300,Duration.ofSeconds(300)));
        assertTrue(AfkDisplay.idle(true,300,Duration.ofHours(2)));
        assertFalse(AfkDisplay.idle(true,300,Duration.ZERO));
    }
    @Test void disabledMissingAndMisconfiguredTimers(){
        assertFalse(AfkDisplay.idle(false,300,Duration.ofHours(2)));
        assertFalse(AfkDisplay.idle(true,300,null));
        assertFalse(AfkDisplay.idle(true,-1,Duration.ofSeconds(29)));
        assertTrue(AfkDisplay.idle(true,-1,Duration.ofSeconds(30)));
    }
}
