package net.nikosnook.core;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Public idle transitions and the shared Discord relay for public notices. */
final class AfkNotices {
    record State(boolean away,boolean hidden){}
    static String message(String name,State previous,State current){
        if(previous==null || previous.hidden() || current.hidden() || previous.away()==current.away())return null;
        return name+(current.away()?" has gone AFK":" is back!");
    }
    static void discord(JavaPlugin owner,String message){
        var bridge=Bukkit.getPluginManager().getPlugin("DiscordSRV");
        if(bridge==null || !bridge.isEnabled())return;
        try{
            Object channel=bridge.getClass().getMethod("getMainTextChannel").invoke(bridge);
            if(channel==null)return;
            var loader=bridge.getClass().getClassLoader();
            Class<?> textChannel=Class.forName("github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel",true,loader);
            Class<?> util=Class.forName("github.scarsz.discordsrv.util.DiscordUtil",true,loader);
            util.getMethod("queueMessage",textChannel,String.class,boolean.class).invoke(null,channel,message,false);
        }catch(ReflectiveOperationException | LinkageError e){owner.getLogger().warning("Could not relay public notice to DiscordSRV: "+e.getClass().getSimpleName());}
    }
}
