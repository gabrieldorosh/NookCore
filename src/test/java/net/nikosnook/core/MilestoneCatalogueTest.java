package net.nikosnook.core;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import static org.junit.jupiter.api.Assertions.*;

class MilestoneCatalogueTest {
    static final Predicate<String> ITEMS=Set.of("BOOK","WRITTEN_BOOK","SPYGLASS","CHEST","EMERALD","PAPER","SUNFLOWER")::contains;
    static String defaults()throws Exception{try(var in=MilestoneCatalogueTest.class.getResourceAsStream("/advancements.yml")){assertNotNull(in);return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
    static MilestoneCatalogue parse(String text){return MilestoneCatalogue.parse(text,ITEMS);}
    static final class Memory implements NookMilestones.Gateway {
        final Map<String,MilestoneCatalogue.Definition> installed=new LinkedHashMap<>();final Set<String> grants=new HashSet<>();final Set<String> incompatible=new HashSet<>();String failRegister="",failGrant="";
        public boolean exists(MilestoneCatalogue.Definition d){return installed.containsKey(d.id());}
        public boolean criterionExists(MilestoneCatalogue.Definition d){return !incompatible.contains(d.id());}
        public boolean presentationMatches(MilestoneCatalogue.Definition d){return installed.get(d.id()).json().equals(d.json());}
        public void register(MilestoneCatalogue.Definition d){if(d.id().equals(failRegister))throw new IllegalStateException("test failure");assertFalse(installed.containsKey(d.id()),"must never overwrite an installed advancement");installed.put(d.id(),d);}
        public boolean grant(Player p,MilestoneCatalogue.Definition d){if(d.id().equals(failGrant))throw new IllegalStateException("test grant failure");return grants.add(d.id()+":"+d.criterion());}
    }
    static Player player(){return (Player)Proxy.newProxyInstance(MilestoneCatalogueTest.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->null);}
    @Test void defaultsContainFourCosmeticEventsAndPreserveLegacyIds()throws Exception{
        var catalogue=parse(defaults());assertTrue(catalogue.enabled());assertEquals(4,catalogue.milestones().size());assertEquals(MilestoneCatalogue.ROOT,catalogue.root().id());
        var choice=catalogue.milestones().getFirst();assertEquals(MilestoneCatalogue.CHOICE,choice.id());assertEquals("chosen",choice.criterion());assertEquals("visit",catalogue.root().criterion());
        assertEquals(Set.of(MilestoneCatalogue.Event.QUESTS_CHOSEN,MilestoneCatalogue.Event.PIP_VISITED,MilestoneCatalogue.Event.COMMUNITY_CONTRIBUTION,MilestoneCatalogue.Event.WEEK_COMPLETE),new HashSet<>(catalogue.milestones().stream().map(MilestoneCatalogue.Definition::event).toList()));
        for(var definition:catalogue.milestones())assertFalse(definition.json().contains("\"rewards\""));
    }
    @Test void separateGameCatalogueUsesStableIdsAndTogetherCataloguesCoverSupportedEvents()throws Exception{
        String yaml;try(var in=MilestoneCatalogueTest.class.getResourceAsStream("/game-advancements.yml")){assertNotNull(in);yaml=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
        var games=parse(yaml);assertTrue(games.enabled());assertEquals(MilestoneCatalogue.ROOT,games.root().id());
        assertEquals(Set.of("nikosnook:games/five_little_letters","nikosnook:games/heads_or_tails"),new HashSet<>(games.milestones().stream().map(MilestoneCatalogue.Definition::id).toList()));
        assertEquals(Set.of(MilestoneCatalogue.Event.WORDLE_SOLVED,MilestoneCatalogue.Event.COIN_FLIP_FINISHED),new HashSet<>(games.milestones().stream().map(MilestoneCatalogue.Definition::event).toList()));
        var all=new HashSet<MilestoneCatalogue.Event>();parse(defaults()).milestones().forEach(d->all.add(d.event()));games.milestones().forEach(d->all.add(d.event()));
        assertEquals(Set.of(MilestoneCatalogue.Event.values()),all);
        var memory=new Memory();new NookMilestones(parse(defaults()),memory,s->{});new NookMilestones(games,memory,s->{});
        assertEquals(7,memory.installed.size());for(var d:games.milestones())assertFalse(d.json().contains("\"rewards\""));
    }
    @Test void generatedJsonIsValidAndEscapesLiteralOwnerText()throws Exception{
        String edited=defaults().replace("title: 'a little choice'","title: 'a \"little\" \\ choice <3'");var definition=parse(edited).milestones().getFirst();
        @SuppressWarnings("unchecked")var json=(Map<String,Object>)new Yaml().load(definition.json());
        @SuppressWarnings("unchecked")var display=(Map<String,Object>)json.get("display");
        assertEquals(Map.of("text","a \"little\" \\ choice <3"),display.get("title"));assertEquals(true,display.get("announce_to_chat"));assertEquals("nikosnook:small_comforts",json.get("parent"));
    }
    @Test void schemaUnknownEventAndInvalidIconAreRejected()throws Exception{
        String yaml=defaults();assertThrows(IllegalArgumentException.class,()->parse(yaml.replace("schema-version: 1","schema-version: 2")));
        assertThrows(IllegalArgumentException.class,()->parse(yaml.replace("event: PIP_VISITED","event: BUY_DIAMONDS")));
        assertThrows(IllegalArgumentException.class,()->parse(yaml.replace("icon: CHEST","icon: DIAMOND_DATABASE")));
    }
    @Test void rewardsCommandsAndIncorrectTypesAreRejected()throws Exception{
        String yaml=defaults();assertThrows(IllegalArgumentException.class,()->parse(yaml.replace("    event: PIP_VISITED","    reward-cents: 500\n    event: PIP_VISITED")));
        assertThrows(IllegalArgumentException.class,()->parse(yaml.replace("    event: PIP_VISITED","    commands: ['op player']\n    event: PIP_VISITED")));
        assertThrows(IllegalArgumentException.class,()->parse(yaml.replace("schema-version: 1","schema-version: '1'")));
        assertThrows(IllegalArgumentException.class,()->parse(yaml.replace("enabled: true","enabled: 'true'")));
    }
    @Test void duplicateYamlAndNormalizedIdsAreRejected()throws Exception{
        String yaml=defaults();assertThrows(RuntimeException.class,()->parse(yaml+"\nenabled: false\n"));
        String duplicate="  nikosnook:quests/a_little_choice:\n    title: another\n    description: another\n    icon: BOOK\n    event: QUESTS_CHOSEN\n";
        assertThrows(IllegalArgumentException.class,()->parse(yaml+duplicate));
    }
    @Test void foreignNamespacesAndTraversalIdsAreRejected()throws Exception{
        String yaml=defaults();for(String id:List.of("minecraft:quests/test","../escape","quests/../escape","/absolute","quests//empty","UPPERCASE"))assertThrows(IllegalArgumentException.class,()->parse(yaml.replace("quests/a_little_choice:",id+":")),id);
        assertThrows(IllegalArgumentException.class,()->parse(yaml.replace("quests/a_little_choice:","small_comforts:")));
    }
    @Test void freshRegistrationAndTriggersOnlyGrantRequestedCosmeticMilestone()throws Exception{
        var memory=new Memory();var warnings=new ArrayList<String>();var milestones=new NookMilestones(parse(defaults()),memory,warnings::add);assertEquals(5,memory.installed.size());assertTrue(warnings.isEmpty());
        milestones.trigger("PIP_VISITED",player());assertEquals(Set.of(MilestoneCatalogue.ROOT+":visit","nikosnook:community/just_browsing:complete"),memory.grants);
        milestones.trigger("PIP_VISITED",player());assertEquals(2,memory.grants.size());milestones.trigger("UNKNOWN",player());assertEquals(2,memory.grants.size());
    }
    @Test void unchangedExistingTreeIsNotRecreatedAndLegacyHelperStillWorks()throws Exception{
        var catalogue=parse(defaults());var memory=new Memory();var warnings=new ArrayList<String>();new NookMilestones(catalogue,memory,warnings::add);var original=new LinkedHashMap<>(memory.installed);
        var again=new NookMilestones(catalogue,memory,warnings::add);again.selectedQuests(player());assertEquals(original,memory.installed);assertTrue(warnings.isEmpty());assertTrue(memory.grants.contains(MilestoneCatalogue.CHOICE+":chosen"));
    }
    @Test void editedExistingPresentationIsRetainedAndWarnedWithoutLosingProgress()throws Exception{
        var memory=new Memory();var warnings=new ArrayList<String>();new NookMilestones(parse(defaults()),memory,warnings::add).selectedQuests(player());
        var original=memory.installed.get(MilestoneCatalogue.CHOICE);var edited=parse(defaults().replace("title: 'a little choice'","title: 'new title'"));new NookMilestones(edited,memory,warnings::add).selectedQuests(player());
        assertSame(original,memory.installed.get(MilestoneCatalogue.CHOICE));assertTrue(memory.grants.contains(MilestoneCatalogue.CHOICE+":chosen"));assertEquals(1,warnings.size());assertTrue(warnings.getFirst().contains("retained"));
    }
    @Test void disabledMilestonesDoNotEraseExistingProgressOrUnlockAgain()throws Exception{
        var memory=new Memory();var warnings=new ArrayList<String>();new NookMilestones(parse(defaults()),memory,warnings::add).selectedQuests(player());var old=new HashSet<>(memory.grants);
        var disabled=new NookMilestones(parse(defaults().replace("enabled: true","enabled: false")),memory,warnings::add);disabled.trigger("WEEK_COMPLETE",player());assertEquals(old,memory.grants);assertEquals(5,memory.installed.size());
    }
    @Test void incompatibleCriteriaAndRegistrationFailuresRemainIsolated()throws Exception{
        var memory=new Memory();var warnings=new ArrayList<String>();memory.failRegister="nikosnook:community/just_browsing";memory.incompatible.add(MilestoneCatalogue.CHOICE);
        var milestones=new NookMilestones(parse(defaults()),memory,warnings::add);milestones.selectedQuests(player());assertFalse(memory.grants.contains(MilestoneCatalogue.CHOICE+":chosen"));
        milestones.trigger("COMMUNITY_CONTRIBUTION",player());assertTrue(memory.grants.contains("nikosnook:community/a_helping_hand:complete"));assertEquals(2,warnings.size());
    }
    static Player namedPlayer(GameMode mode){return (Player)Proxy.newProxyInstance(MilestoneCatalogueTest.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){case "getName"->"DrunkeUnicorn";case "getUniqueId"->UUID.fromString("27572a99-54f0-40f0-9e6e-8553e20e4930");case "getGameMode"->mode;default->null;});}
    static PlayerAdvancementDoneEvent event(Player player,String id,Component message){
        var advancement=(Advancement)Proxy.newProxyInstance(MilestoneCatalogueTest.class.getClassLoader(),new Class<?>[]{Advancement.class},(p,m,a)->m.getName().equals("getKey")?NamespacedKey.fromString(id):null);
        return new PlayerAdvancementDoneEvent(player,advancement,message);
    }
    @Test void onlyNewMilestonesCelebrateAndCompletionUsesChallengeEffect()throws Exception{
        var memory=new Memory();var warnings=new ArrayList<String>();var effects=new ArrayList<Boolean>();
        var milestones=new NookMilestones(parse(defaults()),memory,warnings::add,(p,challenge)->effects.add(challenge));
        milestones.selectedQuests(player());milestones.selectedQuests(player());milestones.trigger("PIP_VISITED",player());milestones.trigger("COMMUNITY_CONTRIBUTION",player());milestones.trigger("WEEK_COMPLETE",player());milestones.trigger("WEEK_COMPLETE",player());
        assertEquals(List.of(false,false,false,true),effects);assertEquals(5,memory.grants.size());assertTrue(warnings.isEmpty());
    }
    @Test void alreadyEarnedCriteriaDoNotCelebrateAfterModuleReconstruction()throws Exception{
        var memory=new Memory();var effects=new ArrayList<Boolean>();var catalogue=parse(defaults());
        new NookMilestones(catalogue,memory,s->{},(p,c)->effects.add(c)).selectedQuests(player());
        new NookMilestones(catalogue,memory,s->{},(p,c)->effects.add(c)).selectedQuests(player());assertEquals(List.of(false),effects);
    }
    @Test void celebrationFailureKeepsGrantAndCannotEscapeIntoQuestPayout()throws Exception{
        var memory=new Memory();var warnings=new ArrayList<String>();var milestones=new NookMilestones(parse(defaults()),memory,warnings::add,(p,c)->{throw new IllegalStateException("particles unavailable");});
        assertDoesNotThrow(()->milestones.trigger("PIP_VISITED",player()));assertDoesNotThrow(()->milestones.trigger("PIP_VISITED",player()));
        assertTrue(memory.grants.contains("nikosnook:community/just_browsing:complete"));assertEquals(1,warnings.size());assertTrue(warnings.getFirst().contains("celebrate"));
    }
    @Test void legacySilentInstalledAdvancementsGetEventAnnouncementWithoutReplacement()throws Exception{
        var memory=new Memory();var milestones=new NookMilestones(parse(defaults()),memory,s->{});var before=new LinkedHashMap<>(memory.installed);
        var event=event(namedPlayer(GameMode.SURVIVAL),MilestoneCatalogue.CHOICE,null);milestones.announce(event);
        assertEquals(NookMilestones.announcement(event.getPlayer(),memory.installed.get(MilestoneCatalogue.CHOICE)),event.message());assertEquals(before,memory.installed);
    }
    @Test void nativeAnnouncementIsNotDuplicatedAndRootForeignAndDisabledStayQuiet()throws Exception{
        var milestones=new NookMilestones(parse(defaults()),new Memory(),s->{});var player=namedPlayer(GameMode.SURVIVAL);var original=Component.text("existing native announcement");
        var nativeEvent=event(player,MilestoneCatalogue.CHOICE,original);milestones.announce(nativeEvent);assertSame(original,nativeEvent.message());
        for(String id:List.of(MilestoneCatalogue.ROOT,"minecraft:story/mine_stone","nikosnook:unknown")){var other=event(player,id,null);milestones.announce(other);assertNull(other.message());}
        var disabled=new NookMilestones(parse(defaults().replace("enabled: true","enabled: false")),new Memory(),s->{});var inactive=event(player,MilestoneCatalogue.CHOICE,null);disabled.announce(inactive);assertNull(inactive.message());
    }
    @Test void nonSurvivalMilestoneAnnouncementIsSuppressedAndRootJsonRemainsSilent()throws Exception{
        var catalogue=parse(defaults());var milestones=new NookMilestones(catalogue,new Memory(),s->{});
        var creative=event(namedPlayer(GameMode.CREATIVE),MilestoneCatalogue.CHOICE,Component.text("native"));milestones.announce(creative);assertNull(creative.message());
        @SuppressWarnings("unchecked")var json=(Map<String,Object>)new Yaml().load(catalogue.root().json());
        @SuppressWarnings("unchecked")var display=(Map<String,Object>)json.get("display");assertEquals(false,display.get("announce_to_chat"));
    }
    @Test void grantFailureCannotEscapeIntoEconomyAndWarningIsNotRepeated()throws Exception{
        var memory=new Memory();var warnings=new ArrayList<String>();memory.failGrant=MilestoneCatalogue.CHOICE;var milestones=new NookMilestones(parse(defaults()),memory,warnings::add);
        assertDoesNotThrow(()->milestones.selectedQuests(player()));assertDoesNotThrow(()->milestones.selectedQuests(player()));assertEquals(1,warnings.size());
    }
}
