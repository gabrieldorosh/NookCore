package net.nikosnook.core;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import static org.junit.jupiter.api.Assertions.*;

class MilestoneCatalogueTest {
    static final Predicate<String> ITEMS=Set.of("BOOK","WRITTEN_BOOK","SPYGLASS","CHEST","EMERALD")::contains;
    static String defaults()throws Exception{try(var in=MilestoneCatalogueTest.class.getResourceAsStream("/advancements.yml")){assertNotNull(in);return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
    static MilestoneCatalogue parse(String text){return MilestoneCatalogue.parse(text,ITEMS);}
    static final class Memory implements NookMilestones.Gateway {
        final Map<String,MilestoneCatalogue.Definition> installed=new LinkedHashMap<>();final Set<String> grants=new HashSet<>();final Set<String> incompatible=new HashSet<>();String failRegister="",failGrant="";
        public boolean exists(MilestoneCatalogue.Definition d){return installed.containsKey(d.id());}
        public boolean criterionExists(MilestoneCatalogue.Definition d){return !incompatible.contains(d.id());}
        public boolean presentationMatches(MilestoneCatalogue.Definition d){return installed.get(d.id()).json().equals(d.json());}
        public void register(MilestoneCatalogue.Definition d){if(d.id().equals(failRegister))throw new IllegalStateException("test failure");assertFalse(installed.containsKey(d.id()),"must never overwrite an installed advancement");installed.put(d.id(),d);}
        public void grant(Player p,MilestoneCatalogue.Definition d){if(d.id().equals(failGrant))throw new IllegalStateException("test grant failure");grants.add(d.id()+":"+d.criterion());}
    }
    static Player player(){return (Player)Proxy.newProxyInstance(MilestoneCatalogueTest.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->null);}
    @Test void defaultsContainFourCosmeticEventsAndPreserveLegacyIds()throws Exception{
        var catalogue=parse(defaults());assertTrue(catalogue.enabled());assertEquals(4,catalogue.milestones().size());assertEquals(MilestoneCatalogue.ROOT,catalogue.root().id());
        var choice=catalogue.milestones().getFirst();assertEquals(MilestoneCatalogue.CHOICE,choice.id());assertEquals("chosen",choice.criterion());assertEquals("visit",catalogue.root().criterion());
        assertEquals(Set.of(MilestoneCatalogue.Event.values()),new HashSet<>(catalogue.milestones().stream().map(MilestoneCatalogue.Definition::event).toList()));
        for(var definition:catalogue.milestones())assertFalse(definition.json().contains("\"rewards\""));
    }
    @Test void generatedJsonIsValidAndEscapesLiteralOwnerText()throws Exception{
        String edited=defaults().replace("title: 'a little choice'","title: 'a \"little\" \\ choice <3'");var definition=parse(edited).milestones().getFirst();
        @SuppressWarnings("unchecked")var json=(Map<String,Object>)new Yaml().load(definition.json());
        @SuppressWarnings("unchecked")var display=(Map<String,Object>)json.get("display");
        assertEquals(Map.of("text","a \"little\" \\ choice <3"),display.get("title"));assertEquals(false,display.get("announce_to_chat"));assertEquals("nikosnook:small_comforts",json.get("parent"));
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
    @Test void grantFailureCannotEscapeIntoEconomyAndWarningIsNotRepeated()throws Exception{
        var memory=new Memory();var warnings=new ArrayList<String>();memory.failGrant=MilestoneCatalogue.CHOICE;var milestones=new NookMilestones(parse(defaults()),memory,warnings::add);
        assertDoesNotThrow(()->milestones.selectedQuests(player()));assertDoesNotThrow(()->milestones.selectedQuests(player()));assertEquals(1,warnings.size());
    }
}
