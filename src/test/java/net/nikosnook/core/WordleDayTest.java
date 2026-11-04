package net.nikosnook.core;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
class WordleDayTest {
 long at(String value){return Instant.parse(value).toEpochMilli();}
 @Test void midnightIsNotTheReset(){assertEquals("2026-11-03",WordleDay.at(at("2026-11-04T01:59:59Z")).id());assertEquals("2026-11-04",WordleDay.at(at("2026-11-04T02:00:00Z")).id());}
 @Test void springBoundaryHasTwentyThreeHours(){var day=WordleDay.at(at("2026-03-28T02:00:00Z"));assertEquals(at("2026-03-29T01:00:00Z"),day.nextReset());assertEquals("2026-03-29",WordleDay.at(day.nextReset()).id());}
 @Test void autumnBoundaryHasTwentyFiveHours(){var day=WordleDay.at(at("2026-10-24T01:00:00Z"));assertEquals(at("2026-10-25T02:00:00Z"),day.nextReset());assertEquals("2026-10-24",WordleDay.at(at("2026-10-25T01:59:59Z")).id());}
}
