package net.nikosnook.core;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Explicit private messages; never dispatches a public chat event. */
final class StaffChat implements CommandExecutor, TabCompleter {
    static final String PERMISSION = "nookcore.staff.chat";
    private final Supplier<? extends Collection<? extends Player>> players;

    StaffChat(JavaPlugin plugin) {
        this(Bukkit::getOnlinePlayers);
        var command = java.util.Objects.requireNonNull(plugin.getCommand("staff"));
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    StaffChat(Supplier<? extends Collection<? extends Player>> players) { this.players = players; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(NookUi.problem("Staff", "You do not have permission to use staff chat."));
            return true;
        }
        String message = String.join(" ", args).trim();
        if (message.isEmpty()) {
            sender.sendMessage(NookUi.text("/" + label + " <message>"));
            return true;
        }
        Component line = NookUi.prefix("Staff")
            .append(Component.text(sender.getName(), NookUi.COMMAND))
            .append(Component.text(": " + message, NookUi.BODY));
        for (Player player : players.get()) {
            if (player.hasPermission(PERMISSION)) player.sendMessage(line);
        }
        if (!(sender instanceof Player)) sender.sendMessage(line);
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
