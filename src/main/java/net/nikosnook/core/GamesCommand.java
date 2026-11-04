package net.nikosnook.core;

import java.sql.SQLException;
import java.util.*;
import java.util.function.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

/** A small discovery page and collection point for already-earned game money. */
final class GamesCommand implements CommandExecutor,TabCompleter {
    private final GameLedger ledger;
    private final BooleanSupplier healthy,wordle,flips;
    private final Consumer<Exception> failure;
    GamesCommand(org.bukkit.plugin.java.JavaPlugin plugin,NookStore store,BooleanSupplier healthy,Consumer<Exception> failure,BooleanSupplier wordle,BooleanSupplier flips){
        ledger=new GameLedger(store);this.healthy=healthy;this.failure=failure;this.wordle=wordle;this.flips=flips;
        var command=Objects.requireNonNull(plugin.getCommand("games"));command.setExecutor(this);command.setTabCompleter(this);
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player player)){sender.sendMessage(NookUi.message("Games","visit the games corner in Minecraft <3"));return true;}
        if(!player.hasPermission("nookcore.games")){player.sendMessage(NookUi.problem("Games","the games corner isn't available for you right now."));return true;}
        if(!healthy.getAsBoolean()){player.sendMessage(NookUi.message("Games","the games corner is taking a little rest. please try again later <3"));return true;}
        try{
            if(args.length==1 && args[0].equalsIgnoreCase("collect")){
                long paid=ledger.transact(tx->{long now=System.currentTimeMillis();return Math.addExact(tx.claimCredits(player.getUniqueId(),now),WordleStore.claimPending(tx,player.getUniqueId(),now));});
                long waiting=ledger.transact(tx->Math.addExact(tx.pendingCredits(player.getUniqueId()),WordleStore.pendingRewards(tx,player.getUniqueId())));
                if(paid>0)player.sendMessage(NookUi.message("Nooks","+"+Money.format(paid)+" · game rewards and returned stakes"));
                if(waiting>0)player.sendMessage(NookUi.message("Games",Money.format(waiting)+" is waiting safely. make a little room in your balance, then try /games collect."));
                else if(paid==0)player.sendMessage(NookUi.message("Games","all caught up! there's nothing waiting to collect :3"));
                return true;
            }
            if(args.length>1 || args.length==1 && !args[0].equalsIgnoreCase("help")){
                player.sendMessage(NookUi.message("Games","not quite right! try /games help"));return true;
            }
            player.sendMessage(NookUi.heading("a little break in the nook"));
            boolean any=false;
            if(wordle.getAsBoolean()){
                player.sendMessage(NookUi.command("/wordle").append(NookUi.text(" · five little letters, six guesses, a new puzzle each day")));any=true;
            }
            if(flips.getAsBoolean()){
                player.sendMessage(NookUi.command("/coinflip").append(NookUi.text(" · a friendly 50/50 with another nookling")));any=true;
            }
            long waiting=ledger.transact(tx->Math.addExact(tx.pendingCredits(player.getUniqueId()),WordleStore.pendingRewards(tx,player.getUniqueId())));
            if(waiting>0)player.sendMessage(NookUi.command("/games collect").append(NookUi.text(" · "+Money.format(waiting)+" waiting safely")));
            if(!any && waiting==0)player.sendMessage(NookUi.text("the games corner is having a little rest. come back soon <3"));
        }catch(IllegalArgumentException error){player.sendMessage(NookUi.problem("Games",error.getMessage()));}
        catch(SQLException error){failure.accept(error);player.sendMessage(NookUi.problem("Games","your game Nooks are kept safely. please ask staff for a hand."));}
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        return sender.hasPermission("nookcore.games") && args.length==1?NookUi.complete(args[0],List.of("help","collect")):List.of();
    }
}
