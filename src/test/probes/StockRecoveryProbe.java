import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;

public class StockRecoveryProbe extends JavaPlugin {
    ClassLoader loader;Object store;int checks;Path db,backups;final AtomicBoolean ready=new AtomicBoolean(true);final List<Exception> errors=new ArrayList<>();
    Class<?> type(String name)throws Exception{return Class.forName("net.nikosnook.core."+name,true,loader);}
    Object invoke(Object obj,String name,Object...args)throws Exception{
        Class<?> c=obj instanceof Class<?> t?t:obj.getClass();
        for(Method m:c.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);try{return m.invoke(obj instanceof Class<?>?null:obj,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception cause)throw cause;throw e;}}
        throw new NoSuchMethodException(name);
    }
    Object construct(Class<?> type,Class<?>[] types,Object...args)throws Exception{var c=type.getDeclaredConstructor(types);c.setAccessible(true);return c.newInstance(args);}
    void check(boolean good,String name){if(!good)throw new AssertionError(name);checks++;getLogger().info("RECOVERY_PASS "+name);}
    void dispatch(String text){if(!Bukkit.dispatchCommand(Bukkit.getConsoleSender(),text))throw new AssertionError("Command not routed: "+text);}
    List<Path> backups()throws Exception{if(!Files.isDirectory(backups))return List.of();try(var files=Files.list(backups)){return files.filter(p->p.getFileName().toString().startsWith("before-stock-recovery-")).toList();}}
    int sqlCount(String query)throws Exception{try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var s=c.createStatement();var r=s.executeQuery(query)){r.next();return r.getInt(1);}}
    UUID[] receipt(String plot,String name)throws Exception{
        UUID actor=UUID.randomUUID(),receipt=UUID.randomUUID();long now=System.currentTimeMillis();
        invoke(store,"join",actor,name,now,10000L);invoke(store,"definePlot",plot,600L);invoke(store,"rent",plot,actor,now);
        var book=new ItemStack(Material.ENCHANTED_BOOK);var meta=(EnchantmentStorageMeta)book.getItemMeta();meta.addStoredEnchant(org.bukkit.enchantments.Enchantment.MENDING,1,true);meta.addStoredEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING,3,true);book.setItemMeta(meta);
        Object offer=invoke(store,"publishNativeOffer",plot+":1:2:3",plot,actor,book.serializeAsBytes(),1,100L,now);UUID id=(UUID)invoke(offer,"id");
        byte[] before=new byte[32],after=new byte[32];before[0]=1;after[0]=2;
        invoke(store,"planNativeStockDeposit",receipt,id,actor,1,before,after,now);
        return new UUID[]{receipt,id,actor};
    }
    Object review(UUID id)throws Exception{return invoke(store,"nativeStockReview",id);}
    int stock(UUID offer)throws Exception{return (Integer)invoke(invoke(store,"nativeOffer",offer),"stock");}
    void run()throws Exception{
        var core=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NookCore");loader=core.getClass().getClassLoader();
        Files.createDirectories(getDataFolder().toPath());db=getDataFolder().toPath().resolve("probe-"+UUID.randomUUID()+".db");backups=core.getDataFolder().toPath().resolve("backups");
        store=construct(type("NookStore"),new Class[]{Path.class},db);
        try{
            Object shops=construct(type("NativeShops"),new Class[]{JavaPlugin.class,type("NookStore"),BooleanSupplier.class,Function.class,Consumer.class},core,store,(BooleanSupplier)ready::get,(Function<Block,String>)(b->"test"),(Consumer<Exception>)errors::add);
            construct(type("ShopSetup"),new Class[]{JavaPlugin.class,type("NativeShops"),BiConsumer.class},core,shops,(BiConsumer<Player,Block>)((p,b)->{}));
            UUID[] one=receipt("one","MissingBook"),two=receipt("two","KeptBook");
            dispatch("nookshops review "+one[0]);
            check(stock(one[1])==0 && invoke(review(one[0]),"state").equals("REVIEW"),"read-only detail command leaves pending deposit unchanged");
            ready.set(false);dispatch("nookshops recover-stock "+one[0]+" credit confirmed missing and unreplaced");dispatch("nookshops review "+one[0]);
            check(stock(one[1])==0 && backups().isEmpty(),"paused service blocks recovery and permits read-only review");ready.set(true);
            dispatch("nookshops recover-stock "+one[0]+" credit confirmed missing and unreplaced");
            check(stock(one[1])==0 && backups().isEmpty() && invoke(review(one[0]),"resolution")==null,"real console credit preview does not mutate stock or create backup");
            dispatch("nookshops recover-stock "+one[0]+" confirm");
            Object credited=review(one[0]),resolution=invoke(credited,"resolution");
            check(stock(one[1])==1 && Objects.equals(invoke(credited,"creditedQuantity"),1) && invoke(credited,"state").equals("COMPLETE"),"confirm credits recorded quantity exactly once");
            check(!(Boolean)invoke(store,"nativeNeedsReview",one[2]),"credit resolution unblocks original actor");
            check(resolution!=null && invoke(resolution,"recovery").toString().equals("CREDIT") && invoke(resolution,"staff").equals("console:CONSOLE") && invoke(resolution,"reason").equals("confirmed missing and unreplaced"),"credit audit records explicit decision console identity and reason");
            var files=backups();check(files.size()==1,"credit confirmation creates one SQLite backup");
            try(var c=DriverManager.getConnection("jdbc:sqlite:"+files.getFirst());var s=c.createStatement();var r=s.executeQuery("SELECT stock FROM native_offers WHERE id='"+one[1]+"'")){r.next();check(r.getInt(1)==0,"backup contains pre-recovery stock state");}
            dispatch("nookshops recover-stock "+one[0]+" confirm");
            check(stock(one[1])==1 && backups().size()==1 && sqlCount("SELECT COUNT(*) FROM native_stock_resolutions")==1,"duplicate confirm does not duplicate stock backup or audit");
            dispatch("nookshops recover-stock "+two[0]+" no-credit player confirms the item stayed in inventory");
            check(stock(two[1])==0 && invoke(review(two[0]),"state").equals("REVIEW") && (Boolean)invoke(store,"nativeNeedsReview",two[2]),"no-credit preview leaves second actor blocked and stock unchanged");
            dispatch("nookshops recover-stock "+two[0]+" confirm");
            Object closed=review(two[0]);
            check(stock(two[1])==0 && invoke(closed,"creditedQuantity")==null && invoke(closed,"state").equals("COMPLETE") && !(Boolean)invoke(store,"nativeNeedsReview",two[2]),"no-credit confirm adds no stock and unblocks second actor");
            check(invoke(invoke(closed,"resolution"),"recovery").toString().equals("NO_CREDIT") && backups().size()==2 && sqlCount("SELECT COUNT(*) FROM native_stock_resolutions")==2,"no-credit decision has independent audit and backup");
            dispatch("nookshops recover-stock "+two[0]+" credit wrong second attempt");dispatch("nookshops recover-stock "+two[0]+" confirm");
            check(stock(two[1])==0 && backups().size()==2 && sqlCount("SELECT COUNT(*) FROM native_stock_resolutions")==2,"resolved receipt cannot be re-previewed with opposite decision");
            check(errors.isEmpty(),"no storage or protection failure callback");
            getLogger().info("RECOVERY_COMPLETE checks="+checks);
        }finally{invoke(store,"close");}
    }
    public void onEnable(){getServer().getScheduler().runTask(this,()->{try{run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"RECOVERY_FAILED",e);}finally{getServer().shutdown();}});}
}
