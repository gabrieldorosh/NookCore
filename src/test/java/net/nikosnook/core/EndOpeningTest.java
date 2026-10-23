package net.nikosnook.core;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EndOpeningTest {
    final String time="2026-10-23T19:00:00+01:00";
    final long boundary=Instant.parse("2026-10-23T18:00:00Z").toEpochMilli();
    @Test void manualLockRemainsCompatible(){assertTrue(EndOpening.parse(true,"").locked(boundary));assertFalse(EndOpening.parse(false,"").locked(boundary));}
    @Test void missingAndBlankSchedulesPreserveLock(){assertTrue(EndOpening.parse(true,null).locked(boundary));assertTrue(EndOpening.parse(true,"  ").locked(boundary));}
    @Test void exactOpeningBoundaryIsInclusive(){var plan=EndOpening.parse(true,time);assertTrue(plan.locked(boundary-1));assertFalse(plan.locked(boundary));assertFalse(plan.locked(boundary+1));}
    @Test void ukSummerTimeMatchesUtc(){assertEquals(EndOpening.parse(true,time),EndOpening.parse(true,"2026-10-23T18:00:00Z"));}
    @Test void serverDefaultZoneDoesNotAffectExplicitOffset(){assertEquals(Instant.parse("2026-10-23T18:00:00Z"),EndOpening.parse(true,time).opensAt());}
    @Test void restartBeforeOpeningKeepsLock(){assertTrue(EndOpening.parse(true,time).locked(boundary-3600000));assertTrue(EndOpening.parse(true,time).locked(boundary-60000));}
    @Test void restartAfterOpeningDoesNotRelock(){assertFalse(EndOpening.parse(true,time).locked(boundary+3600000));assertFalse(EndOpening.parse(true,time).locked(boundary+7*86400000L));}
    @Test void malformedTimestampIsRejected(){assertThrows(IllegalArgumentException.class,()->EndOpening.parse(true,"tomorrow at seven"));}
    @Test void timestampWithoutOffsetIsRejected(){assertThrows(IllegalArgumentException.class,()->EndOpening.parse(true,"2026-10-23T19:00:00"));}
    @Test void invalidCalendarDateIsRejected(){assertThrows(IllegalArgumentException.class,()->EndOpening.parse(true,"2026-02-30T19:00:00+00:00"));}
    @Test void explicitManualUnlockOverridesSchedule(){assertFalse(EndOpening.parse(false,time).locked(boundary-1));assertTrue(EndOpening.parse(false,time).status(boundary-1).contains("lock-end=false"));}
    @Test void cancelScheduleRestoresManualLock(){assertFalse(EndOpening.parse(true,time).locked(boundary+1));assertTrue(EndOpening.parse(true,"").locked(boundary+1));}
    @Test void statusReflectsCurrentClockWithoutMutation(){var plan=EndOpening.parse(true,time);assertTrue(plan.status(boundary-1).contains("LOCKED until 23 Oct 2026, 19:00 BST"));assertTrue(plan.status(boundary).contains("OPEN since"));assertEquals(Instant.ofEpochMilli(boundary),plan.opensAt());}
}
