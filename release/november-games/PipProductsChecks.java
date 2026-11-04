import java.lang.reflect.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Call run(core) from the isolated combined Paper probe; it changes no world or balances. */
public final class PipProductsChecks {
    static int count;
    static JavaPlugin core;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);count++;core.getLogger().info("PRODUCT_PASS "+message);}
    static Object invoke(Class<?> type,String name,Class<?>[] signature,Object...args)throws Exception {var m=type.getDeclaredMethod(name,signature);m.setAccessible(true);return m.invoke(null,args);}
    static Object read(Object target,String name)throws Exception {var m=target.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(target);}
    static boolean literal(Component page){return page.clickEvent()==null&&page.hoverEvent()==null&&page.children().stream().allMatch(PipProductsChecks::literal);}
    public static int run(JavaPlugin plugin)throws Exception {
        core=plugin;count=0;var loader=plugin.getClass().getClassLoader();var products=Class.forName("net.nikosnook.core.PipProducts",true,loader);
        var productType=Class.forName("net.nikosnook.core.PipProducts$Product",true,loader);
        var config=YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(plugin.getResource("pip-products-examples.yml")),StandardCharsets.UTF_8)).getConfigurationSection("catalogue");
        var made=new LinkedHashMap<String,ItemStack>();
        for(String id:config.getKeys(false)){
            var row=config.getConfigurationSection(id);check(!row.getBoolean("enabled",true),id+" example remains disabled");
            var product=invoke(products,"parse",new Class[]{String.class,ConfigurationSection.class},id,row);
            var item=(ItemStack)invoke(products,"make",new Class[]{productType},product);made.put(id,item);
            check(item.getAmount()==1,id+" factory creates one sale unit");
            check(id.equals(item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey("nookcore","pip-design"),PersistentDataType.STRING)),id+" carries its design identity while an item");
            check(item.isSimilar(ItemStack.deserializeBytes(item.serializeAsBytes())),id+" exact item survives serialization");
            var pdc=item.getItemMeta().getPersistentDataContainer();
            check(row.getString("type").equals(pdc.get(new NamespacedKey("nookcore","pip-product-type"),PersistentDataType.STRING)),id+" carries its real product type");
            check(Integer.valueOf(2).equals(pdc.get(new NamespacedKey("nookcore","pip-schema"),PersistentDataType.INTEGER)),id+" uses the non-head item schema");
        }
        var banner=made.get("sunny-nook-banner");check(banner.getType()==Material.WHITE_BANNER,"banner uses its vanilla base material");
        var bm=(BannerMeta)banner.getItemMeta();check(bm.numberOfPatterns()==2,"banner retains both requested patterns");
        check(bm.getPattern(0).getColor()==DyeColor.YELLOW && bm.getPattern(0).getPattern().equals(Registry.BANNER_PATTERN.get(NamespacedKey.minecraft("border"))),"banner first layer has the requested dye and pattern");
        check(bm.getPattern(1).getColor()==DyeColor.ORANGE && bm.getPattern(1).getPattern().equals(Registry.BANNER_PATTERN.get(NamespacedKey.minecraft("flower"))),"banner flower layer is preserved");
        var book=made.get("pip-lantern-journal");check(book.getType()==Material.WRITTEN_BOOK,"journal is a vanilla written book");
        var bookMeta=(BookMeta)book.getItemMeta();check(bookMeta.getPageCount()==3,"journal retains all three pages");
        check(bookMeta.getGeneration()==BookMeta.Generation.COPY_OF_COPY,"journal uses its explicit vanilla copy generation");
        var journal=config.getConfigurationSection("pip-lantern-journal.book");
        check(journal.getString("title").equals(PlainTextComponentSerializer.plainText().serialize(bookMeta.title())),"journal title is unchanged");
        check(journal.getString("author").equals(PlainTextComponentSerializer.plainText().serialize(bookMeta.author())),"journal author is unchanged");
        check(journal.getStringList("pages").equals(bookMeta.pages().stream().map(PlainTextComponentSerializer.plainText()::serialize).toList()),"journal retains every literal page exactly");
        check(bookMeta.pages().stream().allMatch(PipProductsChecks::literal),"journal pages contain no click or hover actions");
        check(PlainTextComponentSerializer.plainText().serialize(bookMeta.page(3)).contains("<3"),"cozy text stays literal in the book");
        var literal=config.getConfigurationSection("pip-lantern-journal");literal.set("book.pages",List.of("<click:run_command:'/op somebody'>literal ink</click>"));
        var literalProduct=invoke(products,"parse",new Class[]{String.class,ConfigurationSection.class},"literal-journal",literal);
        var literalBook=(ItemStack)invoke(products,"make",new Class[]{productType},literalProduct);
        var literalPage=((BookMeta)literalBook.getItemMeta()).page(1);
        check(literal(literalPage) && PlainTextComponentSerializer.plainText().serialize(literalPage).startsWith("<click:run_command"),"markup-looking page text remains non-executable ink");
        var old=YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(plugin.getResource("pip-catalogue.yml")),StandardCharsets.UTF_8)).getConfigurationSection("catalogue.chick");
        var headProduct=invoke(products,"parse",new Class[]{String.class,ConfigurationSection.class},"chick",old);
        var head=(ItemStack)invoke(products,"make",new Class[]{productType},headProduct);var hm=(SkullMeta)head.getItemMeta();
        check(head.getType()==Material.PLAYER_HEAD && hm.getOwnerProfile()!=null,"legacy head factory still creates its player profile");
        check(hm.getOwnerProfile().getTextures().getSkin().toString().endsWith(old.getString("texture")),"legacy texture hash is unchanged");
        check(Integer.valueOf(1).equals(hm.getPersistentDataContainer().get(new NamespacedKey("nookcore","pip-schema"),PersistentDataType.INTEGER)),"legacy head metadata schema is unchanged");
        var codec=Class.forName("net.nikosnook.core.NativeInventoryCodec",true,loader);
        ItemStack[] inventory={banner.clone(),book.clone(),head.clone(),null};
        var slots=invoke(codec,"capture",new Class[]{ItemStack[].class},(Object)inventory);
        var restored=(ItemStack[])invoke(codec,"restore",new Class[]{List.class},slots);
        check(invoke(codec,"mismatch",new Class[]{List.class,ItemStack[].class},slots,restored)==null,"existing shop inventory codec preserves mixed product metadata");
        var adapter=Class.forName("net.nikosnook.core.NativePlayerInventory",true,loader);
        var planner=Class.forName("net.nikosnook.core.NativeInventoryPlan",true,loader);
        var stackType=Class.forName("net.nikosnook.core.NativeInventoryPlan$Stack",true,loader);
        for(var unit:List.of(banner,book)){
            var stack=invoke(adapter,"item",new Class[]{byte[].class},(Object)unit.serializeAsBytes());
            int limit=(Integer)read(stack,"limit");check(limit==unit.getMaxStackSize() && limit<64,unit.getType()+" uses its vanilla small stack limit");
            int quantity=limit+1;var empty=Collections.nCopies(3,null);
            var plan=invoke(planner,"deliver",new Class[]{List.class,stackType,int.class},empty,stack,quantity);
            var after=(List<?>)read(plan,"after");var delivery=(ItemStack[])invoke(codec,"restore",new Class[]{List.class},after);
            check(Arrays.stream(delivery).filter(Objects::nonNull).mapToInt(ItemStack::getAmount).sum()==quantity,unit.getType()+" complete bundle spans stacks without loss");
            check(Arrays.stream(delivery).filter(Objects::nonNull).allMatch(item->item.getAmount()<=limit && item.isSimilar(unit)),unit.getType()+" split stacks keep exact metadata and legal quantities");
            try{invoke(planner,"deliver",new Class[]{List.class,stackType,int.class},Collections.nCopies(1,null),stack,quantity);throw new AssertionError("Full bundle must reject insufficient space");}
            catch(InvocationTargetException error){check(error.getCause() instanceof IllegalArgumentException,unit.getType()+" rejects insufficient space before partial delivery");}
            check(empty.stream().allMatch(Objects::isNull),unit.getType()+" planning leaves the original inventory unchanged");
        }
        core.getLogger().info("PRODUCT_COMPLETE checks="+count);return count;
    }
}
