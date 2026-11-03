package net.nikosnook.core;

import java.io.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.GameMode;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Private command-driven daily puzzle; ordinary public chat is never intercepted. */
final class WordleCommand implements CommandExecutor,TabCompleter {
    private final BooleanSupplier healthy;
    private final Consumer<Exception> failure;
    private final Consumer<Player> solved;
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
        if(!(sender instanceof Player player)){sender.sendMessage(NookUi.message("Wordle","play today's word puzzle in Minecraft."));return true;}
        if(!player.hasPermission("nookcore.wordle")){player.sendMessage(NookUi.problem("Wordle","wordle isn't available for you right now."));return true;}
        if(!settings.enabled()){player.sendMessage(NookUi.message("Wordle","a little word game is on its way! check back soon <3"));return true;}
        if(!available || !healthy.getAsBoolean()){player.sendMessage(NookUi.problem("Wordle","wordle is taking a little break. please try again later <3"));return true;}
        if(player.getGameMode()!=GameMode.SURVIVAL || StaffMode.active(player.getUniqueId())){player.sendMessage(NookUi.problem("Wordle","come back in Survival and out of staff mode to play!"));return true;}
        try{
            long now=System.currentTimeMillis();WordleStore.Result result;
            if(args.length==0){show(player,game.open(player.getUniqueId(),now));return true;}
            if(args.length==1 && args[0].equalsIgnoreCase("help")){
                player.sendMessage(NookUi.message("Wordle","find today's five-letter word in six guesses! guesses stay private."));
                player.sendMessage(NookUi.text("= right place · ~ elsewhere · - not in the word"));
                NookUi.help(player,"wordle","/wordle — open today's puzzle","/wordle <word> — try a word after opening the board","/wordle guess <word> — also works for words like SHARE","/wordle share — copy a result without the letters");return true;
            }
            if(args.length==1 && args[0].equalsIgnoreCase("share")){
                result=game.open(player.getUniqueId(),now);var share=WordleUi.share(result.board());
                player.sendMessage(Component.text(share,NookUi.BODY));
                player.sendMessage(Component.text("copy result",NookUi.COMMAND).clickEvent(ClickEvent.copyToClipboard(share)));paid(player,result);milestone(player,result);return true;
            }
            if(args.length==1)result=game.submit(player.getUniqueId(),null,args[0],now);
            else if(args.length==2 && args[0].equalsIgnoreCase("guess"))result=game.submit(player.getUniqueId(),null,args[1],now);
            else if(args.length==3 && args[0].equalsIgnoreCase("guess"))result=game.submit(player.getUniqueId(),args[1],args[2],now);
            else {player.sendMessage(NookUi.problem("Wordle","not quite right! try /wordle help"));return true;}
            show(player,result);
            if(result.consumed() && result.board().state()==WordleStore.State.WON)try{QuestCelebrations.play(player,false);}catch(RuntimeException ignored){/* Presentation cannot affect a saved solve. */}
        }catch(WordleStore.StaleBoardException changed){
            player.sendMessage(NookUi.message("Wordle",changed.getMessage()));
            try{show(player,game.open(player.getUniqueId(),System.currentTimeMillis()));}catch(Exception error){available=false;report(error);player.sendMessage(NookUi.problem("Wordle","your saved guesses are safe. wordle needs a hand from staff."));}
        }catch(IllegalArgumentException invalid){player.sendMessage(NookUi.problem("Wordle",invalid.getMessage()));}
        catch(Exception error){available=false;report(error);player.sendMessage(NookUi.problem("Wordle","wordle needs a hand from staff. your saved progress is kept."));}
        return true;
    }
    private void show(Player player,WordleStore.Result result){
        var board=result.board();player.sendMessage(Component.empty());player.sendMessage(NookUi.heading("five little letters · "+board.day()));
        for(var guess:board.guesses())player.sendMessage(WordleUi.row(guess));
        for(int row=board.guesses().size();row<6;row++)player.sendMessage(Component.text("[ ] [ ] [ ] [ ] [ ]",NookUi.MUTED));
        player.sendMessage(Component.text("= right place · ~ elsewhere · - absent",NookUi.MUTED));
        if(board.state()==WordleStore.State.ACTIVE){
            player.sendMessage(NookUi.message("Wordle",(6-board.guesses().size())+" guesses left · today's reward: "+Money.format(board.reward())));
            player.sendMessage(NookUi.command("try a word!","/wordle guess "+board.day()+" "));
        }else if(board.state()==WordleStore.State.WON){
            player.sendMessage(NookUi.message("Wordle","lovely solve! another little puzzle is on its way <3"));
            if(!board.rewardPaid() && board.reward()>0)player.sendMessage(NookUi.message("Wordle","your "+Money.format(board.reward())+" reward is waiting safely. make room in your balance, then open /wordle again."));
        }else player.sendMessage(NookUi.message("Wordle","today's word was "+board.revealedAnswer().toUpperCase(Locale.ROOT)+". have another go tomorrow! <3"));
        player.sendMessage(Component.text("next puzzle: "+NookUi.date(board.nextReset()),NookUi.MUTED));paid(player,result);milestone(player,result);
    }
    private void paid(Player player,WordleStore.Result result){if(result.paidNow()>0)player.sendMessage(NookUi.message("Nooks","+"+Money.format(result.paidNow())+" · daily word puzzle"));}
    private void milestone(Player player,WordleStore.Result result){if(result.anySolved())try{solved.accept(player);}catch(RuntimeException ignored){/* A cosmetic callback cannot reverse a saved puzzle. */}}
    private void report(Exception error){try{failure.accept(error);}catch(RuntimeException ignored){/* The module stays unavailable even if logging fails. */}}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){return args.length==1?NookUi.complete(args[0],List.of("help","share","guess")):List.of();}
}
