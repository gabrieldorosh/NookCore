import java.lang.reflect.*;
import java.sql.Connection;
import java.util.*;
import java.util.function.*;
import java.util.logging.Level;
import com.mojang.authlib.GameProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Runs only in a disposable Paper fixture. No real account or online player is used. */
public final class GamesChecks extends JavaPlugin implements Listener {
    private static final UUID ALICE=UUID.fromString("20261103-0000-4000-8000-000000000001");
    private static final UUID BOB=UUID.fromString("20261103-0000-4000-8000-000000000002");
    private final Map<String,Integer> announcements=new HashMap<>();
    private final List<Exception> moduleErrors=new ArrayList<>();
    private JavaPlugin core;
    private Object store,wordle,coinflip,wagers;
    private Connection db;
    private Fixture alice,bob,denied,spectator;
    private String day;
    private int checks;

    @EventHandler(priority=EventPriority.MONITOR)
    public void announced(PlayerAdvancementDoneEvent event){
        if(event.getAdvancement().getKey().getNamespace().equals("nikosnook") && event.message()!=null)
            announcements.merge(event.getPlayer().getUniqueId()+"|"+event.getAdvancement().getKey(),1,Integer::sum);
    }
    private Object field(Object target,String name)throws Exception{
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{
            var field=type.getDeclaredField(name);field.setAccessible(true);return field.get(target);
        }catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private void set(Object target,String name,Object value)throws Exception{
        var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    private Class<?> boxed(Class<?> type){
        if(!type.isPrimitive())return type;
        if(type==long.class)return Long.class;if(type==int.class)return Integer.class;
        if(type==boolean.class)return Boolean.class;if(type==double.class)return Double.class;
        if(type==float.class)return Float.class;if(type==short.class)return Short.class;
        if(type==byte.class)return Byte.class;if(type==char.class)return Character.class;
        return Void.class;
    }
    private Object invoke(Class<?> type,Object target,String name,Object...args)throws Exception{
        for(var method:type.getDeclaredMethods()){
            if(!method.getName().equals(name)||method.getParameterCount()!=args.length)continue;
            boolean compatible=true;Class<?>[] parameters=method.getParameterTypes();
            for(int i=0;i<args.length;i++)if(args[i]==null?parameters[i].isPrimitive():!boxed(parameters[i]).isInstance(args[i])){compatible=false;break;}
            if(!compatible)continue;
            method.setAccessible(true);
            try{return method.invoke(target,args);}
            catch(InvocationTargetException wrapped){
                if(wrapped.getCause() instanceof Exception error)throw error;
                if(wrapped.getCause() instanceof Error error)throw error;
                throw wrapped;
            }
        }
        throw new NoSuchMethodException(type.getName()+"."+name+"/"+args.length);
    }
    private Object call(Object target,String name,Object...args)throws Exception{return invoke(target.getClass(),target,name,args);}
    private Class<?> coreClass(String name)throws Exception{return Class.forName("net.nikosnook.core."+name,true,core.getClass().getClassLoader());}
    private void check(boolean passed,String description){
        if(!passed)throw new AssertionError(description);
        checks++;getLogger().info("GAMES_PASS "+description);
    }
    private long number(String sql,Object...args)throws Exception{
        try(var query=db.prepareStatement(sql)){
            for(int i=0;i<args.length;i++)query.setObject(i+1,args[i] instanceof UUID?args[i].toString():args[i]);
            try(var rows=query.executeQuery()){if(!rows.next())throw new AssertionError("No scalar row: "+sql);return rows.getLong(1);}
        }
    }
    private String text(String sql,Object...args)throws Exception{
        try(var query=db.prepareStatement(sql)){
            for(int i=0;i<args.length;i++)query.setObject(i+1,args[i] instanceof UUID?args[i].toString():args[i]);
            try(var rows=query.executeQuery()){if(!rows.next())throw new AssertionError("No text row: "+sql);return rows.getString(1);}
        }
    }
    private void update(String sql,Object...args)throws Exception{
        try(var query=db.prepareStatement(sql)){
            for(int i=0;i<args.length;i++)query.setObject(i+1,args[i] instanceof UUID?args[i].toString():args[i]);
            query.executeUpdate();
        }
    }
    private long cash(UUID id)throws Exception{return (long)call(((Optional<?>)call(store,"account",id)).orElseThrow(),"cents");}
    private long supply()throws Exception{return cash(ALICE)+cash(BOB)+(long)call(store,"gameHeldFunds");}
    private long attempts(UUID id)throws Exception{return number("SELECT COUNT(*) FROM wordle_guesses WHERE uuid=? AND day=?",id,day);}
    private int announced(UUID id,String path){return announcements.getOrDefault(id+"|nikosnook:"+path,0);}
    private void command(Fixture player,String name,String...args)throws Exception{
        PluginCommand command=Objects.requireNonNull(core.getCommand(name));
        call(command.getExecutor(),"onCommand",player.proxy,command,name,args);
    }
    @SuppressWarnings("unchecked")
    private List<String> tab(Fixture player,String name,String...args)throws Exception{
        PluginCommand command=Objects.requireNonNull(core.getCommand(name));
        return (List<String>)call(Objects.requireNonNull(command.getTabCompleter()),"onTabComplete",player.proxy,command,name,args);
    }
    private boolean milestone(Fixture player,String path){
        var advancement=Bukkit.getAdvancement(new NamespacedKey("nikosnook",path));
        return advancement!=null && player.proxy.getAdvancementProgress(advancement).isDone();
    }
    private boolean healthy(){try{return (boolean)field(core,"healthy");}catch(Exception error){throw new IllegalStateException(error);}}
    private void gameFailure(Exception error){
        moduleErrors.add(error);
        try{call(core,"gameFailure","Coin-flip probe",error);}catch(Exception failure){throw new IllegalStateException(failure);}
    }
    private void coinMilestone(Player player){try{call(core,"gameMilestone","COIN_FLIP_FINISHED",player);}catch(Exception error){throw new IllegalStateException(error);}}

    private final class Fixture {
        final UUID id;final String name;final GameMode mode;final boolean allowed;
        final Player proxy;
        final List<Component> components=new ArrayList<>();
        final List<String> messages=new ArrayList<>();
        Fixture(UUID id,String name,GameMode mode,boolean allowed){
            this.id=id;this.name=name;this.mode=mode;this.allowed=allowed;
            World world=Bukkit.getWorlds().getFirst();
            var handle=new ServerPlayer(((CraftServer)getServer()).getServer(),((CraftWorld)world).getHandle(),new GameProfile(id,name),ClientInformation.createDefault());
            Player real=handle.getBukkitEntity();
            proxy=(Player)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{Player.class},(object,method,args)->switch(method.getName()){
                case "getUniqueId"->id;case "getName"->name;case "getWorld"->world;
                case "getLocation","getEyeLocation"->new Location(world,0.5,121,0.5);
                case "isOnline","isConnected"->true;case "isDead"->false;case "getGameMode"->mode;
                case "hasPermission"->allowed;case "isOp"->false;
                case "sendMessage","sendPlainMessage","sendRichMessage"->{if(args!=null)for(var value:args)capture(value);yield null;}
                case "playSound","stopSound","sendActionBar","showTitle","sendTitle","resetTitle"->null;
                case "hashCode"->id.hashCode();case "equals"->object==args[0];case "toString"->"GamesChecks["+name+"]";
                default->{try{yield method.invoke(real,args);}catch(InvocationTargetException error){throw error.getCause();}}
            });
        }
        void capture(Object value){
            if(value==null)return;
            if(value instanceof Component component){components.add(component);messages.add(PlainTextComponentSerializer.plainText().serialize(component));}
            else if(value.getClass().isArray()){for(int i=0;i<Array.getLength(value);i++)capture(Array.get(value,i));}
            else if(value instanceof String message)messages.add(message);
        }
        void clear(){messages.clear();components.clear();}
        String output(){return String.join("\n",messages);}
        int results(){return (int)messages.stream().filter(message->message.contains(" won ")&&message.contains("! <3")).count();}
        boolean click(ClickEvent.Action action,String value){return components.stream().anyMatch(component->hasClick(component,action,value));}
    }
    private boolean hasClick(Component component,ClickEvent.Action action,String value){
        var click=component.clickEvent();
        if(click!=null && click.action()==action && click.payload() instanceof ClickEvent.Payload.Text payload && payload.value().equals(value))return true;
        return component.children().stream().anyMatch(child->hasClick(child,action,value));
    }
    private void setup()throws Exception{
        check(Bukkit.isPrimaryThread(),"probe runs on the Paper main thread");
        check(Bukkit.getMinecraftVersion().equals("26.3"),"real Minecraft 26.3 server is running");
        core=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NookCore");
        check(core!=null&&core.isEnabled(),"candidate NookCore enabled");
        store=field(core,"store");check(store!=null&&healthy(),"candidate economy is healthy");
        db=(Connection)field(store,"db");
        check(number("SELECT COUNT(*) FROM accounts")==0,"fresh database contains no real or previous accounts");
        getServer().getPluginManager().registerEvents(this,this);
        alice=new Fixture(ALICE,"GameAlice",GameMode.SURVIVAL,true);
        bob=new Fixture(BOB,"GameBob",GameMode.SURVIVAL,true);
        denied=new Fixture(UUID.fromString("20261103-0000-4000-8000-000000000003"),"GameDenied",GameMode.SURVIVAL,false);
        spectator=new Fixture(UUID.fromString("20261103-0000-4000-8000-000000000004"),"GameWatcher",GameMode.SPECTATOR,true);
        call(store,"join",ALICE,alice.name,System.currentTimeMillis(),5000L);
        call(store,"join",BOB,bob.name,System.currentTimeMillis(),5000L);
        check(cash(ALICE)==5000&&cash(BOB)==5000,"two fixture accounts begin with exactly 5000 cents each");
        check(Bukkit.getPlayer(ALICE)==null&&Bukkit.getPlayer(BOB)==null,"fixture players never enter the real Bukkit player list");
        wordle=field(core,"wordleGame");check(wordle!=null&&(boolean)call(wordle,"ready"),"Wordle module is ready");
        check(call(wordle,"status").toString().contains("02:00 UK"),"Wordle status names the UK reset time");
        check(field(core,"gameMilestones")!=null,"enabled games initialize the cosmetic milestone module");
        for(String path:List.of("games/five_little_letters","games/heads_or_tails"))
            check(Bukkit.getAdvancement(new NamespacedKey("nikosnook",path))!=null,"registered game advancement "+path);
        coinflip=field(core,"coinFlipGame");check(coinflip!=null,"core initializes the coin-flip module");
        call(coinflip,"close");
        List<Player> players=List.of(alice.proxy,bob.proxy,denied.proxy,spectator.proxy);
        Function<UUID,Player> byId=id->players.stream().filter(player->player.getUniqueId().equals(id)).findFirst().orElse(null);
        Function<String,Player> byName=name->players.stream().filter(player->player.getName().equals(name)).findFirst().orElse(null);
        Supplier<Collection<? extends Player>> online=()->players;
        var constructor=Arrays.stream(coreClass("CoinFlipGame").getDeclaredConstructors()).filter(item->item.getParameterCount()==7).findFirst().orElseThrow();
        constructor.setAccessible(true);
        coinflip=constructor.newInstance(core,store,(BooleanSupplier)this::healthy,(Consumer<Exception>)this::gameFailure,byId,byName,online);
        set(core,"coinFlipGame",coinflip);call(coinflip,"onPlayed",(Consumer<Player>)this::coinMilestone);
        wagers=field(coinflip,"wagers");check((boolean)call(coinflip,"enabled"),"injected coin-flip UI uses enabled trial settings");
        Object settings=field(coinflip,"settings");
        check((long)call(settings,"minimum")==100&&(long)call(settings,"maximum")==10000,"coin-flip stake bounds are the trial values");
        check((long)call(settings,"invitationMillis")==90000&&(long)call(settings,"cooldownMillis")==5000,"coin-flip expiry and cooldown are the trial values");
        Object today=invoke(coreClass("WordleDay"),null,"at",System.currentTimeMillis());day=(String)call(today,"id");
        update("INSERT INTO wordle_days(day,answer,reward,created) VALUES(?,?,?,?)",day,"stone",100L,System.currentTimeMillis());
        check(text("SELECT answer FROM wordle_days WHERE day=?",day).equals("stone"),"only today's fixture puzzle is seeded as STONE");
        check(supply()==10000,"initial player money plus escrow is exactly conserved");
    }
    private void wordleChecks()throws Exception{
        long rounds=number("SELECT COUNT(*) FROM wordle_rounds");
        command(denied,"wordle");command(spectator,"wordle");
        check(number("SELECT COUNT(*) FROM wordle_rounds")==rounds,"Wordle denies missing permission and spectator mode before saving progress");
        check(denied.output().contains("isn't available")&&spectator.output().contains("Survival"),"Wordle eligibility failures give private explanations");
        alice.clear();command(alice,"wordle");
        check(attempts(ALICE)==0,"opening Wordle consumes zero guesses");
        check(alice.output().contains("6 guesses left")&&alice.output().contains("= right place"),"Wordle displays remaining attempts and a plain-text feedback key");
        check(alice.click(ClickEvent.Action.SUGGEST_COMMAND,"/wordle guess "+day+" "),"Wordle guess action carries the displayed puzzle date");
        command(alice,"wordle","guess");check(attempts(ALICE)==0,"bare /wordle guess consumes no attempt");
        command(alice,"wordle","zzzzz");check(attempts(ALICE)==0,"unknown dictionary word consumes no attempt");
        alice.clear();command(alice,"wordle","SHEEP");
        check(attempts(ALICE)==1,"a valid uppercase word consumes one guess");
        check(text("SELECT marks FROM wordle_guesses WHERE uuid=? AND day=? AND ordinal=1",ALICE,day).equals("20100"),"SHEEP uses two-pass duplicate-E feedback against STONE");
        check(alice.output().contains("[S=] [H-] [E~] [E-] [P-]"),"Wordle command renders the saved duplicate-letter feedback");
        command(alice,"wordle","sheep");check(attempts(ALICE)==1,"repeating an accepted word consumes no attempt");
        alice.clear();command(alice,"wordle","guess",day,"STONE");
        check(attempts(ALICE)==2&&text("SELECT state FROM wordle_rounds WHERE uuid=? AND day=?",ALICE,day).equals("WON"),"dated Wordle guess saves the winning second attempt");
        check(cash(ALICE)==5100&&cash(BOB)==5000,"Wordle solve awards exactly 100 cents once");
        check(number("SELECT reward_paid FROM wordle_rounds WHERE uuid=? AND day=?",ALICE,day)==1,"Wordle reward is durably marked paid");
        check(number("SELECT COUNT(*) FROM ledger WHERE uuid=? AND kind='game-wordle'",ALICE)==1,"Wordle produces exactly one reward ledger entry");
        check(milestone(alice,"games/five_little_letters"),"first Wordle solve grants its cosmetic advancement");
        check(announced(ALICE,"games/five_little_letters")==1,"Wordle advancement emits one ordinary chat event");
        command(alice,"wordle");command(alice,"wordle","stone");alice.clear();command(alice,"wordle","share");
        String share=alice.messages.stream().filter(message->message.startsWith("niko's nook wordle")).findFirst().orElseThrow();
        check(share.contains("2/6")&&share.contains("[=][-][~][-][-]")&&share.contains("[=][=][=][=][=]"),"share uses the saved result and accessible feedback markers");
        check(!share.toLowerCase(Locale.ROOT).contains("stone")&&!share.toLowerCase(Locale.ROOT).contains("sheep"),"shared result reveals no guess or solution letters");
        check(alice.click(ClickEvent.Action.COPY_TO_CLIPBOARD,share),"share provides a copy-result action");
        check(cash(ALICE)==5100&&attempts(ALICE)==2&&announced(ALICE,"games/five_little_letters")==1,"reopen repeat solve and share cannot repeat money or advancement");
        check(new HashSet<>(tab(alice,"wordle","")).equals(Set.of("help","share","guess")),"Wordle tab completion lists commands without a dictionary or solution");
        check(tab(alice,"wordle","guess","").isEmpty()&&tab(alice,"wordle","st").isEmpty(),"Wordle later arguments and solution prefixes expose no words");
        bob.clear();command(bob,"wordle");
        for(String guess:List.of("crane","grape","brick","wheat","chest","melon"))command(bob,"wordle",guess);
        check(attempts(BOB)==6&&text("SELECT state FROM wordle_rounds WHERE uuid=? AND day=?",BOB,day).equals("LOST"),"six valid wrong guesses finish Bob's puzzle as lost");
        check(bob.output().contains("today's word was STONE")&&cash(BOB)==5000,"losing reveals the answer privately and pays nothing");
        command(bob,"wordle","stone");command(bob,"wordle","share");
        check(attempts(BOB)==6&&cash(BOB)==5000&&!milestone(bob,"games/five_little_letters"),"a lost board cannot be solved again or grant the solve advancement");
        check(supply()==10100,"daily Wordle award is the only new money after both puzzles");
    }
    private void coinflipChecks()throws Exception{
        check(tab(alice,"coinflip","invite","").equals(List.of("GameBob")),"coin-flip targets list only another eligible Survival player");
        check(tab(denied,"coinflip","").isEmpty()&&tab(spectator,"coinflip","").isEmpty(),"coin-flip completion denies missing permission and spectator mode");
        long matches=number("SELECT COUNT(*) FROM game_wagers");
        denied.clear();spectator.clear();command(denied,"coinflip","invite","GameAlice","2");command(spectator,"coinflip","invite","GameAlice","2");
        command(alice,"coinflip","invite","GameDenied","2");command(alice,"coinflip","invite","GameWatcher","2");
        check(number("SELECT COUNT(*) FROM game_wagers")==matches&&supply()==10100,"permission spectator and target eligibility checks create no wager or money change");
        check(denied.output().contains("permission")&&spectator.output().contains("Survival"),"coin-flip denied players receive a private explanation");
        alice.clear();bob.clear();denied.clear();spectator.clear();
        command(alice,"coinflip","invite","GameBob","2");
        Object invitation=((Optional<?>)call(wagers,"outgoing",ALICE)).orElseThrow();UUID match=(UUID)call(invitation,"id");
        check(cash(ALICE)==4900&&cash(BOB)==5000&&(long)call(store,"gameHeldFunds")==200,"invitation reserves Alice's exact two-Nook stake");
        check(supply()==10100&&number("SELECT stake FROM game_wagers WHERE id=?",match)==200,"invitation preserves all money and saves the agreed stake");
        check(alice.output().contains("you're heads")&&bob.output().contains("you're tails"),"private invitation fixes the inviter and guest sides");
        check(bob.output().contains("50/50")&&bob.output().contains("full pot")&&bob.output().contains("no fee")&&bob.output().contains("pot."),"guest sees odds full-pot terms and the pot amount");
        check(bob.click(ClickEvent.Action.RUN_COMMAND,"/coinflip accept GameAlice")&&bob.click(ClickEvent.Action.RUN_COMMAND,"/coinflip decline GameAlice"),"invitation provides private accept and decline actions");
        check(denied.messages.isEmpty()&&spectator.messages.isEmpty(),"invitation is not broadcast to other fixture players");
        check(tab(bob,"coinflip","accept","").equals(List.of("GameAlice")),"accept completion lists the saved eligible inviter");
        command(bob,"coinflip","accept","GameAlice");
        Object settled=call(wagers,"inspect",match,ALICE);UUID winner=(UUID)call(settled,"winner");String side=call(settled,"side").toString();
        check(call(settled,"state").toString().equals("SETTLED")&&(side.equals("HEADS")||side.equals("TAILS")),"accept saves a complete coin-flip outcome");
        check(winner.equals(side.equals("HEADS")?ALICE:BOB),"saved winner matches the preannounced side assignment");
        check(cash(ALICE)==(winner.equals(ALICE)?5300:4900)&&cash(BOB)==(winner.equals(BOB)?5200:4800),"winner receives the full four-Nook pot with no fee");
        check((long)call(store,"gameHeldFunds")==0&&supply()==10100,"settlement leaves no held funds and conserves all player money");
        check(number("SELECT COUNT(*) FROM game_credits WHERE game_id=? AND kind='game-payout' AND cents=400 AND state='CLAIMED'","coinflip:"+match)==1,"full-pot payout is durably saved and collected once");
        check(alice.results()==1&&bob.results()==1,"each participant receives exactly one private result");
        check(number("SELECT creator_notified+target_notified FROM game_wagers WHERE id=?",match)==2,"both result notices are durably acknowledged");
        check(milestone(alice,"games/heads_or_tails")&&milestone(bob,"games/heads_or_tails"),"both participants receive the cosmetic coin-flip advancement");
        check(announced(ALICE,"games/heads_or_tails")==1&&announced(BOB,"games/heads_or_tails")==1,"coin-flip advancements each emit one ordinary chat event");
        command(bob,"coinflip","accept","GameAlice");command(alice,"coinflip","cancel");command(alice,"coinflip");command(bob,"coinflip");
        check(supply()==10100&&(long)call(store,"gameHeldFunds")==0,"repeated accept cancel and summary cannot repeat payouts");
        check(alice.results()==1&&bob.results()==1&&announced(ALICE,"games/heads_or_tails")==1&&announced(BOB,"games/heads_or_tails")==1,"repeated commands cannot repeat result notices or advancements");
        alice.clear();command(alice,"coinflip","history");
        check(alice.output().contains("GameBob")&&(alice.output().contains("won")||alice.output().contains("lost")),"history describes the saved opponent and result");
        check(((List<?>)call(wagers,"history",ALICE)).size()==1,"history contains the one saved settled match");
        command(alice,"coinflip","invite","GameBob","2");
        check(((Optional<?>)call(wagers,"outgoing",ALICE)).isEmpty()&&number("SELECT COUNT(*) FROM game_wagers")==1,"five-second creator cooldown rejects an immediate new invitation");
        long aliceBefore=cash(ALICE),bobBefore=cash(BOB);
        command(bob,"coinflip","invite","GameAlice","2");
        Object reverse=((Optional<?>)call(wagers,"outgoing",BOB)).orElseThrow();UUID reverseId=(UUID)call(reverse,"id");
        check(cash(BOB)==bobBefore-200&&cash(ALICE)==aliceBefore&&(long)call(store,"gameHeldFunds")==200,"fresh creator Bob can invite Alice with his own held stake");
        // A deliberately overdue fixture isolates the read-only command behavior. The whole
        // probe is one main-thread task, so the normal expiry task cannot interleave here.
        update("UPDATE game_wagers SET expires=? WHERE id=?",System.currentTimeMillis()-1,reverseId);
        long ledgerBefore=number("SELECT COUNT(*) FROM ledger"),cashBefore=cash(ALICE)+cash(BOB);
        command(alice,"coinflip","help");command(bob,"coinflip","history");
        check(text("SELECT state FROM game_wagers WHERE id=?",reverseId).equals("INVITED"),"read-only help and history do not expire an overdue fixture invitation");
        check(number("SELECT COUNT(*) FROM ledger")==ledgerBefore&&cash(ALICE)+cash(BOB)==cashBefore&&(long)call(store,"gameHeldFunds")==200,"read-only help and history do not claim or move money");
        update("UPDATE game_wagers SET expires=? WHERE id=?",System.currentTimeMillis()+90000,reverseId);
        command(alice,"coinflip","decline","GameBob");
        check(text("SELECT state FROM game_wagers WHERE id=?",reverseId).equals("DECLINED"),"Alice can decline Bob's waiting invitation");
        check(cash(ALICE)==aliceBefore&&cash(BOB)==bobBefore&&(long)call(store,"gameHeldFunds")==0&&supply()==10100,"decline refunds the exact held stake without changing supply");
        command(alice,"coinflip","decline","GameBob");command(bob,"coinflip","cancel");
        check(cash(ALICE)==aliceBefore&&cash(BOB)==bobBefore&&supply()==10100,"repeated decline and cancel cannot repeat a refund");
    }
    private void discoveryChecks()throws Exception{
        alice.clear();command(alice,"games");
        check(alice.output().contains("/wordle")&&alice.output().contains("/coinflip"),"games discovery offers both ready games");
        check(alice.click(ClickEvent.Action.SUGGEST_COMMAND,"/wordle")&&alice.click(ClickEvent.Action.SUGGEST_COMMAND,"/coinflip"),"games discovery has usable private game actions");
        check(new HashSet<>(tab(alice,"games","")).equals(Set.of("help","collect")),"games completion offers help and collect");
        long ledgerBefore=number("SELECT COUNT(*) FROM ledger");
        command(alice,"games","collect");command(bob,"games","collect");command(alice,"coinflip","collect");
        check(supply()==10100&&number("SELECT COUNT(*) FROM ledger")==ledgerBefore,"empty game collection cannot mint or repeat money");
        check(alice.output().contains("nothing waiting to collect"),"empty collection explains there is nothing waiting");
        denied.clear();command(denied,"games");command(denied,"games","collect");
        check(denied.output().contains("isn't available")&&tab(denied,"games","").isEmpty(),"games UI guards direct calls and completion by permission");
        check(number("SELECT COUNT(*) FROM accounts")==2,"all integration activity stays within the two fixture accounts");
        check(number("SELECT COUNT(*) FROM ledger WHERE kind='game-wordle'")==1&&number("SELECT COUNT(*) FROM ledger WHERE kind='game-payout'")==1,"only the one solve and one settled pot enter the game reward ledger");
        check(moduleErrors.isEmpty()&&healthy(),"game modules finish without storage errors or health degradation");
        check(Bukkit.getPlayer(ALICE)==null&&Bukkit.getPlayer(BOB)==null,"fixture players remain outside the live player list after all commands");
    }
    private void run(){
        try{
            setup();wordleChecks();coinflipChecks();discoveryChecks();
            int products=PipProductsChecks.run(core);check(products==63,"actual Paper banner journal and head factory checks pass");
            World world=Bukkit.getWorlds().getFirst();world.getChunkAt(0,0).load();
            for(int x=6;x<=12;x++)for(int z=6;z<=12;z++){
                world.getBlockAt(x,120,z).setType(Material.STONE_BRICKS,false);
                for(int y=121;y<=123;y++)world.getBlockAt(x,y,z).setType(Material.AIR,false);
            }
            int roaming=NikoRoamingChecks.run(core,new Location(world,8.5,121,8.5));check(roaming==13,"actual Paper FancyNpcs flat-loop movement checks pass");
            getLogger().info("GAMES_COMPLETE checks="+checks+" day="+day+" final-supply-cents="+supply());
        }catch(Throwable error){getLogger().log(Level.SEVERE,"GAMES_FAILED checks="+checks,error);}
        finally{Bukkit.shutdown();}
    }
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,this::run,180L);}
}

