package net.nikosnook.core;
import org.bukkit.command.*;
import java.util.*;
/** Staff setup routes remain reachable when rentals are unavailable. */
final class PlotRoutes implements CommandExecutor,TabCompleter {
 private final PlotCommands plots;private final PlotSetup setup;private final CommandExecutor admin;private final TabCompleter completion;
 PlotRoutes(PlotCommands plots,PlotSetup setup,CommandExecutor admin,TabCompleter completion){this.plots=plots;this.setup=setup;this.admin=admin;this.completion=completion;}
 private boolean staff(String[] args){return args.length>0 && (args[0].equalsIgnoreCase("admin") || args[0].equalsIgnoreCase("setup"));}
 public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
  if(!staff(args))return plots.onCommand(sender,command,label,args);
  if(!sender.hasPermission("nookcore.admin")){sender.sendMessage(NookUi.problem("NookPlots","This command is for admins."));return true;}
  var rest=Arrays.copyOfRange(args,1,args.length);
  if(args[0].equalsIgnoreCase("setup")){
   if(setup==null){sender.sendMessage(NookUi.problem("NookPlots","Plot setup requires WorldEdit and WorldGuard."));return true;}
   return setup.onCommand(sender,command,label,rest);
  }
  if(rest.length==0 || rest[0].equalsIgnoreCase("help")){
   NookUi.help(sender,"NookPlots · Administration","/plots admin clear <address> <storage-note> — record clearance","/plots admin evict <address> <reason|confirm> — preview/confirm eviction","/plots admin storage <address> — read storage notes","/plots admin membership <player> — inspect membership","/plots admin absence <address> <days|off> <reason> — allow a break","/plots admin define <id> <rent> — create a database record","/plots setup help — create district and plot regions");return true;
  }
  try{return admin.onCommand(sender,command,label,FeatureRoutes.plot(rest));}
  catch(IllegalArgumentException e){sender.sendMessage(NookUi.problem("NookPlots",e.getMessage()));return true;}
 }
 public List<String> onTabComplete(CommandSender sender,Command command,String label,String[] args){
  if(staff(args)){
   if(!sender.hasPermission("nookcore.admin"))return List.of();
   var rest=Arrays.copyOfRange(args,1,args.length);
   if(args[0].equalsIgnoreCase("setup"))return setup==null?List.of():setup.onTabComplete(sender,command,label,rest);
   if(rest.length<=1)return NookUi.complete(rest.length==0?"":rest[0],FeatureRoutes.PLOT_ADMIN);
   try{return completion.onTabComplete(sender,command,label,FeatureRoutes.plot(rest));}catch(IllegalArgumentException e){return List.of();}
  }
  var result=new ArrayList<>(plots.onTabComplete(sender,command,label,args));
  if(args.length==1 && sender.hasPermission("nookcore.admin"))result.addAll(NookUi.complete(args[0],List.of("admin","setup")));return result;
 }
}
