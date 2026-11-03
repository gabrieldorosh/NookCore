package net.nikosnook.core;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Cosmetic milestones; monetary rewards remain an explicit configured choice. */
final class NookMilestones {
    private final NamespacedKey root=new NamespacedKey("nikosnook","small_comforts");
    private final NamespacedKey choice=new NamespacedKey("nikosnook","quests/a_little_choice");
    @SuppressWarnings("deprecation")
    NookMilestones(JavaPlugin plugin){
        try{
            if(Bukkit.getAdvancement(root)==null)Bukkit.getUnsafe().loadAdvancement(root,"""
                {"criteria":{"visit":{"trigger":"minecraft:impossible"}},"requirements":[["visit"]],
                 "display":{"icon":{"id":"minecraft:book"},"background":"minecraft:gui/advancements/backgrounds/stone",
                 "title":{"text":"niko's nook"},"description":{"text":"little adventures and small comforts <3"},
                 "announce_to_chat":false,"show_toast":false}}
                """);
            if(Bukkit.getAdvancement(choice)==null)Bukkit.getUnsafe().loadAdvancement(choice,"""
                {"parent":"nikosnook:small_comforts","criteria":{"chosen":{"trigger":"minecraft:impossible"}},"requirements":[["chosen"]],
                 "display":{"icon":{"id":"minecraft:written_book"},"title":{"text":"a little choice"},
                 "description":{"text":"choose your weekly quests with Niko"},"frame":"task","announce_to_chat":false,"show_toast":true}}
                """);
        }catch(RuntimeException e){plugin.getLogger().warning("Could not register Nook milestones: "+e.getMessage());}
    }
    void selectedQuests(Player player){
        var first=Bukkit.getAdvancement(root);var second=Bukkit.getAdvancement(choice);
        if(first!=null)player.getAdvancementProgress(first).awardCriteria("visit");
        if(second!=null)player.getAdvancementProgress(second).awardCriteria("chosen");
    }
}
