package net.nikosnook.core;

import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.*;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Daily puzzle with private guesses and an opt-in public result. */
final class WordleCommand implements CommandExecutor,TabCompleter {
    private final BooleanSupplier healthy;
    private final Consumer<Exception> failure;
    private final Consumer<Player> solved;
    private final Set<UUID> sharedToday=new HashSet<>();
    private String sharedDay;
    private WordleSettings settings=new WordleSettings(false,100);
    private WordleStore game;
    private boolean available;
    WordleCommand(JavaPlugin plugin,NookStore store,BooleanSupplier healthy,Consumer<Exception> failure,Consumer<Player> solved){
        this.healthy=healthy;this.failure=failure;this.solved=solved;
        try{
            Path file=plugin.getDataFolder().toPath().resolve("wordle.yml");if(!Files.exists(file))plugin.saveResource("wordle.yml",false);
            var config=new YamlConfiguration();config.load(file.toFile());settings=WordleSettings.parse(config);
            if(settings.enabled()){
                var words=WordleWords.bundled(getClass().getClassLoader());
                game=new WordleStore(new GameLedger(store),words,settings.reward(),new SecureRandom()::nextInt);available=true;
            }
        }catch(Exception error){report(error);}
        var command=Objects.requireNonNull(plugin.getCommand("wordle"));command.setExecutor(this);command.setTabCompleter(this);
    }
    boolean ready(){return settings.enabled() && available;}
    String status(){return "Wordle: "+(!settings.enabled()?"disabled":available?"ready; daily reset 02:00 UK":"unavailable; check the server log");}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player player)){sender.sendMessage(NookUi.message(WordleUi.TITLE,"play today's word puzzle in Minecraft."));return true;}
        if(!player.hasPermission("nookcore.wordle")){player.sendMessage(NookUi.problem(WordleUi.TITLE,"wordle isn't available for you right now."));return true;}
        if(!settings.enabled()){player.sendMessage(NookUi.message(WordleUi.TITLE,"a little word game is on its way! check back soon <3"));return true;}
        if(!available || !healthy.getAsBoolean()){player.sendMessage(NookUi.problem(WordleUi.TITLE,"wordle is taking a little break. please try again later <3"));return true;}
        if(player.getGameMode()!=GameMode.SURVIVAL || StaffMode.active(player.getUniqueId())){player.sendMessage(NookUi.problem(WordleUi.TITLE,"come back in Survival and out of staff mode to play!"));return true;}
        try{
            long now=System.currentTimeMillis();WordleStore.Result result;
            if(args.length==0){show(player,game.open(player.getUniqueId(),now));return true;}
            if(args.length==1 && args[0].equalsIgnoreCase("help")){
                NookUi.help(player,WordleUi.TITLE,
                    "/wordle — open today's puzzle",
                    "/wordle guess <word> — find the word in six private guesses",
                    "/wordle share — post your finished squares in chat");
                return true;
            }
            if(args.length==1 && args[0].equalsIgnoreCase("guess")){
                player.sendMessage(NookUi.command("/wordle guess <word>").append(NookUi.text(" · choose a five-letter word")));return true;
            }
            if(args.length==1 && args[0].equalsIgnoreCase("share")){
                share(player,game.forShare(player.getUniqueId(),now));
                return true;
            }
            if(args.length==1)result=game.submit(player.getUniqueId(),null,args[0],now);
            else if(args.length==2 && args[0].equalsIgnoreCase("guess"))result=game.submit(player.getUniqueId(),null,args[1],now);
            else if(args.length==3 && args[0].equalsIgnoreCase("guess"))result=game.submit(player.getUniqueId(),args[1],args[2],now);
            else {player.sendMessage(NookUi.problem(WordleUi.TITLE,"not quite right! try /wordle help"));return true;}
            show(player,result);
            if(result.consumed() && result.board().state()==WordleStore.State.WON)try{QuestCelebrations.play(player,false);}catch(RuntimeException ignored){/* Presentation cannot affect a saved solve. */}
        }catch(WordleStore.StaleBoardException changed){
            player.sendMessage(NookUi.message(WordleUi.TITLE,changed.getMessage()));
            try{show(player,game.open(player.getUniqueId(),System.currentTimeMillis()));}catch(Exception error){available=false;report(error);player.sendMessage(NookUi.problem(WordleUi.TITLE,"your saved guesses are safe. wordle needs a hand from staff."));}
        }catch(IllegalArgumentException invalid){player.sendMessage(NookUi.error(invalid.getMessage()));}
        catch(Exception error){available=false;report(error);player.sendMessage(NookUi.problem(WordleUi.TITLE,"wordle needs a hand from staff. your saved progress is kept."));}
        return true;
    }
    private void show(Player player,WordleStore.Result result){
        var board=result.board();
        player.sendMessage(NookUi.heading(WordleUi.TITLE));
        for(var guess:board.guesses())player.sendMessage(WordleUi.row(guess));
        if(board.state()==WordleStore.State.ACTIVE){
            int remaining=6-board.guesses().size();
            player.sendMessage(NookUi.command(remaining+(remaining==1?" guess left":" guesses left"),"/wordle guess "+board.day()+" "));
        }else if(board.state()==WordleStore.State.WON){
            player.sendMessage(NookUi.text("great job! come back tomorrow for another puzzle <3"));
            if(!board.rewardPaid() && board.reward()>0)player.sendMessage(NookUi.message(WordleUi.TITLE,"your "+Money.format(board.reward())+" reward will arrive when there's room in your balance."));
        }else{
            player.sendMessage(NookUi.text("today's word was "+board.revealedAnswer().toUpperCase(Locale.ROOT)+". try again tomorrow! <3"));
        }
        paid(player,result);milestone(player,result);
    }

    private void share(Player player,WordleStore.Result result){
        var board=result.board();
        if(board.state()==WordleStore.State.ACTIVE){
            player.sendMessage(NookUi.error("finish today's puzzle before sharing!"));
        }else{
            if(!board.day().equals(sharedDay)){
                sharedToday.clear();
                sharedDay=board.day();
            }
            if(sharedToday.add(player.getUniqueId())){
                Bukkit.broadcast(WordleUi.share(player.getName(),board));
            }else{
                player.sendMessage(NookUi.error("you've already shared today's puzzle!"));
            }
        }
        paid(player,result);milestone(player,result);
    }
    private void paid(Player player,WordleStore.Result result){if(result.paidNow()>0)player.sendMessage(NookUi.message("Nooks","+"+Money.format(result.paidNow())+" · daily word puzzle"));}
    private void milestone(Player player,WordleStore.Result result){if(result.anySolved())try{solved.accept(player);}catch(RuntimeException ignored){/* A cosmetic callback cannot reverse a saved puzzle. */}}
    private void report(Exception error){try{failure.accept(error);}catch(RuntimeException ignored){/* The module stays unavailable even if logging fails. */}}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){return sender.hasPermission("nookcore.wordle") && args.length==1?NookUi.complete(args[0],List.of("help","share","guess")):List.of();}
}
