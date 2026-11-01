package net.nikosnook.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
class PvpSettingsTest {
    @TempDir Path folder;
    @Test void requiresBothPlayersAndDefaultsOff()throws Exception{
        var s=new PvpSettings(folder.resolve("pvp.properties"),()->100_000L,15_000);var a=UUID.randomUUID();var b=UUID.randomUUID();
        assertFalse(s.enabled(a));assertFalse(s.allows(a,b));s.set(a,true);assertFalse(s.allows(a,b));s.set(b,true);assertTrue(s.allows(a,b));s.set(a,false);assertFalse(s.allows(a,b));assertTrue(s.allows(a,a));
    }
    @Test void preferencesSurviveRestart()throws Exception{
        Path file=folder.resolve("pvp.properties");UUID id=UUID.randomUUID();var s=new PvpSettings(file,()->1L,15_000);s.set(id,true);
        assertTrue(new PvpSettings(file,()->2L,15_000).enabled(id));s.set(id,false);assertFalse(new PvpSettings(file,()->2L,15_000).enabled(id));
    }
    @Test void bothCombatantsRemainLockedUntilBoundary()throws Exception{
        var clock=new AtomicLong(100_000);var s=new PvpSettings(folder.resolve("pvp.properties"),clock::get,15_000);var a=UUID.randomUUID();var b=UUID.randomUUID();s.set(a,true);s.set(b,true);s.combat(a,b);
        clock.set(114_999);assertThrows(IllegalArgumentException.class,()->s.set(a,false));assertThrows(IllegalArgumentException.class,()->s.set(b,false));assertEquals(1,s.secondsRemaining(a));
        clock.set(115_000);s.set(a,false);s.set(b,false);assertFalse(s.enabled(a));assertFalse(s.enabled(b));
    }
    @Test void repeatedDamageExtendsBothLocks()throws Exception{
        var clock=new AtomicLong(100_000);var s=new PvpSettings(folder.resolve("pvp.properties"),clock::get,15_000);var a=UUID.randomUUID();var b=UUID.randomUUID();s.set(a,true);s.set(b,true);s.combat(a,b);clock.set(110_000);s.combat(b,a);clock.set(120_000);assertEquals(5,s.secondsRemaining(a));assertThrows(IllegalArgumentException.class,()->s.set(b,false));
    }
    @Test void deniedHitsDoNotLockOptedOutPlayers()throws Exception{
        var s=new PvpSettings(folder.resolve("pvp.properties"),()->100_000L,15_000);var a=UUID.randomUUID();var b=UUID.randomUUID();s.set(a,true);s.combat(a,b);assertEquals(0,s.secondsRemaining(a));s.set(a,false);
    }
    @Test void malformedPreferencesArePreserved()throws Exception{
        Path file=folder.resolve("pvp.properties");Files.writeString(file,"not-a-uuid=true\n");assertThrows(java.io.IOException.class,()->new PvpSettings(file,()->1L,15_000));assertEquals("not-a-uuid=true\n",Files.readString(file));
    }
    @Test void failedSaveDoesNotEnablePvp()throws Exception{
        Path file=folder.resolve("pvp.properties");var s=new PvpSettings(file,()->1L,15_000);UUID a=UUID.randomUUID();Files.createDirectory(file);assertThrows(java.io.IOException.class,()->s.set(a,true));assertFalse(s.enabled(a));
    }
}
