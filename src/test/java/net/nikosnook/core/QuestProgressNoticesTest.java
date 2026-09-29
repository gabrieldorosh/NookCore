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
    @Test void firstBlockIsImmediateAndSuppressedBlocksDoNotExtendCooldown(){
        assertTrue(notices.immediate(player,update("coal","MINE",1,false),0));
        for(int n=2;n<=50;n++)assertFalse(notices.immediate(player,update("coal","MINE",n,false),n*100));
        assertFalse(notices.immediate(player,update("coal","MINE",51,false),9999));
        assertTrue(notices.immediate(player,update("coal","MINE",52,false),10000));
        assertFalse(notices.immediate(player,update("coal","MINE",53,false),10001));
        assertTrue(notices.immediate(player,update("coal","MINE",54,false),20000));
    }
    @Test void completionBypassesCooldown(){
        assertTrue(notices.immediate(player,update("coal","MINE",98,false),0));
        assertFalse(notices.immediate(player,update("coal","MINE",99,false),1));
        assertTrue(notices.immediate(player,update("coal","MINE",100,true),2));
    }
    @Test void combatAndFishBurstsAreThrottledButCompletionIsImmediate(){
        for(String kind:List.of("KILL","FISH")){
            assertTrue(notices.immediate(player,update(kind.toLowerCase(Locale.ROOT),kind,1,false),0));
            for(int n=2;n<100;n++)assertFalse(notices.immediate(player,update(kind.toLowerCase(Locale.ROOT),kind,n,false),n));
            assertTrue(notices.immediate(player,update(kind.toLowerCase(Locale.ROOT),kind,100,true),100));
        }
    }
    @Test void otherKindsStayImmediate(){
        assertTrue(notices.immediate(player,update("visit","BIOME",1,false),0));
        assertTrue(notices.immediate(player,update("visit","BIOME",2,false),1));
    }
    @Test void playersAndGoalsHaveIndependentCooldowns(){
        assertTrue(notices.immediate(player,update("coal","MINE",1,false),0));
        assertTrue(notices.immediate(player,update("iron","MINE",2,false),1));
        assertTrue(notices.immediate(UUID.randomUUID(),update("coal","MINE",3,false),1));
        assertFalse(notices.immediate(player,update("iron","MINE",3,false),2));
    }
    @Test void logoutAndRotationClearCooldownsAndLongPausesAllowImmediateUpdate(){
        UUID other=UUID.randomUUID();
        notices.immediate(player,update("coal","MINE",1,false),0);
        notices.immediate(other,update("coal","MINE",1,false),0);
        notices.forget(player);
        assertTrue(notices.immediate(player,update("coal","MINE",2,false),1));
        assertFalse(notices.immediate(other,update("coal","MINE",2,false),1));
        notices.clear();assertTrue(notices.immediate(other,update("coal","MINE",3,false),2));
        assertTrue(notices.immediate(other,update("coal","MINE",4,false),60000));
    }
}
