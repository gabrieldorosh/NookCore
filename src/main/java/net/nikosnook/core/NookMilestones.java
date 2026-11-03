package net.nikosnook.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Cosmetic milestones, isolated from economy availability and monetary rewards. */
final class NookMilestones {
    interface Gateway {
        boolean exists(MilestoneCatalogue.Definition definition);
        boolean criterionExists(MilestoneCatalogue.Definition definition);
        boolean presentationMatches(MilestoneCatalogue.Definition definition);
        void register(MilestoneCatalogue.Definition definition);
        void grant(Player player,MilestoneCatalogue.Definition definition);
    }
    private final List<MilestoneCatalogue.Definition> active=new ArrayList<>();
    private final Set<String> warned=new HashSet<>();
    private final Gateway gateway;
    private final Consumer<String> warning;
    private MilestoneCatalogue.Definition root;

    NookMilestones(JavaPlugin plugin){
        gateway=new BukkitGateway();warning=plugin.getLogger()::warning;
        try{
            var file=plugin.getDataFolder().toPath().resolve("advancements.yml");if(!Files.exists(file))plugin.saveResource("advancements.yml",false);
            var catalogue=MilestoneCatalogue.parse(Files.readString(file,StandardCharsets.UTF_8),name->{var material=Material.matchMaterial(name);return material!=null&&material.isItem()&&!material.isAir();});
            install(catalogue);
        }catch(Exception e){warning.accept("Custom advancements disabled: "+e.getMessage()+". Existing advancement progress is retained; other NookCore features remain available.");}
    }
    NookMilestones(MilestoneCatalogue catalogue,Gateway gateway,Consumer<String> warning){this.gateway=gateway;this.warning=warning;install(catalogue);}
    private void install(MilestoneCatalogue catalogue){
        if(!catalogue.enabled())return;
        if(!ensure(catalogue.root()))return;root=catalogue.root();
        for(var definition:catalogue.milestones())if(definition.enabled()&&ensure(definition))active.add(definition);
    }
    private boolean ensure(MilestoneCatalogue.Definition definition){
        try{
            if(!gateway.exists(definition)){gateway.register(definition);if(!gateway.exists(definition))throw new IllegalStateException("registration returned no advancement");}
            if(!gateway.criterionExists(definition)){warn(definition.id()+" has incompatible saved criteria; its definition/progress was retained and new grants are disabled.");return false;}
            if(!gateway.presentationMatches(definition))warn(definition.id()+" already has a different installed presentation. Existing title/icon/description were retained to preserve progress; changing these requires a reviewed definition migration. Enabled/event settings still apply.");
            return true;
        }catch(RuntimeException e){warn("Could not register "+definition.id()+": "+e.getMessage());return false;}
    }
    void selectedQuests(Player player){trigger("QUESTS_CHOSEN",player);}
    void trigger(String event,Player player){
        if(root==null||player==null)return;
        for(var definition:active)if(definition.event().name().equals(event)){
            try{gateway.grant(player,root);gateway.grant(player,definition);}catch(RuntimeException e){warn("Could not grant "+definition.id()+": "+e.getMessage());}
        }
    }
    private void warn(String message){if(warned.add(message))warning.accept(message);}
    @SuppressWarnings("deprecation")
    private static final class BukkitGateway implements Gateway {
        private org.bukkit.advancement.Advancement find(MilestoneCatalogue.Definition definition){return Bukkit.getAdvancement(Objects.requireNonNull(NamespacedKey.fromString(definition.id())));}
        public boolean exists(MilestoneCatalogue.Definition definition){return find(definition)!=null;}
        public boolean criterionExists(MilestoneCatalogue.Definition definition){return find(definition).getCriteria().contains(definition.criterion());}
        public boolean presentationMatches(MilestoneCatalogue.Definition definition){
            var display=find(definition).getDisplay();return display!=null&&display.title().equals(Component.text(definition.title()))&&display.description().equals(Component.text(definition.description()))&&display.icon().getType()==Material.valueOf(definition.icon())&&display.doesShowToast()==definition.showToast()&&!display.doesAnnounceToChat();
        }
        public void register(MilestoneCatalogue.Definition definition){Bukkit.getUnsafe().loadAdvancement(Objects.requireNonNull(NamespacedKey.fromString(definition.id())),definition.json());}
        public void grant(Player player,MilestoneCatalogue.Definition definition){var advancement=find(definition);if(advancement!=null)player.getAdvancementProgress(advancement).awardCriteria(definition.criterion());}
    }
}
