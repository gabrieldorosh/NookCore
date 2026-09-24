package net.nikosnook.core;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.BiConsumer;

/** Opt-in, read-only rehearsal of shop creation. Does not register live shops. */
final class ShopSetup implements Listener,CommandExecutor,TabCompleter {
    private record Draft(Block chest,ItemStack item,ShopDraft terms,long expires){}
    private static final class Menu implements InventoryHolder {
        final UUID owner;Inventory inventory;
        Menu(UUID owner){this.owner=owner;}
        public Inventory getInventory(){return inventory;}
    }
    private final JavaPlugin plugin;
    private final BiConsumer<Player,Block> validate;
    private final Map<UUID,Draft> drafts=new HashMap<>();
    ShopSetup(JavaPlugin plugin,BiConsumer<Player,Block> validate){
        this.plugin=plugin;this.validate=validate;
        plugin.getCommand("nookshops").setExecutor(this);plugin.getCommand("nookshops").setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Bukkit.getScheduler().runTaskTimer(plugin,()->drafts.entrySet().removeIf(e->System.currentTimeMillis()>=e.getValue().expires()),1200,1200);
    }
    static boolean stockContainer(Material type){return type==Material.CHEST || type==Material.TRAPPED_CHEST || type==Material.BARREL || type.name().endsWith("SHULKER_BOX");}
    private boolean enabled(){return plugin.getConfig().getBoolean("shop-setup-preview-enabled",false);}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!enabled()){sender.sendMessage(NookUi.message("NookShops","Shop setup preview is not enabled on this server."));return true;}
        if(!(sender instanceof Player player)){sender.sendMessage(NookUi.message("NookShops","Use shop setup in game."));return true;}
        try{
            if(args.length==0 || args[0].equalsIgnoreCase("help")){
                NookUi.help(sender,"NookShops · Setup preview","/nookshops create — hold your sale item and look at your stock container","/nookshops preview — reopen your draft","/nookshops price <nooks> — set the price per bundle","/nookshops payment <item> [quantity] — use plain items as payment","/nookshops cancel — discard your draft");
                sender.sendMessage(NookUi.message("NookShops","Drafts only: no live shop is created, and no items or Nooks move. Drafts expire after ten minutes or when you leave."));return true;
            }
            String sub=args[0].toLowerCase(Locale.ROOT);
            if(sub.equals("cancel") && args.length==1){drafts.remove(player.getUniqueId());if(player.getOpenInventory().getTopInventory().getHolder() instanceof Menu)player.closeInventory();sender.sendMessage(NookUi.message("NookShops","Shop draft discarded."));return true;}
            if(sub.equals("create") && args.length==1){
                Block chest=player.getTargetBlockExact(5);
                if(chest==null || !stockContainer(chest.getType()))throw new IllegalArgumentException("Look at a chest, barrel or shulker box within five blocks while holding the item you want to sell.");
                validate.accept(player,chest);ItemStack item=player.getInventory().getItemInMainHand().clone();
                if(item.getType().isAir())throw new IllegalArgumentException("Hold an example of the item you want to sell.");
                item.setAmount(1);drafts.put(player.getUniqueId(),new Draft(chest,item,ShopDraft.initial(),System.currentTimeMillis()+600_000));open(player);return true;
            }
            Draft draft=current(player);ShopDraft terms=draft.terms();
            if(sub.equals("price") && args.length==2)terms=terms.nooks(Money.parse(args[1]));
            else if(sub.equals("payment") && (args.length==2 || args.length==3)){
                Material material=Material.matchMaterial(args[1]);
                if(material==null || !material.isItem() || material.isAir())throw new IllegalArgumentException("Choose a real item, such as diamond or emerald.");
                terms=terms.item(material.name(),args.length==2?1:Integer.parseInt(args[2]));
            }else if(sub.equals("payment"))throw new IllegalArgumentException("Choose a payment item: /nookshops payment <item> [quantity]. Quantity defaults to 1.");
            else if(!sub.equals("preview") || args.length!=1)throw new IllegalArgumentException("Use /nookshops help to see the setup commands.");
            drafts.put(player.getUniqueId(),new Draft(draft.chest(),draft.item(),terms,draft.expires()));open(player);
        }catch(IllegalArgumentException e){sender.sendMessage(NookUi.problem("NookShops",e instanceof NumberFormatException?"Use a whole number between 1 and 64 for payment items.":e.getMessage()));}
        catch(RuntimeException e){plugin.getLogger().warning("Shop setup preview failed: "+e.getClass().getSimpleName());sender.sendMessage(NookUi.problem("NookShops","The draft could not be checked. No shop was created."));}
        return true;
    }
    private Draft current(Player player){
        Draft draft=drafts.get(player.getUniqueId());
        if(draft==null || System.currentTimeMillis()>=draft.expires()){drafts.remove(player.getUniqueId());throw new IllegalArgumentException("No current draft. Hold your sale item, look at a stock container and run /nookshops create.");}
        if(!player.getWorld().equals(draft.chest().getWorld()) || !draft.chest().getWorld().isChunkLoaded(draft.chest().getX()>>4,draft.chest().getZ()>>4))throw new IllegalArgumentException("Return near your stock container before continuing setup.");
        if(!stockContainer(draft.chest().getType()))throw new IllegalArgumentException("The selected container is no longer there. Start a new draft.");
        validate.accept(player,draft.chest());return draft;
    }
    private ItemStack icon(Material type,String title,String... lines){
        var item=new ItemStack(type);var meta=item.getItemMeta();meta.displayName(Component.text(title,NookUi.COMMAND).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,false));
        meta.lore(Arrays.stream(lines).map(s->Component.text(s,NookUi.BODY)).toList());item.setItemMeta(meta);return item;
    }
    private void open(Player player){
        Draft d=current(player);Menu menu=new Menu(player.getUniqueId());menu.inventory=Bukkit.createInventory(menu,27,Component.text("NookShops · Draft",NookUi.ACCENT));
        var item=d.item().clone();item.setAmount(Math.min(d.terms().quantity(),item.getMaxStackSize()));
        menu.inventory.setItem(4,icon(Material.PAPER,"Customer receives "+d.terms().quantity()+" item(s)","Customer pays "+d.terms().price(),"This is a draft; purchases are not enabled.","Stock chest: "+d.chest().getX()+", "+d.chest().getY()+", "+d.chest().getZ()));
        menu.inventory.setItem(0,icon(Material.RED_DYE,"Lower price","Nooks: -0.25 · Shift: -1.00","Items: -1 · Shift: -8"));
        menu.inventory.setItem(8,icon(Material.LIME_DYE,"Higher price","Nooks: +0.25 · Shift: +1.00","Items: +1 · Shift: +8"));
        menu.inventory.setItem(11,icon(Material.RED_DYE,"Smaller bundle","Click: -1 · Shift-click: -8"));menu.inventory.setItem(13,item);
        menu.inventory.setItem(15,icon(Material.LIME_DYE,"Larger bundle","Click: +1 · Shift-click: +8"));
        menu.inventory.setItem(18,icon(Material.GOLD_NUGGET,"Payment: Nooks","Use /nookshops price <amount> for a custom price."));
        menu.inventory.setItem(20,icon(Material.DIAMOND,"Payment: diamonds","Click to use one plain diamond per bundle.","Custom items: /nookshops payment <item> [quantity]"));
        menu.inventory.setItem(24,icon(d.terms().paymentItem()==null?Material.GOLD_NUGGET:Material.valueOf(d.terms().paymentItem()),"Selected: "+d.terms().price(),"Payment per bundle", "Preview only; no items or currency move."));
        menu.inventory.setItem(22,icon(Material.BOOK,"Review draft","Customer receives "+d.terms().quantity()+" item(s)","Customer pays "+d.terms().price(),"Plain payment items only; no named/enchanted currency."));
        menu.inventory.setItem(26,icon(Material.BARRIER,"Close preview","Draft remains available for ten minutes from creation.","/nookshops cancel discards it."));player.openInventory(menu.inventory);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof Menu menu))return;
        event.setCancelled(true); // Includes bottom inventory, shift moves, number keys and collection.
        if(!(event.getWhoClicked() instanceof Player player) || !menu.owner.equals(player.getUniqueId()))return;
        if(!enabled()){player.closeInventory();return;}
        int slot=event.getRawSlot();boolean shift=event.isShiftClick();if(slot<0 || slot>=27)return;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!player.isOnline() || player.getOpenInventory().getTopInventory()!=menu.inventory)return;
            try{
                if(slot==26){player.closeInventory();return;}
                Draft d=current(player);var terms=d.terms();int step=shift?8:1;
                if(slot==11 || slot==15)terms=terms.quantity(Math.clamp(terms.quantity()+(slot==11?-step:step),1,64));
                else if(slot==0 || slot==8){
                    terms=terms.adjustPrice(slot==8,shift);
                }
                else if(slot==18)terms=terms.nooks(terms.nooks());
                else if(slot==20)terms=terms.item("DIAMOND",1);
                else if(slot==22){player.sendMessage(NookUi.message("NookShops","Draft: customer receives "+terms.quantity()+" × "+d.item().getType().name().toLowerCase(Locale.ROOT).replace('_',' ')+" for "+terms.price()+". No live shop has been created."));return;}
                else return;
                drafts.put(player.getUniqueId(),new Draft(d.chest(),d.item(),terms,d.expires()));open(player);
            }catch(IllegalArgumentException e){player.closeInventory();player.sendMessage(NookUi.problem("NookShops",e.getMessage()));}
            catch(RuntimeException e){player.closeInventory();plugin.getLogger().warning("Shop draft menu failed: "+e.getClass().getSimpleName());}
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Menu)event.setCancelled(true);}
    @EventHandler public void quit(PlayerQuitEvent event){drafts.remove(event.getPlayer().getUniqueId());}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String label,String[] args){
        if(!enabled())return List.of();
        if(args.length==1)return NookUi.complete(args[0],List.of("create","preview","price","payment","cancel","help"));
        if(args.length==2 && args[0].equalsIgnoreCase("payment"))return NookUi.complete(args[1],Arrays.stream(Material.values()).filter(m->m.isItem() && !m.isAir()).map(m->m.name().toLowerCase(Locale.ROOT)).toList());
        return List.of();
    }
}
