package com.bx.smpPlugin.halloween;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.economy.EconomyContext;
import com.bx.smpPlugin.models.EconomyReason;
import com.bx.smpPlugin.perks.Perk;
import com.bx.smpPlugin.perks.Perks;
import com.bx.smpPlugin.seasonal.EventId;
import com.bx.smpPlugin.seasonal.EventStatus;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;

/**
 * /trickortreat: knock on a door during Trick or Treat. The outcome is decided and handed over the moment the player
 * knocks; the knock, the door and the reveal that follow only show it, so leaving early never loses or repeats a
 * treat. Most knocks give a treat picked by weight from TRICK-OR-TREAT.TREATS; the rest play a harmless trick (a
 * scare, bats, a launch...) that can never hurt the player or take their items.
 *
 * <p>Limits: a wait between knocks (COOLDOWN-MINUTES, shorter with a rank's TRICK-OR-TREAT-COOLDOWN-CUT-PERCENT) and
 * a number of knocks a day (DAILY-LIMIT, more with TRICK-OR-TREAT-EXTRA-KNOCKS). With houses (spots set with
 * /trickortreat house add, or FancyNpcs NPCs named in NPCS) and REQUIRE-HOUSE, players go door to door and the wait
 * counts per house.
 */
public final class TrickOrTreatCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "smpplugin.halloween.trickortreat";
    private static final String LAST_KNOCK = "halloween.trickortreat";
    private static final String HOUSE_FILE = "data/trickortreat-houses.yml";

    /** A spot players knock at. */
    public record House(String id, String name, String world, double x, double y, double z, double radius) {

        boolean contains(Location at) {
            World in = at.getWorld();
            if (in == null || !in.getName().equals(world)) {
                return false;
            }
            double dx = at.getX() - x;
            double dy = at.getY() - y;
            double dz = at.getZ() - z;
            return dx * dx + dy * dy + dz * dz <= radius * radius;
        }
    }

    private final SmpPlugin plugin;
    private final HalloweenManager halloween;
    private final HalloweenHub hub;
    private final PlayerStore store;
    private final SecureRandom random = new SecureRandom();
    private volatile Map<String, House> houses = Map.of();

    public TrickOrTreatCommand(SmpPlugin plugin, HalloweenManager halloween, HalloweenHub hub, PlayerStore store) {
        this.plugin = plugin;
        this.halloween = halloween;
        this.hub = hub;
        this.store = store;
        loadHouses();
    }

    private FileConfiguration config() {
        return halloween.config();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("house")) {
            houseCommand(sender, args);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("odds")) {
            odds(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.send(sender, "GENERAL.PLAYERS-ONLY", "&cOnly players can do that.");
            return true;
        }
        knock(player, null);
        return true;
    }

    /**
     * One knock. {@code npcHouse} is the house id when the player clicked a house NPC, or null for the command.
     * Must run on the player's thread.
     */
    public void knock(Player player, String npcHouse) {
        if (!PermissionUtils.has(player, PERMISSION)) {
            Text.send(player, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return;
        }
        EventStatus status = halloween.events().status(EventId.TRICK_OR_TREAT);
        if (status == EventStatus.DISABLED) {
            Text.send(player, "TRICK-OR-TREAT.DISABLED", "&7Nobody is answering the door right now.");
            return;
        }
        if (status != EventStatus.ACTIVE) {
            long wait = halloween.events().secondsUntilStart(EventId.TRICK_OR_TREAT);
            Text.send(player, "TRICK-OR-TREAT.OUT-OF-SEASON",
                    "&7Trick or Treat opens with the Halloween season{when}.",
                    "{when}", wait > 0 ? " in &f" + Text.duration(wait) + "&7" : "");
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!store.isLoaded(uuid)) {
            Text.send(player, "GENERAL.LOADING", "&7Your data is still loading. Try again in a moment.");
            return;
        }
        String house = npcHouse != null ? npcHouse : houseAt(player.getLocation());
        if (house == null && config().getBoolean("TRICK-OR-TREAT.REQUIRE-HOUSE", false)) {
            Text.send(player, "TRICK-OR-TREAT.NO-HOUSE",
                    "&7There is no door to knock on here. Find a decorated house (or a trick-or-treat NPC).");
            return;
        }

        long now = System.currentTimeMillis();
        long today = halloween.now().toLocalDate().toEpochDay();
        long cooldown = cooldownMillis(player);
        String knocksSection = "halloween." + halloween.seasonYear() + ".knocks";
        String housesSection = "halloween." + halloween.seasonYear() + ".houses";
        KnockLog log = KnockLog.parse(store.get(uuid, knocksSection));
        int limit = dailyLimit(player);
        if (!log.canKnock(today, limit)) {
            Text.send(player, "TRICK-OR-TREAT.DAILY-LIMIT",
                    "&7You knocked &f{limit} &7times today, the most there is. Come back tomorrow!",
                    "{limit}", Integer.toString(limit));
            return;
        }
        Map<String, Long> visited = parseVisits(store.get(uuid, housesSection));
        long last = house == null ? parseLong(store.get(uuid, LAST_KNOCK)) : visited.getOrDefault(house, 0L);
        if (now - last < cooldown) {
            Text.send(player, house == null ? "TRICK-OR-TREAT.COOLDOWN" : "TRICK-OR-TREAT.HOUSE-COOLDOWN",
                    house == null ? "&7You knocked recently. Try again in &f{time}&7."
                            : "&7You already knocked here. Try another house, or come back in &f{time}&7.",
                    "{time}", Text.duration((cooldown - (now - last)) / 1000L));
            return;
        }

        // Decide and record the knock first, then hand over the treat: a crash can lose a treat, never repeat it.
        store.set(uuid, knocksSection, log.knock(today).serialize());
        if (house == null) {
            store.set(uuid, LAST_KNOCK, Long.toString(now));
        } else {
            visited.put(house, now);
            store.set(uuid, housesSection, writeVisits(visited));
        }
        int treatChance = Math.max(0, Math.min(100, config().getInt("TRICK-OR-TREAT.TREAT-CHANCE", 70)));
        boolean treat = random.nextInt(100) < treatChance;
        TreatTable.Treat picked = treat ? table().pick(random) : null;
        List<String> prize = new ArrayList<>();
        if (picked != null) {
            picked.reward().give(plugin, player, EconomyReason.EVENT_REWARD,
                    EconomyContext.of("halloween", "trick or treat: " + picked.id()));
            prize.addAll(picked.reward().describe(plugin));
            long candy = hub.earn(player, picked.candy(), true, "Trick or Treat");
            if (candy > 0L) {
                prize.add("&6" + candy + " " + hub.candyName());
            }
        } else {
            hub.earn(player, Math.max(0L, config().getLong("TRICK-OR-TREAT.TRICK-CANDY", 1L)), true, "Trick or Treat");
        }
        hub.track(player, QuestType.TRICK_OR_TREAT, null, 1);

        Text.actionBar(player, Text.get("TRICK-OR-TREAT.KNOCK", "&6&lKnock, knock..."));
        player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.6F, 1.3F);
        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_OPEN, 0.8F, 0.7F);
            if (picked != null) {
                showTreat(player, picked, prize);
            } else {
                trick(player);
            }
        }, 30L);
    }

    private TreatTable table() {
        return TreatTable.read(config().getConfigurationSection("TRICK-OR-TREAT.TREATS"));
    }

    private int perk(Player player, Perk perk) {
        Perks perks = plugin.getV3Module() == null ? null : plugin.getV3Module().perks();
        return perks == null ? 0 : perks.value(node -> PermissionUtils.has(player, node), perk);
    }

    long cooldownMillis(Player player) {
        long base = Math.max(0L, config().getLong("TRICK-OR-TREAT.COOLDOWN-MINUTES", 60L)) * 60_000L;
        int cut = perk(player, Perk.TRICK_OR_TREAT_COOLDOWN_CUT_PERCENT);
        return base - base * cut / 100L;
    }

    int dailyLimit(Player player) {
        int limit = Math.max(0, config().getInt("TRICK-OR-TREAT.DAILY-LIMIT", 0));
        return limit == 0 ? 0 : limit + perk(player, Perk.TRICK_OR_TREAT_EXTRA_KNOCKS);
    }

    private void showTreat(Player player, TreatTable.Treat treat, List<String> prize) {
        String text = String.join("&7, ", prize);
        Text.title(player, Text.get("TRICK-OR-TREAT.TREAT-TITLE", "&6&lTREAT!"), text, 5, 40, 10);
        Text.send(player, "TRICK-OR-TREAT.TREAT", "&6Treat! &7You got {prize}&7.", "{prize}", text);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.5F);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 20, 0.5, 0.6, 0.5, 0.0);
        if (treat.announce()) {
            Text.broadcast(Text.prefix() + Text.get("TRICK-OR-TREAT.JACKPOT-BROADCAST",
                    "&6{player} &7got a rare treat: {prize}&7! &8(/trickortreat)", "{player}", player.getName(), "{prize}", text));
        }
    }

    private void trick(Player player) {
        List<String> tricks = new ArrayList<>();
        for (String raw : config().getStringList("TRICK-OR-TREAT.TRICKS")) {
            tricks.add(raw.trim().toUpperCase(Locale.ROOT));
        }
        if (tricks.isEmpty()) {
            tricks.add("SCARE");
        }
        String trick = tricks.get(random.nextInt(tricks.size()));
        Text.title(player, Text.get("TRICK-OR-TREAT.TRICK-TITLE", "&5&lTRICK!"), "", 5, 30, 10);
        Location at = player.getLocation();
        switch (trick) {
            case "BATS" -> {
                Text.send(player, "TRICK-OR-TREAT.TRICK-BATS", "&5Trick! &7A swarm of bats flies out of the door!");
                List<Bat> bats = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    bats.add(player.getWorld().spawn(at.clone().add(0, 1.5, 0), Bat.class, bat -> {
                        bat.setPersistent(false);
                        bat.setRemoveWhenFarAway(true);
                    }));
                }
                plugin.getSpigotScheduler().runEntityLater(player, () -> bats.forEach(bat -> {
                    if (bat.isValid()) {
                        bat.getWorld().spawnParticle(Particle.SMOKE, bat.getLocation(), 6, 0.1, 0.1, 0.1, 0.01);
                        bat.remove();
                    }
                }), 100L);
            }
            case "LAUNCH" -> {
                Text.send(player, "TRICK-OR-TREAT.TRICK-LAUNCH", "&5Trick! &7Up you go!");
                player.setVelocity(new Vector(0, 1.3, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 160, 0, false, false, true));
                player.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1F, 0.8F);
            }
            case "SPIN" -> {
                Text.send(player, "TRICK-OR-TREAT.TRICK-SPIN", "&5Trick! &7The world spins...");
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0, false, false, true));
                Location turned = at.clone();
                turned.setYaw(at.getYaw() + 180F);
                plugin.getSpigotScheduler().teleport(player, turned);
            }
            case "DARKNESS" -> {
                Text.send(player, "TRICK-OR-TREAT.TRICK-DARKNESS", "&5Trick! &7The lights go out...");
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0, false, false, true));
                player.playSound(at, Sound.AMBIENT_CAVE, 1F, 0.8F);
            }
            case "FAKE_CREEPER" -> {
                Text.send(player, "TRICK-OR-TREAT.TRICK-CREEPER", "&5Trick! &7Did you hear that?");
                Location behind = at.clone().subtract(at.getDirection().setY(0).normalize().multiply(2));
                player.playSound(behind, Sound.ENTITY_CREEPER_PRIMED, 1F, 1F);
            }
            default -> {
                Text.send(player, "TRICK-OR-TREAT.TRICK-SCARE", "&5Trick! &7BOO!");
                player.spawnParticle(Particle.ELDER_GUARDIAN, at, 1);
                player.playSound(at, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1F, 1F);
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0, false, false, true));
            }
        }
    }

    /** Knocks left today for the hub ("-1" means no limit). */
    public int knocksLeft(Player player) {
        int limit = dailyLimit(player);
        if (limit <= 0) {
            return -1;
        }
        KnockLog log = KnockLog.parse(store.get(player.getUniqueId(), "halloween." + halloween.seasonYear() + ".knocks"));
        return Math.max(0, limit - log.usedOn(halloween.now().toLocalDate().toEpochDay()));
    }

    /** Seconds until the next knock without a house (0: now). */
    public long secondsUntilKnock(Player player) {
        long last = parseLong(store.get(player.getUniqueId(), LAST_KNOCK));
        long left = cooldownMillis(player) - (System.currentTimeMillis() - last);
        return Math.max(0L, left / 1000L);
    }

    // ------------------------------------------------------------------ houses

    /** The house whose spot contains {@code at}, or null. */
    private String houseAt(Location at) {
        for (House house : houses.values()) {
            if (house.contains(at)) {
                return house.id();
            }
        }
        return null;
    }

    /** True when an NPC with this name is a Trick or Treat house (TRICK-OR-TREAT.NPCS). */
    public boolean isHouseNpc(String npcName) {
        if (npcName == null) {
            return false;
        }
        for (String name : config().getStringList("TRICK-OR-TREAT.NPCS")) {
            if (name.equalsIgnoreCase(npcName)) {
                return true;
            }
        }
        return false;
    }

    private void houseCommand(CommandSender sender, String[] args) {
        if (!PermissionUtils.has(sender, HalloweenManager.ADMIN_PERMISSION)) {
            Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "add" -> {
                if (!(sender instanceof Player player) || args.length < 3) {
                    Text.send(sender, "TRICK-OR-TREAT.HOUSE-USAGE", "&7/trickortreat house add <id> [radius] &8(stand at the door)");
                    return;
                }
                String id = args[2].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
                double radius = 4D;
                if (args.length > 3) {
                    try {
                        radius = Math.max(1D, Math.min(32D, Double.parseDouble(args[3])));
                    } catch (NumberFormatException ignored) {
                        radius = 4D;
                    }
                }
                Location at = player.getLocation();
                if (id.isEmpty() || at.getWorld() == null) {
                    Text.send(sender, "TRICK-OR-TREAT.HOUSE-USAGE", "&7/trickortreat house add <id> [radius] &8(stand at the door)");
                    return;
                }
                Map<String, House> updated = new TreeMap<>(houses);
                updated.put(id, new House(id, id, at.getWorld().getName(), at.getX(), at.getY(), at.getZ(), radius));
                houses = Map.copyOf(updated);
                saveHouses();
                plugin.getLogger().info("[Halloween] " + sender.getName() + " added the Trick or Treat house " + id + ".");
                Text.send(sender, "TRICK-OR-TREAT.HOUSE-ADDED", "&aHouse &f{id} &aadded here (radius {radius}).",
                        "{id}", id, "{radius}", Double.toString(radius));
            }
            case "remove" -> {
                if (args.length < 3 || !houses.containsKey(args[2].toLowerCase(Locale.ROOT))) {
                    Text.send(sender, "TRICK-OR-TREAT.HOUSE-UNKNOWN", "&7No house with that id. &f/trickortreat house list");
                    return;
                }
                Map<String, House> updated = new TreeMap<>(houses);
                updated.remove(args[2].toLowerCase(Locale.ROOT));
                houses = Map.copyOf(updated);
                saveHouses();
                Text.send(sender, "TRICK-OR-TREAT.HOUSE-REMOVED", "&7House &f{id} &7removed.", "{id}", args[2]);
            }
            default -> {
                Text.send(sender, "TRICK-OR-TREAT.HOUSE-LIST", "&6Trick or Treat houses: &f{count}{npcs}",
                        "{count}", Integer.toString(houses.size()),
                        "{npcs}", config().getStringList("TRICK-OR-TREAT.NPCS").isEmpty() ? ""
                                : " &7(and NPCs: &f" + String.join(", ", config().getStringList("TRICK-OR-TREAT.NPCS")) + "&7)");
                for (House house : houses.values()) {
                    Text.raw(sender, "&8 » &f" + house.id() + " &7" + house.world() + " " + Math.round(house.x()) + " "
                            + Math.round(house.y()) + " " + Math.round(house.z()) + " &8(radius " + house.radius() + ")");
                }
            }
        }
    }

    private void odds(CommandSender sender) {
        if (!PermissionUtils.has(sender, HalloweenManager.ADMIN_PERMISSION)) {
            Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return;
        }
        TreatTable table = table();
        int treatChance = Math.max(0, Math.min(100, config().getInt("TRICK-OR-TREAT.TREAT-CHANCE", 70)));
        Text.send(sender, "TRICK-OR-TREAT.ODDS-HEADER", "&6Trick or Treat: &f{chance}% &7treats, the rest tricks",
                "{chance}", Integer.toString(treatChance));
        for (TreatTable.Treat treat : table.treats()) {
            Text.raw(sender, String.format(Locale.ROOT, "&8 » &f%s &7%.2f%% of treats &8(weight %d)", treat.id(),
                    table.chancePercent(treat), treat.weight()));
        }
        for (String problem : table.problems()) {
            Text.raw(sender, "&c " + problem);
        }
    }

    private File houseFile() {
        return new File(plugin.getDataFolder(), HOUSE_FILE);
    }

    private void loadHouses() {
        File file = houseFile();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not read " + HOUSE_FILE + "; Trick or Treat houses are off until it is fixed.", exception);
            return;
        }
        Map<String, House> loaded = new TreeMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("HOUSES");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection house = section.getConfigurationSection(id);
                if (house != null && house.getString("WORLD") != null) {
                    loaded.put(id, new House(id, house.getString("NAME", id), house.getString("WORLD"), house.getDouble("X"),
                            house.getDouble("Y"), house.getDouble("Z"), Math.max(1D, house.getDouble("RADIUS", 4D))));
                }
            }
        }
        houses = Map.copyOf(loaded);
    }

    private void saveHouses() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (House house : houses.values()) {
            String path = "HOUSES." + house.id();
            yaml.set(path + ".NAME", house.name());
            yaml.set(path + ".WORLD", house.world());
            yaml.set(path + ".X", house.x());
            yaml.set(path + ".Y", house.y());
            yaml.set(path + ".Z", house.z());
            yaml.set(path + ".RADIUS", house.radius());
        }
        String text = "# Trick or Treat houses, set with /trickortreat house add|remove.\n" + yaml.saveToString();
        File file = houseFile();
        plugin.getDatabaseManager().executeAsync(() -> {
            try {
                Files.createDirectories(file.getParentFile().toPath());
                File temp = new File(file.getParentFile(), file.getName() + ".tmp");
                Files.writeString(temp.toPath(), text, StandardCharsets.UTF_8);
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not save " + HOUSE_FILE, exception);
            }
        });
    }

    static Map<String, Long> parseVisits(String stored) {
        Map<String, Long> visits = new TreeMap<>();
        if (stored == null || stored.isBlank()) {
            return visits;
        }
        for (String entry : stored.split(";")) {
            int split = entry.indexOf('=');
            if (split > 0) {
                visits.put(entry.substring(0, split), parseLong(entry.substring(split + 1)));
            }
        }
        return visits;
    }

    static String writeVisits(Map<String, Long> visits) {
        StringBuilder out = new StringBuilder();
        visits.forEach((house, at) -> out.append(out.isEmpty() ? "" : ";").append(house).append('=').append(at));
        return out.toString();
    }

    private static long parseLong(String raw) {
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!PermissionUtils.has(sender, HalloweenManager.ADMIN_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("house", "odds"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("house")) {
            return filter(List.of("add", "remove", "list"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("house") && args[1].equalsIgnoreCase("remove")) {
            return filter(List.copyOf(houses.keySet()), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String typed) {
        String lower = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
