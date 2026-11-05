package net.nikosnook.core;

import java.sql.SQLException;
import java.util.*;
import java.util.function.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Minigame discovery and automatic delivery of saved game rewards. */
final class GamesCommand implements CommandExecutor,TabCompleter,Listener {
    private final JavaPlugin plugin;
    private final GameLedger ledger;
    private final BooleanSupplier healthy,wordle,flips;
    private final Consumer<Exception> failure;
    GamesCommand(JavaPlugin plugin,NookStore store,BooleanSupplier healthy,Consumer<Exception> failure,BooleanSupplier wordle,BooleanSupplier flips){
        this.plugin=plugin;ledger=new GameLedger(store);this.healthy=healthy;this.failure=failure;this.wordle=wordle;this.flips=flips;
        var command=Objects.requireNonNull(plugin.getCommand("games"));command.setExecutor(this);command.setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        // A cap-blocked credit stays durable until the owner makes room in their balance.
        Bukkit.getScheduler().runTaskTimer(plugin,()->{
            if(healthy.getAsBoolean())Bukkit.getOnlinePlayers().forEach(this::recover);
        },100,100);
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player player)){sender.sendMessage(NookUi.message("games","play minigames in Minecraft."));return true;}
        if(!player.hasPermission("nookcore.games")){player.sendMessage(NookUi.problem("games","the games corner isn't available for you right now."));return true;}
        if(!healthy.getAsBoolean()){player.sendMessage(NookUi.message("games","minigames are paused. please try again later."));return true;}
        if(args.length==1 && args[0].equalsIgnoreCase("collect")){
            try{
                long paid=claim(player.getUniqueId());
                long waiting=ledger.transact(tx->Math.addExact(tx.pendingCredits(player.getUniqueId()),WordleStore.pendingRewards(tx,player.getUniqueId())));
                if(paid>0)creditNotice(player,paid);
                if(waiting>0)player.sendMessage(NookUi.message("games",Money.format(waiting)+" will be added when there's room in your balance."));
                else if(paid==0)player.sendMessage(NookUi.message("games","all your game Nooks are in your balance."));
            }catch(SQLException error){failure.accept(error);player.sendMessage(NookUi.problem("games","couldn't check your rewards. please ask staff for help."));}
            return true;
        }
        if(args.length>1 || args.length==1 && !args[0].equalsIgnoreCase("help")){
            player.sendMessage(NookUi.message("games","try /games help."));return true;
        }
        player.sendMessage(NookUi.heading("minigames"));
        boolean showWordle=wordle.getAsBoolean() && player.hasPermission("nookcore.wordle");
        boolean showFlips=flips.getAsBoolean() && player.hasPermission(CoinFlipGame.PERMISSION);
        if(showWordle)player.sendMessage(NookUi.command("/wordle").append(NookUi.text(" · a daily word puzzle")));
        if(showFlips)player.sendMessage(NookUi.command("/coinflip").append(NookUi.text(" · heads or tails?")));
        if(!showWordle && !showFlips)player.sendMessage(NookUi.text("minigames are paused. come back soon <3"));
        return true;
    }
    private long claim(UUID player)throws SQLException {
        return ledger.transact(tx->{
            long now=System.currentTimeMillis();
            long coins=tx.pendingCredits(player)>0?tx.claimCredits(player,now):0;
            return Math.addExact(coins,WordleStore.claimPending(tx,player,now));
        });
    }
    void recover(Player player){
        if(!healthy.getAsBoolean() || !player.isOnline())return;
        try{
            long paid=claim(player.getUniqueId());
            if(paid>0)creditNotice(player,paid);
        }catch(SQLException error){failure.accept(error);}
    }
    private static void creditNotice(Player player,long paid){
        player.sendMessage(NookUi.message("games","+"+Money.format(paid)+" in your balance."));
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void join(PlayerJoinEvent event){
        Bukkit.getScheduler().runTaskLater(plugin,()->recover(event.getPlayer()),1);
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        return sender.hasPermission("nookcore.games") && args.length==1?NookUi.complete(args[0],List.of("help")):List.of();
    }
}
