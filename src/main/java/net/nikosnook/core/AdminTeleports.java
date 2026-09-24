package net.nikosnook.core;

import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import java.util.*;
import java.util.function.*;

/** A one-use exception for an explicitly authorised, synchronous moderation teleport. */
final class AdminTeleports implements Listener {
    private record Permit(Location destination) {}
    private record ReturnPoint(Location location,long expires){}
    static final long RETURN_LIFETIME=30*60_000L;
    private final Map<UUID,ReturnPoint> returns=new HashMap<>();
    private final LongSupplier clock;
    private final Predicate<Location> safeReturn;
    private final Map<UUID,Permit> permits=new HashMap<>();
    private final Function<String,Player> players;
    private final Consumer<String> audit;
    AdminTeleports(Function<String,Player> players,Consumer<String> audit){this(players,audit,System::currentTimeMillis,AdminTeleports::safeReturn);}
    AdminTeleports(Function<String,Player> players,Consumer<String> audit,LongSupplier clock,Predicate<Location> safeReturn){this.players=players;this.audit=audit;this.clock=clock;this.safeReturn=safeReturn;}
    static boolean safeReturn(Location location){
        var world=location.getWorld();double x=location.getX(),y=location.getY(),z=location.getZ();
        if(world==null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || y<world.getMinHeight() || y+1.8>world.getMaxHeight())return false;
        double epsilon=0.00001;
        var body=new org.bukkit.util.BoundingBox(x-.3+epsilon,y+epsilon,z-.3+epsilon,x+.3-epsilon,y+1.8-epsilon,z+.3-epsilon);
        for(double dx:new double[]{-.3,.3})for(double dz:new double[]{-.3,.3})if(!world.getWorldBorder().isInside(new Location(world,x+dx,y,z+dz)))return false;
        int minX=(int)Math.floor(body.getMinX()),maxX=(int)Math.floor(body.getMaxX()),minZ=(int)Math.floor(body.getMinZ()),maxZ=(int)Math.floor(body.getMaxZ());
        for(int bx=minX;bx<=maxX;bx++)for(int bz=minZ;bz<=maxZ;bz++)if(!world.isChunkGenerated(bx>>4,bz>>4))return false;
        boolean supported=false;
        for(int bx=minX;bx<=maxX;bx++)for(int bz=minZ;bz<=maxZ;bz++)for(int by=Math.max(world.getMinHeight(),(int)Math.floor(y)-1);by<=Math.min(world.getMaxHeight()-1,(int)Math.floor(y+1.8));by++){
            var block=world.getBlockAt(bx,by,bz);
            var volume=new org.bukkit.util.BoundingBox(bx,by,bz,bx+1,by+1,bz+1);
            boolean dangerous=Set.of(org.bukkit.Material.WATER,org.bukkit.Material.LAVA,org.bukkit.Material.POWDER_SNOW,org.bukkit.Material.FIRE,org.bukkit.Material.SOUL_FIRE,org.bukkit.Material.CACTUS,org.bukkit.Material.MAGMA_BLOCK,org.bukkit.Material.CAMPFIRE,org.bukkit.Material.SOUL_CAMPFIRE,org.bukkit.Material.SWEET_BERRY_BUSH,org.bukkit.Material.WITHER_ROSE).contains(block.getType());
            boolean wet=block.getBlockData() instanceof org.bukkit.block.data.Waterlogged water && water.isWaterlogged();
            if((dangerous || wet) && volume.overlaps(body))return false;
            // Paper's collision boxes are block-local; translate them to world coordinates.
            for(var local:block.getCollisionShape().getBoundingBoxes()){
                var shape=local.clone().shift(bx,by,bz);
                if(shape.overlaps(body))return false;
                if(Math.abs(shape.getMaxY()-y)<.0001 && shape.getMaxX()>body.getMinX() && shape.getMinX()<body.getMaxX() && shape.getMaxZ()>body.getMinZ() && shape.getMinZ()<body.getMaxZ()){
                    if(dangerous)return false;supported=true;
                }
            }
        }
        return supported;
    }
    static String destinationDescription(Location point){
        String world="unavailable world";
        try{if(point.getWorld()!=null && point.getWorld().getName()!=null)world=point.getWorld().getName();}catch(RuntimeException ignored){}
        return String.format(Locale.ROOT,"%s · X %.3f, Y %.3f, Z %.3f",world,point.getX(),point.getY(),point.getZ());
    }
    void forget(UUID player){returns.remove(player);}
    @EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent event){forget(event.getPlayer().getUniqueId());}
    @EventHandler public void death(org.bukkit.event.entity.PlayerDeathEvent event){forget(event.getEntity().getUniqueId());}
    private void returnPlayer(CommandSender sender,String[] args){
        if(args.length<1 || args.length>2){sender.sendMessage(NookUi.message("NookAdmin","Use /nookadmin return [player]. Console must name a player."));return;}
        Player player=args.length==2?players.apply(args[1]):sender instanceof Player self?self:null;
        if(player==null){sender.sendMessage(NookUi.problem("NookAdmin","Choose an online player. Console must use /nookadmin return <player>."));return;}
        ReturnPoint point=returns.get(player.getUniqueId());
        if(point==null || clock.getAsLong()>=point.expires()){
            forget(player.getUniqueId());sender.sendMessage(NookUi.message("NookAdmin","No current return point for "+player.getName()+". Points last 30 minutes and clear on death, logout or restart."));return;
        }
        try{
            Location destination=point.location().clone();
            if(!safeReturn.test(destination)){sender.sendMessage(NookUi.problem("NookAdmin","Cannot safely return to "+destinationDescription(destination)+". Check the landing, hazards and border. The return point is kept."));return;}
            if(move(sender,player,destination,NookUi.text("their previous position"),false))returns.remove(player.getUniqueId(),point);
        }catch(RuntimeException e){audit.accept("Return check by "+sender.getName()+" failed: "+e.getClass().getSimpleName());sender.sendMessage(NookUi.problem("NookAdmin","Could not check "+destinationDescription(point.location())+". No return was confirmed; the point is kept."));}
    }
    static boolean authorised(CommandSender sender){
        return sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender
            || sender instanceof Player && sender.hasPermission("nookcore.admin") && sender.hasPermission("nookcore.travel.bypass");
    }
    boolean consume(PlayerTeleportEvent event){
        Permit permit=permits.get(event.getPlayer().getUniqueId());
        if(permit==null || event.getCause()!=PlayerTeleportEvent.TeleportCause.PLUGIN || !permit.destination().equals(event.getTo()))return false;
        permits.remove(event.getPlayer().getUniqueId());return true;
    }
    void execute(CommandSender sender,String[] args){
        if(!authorised(sender)){sender.sendMessage(NookUi.problem("NookAdmin","Only console and authorised admins can use moderation teleports."));return;}
        if(args.length>0 && args[0].equalsIgnoreCase("return")){returnPlayer(sender,args);return;}
        if(args.length==4 || args.length==5){
            Player moved=args.length==5?players.apply(args[1]):sender instanceof Player self?self:null;
            if(moved==null){sender.sendMessage(NookUi.problem("NookAdmin","Specify an online player: /nookadmin teleport <player> <x> <y> <z>."));return;}
            Location location=moved.getLocation().clone();int start=args.length-3;
            try{
                location.setX(coordinate(args[start],location.getX()));
                location.setY(coordinate(args[start+1],location.getY()));
                location.setZ(coordinate(args[start+2],location.getZ()));
            }catch(IllegalArgumentException e){sender.sendMessage(NookUi.problem("NookAdmin","Use finite coordinates between -30,000,000 and 30,000,000, or ~ offsets from the player being moved."));return;}
            move(sender,moved,location,net.kyori.adventure.text.Component.text(location.getX()+", "+location.getY()+", "+location.getZ(),NookUi.ACCENT),true);return;
        }
        if(args.length==2 && sender instanceof Player self)args=new String[]{"teleport",self.getName(),args[1]};
        if(args.length!=3){sender.sendMessage(NookUi.message("NookAdmin","Use /nookadmin teleport <player> [destination-player]. With one name, an admin moves to that player. Console requires both names. Coordinates: /nookadmin teleport [player] <x> <y> <z>."));return;}
        Player moved=players.apply(args[1]),destination=players.apply(args[2]);
        if(moved==null || destination==null){sender.sendMessage(NookUi.problem("NookAdmin","Both players must be online. Use their exact names, without selectors."));return;}
        if(moved.getUniqueId().equals(destination.getUniqueId())){sender.sendMessage(NookUi.problem("NookAdmin","Choose two different players."));return;}
        Location location=destination.getLocation().clone();
        move(sender,moved,location,NookUi.name(destination.getUniqueId(),destination.getName()),true);
    }
    static double coordinate(String input,double origin){
        boolean relative=input.startsWith("~");String number=relative?input.substring(1):input;
        double result=(relative?origin:0)+(relative && number.isEmpty()?0:Double.parseDouble(number));
        if(!Double.isFinite(result) || Math.abs(result)>30_000_000)throw new IllegalArgumentException("Coordinate out of range");
        return result;
    }
    private boolean move(CommandSender sender,Player moved,Location location,net.kyori.adventure.text.Component destinationLabel,boolean remember){
        if(location.getWorld()==null){sender.sendMessage(NookUi.problem("NookAdmin","The destination world is unavailable."));return false;}
        UUID id=moved.getUniqueId();
        if(permits.containsKey(id)){sender.sendMessage(NookUi.problem("NookAdmin","A moderation teleport is already in progress for that player."));return false;}
        Location origin=moved.getLocation().clone();
        Permit permit=new Permit(location.clone());permits.put(id,permit);
        try{
            boolean success=moved.teleport(location,PlayerTeleportEvent.TeleportCause.PLUGIN);
            audit.accept("Moderation teleport by "+sender.getName()+": "+moved.getName()+" to "+net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(destinationLabel)+" ["+(success?"completed":"cancelled")+"]");
            if(success){
                if(remember){returns.entrySet().removeIf(e->clock.getAsLong()>=e.getValue().expires());returns.put(id,new ReturnPoint(origin,clock.getAsLong()+RETURN_LIFETIME));}
                sender.sendMessage(NookUi.message("NookAdmin","Teleported ").append(NookUi.name(id,moved.getName())).append(NookUi.text(" to ")).append(destinationLabel).append(NookUi.text(".")));
                if(!moved.getUniqueId().equals(sender instanceof Player actor?actor.getUniqueId():null))moved.sendMessage(NookUi.message("NookAdmin","An admin moved you to ").append(destinationLabel).append(NookUi.text(".")));
                return true;
            }else sender.sendMessage(NookUi.problem("NookAdmin","The teleport was cancelled. Check protection rules or other plugins."));
        }catch(RuntimeException e){
            audit.accept("Moderation teleport by "+sender.getName()+" for "+moved.getName()+" failed: "+e.getClass().getSimpleName());
            sender.sendMessage(NookUi.problem("NookAdmin","The teleport could not be confirmed. Check the player's position before retrying."));
        }finally{permits.remove(id,permit);}
        return false;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void consoleCommand(ServerCommandEvent event){
        routeConsole(event.getSender(),event.getCommand(),()->event.setCancelled(true));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void remoteCommand(RemoteServerCommandEvent event){consoleCommand(event);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void playerCommand(PlayerCommandPreprocessEvent event){
        if(!authorised(event.getPlayer()))return;
        String[] words=event.getMessage().trim().split("\\s+");
        if(!Set.of("/tp","/teleport","/minecraft:tp","/minecraft:teleport","/essentials:tp","/essentials:teleport").contains(words[0].toLowerCase(Locale.ROOT)))return;
        // Leave coordinates, selectors and other plugin grammars with their existing handlers.
        if(words.length<2 || words.length>3 || Arrays.stream(words,1,words.length).anyMatch(w->!w.matches("[A-Za-z0-9_.]{1,32}")))return;
        event.setCancelled(true);
        String[] args=Arrays.copyOf(words,words.length);args[0]="teleport";execute(event.getPlayer(),args);
    }
    void routeConsole(CommandSender sender,String command,Runnable cancel){
        if(!(sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender))return;
        String[] words=command.trim().split("\\s+");
        String root=words[0].toLowerCase(Locale.ROOT);if(root.startsWith("/"))root=root.substring(1);
        if(!Set.of("tp","teleport","minecraft:tp","minecraft:teleport").contains(root))return;
        if(words.length==3 && players.apply(words[1])!=null && players.apply(words[2])!=null){
            cancel.run();execute(sender,new String[]{"teleport",words[1],words[2]});
        }
    }
}
