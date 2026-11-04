import java.lang.reflect.*;
import java.sql.Connection;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import com.mojang.authlib.GameProfile;

/** Disposable local UI proof. Never adds its synthetic operator to the player list. */
public final class CataloguePreviewProbe extends JavaPlugin {
    JavaPlugin core;Object npcs,store;Connection db;Player player;PlayerInventory inventory;Inventory top;InventoryView view;
    final UUID id=UUID.fromString("20261104-0000-4000-8000-000000000020");
    final Set<String> seen=new LinkedHashSet<>();boolean allowed=true;int stage,checks;long accounts,ledger;
    Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    Object call(Object o,String name,Object...args)throws Exception{for(var m:o.getClass().getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);try{return m.invoke(o,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception cause)throw cause;throw e;}}throw new NoSuchMethodException(name);}
    void check(boolean b,String text){if(!b)throw new AssertionError(text);checks++;getLogger().info("CATALOGUE_PASS "+text);}
    long rows(String table)throws Exception{try(var s=db.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();return r.getLong(1);}}
    int page()throws Exception{return (int)field(top.getHolder(),"cataloguePage");}
    void capturePage(){for(int i=0;i<45;i++){var item=top.getItem(i);if(item==null)continue;String design=item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey("nookcore","pip-design"),org.bukkit.persistence.PersistentDataType.STRING);check(design!=null,"product slot retains a design identity");check(seen.add(design),"catalogue design is reachable once");}}
    void click(int slot,ClickType type)throws Exception{var e=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,slot,type,InventoryAction.PICKUP_ALL);call(npcs,"click",e);check(e.isCancelled(),"preview item transfer is cancelled: "+type);}
    void setup()throws Exception{
        check("127.0.0.1".equals(getServer().getIp()),"fixture is loopback only");
        core=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NookCore");npcs=Objects.requireNonNull(field(core,"spawnNpcs"));store=field(core,"store");db=(Connection)field(store,"db");
        accounts=rows("accounts");ledger=rows("ledger");World world=Bukkit.getWorlds().getFirst();
        var handle=new ServerPlayer(((CraftServer)getServer()).getServer(),((CraftWorld)world).getHandle(),new GameProfile(id,"CatalogueTester"),ClientInformation.createDefault());var real=handle.getBukkitEntity();inventory=real.getInventory();top=Bukkit.createInventory(null,54);
        player=(Player)Proxy.newProxyInstance(getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
            case "getUniqueId"->id;case "getName"->"CatalogueTester";case "getWorld"->world;case "getLocation"->new Location(world,5.5,121,-4.5);
            case "getGameMode"->GameMode.SURVIVAL;case "isOnline"->true;case "hasPermission","isOp"->allowed;case "getOpenInventory"->view;
            case "openInventory"->{top=(Inventory)a[0];yield view;}case "sendMessage","sendPlainMessage"->null;
            default->{try{yield m.invoke(real,a);}catch(InvocationTargetException e){throw e.getCause();}}
        });
        view=(InventoryView)Proxy.newProxyInstance(getClassLoader(),new Class[]{InventoryView.class},(o,m,a)->switch(m.getName()){
            case "getTopInventory"->top;case "getBottomInventory"->inventory;case "getPlayer"->player;case "getType"->InventoryType.CHEST;
            case "getInventory"->(int)a[0]<0?null:(int)a[0]<top.getSize()?top:inventory;
            case "convertSlot"->(int)a[0]<top.getSize()?(int)a[0]:(int)a[0]-top.getSize();
            case "getItem"->(int)a[0]<top.getSize()?top.getItem((int)a[0]):inventory.getItem((int)a[0]-top.getSize());
            case "getCursor"->new ItemStack(Material.AIR);case "getSlotType"->InventoryType.SlotType.CONTAINER;case "countSlots"->top.getSize()+inventory.getSize();
            default->null;
        });
        call(npcs,"command",player,new String[]{"vendor","catalogue"});
        check(page()==0 && top.getSize()==54,"admin command opens first catalogue page");
        check(top.getItem(45)==null && top.getItem(53).getType()==Material.ARROW,"first page has only forward navigation");
        capturePage();check(seen.size()==45,"first page exposes forty-five products");
        click(0,ClickType.LEFT);
    }
    void tick(){try{
        switch(stage++){
            case 0->setup();
            case 1->{check(page()==0,"clicking a preview product does not buy it");click(53,ClickType.RIGHT);click(53,ClickType.SHIFT_LEFT);}
            case 2->{check(page()==0,"right and shift clicks do not navigate or transfer");click(53,ClickType.LEFT);}
            case 3->{check(page()==1,"next arrow opens the second page");capturePage();check(seen.size()==54,"all fifty-four designs including new examples are reachable");check(top.getItem(45).getType()==Material.ARROW && top.getItem(53)==null,"last page has only backward navigation");allowed=false;click(45,ClickType.LEFT);}
            case 4->{check(page()==1,"permission revoked during browsing blocks navigation");allowed=true;click(45,ClickType.LEFT);}
            case 5->{check(page()==0,"previous arrow returns to the first page");check(rows("accounts")==accounts && rows("ledger")==ledger,"preview creates no account or money entries");check(Arrays.stream(inventory.getStorageContents()).allMatch(item->item==null||item.getType().isAir()),"no preview item enters the player's inventory");check(Bukkit.getPlayer(id)==null,"fixture operator never enters the player list");getLogger().info("CATALOGUE_COMPLETE checks="+checks);Bukkit.getScheduler().cancelTasks(this);}
        }
    }catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"CATALOGUE_FAILED checks="+checks,error);Bukkit.getScheduler().cancelTasks(this);}}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskTimer(this,this::tick,140,3);}
}
