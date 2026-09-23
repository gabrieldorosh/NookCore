package net.nikosnook.core;

import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import java.util.*;
import java.util.function.*;

/** A one-use exception for an explicitly authorised, synchronous moderation teleport. */
final class AdminTeleports implements Listener {
    private record Permit(Location destination) {}
    private final Map<UUID,Permit> permits=new HashMap<>();
    private final Function<String,Player> players;
    private final Consumer<String> audit;
    AdminTeleports(Function<String,Player> players,Consumer<String> audit){this.players=players;this.audit=audit;}
    static boolean authorised(CommandSender sender){
        return sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender
            || sender instanceof Player && sender.hasPermission("nookcore.admin") && sender.hasPermission("nookcore.travel.bypass");
    }
    boolean consume(PlayerTeleportEvent event){
        Permit permit=permits.get(event.getPlayer().getUniqueId());
        if(permit==null || event.getCause()!=PlayerTeleportEvent.TeleportCause.PLUGIN || !permit.destination().equals(event.getTo()))return false;
        permits.remove(event.getPlayer().getUniqueId());return true;
    }
    void execute(CommandSender sender,String[] args){
        if(!authorised(sender)){sender.sendMessage(NookUi.problem("NookAdmin","Only console and authorised admins can use moderation teleports."));return;}
        if(args.length!=3){sender.sendMessage(NookUi.message("NookAdmin","Use /nookadmin teleport <player> <destination-player>. Both players must be online; use their exact names."));return;}
        Player moved=players.apply(args[1]),destination=players.apply(args[2]);
        if(moved==null || destination==null){sender.sendMessage(NookUi.problem("NookAdmin","Both players must be online. Use their exact names, without selectors."));return;}
        if(moved.getUniqueId().equals(destination.getUniqueId())){sender.sendMessage(NookUi.problem("NookAdmin","Choose two different players."));return;}
        Location location=destination.getLocation().clone();
        if(location.getWorld()==null){sender.sendMessage(NookUi.problem("NookAdmin","The destination world is unavailable."));return;}
        UUID id=moved.getUniqueId();
        if(permits.containsKey(id)){sender.sendMessage(NookUi.problem("NookAdmin","A moderation teleport is already in progress for that player."));return;}
        Permit permit=new Permit(location.clone());permits.put(id,permit);
        try{
            boolean success=moved.teleport(location,PlayerTeleportEvent.TeleportCause.PLUGIN);
            audit.accept("Moderation teleport by "+sender.getName()+": "+moved.getName()+" to "+destination.getName()+" ["+(success?"completed":"cancelled")+"]");
            if(success){
                sender.sendMessage(NookUi.message("NookAdmin","Teleported ").append(NookUi.name(id,moved.getName())).append(NookUi.text(" to ")).append(NookUi.name(destination.getUniqueId(),destination.getName())).append(NookUi.text(".")));
                moved.sendMessage(NookUi.message("NookAdmin","An admin moved you to ").append(NookUi.name(destination.getUniqueId(),destination.getName())).append(NookUi.text(" for moderation.")));
            }else sender.sendMessage(NookUi.problem("NookAdmin","The teleport was cancelled. Check protection rules or other plugins."));
        }catch(RuntimeException e){
            audit.accept("Moderation teleport by "+sender.getName()+" for "+moved.getName()+" failed: "+e.getClass().getSimpleName());
            sender.sendMessage(NookUi.problem("NookAdmin","The teleport could not be confirmed. Check the player's position before retrying."));
        }finally{permits.remove(id,permit);}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void consoleCommand(ServerCommandEvent event){
        routeConsole(event.getSender(),event.getCommand(),()->event.setCancelled(true));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void remoteCommand(RemoteServerCommandEvent event){consoleCommand(event);}
    void routeConsole(CommandSender sender,String command,Runnable cancel){
        if(!(sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender))return;
        String[] words=command.trim().split("\\s+");
        String root=words[0].toLowerCase(Locale.ROOT);if(root.startsWith("/"))root=root.substring(1);
        if(!Set.of("tp","teleport","minecraft:tp","minecraft:teleport").contains(root))return;
        if(words.length==3 && players.apply(words[1])!=null && players.apply(words[2])!=null){
            cancel.run();execute(sender,new String[]{"teleport",words[1],words[2]});
        }
    }
}
