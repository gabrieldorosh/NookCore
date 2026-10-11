package net.nikosnook.core;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BuildIdentityTest {
 @Test void absentBuildIsExplicitlyUnrecorded(){var b=BuildIdentity.read(null);assertEquals("unrecorded",b.commit());assertEquals("unrecorded",b.inputs());}
 @Test void unresolvedMavenPropertiesAreNotReportedAsIdentity(){var b=BuildIdentity.read(new ByteArrayInputStream("commit=${nook.commit}\nstate=\n".getBytes(StandardCharsets.UTF_8)));assertEquals("unrecorded",b.commit());assertEquals("unrecorded",b.state());}
 @Test void recordedFieldsSurviveLoading(){var b=BuildIdentity.read(new ByteArrayInputStream("commit=abc123\nstate=dirty\ninputs=def456\nbuilt=2026-10-11T12:00:00Z\n".getBytes(StandardCharsets.UTF_8)));assertEquals("abc123",b.commit());assertEquals("dirty",b.state());assertTrue(b.lines().get(2).contains("def456"));}
}
