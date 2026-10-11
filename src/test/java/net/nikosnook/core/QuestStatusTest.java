package net.nikosnook.core;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class QuestStatusTest {
 QuestPlan.Goal goal(String id,int amount){return new QuestPlan.Goal(id,"Mine diamonds","MINE","DIAMOND",amount,1500);}
 @Test void activationIdsAndOrderDoNotProduceFalseMismatch(){assertTrue(QuestPlan.sameDefinitions(List.of(goal("a",8),goal("b",9)),List.of(goal("launch_1",9),goal("launch_0",8))));}
 @Test void changedAmountsAreVisible(){assertFalse(QuestPlan.sameDefinitions(List.of(goal("a",8)),List.of(goal("a",9))));}
 @Test void duplicateDefinitionsStillCount(){assertFalse(QuestPlan.sameDefinitions(List.of(goal("a",8),goal("b",8)),List.of(goal("c",8))));}
}
