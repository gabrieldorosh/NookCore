package net.nikosnook.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Advancement presentation, isolated from the standard durable reward listener. */
final class NookMilestones implements Listener {
    interface Gateway {
        boolean exists(MilestoneCatalogue.Definition definition);
        boolean criterionExists(MilestoneCatalogue.Definition definition);
        boolean presentationMatches(MilestoneCatalogue.Definition definition);
        void register(MilestoneCatalogue.Definition definition);
        boolean grant(Player player,MilestoneCatalogue.Definition definition);
    }
    private final List<MilestoneCatalogue.Definition> active=new ArrayList<>();
    private final Set<String> warned=new HashSet<>();
    private final Gateway gateway;
    private final Consumer<String> warning;
    private final BiConsumer<Player,Boolean> celebration;
    private MilestoneCatalogue.Definition root;

    NookMilestones(JavaPlugin plugin){
        gateway=new BukkitGateway();warning=plugin.getLogger()::warning;celebration=QuestCelebrations::play;
        try{
            var file=plugin.getDataFolder().toPath().resolve("advancements.yml");if(!Files.exists(file))plugin.saveResource("advancements.yml",false);
            var catalogue=MilestoneCatalogue.parse(Files.readString(file,StandardCharsets.UTF_8),name->{var material=Material.matchMaterial(name);return material!=null&&material.isItem()&&!material.isAir();});
            install(catalogue);
            plugin.getServer().getPluginManager().registerEvents(this,plugin);
        }catch(Exception e){warning.accept("Custom advancements disabled: "+e.getMessage()+". Existing advancement progress is retained; other NookCore features remain available.");}
    }
    NookMilestones(MilestoneCatalogue catalogue,Gateway gateway,Consumer<String> warning){this(catalogue,gateway,warning,(player,challenge)->{});}
    NookMilestones(MilestoneCatalogue catalogue,Gateway gateway,Consumer<String> warning,BiConsumer<Player,Boolean> celebration){this.gateway=gateway;this.warning=warning;this.celebration=celebration;install(catalogue);}
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
            try{
                gateway.grant(player,root);
                if(gateway.grant(player,definition)){
                    try{celebration.accept(player,definition.event()==MilestoneCatalogue.Event.WEEK_COMPLETE);}
                    catch(RuntimeException e){warn("Could not celebrate "+definition.id()+": "+e.getMessage());}
                }
            }catch(RuntimeException e){warn("Could not grant "+definition.id()+": "+e.getMessage());}
        }
    }
    @EventHandler(priority=EventPriority.NORMAL)
    public void announce(PlayerAdvancementDoneEvent event){
        String id=event.getAdvancement().getKey().toString();
        var definition=active.stream().filter(d->d.id().equals(id)).findFirst().orElse(null);
        if(definition==null)return; // The root and other plugins' advancements keep their own behaviour.
        var player=event.getPlayer();
        if(player.getGameMode()!=GameMode.SURVIVAL || StaffMode.active(player.getUniqueId())){event.message(null);return;}
        // Previously registered cosmetic entries have announce_to_chat=false. Supplying
        // the Paper event message preserves their IDs, criteria and earned progress.
        // HIGH chat formatting and HIGHEST staff suppression still run afterwards.
        if(event.message()==null)event.message(announcement(player,definition));
    }
    static Component announcement(Player player,MilestoneCatalogue.Definition definition){
        var title=Component.text("["+definition.title()+"]",NookUi.PRICE)
            .hoverEvent(Component.text(definition.title()+"\n"+definition.description()));
        return Component.translatable("chat.type.advancement.task",Component.text(player.getName()),title);
    }
    private void warn(String message){if(warned.add(message))warning.accept(message);}
    @SuppressWarnings("deprecation")
    private static final class BukkitGateway implements Gateway {
        private org.bukkit.advancement.Advancement find(MilestoneCatalogue.Definition definition){return Bukkit.getAdvancement(Objects.requireNonNull(NamespacedKey.fromString(definition.id())));}
        public boolean exists(MilestoneCatalogue.Definition definition){return find(definition)!=null;}
        public boolean criterionExists(MilestoneCatalogue.Definition definition){return find(definition).getCriteria().contains(definition.criterion());}
        public boolean presentationMatches(MilestoneCatalogue.Definition definition){
            var display=find(definition).getDisplay();return display!=null&&display.title().equals(Component.text(definition.title()))&&display.description().equals(Component.text(definition.description()))&&display.icon().getType()==Material.valueOf(definition.icon())&&display.doesShowToast()==definition.showToast();
        }
        public void register(MilestoneCatalogue.Definition definition){Bukkit.getUnsafe().loadAdvancement(Objects.requireNonNull(NamespacedKey.fromString(definition.id())),definition.json());}
        public boolean grant(Player player,MilestoneCatalogue.Definition definition){var advancement=find(definition);return advancement!=null&&player.getAdvancementProgress(advancement).awardCriteria(definition.criterion());}
    }
}
