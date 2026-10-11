package net.nikosnook.core;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
/** Session-scoped view-only InvSee++ access for helpers, independent of staff mode. */
final class HelperInspection implements Listener {
 private final JavaPlugin plugin;private final Map<UUID,PermissionAttachment> attachments=new HashMap<>();
 HelperInspection(JavaPlugin plugin){this.plugin=plugin;Bukkit.getPluginManager().registerEvents(this,plugin);Bukkit.getScheduler().runTaskTimer(plugin,()->{for(var p:Bukkit.getOnlinePlayers())refresh(p);},1,100);}
 private void refresh(Player p){boolean helper=p.hasPermission("nookcore.staff.use")&&!p.hasPermission("nookcore.admin");
  if(helper && !attachments.containsKey(p.getUniqueId())){var a=p.addAttachment(plugin);a.setPermission("invseeplusplus.invsee.view",true);a.setPermission("invseeplusplus.invsee.edit",false);a.setPermission("invseeplusplus.endersee.edit",false);attachments.put(p.getUniqueId(),a);}
  else if(!helper){var a=attachments.remove(p.getUniqueId());if(a!=null)p.removeAttachment(a);}}
 @EventHandler public void join(PlayerJoinEvent e){refresh(e.getPlayer());}
 @EventHandler public void quit(PlayerQuitEvent e){var a=attachments.remove(e.getPlayer().getUniqueId());if(a!=null)e.getPlayer().removeAttachment(a);}
}
