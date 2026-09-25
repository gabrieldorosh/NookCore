package net.nikosnook.core;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.managers.RegionManager;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Add-only region setup. A durable journal blocks trading if a multi-file save is interrupted. */
final class PlotSetup implements CommandExecutor,TabCompleter,Listener {
    private record Draft(UUID world,String id,boolean district,PlotLayout box,long rent,String address,long expires){}
    private final JavaPlugin plugin;
    private final NookStore store;
    private final BooleanSupplier healthy;
    private final Consumer<Exception> failure;
    private final Map<UUID,Draft> drafts=new HashMap<>();
    PlotSetup(JavaPlugin plugin,NookStore store,BooleanSupplier healthy,Consumer<Exception> failure){
        this.plugin=plugin;this.store=store;this.healthy=healthy;this.failure=failure;
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
    }
    @EventHandler public void quit(PlayerQuitEvent event){drafts.remove(event.getPlayer().getUniqueId());}
    private RegionManager manager(Player p){return Objects.requireNonNull(WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(p.getWorld())),"WorldGuard region storage unavailable");}
    private void validate(Player p,Draft d)throws Exception {
        if(!p.getWorld().getUID().equals(d.world()))throw new IllegalArgumentException("Return to the selection's world first.");
        if(d.box().area()>1_000_000)throw new IllegalArgumentException("Choose an area no larger than 1,000,000 blocks.");
        var manager=manager(p);
        if(manager.hasRegion(d.id()) || store.plots().stream().anyMatch(plot->plot.id().equals(d.id())))throw new IllegalArgumentException("That ID already exists. Setup never replaces regions or plots.");
        if(!d.district())for(var plot:store.plots())if(store.plotAddress(plot.id()).equalsIgnoreCase(d.address()))throw new IllegalArgumentException("That address was just taken. Preview again for a new address.");
        String parent=plugin.getConfig().getString("shop-gate.district-region","");
        if(d.district()){
            var mappings=plugin.getConfig().getConfigurationSection("shop-gate.plot-regions");
            if(!plugin.getConfig().getString("shop-gate.world-uuid", "").isBlank() || plugin.getConfig().getBoolean("shop-gate.enabled") || mappings!=null && !mappings.getKeys(false).isEmpty())throw new IllegalArgumentException("A district is already configured. Setup cannot replace it.");
        }else{
            if(!p.getWorld().getUID().toString().equals(plugin.getConfig().getString("shop-gate.world-uuid")))throw new IllegalArgumentException("Select a plot in the configured district world.");
            var region=manager.getRegion(parent);
            if(!(region instanceof ProtectedCuboidRegion))throw new IllegalArgumentException("Create a district first.");
            if(!box(region).contains(d.box()))throw new IllegalArgumentException("Both plot corners must be inside the district.");
            if(region.getMinimumPoint().y()>p.getWorld().getMinHeight() || region.getMaximumPoint().y()<p.getWorld().getMaxHeight()-1)throw new IllegalArgumentException("The existing district must cover the full world height.");
            if(region.getPriority()==Integer.MAX_VALUE)throw new IllegalArgumentException("District priority is too high.");
        }
        for(var region:manager.getRegions().values()){
            if(region.getId().equals("__global__") || !d.district() && region.getId().equals(parent))continue;
            if(box(region).overlaps(d.box()))throw new IllegalArgumentException("Selection overlaps region "+region.getId()+". Leave paths and other plots outside it.");
        }
    }
    private static PlotLayout box(com.sk89q.worldguard.protection.regions.ProtectedRegion r){return new PlotLayout(r.getMinimumPoint().x(),r.getMinimumPoint().z(),r.getMaximumPoint().x(),r.getMaximumPoint().z());}
    private void status(CommandSender sender){
        sender.sendMessage(NookUi.heading("NookPlots · Setup status"));
        sender.sendMessage(NookUi.text("Economy healthy: "+healthy.getAsBoolean()+" · Rental commands: "+plugin.getConfig().getBoolean("rental-commands-enabled")+" · Shop gate enabled: "+plugin.getConfig().getBoolean("shop-gate.enabled")));
        String uuid=plugin.getConfig().getString("shop-gate.world-uuid","");
        String district=plugin.getConfig().getString("shop-gate.district-region","");
        sender.sendMessage(NookUi.text("Configured world UUID: "+uuid));
        org.bukkit.World world;
        try{world=plugin.getServer().getWorld(UUID.fromString(uuid));}catch(IllegalArgumentException e){sender.sendMessage(NookUi.problem("NookPlots","The world UUID is missing or invalid."));return;}
        if(world==null){sender.sendMessage(NookUi.problem("NookPlots","That world UUID is not loaded. Check whether the world was replaced."));return;}
        sender.sendMessage(NookUi.text("Loaded world: "+world.getName()));
        var manager=WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if(manager==null){sender.sendMessage(NookUi.problem("NookPlots","WorldGuard region storage is unavailable."));return;}
        var parent=manager.getRegion(district);
        sender.sendMessage(NookUi.text("District region: "+district+(parent==null?" (missing)":" (present)")));
        var mappings=plugin.getConfig().getConfigurationSection("shop-gate.plot-regions");
        if(mappings==null || mappings.getKeys(false).isEmpty()){sender.sendMessage(NookUi.text("No plot mappings configured. Create the first plot inside the district, then restart."));return;}
        for(String id:mappings.getKeys(false)){
            String plot=mappings.getString(id);var region=manager.getRegion(id);String record;
            try{record=store.plot(plot).state();}catch(Exception e){record="missing/unreadable plot record";}
            sender.sendMessage(NookUi.text(id+" → "+plot+" · "+(region==null?"region missing":parent==null || region.getParent()!=parent?"wrong/missing parent":"region present")+" · "+record));
        }
        sender.sendMessage(NookUi.text("Read-only report. Configuration changes need a restart; startup errors remain in console."));
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(args.length==1 && args[0].equalsIgnoreCase("status") && (sender instanceof ConsoleCommandSender || sender.hasPermission("nookcore.admin"))){status(sender);return true;}
        if(!(sender instanceof Player p) || !p.hasPermission("nookcore.admin")){sender.sendMessage(NookUi.problem("NookPlots","An admin must run setup in game."));return true;}
        if(!healthy.getAsBoolean()){sender.sendMessage(NookUi.problem("NookPlots","Setup is paused. Resolve the server startup/storage error first."));return true;}
        try{
            String action=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);
            if(action.equals("cancel") && args.length==1){drafts.remove(p.getUniqueId());p.sendMessage(NookUi.message("NookPlots","Setup preview cancelled."));return true;}
            if(action.equals("confirm") && args.length==1){
                Draft d=drafts.remove(p.getUniqueId());
                if(d==null || System.currentTimeMillis()>=d.expires())throw new IllegalArgumentException("No current setup preview. Select corners and preview again.");
                validate(p,d);apply(p,d);return true;
            }
            boolean district=action.equals("district");
            if(district && args.length==2 || action.equals("plot") && args.length<=3){
                drafts.remove(p.getUniqueId());
                String id=args.length>=2?PlotLayout.id(args[1]):null;
                var session=WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(p));
                var selected=session.getSelection(BukkitAdapter.adapt(p.getWorld()));
                if(!(selected instanceof CuboidRegion))throw new IllegalArgumentException("Use a cuboid selection: //sel cuboid, then select two corners with //wand.");
                var a=selected.getMinimumPoint();var b=selected.getMaximumPoint();
                PlotLayout footprint=new PlotLayout(a.x(),a.z(),b.x(),b.z());
                long rent=district?0:args.length==3?Money.parse(args[2]):PlotPricing.weekly(footprint.area());
                if(!district && (rent<=0 || rent>Money.MAX/4))throw new IllegalArgumentException("Choose a positive weekly rent within the supported range.");
                Set<String> used=new HashSet<>(manager(p).getRegions().keySet());
                for(var plot:store.plots()){used.add(plot.id());used.add(store.plotAddress(plot.id()).toLowerCase(Locale.ROOT).replace(' ','-'));}
                var address=district?null:PlotAddresses.choose(used::contains,new Random());
                if(id==null)id=address.id();
                Draft d=new Draft(p.getWorld().getUID(),id,district,footprint,rent,address==null?null:address.label(),System.currentTimeMillis()+60_000);
                validate(p,d);drafts.put(p.getUniqueId(),d);
                p.sendMessage(NookUi.heading("NookPlots · "+(district?"District":"Plot")+" preview"));
                p.sendMessage(NookUi.text(id+" · X "+a.x()+" to "+b.x()+", Z "+a.z()+" to "+b.z()+" · "+d.box().area()+" blocks · full world height"));
                if(!district)p.sendMessage(NookUi.text(d.address()+" · Weekly rent: "+Money.format(rent)+(args.length==3?" (explicit price)":" (area suggestion)")));
                p.sendMessage(NookUi.message("NookPlots","Run ").append(NookUi.command("/plots setup confirm")).append(NookUi.text(" within 60 seconds. Nothing changes until confirmed; new plots need a restart before renting.")));return true;
            }
            NookUi.help(p,"NookPlots · Setup","/plots setup status — inspect the configured district and rental gate","//wand — select two opposite corners; Y is expanded automatically","/plots setup district <id> — preview a new district","/plots setup plot [id] [weekly-rent] — preview a plot; omitted values are generated","/plots setup confirm — save the preview","/plots setup cancel — discard the preview");
        }catch(com.sk89q.worldedit.IncompleteRegionException e){p.sendMessage(NookUi.problem("NookPlots","Select both corners with //wand in this world first."));}
        catch(IllegalArgumentException e){p.sendMessage(NookUi.problem("NookPlots",e.getMessage()));}
        catch(Exception e){failure.accept(e);plugin.getLogger().log(java.util.logging.Level.SEVERE,"Plot setup failed",e);p.sendMessage(NookUi.problem("NookPlots","Setup could not be confirmed. Check console and the setup journal before retrying."));}
        return true;
    }
    private void apply(Player p,Draft d)throws Exception {
        Path folder=plugin.getDataFolder().toPath();Path journal=folder.resolve("plot-setup-pending.yml");
        if(Files.exists(journal))throw new IllegalStateException("An unfinished setup journal needs recovery");
        var backup=folder.resolve("setup-backups").resolve(UUID.randomUUID().toString());Files.createDirectories(backup);
        Files.copy(folder.resolve("config.yml"),backup.resolve("config.yml"));store.backup(backup.resolve("nooks.db"));
        var manager=manager(p);manager.save();
        // Recovery needs only remove this newly named region; no pre-existing region is edited.
        Files.writeString(journal,"world: "+d.world()+"\nregion: "+d.id()+"\ndistrict: "+d.district()+"\nbackup: "+backup.toAbsolutePath()+"\n",StandardOpenOption.CREATE_NEW);
        try{
            var region=new ProtectedCuboidRegion(d.id(),BlockVector3.at(d.box().minX(),p.getWorld().getMinHeight(),d.box().minZ()),BlockVector3.at(d.box().maxX(),p.getWorld().getMaxHeight()-1,d.box().maxZ()));
            if(!d.district()){
                var parent=Objects.requireNonNull(manager.getRegion(plugin.getConfig().getString("shop-gate.district-region")));
                region.setParent(parent);region.setPriority(parent.getPriority()+1);
            }
            manager.addRegion(region);manager.save();
            if(d.district()){
                plugin.getConfig().set("shop-gate.world-uuid",d.world().toString());plugin.getConfig().set("shop-gate.district-region",d.id());
            }else{
                store.definePlot(d.id(),d.rent());store.addressPlot(d.id(),d.address());
                plugin.getConfig().set("shop-gate.plot-regions."+d.id(),d.id());
                plugin.getConfig().set("shop-gate.enabled",true);plugin.getConfig().set("rental-commands-enabled",true);
            }
            // saveConfig logs rather than throws; use the throwing API so incomplete saves keep the journal.
            plugin.getConfig().save(folder.resolve("config.yml").toFile());
            Files.delete(journal);
            plugin.getLogger().info("Plot setup by "+p.getName()+": "+d.id()+" world="+d.world()+" area="+d.box().area()+" weekly="+d.rent());
            p.sendMessage(NookUi.message("NookPlots",(d.district()?"District ":"Plot ")+d.id()+" saved. "+(d.district()?"Select the first plot's corners next.":"Restart before renting new plots. Existing leases and prices are unchanged.")));
        }catch(Exception e){
            failure.accept(e);
            var chestShop=plugin.getServer().getPluginManager().getPlugin("ChestShop");if(chestShop!=null)plugin.getServer().getPluginManager().disablePlugin(chestShop);
            throw e;
        }
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String label,String[] args){return sender.hasPermission("nookcore.admin") && args.length==1?NookUi.complete(args[0],List.of("district","plot","confirm","cancel","status","help")):List.of();}
}
