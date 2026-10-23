// SmpPlugin by LowBacon
package com.bx.smpPlugin.engagement;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Reward;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.managers.FeatureManager;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChatGames implements CommandExecutor, TabCompleter, Listener {

    public static final String HOST_PERMISSION = "smpplugin.staff.chatgame";

    public enum Type {
        UNSCRAMBLE, MATH, TYPE, TRIVIA
    }

    record Game(Type type, String question, List<String> answers, long startedAt, AtomicBoolean won) {
    }

    private final SmpPlugin plugin;
    private volatile Game active;
    private BukkitTask timeout;
    private long lastAutoStart = System.currentTimeMillis();

    public ChatGames(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration config() {
        FileConfiguration extras = plugin.getConfigManager().getExtras();
        return extras == null ? new YamlConfiguration() : extras;
    }

    public void autoTick() {
        if (active != null || !config().getBoolean("CHAT-GAMES.AUTO", true)
                || !plugin.getFeatureManager().isEnabled(FeatureManager.Feature.EVENTS)) {
            return;
        }
        long interval = Math.max(1L, config().getLong("CHAT-GAMES.INTERVAL-MINUTES", 12L)) * 60_000L;
        if (System.currentTimeMillis() - lastAutoStart < interval
                || Bukkit.getOnlinePlayers().size() < config().getInt("CHAT-GAMES.MIN-PLAYERS", 3)) {
            return;
        }
        start(null);
    }

    public boolean start(Type requested) {
        if (active != null) {
            return false;
        }
        Type type = requested;
        if (type == null) {
            List<Type> enabled = new ArrayList<>();
            for (String raw : config().getStringList("CHAT-GAMES.TYPES")) {
                try {
                    enabled.add(Type.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    // Unknown type names are skipped.
                }
            }
            if (enabled.isEmpty()) {
                enabled.addAll(List.of(Type.values()));
            }
            type = enabled.get(ThreadLocalRandom.current().nextInt(enabled.size()));
        }
        Game game = create(type);
        if (game == null) {
            return false;
        }
        lastAutoStart = System.currentTimeMillis();
        active = game;
        String header = Text.get("CHATGAME.HEADER." + type.name(), switch (type) {
            case UNSCRAMBLE -> "&b&l✎ UNSCRAMBLE &8» &7Unscramble this word:";
            case MATH -> "&e&l➗ QUICK MATH &8» &7Solve:";
            case TYPE -> "&a&l⌨ FAST TYPER &8» &7Be the first to type:";
            case TRIVIA -> "&d&l? TRIVIA &8» &7Answer this:";
        });
        String prize = String.join("&7, ", reward().describe(plugin));
        for (Player player : Bukkit.getOnlinePlayers()) {
            Text.raw(player, "");
            Text.raw(player, header);
            Text.raw(player, "   &f&l" + game.question());
            Text.raw(player, Text.get("CHATGAME.PRIZE", "&7First right answer in chat wins {prize}&7!", "{prize}", prize));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.6F, 1.6F);
        }
        Text.raw(Bukkit.getConsoleSender(), header + " &f" + game.question() + " &8(answer: " + game.answers().get(0) + ")");
        long limit = Math.max(10L, config().getLong("CHAT-GAMES.TIME-LIMIT", 60L));
        timeout = plugin.getSpigotScheduler().runGlobalLater(() -> expire(game), limit * 20L);
        return true;
    }

    private Game create(Type type) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return switch (type) {
            case UNSCRAMBLE -> {
                List<String> words = config().getStringList("CHAT-GAMES.WORDS");
                if (words.isEmpty()) {
                    words = List.of("diamond", "creeper", "pumpkin");
                }
                String word = words.get(random.nextInt(words.size())).trim().toLowerCase(Locale.ROOT);
                yield new Game(type, scramble(word), List.of(word), System.currentTimeMillis(), new AtomicBoolean());
            }
            case MATH -> {
                int a = random.nextInt(2, 20);
                int b = random.nextInt(2, 13);
                int c = random.nextInt(1, 50);
                boolean multiplyFirst = random.nextBoolean();
                String question = multiplyFirst ? a + " × " + b + " + " + c : c + " + " + a + " × " + b;
                yield new Game(type, question, List.of(Integer.toString(a * b + c)), System.currentTimeMillis(), new AtomicBoolean());
            }
            case TYPE -> {
                String alphabet = "abcdefghjkmnpqrstuvwxyz23456789";
                StringBuilder code = new StringBuilder();
                for (int i = 0; i < 10; i++) {
                    code.append(alphabet.charAt(random.nextInt(alphabet.length())));
                }
                yield new Game(type, code.toString(), List.of(code.toString()), System.currentTimeMillis(), new AtomicBoolean());
            }
            case TRIVIA -> {
                List<Map<?, ?>> entries = config().getMapList("CHAT-GAMES.TRIVIA");
                if (entries.isEmpty()) {
                    yield null;
                }
                Map<?, ?> entry = entries.get(random.nextInt(entries.size()));
                Object question = entry.get("QUESTION");
                Object answers = entry.get("ANSWERS");
                List<String> accepted = new ArrayList<>();
                if (answers instanceof List<?> list) {
                    list.forEach(answer -> accepted.add(normalize(String.valueOf(answer))));
                }
                yield question == null || accepted.isEmpty() ? null
                        : new Game(type, String.valueOf(question), accepted, System.currentTimeMillis(), new AtomicBoolean());
            }
        };
    }

    static String scramble(String word) {
        if (word.length() < 2) {
            return word;
        }
        List<Character> letters = new ArrayList<>();
        for (char c : word.toCharArray()) {
            letters.add(c);
        }
        String result = word;
        for (int attempt = 0; attempt < 10 && result.equals(word); attempt++) {
            Collections.shuffle(letters);
            StringBuilder out = new StringBuilder();
            letters.forEach(out::append);
            result = out.toString();
        }
        return result;
    }

    static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private Reward reward() {
        Reward reward = Reward.read(config().getConfigurationSection("CHAT-GAMES.REWARD"));
        return reward.isEmpty() ? new Reward(500D, 0L, List.of(), List.of(), List.of()) : reward;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Game game = active;
        if (game == null || !game.answers().contains(normalize(event.getMessage()))) {
            return;
        }
        if (!game.won().compareAndSet(false, true)) {
            return;
        }
        Player winner = event.getPlayer();
        double seconds = (System.currentTimeMillis() - game.startedAt()) / 1000D;
        plugin.getSpigotScheduler().runGlobal(() -> finish(game, winner, seconds));
    }

    private void finish(Game game, Player winner, double seconds) {
        if (active != game) {
            return;
        }
        active = null;
        if (timeout != null) {
            timeout.cancel();
        }
        Reward reward = reward();
        Text.broadcast(Text.prefix() + Text.get("CHATGAME.WON",
                "&a{player} &7got it in &f{seconds}s&7! The answer was &f{answer}&7.",
                "{player}", winner.getName(), "{seconds}", String.format(Locale.ROOT, "%.1f", seconds),
                "{answer}", game.answers().get(0)));
        plugin.getSpigotScheduler().runEntity(winner, () -> {
            if (winner.isOnline()) {
                reward.give(plugin, winner);
                winner.playSound(winner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.4F);
                Text.send(winner, "CHATGAME.PRIZE-GIVEN", "&aYou won {prize}&a!",
                        "{prize}", String.join("&7, ", reward.describe(plugin)));
            }
        });
    }

    private void expire(Game game) {
        if (active != game || !game.won().compareAndSet(false, true)) {
            return;
        }
        active = null;
        Text.broadcast(Text.prefix() + Text.get("CHATGAME.EXPIRED", "&7Nobody got it! The answer was &f{answer}&7.",
                "{answer}", game.answers().get(0)));
    }

    public void stop() {
        Game game = active;
        active = null;
        if (timeout != null) {
            timeout.cancel();
        }
        if (game != null) {
            game.won().set(true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("start") || action.equals("stop")) {
            if (!PermissionUtils.has(sender, HOST_PERMISSION)) {
                Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
                return true;
            }
            if (action.equals("stop")) {
                stop();
                Text.send(sender, "CHATGAME.STOPPED", "&7Chat game stopped.");
                return true;
            }
            Type type = null;
            if (args.length > 1) {
                try {
                    type = Type.valueOf(args[1].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    Text.send(sender, "CHATGAME.TYPES", "&7Types: &funscramble, math, type, trivia");
                    return true;
                }
            }
            if (!start(type)) {
                Text.send(sender, "CHATGAME.BUSY", "&7A chat game is already running (or there is no trivia set up).");
            }
            return true;
        }
        Game game = active;
        if (game == null) {
            Text.send(sender, "CHATGAME.NONE", "&7No chat game is running. One starts every few minutes!");
        } else {
            Text.send(sender, "CHATGAME.CURRENT", "&7Current question: &f&l{question}", "{question}", game.question());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!PermissionUtils.has(sender, HOST_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("start", "stop").stream().filter(o -> o.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return List.of("unscramble", "math", "type", "trivia").stream()
                    .filter(o -> o.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
