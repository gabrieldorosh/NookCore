package net.nikosnook.core;
import java.util.*;
import java.util.function.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
final class RentReduction implements CommandExecutor {
    private record Preview(List<NookStore.Plot> plots,long expires){}
    private final Map<String,Preview> pending=new HashMap<>();
    private final JavaPlugin plugin;private final NookStore store;private final BooleanSupplier healthy;private final Consumer<Exception> failure;
    RentReduction(JavaPlugin plugin,NookStore store,BooleanSupplier healthy,Consumer<Exception> failure){this.plugin=plugin;this.store=store;this.healthy=healthy;this.failure=failure;}
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!sender.hasPermission("nookcore.admin")){sender.sendMessage(NookUi.problem("NookPlots","Admins only."));return true;}
        if(!healthy.getAsBoolean()){sender.sendMessage(NookUi.problem("NookPlots","Economy paused; cannot change rents."));return true;}
        String actor=sender instanceof Player p?p.getUniqueId().toString():"console";long now=System.currentTimeMillis();
        pending.entrySet().removeIf(e->e.getValue().expires()<now);
        try{
            if(store.rentReductionApplied())throw new IllegalArgumentException("The 80% rent reduction has already been applied. It will not run twice.");
            if(args.length==1){
                var plots=List.copyOf(store.plots());if(plots.isEmpty())throw new IllegalArgumentException("There are no plots to reprice.");
                for(var plot:plots)sender.sendMessage(NookUi.text(plot.id()+": "+Money.format(plot.weekly())+" -> "+Money.format(NookStore.reducedRent(plot.weekly()))+" / week"));
                sender.sendMessage(NookUi.message("NookPlots","80% cut for all "+plots.size()+" plots. Ownership, prepaid time and balances stay unchanged. Future payments and automatic setup quotes use reduced rates. Confirm within 60 seconds: /nookadmin plots rent-cut confirm"));
                pending.put(actor,new Preview(plots,now+60000));return true;
            }
            if(args.length!=2 || !args[1].equalsIgnoreCase("confirm"))throw new IllegalArgumentException("Use /nookadmin plots rent-cut, then /nookadmin plots rent-cut confirm.");
            var preview=pending.remove(actor);if(preview==null || preview.expires()<now)throw new IllegalArgumentException("Preview expired. Run /nookadmin plots rent-cut again.");
            var folder=plugin.getDataFolder().toPath().resolve("backups");java.nio.file.Files.createDirectories(folder);
            var file=folder.resolve("before-rent-cut-"+UUID.randomUUID()+".db");store.backup(file);
            store.reducePlotRents(preview.plots(),actor,now);
            sender.sendMessage(NookUi.message("NookPlots","Rents reduced by 80%. Existing paid-through dates preserved. Backup: "+file.getFileName()));
            plugin.getLogger().info("80% plot rent reduction applied by "+actor+" to "+preview.plots().size()+" plots.");
        }catch(IllegalArgumentException e){sender.sendMessage(NookUi.problem("NookPlots",e.getMessage()));}
        catch(java.io.IOException e){sender.sendMessage(NookUi.problem("NookPlots","Backup failed; rent reduction was not applied."));plugin.getLogger().warning(e.toString());}
        catch(Exception e){failure.accept(e);sender.sendMessage(NookUi.problem("NookPlots","Rent update failed; check the server log before retrying."));}
        return true;
    }
}
