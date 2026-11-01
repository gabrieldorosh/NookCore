package net.nikosnook.core;

import org.junit.jupiter.api.Test;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PipExpandedCatalogueTest {
    static YamlConfiguration configuration(){return YamlConfiguration.loadConfiguration(new InputStreamReader(PipExpandedCatalogueTest.class.getResourceAsStream("/pip-catalogue.yml"),java.nio.charset.StandardCharsets.UTF_8));}
    static List<VendorCatalogue.Design> designs(){
        var section=configuration().getConfigurationSection("catalogue");List<VendorCatalogue.Design> designs=new ArrayList<>();
        for(String id:section.getKeys(false)){
            var c=section.getConfigurationSection(id);designs.add(new VendorCatalogue.Design(id,c.getString("name"),c.getString("texture"),VendorCatalogue.Rarity.valueOf(c.getString("rarity")),c.getString("category"),new HashSet<>(c.getStringList("seasons")),c.getBoolean("year-round"),c.getBoolean("modern"),c.getLong("price-cents"),c.getInt("stock-per-allocation"),c.getInt("cap-per-visit"),c.getString("source")));
        }return designs;
    }
    @Test void distinctDesignsKeepExistingPricesAndNoPurchaseCaps(){
        var designs=designs();assertEquals(48,designs.size());assertEquals(48,designs.stream().map(VendorCatalogue.Design::texture).distinct().count());
        for(var d:designs){assertEquals(0,d.cap());assertTrue(d.source().startsWith("https://mcheads.ru/en/"));assertEquals(switch(d.rarity()){case COMMON->400;case UNCOMMON->800;case RARE->1500;},d.price());assertEquals(switch(d.rarity()){case COMMON->10;case UNCOMMON->5;case RARE->4;},d.allocation());}
    }
    @Test void everyDayHasASeasonIncludingWinterYearBoundary(){
        var config=configuration();for(int n=0;n<366;n++)assertNotEquals("none",SpawnNpcs.season(config,LocalDate.of(2028,1,1).plusDays(n)));
        assertEquals("autumn",SpawnNpcs.season(config,LocalDate.of(2026,11,4)));assertEquals("halloween",SpawnNpcs.season(config,LocalDate.of(2026,10,31)));assertEquals("winter",SpawnNpcs.season(config,LocalDate.of(2027,1,1)));assertEquals("spring",SpawnNpcs.season(config,LocalDate.of(2027,3,1)));
    }
    @Test void eachSeasonCanPublishValidVariedSevenItemVisits(){
        var all=designs();for(String season:List.of("halloween","autumn","winter","spring","summer")){
            Set<String> previous=Set.of();Set<List<String>> visits=new HashSet<>();
            for(int seed=0;seed<24;seed++){
                var picked=VendorCatalogue.select(all,season,seed==0?Set.of("cctv-camera"):Set.of(),previous,seed);
                assertTrue(VendorCatalogue.valid(picked,season),season+" seed "+seed);visits.add(picked.stream().map(VendorCatalogue.Design::id).sorted().toList());previous=new HashSet<>(picked.stream().map(VendorCatalogue.Design::id).toList());
            }assertTrue(visits.size()>12,season+" has varied visits");
        }
    }
    @Test void existingDesignIdsAndTexturesArePreserved(){
        var original=YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/spawn-npcs.yml"),java.nio.charset.StandardCharsets.UTF_8)).getConfigurationSection("catalogue");var expanded=configuration().getConfigurationSection("catalogue");
        for(String id:original.getKeys(false)){assertEquals(original.getString(id+".texture"),expanded.getString(id+".texture"));assertEquals(original.getLong(id+".price-cents"),expanded.getLong(id+".price-cents"));}
    }
}
