package net.nikosnook.core;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class PlotLeaveTest {
    @TempDir Path dir;
    @Test void coownerCanLeaveAndRentElsewhereWithoutAffectingOwner()throws Exception {
        try(var s=new NookStore(dir.resolve("nooks.db"))){UUID owner=UUID.randomUUID(),member=UUID.randomUUID();long now=1800000000000L;
            s.join(owner,"Owner",now,6000);s.join(member,"Member",now,6000);s.definePlot("one",3000);s.definePlot("two",3000);s.rent("one",owner,now);
            s.acceptInvitation(s.invite("one",owner,member,"STOCK",now).token(),member,now);s.leavePlot("one",member,now);
            assertEquals("NONE",s.role("one",member));assertEquals(owner,s.plot("one").owner());s.rent("two",member,now);
        }
    }
    @Test void originalRenterCannotOrphanLease()throws Exception {
        try(var s=new NookStore(dir.resolve("nooks.db"))){UUID owner=UUID.randomUUID();s.join(owner,"Owner",1,6000);s.definePlot("one",3000);s.rent("one",owner,1);
            assertThrows(IllegalArgumentException.class,()->s.leavePlot("one",owner,2));assertEquals("OWNER",s.role("one",owner));
        }
    }
}
