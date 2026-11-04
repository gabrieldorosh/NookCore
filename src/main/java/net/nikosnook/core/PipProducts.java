package net.nikosnook.core;

import java.util.*;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import org.bukkit.*;
import org.bukkit.block.banner.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

/** Typed vanilla goods; only immutable definitions enter the shared catalogue selector. */
final class PipProducts {
    sealed interface Spec permits Head,Banner,Book { VendorCatalogue.Type type(); }
    record Head() implements Spec {public VendorCatalogue.Type type(){return VendorCatalogue.Type.HEAD;}}
    record BannerPattern(String pattern,DyeColor colour) {}
    record Banner(DyeColor base,List<BannerPattern> patterns) implements Spec {
        Banner {Objects.requireNonNull(base);patterns=List.copyOf(patterns);}
        public VendorCatalogue.Type type(){return VendorCatalogue.Type.BANNER;}
    }
    enum Generation { ORIGINAL, COPY_OF_ORIGINAL, COPY_OF_COPY }
    record Book(String title,String author,List<String> pages,Generation generation) implements Spec {
        Book {pages=List.copyOf(pages);Objects.requireNonNull(generation);}
        public VendorCatalogue.Type type(){return VendorCatalogue.Type.WRITTEN_BOOK;}
    }
    record Product(VendorCatalogue.Design design,Spec spec) {
        Product {Objects.requireNonNull(design);Objects.requireNonNull(spec);if(design.type()!=spec.type())throw new IllegalArgumentException("Pip product type does not match its specification.");}
    }
    private static final Set<String> BANNER_KEYS=Set.of("base","patterns");
    private static final Set<String> BOOK_KEYS=Set.of("title","author","pages","generation");
    private static final Set<String> PRODUCT_KEYS=Set.of("enabled","type","name","texture","rarity","category","seasons","year-round","modern","price-cents","stock-per-allocation","cap-per-visit","source","banner","book");

    static Product parse(String id,ConfigurationSection config){
        return parse(id,config,key->Registry.BANNER_PATTERN.get(NamespacedKey.minecraft(key))!=null);
    }
    /** Injected pattern lookup keeps configuration tests independent of Paper's registries. */
    static Product parse(String id,ConfigurationSection config,Predicate<String> knownPattern){
        try{
            Objects.requireNonNull(config,"Missing product section");
            var type=VendorCatalogue.Type.valueOf(config.getString("type","HEAD").toUpperCase(Locale.ROOT));
            if(type!=VendorCatalogue.Type.HEAD)keys(config,PRODUCT_KEYS,"product");
            var rarity=VendorCatalogue.Rarity.valueOf(config.getString("rarity","").toUpperCase(Locale.ROOT));
            var design=new VendorCatalogue.Design(id,config.getString("name",""),config.getString("texture",""),rarity,
                config.getString("category",""),new HashSet<>(config.getStringList("seasons")),config.getBoolean("year-round",true),config.getBoolean("modern",false),
                config.getLong("price-cents"),config.getInt("stock-per-allocation"),config.getInt("cap-per-visit",0),config.getString("source",""),type);
            Spec spec=switch(type){
                case HEAD->{if(config.contains("banner")||config.contains("book"))throw bad("head entries cannot contain banner or book settings");yield new Head();}
                case BANNER->{if(config.contains("book"))throw bad("banner entries cannot contain book settings");yield banner(config.getConfigurationSection("banner"),knownPattern);}
                case WRITTEN_BOOK->{if(config.contains("banner"))throw bad("book entries cannot contain banner settings");yield book(config.getConfigurationSection("book"));}
            };
            return new Product(design,spec);
        }catch(IllegalArgumentException|NullPointerException error){throw new IllegalArgumentException("Pip product "+id+": "+error.getMessage(),error);}
    }
    private static Banner banner(ConfigurationSection config,Predicate<String> knownPattern){
        require(config,"banner");keys(config,BANNER_KEYS,"banner");
        DyeColor base=colour(config.getString("base",""));
        if(config.contains("patterns")&&!config.isList("patterns"))throw bad("banner.patterns must be a list");
        var rows=config.getMapList("patterns");if(rows.size()>6)throw bad("banners may have at most six vanilla pattern layers");
        var patterns=new ArrayList<BannerPattern>();
        for(var row:rows){
            if(!row.keySet().equals(Set.of("pattern","colour")))throw bad("each banner layer needs only pattern and colour");
            if(!(row.get("pattern") instanceof String pattern)||!(row.get("colour") instanceof String dye))throw bad("banner pattern and colour must be text");
            String name=pattern.toLowerCase(Locale.ROOT);if(name.startsWith("minecraft:"))name=name.substring(10);
            if(!name.matches("[a-z0-9_]{1,64}")||!knownPattern.test(name))throw bad("unknown vanilla banner pattern: "+pattern);
            patterns.add(new BannerPattern(name,colour(dye)));
        }
        if(config.isList("patterns")&&config.getList("patterns").size()!=rows.size())throw bad("every banner pattern must be a mapping");
        return new Banner(base,patterns);
    }
    private static Book book(ConfigurationSection config){
        require(config,"book");keys(config,BOOK_KEYS,"book");
        String title=text(string(config,"title",""),32,false,"book.title");
        String author=text(string(config,"author","Pip"),32,false,"book.author");
        var raw=config.getList("pages");if(raw==null||raw.isEmpty()||raw.size()>20)throw bad("book.pages needs one to twenty pages");
        var pages=new ArrayList<String>();for(var page:raw){if(!(page instanceof String value))throw bad("book pages must be text");pages.add(text(value,512,true,"book page"));}
        Generation generation;
        try{generation=Generation.valueOf(string(config,"generation","ORIGINAL").toUpperCase(Locale.ROOT));}
        catch(IllegalArgumentException error){throw bad("book.generation must be ORIGINAL, COPY_OF_ORIGINAL or COPY_OF_COPY");}
        return new Book(title,author,pages,generation);
    }
    private static void require(ConfigurationSection section,String name){if(section==null)throw bad("missing "+name+" settings");}
    private static String string(ConfigurationSection section,String key,String fallback){var value=section.get(key);if(value==null)return fallback;if(!(value instanceof String text))throw bad(key+" must be text");return text;}
    private static void keys(ConfigurationSection section,Set<String> accepted,String name){for(String key:section.getKeys(false))if(!accepted.contains(key))throw bad("unsupported "+name+" setting: "+key);}
    private static DyeColor colour(String name){try{return DyeColor.valueOf(name.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException error){throw bad("unknown vanilla dye colour: "+name);}}
    private static String text(String value,int limit,boolean multiline,String name){
        if(value.isBlank()||value.length()>limit||value.chars().anyMatch(c->c<32&&!(multiline&&c=='\n')))throw bad(name+" must be nonblank text up to "+limit+" characters");
        return value;
    }
    private static IllegalArgumentException bad(String message){return new IllegalArgumentException(message);}

    static ItemStack make(Product product){
        var design=product.design();
        if(product.spec() instanceof Head)return PipHeads.make(design); // Existing head identity is deliberately unchanged.
        ItemStack item;
        if(product.spec() instanceof Banner banner){
            item=new ItemStack(Material.valueOf(banner.base().name()+"_BANNER"));var meta=(BannerMeta)item.getItemMeta();
            for(var layer:banner.patterns()){
                var pattern=Registry.BANNER_PATTERN.get(NamespacedKey.minecraft(layer.pattern()));
                if(pattern==null)throw bad("unknown vanilla banner pattern: "+layer.pattern());
                meta.addPattern(new Pattern(layer.colour(),pattern));
            }
            item.setItemMeta(meta);
        }else if(product.spec() instanceof Book book){
            item=new ItemStack(Material.WRITTEN_BOOK);var meta=(BookMeta)item.getItemMeta();
            meta.title(Component.text(book.title()));meta.author(Component.text(book.author()));
            meta.pages(book.pages().stream().map(Component::text).map(component->(Component)component).toList());
            meta.setGeneration(BookMeta.Generation.valueOf(book.generation().name()));item.setItemMeta(meta);
        }else throw bad("unsupported Pip product type");
        var colour=switch(design.rarity()){case COMMON->TextColor.color(0xC5DAB1);case UNCOMMON->TextColor.color(0xB9CFF0);case RARE->TextColor.color(0xE5C0E9);};
        var meta=item.getItemMeta();meta.displayName(Component.text(design.name(),colour).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(Component.text(switch(design.rarity()){case COMMON->"Common";case UNCOMMON->"Uncommon";case RARE->"Rare";},colour).decoration(TextDecoration.ITALIC,false),
            Component.text("Pip's collection · "+(design.type()==VendorCatalogue.Type.BANNER?"banner":"travel journal"),NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false)));
        var pdc=meta.getPersistentDataContainer();pdc.set(PipHeads.DESIGN,PersistentDataType.STRING,design.id());
        pdc.set(new NamespacedKey("nookcore","pip-rarity"),PersistentDataType.STRING,design.rarity().name());
        pdc.set(new NamespacedKey("nookcore","pip-seasons"),PersistentDataType.STRING,String.join(",",new TreeSet<>(design.seasons())));
        pdc.set(new NamespacedKey("nookcore","pip-product-type"),PersistentDataType.STRING,design.type().name());
        pdc.set(new NamespacedKey("nookcore","pip-schema"),PersistentDataType.INTEGER,2);
        item.setItemMeta(meta);return item;
    }
}
