package net.nikosnook.core;

import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

/** Grouped public routes; underlying handlers retain permission and transaction checks. */
final class CommandNavigation implements CommandExecutor,TabCompleter {
    private final JavaPlugin plugin;
    private final Map<String,CommandExecutor> handlers=new HashMap<>();
    private final Map<String,TabCompleter> completers=new HashMap<>();
    CommandNavigation(JavaPlugin plugin){this.plugin=plugin;for(String name:List.of("nooks","nookadmin","plots","quests","nookshops")){var c=plugin.getCommand(name);handlers.put(name,c.getExecutor());completers.put(name,c.getTabCompleter());c.setExecutor(this);c.setTabCompleter(this);}}
    private boolean run(String root,CommandSender s,String[] a){return handlers.get(root).onCommand(s,plugin.getCommand(root),root,a);}
    static String[] prepend(String first,String[] a){String[] out=new String[a.length+1];out[0]=first;System.arraycopy(a,0,out,1,a.length);return out;}
    static String[] tail(String[] a,int n){return Arrays.copyOfRange(a,Math.min(n,a.length),a.length);}
    static String[] plotArgs(String[] a){
        if(a.length>=2 && a[0].equalsIgnoreCase("invite")){
            String child=a[1].toLowerCase(Locale.ROOT);
            if(Set.of("accept","decline","list","send").contains(child))return prepend(child.equals("list")?"invitations":child.equals("send")?"invite":child,tail(a,2));
        }
        if(a.length>=2 && a[0].equalsIgnoreCase("rent") && Set.of("prepay","reopen").contains(a[1].toLowerCase(Locale.ROOT)))return prepend(a[1],tail(a,2));
        return a;
    }
    private boolean help(CommandSender s,String title,String...lines){NookUi.help(s,title,lines);return true;}
    @Override public boolean onCommand(CommandSender s,Command c,String label,String[] args){
        String root=c.getName(),sub=args.length==0?"":args[0].toLowerCase(Locale.ROOT);
        if(root.equals("nookadmin")){
            if(!s.hasPermission("nookcore.admin")){s.sendMessage(NookUi.problem("NookAdmin","This command is for admins."));return true;}
            if(sub.isEmpty() || sub.equals("help"))return help(s,"Administration","/nookadmin economy — balances, reports and adjustments","/nookadmin plots — setup and rental administration","/nookadmin quests — quest administration and contributions","/nookadmin shops — review and recovery","/nookadmin status — build and health","/staffmode — staff presence and travel","/invsee <player> — inventory inspection");
            String[] rest=tail(args,1);
            if(sub.equals("economy")){
                if(rest.length==0 || rest[0].equalsIgnoreCase("help"))return help(s,"Economy administration","/nookadmin economy balance <player>","/nookadmin economy give <player> <amount> <reason>","/nookadmin economy take <player> <amount> <reason>","/nookadmin economy report [days]","/nookadmin economy metrics [days]","/nookadmin economy metrics export [days]","/nookadmin economy backup","/nookadmin economy advancements <on|off>");
                return run("nooks",s,rest);
            }
            if(sub.equals("plots")){
                if(rest.length>0 && rest[0].equalsIgnoreCase("setup"))return run("plots",s,rest);
                return run("plots",s,prepend("admin",rest));
            }
            if(sub.equals("quests")){
                if(rest.length==0 || rest[0].equalsIgnoreCase("help"))return help(s,"Quest administration","/nookadmin quests inspect <player>","/nookadmin quests contributions — list community stock","/nookadmin quests contributions collect <item> <quantity>","/nookadmin quests activate-config [confirm] — preview replacement; fresh progress for everyone");
                if(rest[0].equalsIgnoreCase("contributions") && rest.length>1){
                    if(rest.length!=4 || !rest[1].equalsIgnoreCase("collect"))return help(s,"Contributions","/nookadmin quests contributions collect <item> <quantity>");
                    return run("quests",s,new String[]{"collect",rest[2],rest[3]});
                }
                return run("quests",s,rest);
            }
            if(sub.equals("shops")){
                if(rest.length==0 || rest[0].equalsIgnoreCase("help"))return help(s,"Shop administration","/nookadmin shops review [receipt]","/nookadmin shops recover-stock <receipt> <credit|no-credit> <reason>","/nookadmin shops recover-stock <receipt> confirm");
                if(!Set.of("review","recover-stock").contains(rest[0].toLowerCase(Locale.ROOT)))return help(s,"Shop administration","/nookadmin shops review [receipt]","/nookadmin shops recover-stock <receipt> <credit|no-credit> <reason>");
                return run("nookshops",s,rest);
            }
            if(!Set.of("status","teleport","return").contains(sub)){s.sendMessage(NookUi.text("Not quite right. Try /nookadmin help"));return true;}
            return run(root,s,args);
        }
        if(root.equals("plots")){
            if(sub.isEmpty() || sub.equals("help"))return help(s,"Plots","/plots list [page] — available plots","/plots info [plot] — rent and members","/plots find [plot] — directions","/plots rent — rent and prepay","/plots invite — invitations","/plots name <plot> <name|reset>","/plots sharing <plot> [on|off]","/plots role <plot> <player> <build|stock|both>","/plots remove <plot> <player>","/plots leave","/plots abandon <plot> [confirm]");
            if(sub.equals("invite") && (args.length==1 || args[1].equalsIgnoreCase("help")))return help(s,"Invitations","/plots invite send <plot> <player> <build|stock|both>","/plots invite list","/plots invite accept [player]","/plots invite decline [player]");
            if(sub.equals("rent") && (args.length==1 || args[1].equalsIgnoreCase("help")))return help(s,"Rent","/plots rent <plot>","/plots rent prepay <plot> <weeks>","/plots rent reopen <plot>");
            if(sub.equals("admin") && args.length>1 && args[1].equalsIgnoreCase("setup"))return run(root,s,tail(args,1));
            return run(root,s,plotArgs(args));
        }
        if(root.equals("nookshops")){
            if(sub.isEmpty() || sub.equals("help"))return help(s,"Shops","/nookshops create [payment price] — hold the item you want to sell","/nookshops price <nooks> — change your setup price","/nookshops payment <item> [quantity] — choose item payment","/nookshops mine — your shops and stock","Open a shop to stock it, collect items or close it.");
            if(Set.of("cancel","preview","collect","manage").contains(sub)){s.sendMessage(NookUi.message("NookShops","Open the shop to manage stock and collect items. Cancel setup using its Cancel button."));return true;}
            if(sub.equals("create") && args.length!=1 && args.length!=3)return help(s,"Create shop","/nookshops create [payment price] — for example: /nookshops create nooks 2.50");
            if(sub.equals("mine") && args.length!=1)return help(s,"Your shops","/nookshops mine");
            if(sub.equals("price") && args.length!=2)return help(s,"Shop price","/nookshops price <nooks>");
            if(sub.equals("payment") && args.length!=2 && args.length!=3)return help(s,"Shop payment","/nookshops payment <item> [quantity]");
            if(!Set.of("create","mine","price","payment","review","recover-stock").contains(sub)){s.sendMessage(NookUi.text("Not quite right. Try /nookshops help"));return true;}
        }
        if(root.equals("quests") && sub.equals("collect") && args.length!=3)return help(s,"Contributions","/nookadmin quests contributions collect <item> <quantity>");
        return run(root,s,args);
    }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String label,String[] a){
        String root=c.getName();List<String> choices=null;
        if(root.equals("nookadmin")){
            if(!s.hasPermission("nookcore.admin"))return List.of();
            if(a.length==1)choices=List.of("economy","plots","quests","shops","status","help");
            else if(a.length==2)choices=switch(a[0].toLowerCase(Locale.ROOT)){case "economy"->List.of("balance","give","take","report","metrics","backup","advancements");case "plots"->List.of("setup","clear","evict","storage","membership","absence","define","rent-cut");case "quests"->List.of("inspect","contributions","activate-config");case "shops"->List.of("review","recover-stock");default->List.of();};
            else if(a[0].equalsIgnoreCase("plots"))return complete("plots",s,a[1].equalsIgnoreCase("setup")?tail(a,1):prepend("admin",tail(a,1)));
            else if(a[0].equalsIgnoreCase("economy"))return complete("nooks",s,tail(a,1));
            else if(a[0].equalsIgnoreCase("quests")){if(a[1].equalsIgnoreCase("contributions") && a.length==3)choices=List.of("collect");else return complete("quests",s,tail(a,1));}
        }
        if(root.equals("plots")){
            if(a.length==1)choices=List.of("list","info","find","rent","invite","name","sharing","role","remove","leave","abandon","help");
            else if(a.length==2 && a[0].equalsIgnoreCase("invite"))choices=List.of("send","list","accept","decline","help");
            else if(a.length==2 && a[0].equalsIgnoreCase("rent")){var result=new ArrayList<>(complete(root,s,a));result.addAll(NookUi.complete(a[1],List.of("prepay","reopen","help")));return result;}
            else return complete(root,s,plotArgs(a));
        }
        if(root.equals("nookshops") && a.length==1)choices=List.of("create","mine","price","payment","help");
        if(root.equals("nookshops") && a.length==2 && a[0].equalsIgnoreCase("create"))choices=List.of("nooks","diamond","emerald");
        return choices==null?complete(root,s,a):NookUi.complete(a[a.length-1],choices);
    }
    private List<String> complete(String root,CommandSender s,String[] a){var t=completers.get(root);if(t==null || a.length==0)return List.of();var result=t.onTabComplete(s,plugin.getCommand(root),root,a);return result==null?List.of():result;}
}
