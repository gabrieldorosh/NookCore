package net.nikosnook.core;

import org.bukkit.*;
import org.bukkit.entity.Player;

final class QuestCelebrations {
    static void play(Player player,boolean challenge){
        var at=player.getLocation().add(0,1.2,0);
        // Client particles only: no explosive entities, damage, fire or item drops.
        player.spawnParticle(Particle.FIREWORK,at,challenge?70:25,0.65,0.6,0.65,0.06);
        player.spawnParticle(Particle.DUST,at,challenge?45:18,0.7,0.7,0.7,0,new Particle.DustOptions(Color.fromRGB(challenge?216:184,challenge?180:216,challenge?223:168),1.1f));
        player.playSound(at,challenge?Sound.UI_TOAST_CHALLENGE_COMPLETE:Sound.ENTITY_EXPERIENCE_ORB_PICKUP,challenge?0.45f:0.55f,challenge?1.2f:1.4f);
    }
}
