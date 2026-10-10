package net.nikosnook.core;

import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Explicit staff reconciliation; never replays or rewrites a player's inventory. */
final class NativeStockRecovery {
    private record Preview(NativeStockReview detail,NativeStockReview.Recovery action,String reason,long expires){}
    private final Map<String,Preview> previews=new HashMap<>();
    private final NookStore store;private final Path backups;private final BooleanSupplier ready;
    private final Consumer<Exception> failure;private final LongSupplier clock;
    NativeStockRecovery(JavaPlugin plugin,NookStore store,BooleanSupplier ready,Consumer<Exception> failure){
        this(store,plugin.getDataFolder().toPath().resolve("backups"),ready,failure,System::currentTimeMillis);
    }
    NativeStockRecovery(NookStore store,Path backups,BooleanSupplier ready,Consumer<Exception> failure,LongSupplier clock){
        this.store=store;this.backups=backups;this.ready=ready;this.failure=failure;this.clock=clock;
    }
    boolean command(CommandSender sender,String[] args){
        if(args.length==0 || !args[0].equalsIgnoreCase("recover-stock"))return false;
        if(!sender.hasPermission("nookcore.admin")){sender.sendMessage(NookUi.problem("NookShops","This command is for admins."));return true;}
        String actor=sender instanceof Player p?p.getUniqueId().toString():"console:"+sender.getName();long now=clock.getAsLong();
        previews.entrySet().removeIf(e->e.getValue().expires()<=now);
        try{
            if(!ready.getAsBoolean())throw new IllegalArgumentException("Shop services are paused. Investigate and restart before resolving a receipt.");
            if(args.length<3)throw new IllegalArgumentException("Use /nookshops recover-stock <receipt> <credit|no-credit> <reason>, then /nookshops recover-stock <receipt> confirm.");
            UUID receipt;try{receipt=UUID.fromString(args[1]);}catch(IllegalArgumentException e){throw new IllegalArgumentException("Use the full stock receipt UUID.");}
            if(args[2].equalsIgnoreCase("confirm")){
                if(args.length!=3)throw new IllegalArgumentException("Use /nookshops recover-stock <receipt> confirm.");
                var preview=previews.get(actor);
                if(preview==null || !preview.detail().receipt().equals(receipt))throw new IllegalArgumentException("Preview this receipt from the same console or admin first; previews last 60 seconds.");
                previews.remove(actor);
                Files.createDirectories(backups);Path backup=backups.resolve("before-stock-recovery-"+receipt+"-"+UUID.randomUUID()+".db");
                store.backup(backup);
                boolean changed=store.resolveNativeStock(preview.detail(),preview.action(),actor,preview.reason(),now);
                String result=!changed?"This exact recovery was already recorded; nothing changed.":preview.action()==NativeStockReview.Recovery.CREDIT?
                    "Recovered "+preview.detail().quantity()+" item(s) into the original shop stock. No player inventory was changed.":
                    "Closed the failed deposit without adding stock. No player inventory was changed.";
                sender.sendMessage(NookUi.message("NookShops",result+" Backup: "+backup.getFileName()));
                sender.sendMessage(NookUi.message("NookShops","Check /nookshops review "+receipt+". Any other pending receipt still requires its own review."));
                return true;
            }
            previews.remove(actor);
            var action=switch(args[2].toLowerCase(Locale.ROOT)){case "credit"->NativeStockReview.Recovery.CREDIT;case "no-credit"->NativeStockReview.Recovery.NO_CREDIT;default->throw new IllegalArgumentException("Choose credit only for missing, unreplaced items; choose no-credit only when the player retained them or was already compensated.");};
            if(args.length<4)throw new IllegalArgumentException("Include the player's confirmed item outcome as the reason. Leave uncertain receipts pending.");
            String reason=String.join(" ",Arrays.copyOfRange(args,3,args.length)).strip();
            if(reason.isEmpty() || reason.length()>240 || reason.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Use a single-line reason of 1–240 characters.");
            var detail=store.nativeStockReview(receipt);
            if(!detail.state().equals("REVIEW") || detail.creditedQuantity()!=null || detail.resolution()!=null)throw new IllegalArgumentException("This deposit is already credited or resolved. Nothing changed.");
            if(action==NativeStockReview.Recovery.CREDIT && (long)detail.currentStock()+detail.quantity()>NativeShop.MAX_STOCK)throw new IllegalArgumentException("The shop has insufficient stock capacity for this recovery.");
            previews.put(actor,new Preview(detail,action,reason,now+60000));
            sender.sendMessage(NookUi.message("NookShops","Preview only: receipt "+receipt+" · "+detail.actorName()+" · plot "+detail.plot()+" · "+detail.quantity()+" item(s)."));
            sender.sendMessage(NookUi.message("NookShops",action==NativeStockReview.Recovery.CREDIT?
                "CREDIT: add the recorded items to the original shop, using its exact stored item metadata. Use only if the items disappeared and have not been replaced.":
                "NO CREDIT: close this failed attempt without adding stock. Use only if the player kept the items or has already been compensated."));
            sender.sendMessage(NookUi.message("NookShops","Staff statement: "+reason+". No inventory will be changed. Confirm within 60 seconds: /nookshops recover-stock "+receipt+" confirm"));
        }catch(IllegalArgumentException e){sender.sendMessage(NookUi.problem("NookShops",e.getMessage()));}
        catch(java.io.IOException e){sender.sendMessage(NookUi.problem("NookShops","Backup could not be created; recovery was not applied."));}
        catch(Exception e){failure.accept(e);sender.sendMessage(NookUi.problem("NookShops","Recovery could not be confirmed. Check the receipt and server log before retrying."));}
        return true;
    }
}
