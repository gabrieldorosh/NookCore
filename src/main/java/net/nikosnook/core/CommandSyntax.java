package net.nikosnook.core;

import java.util.*;

/** Validate shapes before command handlers can mutate preferences, money or memberships. */
final class CommandSyntax {
    private record Shape(int min,int max,String example,String explanation) {}
    private static Shape exact(int count,String example,String explanation){return new Shape(count,count,example,explanation);}
    private static final Map<String,Map<String,Shape>> SHAPES=Map.of(
        "nookchat",Map.of(
            "help",exact(1,"/nookchat help","Help does not take any extra words."),
            "preview",exact(1,"/nookchat preview","Preview does not take any extra words."),
            "colour",exact(2,"/nookchat colour aqua","Choose a colour name, #RRGGBB, or reset."),
            "pronouns",new Shape(2,Integer.MAX_VALUE,"/nookchat pronouns they/them","Choose pronouns, or use reset to hide them.")),
        "nooks",Map.of(
            "top",new Shape(1,2,"/nooks top [page]","View the balance leaderboard."),
            "help",exact(1,"/nooks help","Help does not take any extra words."),
            "history",exact(1,"/nooks history","History does not take any extra words."),
            "pay",exact(3,"/nooks pay <player> <amount>","Enter a player and the amount to send.")),
        "nookplots",Map.ofEntries(
            Map.entry("help",exact(1,"/nookplots help","Help does not take any extra words.")),
            Map.entry("list",new Shape(1,2,"/nookplots list [page]","Choose an optional page number.")),
            Map.entry("leave",exact(1,"/nookplots leave","Leave applies to the plot you currently co-own.")),
            Map.entry("find",new Shape(1,2,"/nookplots find [plot]","Find your own plot, or enter a plot name.")),
            Map.entry("name",new Shape(3,34,"/nookplots name <plot> <name|reset>","Enter a plot ID and shop name, or reset.")),
            Map.entry("info",new Shape(1,2,"/nookplots info [address]","View the plot you are standing in, or choose an address.")),
            Map.entry("rent",exact(2,"/nookplots rent <plot>","Enter the plot you want to rent.")),
            Map.entry("invite",exact(4,"/nookplots invite <plot> <player> <build|stock|both>","Enter a plot, player and the permissions to give them.")),
            Map.entry("role",exact(4,"/nookplots role <plot> <player> <build|stock|both>","Enter a plot, member and their new permissions.")),
            Map.entry("remove",exact(3,"/nookplots remove <plot> <player>","Enter the plot and the member to remove.")),
            Map.entry("prepay",exact(3,"/nookplots prepay <plot> <weeks>","Enter the plot and a whole number of weeks, from 1 to 4.")),
            Map.entry("reopen",exact(2,"/nookplots reopen <plot>","Enter the plot you want to reopen.")),
            Map.entry("invitations",exact(1,"/nookplots invitations","Invitations does not take any extra words.")),
            Map.entry("accept",new Shape(1,2,"/nookplots accept [player]","Choose one inviter, or omit the name when you have one invitation.")),
            Map.entry("decline",new Shape(1,2,"/nookplots decline [player]","Choose one inviter, or omit the name when you have one invitation.")),
            Map.entry("abandon",new Shape(2,3,"/nookplots abandon <plot>","Enter the plot to review before confirming abandonment."))),
        "nookadmin",Map.ofEntries(
            Map.entry("return",new Shape(1,2,"/nookadmin return [player]","Return yourself, or name one online player.")),
            Map.entry("teleport",new Shape(2,5,"/nookadmin teleport <player> [destination-player]","Use one name to visit a player, or two names to move one player to another.")),
            Map.entry("help",exact(1,"/nookadmin help","Help does not take any extra words.")),
            Map.entry("balance",exact(2,"/nookadmin balance <player>","Enter the player whose balance you want to inspect.")),
            Map.entry("give",new Shape(4,Integer.MAX_VALUE,"/nookadmin give <player> <amount> <reason>","Enter a player, amount and an audit reason.")),
            Map.entry("take",new Shape(4,Integer.MAX_VALUE,"/nookadmin take <player> <amount> <reason>","Enter a player, amount and an audit reason.")),
            Map.entry("backup",exact(1,"/nookadmin backup","Backup does not take any extra words.")),
            Map.entry("awards",exact(2,"/nookadmin awards <on|off>","Choose on or off.")),
            Map.entry("plotdefine",exact(3,"/nookadmin plotdefine <id> <weekly-price>","Enter a plot ID and weekly rent.")),
            Map.entry("plotstorage",exact(2,"/nookadmin plotstorage <plot>","Choose a plot to read its recorded storage notes.")),
            Map.entry("plotmembership",exact(2,"/nookadmin plotmembership <player>","Inspect the membership using that player's plot allowance.")),
            Map.entry("plotevict",new Shape(3,Integer.MAX_VALUE,"/nookadmin plotevict <address> <reason|confirm>","Preview an eviction and refund, then confirm within 60 seconds.")),
            Map.entry("plotclear",new Shape(3,Integer.MAX_VALUE,"/nookadmin plotclear <plot> <storage-note>","This makes a cleared plot available to rent again; it does not clear blocks. First store the former renter's belongings, then describe where they are kept (for example: staff storage, chest A3).")),
            Map.entry("plotabsence",new Shape(4,Integer.MAX_VALUE,"/nookadmin plotabsence <plot> <days|off> <reason>","Enter a plot, absence duration and an audit reason.")))
    );
    static void check(String command,String[] args){
        if(args.length==0)return;
        String action=args[0].toLowerCase(Locale.ROOT);
        Shape shape=SHAPES.get(command).get(action);
        if(shape==null)throw new IllegalArgumentException("Unknown command. Use /"+command+" help to see the available commands.");
        if(args.length<shape.min() || args.length>shape.max())throw new IllegalArgumentException(shape.explanation()+" Try "+shape.example()+".");
        if(command.equals("nookplots") && Set.of("invite","role").contains(action)){
            if(!Set.of("build","stock","both","build_stock").contains(args[3].toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Choose build, stock, or both for their permissions.");
        }
        if(command.equals("nookplots") && action.equals("prepay"))integer(args[2],1,4,"Choose a whole number of weeks from 1 to 4.");
        if(command.equals("nookadmin") && action.equals("plotabsence") && !args[2].equalsIgnoreCase("off"))integer(args[2],1,365,"Choose a whole number of days from 1 to 365, or off.");
        if(command.equals("nookadmin") && action.equals("awards") && !Set.of("on","off").contains(args[1].toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Choose on or off. Try /nookadmin awards off.");
    }
    private static void integer(String input,int min,int max,String message){
        try{int value=Integer.parseInt(input);if(value<min || value>max)throw new NumberFormatException();}
        catch(NumberFormatException e){throw new IllegalArgumentException(message);}
    }
    static boolean chatHelp(String[] args){return args.length==0 || args[0].equalsIgnoreCase("help");}
}
