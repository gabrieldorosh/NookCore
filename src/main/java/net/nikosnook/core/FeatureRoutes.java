package net.nikosnook.core;
import java.util.*;
final class FeatureRoutes {
 static final List<String> PLOT_ADMIN=List.of("clear","evict","storage","membership","absence","define");
 static final List<String> MONEY_ADMIN=List.of("give","take","backup","advancements");
 static boolean moneyAdmin(String[] args){return args.length>0 && (MONEY_ADMIN.contains(args[0].toLowerCase(Locale.ROOT)) || args.length>1 && args[0].equalsIgnoreCase("balance"));}
 static String[] money(String[] args){var result=args.clone();if(result[0].equalsIgnoreCase("advancements"))result[0]="awards";return result;}
 static String[] plot(String[] args){
  if(args.length==0 || !PLOT_ADMIN.contains(args[0].toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Use /plots admin to see the staff plot commands.");
  var result=args.clone();result[0]="plot"+args[0].toLowerCase(Locale.ROOT);return result;
 }
}
