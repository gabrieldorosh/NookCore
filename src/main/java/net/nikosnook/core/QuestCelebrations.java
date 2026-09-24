package net.nikosnook.core;

import org.bukkit.*;
import org.bukkit.entity.Player;

final class QuestCelebrations {
    static void play(Player player,boolean challenge){
        var at=player.getEyeLocation().add(player.getLocation().getDirection().multiply(1.1));
        // Nearby players share the celebration; no explosive entities or world changes.
        player.getWorld().spawnParticle(Particle.FIREWORK,at,challenge?70:55,0.65,0.6,0.65,0.06);
        player.getWorld().spawnParticle(Particle.DUST,at,challenge?45:35,0.7,0.7,0.7,0,new Particle.DustOptions(Color.fromRGB(challenge?216:184,challenge?180:216,challenge?223:168),1.1f));
        player.getWorld().playSound(at,challenge?Sound.UI_TOAST_CHALLENGE_COMPLETE:Sound.ENTITY_PLAYER_LEVELUP,challenge?0.45f:0.75f,challenge?1.2f:1.4f);
    }
}
