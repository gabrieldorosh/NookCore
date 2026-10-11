package net.nikosnook.core;
import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;import java.util.*;
class PolishPolicyTest {
 @Test void purchasesResetQuietPeriodAndAggregateActualShares(){var a=SaleNotices.add(null,100,50,true,1000);var b=SaleNotices.add(a,200,100,true,10000);assertEquals(25000,b.due());assertEquals(300,b.total());assertEquals(150,b.share());assertTrue(b.shared());}
 @Test void singleOwnerHasNoShareSuffix(){assertFalse(SaleNotices.add(null,100,100,false,0).shared());}
 @Test void groupedInvitationDoesNotChangeRecipientArguments(){assertArrayEquals(new String[]{"invite","plot","Name","stock"},CommandNavigation.plotArgs(new String[]{"invite","send","plot","Name","stock"}));assertArrayEquals(new String[]{"accept","Name"},CommandNavigation.plotArgs(new String[]{"invite","accept","Name"}));assertArrayEquals(new String[]{"invitations"},CommandNavigation.plotArgs(new String[]{"invite","list"}));}
 @Test void groupedRentPreservesAmount(){assertArrayEquals(new String[]{"prepay","plot","4"},CommandNavigation.plotArgs(new String[]{"rent","prepay","plot","4"}));}
 @Test void oldSavedStaffStateRetainsSilentReturn(){UUID world=UUID.randomUUID();var old=StaffState.Return.decode(world+",1,65,2,0,0,SURVIVAL,false,false,true");assertEquals("legacy",old.presence());assertEquals(world,old.world());assertEquals(old,StaffState.Return.decode(old.encode()));}
 @Test void unknownPresenceCannotLoad(){assertThrows(IllegalArgumentException.class,()->StaffState.Return.decode(UUID.randomUUID()+",1,65,2,0,0,SURVIVAL,false,false,true,broken"));}
}
