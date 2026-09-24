package net.nikosnook.core;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

/** Paper adapter. Rental features are opt-in for staging until player acceptance passes. */
public final class NookCorePlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private NookStore store;
    private final Map<String,Long> rewards=new HashMap<>();
    private boolean healthy=true, awardsEnabled;
    private ChestShopRentalGate plotGate;
    private NookChat chat;
    private AdminTeleports adminTeleports;
    @Override public void onEnable(){
        saveDefaultConfig();
        if(Files.exists(getDataFolder().toPath().resolve("plot-setup-pending.yml"))){blockStartup("Unfinished plot setup: recover plot-setup-pending.yml before enabling trades.");return;}
        try{new org.bukkit.configuration.file.YamlConfiguration().load(getDataFolder().toPath().resolve("config.yml").toFile());}
        catch(Exception e){getLogger().log(Level.SEVERE,"NookCore could not initialise: invalid config.yml",e);blockStartup("Invalid config.yml; restore a valid configuration before restarting.");return;}
        new SmitePrank(this);
        if(getConfig().getBoolean("exact-world-spawn-enabled",false))getServer().getPluginManager().registerEvents(new ExactWorldSpawn(AdminTeleports::safeReturn,message->getLogger().warning(message)),this);
        adminTeleports=new AdminTeleports(Bukkit::getPlayerExact,message->getLogger().info(message));
        getServer().getPluginManager().registerEvents(adminTeleports,this);
        getCommand("return").setExecutor((sender,command,label,args)->{
            String[] routed=new String[args.length+1];routed[0]="return";System.arraycopy(args,0,routed,1,args.length);adminTeleports.execute(sender,routed);return true;
        });
        getCommand("return").setTabCompleter((sender,command,label,args)->AdminTeleports.authorised(sender) && args.length==1?NookUi.complete(args[0],Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()):List.of());
        try {
            Files.createDirectories(getDataFolder().toPath());
            store=new NookStore(getDataFolder().toPath().resolve("nooks.db"));
            var section=getConfig().getConfigurationSection("advancement-rewards");
            if(section==null)throw new IllegalArgumentException("Missing advancement reward map.");
            for(String key:section.getKeys(false)) {
                long amount=section.getLong(key,-1);
                if(amount<0 || amount>Money.MAX || !key.startsWith("minecraft:"))throw new IllegalArgumentException("Invalid reward: "+key);
                rewards.put(key,amount);
            }
            awardsEnabled=getConfig().getBoolean("advancement-rewards-enabled",true);
            if(getServer().getPluginManager().getPlugin("Vault")!=null && getConfig().getBoolean("vault-enabled",true)){
                getServer().getServicesManager().register(net.milkbowl.vault.economy.Economy.class,new VaultBridge(store,()->healthy && isEnabled(),this::fail),this,org.bukkit.plugin.ServicePriority.Highest);
                getLogger().info("Registered Nooks as the Vault economy provider.");
            }
            getServer().getPluginManager().registerEvents(this,this);
            if(getConfig().getBoolean("chat.enabled",true)){
                if(getServer().getPluginManager().getPlugin("ChatControl")!=null)getLogger().warning("NookCore chat disabled while ChatControl is installed; choose one chat formatter before testing.");
                else {chat=new NookChat(this);getLogger().info("NookCore chat enabled with text ranks, name colours and pronouns.");}
            }
            Objects.requireNonNull(getCommand("nooks")).setExecutor(this);
            Objects.requireNonNull(getCommand("nookadmin")).setExecutor(this);
            getCommand("nooks").setTabCompleter(this);getCommand("nookadmin").setTabCompleter(this);
            if(getServer().getPluginManager().isPluginEnabled("WorldGuard") && getServer().getPluginManager().isPluginEnabled("WorldEdit"))new PlotSetup(this,store,()->healthy,this::fail);
            var plots=new PlotCommands(store,this::rentalsReady,this::reconcilePlots,this::fail,id->plotGate!=null && plotGate.manages(id),()->plotGate.validateConfiguration());
            new ShopSetup(this,(player,block)->{
                if(!rentalsReady())throw new IllegalArgumentException("Rentals must be available before previewing a district shop.");
                try{
                    plotGate.validateConfiguration();
                    String plot=plotGate.regionAt(block.getLocation());
                    if(!plotGate.manages(plot) || !store.role(plot,player.getUniqueId()).equals("OWNER") || !store.canTrade(plot,System.currentTimeMillis()))throw new IllegalArgumentException("Preview setup in your own active rented plot. Co-owner and town setup will follow later.");
                    if(block.getState() instanceof org.bukkit.block.Chest chest && chest.getInventory().getHolder() instanceof org.bukkit.block.DoubleChest pair){
                        for(var side:List.of(pair.getLeftSide(),pair.getRightSide()))if(!(side instanceof org.bukkit.block.Chest half) || !plot.equals(plotGate.regionAt(half.getLocation())))throw new IllegalArgumentException("Both halves of the chest must be inside your plot.");
                    }
                }catch(java.sql.SQLException e){throw new IllegalStateException("Could not read rental",e);}
            });
            var finder=new PlotFinder(this,id->plotGate.bounds(id),this::rentalsReady);
            plots.locator(finder::show);
            Objects.requireNonNull(getCommand("nookplots")).setExecutor(plots);getCommand("nookplots").setTabCompleter(plots);
            Bukkit.getScheduler().runTaskTimer(this,()->plots.expireConfirmations((id,plot)->{
                Player player=Bukkit.getPlayer(id);
                if(player!=null)player.sendMessage(PlotCommands.expiryNotice(plot));
            }),20,20);
            for(Player p:Bukkit.getOnlinePlayers())joinPlayer(p);
            new WeeklyQuests(this,store,()->healthy,this::fail);
            Bukkit.getScheduler().runTaskTimer(this,()->{
                if(!healthy)return;
                try {
                    for(Player p:Bukkit.getOnlinePlayers())store.touch(p.getUniqueId(),System.currentTimeMillis());
                    if(rentalsReady()){
                        plotGate.validateConfiguration();Map<String,String> before=new HashMap<>();for(var plot:store.plots())before.put(plot.id(),plot.state());
                        store.tick(System.currentTimeMillis(),plotGate.managedPlots());
                        for(var plot:store.plots())if(!plot.state().equals(before.get(plot.id())))for(Player p:Bukkit.getOnlinePlayers())plotNotice(p,plot);
                    }
                }
                catch(Exception e){fail(e);}
            },1200,1200);
            if(getConfig().getBoolean("shop-gate.enabled",false) || Files.exists(getDataFolder().toPath().resolve("plot-setup-pending.yml")))Bukkit.getScheduler().runTask(this,this::enableShopGate);
            getLogger().info("Economy foundation ready. Rental features are opt-in and require the separate shop gate startup check.");
        }catch(Exception e){getLogger().log(Level.SEVERE,"NookCore could not initialise; economy unavailable.",e);blockStartup("NookCore startup failed; check the console before restarting.");}
    }
    private void blockStartup(String reason){
        healthy=false;getLogger().severe(reason);
        for(String name:getDescription().getCommands().keySet()){
            var command=getCommand(name);if(command!=null){
                command.setExecutor((sender,cmd,label,args)->{sender.sendMessage(NookUi.problem("NookCore","Setup is paused. Ask an admin to check the startup error."));return true;});
                command.setTabCompleter((sender,cmd,label,args)->List.of());
            }
        }
        // Stay loaded long enough to disable a load-after shop provider, even if config is damaged.
        Bukkit.getScheduler().runTask(this,()->{
            var shop=getServer().getPluginManager().getPlugin("ChestShop");
            if(shop!=null)getServer().getPluginManager().disablePlugin(shop);
        });
    }
    private void enableShopGate(){
        try {
            if(Files.exists(getDataFolder().toPath().resolve("plot-setup-pending.yml")))throw new IllegalStateException("Unfinished plot setup: recover plot-setup-pending.yml before enabling trades.");
            for(String dependency:List.of("ChestShop","WorldGuard","WorldEdit"))
                if(!getServer().getPluginManager().isPluginEnabled(dependency))throw new IllegalStateException("Missing shop gate dependency: "+dependency);
            var gate=new ChestShopRentalGate(this,store,()->healthy && isEnabled(),this::fail);
            getServer().getPluginManager().registerEvents(gate,this);
            getServer().getPluginManager().registerEvents(new PlotProtectionListener(gate,store,()->healthy && isEnabled(),this::fail),this);
            gate.reconcileMembers(store);
            plotGate=gate;
            new PlotEntryNotices(this,store,gate,this::rentalsReady,this::fail);
            getLogger().info("Staging ChestShop rental sales gate registered. Role protection registered and WorldGuard membership reconciled; gameplay acceptance still required.");
        }catch(Exception | LinkageError e){
            healthy=false;getLogger().log(Level.SEVERE,"Configured shop gate could not start. Disabling ChestShop to prevent unguarded sales.",e);
            var shop=getServer().getPluginManager().getPlugin("ChestShop");if(shop!=null)getServer().getPluginManager().disablePlugin(shop);
        }
    }
    private boolean rentalsReady(){return healthy && plotGate!=null && getConfig().getBoolean("rental-commands-enabled",false);}
    private void reconcilePlots(){try{plotGate.validateConfiguration();plotGate.reconcileMembers(store);}catch(Exception ex){fail(ex);throw new IllegalStateException("WorldGuard update failed after the database operation; do not retry payment.",ex);}}
    private void fail(Exception e){if(!healthy)return;healthy=false;getLogger().log(Level.SEVERE,"Economy/plots paused after a storage or protection failure; restart after investigating. No success should be assumed.",e);}
    private void joinPlayer(Player p)throws SQLException {
        if(!healthy)return;
        long bonus=getConfig().getLong("joining-bonus-cents",6000);
        if(store.join(p.getUniqueId(),p.getName(),System.currentTimeMillis(),bonus))p.sendMessage(NookUi.message("Nooks","Welcome to Niko's Nook! Your starting balance is "+Money.format(bonus)+"."));
        else p.sendMessage(NookUi.message("Nooks","Your balance: "+Money.format(store.account(p.getUniqueId()).orElseThrow().cents())));
        if(rentalsReady())for(var plot:store.plots())plotNotice(p,plot);
    }
    private void plotNotice(Player player,NookStore.Plot plot)throws SQLException {
        if(!plotGate.manages(plot.id()) || store.role(plot.id(),player.getUniqueId()).equals("NONE"))return;
        if(plot.state().equals("GRACE"))player.sendMessage(NookUi.overdue(plot));
        else if(plot.state().equals("RECLAIM"))player.sendMessage(NookUi.message("NookPlots","Plot "+plot.id()+" is awaiting staff reclamation. Sales are closed; contact staff about collecting your belongings. Nothing is automatically deleted."));
    }
    @EventHandler public void join(PlayerJoinEvent e){try{joinPlayer(e.getPlayer());}catch(SQLException ex){fail(ex);}}
    @EventHandler public void quit(PlayerQuitEvent e){if(healthy)try{store.touch(e.getPlayer().getUniqueId(),System.currentTimeMillis());}catch(SQLException ex){fail(ex);}}
    @EventHandler public void advancement(PlayerAdvancementDoneEvent e){
        if(!healthy || !awardsEnabled)return;
        String key=e.getAdvancement().getKey().toString();Long amount=rewards.get(key);if(amount==null)return;
        try{
            // Advancement events can precede join handlers on some paths: never silently lose an award.
            if(store.account(e.getPlayer().getUniqueId()).isEmpty())joinPlayer(e.getPlayer());
            if(store.award(e.getPlayer().getUniqueId(),"adv:"+key,amount,System.currentTimeMillis())){
                var display=e.getAdvancement().getDisplay();
                var title=display==null?net.kyori.adventure.text.Component.text(key):display.title();
                e.getPlayer().sendMessage(NookUi.prefix("Nooks").append(net.kyori.adventure.text.Component.text("+"+Money.format(amount)+" · ",NookUi.PRICE)).append(title.colorIfAbsent(NookUi.BODY)).hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text(key))));
            }
        }catch(SQLException ex){fail(ex);}catch(IllegalArgumentException ex){getLogger().warning("Advancement reward was not paid: "+ex.getMessage());}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent e){
        if(e.getTo()==null || adminTeleports.consume(e) || e.getPlayer().hasPermission("nookcore.travel.bypass"))return;
        World.Environment env=e.getTo().getWorld().getEnvironment();
        if((env==World.Environment.NETHER && getConfig().getBoolean("travel.lock-nether",true)) || (env==World.Environment.THE_END && getConfig().getBoolean("travel.lock-end",true))){e.setCancelled(true);e.getPlayer().sendMessage(NookUi.message("NookCore","That dimension has not opened yet."));return;}
        if(getConfig().getBoolean("travel.block-command-teleports",true) && (e.getCause()==PlayerTeleportEvent.TeleportCause.COMMAND || e.getCause()==PlayerTeleportEvent.TeleportCause.PLUGIN)){
            e.setCancelled(true);e.getPlayer().sendMessage(NookUi.message("NookCore","Use the world's roads, rails and vanilla travel methods to get around."));
        }
    }
    private NookStore.Account resolve(String input)throws SQLException {
        try{return store.account(UUID.fromString(input)).orElseThrow(()->new IllegalArgumentException("Unknown account."));}
        catch(IllegalArgumentException e){return store.byName(input).orElseThrow(()->new IllegalArgumentException("Player must have joined this season. Use their current name or UUID."));}
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        String section=command.getName().equals("nookadmin")?"NookAdmin":"Nooks";
        if(command.getName().equals("nookadmin") && args.length>0 && Set.of("teleport","return").contains(args[0].toLowerCase(Locale.ROOT))){adminTeleports.execute(sender,args);return true;}
        if(!healthy){sender.sendMessage(NookUi.message(section,"The economy is paused. Please contact an admin."));return true;}
        try{
            long now=System.currentTimeMillis();
            if(command.getName().equals("nookadmin")){
                if(!sender.hasPermission("nookcore.admin")){sender.sendMessage(NookUi.problem(section,"You do not have permission to use this command."));return true;}
                CommandSyntax.check("nookadmin",args);
                if(args.length==3 && args[0].equalsIgnoreCase("plotdefine")){
                    store.definePlot(args[1],Money.parse(args[2]));sender.sendMessage(NookUi.message(section,"Plot record exists: "+args[1]+", "+Money.format(store.plot(args[1]).weekly())+"/week. Configure its WorldGuard mapping before rentals are enabled."));return true;
                }
                if(args.length>=4 && args[0].equalsIgnoreCase("plotabsence")){
                    long expires=0;
                    if(!args[2].equalsIgnoreCase("off")){int days=Integer.parseInt(args[2]);if(days<1 || days>365)throw new IllegalArgumentException("Choose 1–365 days, or off.");expires=now+java.time.Duration.ofDays(days).toMillis();}
                    store.setAbsence(args[1],expires,sender.getName(),String.join(" ",Arrays.copyOfRange(args,3,args.length)),now);
                    sender.sendMessage(NookUi.message(section,"Absence exception recorded. Rent still requires funds; this does not reopen a closed shop or waive rent."));return true;
                }
                if(args.length==2 && args[0].equalsIgnoreCase("plotstorage")){
                    var notes=store.storageNotes(args[1]);sender.sendMessage(NookUi.heading("NookAdmin · Storage notes for "+args[1]));
                    if(notes.isEmpty())sender.sendMessage(NookUi.text("No clearance storage notes have been recorded for this plot."));
                    for(var note:notes)sender.sendMessage(NookUi.text(NookUi.date(note.time())+" · "+note.detail()));
                    return true;
                }
                if(args.length>=3 && args[0].equalsIgnoreCase("plotclear")){
                    if(!rentalsReady() || !plotGate.manages(args[1]))throw new IllegalArgumentException("A working configured rental bridge is required.");
                    store.confirmCleared(args[1],sender.getName(),String.join(" ",Arrays.copyOfRange(args,2,args.length)),now);reconcilePlots();sender.sendMessage(NookUi.message(section,"Plot "+args[1]+" is now available to rent. Storage note saved; view it with ").append(NookUi.command("/nookadmin plotstorage "+args[1])).append(NookUi.text(". No blocks or items were moved.")));return true;
                }
                if(args.length==1 && args[0].equalsIgnoreCase("backup")){
                    Path folder=getDataFolder().toPath().resolve("backups");Files.createDirectories(folder);Path file=folder.resolve("nooks-"+now+".db");store.backup(file);sender.sendMessage(NookUi.message(section,"Consistent economy backup saved: "+file.getFileName()));return true;
                }
                if(args.length==2 && args[0].equalsIgnoreCase("awards") && Set.of("on","off").contains(args[1].toLowerCase(Locale.ROOT))){
                    awardsEnabled=args[1].equalsIgnoreCase("on");getConfig().set("advancement-rewards-enabled",awardsEnabled);saveConfig();sender.sendMessage(NookUi.message(section,"Advancement rewards "+(awardsEnabled?"enabled":"disabled")+". Disabled-period advancements are not backfilled."));return true;
                }
                if(args.length==2 && args[0].equalsIgnoreCase("balance")){var a=resolve(args[1]);sender.sendMessage(NookUi.prefix(section).append(NookUi.name(a.id(),a.name())).append(NookUi.text(": "+Money.format(a.cents()))));return true;}
                if(args.length>=4 && Set.of("give","take").contains(args[0].toLowerCase(Locale.ROOT))){var a=resolve(args[1]);long value=Money.parse(args[2]);if(args[0].equalsIgnoreCase("take"))value=-value;String reason=String.join(" ",Arrays.copyOfRange(args,3,args.length));store.adjust(a.id(),value,sender.getName(),reason,now);sender.sendMessage(NookUi.message(section,"Recorded adjustment for ").append(NookUi.name(a.id(),a.name())).append(NookUi.text(": "+Money.format(value))));return true;}
                NookUi.help(sender,"NookAdmin","/nookadmin teleport <player> [destination-player] — visit a player, or move one player to another","/nookadmin return [player] — return after a staff teleport (30-minute window)","/nookadmin balance <player> — inspect a balance","/nookadmin give <player> <amount> <reason> — credit Nooks","/nookadmin take <player> <amount> <reason> — debit Nooks","/nookadmin backup — save an economy snapshot","/nookadmin awards <on|off> — toggle advancement payments","/nookadmin plotdefine <id> <weekly-price> — create a plot record","/nookadmin plotclear <id> <storage-note> — release a cleared plot; record where belongings are stored","/nookadmin plotstorage <id> — read the latest 10 clearance storage notes","/nookadmin plotabsence <id> <days|off> <reason> — record an absence");return true;
            }
            if(!(sender instanceof Player p)){sender.sendMessage(NookUi.message(section,"Use /nookadmin balance <player> from console."));return true;}
            CommandSyntax.check("nooks",args);
            if(args.length==0){sender.sendMessage(NookUi.message(section,"Your balance: "+Money.format(store.account(p.getUniqueId()).orElseThrow().cents())));return true;}
            if(args.length==1 && args[0].equalsIgnoreCase("history")){
                var entries=store.history(p.getUniqueId());sender.sendMessage(NookUi.heading("Nooks · Recent transactions"));
                if(entries.isEmpty())sender.sendMessage(NookUi.message(section,"No transactions yet."));
                for(var row:entries)sender.sendMessage(NookUi.history(row));
                sender.sendMessage(net.kyori.adventure.text.Component.text("Showing up to 10 entries. Hover for audit details on supported clients.",NookUi.MUTED));return true;
            }
            if(args.length==3 && args[0].equalsIgnoreCase("pay")){
                var recipient=resolve(args[1]);long amount=Money.parse(args[2]);store.transfer(p.getUniqueId(),recipient.id(),amount,now);sender.sendMessage(NookUi.message(section,"Paid ").append(NookUi.name(recipient.id(),recipient.name())).append(NookUi.text(" "+Money.format(amount)+".")));Player target=Bukkit.getPlayer(recipient.id());if(target!=null)target.sendMessage(NookUi.prefix("Nooks").append(NookUi.name(p.getUniqueId(),p.getName())).append(NookUi.text(" paid you "+Money.format(amount)+".")));return true;
            }
            NookUi.help(sender,"Nooks","/nooks — check your balance","/nooks pay <player> <amount> — send Nooks, including offline players","/nooks history — recent transactions");return true;
        }catch(NumberFormatException e){sender.sendMessage(NookUi.problem(section,"Choose a whole number of days from 1 to 365, or off."));return true;}
        catch(IllegalArgumentException e){sender.sendMessage(NookUi.problem(section,e.getMessage()));return true;}
        catch(Exception e){fail(e);sender.sendMessage(NookUi.message(section,"The operation could not be confirmed. Contact staff before retrying."));return true;}
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        boolean admin=command.getName().equals("nookadmin");if(!healthy || admin && !sender.hasPermission("nookcore.admin"))return List.of();
        if(admin && args.length>=2 && args.length<=3 && Set.of("teleport","return").contains(args[0].toLowerCase(Locale.ROOT)))return AdminTeleports.authorised(sender)?NookUi.complete(args[args.length-1],Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()):List.of();
        List<String> values=new ArrayList<>();
        try{
            if(args.length==1)values.addAll(admin?List.of("balance","give","take","backup","awards","plotdefine","plotclear","plotstorage","plotabsence","help","teleport","return"):List.of("pay","history","help"));
            else if(args.length==2){
                if(Set.of("pay","balance","give","take").contains(args[0].toLowerCase(Locale.ROOT))){for(var a:store.accounts())values.add(a.name());}
                else if(admin && args[0].equalsIgnoreCase("awards"))values.addAll(List.of("on","off"));
                else if(admin && Set.of("plotclear","plotstorage","plotabsence").contains(args[0].toLowerCase(Locale.ROOT)))for(var p:store.plots())if(!args[0].equalsIgnoreCase("plotclear") || rentalsReady() && plotGate.manages(p.id()))values.add(p.id());
            }
        }catch(SQLException e){fail(e);}
        return NookUi.complete(args[args.length-1],values);
    }
    @Override public void onDisable(){healthy=false;if(chat!=null)chat.close();getServer().getServicesManager().unregisterAll(this);if(store!=null)try{store.close();}catch(SQLException e){getLogger().log(Level.SEVERE,"Database close failed",e);}}
}
