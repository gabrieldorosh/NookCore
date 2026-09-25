package net.nikosnook.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RentalOpeningTest {
 @Test void ukLaunchDeadlineIsAbsoluteAndDoesNotMoveOnRestart(){long opens=RentalOpening.parse("2026-09-26T19:00:00+01:00");assertEquals(java.time.Instant.parse("2026-09-26T18:00:00Z").toEpochMilli(),opens);assertEquals(opens,RentalOpening.parse("2026-09-26T19:00:00+01:00"));assertDoesNotThrow(()->RentalOpening.check(opens,opens));}
 @Test void countdownRoundsUpAndRejectsUntilExactBoundary(){long opens=100000;assertTrue(assertThrows(IllegalArgumentException.class,()->RentalOpening.check(opens,opens-1)).getMessage().contains("0h 0m 1s"));assertTrue(assertThrows(IllegalArgumentException.class,()->RentalOpening.check(opens,opens-86400000)).getMessage().contains("24h 0m 0s"));}
 @Test void blankDisablesAndInvalidOrUnzonedDatesFail(){assertEquals(0,RentalOpening.parse(""));assertThrows(IllegalArgumentException.class,()->RentalOpening.parse("tomorrow"));assertThrows(IllegalArgumentException.class,()->RentalOpening.parse("2026-09-26T19:00:00"));}
}
