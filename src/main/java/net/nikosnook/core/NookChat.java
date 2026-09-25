package net.nikosnook.core;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;

/** Main-thread permission snapshots keep asynchronous chat free of Bukkit permission/storage calls. */
final class NookChat implements Listener, CommandExecutor, TabCompleter {
    private record Snapshot(String name,String rank,ChatPreferences.Preference preference,int deaths){}
    private final JavaPlugin plugin;
    private final ChatPreferences preferences;
    private final Map<UUID,Snapshot> snapshots=new ConcurrentHashMap<>();
    private final Set<UUID> rankPackLoaded=ConcurrentHashMap.newKeySet();
    private final Map<String,Component> images=new HashMap<>();
    private final UUID rankPack;
    private Runnable unregister=()->{};
    static final List<String> PRONOUNS=List.of("he/him","she/her","they/them","he/they","she/they","they/he","they/she","it/its","any","ask");
    private List<String> pronouns(){var configured=plugin.getConfig().getStringList("chat.pronoun-options");return configured.isEmpty()?PRONOUNS:configured;}
    static String selectedPronouns(String input,List<String> allowed){if((input.equalsIgnoreCase("reset") || input.equalsIgnoreCase("clear")))return "";return allowed.stream().filter(v->v.equalsIgnoreCase(input)).findFirst().orElseThrow(()->new IllegalArgumentException("Choose pronouns from: "+String.join(", ",allowed)+". Ask staff if yours are missing."));}
    NookChat(JavaPlugin plugin)throws IOException {
        this.plugin=plugin;preferences=new ChatPreferences(plugin.getDataFolder().toPath().resolve("chat-preferences.properties"));
        String packId=plugin.getConfig().getString("chat.rank-images.pack-id","");
        UUID configuredPack=null;
        try {
            if(!packId.isBlank()){
                configuredPack=UUID.fromString(packId);
                Key font=Key.key(plugin.getConfig().getString("chat.rank-images.font","minecraft:default"));
                for(String rank:List.of("nookling","supporter","helper","admin","owner")){
                    String glyph=plugin.getConfig().getString("chat.rank-images.glyphs."+rank,"");
                    if(!glyph.isEmpty()){
                        if(glyph.codePointCount(0,glyph.length())!=1 || Character.getType(glyph.codePointAt(0))!=Character.PRIVATE_USE)throw new IllegalArgumentException("Each rank glyph must be one private-use code point.");
                        images.put(rank,Component.text(glyph).font(font));
                    }
                }
            }
        }catch(IllegalArgumentException e){configuredPack=null;images.clear();plugin.getLogger().warning("Rank images disabled: "+e.getMessage());}
        rankPack=configuredPack;
        NookUi.preferences=preferences;
        Objects.requireNonNull(plugin.getCommand("nookchat")).setExecutor(this);
        plugin.getCommand("nookchat").setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(Player player:Bukkit.getOnlinePlayers())refresh(player);
        Bukkit.getScheduler().runTaskTimer(plugin,()->{for(Player player:Bukkit.getOnlinePlayers())refresh(player);},100,100);
        if(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")){var expansion=new NookPlaceholders(this);if(expansion.register()){unregister=()->expansion.unregister();plugin.getLogger().info("Registered NookCore name, rank and pronoun placeholders for TAB.");}}
    }
    void close(){unregister.run();NookUi.preferences=null;}
    String placeholder(UUID id,String key){
        Snapshot s=snapshots.get(id);if(s==null)return key.equals("deaths")?"0":"";
        return switch(key){case "deaths"->Integer.toString(s.deaths());case "name"->"&"+s.preference().textColour().asHexString()+s.name()+"&r";case "pronouns"->s.preference().pronouns().isEmpty()?"":" &7· "+s.preference().pronouns()+"&r";case "rank"->("&"+(s.rank().equals("nookling")?NookUi.GOOD:NookUi.ACCENT).asHexString())+"["+ChatStyle.rankLabel(s.rank())+"] &r";default->null;};
    }
    static String displayRank(java.util.function.Predicate<String> permission){
        for(String rank:List.of("owner","admin","helper","supporter"))if(permission.test("nookcore.chat.rank."+rank))return rank;
        return "nookling";
    }
    private void refresh(Player player){
        String rank=displayRank(player::hasPermission);
        snapshots.put(player.getUniqueId(),new Snapshot(player.getName(),rank,preferences.get(player.getUniqueId()),player.getStatistic(org.bukkit.Statistic.DEATHS)));
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void death(org.bukkit.event.entity.PlayerDeathEvent event){
        var player=event.getEntity();var message=event.deathMessage();
        if(message==null)return;
        message=PlayerPresentation.colourName(message,player.getName(),preferences.get(player.getUniqueId()).textColour());
        var killer=player.getKiller();
        if(killer!=null)message=PlayerPresentation.colourName(message,killer.getName(),preferences.get(killer.getUniqueId()).textColour());
        event.deathMessage(message);
    }
    @EventHandler(priority=EventPriority.HIGH) public void join(PlayerJoinEvent event){
        refresh(event.getPlayer());
        event.joinMessage(PlayerPresentation.colourName(event.joinMessage(),event.getPlayer().getName(),preferences.get(event.getPlayer().getUniqueId()).textColour()));
    }
    @EventHandler(priority=EventPriority.HIGH) public void quit(PlayerQuitEvent event){
        event.quitMessage(PlayerPresentation.colourName(event.quitMessage(),event.getPlayer().getName(),preferences.get(event.getPlayer().getUniqueId()).textColour()));
        snapshots.remove(event.getPlayer().getUniqueId());rankPackLoaded.remove(event.getPlayer().getUniqueId());}
    @EventHandler public void pack(PlayerResourcePackStatusEvent event){
        if(rankPack==null || !rankPack.equals(event.getID()))return;
        if(event.getStatus()==PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED)rankPackLoaded.add(event.getPlayer().getUniqueId());
        else rankPackLoaded.remove(event.getPlayer().getUniqueId());
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void chat(AsyncChatEvent event){
        Snapshot snapshot=snapshots.get(event.getPlayer().getUniqueId());if(snapshot==null)return;
        // Respect the current message (including earlier moderation edits), never the unfiltered original.
        var body=ChatStyle.message(PlainTextComponentSerializer.plainText().serialize(event.message()));
        event.message(body);
        event.renderer((source,displayName,message,viewer)->{
            Component image=images.get(snapshot.rank());
            if(image!=null && viewer instanceof Player player && rankPackLoaded.contains(player.getUniqueId()))return ChatStyle.render(image,snapshot.name(),snapshot.preference(),message);
            return ChatStyle.render(snapshot.rank(),snapshot.name(),snapshot.preference(),message);
        });
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player player)){sender.sendMessage(NookUi.message("NookChat","Use /nookchat in game."));return true;}
        try {
            CommandSyntax.check("nookchat",args);
            if(CommandSyntax.chatHelp(args)){
                NookUi.help(sender,"NookChat","/nookchat colour <colour|#RRGGBB|reset> — change your name colour","/nookchat pronouns <pronouns|reset> — choose or hide pronouns","/nookchat preview — see your current style");
                sender.sendMessage(NookUi.text("Message formatting: ").append(Component.text("&l bold  &o italic  &n underline  &m strikethrough  &r reset",NookUi.MUTED)));
                sender.sendMessage(NookUi.text("Use named colours or a hex colour such as #b8d8a8. Reset restores the default."));
                return true;
            }
            var old=preferences.get(player.getUniqueId());
            if(args.length==2 && args[0].equalsIgnoreCase("colour"))preferences.set(player.getUniqueId(),new ChatPreferences.Preference(args[1].equalsIgnoreCase("reset")?"white":args[1],old.pronouns()));
            else if(args.length>=2 && args[0].equalsIgnoreCase("pronouns")){
                String text=String.join(" ",Arrays.copyOfRange(args,1,args.length));
                preferences.set(player.getUniqueId(),new ChatPreferences.Preference(old.colour(),selectedPronouns(text,pronouns())));
            }
            refresh(player);var snapshot=snapshots.get(player.getUniqueId());
            if(args[0].equalsIgnoreCase("preview"))sender.sendMessage(ChatStyle.render(snapshot.rank(),snapshot.name(),snapshot.preference(),Component.text("Hello!")));
            else if(args[0].equalsIgnoreCase("colour"))sender.sendMessage(NookUi.message("NookChat","Name colour saved: ").append(NookUi.name(player.getUniqueId(),player.getName())));
            else sender.sendMessage(NookUi.message("NookChat",snapshot.preference().pronouns().isEmpty()?"Pronouns hidden.":"Pronouns saved: "+snapshot.preference().pronouns()));
        }catch(IllegalArgumentException e){sender.sendMessage(NookUi.problem("NookChat",e.getMessage()));}
        catch(IOException e){plugin.getLogger().log(Level.SEVERE,"Chat preference save failed; previous settings retained.",e);sender.sendMessage(NookUi.message("NookChat","Your chat settings could not be saved. Your previous settings remain active."));}
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        List<String> values=new ArrayList<>();
        if(args.length==1)values.addAll(List.of("colour","pronouns","preview","help"));
        else if(args.length==2 && args[0].equalsIgnoreCase("colour")){values.addAll(net.kyori.adventure.text.format.NamedTextColor.NAMES.keys());values.add("reset");}
        else if(args.length==2 && args[0].equalsIgnoreCase("pronouns")){values.addAll(pronouns());values.add("reset");}
        return NookUi.complete(args[args.length-1],values);
    }
}
