package net.nikosnook.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

final class QuestUi {
    private static final TextColor CHALLENGE=TextColor.color(0xD8B4DF);
    static Component row(QuestPlan.Goal goal,int progress){
        boolean done=progress>=goal.amount(),challenge=goal.reward()==3000;
        return Component.text(done?"✓ ":"• ",done?NookUi.GOOD:NookUi.MUTED)
            .append(Component.text((challenge?"Challenge · ":"")+goal.title(),challenge?CHALLENGE:NookUi.BODY))
            .append(Component.text("  "+progress+"/"+goal.amount(),done?NookUi.GOOD:NookUi.COMMAND))
            .append(Component.text("  ·  ",NookUi.MUTED))
            .append(Component.text(Money.format(goal.reward())+(done?" paid":""),NookUi.PRICE));
    }
    static Component progress(NookStore.QuestUpdate update){
        var goal=update.goal();
        return NookUi.prefix("NookQuests")
            .append(Component.text(goal.title(),goal.reward()==3000?CHALLENGE:NookUi.BODY))
            .append(update.paid()?Component.text(" complete · +"+Money.format(goal.reward()),NookUi.GOOD)
                :Component.text(" · "+update.progress()+"/"+goal.amount(),NookUi.COMMAND));
    }
}
