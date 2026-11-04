package net.nikosnook.core;

import org.bukkit.DyeColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PipProductsTest {
    YamlConfiguration entry(String type){
        var c=new YamlConfiguration();c.set("type",type);c.set("name","A little find");c.set("rarity","COMMON");c.set("category","home");
        c.set("price-cents",400);c.set("stock-per-allocation",10);return c;
    }
    YamlConfiguration banner(){var c=entry("BANNER");c.set("banner.base","WHITE");c.set("banner.patterns",List.of(Map.of("pattern","minecraft:border","colour","YELLOW")));return c;}
    YamlConfiguration book(){var c=entry("WRITTEN_BOOK");c.set("book.title","a little journal");c.set("book.pages",List.of("a warm light <3","<click:run_command:'/op somebody'>literal ink</click>"));return c;}
    PipProducts.Product parse(YamlConfiguration c){return PipProducts.parse("little-find",c,Set.of("border","flower","stripe_bottom")::contains);}
    @Test void oldHeadDefinitionsAndConstructorsKeepTheirExactIdentity(){
        var c=entry("HEAD");c.set("type",null);c.set("texture","a".repeat(64));
        var product=parse(c);var expected=new VendorCatalogue.Design("little-find","A little find","a".repeat(64),VendorCatalogue.Rarity.COMMON,"home",Set.of(),true,false,400,10,0,"");
        assertEquals(expected,product.design());assertInstanceOf(PipProducts.Head.class,product.spec());assertEquals(VendorCatalogue.Type.HEAD,product.design().type());
    }
    @Test void everyBundledHeadKeepsItsOriginalDefinitionWithoutLookingUpBannerPatterns(){
        var resource=getClass().getResourceAsStream("/pip-catalogue.yml");assertNotNull(resource);
        var catalogue=YamlConfiguration.loadConfiguration(new InputStreamReader(resource,StandardCharsets.UTF_8)).getConfigurationSection("catalogue");
        assertEquals(48,catalogue.getKeys(false).size());
        for(String id:catalogue.getKeys(false)){
            var c=catalogue.getConfigurationSection(id);
            var expected=new VendorCatalogue.Design(id,c.getString("name"),c.getString("texture"),VendorCatalogue.Rarity.valueOf(c.getString("rarity")),c.getString("category"),new HashSet<>(c.getStringList("seasons")),c.getBoolean("year-round"),c.getBoolean("modern"),c.getLong("price-cents"),c.getInt("stock-per-allocation"),c.getInt("cap-per-visit"),c.getString("source"));
            var actual=PipProducts.parse(id,c,key->{throw new AssertionError("Head parsing must not query banner patterns");});
            assertEquals(expected,actual.design(),id);assertInstanceOf(PipProducts.Head.class,actual.spec());assertEquals(0,actual.design().cap());
        }
    }
    @Test void legacySixHeadVisitsCannotSelectNewProductTypesForTheirSeventhSlot(){
        var heads=new ArrayList<VendorCatalogue.Design>();
        for(var rarity:VendorCatalogue.Rarity.values())for(int i=0;i<8;i++){
            String id=rarity.name().toLowerCase(Locale.ROOT)+i;
            heads.add(new VendorCatalogue.Design(id,id,"a".repeat(64),rarity,id,i<3?Set.of("halloween"):Set.of(),i>=3,false,400,10,0,""));
        }
        var first=VendorCatalogue.select(heads,"halloween",Set.of(),Set.of(),42);
        var pins=new HashSet<>(first.stream().limit(6).map(VendorCatalogue.Design::id).toList());
        var catalogue=new ArrayList<>(heads);catalogue.add(parse(banner()).design());
        var journal=book();journal.set("rarity","RARE");catalogue.add(PipProducts.parse("new-journal",journal,key->true).design());
        var legacy=VendorCatalogue.legacyVisitPool(catalogue);assertEquals(heads,legacy);
        for(int seed=0;seed<100;seed++){
            var expanded=VendorCatalogue.select(legacy,"halloween",pins,Set.of(),seed);
            assertEquals(7,expanded.size());assertTrue(expanded.stream().allMatch(d->d.type()==VendorCatalogue.Type.HEAD));
            assertTrue(expanded.stream().map(VendorCatalogue.Design::id).toList().containsAll(pins));
        }
        var removed=new ArrayList<>(legacy);removed.removeIf(d->pins.contains(d.id()));
        assertThrows(IllegalArgumentException.class,()->VendorCatalogue.select(removed,"halloween",pins,Set.of(),42));
    }
    @Test void bannersHaveTypedPatternsWithoutAPlaceholderTexture(){
        var product=parse(banner());var spec=(PipProducts.Banner)product.spec();
        assertEquals(VendorCatalogue.Type.BANNER,product.design().type());assertEquals("",product.design().texture());
        assertEquals(DyeColor.WHITE,spec.base());assertEquals(List.of(new PipProducts.BannerPattern("border",DyeColor.YELLOW)),spec.patterns());
        assertThrows(UnsupportedOperationException.class,()->spec.patterns().clear());
    }
    @Test void headTextureCannotBeSmuggledIntoAnotherProductType(){
        var c=banner();c.set("texture","a".repeat(64));assertThrows(IllegalArgumentException.class,()->parse(c));
        assertThrows(IllegalArgumentException.class,()->new VendorCatalogue.Design("banner","Banner","a".repeat(64),VendorCatalogue.Rarity.COMMON,"home",Set.of(),true,false,400,10,0,"",VendorCatalogue.Type.BANNER));
    }
    @Test void writtenPagesRemainLiteralAndDefaultsAreExplicit(){
        var product=parse(book());var spec=(PipProducts.Book)product.spec();assertEquals("Pip",spec.author());assertEquals(PipProducts.Generation.ORIGINAL,spec.generation());
        assertEquals("a warm light <3",spec.pages().getFirst());assertTrue(spec.pages().getLast().startsWith("<click:run_command"));
        assertThrows(UnsupportedOperationException.class,()->spec.pages().clear());
    }
    @Test void invalidOrMixedProductSettingsFailWithTheEntryId(){
        var c=banner();c.set("book.title","wrong type");var mixed=c;String error=assertThrows(IllegalArgumentException.class,()->parse(mixed)).getMessage();assertTrue(error.contains("little-find"));
        c=banner();c.set("command","op somebody");var command=c;assertThrows(IllegalArgumentException.class,()->parse(command));
        c=entry("TELEPORT");var unknown=c;assertThrows(IllegalArgumentException.class,()->parse(unknown));
    }
    @Test void bannerPatternsMustBeVanillaMappingsWithKnownDyesAndAtMostSixLayers(){
        var unknown=banner();unknown.set("banner.patterns",List.of(Map.of("pattern","not_real","colour","YELLOW")));assertThrows(IllegalArgumentException.class,()->parse(unknown));
        var dye=banner();dye.set("banner.base","PASTEL");assertThrows(IllegalArgumentException.class,()->parse(dye));
        var many=banner();many.set("banner.patterns",Collections.nCopies(7,Map.of("pattern","border","colour","YELLOW")));assertThrows(IllegalArgumentException.class,()->parse(many));
        var malformed=banner();malformed.set("banner.patterns",List.of("border"));assertThrows(IllegalArgumentException.class,()->parse(malformed));
        var command=banner();command.set("banner.patterns",List.of(Map.of("pattern","border","colour","YELLOW","command","op")));assertThrows(IllegalArgumentException.class,()->parse(command));
    }
    @Test void bookPagesAndPresentationHaveBoundedSupportedFields(){
        var longTitle=book();longTitle.set("book.title","x".repeat(33));assertThrows(IllegalArgumentException.class,()->parse(longTitle));
        var page=book();page.set("book.pages",List.of("x".repeat(513)));assertThrows(IllegalArgumentException.class,()->parse(page));
        var count=book();count.set("book.pages",Collections.nCopies(21,"ink"));assertThrows(IllegalArgumentException.class,()->parse(count));
        var numeric=book();numeric.set("book.pages",List.of(123));assertThrows(IllegalArgumentException.class,()->parse(numeric));
        var title=book();title.set("book.title",true);assertThrows(IllegalArgumentException.class,()->parse(title));
        var action=book();action.set("book.command","op");assertThrows(IllegalArgumentException.class,()->parse(action));
    }
    @Test void bookGenerationAndTextCannotHideControlData(){
        var c=book();c.set("book.generation","COPY_OF_COPY");assertEquals(PipProducts.Generation.COPY_OF_COPY,((PipProducts.Book)parse(c).spec()).generation());
        c=book();c.set("book.generation","UNLIMITED");var generation=c;assertThrows(IllegalArgumentException.class,()->parse(generation));
        c=book();c.set("book.author","Pip\nOwner");var author=c;assertThrows(IllegalArgumentException.class,()->parse(author));
        c=book();c.set("book.pages",List.of("hidden\u0000text"));var controls=c;assertThrows(IllegalArgumentException.class,()->parse(controls));
    }
    @Test void specificationTypeMustMatchItsCatalogueEntry(){
        var product=parse(banner());assertThrows(IllegalArgumentException.class,()->new PipProducts.Product(product.design(),new PipProducts.Head()));
    }
    @Test void selectionConstraintsDoNotDependOnWhetherAnItemIsAHead(){
        var pool=new ArrayList<VendorCatalogue.Design>();
        for(var rarity:VendorCatalogue.Rarity.values())for(int i=0;i<8;i++){
            var type=i%3==0?VendorCatalogue.Type.BANNER:i%3==1?VendorCatalogue.Type.WRITTEN_BOOK:VendorCatalogue.Type.HEAD;
            pool.add(new VendorCatalogue.Design(rarity.name().toLowerCase()+i,"A find",type==VendorCatalogue.Type.HEAD?"a".repeat(64):"",rarity,"category"+i,i<3?Set.of("autumn"):Set.of(),i>=3,false,400,10,0,"",type));
        }
        for(int seed=0;seed<100;seed++)assertTrue(VendorCatalogue.valid(VendorCatalogue.select(pool,"autumn",Set.of(),Set.of(),seed),"autumn"));
    }
    @Test void bundledExamplesAreValidButNeverEnabledByDefault(){
        var resource=getClass().getResourceAsStream("/pip-products-examples.yml");assertNotNull(resource);
        var config=YamlConfiguration.loadConfiguration(new InputStreamReader(resource,StandardCharsets.UTF_8)).getConfigurationSection("catalogue");
        assertEquals(2,config.getKeys(false).size());
        for(String id:config.getKeys(false)){var c=config.getConfigurationSection(id);assertFalse(c.getBoolean("enabled",true));assertNotNull(PipProducts.parse(id,c,Set.of("border","flower","stripe_bottom")::contains));}
    }
}
