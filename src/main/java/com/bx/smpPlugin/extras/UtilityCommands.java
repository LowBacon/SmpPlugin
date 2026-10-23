// SmpPlugin by LowBacon
package com.bx.smpPlugin.extras;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.managers.EconomyManager;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.models.Team;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import com.bx.smpPlugin.utils.RolePrefixResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UtilityCommands implements CommandExecutor, TabCompleter {

    private static final List<String> WORKSTATIONS =
            List.of("craft", "anvil", "grindstone", "stonecutter", "loom", "smithing", "cartography");

    private final SmpPlugin plugin;
    private final ExtrasListener tracker;
    private final Map<String, Map<UUID, Long>> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> suicideConfirm = new ConcurrentHashMap<>();

    public UtilityCommands(SmpPlugin plugin, ExtrasListener tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
    }

    private FileConfiguration config() {
        FileConfiguration extras = plugin.getConfigManager().getExtras();
        return extras == null ? new YamlConfiguration() : extras;
    }

    public static String permission(String command) {
        if (WORKSTATIONS.contains(command)) {
            return "smpplugin.workbench." + command;
        }
        return "smpplugin." + command;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!PermissionUtils.has(sender, permission(name))) {
            Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        switch (name) {
            case "uptime" -> uptime(sender);
            case "calc" -> calc(sender, args);
            case "online" -> online(sender);
            case "seen" -> seen(sender, args);
            default -> {
                if (!(sender instanceof Player player)) {
                    Text.send(sender, "GENERAL.PLAYERS-ONLY", "&cOnly players can do that.");
                    return true;
                }
                playerCommand(player, name, args);
            }
        }
        return true;
    }

    private void playerCommand(Player player, String name, String[] args) {
        switch (name) {
            case "back" -> back(player);
            case "near" -> near(player);
            case "coords" -> coords(player, args);
            case "suicide" -> suicide(player);
            case "ptime" -> ptime(player, args);
            case "pweather" -> pweather(player, args);
            case "repair" -> repair(player, args);
            case "skull" -> skull(player, args);
            case "lore" -> lore(player, args);
            case "glow" -> {
                player.setGlowing(!player.isGlowing());
                Text.send(player, "UTIL.GLOW", player.isGlowing() ? "&aYou are glowing." : "&7You stopped glowing.");
            }
            default -> workstation(player, name);
        }
    }

    private boolean onCooldown(Player player, String key, long seconds) {
        if (seconds <= 0) {
            return false;
        }
        Map<UUID, Long> map = cooldowns.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
        long now = System.currentTimeMillis();
        Long last = map.get(player.getUniqueId());
        if (last != null && now - last < seconds * 1000L) {
            Text.send(player, "UTIL.COOLDOWN", "&7You can do that again in &f{time}&7.",
                    "{time}", Text.duration((seconds * 1000L - (now - last)) / 1000L + 1));
            return true;
        }
        map.put(player.getUniqueId(), now);
        return false;
    }

    // ------------------------------------------------------------------ workstations

    private void workstation(Player player, String name) {
        MenuType.Typed<?, ?> type = switch (name) {
            case "craft" -> MenuType.CRAFTING;
            case "anvil" -> MenuType.ANVIL;
            case "grindstone" -> MenuType.GRINDSTONE;
            case "stonecutter" -> MenuType.STONECUTTER;
            case "loom" -> MenuType.LOOM;
            case "smithing" -> MenuType.SMITHING;
            default -> MenuType.CARTOGRAPHY_TABLE;
        };
        try {
            InventoryView view = type.create(player, ColorUtils.toComponent(
                    Text.get("UTIL.WORKSTATION-TITLE." + name.toUpperCase(Locale.ROOT), titleOf(name))));
            player.openInventory(view);
        } catch (RuntimeException | LinkageError unsupported) {
            if (name.equals("craft")) {
                player.openWorkbench(null, true);
            } else {
                Text.send(player, "UTIL.WORKSTATION-UNSUPPORTED", "&cThis server can't open a portable {name}.", "{name}", name);
            }
        }
    }

    private static String titleOf(String name) {
        return switch (name) {
            case "craft" -> "Crafting";
            case "anvil" -> "Repair & Name";
            case "cartography" -> "Cartography Table";
            default -> Character.toUpperCase(name.charAt(0)) + name.substring(1);
        };
    }

    // ------------------------------------------------------------------ movement

    private void back(Player player) {
        Location target = tracker.lastLocation(player.getUniqueId());
        if (target == null || target.getWorld() == null) {
            Text.send(player, "UTIL.BACK-NONE", "&7There is nowhere to go back to.");
            return;
        }
        if (plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            Text.send(player, "GENERAL.IN-COMBAT", "&cYou can't do that in combat.");
            return;
        }
        if (plugin.getHomeManager() != null && plugin.getHomeManager().isInArena(player)) {
            Text.send(player, "UTIL.BACK-ARENA", "&cYou can't use /back in an arena.");
            return;
        }
        if (onCooldown(player, "back", config().getLong("UTILITIES.BACK-COOLDOWN-SECONDS", 10L))) {
            return;
        }
        plugin.getSpigotScheduler().teleport(player, target).thenAccept(success -> {
            if (Boolean.TRUE.equals(success)) {
                plugin.getSpigotScheduler().runEntity(player, () -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6F, 1.2F);
                    Text.send(player, "UTIL.BACK", "&aWhoosh! You went back.");
                });
            }
        });
    }

    private void near(Player player) {
        double radius = Math.max(10D, config().getDouble("UTILITIES.NEAR-RADIUS", 200D));
        List<String> found = new ArrayList<>();
        Location here = player.getLocation();
        List<Player> others = new ArrayList<>();
        for (Player other : player.getWorld().getPlayers()) {
            if (!other.equals(player) && player.canSee(other) && other.getLocation().distanceSquared(here) <= radius * radius) {
                others.add(other);
            }
        }
        others.sort((a, b) -> Double.compare(a.getLocation().distanceSquared(here), b.getLocation().distanceSquared(here)));
        for (Player other : others) {
            String name = plugin.getHideManager() == null ? other.getName() : plugin.getHideManager().visibleName(player, other);
            found.add("&f" + name + " &7" + (int) other.getLocation().distance(here) + "m " + arrow(here, other.getLocation()));
        }
        if (found.isEmpty()) {
            Text.send(player, "UTIL.NEAR-NONE", "&7Nobody within &f{radius} &7blocks.", "{radius}", Integer.toString((int) radius));
            return;
        }
        Text.send(player, "UTIL.NEAR", "&7Players within &f{radius} &7blocks: {list}",
                "{radius}", Integer.toString((int) radius), "{list}", String.join("&8, ", found));
    }

    static String arrow(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = Math.floorMod((long) Math.round(targetYaw - from.getYaw()), 360L);
        String[] arrows = {"⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉"};
        return "&e" + arrows[(int) Math.round(relative / 45D) % 8];
    }

    private void coords(Player player, String[] args) {
        PlayerData data = plugin.getPlayerDataManager().get(player);
        Location at = player.getLocation();
        String shown = data == null ? at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ() : data.getDisplayCoords(at);
        String world = worldName(at.getWorld());
        if (args.length > 0 && args[0].equalsIgnoreCase("share")) {
            Team team = plugin.getTeamManager() == null ? null : plugin.getTeamManager().getTeam(player.getUniqueId());
            if (team == null) {
                Text.send(player, "UTIL.COORDS-NO-TEAM", "&7Join a team to share your coordinates with it.");
                return;
            }
            String message = Text.get("UTIL.COORDS-SHARED", "&b[Team] &f{player} &7is at &f{coords} &7in &f{world}",
                    "{player}", player.getName(), "{coords}", shown, "{world}", world);
            for (UUID member : team.getMemberUuids()) {
                Player online = Bukkit.getPlayer(member);
                if (online != null) {
                    Text.raw(online, message);
                }
            }
            return;
        }
        Text.line(player, Text.prefix(),
                Text.get("UTIL.COORDS", "&7You are at &f{coords} &7in &f{world} &7facing &f{facing}&7. ",
                        "{coords}", shown, "{world}", world, "{facing}", facing(at.getYaw())),
                Text.button(Text.get("UTIL.COORDS-SHARE", "&b[Share with team]"), "coords share",
                        Text.get("UTIL.COORDS-SHARE-HOVER", "&7Send your coordinates to your team")));
    }

    static String facing(float yaw) {
        String[] names = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        return names[(int) Math.floorMod(Math.round(yaw / 45F), 8)];
    }

    private static String worldName(World world) {
        if (world == null) {
            return "?";
        }
        return switch (world.getEnvironment()) {
            case NETHER -> "the Nether";
            case THE_END -> "the End";
            default -> world.getName();
        };
    }

    private void suicide(Player player) {
        if (plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            Text.send(player, "GENERAL.IN-COMBAT", "&cYou can't do that in combat.");
            return;
        }
        long now = System.currentTimeMillis();
        Long asked = suicideConfirm.get(player.getUniqueId());
        if (asked == null || now - asked > 10_000L) {
            suicideConfirm.put(player.getUniqueId(), now);
            Text.send(player, "UTIL.SUICIDE-CONFIRM", "&cAre you sure? You will drop your items. Type &f/suicide &cagain within 10 seconds.");
            return;
        }
        suicideConfirm.remove(player.getUniqueId());
        player.setHealth(0D);
    }

    // ------------------------------------------------------------------ personal time and weather

    private void ptime(Player player, String[] args) {
        String mode = args.length == 0 ? "reset" : args[0].toLowerCase(Locale.ROOT);
        long time;
        switch (mode) {
            case "day" -> time = 1000L;
            case "noon" -> time = 6000L;
            case "sunset" -> time = 12000L;
            case "night" -> time = 14000L;
            case "midnight" -> time = 18000L;
            case "sunrise" -> time = 23000L;
            case "reset" -> {
                player.resetPlayerTime();
                Text.send(player, "UTIL.PTIME-RESET", "&7Your time follows the server again.");
                return;
            }
            default -> {
                try {
                    time = Math.floorMod(Long.parseLong(mode), 24000L);
                } catch (NumberFormatException ignored) {
                    Text.send(player, "UTIL.PTIME-USAGE", "&7/ptime <day|noon|sunset|night|midnight|sunrise|ticks|reset>");
                    return;
                }
            }
        }
        player.setPlayerTime(time, false);
        Text.send(player, "UTIL.PTIME", "&7Your time is now &f{time}&7. &8(/ptime reset to undo)", "{time}", mode);
    }

    private void pweather(Player player, String[] args) {
        String mode = args.length == 0 ? "reset" : args[0].toLowerCase(Locale.ROOT);
        switch (mode) {
            case "clear", "sun" -> player.setPlayerWeather(WeatherType.CLEAR);
            case "rain", "storm" -> player.setPlayerWeather(WeatherType.DOWNFALL);
            case "reset" -> {
                player.resetPlayerWeather();
                Text.send(player, "UTIL.PWEATHER-RESET", "&7Your weather follows the server again.");
                return;
            }
            default -> {
                Text.send(player, "UTIL.PWEATHER-USAGE", "&7/pweather <clear|rain|reset>");
                return;
            }
        }
        Text.send(player, "UTIL.PWEATHER", "&7Your weather is now &f{weather}&7.", "{weather}", mode);
    }

    // ------------------------------------------------------------------ items

    private void repair(Player player, String[] args) {
        boolean all = args.length > 0 && args[0].equalsIgnoreCase("all");
        if (all && !PermissionUtils.has(player, "smpplugin.repair.all")) {
            Text.send(player, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return;
        }
        List<ItemStack> targets = new ArrayList<>();
        if (all) {
            targets.addAll(Arrays.asList(player.getInventory().getContents()));
        } else {
            targets.add(player.getInventory().getItemInMainHand());
        }
        int repaired = 0;
        for (ItemStack item : targets) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof Damageable damageable && damageable.hasDamage()) {
                repaired++;
            }
        }
        if (repaired == 0) {
            Text.send(player, "UTIL.REPAIR-NOTHING", all ? "&7Nothing in your inventory needs repairing." : "&7That item doesn't need repairing.");
            return;
        }
        if (!PermissionUtils.has(player, "smpplugin.repair.bypasscooldown")
                && onCooldown(player, "repair", config().getLong("UTILITIES.REPAIR-COOLDOWN-SECONDS", 3600L))) {
            return;
        }
        for (ItemStack item : targets) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof Damageable damageable && damageable.hasDamage()) {
                damageable.setDamage(0);
                item.setItemMeta(meta);
            }
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6F, 1.4F);
        Text.send(player, "UTIL.REPAIRED", "&aRepaired &f{count} &aitem(s).", "{count}", Integer.toString(repaired));
    }

    private void skull(Player player, String[] args) {
        String name = args.length > 0 ? args[0] : player.getName();
        com.bx.smpPlugin.core.Lookup.account(plugin, player, name, account -> {
            if (account == null) {
                Text.send(player, "UTIL.SKULL-UNKNOWN", "&cNo player called &f{player} &chas joined this server.", "{player}", name);
            } else {
                giveSkull(player, account);
            }
        });
    }

    private void giveSkull(Player player, EconomyManager.AccountReference account) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(account.uuid());
        ItemStack head = ItemUtils.createPlayerHead(owner, "&e" + account.displayName() + "'s Head", List.of());
        for (ItemStack rest : player.getInventory().addItem(head).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
        Text.send(player, "UTIL.SKULL", "&aHere is &f{player}&a's head.", "{player}", account.displayName());
    }

    private void lore(Player player, String[] args) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            Text.send(player, "UTIL.LORE-EMPTY", "&7Hold the item you want to change.");
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || args.length == 0) {
            Text.send(player, "UTIL.LORE-USAGE", "&7/lore add <text> &8| &7/lore set <line> <text> &8| &7/lore remove <line> &8| &7/lore clear");
            return;
        }
        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        String action = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "add" -> lore.add(ColorUtils.colorize(String.join(" ", Arrays.copyOfRange(args, 1, args.length))));
                case "set" -> lore.set(Integer.parseInt(args[1]) - 1,
                        ColorUtils.colorize(String.join(" ", Arrays.copyOfRange(args, 2, args.length))));
                case "remove" -> lore.remove(Integer.parseInt(args[1]) - 1);
                case "clear" -> lore.clear();
                default -> {
                    Text.send(player, "UTIL.LORE-USAGE", "&7/lore add <text> &8| &7/lore set <line> <text> &8| &7/lore remove <line> &8| &7/lore clear");
                    return;
                }
            }
        } catch (RuntimeException badLine) {
            Text.send(player, "UTIL.LORE-LINE", "&cThere is no line like that. The item has &f{count} &cline(s).",
                    "{count}", Integer.toString(lore.size()));
            return;
        }
        if (lore.size() > 20) {
            Text.send(player, "UTIL.LORE-LIMIT", "&cAn item can have at most 20 lore lines here.");
            return;
        }
        meta.setLore(lore.isEmpty() ? null : lore);
        item.setItemMeta(meta);
        Text.send(player, "UTIL.LORE", "&aLore updated.");
    }

    // ------------------------------------------------------------------ information

    private void online(CommandSender sender) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (sender instanceof Player viewer && !viewer.canSee(player)) {
                continue;
            }
            String rank = RolePrefixResolver.resolve(player);
            String name = plugin.getHideManager() == null || !(sender instanceof Player viewer)
                    ? player.getName() : plugin.getHideManager().visibleName(viewer, player);
            names.add((rank == null || rank.isBlank() ? "&f" : rank.trim() + " &f") + name);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        Text.send(sender, "UTIL.ONLINE", "&7Online (&a{count}&7/&f{max}&7): {list}",
                "{count}", Integer.toString(names.size()), "{max}", Integer.toString(Bukkit.getMaxPlayers()),
                "{list}", names.isEmpty() ? "&8nobody" : String.join("&8, ", names));
    }

    private void uptime(CommandSender sender) {
        long uptime = (System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime()) / 1000L;
        double tps = plugin.getOptimizationManager() == null ? -1D : plugin.getOptimizationManager().getLastTps();
        String tpsText = tps < 0 ? "&7measuring..." : (tps >= 18 ? "&a" : tps >= 15 ? "&e" : "&c") + String.format(Locale.ROOT, "%.1f", Math.min(20D, tps));
        Text.send(sender, "UTIL.UPTIME", "&7The server has been up for &f{uptime}&7. TPS: {tps}&7, players: &f{players}",
                "{uptime}", Text.duration(uptime), "{tps}", tpsText, "{players}", Integer.toString(Bukkit.getOnlinePlayers().size()));
    }

    private void calc(CommandSender sender, String[] args) {
        if (args.length == 0) {
            Text.send(sender, "UTIL.CALC-USAGE", "&7/calc <sum>, e.g. &f/calc 64*27 &7or &f/calc 2.5m/3");
            return;
        }
        String expression = String.join(" ", args);
        try {
            Text.send(sender, "UTIL.CALC", "&7{expression} &8= &a&l{result}",
                    "{expression}", expression.replace('&', '＆'), "{result}", Calculator.format(Calculator.evaluate(expression)));
        } catch (IllegalArgumentException invalid) {
            Text.send(sender, "UTIL.CALC-ERROR", "&cCan't work that out: {reason}", "{reason}", invalid.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private void seen(CommandSender sender, String[] args) {
        if (args.length == 0) {
            Text.send(sender, "UTIL.SEEN-USAGE", "&7/seen <player>");
            return;
        }
        com.bx.smpPlugin.core.Lookup.account(plugin, sender, args[0], account -> {
            if (account == null) {
                Text.send(sender, "UTIL.SEEN-UNKNOWN", "&cNo player called &f{player} &chas joined this server.", "{player}", args[0]);
            } else {
                seen(sender, account);
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void seen(CommandSender sender, EconomyManager.AccountReference account) {
        Player online = Bukkit.getPlayer(account.uuid());
        boolean hidden = online != null && sender instanceof Player viewer && !viewer.canSee(online);
        OfflinePlayer offline = Bukkit.getOfflinePlayer(account.uuid());
        long first = offline.getFirstPlayed();
        if (online != null && !hidden) {
            Text.send(sender, "UTIL.SEEN-ONLINE", "&f{player} &ais online now&7. First joined &f{first} &7ago.",
                    "{player}", account.displayName(), "{first}", first > 0 ? Text.duration((System.currentTimeMillis() - first) / 1000L) : "?");
            return;
        }
        long last = offline.getLastPlayed();
        Text.send(sender, "UTIL.SEEN", "&f{player} &7was last seen &f{last} &7ago. First joined &f{first} &7ago.",
                "{player}", account.displayName(),
                "{last}", last > 0 ? Text.duration((System.currentTimeMillis() - last) / 1000L) : "a long time",
                "{first}", first > 0 ? Text.duration((System.currentTimeMillis() - first) / 1000L) : "?");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length != 1) {
            return List.of();
        }
        String typed = args[0].toLowerCase(Locale.ROOT);
        List<String> options = switch (name) {
            case "ptime" -> List.of("day", "noon", "sunset", "night", "midnight", "sunrise", "reset");
            case "pweather" -> List.of("clear", "rain", "reset");
            case "repair" -> List.of("all");
            case "lore" -> List.of("add", "set", "remove", "clear");
            case "coords" -> List.of("share");
            case "seen", "skull" -> {
                List<String> names = new ArrayList<>();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!(sender instanceof Player viewer) || viewer.canSee(online)) {
                        names.add(online.getName());
                    }
                }
                yield names;
            }
            default -> List.of();
        };
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }
}
