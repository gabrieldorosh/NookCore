package net.nikosnook.core;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Observes accepted public chat without changing its renderer, body or audience. */
final class ChatHelp implements Listener {
    private final JavaPlugin plugin;
    private final KeywordReplies replies;
    ChatHelp(JavaPlugin plugin){
        this.plugin=plugin;
        // Materialise bundled values: nested sections in older configs may expose no local keys.
        YamlConfiguration effective;
        try(var stream=plugin.getResource("config.yml")){
            if(stream==null)throw new IllegalArgumentException("Bundled help defaults missing.");
            effective=YamlConfiguration.loadConfiguration(new InputStreamReader(stream,StandardCharsets.UTF_8));
        }catch(java.io.IOException e){throw new IllegalArgumentException("Cannot read help defaults.",e);}
        var configured=plugin.getConfig().getConfigurationSection("chat.help-replies");
        if(configured!=null)for(String key:configured.getKeys(true))
            if(!configured.isConfigurationSection(key))effective.set("chat.help-replies."+key,configured.get(key));
        replies=new KeywordReplies(effective.getConfigurationSection("chat.help-replies"));
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void chat(AsyncChatEvent event){
        // Hop to the server thread for player access and cooldowns; recheck later cancellation.
        plugin.getServer().getScheduler().runTask(plugin,()->{
            if(event.isCancelled() || !event.getPlayer().isOnline())return;
            String message=PlainTextComponentSerializer.plainText().serialize(event.message());
            replies.reply(event.getPlayer().getUniqueId(),message,System.nanoTime()/1_000_000)
                .ifPresent(reply->event.getPlayer().sendMessage(NookUi.message("NookHelp",reply)));
        });
    }
    @EventHandler public void quit(PlayerQuitEvent event){replies.forget(event.getPlayer().getUniqueId());}
    void close(){HandlerList.unregisterAll(this);replies.clear();}
}
