package net.nikosnook.core;
import java.util.*;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;

/** Permission check precedes all diagnostic suppliers; no mutation or repair routes. */
final class AdminStatus {
    static boolean execute(CommandSender sender,String[] args,Supplier<List<String>> snapshot){
        if(!sender.hasPermission("nookcore.admin")){sender.sendMessage(NookUi.problem("NookAdmin","This command is for admins."));return true;}
        if(args.length!=1){sender.sendMessage(NookUi.problem("NookAdmin","Use /nookadmin status."));return true;}
        sender.sendMessage(NookUi.heading("NookCore · Status (read-only)"));
        for(String line:snapshot.get())sender.sendMessage(NookUi.text(line));
        return true;
    }
}
