package net.nikosnook.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AfkNoticesTest {
    private final AfkNotices.State active=new AfkNotices.State(false,false),away=new AfkNotices.State(true,false);
    @Test void initialJoinDoesNotAnnounceAReturn(){assertNull(AfkNotices.message("Player",null,active));assertNull(AfkNotices.message("Player",null,away));}
    @Test void repeatedIdlePollingDoesNotRepeatTheAnnouncement(){assertNull(AfkNotices.message("Player",active,active));assertNull(AfkNotices.message("Player",away,away));}
    @Test void enteringHiddenStaffModeDoesNotAnnounceGoingAfk(){assertNull(AfkNotices.message("Player",active,new AfkNotices.State(true,true)));}
    @Test void leavingHiddenStaffModeDoesNotAnnounceReturning(){assertNull(AfkNotices.message("Player",new AfkNotices.State(true,true),active));}
    @Test void activityChangesWhileHiddenStayPrivate(){assertNull(AfkNotices.message("Player",new AfkNotices.State(false,true),new AfkNotices.State(true,true)));assertNull(AfkNotices.message("Player",new AfkNotices.State(true,true),new AfkNotices.State(false,true)));}
    @Test void visibleTransitionsProduceDistinctNotices(){String leaving=AfkNotices.message("Player",active,away),returning=AfkNotices.message("Player",away,active);assertNotNull(leaving);assertNotNull(returning);assertNotEquals(leaving,returning);}
}
