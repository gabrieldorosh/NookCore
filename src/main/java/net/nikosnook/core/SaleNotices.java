package net.nikosnook.core;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

/** Cosmetic summaries only: payments remain in the existing transactional ledger. */
final class SaleNotices implements Listener {
 record Key(UUID recipient,String plot){}
 record Batch(long total,long share,boolean shared,long due){}
 private final JavaPlugin plugin;private final NookStore store;
 private final Map<Key,Batch> batches=new HashMap<>();
 SaleNotices(JavaPlugin plugin,NookStore store){this.plugin=plugin;this.store=store;Bukkit.getPluginManager().registerEvents(this,plugin);
  Bukkit.getScheduler().runTaskTimer(plugin,this::flush,20,20);
  Bukkit.getScheduler().runTaskTimer(plugin,()->Bukkit.getOnlinePlayers().forEach(this::remind),144000,144000);
 }
 static Batch add(Batch old,long total,long share,boolean shared,long now){return new Batch(Math.addExact(old==null?0:old.total,total),Math.addExact(old==null?0:old.share,share),shared || old!=null&&old.shared,now+15000);}
 void sale(NativeShop.Offer offer,NativeShop.Receipt receipt){try{
  if(receipt.cents()==0)return; // Item proceeds receive the collection reminder, not a money claim.
  var shares=new LinkedHashMap<>(store.nativeSaleShares(receipt.id()));var members=store.members(offer.plot());boolean shared=members.size()>1;members.keySet().forEach(id->shares.putIfAbsent(id,0L));
  for(var entry:shares.entrySet()){Player recipient=Bukkit.getPlayer(entry.getKey());if(recipient==null)continue;var key=new Key(entry.getKey(),offer.plot());batches.put(key,add(batches.get(key),receipt.cents(),entry.getValue(),shared,System.currentTimeMillis()));}
 }catch(Exception e){plugin.getLogger().warning("Sale notification unavailable; payments were already recorded: "+e.getClass().getSimpleName());}}
 private void flush(){long now=System.currentTimeMillis();var it=batches.entrySet().iterator();while(it.hasNext()){var e=it.next();var b=e.getValue();if(now<b.due)continue;it.remove();Player p=Bukkit.getPlayer(e.getKey().recipient);if(p!=null)p.sendMessage(NookUi.message("NookShops",Money.format(b.total)+" earnt from your shop!"+(b.shared?" Your share: "+Money.format(b.share):"")));}}
 private void remind(Player p){try{if(!p.isOnline() || StaffMode.active(p.getUniqueId()))return;if(!store.nativePaymentCollections(p.getUniqueId()).isEmpty() || !store.nativeDeliveries(p.getUniqueId()).isEmpty() || store.nativeOffers(p.getUniqueId()).stream().anyMatch(o->o.closed()&&o.stock()>0))p.sendMessage(NookUi.message("NookShops","You have items awaiting collection. Check your shops."));}catch(Exception e){plugin.getLogger().warning("Shop collection reminder unavailable: "+e.getClass().getSimpleName());}}
 @EventHandler public void join(PlayerJoinEvent e){Bukkit.getScheduler().runTaskLater(plugin,()->remind(e.getPlayer()),100);}
 @EventHandler public void quit(PlayerQuitEvent e){batches.keySet().removeIf(k->k.recipient.equals(e.getPlayer().getUniqueId()));}
}
