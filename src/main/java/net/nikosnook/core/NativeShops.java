package net.nikosnook.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.*;

/** Opt-in district shops. The block is an interface; stock lives in the receipt-backed store. */
final class NativeShops implements Listener {
    private static final class Menu implements InventoryHolder {
        final UUID viewer,offer;final long revision;final boolean owner;Inventory inventory;boolean stock;int amount=1;
        Menu(UUID viewer,NativeShop.Offer offer,boolean owner){this.viewer=viewer;this.offer=offer.id();this.revision=offer.revision();this.owner=owner;}
        public Inventory getInventory(){return inventory;}
    }
    private final JavaPlugin plugin;private final NookStore store;private final NativeCustody custody;
    private final BooleanSupplier ready;private final Function<Block,String> plot;private final Consumer<Exception> failure;
    private final NamespacedKey anchor=new NamespacedKey("nookcore","native-shop");
    NativeShops(JavaPlugin plugin,NookStore store,BooleanSupplier ready,Function<Block,String> plot,Consumer<Exception> failure){
        this.plugin=plugin;this.store=store;this.custody=new NativeCustody(store);this.ready=ready;this.plot=plot;this.failure=failure;
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    boolean enabled(){return plugin.getConfig().getBoolean("native-shops-enabled",false);}
    private void ready(Player p){
        if(!enabled() || !ready.getAsBoolean())throw new IllegalArgumentException("Native shops are unavailable right now.");
        if(p.getGameMode()!=GameMode.SURVIVAL)throw new IllegalArgumentException("Use shops in survival mode.");
        if(p.isDead() || !p.isOnline())throw new IllegalArgumentException("Return to the world before using shops.");
    }
    static String location(Block block){return block.getWorld().getUID()+":"+block.getX()+":"+block.getY()+":"+block.getZ();}
    private List<Container> containers(Block block){
        if(!(block.getState() instanceof Container container) || !ShopSetup.stockContainer(block.getType()))throw new IllegalArgumentException("Look at a chest, barrel or shulker box.");
        if(container instanceof Chest chest && chest.getInventory().getHolder() instanceof DoubleChest pair){
            return List.of((Container)pair.getLeftSide(),(Container)pair.getRightSide());
        }return List.of(container);
    }
    private Block origin(Block block){return containers(block).stream().map(Container::getBlock).min(java.util.Comparator.comparing(NativeShops::location)).orElseThrow();}
    private void noChestShop(Block block){
        for(var side:containers(block))for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)
            if(side.getBlock().getRelative(x,y,z).getState() instanceof Sign)throw new IllegalArgumentException("Use a separate container away from signs. ChestShop and NookShops must not share a container.");
    }
    void publish(Player player,Block block,ItemStack item,ShopDraft terms)throws Exception {
        ready(player);
        noChestShop(block);String id=plot.apply(block);
        for(var side:containers(block)){
            if(!id.equals(plot.apply(side.getBlock())))throw new IllegalArgumentException("Both container halves must be in the same rented plot.");
            if(Arrays.stream(side.getInventory().getContents()).anyMatch(i->i!=null && !i.getType().isAir()))throw new IllegalArgumentException("Empty the physical container first. Stock is deposited from your inventory through NookShops.");
        }
        Block origin=origin(block);
        var offer=store.publishNativeOffer(location(origin),id,player.getUniqueId(),NativeInventoryCodec.saleItem(item),terms.quantity(),terms.nooks(),terms.paymentItem()==null?null:NativeInventoryCodec.saleItem(new ItemStack(Material.valueOf(terms.paymentItem()))),terms.paymentQuantity(),System.currentTimeMillis());
        // A failed block write leaves a zero-stock offer that can be closed from My shops.
        for(var side:containers(block)){
            side.getPersistentDataContainer().set(anchor,PersistentDataType.STRING,offer.id().toString());
            if(!side.update())throw new IllegalStateException("Could not save the shop container marker.");
        }
        player.sendMessage(NookUi.message("NookShops","Shop created. Use Add stock to deposit matching items from your inventory. The container itself stays empty."));open(player,offer,true);
    }
    private NativeShop.Offer at(Block block)throws Exception {
        var result=store.nativeAt(location(origin(block))).orElseThrow(()->new IllegalArgumentException("There is no native shop here."));
        for(var side:containers(block))if(!result.id().toString().equals(side.getPersistentDataContainer().get(anchor,PersistentDataType.STRING)))throw new IllegalArgumentException("The shop container changed. Its owner can close it from /nookshops mine.");
        if(!result.plot().equals(plot.apply(block)))throw new IllegalArgumentException("This shop is outside its configured plot.");
        for(var side:containers(block))if(!result.plot().equals(plot.apply(side.getBlock())))throw new IllegalArgumentException("This container crosses the plot boundary.");
        noChestShop(block);return result;
    }
    private NativeShop.Offer nearby(Player player,UUID expected)throws Exception {
        Block target=player.getTargetBlockExact(5);if(target==null)throw new IllegalArgumentException("Look at the shop container within five blocks.");
        var offer=at(target);if(!offer.id().equals(expected))throw new IllegalArgumentException("Look at this shop's container to trade or add stock.");return offer;
    }
    boolean review(org.bukkit.command.CommandSender sender,String[] args){
        if(args.length!=1 || !args[0].equalsIgnoreCase("review"))return false;
        if(!sender.hasPermission("nookcore.admin")){sender.sendMessage(NookUi.problem("NookShops","This command is for admins."));return true;}
        try{var reviews=store.nativeReviews();sender.sendMessage(NookUi.message("NookShops",reviews.isEmpty()?"No shop inventory operations need review.":"Inventory operations needing review (up to 50):"));for(String line:reviews)sender.sendMessage(NookUi.message("NookShops",line));}
        catch(Exception e){failure.accept(e);sender.sendMessage(NookUi.problem("NookShops","Could not read review records. Check the server log."));}return true;
    }
    boolean command(Player player,String[] args){
        String sub=args.length==0?"":args[0].toLowerCase(Locale.ROOT);
        if(!List.of("mine","manage","collect").contains(sub))return false;
        try{
            ready(player);
            if(sub.equals("mine")){
                var offers=store.nativeOffers(player.getUniqueId());player.sendMessage(NookUi.message("NookShops","Your shops · "+offers.size()));
                for(var offer:offers){String code=offer.id().toString().substring(0,8);player.sendMessage(NookUi.message("NookShops",code+" · "+name(offer.item())+" · "+offer.stock()+" in stock"+(offer.closed()?" · closed":"")).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/nookshops manage "+code)));}
                if(offers.isEmpty())player.sendMessage(NookUi.message("NookShops","Hold an item, look at an empty container in your plot and run /nookshops create."));
            }else if(sub.equals("manage")){
                NativeShop.Offer offer;
                if(args.length==1){Block target=player.getTargetBlockExact(5);if(target==null)throw new IllegalArgumentException("Look at your shop container.");offer=at(target);}
                else {var matches=store.nativeOffers(player.getUniqueId()).stream().filter(o->o.id().toString().startsWith(args[1].toLowerCase(Locale.ROOT))).toList();if(matches.size()!=1)throw new IllegalArgumentException("Choose one of your shops from /nookshops mine.");offer=matches.getFirst();}
                if(!offer.owner().equals(player.getUniqueId()))throw new IllegalArgumentException("Only the original owner can manage this shop.");open(player,offer,true);
            }else {
                var pending=store.nativeDeliveries(player.getUniqueId());var next=pending.stream().filter(r->{try{return store.nativeDeliveryState(r.id()).equals("PENDING");}catch(Exception e){throw new IllegalStateException(e);}}).findFirst();
                if(next.isEmpty()){
                    if(!pending.isEmpty())throw new IllegalArgumentException("Your earlier collection needs staff review. No items will be replayed.");
                    var balances=store.nativePaymentCollections(player.getUniqueId());
                    if(balances.isEmpty())throw new IllegalArgumentException("You have no items waiting for collection.");
                    UUID id=balances.getFirst();int amount=Math.min(64,store.nativePaymentBalance(player.getUniqueId(),id));
                    var payment=store.nativePayment(id).orElseThrow();
                    NativeInventoryPlan.deliver(new NativePlayerInventory(player).capture(),NativePlayerInventory.item(payment.item()),amount);
                    next=Optional.of(store.reserveNativePaymentReturn(UUID.randomUUID(),id,player.getUniqueId(),amount,System.currentTimeMillis()));
                }
                collect(player,next.get());player.sendMessage(NookUi.message("NookShops","Items collected. Run /nookshops collect again if more are waiting."));
            }
        }catch(Exception e){problem(player,e);}return true;
    }
    private void collect(Player p,NativeShop.Receipt receipt)throws Exception {custody.deliver(receipt.id(),p.getUniqueId(),NativePlayerInventory.item(receipt.item()),new NativePlayerInventory(p));}
    private static String name(byte[] encoded){return org.bukkit.inventory.ItemStack.deserializeBytes(encoded).getType().name().toLowerCase(Locale.ROOT).replace('_',' ');}
    private ItemStack icon(Material type,String title,String... lines){
        var item=new ItemStack(type);var meta=item.getItemMeta();meta.displayName(Component.text(title,NookUi.COMMAND).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,false));meta.lore(Arrays.stream(lines).map(s->Component.text(s,NookUi.BODY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,false)).toList());item.setItemMeta(meta);return item;
    }
    private String price(NativeShop.Offer offer)throws Exception {
        var payment=store.nativePayment(offer.id());return payment.isEmpty()?Money.format(offer.cents()):payment.get().quantity()+" × "+name(payment.get().item());
    }
    private boolean stockMember(NativeShop.Offer offer,UUID player)throws Exception {
        return new PlotPermissions(store).allowed(offer.plot(),player,PlotPermissions.Action.STOCK) && store.canTrade(offer.plot(),System.currentTimeMillis());
    }
    private void open(Player player,NativeShop.Offer offer,boolean owner)throws Exception {
        var menu=new Menu(player.getUniqueId(),offer,owner);menu.inventory=Bukkit.createInventory(menu,27,Component.text(owner?"NookShops · Your shop":"NookShops · Buy",NamedTextColor.DARK_GRAY));
        var item=ItemStack.deserializeBytes(offer.item());item.setAmount(Math.min(offer.bundle(),item.getMaxStackSize()));menu.inventory.setItem(13,item);
        menu.inventory.setItem(4,icon(Material.PAPER,offer.bundle()+" × "+name(offer.item()),"Price: "+price(offer)+" per bundle","Stock: "+offer.stock()+" items",offer.closed()?"Closed":"Plot: "+store.plotAddress(offer.plot())));
        if(owner){
            if(!offer.closed()){menu.inventory.setItem(11,icon(Material.CHEST,"Manage stock","Deposit or withdraw a chosen quantity.","Look at your shop container to manage it."));menu.inventory.setItem(22,icon(Material.RED_DYE,"Close shop","Stop sales. Stock remains yours to collect.","Create a new draft to change item, price or bundle."));}
            else menu.inventory.setItem(22,icon(Material.CHEST,"Collect remaining stock","Collect up to 64 items. Make room first.","Works even after your rental has ended."));
        }else menu.inventory.setItem(22,icon(Material.LIME_DYE,"Buy "+offer.bundle()+" for "+price(offer),"Click once to confirm this purchase.","Payment is credited even when the shop members are offline."));
        if(!owner && stockMember(offer,player.getUniqueId()) && !offer.closed()){
            menu.inventory.setItem(11,icon(Material.CHEST,"Manage stock","Deposit or withdraw a chosen quantity."));
            menu.inventory.setItem(22,icon(Material.PAPER,"You help stock this shop","Plot members cannot purchase from their own plot."));
        }
        var payment=store.nativePayment(offer.id());
        if(payment.isPresent()){
            var currency=icon(ItemStack.deserializeBytes(payment.get().item()).getType(),"Payment: "+price(offer),"Plain payment items only.","Owner collects item proceeds with /nookshops collect.");
            currency.setAmount(Math.min(payment.get().quantity(),currency.getMaxStackSize()));menu.inventory.setItem(15,currency);
        }
        menu.inventory.setItem(26,icon(Material.BARRIER,"Close"));player.openInventory(menu.inventory);
    }
    private void openStock(Player player,NativeShop.Offer offer,int amount)throws Exception {
        if(!stockMember(offer,player.getUniqueId()) || offer.closed())throw new IllegalArgumentException("You need stock access to an open shop.");
        Menu menu=new Menu(player.getUniqueId(),offer,offer.owner().equals(player.getUniqueId()));menu.stock=true;menu.amount=Math.clamp(amount,1,64);
        menu.inventory=Bukkit.createInventory(menu,27,Component.text("NookShops · Stock",NamedTextColor.DARK_GRAY));
        menu.inventory.setItem(4,icon(Material.PAPER,"Stock: "+offer.stock()+" items","Selected amount: "+menu.amount,"Click an inventory stack to deposit it.","Right-click an inventory stack to deposit one."));
        var item=ItemStack.deserializeBytes(offer.item());item.setAmount(Math.min(menu.amount,item.getMaxStackSize()));menu.inventory.setItem(13,item);
        menu.inventory.setItem(10,icon(Material.RED_DYE,"Fewer items","Click: -1 · Shift-click: -8"));
        menu.inventory.setItem(16,icon(Material.LIME_DYE,"More items","Click: +1 · Shift-click: +8"));
        menu.inventory.setItem(11,icon(Material.PAPER,"Select 1"));menu.inventory.setItem(15,icon(Material.PAPER,"Select 64"));
        menu.inventory.setItem(18,icon(Material.CHEST,"Deposit "+menu.amount,"Moves matching items from your inventory."));
        menu.inventory.setItem(22,icon(Material.HOPPER,"Withdraw "+menu.amount,"The shop stays open. Make room first."));
        menu.inventory.setItem(26,icon(Material.ARROW,"Back"));player.openInventory(menu.inventory);
    }
    private void stockClick(Player player,Menu menu,int slot,ClickType click,ItemStack clicked){
        if(!List.of(ClickType.LEFT,ClickType.RIGHT,ClickType.SHIFT_LEFT,ClickType.SHIFT_RIGHT).contains(click))return;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!player.isOnline() || player.getOpenInventory().getTopInventory()!=menu.inventory)return;
            player.closeInventory();
            try{
                ready(player);var offer=nearby(player,menu.offer);UUID actor=player.getUniqueId();
                if(offer.closed() || !stockMember(offer,actor))throw new IllegalArgumentException("You no longer have stock access to this shop.");
                if(slot==26){open(player,offer,offer.owner().equals(actor));return;}
                int selected=menu.amount;
                if(slot==10 || slot==16)selected=Math.clamp(selected+(slot==10?-1:1)*(click.isShiftClick()?8:1),1,64);
                else if(slot==11)selected=1;
                else if(slot==15)selected=64;
                else if(slot==18 || slot==22 || slot>=27){
                    var inventory=new NativePlayerInventory(player);var exact=NativePlayerInventory.item(offer.item());int count=selected;
                    if(slot>=27){
                        if(clicked==null || clicked.getType().isAir())return;
                        if(!Base64.getEncoder().encodeToString(NativeInventoryCodec.saleItem(clicked)).equals(exact.key()))throw new IllegalArgumentException("Only exactly matching sale items can be stocked here.");
                        count=click==ClickType.RIGHT || click==ClickType.SHIFT_RIGHT?1:Math.min(64,clicked.getAmount());
                    }
                    if(slot==22){
                        NativeInventoryPlan.deliver(inventory.capture(),exact,count);
                        var receipt=store.reserveNativeStockWithdrawal(UUID.randomUUID(),offer.id(),actor,count,System.currentTimeMillis());collect(player,receipt);
                    }else custody.deposit(offer.id(),actor,count,exact.key(),inventory,System.currentTimeMillis());
                    player.sendMessage(NookUi.message("NookShops",(slot==22?"Withdrew ":"Deposited ")+count+" items."));
                }else {openStock(player,offer,selected);return;}
                openStock(player,store.nativeOffer(offer.id()),selected);
            }catch(Exception error){problem(player,error);}
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void interact(PlayerInteractEvent event){
        if(!enabled() || event.getHand()!=EquipmentSlot.HAND || event.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || event.getClickedBlock()==null)return;
        var block=event.getClickedBlock();if(!(block.getState() instanceof Container container) || !container.getPersistentDataContainer().has(anchor,PersistentDataType.STRING))return;
        event.setCancelled(true);
        var p=event.getPlayer();
        try{ready(p);var offer=at(block);open(p,offer,offer.owner().equals(p.getUniqueId()));}catch(Exception e){problem(p,e);}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof Menu menu))return;e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p) || !menu.viewer.equals(p.getUniqueId()) || e.getRawSlot()<0)return;
        if(menu.stock){stockClick(p,menu,e.getRawSlot(),e.getClick(),e.getCurrentItem()==null?null:e.getCurrentItem().clone());return;}
        if(e.getRawSlot()>=27)return;
        int slot=e.getRawSlot();if(slot!=11 && slot!=22 && slot!=26)return;Bukkit.getScheduler().runTask(plugin,()->{
            if(!p.isOnline() || p.getOpenInventory().getTopInventory()!=menu.inventory)return;
            p.closeInventory(); // A queued duplicate click can no longer spend again.
            try{
                ready(p);if(slot==26)return;var offer=store.nativeOffer(menu.offer);long now=System.currentTimeMillis();
                if(menu.owner || slot==11 && stockMember(offer,p.getUniqueId())){
                    if(slot!=11 && !offer.owner().equals(p.getUniqueId()))throw new IllegalArgumentException("Only the original owner can close this shop or collect its stock.");
                    if(slot==11 && !offer.closed()){
                        offer=nearby(p,offer.id());openStock(p,offer,1);return;
                    }else if(slot==22 && !offer.closed()){store.closeNativeOffer(offer.id(),p.getUniqueId());p.sendMessage(NookUi.message("NookShops","Shop closed. Collect its remaining stock from this menu."));}
                    else if(slot==22){
                        int amount=Math.min(64,offer.stock());if(amount==0)throw new IllegalArgumentException("No stock remains.");
                        NativeInventoryPlan.deliver(new NativePlayerInventory(p).capture(),NativePlayerInventory.item(offer.item()),amount);
                        var receipt=store.reserveNativeStockReturn(UUID.randomUUID(),offer.id(),p.getUniqueId(),amount,now);collect(p,receipt);p.sendMessage(NookUi.message("NookShops","Remaining stock collected."));
                    }else return;
                    open(p,store.nativeOffer(offer.id()),offer.owner().equals(p.getUniqueId()));
                }else if(slot==22){
                    offer=nearby(p,offer.id());if(store.nativeNeedsReview(p.getUniqueId()))throw new IllegalArgumentException("An earlier inventory operation needs staff review first.");
                    if(offer.revision()!=menu.revision || offer.closed() || !store.canTrade(offer.plot(),now))throw new IllegalArgumentException("This shop changed or its rent expired. Review it again.");
                    if(offer.stock()<offer.bundle())throw new IllegalArgumentException("Not enough stock.");
                    if(!store.role(offer.plot(),p.getUniqueId()).equals("NONE"))throw new IllegalArgumentException("Plot members cannot purchase from their own plot.");
                    var inventory=new NativePlayerInventory(p);var payment=store.nativePayment(offer.id());
                    if(payment.isPresent()){
                        int needed=Math.max(0,payment.get().quantity()-store.nativePaymentBalance(p.getUniqueId(),offer.id()));
                        var paymentItem=NativePlayerInventory.item(payment.get().item());
                        var afterPayment=needed==0?inventory.capture():NativeInventoryPlan.deposit(inventory.capture(),paymentItem.key(),needed).after();
                        NativeInventoryPlan.deliver(afterPayment,NativePlayerInventory.item(offer.item()),offer.bundle());
                        if(needed>0)custody.payment(offer.id(),p.getUniqueId(),needed,paymentItem.key(),inventory,now);
                    }else NativeInventoryPlan.deliver(inventory.capture(),NativePlayerInventory.item(offer.item()),offer.bundle());
                    var receipt=store.settleNativePurchase(UUID.randomUUID(),offer.id(),p.getUniqueId(),menu.revision,now);collect(p,receipt);
                    p.sendMessage(NookUi.message("NookShops","Bought "+receipt.quantity()+" × "+name(receipt.item())+" for "+price(offer)+"."));
                }
            }catch(Exception error){problem(p,error);}
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
    private void problem(Player p,Exception e){
        if(e instanceof IllegalArgumentException){p.sendMessage(NookUi.problem("NookShops",e.getMessage()));return;}
        failure.accept(e);p.sendMessage(NookUi.problem("NookShops","Shops are paused after an inventory or storage error. Ask staff to review the receipt before retrying; items will not be replayed automatically."));
    }
}

