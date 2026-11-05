package net.nikosnook.core;

import java.sql.SQLException;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Player-funded challenges. Animation and messages never decide the money. */
final class CoinFlipGame implements CommandExecutor,TabCompleter,Listener,AutoCloseable {
    static final String PERMISSION="nookcore.coinflip";
    private static final Component PREFIX=Component.text("coinflip >> ",NookUi.ACCENT);
    private static final List<String> SIDES=List.of("heads","tails");
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
        this(plugin,store,healthy,failure,Bukkit::getPlayer,Bukkit::getPlayerExact,Bukkit::getOnlinePlayers);
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
            try{expireInvitations();}
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
    private void expireInvitations()throws SQLException {
        for(var match:wagers.expireDue(System.currentTimeMillis()))deliverParticipants(match,false);
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!sender.hasPermission(PERMISSION)){sender.sendMessage(problem("you don't have permission to play coin flips."));return true;}
        String sub=args.length==0?"":args[0].toLowerCase(Locale.ROOT);
        if(sub.equals("help")){help(sender);return true;}
        if(!(sender instanceof Player player)){sender.sendMessage(message("play coin flips in game."));return true;}
        if(!healthy.getAsBoolean()){sender.sendMessage(problem("coinflips are paused. please ask staff for help."));return true;}
        if(!eligible(player)){sender.sendMessage(problem("play coin flips while in Survival and out of staff mode."));return true;}
        try{
            expireInvitations();
            switch(sub){
                case "collect"->{
                    if(args.length!=1)throw new IllegalArgumentException("/coinflip collect");
                    long received=wagers.collect(player.getUniqueId(),System.currentTimeMillis());
                    long waiting=wagers.waiting(player.getUniqueId());
                    if(received>0)sender.sendMessage(message("+"+Money.format(received)+" in your balance."));
                    if(waiting>0)sender.sendMessage(message(Money.format(waiting)+" will be added when there's room in your balance."));
                    else if(received==0)sender.sendMessage(message("all your game Nooks are in your balance."));
                }
                case "cancel"->{
                    if(args.length!=1)throw new IllegalArgumentException("/coinflip cancel");
                    var match=wagers.outgoing(player.getUniqueId()).orElseThrow(()->new IllegalArgumentException("you don't have a coin flip waiting."));
                    deliverParticipants(wagers.cancel(match.id(),player.getUniqueId(),System.currentTimeMillis()),false);
                }
                case "accept","decline"->{
                    if(args.length!=2)throw new IllegalArgumentException("/coinflip "+sub+" <player>");
                    var creator=store.byName(args[1]).orElseThrow(()->new IllegalArgumentException("there isn't a waiting invitation from that player."));
                    var match=wagers.invitation(creator.id(),player.getUniqueId()).orElseThrow(()->new IllegalArgumentException("there isn't a waiting invitation from that player."));
                    if(sub.equals("accept")){
                        var other=playersById.apply(creator.id());
                        if(!available(player,other))throw new IllegalArgumentException("both players need to be ready to play. you can still decline.");
                        var result=wagers.accept(match.id(),player.getUniqueId(),System.currentTimeMillis());
                        boolean settled=result.state()==WagerService.State.SETTLED;
                        if(settled)announce(resultMessage(result,result.winner().equals(creator.id())?other.getName():player.getName()),other,player);
                        deliverParticipants(result,settled);
                    }else deliverParticipants(wagers.decline(match.id(),player.getUniqueId(),System.currentTimeMillis()),false);
                }
                case ""->summary(player);
                case "invite"->createInvitation(player,args,1);
                default->createInvitation(player,args,0);
            }
        }catch(IllegalArgumentException error){sender.sendMessage(problem(error.getMessage()));}
        catch(SQLException error){failure.accept(error);sender.sendMessage(problem("couldn't save that flip. please ask staff to check it."));}
        return true;
    }

    private void createInvitation(Player player,String[] args,int first)throws SQLException {
        if(args.length!=first+2 && args.length!=first+3){help(player);return;}
        if(!settings.enabled())throw new IllegalArgumentException("coinflips are paused.");
        var side=WagerService.Side.HEADS;
        if(args.length==first+3){
            try{side=WagerService.Side.valueOf(args[first+2].toUpperCase(Locale.ROOT));}
            catch(IllegalArgumentException error){throw new IllegalArgumentException("choose heads or tails.");}
        }
        var target=playersByName.apply(args[first]);
        if(!available(player,target))throw new IllegalArgumentException("that player isn't available for a coin flip right now.");
        var match=wagers.invite(player.getUniqueId(),target.getUniqueId(),Money.parse(args[first+1]),side,System.currentTimeMillis());
        String amount=Money.format(match.stake());
        player.sendMessage(message("invited "+target.getName()+" for "+amount+"!").append(NookUi.text("  ")).append(cancelAction()));
        invite(target,match);
        announce(player.getName()+" invited "+target.getName()+" to a coinflip for "+amount+"!",player,target);
    }
    private void summary(Player player)throws SQLException {
        help(player);
        var outgoing=wagers.outgoing(player.getUniqueId());
        if(outgoing.isPresent()){
            var match=outgoing.get();
            player.sendMessage(message("waiting for "+name(match.target())+" · "+Money.format(match.stake())+" · "+match.creatorSide().label())
                .append(NookUi.text("  ")).append(cancelAction()));
        }
        for(var match:wagers.incoming(player.getUniqueId()))invite(player,match);
        for(var match:wagers.notices(player.getUniqueId()))deliver(player,match,false);
        if(wagers.hasPlayed(player.getUniqueId()))milestone(player);
    }
    private static Component cancelAction(){
        return NookUi.command("cancel","/coinflip cancel").clickEvent(ClickEvent.runCommand("/coinflip cancel"));
    }
    private void invite(Player target,WagerService.Match match)throws SQLException {
        String creator=name(match.creator());
        target.sendMessage(message(creator+" invited you to a coinflip for "+Money.format(match.stake())+"! they chose "+match.creatorSide().label()+"."));
        Component actions=NookUi.command("decline","/coinflip decline "+creator).clickEvent(ClickEvent.runCommand("/coinflip decline "+creator));
        if(settings.enabled())actions=NookUi.command("accept","/coinflip accept "+creator).clickEvent(ClickEvent.runCommand("/coinflip accept "+creator)).append(NookUi.text("  ·  ")).append(actions);
        target.sendMessage(actions);
    }
    void showHistory(CommandSender sender,String[] args){
        if(args.length!=2 || !args[0].equalsIgnoreCase("history")){
            NookUi.help(sender,"games administration","/nookadmin games history <player> — recent coinflips");return;
        }
        try{
            var player=store.byName(args[1]).orElseThrow(()->new IllegalArgumentException("no saved Nooks account for that player."));
            sender.sendMessage(NookUi.heading(player.name()+"'s recent coinflips"));
            var matches=wagers.history(player.id());
            if(matches.isEmpty())sender.sendMessage(NookUi.text("no coinflips yet."));
            for(var match:matches){
                UUID other=match.creator().equals(player.id())?match.target():match.creator();
                String result=match.state()==WagerService.State.SETTLED
                    ?match.side().label()+" · "+name(match.winner())+" won "+Money.format(match.pot())
                    :match.state().name().toLowerCase(Locale.ROOT);
                sender.sendMessage(NookUi.text(name(other)+" · "+Money.format(match.stake())+" each · "+result+" · "+NookUi.date(match.created())));
            }
        }catch(IllegalArgumentException error){sender.sendMessage(problem(error.getMessage()));}
        catch(SQLException error){failure.accept(error);sender.sendMessage(problem("couldn't read the saved coinflips."));}
    }
    private void announce(String text,Player creator,Player guest){
        var announcement=message(text);
        for(var viewer:onlinePlayers.get()){
            UUID id=viewer.getUniqueId();
            if(!id.equals(creator.getUniqueId()) && !id.equals(guest.getUniqueId()) && viewer.canSee(creator) && viewer.canSee(guest))
                viewer.sendMessage(announcement);
        }
        AfkNotices.discord(plugin,"coinflip >> "+text);
    }
    private void deliverParticipants(WagerService.Match match,boolean showResult)throws SQLException {
        for(UUID id:List.of(match.creator(),match.target())){
            var player=playersById.apply(id);
            // A duplicate command may return an existing committed match; do not repeat its notice.
            if(player!=null && eligible(player) && wagers.notices(id).stream().anyMatch(waiting->waiting.id().equals(match.id())))
                deliver(player,match,showResult);
        }
    }
    private void deliver(Player player,WagerService.Match match,boolean showResult)throws SQLException {
        if(match.state()==WagerService.State.SETTLED){
            String winner=name(match.winner());
            player.sendMessage(message(resultMessage(match,winner)));
            if(showResult){
                try{
                    player.showTitle(Title.title(NookUi.heading(match.side().name()+"!"),NookUi.text(winner+" wins")));
                    player.playSound(player.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,0.6f,1.4f);
                }catch(RuntimeException ignored){/* The result is already committed. */}
            }
            if(match.winner().equals(player.getUniqueId()))pendingNotice(player);
        }else{
            boolean creator=match.creator().equals(player.getUniqueId());
            String other=name(creator?match.target():match.creator());
            String text=switch(match.state()){
                case DECLINED->creator?other+" declined your coin flip </3":"declined "+other+"'s coin flip.";
                case CANCELLED->creator?"cancelled your coin flip.":other+" cancelled their coin flip.";
                case EXPIRED->"the coin flip with "+other+" expired.";
                default->throw new IllegalArgumentException("that coin flip is still waiting.");
            };
            player.sendMessage(message(text));
            if(creator)pendingNotice(player);
        }
        wagers.acknowledge(match.id(),player.getUniqueId());
        if(match.state()==WagerService.State.SETTLED)milestone(player);
    }
    private void pendingNotice(Player player)throws SQLException {
        if(wagers.waiting(player.getUniqueId())>0)
            player.sendMessage(message("your Nooks will be added when there's room in your balance."));
    }
    private static String resultMessage(WagerService.Match match,String winner){
        return match.side().label()+"! "+winner+" won "+Money.format(match.pot())+"! <3";
    }
    private String name(UUID id)throws SQLException {return store.account(id).map(NookStore.Account::name).orElse("a nookling");}
    private static Component message(String text){return PREFIX.append(NookUi.text(text));}
    private static Component problem(String text){return PREFIX.append(NookUi.error(text));}
    private static boolean eligible(Player player){
        return player.hasPermission(PERMISSION) && player.isOnline() && !player.isDead()
            && player.getGameMode()==GameMode.SURVIVAL && !StaffMode.active(player.getUniqueId()) && player.isVisibleByDefault()
            && (!player.hasMetadata("vanished") || player.getMetadata("vanished").stream().noneMatch(v->v.asBoolean()));
    }
    private static boolean available(Player player,Player other){
        return other!=null && eligible(other) && player.canSee(other) && other.canSee(player);
    }
    private void help(CommandSender sender){
        if(settings.enabled())
            NookUi.help(sender,"heads or tails?","/coinflip [invite] <player> <amount> [heads|tails] — heads by default, 50/50, full pot",
                "/coinflip accept <player>","/coinflip decline <player>","/coinflip cancel");
        else sender.sendMessage(message("coinflips are paused. you can still cancel or decline."));
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void join(PlayerJoinEvent event){
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            var player=event.getPlayer();if(!healthy.getAsBoolean() || !eligible(player))return;
            try{
                for(var match:wagers.notices(player.getUniqueId()))deliver(player,match,false);
                for(var match:wagers.incoming(player.getUniqueId()))if(match.expires()>System.currentTimeMillis())invite(player,match);
                if(wagers.hasPlayed(player.getUniqueId()))milestone(player);
            }catch(SQLException error){failure.accept(error);}
        },2);
    }
    private List<String> invitationTargets(Player player){
        return onlinePlayers.get().stream().filter(p->!p.getUniqueId().equals(player.getUniqueId()) && available(player,p)).map(Player::getName).toList();
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(!(sender instanceof Player player) || !healthy.getAsBoolean() || !eligible(player) || args.length==0)return List.of();
        String sub=args[0].toLowerCase(Locale.ROOT);
        try{
            if(args.length==1){
                List<String> choices=new ArrayList<>(List.of("help","decline","cancel"));
                if(settings.enabled()){
                    choices.addAll(List.of("invite","accept"));choices.addAll(invitationTargets(player));
                }
                return NookUi.complete(args[0],choices);
            }
            if(args.length==2 && sub.equals("invite"))
                return settings.enabled()?NookUi.complete(args[1],invitationTargets(player)):List.of();
            if(args.length==2 && (sub.equals("decline") || sub.equals("accept") && settings.enabled())){
                List<String> choices=new ArrayList<>();
                for(var match:wagers.incoming(player.getUniqueId())){
                    var creator=playersById.apply(match.creator());
                    if(available(player,creator))choices.add(creator.getName());
                }
                return NookUi.complete(args[1],choices);
            }
            if(settings.enabled() && !Set.of("help","accept","decline","cancel","collect").contains(sub)){
                int first=sub.equals("invite")?1:0;
                if(args.length==first+3)return NookUi.complete(args[args.length-1],SIDES);
                if(args.length==first+2){
                    List<String> choices=new ArrayList<>();
                    for(long cents:new long[]{settings.minimum(),500,1000})if(cents>=settings.minimum()&&cents<=settings.maximum())
                        choices.add(java.math.BigDecimal.valueOf(cents,2).stripTrailingZeros().toPlainString());
                    return NookUi.complete(args[args.length-1],choices);
                }
            }
        }catch(SQLException error){failure.accept(error);}
        return List.of();
    }
    String status(){return "Coin flips: "+(settings.enabled()?"enabled":"closed")+"; equal stakes "+Money.format(settings.minimum())+"–"+Money.format(settings.maximum())+"; existing stakes recover on expiry";}
    @Override public void close(){expiry.cancel();HandlerList.unregisterAll(this);}
}
