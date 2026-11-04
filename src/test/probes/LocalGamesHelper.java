import com.mojang.authlib.GameProfile;
import java.io.BufferedReader;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Disposable, loopback-only manual opponent. Never include this in a release jar. */
public final class LocalGamesHelper extends JavaPlugin implements CommandExecutor {
    private static final String MARKER = "nikosnook-disposable-games-fixture";
    private static final String BOT_NAME = "NookTester";
    private static final UUID BOT_ID = UUID.nameUUIDFromBytes(
            "nikosnook:disposable-local-games:NookTester".getBytes(StandardCharsets.UTF_8));
    private static final String PREFIX = "[local test] ";
    private final Set<UUID> seen = new HashSet<>();
    private final Map<UUID, BukkitTask> replies = new HashMap<>();
    private final ArrayDeque<String> botMessages = new ArrayDeque<>();
    private JavaPlugin core;
    private Object store, coinflip, wagers;
    private Player bot;
    private BukkitTask poll;
    private String mode = "accept";
    private boolean active;

    private boolean guarded() {
        if (!"127.0.0.1".equals(getServer().getIp())) return false;
        Path marker = Path.of("LOCAL-TEST-ONLY.txt").toAbsolutePath();
        try (BufferedReader input = Files.newBufferedReader(marker, StandardCharsets.UTF_8)) {
            return MARKER.equals(input.readLine());
        } catch (Exception absent) {
            return false;
        }
    }

    private void disable(String reason, Throwable error) {
        active = false;
        if (error == null) getLogger().warning(reason);
        else getLogger().log(Level.SEVERE, reason, error);
        getServer().getPluginManager().disablePlugin(this);
    }

    private boolean mayRun() {
        if (!active) return false;
        if (!guarded()) {
            disable("Local games helper disabled: the loopback/fixture marker guard is missing.", null);
            return false;
        }
        if (Bukkit.getPlayer(BOT_ID) != null || Bukkit.getPlayerExact(BOT_NAME) != null) {
            disable("Local games helper disabled: a real player conflicts with the test opponent.", null);
            return false;
        }
        return true;
    }

    private Field findField(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(target.getClass().getName() + "." + name);
    }

    private Object field(Object target, String name) throws ReflectiveOperationException {
        return findField(target, name).get(target);
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == long.class) return Long.class;
        if (type == int.class) return Integer.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return Void.class;
    }

    private static boolean compatible(Class<?>[] parameters, Object[] args) {
        if (parameters.length != args.length) return false;
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null ? parameters[i].isPrimitive() : !boxed(parameters[i]).isInstance(args[i])) return false;
        }
        return true;
    }

    private Object call(Object target, String name, Object... args) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name) || !compatible(method.getParameterTypes(), args)) continue;
                method.setAccessible(true);
                try { return method.invoke(target, args); }
                catch (InvocationTargetException wrapped) {
                    if (wrapped.getCause() instanceof Exception error) throw error;
                    if (wrapped.getCause() instanceof Error error) throw error;
                    throw wrapped;
                }
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name + "/" + args.length);
    }

    private boolean healthy() {
        try { return (boolean) field(core, "healthy"); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private void gameFailure(Exception error) {
        disable("Local games helper disabled after a coin-flip adapter failure; inspect this fixture's log.", error);
    }

    private void milestone(Player player) {
        try { call(core, "gameMilestone", "COIN_FLIP_FINISHED", player); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    private Player byId(UUID id) {
        Player real = Bukkit.getPlayer(id);
        return real != null ? real : BOT_ID.equals(id) && mayRun() ? bot : null;
    }

    private Player byName(String name) {
        Player real = Bukkit.getPlayerExact(name);
        return real != null ? real : BOT_NAME.equalsIgnoreCase(name) && mayRun() ? bot : null;
    }

    private Collection<? extends Player> online() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (mayRun()) players.add(bot);
        return players;
    }

    private void capture(Object value) {
        if (value == null) return;
        if (value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) capture(Array.get(value, i));
            return;
        }
        String message = value instanceof Component component
                ? PlainTextComponentSerializer.plainText().serialize(component)
                : value instanceof String text ? text : null;
        if (message == null || message.isBlank()) return;
        if (botMessages.size() >= 40) botMessages.removeFirst();
        botMessages.addLast(message);
    }

    private Player createBot() {
        World world = Bukkit.getWorlds().getFirst();
        ServerPlayer handle = new ServerPlayer(((CraftServer) getServer()).getServer(),
                ((CraftWorld) world).getHandle(), new GameProfile(BOT_ID, BOT_NAME), ClientInformation.createDefault());
        // Construct a backing CraftPlayer for APIs such as advancement progress, without
        // adding a player to a world/player list or creating a connection.
        Player backing = handle.getBukkitEntity();
        return (Player) Proxy.newProxyInstance(getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getUniqueId" -> BOT_ID;
                case "getName" -> BOT_NAME;
                case "getGameMode" -> GameMode.SURVIVAL;
                case "isOnline", "isConnected" -> true;
                case "isDead", "isOp" -> false;
                case "hasPermission" -> args != null && args.length == 1 &&
                        ("nookcore.coinflip".equals(args[0]) || args[0] instanceof org.bukkit.permissions.Permission permission &&
                                "nookcore.coinflip".equals(permission.getName()));
                case "sendMessage", "sendPlainMessage", "sendRichMessage" -> {
                    if (args != null) for (Object value : args) capture(value);
                    yield null;
                }
                case "playSound", "stopSound", "sendActionBar", "showTitle", "sendTitle", "resetTitle" -> null;
                case "hashCode" -> BOT_ID.hashCode();
                case "equals" -> proxy == args[0];
                case "toString" -> "LocalGamesHelper[" + BOT_NAME + "]";
                default -> {
                    try { yield method.invoke(backing, args); }
                    catch (InvocationTargetException wrapped) { throw wrapped.getCause(); }
                }
            };
        });
    }

    @Override public void onEnable() {
        if (!guarded()) {
            disable("Local games helper requires server-ip=127.0.0.1 and the exact LOCAL-TEST-ONLY.txt fixture marker.", null);
            return;
        }
        try {
            if (!Bukkit.isPrimaryThread() || !Bukkit.getMinecraftVersion().equals("26.3"))
                throw new IllegalStateException("This helper requires the Paper 26.3 main thread.");
            core = (JavaPlugin) Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("NookCore"), "NookCore");
            if (!core.isEnabled() || !healthy()) throw new IllegalStateException("NookCore economy is not ready.");
            store = Objects.requireNonNull(field(core, "store"), "NookCore.store");
            Object original = Objects.requireNonNull(field(core, "coinFlipGame"), "NookCore.coinFlipGame");
            if (!(boolean) call(original, "enabled")) throw new IllegalStateException("Enable coin flips in this disposable fixture first.");
            if (Bukkit.getPlayer(BOT_ID) != null || Bukkit.getPlayerExact(BOT_NAME) != null)
                throw new IllegalStateException("The test opponent conflicts with a real player.");
            Optional<?> existing = (Optional<?>) call(store, "byName", BOT_NAME);
            if (existing.isPresent() && !BOT_ID.equals(call(existing.get(), "id")))
                throw new IllegalStateException("An existing real account uses the test opponent's name.");
            bot = createBot();
            Function<UUID, Player> idResolver = this::byId;
            Function<String, Player> nameResolver = this::byName;
            Supplier<Collection<? extends Player>> onlinePlayers = this::online;
            Object[] arguments = {core, store, (BooleanSupplier) this::healthy,
                    (Consumer<Exception>) this::gameFailure, idResolver, nameResolver, onlinePlayers};
            Constructor<?> constructor = null;
            for (Constructor<?> candidate : original.getClass().getDeclaredConstructors()) {
                if (candidate.getParameterCount() == 7 && compatible(candidate.getParameterTypes(), arguments)) {
                    constructor = candidate;
                    break;
                }
            }
            if (constructor == null) throw new NoSuchMethodException("CoinFlipGame seven-argument constructor");
            constructor.setAccessible(true);
            call(original, "close");
            coinflip = constructor.newInstance(arguments);
            findField(core, "coinFlipGame").set(core, coinflip);
            call(coinflip, "onPlayed", (Consumer<Player>) this::milestone);
            wagers = field(coinflip, "wagers");
            // NookStore's joining award key makes this a one-time 100-Nook fixture grant.
            call(store, "join", BOT_ID, BOT_NAME, System.currentTimeMillis(), 10000L);
            PluginCommand command = Objects.requireNonNull(getCommand("testgames"), "testgames command");
            command.setExecutor(this);
            command.setTabCompleter((sender, ignored, alias, args) -> List.of());
            active = true;
            poll = Bukkit.getScheduler().runTaskTimer(this, this::pollInvitations, 20L, 20L);
            getLogger().info("LOCAL_GAMES_READY opponent=" + BOT_NAME + " uuid=" + BOT_ID + " mode=" + mode +
                    "; disposable loopback fixture only; opponent is outside Bukkit's player list.");
        } catch (Exception error) {
            disable("Local games helper could not start; inspect this fixture's log.", error);
        }
    }

    private void local(CommandSender player, String message) { player.sendMessage(PREFIX + message); }

    private void feedback(Player player) {
        if (botMessages.isEmpty()) local(player, "NookTester could not complete that command; inspect this fixture's log.");
        else for (String message : List.copyOf(botMessages)) local(player, "NookTester: " + message);
    }

    private void botCommand(String... args) throws Exception {
        botMessages.clear();
        PluginCommand command = Objects.requireNonNull(core.getCommand("coinflip"));
        call(coinflip, "onCommand", bot, command, "coinflip", args);
    }

    private Optional<?> incoming(UUID matchId) throws Exception {
        for (Object match : (List<?>) call(wagers, "incoming", BOT_ID)) {
            if (matchId.equals(call(match, "id"))) return Optional.of(match);
        }
        return Optional.empty();
    }

    private void pollInvitations() {
        if (!mayRun()) return;
        try {
            if (!healthy()) return;
            for (Object match : (List<?>) call(wagers, "incoming", BOT_ID)) {
                UUID id = (UUID) call(match, "id");
                if (!seen.add(id)) continue;
                UUID creatorId = (UUID) call(match, "creator");
                Player creator = Bukkit.getPlayer(creatorId);
                String response = mode;
                if (creator != null) local(creator, response.equals("ignore")
                        ? "NookTester will ignore this invitation so you can check its timeout."
                        : "NookTester will " + response + " shortly (about 2 seconds).");
                if (response.equals("ignore")) continue;
                BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> respond(id, creatorId, response), 40L);
                replies.put(id, task);
            }
        } catch (Exception error) { disable("Local games helper stopped while reading invitations.", error); }
    }

    private void respond(UUID id, UUID creatorId, String response) {
        replies.remove(id);
        if (!mayRun()) return;
        Player creator = Bukkit.getPlayer(creatorId);
        try {
            if (!healthy() || incoming(id).isEmpty()) return;
            Object account = ((Optional<?>) call(store, "account", creatorId)).orElseThrow();
            botCommand(response, (String) call(account, "name"));
            if (incoming(id).isPresent() && creator != null) feedback(creator);
        } catch (Exception error) {
            if (creator != null) local(creator, "NookTester stopped after an error; inspect this fixture's log.");
            disable("Local games helper stopped while answering an invitation.", error);
        }
    }

    private void invite(Player operator, String amount) throws Exception {
        Optional<?> before = (Optional<?>) call(wagers, "outgoing", BOT_ID);
        UUID beforeId = before.isPresent() ? (UUID) call(before.get(), "id") : null;
        botCommand("invite", operator.getName(), amount);
        Optional<?> after = (Optional<?>) call(wagers, "outgoing", BOT_ID);
        if (after.isPresent() && !Objects.equals(beforeId, call(after.get(), "id")) &&
                operator.getUniqueId().equals(call(after.get(), "target"))) {
            local(operator, "NookTester invited you through the normal coin-flip command. Accept or decline its invitation normally.");
        } else feedback(operator);
    }

    private void answer(Player operator) throws Exception {
        Object wordle = Objects.requireNonNull(field(core, "wordleGame"), "Wordle module");
        if (!(boolean) call(wordle, "ready")) {
            local(operator, "Wordle is unavailable in this fixture.");
            return;
        }
        Object game = Objects.requireNonNull(field(wordle, "game"), "Wordle store");
        Object result = call(game, "open", operator.getUniqueId(), System.currentTimeMillis());
        String day = (String) call(call(result, "board"), "day");
        Connection db = (Connection) field(store, "db");
        try (var query = db.prepareStatement("SELECT answer FROM wordle_days WHERE day=?")) {
            query.setString(1, day);
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new IllegalStateException("Current Wordle puzzle is missing.");
                local(operator, "Disposable local fixture only: Wordle " + day + " answer is " +
                        row.getString(1).toUpperCase(Locale.ROOT) + ". Your saved guesses and rewards have not been reset.");
            }
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() || !sender.hasPermission("nookcore.localtest")) {
            local(sender, "Only an operator may use this disposable fixture helper.");
            return true;
        }
        if (!mayRun()) { local(sender, "This local fixture helper is disabled."); return true; }
        if (!(sender instanceof Player operator)) { local(sender, "Use this helper from your in-game operator account."); return true; }
        try {
            if (args.length == 2 && args[0].equalsIgnoreCase("mode") && Set.of("accept", "decline", "ignore").contains(args[1].toLowerCase(Locale.ROOT))) {
                mode = args[1].toLowerCase(Locale.ROOT);
                local(operator, "NookTester will " + mode + " new invitations. Replies already scheduled keep their announced mode.");
            } else if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
                Object account = ((Optional<?>) call(store, "account", BOT_ID)).orElseThrow();
                long cents = (long) call(account, "cents");
                local(operator, "NookTester balance: " + java.math.BigDecimal.valueOf(cents, 2).toPlainString() +
                        " Nooks; mode: " + mode + "; synthetic opponent is outside Bukkit's player list.");
            } else if (!healthy()) {
                local(operator, "NookCore economy is paused; inspect this fixture's log before continuing.");
            } else if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
                invite(operator, args[1]);
            } else if (args.length == 1 && args[0].equalsIgnoreCase("answer")) {
                answer(operator);
            } else {
                local(operator, "/testgames mode <accept|decline|ignore> | invite <amount> | answer | status");
                local(operator, "Invite NookTester with /coinflip invite NookTester <amount>. All stakes, consent, cooldowns and outcomes use the real game.");
            }
        } catch (Exception error) {
            local(operator, "Helper stopped after an error; inspect this fixture's log.");
            disable("Local games helper command failed.", error);
        }
        return true;
    }

    @Override public void onDisable() {
        active = false;
        if (poll != null) poll.cancel();
        for (BukkitTask task : replies.values()) task.cancel();
        replies.clear();
        botMessages.clear();
        // The injected adapter and NookStore belong to NookCore. Its normal
        // shutdown closes them; inactive resolvers already exclude the bot.
    }
}
