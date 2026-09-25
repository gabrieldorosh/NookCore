package net.nikosnook.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QuestProgressNoticesTest {
    private final QuestProgressNotices notices=new QuestProgressNotices();
    private final UUID player=UUID.randomUUID();
    private NookStore.QuestUpdate update(String id,String kind,int progress,boolean paid){
        return new NookStore.QuestUpdate(new QuestPlan.Goal(id,"Mining",kind,kind.equals("MINE")?"COAL":"COD",100,1500),progress,paid);
    }
    @Test void burstProducesLatestProgressOnceAfterTenSeconds(){
        for(int n=1;n<=50;n++)assertFalse(notices.immediate(player,update("coal","MINE",n,false),n));
        assertTrue(notices.drain(10000).isEmpty());
        var output=notices.drain(10001);
        assertEquals(1,output.size());assertEquals(50,output.getFirst().update().progress());
        assertTrue(notices.drain(20000).isEmpty());
    }
    @Test void completionIsImmediateAndCancelsStaleNotice(){
        notices.immediate(player,update("coal","MINE",99,false),0);
        assertTrue(notices.immediate(player,update("coal","MINE",100,true),1));
        assertTrue(notices.drain(10000).isEmpty());
    }
    @Test void otherKindsStayImmediate(){
        assertTrue(notices.immediate(player,update("fish","FISH",1,false),0));
        assertTrue(notices.drain(10000).isEmpty());
    }
    @Test void PlayersAndGoalsHaveIndependentBatches(){
        notices.immediate(player,update("coal","MINE",1,false),0);
        notices.immediate(player,update("iron","MINE",2,false),1000);
        notices.immediate(UUID.randomUUID(),update("coal","MINE",3,false),0);
        assertEquals(2,notices.drain(10000).size());assertEquals(1,notices.drain(11000).size());
    }
    @Test void QuitAndRotationDiscardOnlyCosmeticPendingMessages(){
        notices.immediate(player,update("coal","MINE",1,false),0);
        notices.immediate(UUID.randomUUID(),update("coal","MINE",3,false),0);
        notices.forget(player);assertEquals(1,notices.drain(10000).size());
        notices.immediate(player,update("coal","MINE",1,false),20000);
        notices.clear();assertTrue(notices.drain(30000).isEmpty());
    }
}
