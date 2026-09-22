package net.nikosnook.core;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

final class NookPlaceholders extends PlaceholderExpansion {
    private final NookChat chat;
    NookPlaceholders(NookChat chat){this.chat=chat;}
    @Override public String getIdentifier(){return "nookcore";}
    @Override public String getAuthor(){return "NikosNook";}
    @Override public String getVersion(){return "0.1.0";}
    @Override public boolean persist(){return true;}
    @Override public String onRequest(OfflinePlayer player,String key){return player==null?"":chat.placeholder(player.getUniqueId(),key);}
}
