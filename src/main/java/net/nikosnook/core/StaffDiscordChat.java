package net.nikosnook.core;

import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.ListenerPriority;
import github.scarsz.discordsrv.api.events.GameChatMessagePreProcessEvent;
import github.scarsz.discordsrv.api.events.GameChatMessagePostProcessEvent;
import org.bukkit.event.Cancellable;

/** Loaded only when DiscordSRV is installed; protects the relay independently of Bukkit listener order. */
public final class StaffDiscordChat {
    public StaffDiscordChat() {}
    @Subscribe(priority=ListenerPriority.HIGHEST)
    public void before(GameChatMessagePreProcessEvent event) {
        if (StaffMode.active(event.getPlayer().getUniqueId()) || event.getTriggeringBukkitEvent() instanceof Cancellable c && c.isCancelled()) event.setCancelled(true);
    }
    @Subscribe(priority=ListenerPriority.HIGHEST)
    public void after(GameChatMessagePostProcessEvent event) {
        if (StaffMode.active(event.getPlayer().getUniqueId()) || event.getTriggeringBukkitEvent() instanceof Cancellable c && c.isCancelled()) event.setCancelled(true);
    }
}
