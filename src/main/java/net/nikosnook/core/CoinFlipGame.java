package net.nikosnook.core;

import java.sql.SQLException;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Private player-to-player challenges. Animation and messages never decide the money. */
final class CoinFlipGame implements CommandExecutor,TabCompleter,Listener,AutoCloseable {
    static final String PERMISSION="nookcore.coinflip";
    private final NookStore store;
    private final WagerService wagers;
    private final CoinFlipSettings settings;
    private final BooleanSupplier healthy;
    private final Consumer<Exception> failure;
    private final JavaPlugin plugin;
    private final BukkitTask expiry;
    private final Function<UUID,Player> playersById;
    private final Function<String,Player> playersByName;
    private final Supplier<Collection<? extends Player>> onlinePlayers;
    private Consumer<Player> played=player->{};
    private boolean milestoneWarning;

    CoinFlipGame(JavaPlugin plugin,NookStore store,BooleanSupplier healthy,Consumer<Exception> failure)throws SQLException {
        this(plugin,store,healthy,failure,Bukkit::getPlayer,Bukkit::getPlayerExact,()->Bukkit.getOnlinePlayers());
    }
    CoinFlipGame(JavaPlugin plugin,NookStore store,BooleanSupplier healthy,Consumer<Exception> failure,
                 Function<UUID,Player> playersById,Function<String,Player> playersByName,
                 Supplier<Collection<? extends Player>> onlinePlayers)throws SQLException {
        this.plugin=plugin;this.store=store;this.healthy=healthy;this.failure=failure;
        this.playersById=Objects.requireNonNull(playersById);this.playersByName=Objects.requireNonNull(playersByName);this.onlinePlayers=Objects.requireNonNull(onlinePlayers);
        settings=CoinFlipSettings.load(plugin.getConfig().getConfigurationSection("games.coinflip"));
        wagers=new WagerService(new GameLedger(store),settings);
        var command=Objects.requireNonNull(plugin.getCommand("coinflip"));command.setExecutor(this);command.setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        // Recovery remains active while new flips are disabled: old stakes still belong to players.
        expiry=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            if(!healthy.getAsBoolean())return;
            try{for(var match:wagers.expireDue(System.currentTimeMillis()))deliverParticipants(match);}
            catch(SQLException error){failure.accept(error);}
        },20,20);
    }

    /** May also replay for historical recovery; the milestone receiver must be idempotent. */
    void onPlayed(Consumer<Player> callback){played=Objects.requireNonNull(callback);}
    boolean enabled(){return settings.enabled();}
    private void milestone(Player player){
        try{played.accept(player);}
        catch(RuntimeException error){if(!milestoneWarning){milestoneWarning=true;plugin.getLogger().warning("The coin-flip milestone could not be shown; saved results and Nooks are unaffected.");}}
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!sender.hasPermission(PERMISSION)){sender.sendMessage(NookUi.problem("Coin flip","you don't have permission to play coin flips."));return true;}
        if(args.length>=1 && args[0].equalsIgnoreCase("help")){help(sender);return true;}
        if(!(sender instanceof Player player)){sender.sendMessage(NookUi.message("Coin flip","play coin flips in game."));return true;}
        if(!healthy.getAsBoolean()){sender.sendMessage(NookUi.problem("Coin flip","games are taking a little break. please ask staff for a hand."));return true;}
        if(!eligible(player)){sender.sendMessage(NookUi.problem("Coin flip","play coin flips while in Survival and out of staff mode."));return true;}
        try{
            String sub=args.length==0?"":args[0].toLowerCase(Locale.ROOT);
            if(sub.equals("history")){if(args.length!=1)throw new IllegalArgumentException("/coinflip history");history(player);return true;}
            for(var match:wagers.expireDue(System.currentTimeMillis()))deliverParticipants(match);
            switch(sub){
                case "help"->{help(sender);return true;}
                case "collect"->{
                    if(args.length!=1)throw new IllegalArgumentException("/coinflip collect");
                    long received=wagers.collect(player.getUniqueId(),System.currentTimeMillis());
                    if(received>0)sender.sendMessage(NookUi.message("Coin flip","collected "+Money.format(received)+"!"));
                    long waiting=wagers.waiting(player.getUniqueId());
                    sender.sendMessage(NookUi.message("Coin flip",waiting==0?"all your game Nooks are in your balance!":"you have "+Money.format(waiting)+" waiting safely. make a little room in your balance, then try again."));return true;
                }
                case "cancel"->{
                    if(args.length!=1)throw new IllegalArgumentException("/coinflip cancel");
                    var match=wagers.outgoing(player.getUniqueId()).orElseThrow(()->new IllegalArgumentException("you don't have a coin flip waiting."));
                    deliverParticipants(wagers.cancel(match.id(),player.getUniqueId(),System.currentTimeMillis()));return true;
                }
                case "accept","decline"->{
                    if(args.length!=2)throw new IllegalArgumentException("/coinflip "+sub+" <player>");
                    var creator=store.byName(args[1]).orElseThrow(()->new IllegalArgumentException("there isn't a waiting invitation from that player."));
                    var match=wagers.invitation(creator.id(),player.getUniqueId()).orElseThrow(()->new IllegalArgumentException("there isn't a waiting invitation from that player."));
                    if(sub.equals("accept")){
                        var other=playersById.apply(creator.id());
                        if(other==null || !eligible(other))throw new IllegalArgumentException("both players need to be here and ready to play. you can still decline the invitation.");
                        deliverParticipants(wagers.accept(match.id(),player.getUniqueId(),System.currentTimeMillis()));
                    }else deliverParticipants(wagers.decline(match.id(),player.getUniqueId(),System.currentTimeMillis()));
                    return true;
                }
                case ""->{summary(player);return true;}
                case "invite"->{
                    if(args.length!=3)throw new IllegalArgumentException("/coinflip invite <player> <amount>");
                    createInvitation(player,args[1],args[2]);return true;
                }
                default->{
                    if(args.length!=2){help(sender);return true;}
                    createInvitation(player,args[0],args[1]);return true;
                }
            }
        }catch(IllegalArgumentException error){sender.sendMessage(NookUi.problem("Coin flip",error.getMessage()));}
        catch(SQLException error){failure.accept(error);sender.sendMessage(NookUi.problem("Coin flip","something went wrong saving that flip. please ask staff to check it before trying again."));}
        return true;
    }

    private void createInvitation(Player player,String targetName,String amount)throws SQLException {
        if(!settings.enabled())throw new IllegalArgumentException("coin flips aren't open just yet.");
        var target=playersByName.apply(targetName);
        if(target==null || !eligible(target))throw new IllegalArgumentException("that player isn't available for a coin flip right now.");
        var match=wagers.invite(player.getUniqueId(),target.getUniqueId(),Money.parse(amount),System.currentTimeMillis());
        player.sendMessage(NookUi.message("Coin flip","invited "+target.getName()+"! your "+Money.format(match.stake())+" stake is held until they reply or the invitation expires."));
        player.sendMessage(NookUi.command("cancel invitation","/coinflip cancel").clickEvent(ClickEvent.runCommand("/coinflip cancel")));
        invite(target,match);
    }
    private void summary(Player player)throws SQLException {
        if(!settings.enabled())player.sendMessage(NookUi.message("Coin flip","coin flips aren't open just yet. waiting stakes can still be cancelled or collected."));
        else {
            player.sendMessage(NookUi.heading("a little coin flip?"));
            player.sendMessage(NookUi.text("equal stakes · 50/50 · winner takes the full pot · no fee"));
            player.sendMessage(NookUi.command("/coinflip invite <player> <amount>").append(NookUi.text(" · "+Money.format(settings.minimum())+" to "+Money.format(settings.maximum())+" each")));
        }
        var outgoing=wagers.outgoing(player.getUniqueId());
        if(outgoing.isPresent()){
            var match=outgoing.get();player.sendMessage(NookUi.text("waiting for "+name(match.target())+" · your stake: "+Money.format(match.stake())));
            player.sendMessage(NookUi.command("cancel invitation","/coinflip cancel").clickEvent(ClickEvent.runCommand("/coinflip cancel")));
        }
        for(var match:wagers.incoming(player.getUniqueId()))invite(player,match);
        for(var match:wagers.notices(player.getUniqueId()))deliver(player,match);
        if(wagers.hasPlayed(player.getUniqueId()))milestone(player);
        long waiting=wagers.waiting(player.getUniqueId());
        if(waiting>0)player.sendMessage(NookUi.message("Coin flip",Money.format(waiting)+" is waiting safely. use /coinflip collect when there's room in your balance."));
    }
    private void invite(Player target,WagerService.Match match)throws SQLException {
        String creator=name(match.creator());
        target.sendMessage(NookUi.message("Coin flip",creator+" would like a coin flip! stake "+Money.format(match.stake())+" each for a "+Money.format(match.pot())+" pot."));
        target.sendMessage(NookUi.text("50/50 · full pot · no fee · invitation lasts "+(settings.invitationMillis()/1000)+" seconds"));
        Component actions=NookUi.command("decline","/coinflip decline "+creator).clickEvent(ClickEvent.runCommand("/coinflip decline "+creator));
        if(settings.enabled())actions=NookUi.command("accept","/coinflip accept "+creator).clickEvent(ClickEvent.runCommand("/coinflip accept "+creator)).append(NookUi.text("  ·  ")).append(actions);
        target.sendMessage(actions);
    }
    private void history(Player player)throws SQLException {
        player.sendMessage(NookUi.heading("your recent coin flips"));
        var matches=wagers.history(player.getUniqueId());if(matches.isEmpty())player.sendMessage(NookUi.text("no coin flips yet!"));
        for(var match:matches){
            UUID other=match.creator().equals(player.getUniqueId())?match.target():match.creator();
            String result=match.state()==WagerService.State.SETTLED?(match.winner().equals(player.getUniqueId())?"won "+Money.format(match.pot()):"lost "+Money.format(match.stake())):match.state().name().toLowerCase(Locale.ROOT);
            player.sendMessage(NookUi.text(name(other)+" · "+Money.format(match.stake())+" each · "+result+" · "+NookUi.date(match.created())));
        }
    }
    private void deliverParticipants(WagerService.Match match)throws SQLException {
        for(UUID id:List.of(match.creator(),match.target())){
            var player=playersById.apply(id);if(player!=null && eligible(player)){
                // A duplicate command may return an existing committed match; do not repeat its notice.
                if(wagers.notices(id).stream().anyMatch(waiting->waiting.id().equals(match.id())))deliver(player,match);
            }
        }
    }
    private void deliver(Player player,WagerService.Match match)throws SQLException {
        if(match.state()==WagerService.State.INVITED)return;
        if(match.state()==WagerService.State.SETTLED){
            player.sendMessage(NookUi.prefix("Coin flip").append(NookUi.text(match.side().name().toLowerCase(Locale.ROOT)+"! "))
                .append(NookUi.name(match.winner(),name(match.winner()))).append(NookUi.text(" won "+Money.format(match.pot())+"! <3")));
            try{player.playSound(player.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,0.6f,1.4f);}catch(RuntimeException ignored){/* The result is already committed. */}
            if(match.winner().equals(player.getUniqueId()) && wagers.waiting(player.getUniqueId())>0)
                player.sendMessage(NookUi.text("some Nooks are waiting safely. use /coinflip collect when there's room in your balance."));
        }else {
            UUID other=match.creator().equals(player.getUniqueId())?match.target():match.creator();
            String message="the coin flip with "+name(other)+(match.state()==WagerService.State.EXPIRED?" has expired.":" was "+match.state().name().toLowerCase(Locale.ROOT)+".");
            if(match.creator().equals(player.getUniqueId()))message+=wagers.waiting(player.getUniqueId())==0?" your stake has been returned.":" your stake is safe; some Nooks are waiting to collect.";
            player.sendMessage(NookUi.message("Coin flip",message));
        }
        wagers.acknowledge(match.id(),player.getUniqueId());
        if(match.state()==WagerService.State.SETTLED)milestone(player);
    }
    private String name(UUID id)throws SQLException {return store.account(id).map(NookStore.Account::name).orElse("a nookling");}
    private static boolean eligible(Player player){return player.hasPermission(PERMISSION)&&player.isOnline()&&!player.isDead()&&player.getGameMode()==GameMode.SURVIVAL&&!StaffMode.active(player.getUniqueId());}
    private static void help(CommandSender sender){
        NookUi.help(sender,"coin flips","/coinflip invite <player> <amount> — invite someone to equal stakes",
            "/coinflip accept <player> — accept their waiting invitation","/coinflip decline <player> — decline it",
            "/coinflip cancel — return your waiting stake","/coinflip collect — collect any waiting game Nooks","/coinflip history — see your last five flips");
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void join(PlayerJoinEvent event){
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            var player=event.getPlayer();if(!healthy.getAsBoolean() || !eligible(player))return;
            try{
                wagers.collect(player.getUniqueId(),System.currentTimeMillis());
                for(var match:wagers.notices(player.getUniqueId()))deliver(player,match);
                for(var match:wagers.incoming(player.getUniqueId()))if(match.expires()>System.currentTimeMillis())invite(player,match);
                if(wagers.hasPlayed(player.getUniqueId()))milestone(player);
            }catch(SQLException error){failure.accept(error);}
        },2);
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(!sender.hasPermission(PERMISSION)||!(sender instanceof Player player)||!healthy.getAsBoolean()||!eligible(player))return List.of();
        try{
            if(args.length==1){
                List<String> choices=new ArrayList<>(List.of("help","invite","accept","decline","cancel","collect","history"));
                if(settings.enabled())onlinePlayers.get().stream().filter(CoinFlipGame::eligible).filter(p->!p.getUniqueId().equals(player.getUniqueId())).map(Player::getName).forEach(choices::add);
                return NookUi.complete(args[0],choices);
            }
            if(args.length==2 && args[0].equalsIgnoreCase("invite"))return settings.enabled()?NookUi.complete(args[1],onlinePlayers.get().stream().filter(CoinFlipGame::eligible).filter(p->!p.getUniqueId().equals(player.getUniqueId())).map(Player::getName).toList()):List.of();
            if(args.length==2 && Set.of("accept","decline").contains(args[0].toLowerCase(Locale.ROOT))){
                List<String> choices=new ArrayList<>();for(var match:wagers.incoming(player.getUniqueId())){
                    var creator=playersById.apply(match.creator());if(creator!=null&&eligible(creator))choices.add(creator.getName());
                }
                return NookUi.complete(args[1],choices);
            }
            if(settings.enabled() && (args.length==3 && args[0].equalsIgnoreCase("invite") || args.length==2 && !Set.of("help","invite","accept","decline","cancel","collect","history").contains(args[0].toLowerCase(Locale.ROOT)))){
                List<String> choices=new ArrayList<>();for(long cents:new long[]{settings.minimum(),500,1000})if(cents>=settings.minimum()&&cents<=settings.maximum())choices.add(java.math.BigDecimal.valueOf(cents,2).stripTrailingZeros().toPlainString());
                return NookUi.complete(args[args.length-1],choices);
            }
        }catch(SQLException error){failure.accept(error);}
        return List.of();
    }
    String status(){return "Coin flips: "+(settings.enabled()?"enabled":"closed")+"; equal stakes "+Money.format(settings.minimum())+"–"+Money.format(settings.maximum())+"; existing stakes recover on expiry";}
    @Override public void close(){expiry.cancel();HandlerList.unregisterAll(this);}
}
