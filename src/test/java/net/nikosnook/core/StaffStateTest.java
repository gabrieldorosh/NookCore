package net.nikosnook.core;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class StaffStateTest {
 @TempDir Path dir;
 StaffState.Return point(){return new StaffState.Return(UUID.randomUUID(),1.5,65,-4,15,20,"SURVIVAL",false,false,true);}
 @Test void returnPointSurvivesRestartAndClearsOnlyAfterFinish()throws Exception{Path file=dir.resolve("state");UUID a=UUID.randomUUID(),b=UUID.randomUUID();var state=new StaffState(file);var p=point();state.begin(a,p);state.begin(b,point());var reloaded=new StaffState(file);assertEquals(p,reloaded.get(a));reloaded.finish(a);var again=new StaffState(file);assertNull(again.get(a));assertNotNull(again.get(b));}
 @Test void repeatedEntryCannotReplaceOriginalLocation()throws Exception{var state=new StaffState(dir.resolve("state"));var id=UUID.randomUUID();var original=point();state.begin(id,original);assertThrows(IllegalArgumentException.class,()->state.begin(id,point()));assertEquals(original,state.get(id));}
 @Test void failedSaveDoesNotPublishActiveState()throws Exception{Path blocker=dir.resolve("not-a-directory");Files.writeString(blocker,"keep");var state=new StaffState(blocker.resolve("state"));UUID id=UUID.randomUUID();assertThrows(java.io.IOException.class,()->state.begin(id,point()));assertNull(state.get(id));assertEquals("keep",Files.readString(blocker));}
 @Test void corruptStateIsPreservedForRepair()throws Exception{Path file=dir.resolve("state");Files.writeString(file,"broken=value");assertThrows(java.io.IOException.class,()->new StaffState(file));assertEquals("broken=value",Files.readString(file));assertThrows(IllegalArgumentException.class,()->StaffState.Return.decode(point().encode().replace("false","invalid")));assertThrows(IllegalArgumentException.class,()->new StaffState.Return(UUID.randomUUID(),Double.NaN,1,2,0,0,"SURVIVAL",false,false,true));}
}
