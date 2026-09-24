package net.nikosnook.core;



import org.bukkit.Bukkit;

import org.bukkit.command.*;

import org.bukkit.entity.Player;

import java.sql.SQLException;

import java.util.*;

import java.util.function.*;



/** Commands stay disabled until explicitly enabled on staging with the protection bridge. */

public final class PlotCommands implements CommandExecutor, TabCompleter {

    private final NookStore store;

    private final BooleanSupplier ready;

    private final Runnable reconcile;

    private final Consumer<Exception> failure;

    private final Predicate<String> managed;

    private final Runnable validate;

    private final LongSupplier clock;
    private final BiConsumer<UUID,net.kyori.adventure.text.Component> notifyPlayer;
    private BiConsumer<Player,String> locator=(player,plot)->player.sendMessage(NookUi.message("NookPlots","Plot directions are unavailable right now."));
    void locator(BiConsumer<Player,String> locator){this.locator=locator;}
    static net.kyori.adventure.text.Component findLink(String plot){
        return net.kyori.adventure.text.Component.text(" [Find]",NookUi.COMMAND).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/nookplots find "+plot));
    }
    private record PendingAbandonment(NookStore.AbandonmentQuote quote,long expires) {}
    private final Map<UUID,PendingAbandonment> pendingAbandonments=new HashMap<>();

    public PlotCommands(NookStore store,BooleanSupplier ready,Runnable reconcile,Consumer<Exception> failure,Predicate<String> managed,Runnable validate){this(store,ready,reconcile,failure,managed,validate,System::currentTimeMillis,(id,message)->{Player target=Bukkit.getPlayer(id);if(target!=null)target.sendMessage(message);});}
    PlotCommands(NookStore store,BooleanSupplier ready,Runnable reconcile,Consumer<Exception> failure,Predicate<String> managed,Runnable validate,LongSupplier clock){this(store,ready,reconcile,failure,managed,validate,clock,(id,message)->{});}
    PlotCommands(NookStore store,BooleanSupplier ready,Runnable reconcile,Consumer<Exception> failure,Predicate<String> managed,Runnable validate,LongSupplier clock,BiConsumer<UUID,net.kyori.adventure.text.Component> notifyPlayer){this.store=store;this.ready=ready;this.reconcile=reconcile;this.failure=failure;this.managed=managed;this.validate=validate;this.clock=clock;this.notifyPlayer=notifyPlayer;}

    static net.kyori.adventure.text.Component expiryNotice(String plot){
        return NookUi.prefix("NookPlots")
            .append(NookUi.text("Your abandonment confirmation for "+plot+" expired. Nothing was changed. "))
            .append(net.kyori.adventure.text.Component.text("[Review again]",NookUi.COMMAND)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand("/nookplots abandon "+plot)));
    }
    void expireConfirmations(BiConsumer<UUID,String> notify){
        long now=clock.getAsLong();
        var iterator=pendingAbandonments.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();
            if(now>=entry.getValue().expires()){
                UUID actor=entry.getKey();String plot=entry.getValue().quote().plot();
                iterator.remove();notify.accept(actor,plot);
            }
        }
    }

    static String role(String input){return input.equalsIgnoreCase("both")?"BUILD_STOCK":input.toUpperCase(Locale.ROOT);}

    private UUID player(String text)throws SQLException {

        UUID id;

        try{id=UUID.fromString(text);}catch(IllegalArgumentException ex){return store.byName(text).orElseThrow(()->new IllegalArgumentException("Unknown or ambiguous player; use a known UUID.")).id();}

        if(store.account(id).isEmpty())throw new IllegalArgumentException("That player has not joined this season.");return id;

    }

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){

        if(!ready.getAsBoolean()){sender.sendMessage(NookUi.message("NookPlots","Plot rentals are unavailable right now. Please ask staff for help."));return true;}



        try {

            CommandSyntax.check("nookplots",args);

            validate.run(); // No debit or membership mutation until live protection is verified.

            if(args.length==0 || args.length==1 && args[0].equalsIgnoreCase("list")){

                sender.sendMessage(NookUi.heading("NookPlots · Shopping district"));

                for(var plot:store.plots())if(managed.test(plot.id())){

                    sender.sendMessage(NookUi.text("• ").append(NookUi.plot(store,plot)).append(findLink(plot.id())));

                    for(var member:store.members(plot.id()).entrySet())if(!member.getKey().equals(plot.owner()))sender.sendMessage(NookUi.text("  Co-owner: ").append(NookUi.name(store,member.getKey())));

                }

                sender.sendMessage(net.kyori.adventure.text.Component.text("/nookplots help — commands and permissions",NookUi.MUTED).clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand("/nookplots help")));return true;

            }

            if(args.length==2 && args[0].equalsIgnoreCase("info")){
                if(!managed.test(args[1]))throw new IllegalArgumentException("That plot is not part of the shopping district.");
                var plot=store.plot(args[1]);sender.sendMessage(NookUi.heading("NookPlots · "+plot.id()));
                sender.sendMessage(NookUi.plot(store,plot));
                if(plot.owner()!=null){
                    sender.sendMessage(NookUi.text("Paid until: "+NookUi.date(plot.paidUntil())));
                    sender.sendMessage(NookUi.text("Future prepaid weeks: "+NookStore.prepaidWeeks(plot,clock.getAsLong())+"/4 · excludes the current rental week"));
                }
                return true;
            }
            if(!(sender instanceof Player p)){
                NookUi.help(sender,"NookPlots · Console","/nookplots list — view plots, owners and availability","/nookadmin plotclear <plot> <storage-note> — release a plot after saving belongings and clearing it");
                sender.sendMessage(NookUi.message("NookPlots","Renting and reopening require the renter to run /nookplots in game."));return true;
            }

            long now=clock.getAsLong();UUID actor=p.getUniqueId();
            var expired=pendingAbandonments.get(actor);
            if(expired!=null && now>=expired.expires()){
                pendingAbandonments.remove(actor);
                sender.sendMessage(expiryNotice(expired.quote().plot()));
                if(args.length==3 && args[0].equalsIgnoreCase("abandon") && args[2].equalsIgnoreCase("confirm"))return true;
            }

            if(args.length>1 && Set.of("rent","invite","role","remove","prepay","reopen","abandon").contains(args[0].toLowerCase(Locale.ROOT)) && !managed.test(args[1]))throw new IllegalArgumentException("That plot has no configured protection mapping.");

            switch(args[0].toLowerCase(Locale.ROOT)){
                case "find" -> {
                    String plot=args.length==2?args[1]:null;
                    if(plot==null){
                        for(var candidate:store.plots())if(managed.test(candidate.id()) && store.members(candidate.id()).containsKey(actor)){plot=candidate.id();break;}
                        if(plot==null)throw new IllegalArgumentException("You do not belong to a plot. Use /nookplots find <plot> to locate one from the list.");
                    }
                    if(!managed.test(plot))throw new IllegalArgumentException("That plot is not part of the shopping district.");
                    locator.accept(p,plot);return true;
                }
                case "abandon" -> {
                    if(args.length<2 || args.length>3)throw new IllegalArgumentException("Use /nookplots abandon <plot> to review the refund before confirming.");
                    if(args.length==2){
                        var quote=store.abandonmentQuote(args[1],actor,now);
                        pendingAbandonments.put(actor,new PendingAbandonment(quote,now+60_000));
                        sender.sendMessage(net.kyori.adventure.text.Component.text("Abandon "+quote.plot()+"?",NookUi.WARNING));
                        sender.sendMessage(NookUi.text("Refund: "+Money.format(quote.refund())+" for wholly unused prepaid weeks. The current rental week is not refunded."));
                        sender.sendMessage(NookUi.text("Sales stop and all members lose access immediately. Everyone can rent elsewhere; this plot stays closed until staff clear it."));
                        sender.sendMessage(NookUi.text("No blocks or items are deleted. Contact staff for collection; belongings are kept indefinitely."));
                        sender.sendMessage(net.kyori.adventure.text.Component.text("Within 60 seconds: /nookplots abandon "+quote.plot()+" confirm",NookUi.COMMAND));
                        return true;
                    }
                    if(!args[2].equalsIgnoreCase("confirm"))throw new IllegalArgumentException("Use /nookplots abandon "+args[1]+" first, then add confirm if you want to proceed.");
                    var pending=pendingAbandonments.get(actor);
                    if(pending==null || !pending.quote().plot().equals(args[1]))throw new IllegalArgumentException("Review /nookplots abandon "+args[1]+" first. Confirmation expires after 60 seconds.");
                    // Consume before attempting the transaction: a failed confirmation must be reviewed again.
                    pendingAbandonments.remove(actor);
                    long refund=store.abandon(pending.quote(),actor,now);
                    reconcile.run();
                    sender.sendMessage(NookUi.message("NookPlots","Plot abandoned · "+Money.format(refund)+" refunded."));
                    sender.sendMessage(NookUi.text("Your plot allowance is free. Contact staff to collect your shop belongings."));
                    return true;
                }
                case "leave" -> {
                    if(args.length!=1)break;
                    for(var plot:store.plots())if(managed.test(plot.id()) && !store.role(plot.id(),actor).equals("NONE")){
                        store.leavePlot(plot.id(),actor,now);reconcile.run();sender.sendMessage(NookUi.message("NookPlots","You left "+plot.id()+". Your build and stock access has ended."));return true;
                    }
                    throw new IllegalArgumentException("You do not belong to a rented plot.");
                }
                case "rent" -> {if(args.length!=2)break;store.rent(args[1],actor,now);reconcile.run();sender.sendMessage(NookUi.message("NookPlots","Rented "+args[1]+" for "+Money.format(store.plot(args[1]).weekly())+". Rent renews automatically while you remain active and have enough Nooks. You can prepay up to four future weeks.").append(findLink(args[1])));return true;}

                case "invite" -> {

                    if(args.length!=4)break;

                    UUID member=player(args[2]);

                    if(!store.role(args[1],member).equals("NONE")){store.changeRole(args[1],actor,member,role(args[3]),now);reconcile.run();notifyRole(member,args[1],args[3]);sender.sendMessage(NookUi.message("NookPlots","Member permissions updated to "+(role(args[3]).equals("BUILD_STOCK")?"build and stock":args[3].toLowerCase(Locale.ROOT))+"."));return true;}

                    var invite=store.invite(args[1],actor,member,role(args[3]),now);

                    sender.sendMessage(NookUi.message("NookPlots","Invitation sent · expires in 24 hours."));

                    Player target=Bukkit.getPlayer(invite.member());if(target!=null)showInvitation(target,invite);return true;

                }

                case "invitations" -> {

                    if(args.length!=1)break;var invites=store.invitations(actor,now);

                    if(invites.isEmpty())sender.sendMessage(NookUi.message("NookPlots","You have no current plot invitations."));

                    for(var i:invites)showInvitation(sender,i);return true;

                }

                case "accept" -> {if(args.length>2)break;var invitation=resolveInvitation(actor,args.length==2?args[1]:"",now);if(!managed.test(invitation.plot()))throw new IllegalArgumentException("That plot has no configured protection mapping.");store.acceptInvitation(invitation.token(),actor,now);reconcile.run();notifyPlayer.accept(invitation.inviter(),notice().append(NookUi.name(store,actor)).append(NookUi.text(" accepted your invitation to "+invitation.plot()+".")));sender.sendMessage(NookUi.message("NookPlots","Invitation accepted. You now co-own "+invitation.plot()+"."));return true;}

                case "decline" -> {if(args.length>2)break;var invitation=resolveInvitation(actor,args.length==2?args[1]:"",now);store.declineInvitation(invitation.token(),actor,now);notifyPlayer.accept(invitation.inviter(),notice().append(NookUi.name(store,actor)).append(NookUi.text(" declined your invitation to "+invitation.plot()+".")));sender.sendMessage(NookUi.message("NookPlots","Invitation declined."));return true;}

                case "role" -> {if(args.length!=4)break;UUID member=player(args[2]);store.changeRole(args[1],actor,member,role(args[3]),now);reconcile.run();notifyRole(member,args[1],args[3]);sender.sendMessage(NookUi.message("NookPlots","Plot permissions updated."));return true;}

                case "remove" -> {if(args.length!=3)break;UUID member=player(args[2]);store.removeMember(args[1],actor,member,now);reconcile.run();notifyPlayer.accept(member,notice().append(NookUi.text("Your access to "+args[1]+" was removed. You can no longer build or manage stock there.")));sender.sendMessage(NookUi.message("NookPlots","Member removed and any pending invitation withdrawn."));return true;}

                case "prepay" -> {if(args.length!=3)break;store.prepay(args[1],actor,Integer.parseInt(args[2]),now);sender.sendMessage(NookUi.message("NookPlots","Rent prepaid · paid until "+NookUi.date(store.plot(args[1]).paidUntil())+" · Future prepaid weeks: "+NookStore.prepaidWeeks(store.plot(args[1]),now)+"/4."));return true;}

                case "reopen" -> {if(args.length!=2)break;long charged=store.reopen(args[1],actor,now);sender.sendMessage(NookUi.message("NookPlots","Shop reopened for "+Money.format(charged)+"; the original billing date is unchanged."));return true;}

            }

            NookUi.help(sender,"NookPlots","/nookplots abandon <plot> — review closure and a prepaid-week refund","/nookplots leave — leave as a co-owner","/nookplots list — see prices, owners and availability","/nookplots find [plot] — locate your plot or a named plot","/nookplots info <plot> — check paid-through date and prepaid weeks","/nookplots rent <plot> — rent an available plot","/nookplots invite <plot> <player> <build|stock|both> — invite or update a member","/nookplots invitations — see your invitations","/nookplots accept [player] — accept; omit player if only one invitation","/nookplots decline [player] — decline an invitation","/nookplots role <plot> <player> <build|stock|both> — replace their permissions","/nookplots remove <plot> <player> — remove a member","/nookplots prepay <plot> <weeks> — pay ahead, up to four weeks","/nookplots reopen <plot> — pay remaining rent and resume sales");

        }catch(NumberFormatException ex){sender.sendMessage(NookUi.problem("NookPlots","Choose a whole number of weeks from 1 to 4."));}
        catch(IllegalArgumentException ex){sender.sendMessage(NookUi.problem("NookPlots",ex.getMessage()));}

        catch(Exception ex){failure.accept(ex);sender.sendMessage(NookUi.message("NookPlots","Could not confirm the plot operation. Payments/protection are paused; contact staff before retrying."));}

        return true;

    }

    NookStore.Invitation resolveInvitation(UUID actor,String selector,long now)throws SQLException {

        var invitations=store.invitations(actor,now);

        List<NookStore.Invitation> matches=new ArrayList<>();

        for(var invite:invitations){String name=store.account(invite.inviter()).orElseThrow().name();if(selector.isEmpty() || name.equalsIgnoreCase(selector) || invite.token().toString().equalsIgnoreCase(selector))matches.add(invite);}

        if(matches.isEmpty())throw new IllegalArgumentException(selector.isEmpty()?"You have no current plot invitations.":"No current invitation from that player. Try /nookplots invitations.");

        if(matches.size()>1)throw new IllegalArgumentException("You have several invitations. Use /nookplots accept <player> to choose.");

        return matches.getFirst();

    }

    private static net.kyori.adventure.text.Component notice(){return NookUi.prefix("NookPlots");}
    private void notifyRole(UUID member,String plot,String role){
        notifyPlayer.accept(member,notice().append(NookUi.text("Your permissions in "+plot+" are now "+(role(role).equals("BUILD_STOCK")?"build and stock":role.toLowerCase(Locale.ROOT))+".")));
    }
    private void showInvitation(CommandSender sender,NookStore.Invitation invite)throws SQLException {
        String name=store.account(invite.inviter()).orElseThrow().name();
        sender.sendMessage(notice().append(NookUi.name(store,invite.inviter())).append(NookUi.text(" invited you to "+invite.plot()+".")));
        sender.sendMessage(net.kyori.adventure.text.Component.text("Permissions: "+(invite.role().equals("BUILD_STOCK")?"build and stock":invite.role().toLowerCase(Locale.ROOT))+" · Expires "+NookUi.date(invite.expires()),NookUi.MUTED));
        sender.sendMessage(net.kyori.adventure.text.Component.text("[Accept]",NookUi.COMMAND)
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/nookplots accept "+invite.token()))
            .append(net.kyori.adventure.text.Component.text("  "))
            .append(net.kyori.adventure.text.Component.text("[Decline]",NookUi.COMMAND).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/nookplots decline "+invite.token()))));
        sender.sendMessage(NookUi.text("Joining uses your one-plot allowance."));
        sender.sendMessage(net.kyori.adventure.text.Component.text("Or type /nookplots <accept|decline> "+name,NookUi.MUTED));
    }

    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){

        if(!ready.getAsBoolean() || !(sender instanceof Player player))return List.of();

        try{

            List<String> values=new ArrayList<>();String sub=args[0].toLowerCase(Locale.ROOT);

            if(args.length==1)values.addAll(List.of("abandon","leave","list","info","find","help","rent","invite","invitations","accept","decline","role","remove","prepay","reopen"));

            else if(args.length==2){

                if(Set.of("accept","decline").contains(sub)){for(var i:store.invitations(player.getUniqueId(),System.currentTimeMillis()))values.add(store.account(i.inviter()).orElseThrow().name());}

                else if((sub.equals("info") || sub.equals("find"))){for(var plot:store.plots())if(managed.test(plot.id()))values.add(plot.id());}
                else if(Set.of("rent","invite","role","remove","prepay","reopen","abandon").contains(sub)){for(var plot:store.plots())if(managed.test(plot.id()) && (sub.equals("rent")?plot.state().equals("AVAILABLE"):player.getUniqueId().equals(plot.owner()) && store.role(plot.id(),player.getUniqueId()).equals("OWNER")))values.add(plot.id());}

            }else if(args.length==3){

                if(sub.equals("abandon")){var pending=pendingAbandonments.get(player.getUniqueId());if(pending!=null && clock.getAsLong()<pending.expires() && pending.quote().plot().equals(args[1]))values.add("confirm");}

                if(sub.equals("invite")){for(var a:store.accounts())if(!a.id().equals(player.getUniqueId()))values.add(a.name());}

                else if(Set.of("role","remove").contains(sub)){for(UUID member:store.members(args[1]).keySet())if(!member.equals(player.getUniqueId()))values.add(store.account(member).orElseThrow().name());}

                else if(sub.equals("prepay"))values.addAll(List.of("1","2","3","4"));

            }else if(args.length==4 && Set.of("invite","role").contains(sub))values.addAll(List.of("build","stock","both"));

            return NookUi.complete(args[args.length-1],values);

        }catch(SQLException e){failure.accept(e);return List.of();}

    }

}
