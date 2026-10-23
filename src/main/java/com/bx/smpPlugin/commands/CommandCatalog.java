// SmpPlugin by LowBacon
package com.bx.smpPlugin.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permissible;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// Which /help category each command belongs to, and who may see it there. Names, aliases, usage,
// descriptions and permissions come from plugin.yml, so help never disagrees with what is registered;
// CommandCatalogTest fails the build when a command in plugin.yml has no category here.
public final class CommandCatalog {

    public enum Category {
        GENERAL("general", "General", "Menus, help, rules and server links"),
        TRAVEL("travel", "Travel and homes", "Spawn, lobby, homes, warps, RTP and teleport requests"),
        CHAT("chat", "Chat", "Private messages, mail, ignoring and reporting"),
        SOCIAL("social", "Teams and friends", "Teams, friends and emotes"),
        ECONOMY("economy", "Economy", "Money, shards and payments"),
        MARKET("market", "Shops and auctions", "Shop, selling, Auction House and Orders"),
        REWARDS("rewards", "Rewards and events", "Daily rewards, challenges and seasonal calendars"),
        STATS("stats", "Statistics", "Stats, playtime, leaderboards and who is online"),
        PVP("pvp", "PvP", "Duels, FFA and bounties"),
        GAMES("games", "Games", "Crates, CoinFlip, dice, roulette and giveaways"),
        UTILITY("utility", "Utility", "Workbenches, repair and other handy tools"),
        SETTINGS("settings", "Settings", "Your settings and toggles"),
        STAFF("staff", "Staff", "Moderation and punishments"),
        ADMIN("admin", "Administration", "Server setup and player data");

        private final String key;
        private final String title;
        private final String description;

        Category(String key, String title, String description) {
            this.key = key;
            this.title = title;
            this.description = description;
        }

        public String key() {
            return key;
        }

        public String title() {
            return title;
        }

        public String description() {
            return description;
        }

        public boolean privileged() {
            return this == STAFF || this == ADMIN;
        }

        public static Optional<Category> parse(String input) {
            String wanted = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
            for (Category category : values()) {
                if (category.key.equals(wanted)) {
                    return Optional.of(category);
                }
            }
            return switch (wanted) {
                case "homes", "teleport" -> Optional.of(TRAVEL);
                case "money", "shards" -> Optional.of(ECONOMY);
                case "shops", "auctions" -> Optional.of(MARKET);
                case "events" -> Optional.of(REWARDS);
                case "friends", "teams" -> Optional.of(SOCIAL);
                case "moderation" -> Optional.of(STAFF);
                default -> Optional.empty();
            };
        }
    }

    // One command as help shows it.
    public record Entry(String name, Category category, String description, String usage, List<String> aliases,
                        String permission) {
    }

    private static final Map<String, Category> CATEGORIES = new LinkedHashMap<>();

    // Staff and admin commands whose permission is checked inside the command, not in plugin.yml.
    private static final Map<String, List<String>> VIEW_PERMISSIONS = Map.of(
            "eco", List.of("smpplugin.admin.addmoney", "smpplugin.admin.removemoney", "smpplugin.admin.setmoney",
                    "smpplugin.admin.economy.history", "smpplugin.admin.economy.stats"),
            "playerwipe", List.of("smpplugin.admin.playerwipe"),
            "playerunwipe", List.of("smpplugin.admin.playerunwipe"),
            "seehomes", List.of("smpplugin.staff.seehomes", "smpplugin.staff.profileviewer"));

    static {
        put(Category.GENERAL,
                "menu", "discord", "twitter", "store", "social", "rules", "safety", "help", "servers", "perks",
                "guide");
        put(Category.TRAVEL,
                "lobby", "home", "homes", "sethome", "delhome", "renamehome", "spawn", "afk", "away", "rtp",
                "rtpq", "tpa", "tpahere", "tpaccept", "tpadeny", "tpacancel", "warp", "back");
        put(Category.CHAT,
                "ignore", "unignore", "msg", "reply", "helpop", "report", "mail");
        put(Category.SOCIAL,
                "team", "friends", "friend", "slap", "highfive", "poke", "cheer", "firework", "sit");
        put(Category.ECONOMY,
                "balance", "pay", "baltop", "transactions", "shards", "shardpay");
        put(Category.MARKET,
                "shop", "shardshop", "orders", "ah", "enderchest", "sell", "sellhand", "sellall", "sellhistory",
                "sellmulti", "sellmultiplier", "sellprogress", "meta", "worth", "billford");
        put(Category.REWARDS,
                "halloween", "trickortreat", "advent", "events", "claimitems", "daily", "challenges");
        put(Category.STATS,
                "stats", "ping", "playtime", "leaderboard", "near", "seen", "online", "uptime");
        put(Category.PVP,
                "duel", "create", "queue", "leave", "draw", "ffa", "ffastats", "bounty");
        put(Category.GAMES,
                "crate", "crates", "keys", "coinflip", "giveaway", "dice", "roulette", "rate");
        put(Category.UTILITY,
                "fly", "kill", "craft", "anvil", "grindstone", "stonecutter", "loom", "smithing", "cartography",
                "coords", "suicide", "ptime", "pweather", "repair", "skull", "lore", "glow", "calc");
        put(Category.SETTINGS,
                "pm", "tpauto", "tpahereauto", "nightvision", "phantom", "settings", "sidebar");
        put(Category.STAFF,
                "chat", "ecsee", "logs", "freeze", "heal", "feed", "god", "gamemode", "staffmode", "stafflist",
                "staffchat", "rename", "randomteleport", "teleport", "alts", "vanish", "invsee", "profileviewer",
                "chatlog", "seehomes", "punishments", "ban", "tempban", "mute", "tempmute", "warn", "kick",
                "blacklist", "unban", "unmute", "unblacklist", "vcmute", "vcunmute", "offend", "spawnstash",
                "fakeplayer", "hide", "disguise", "countdown", "speed", "whois", "socialspy", "commandspy");
        put(Category.ADMIN,
                "setspawn", "setlobby", "afkzone", "arena", "ffaarena", "topsell", "eco", "addmoney", "removemoney",
                "setmoney", "addplaytime", "addkills", "adddeaths", "setkills", "setdeaths", "setplaytime",
                "addshards", "removeshards", "setshards", "warpmanager", "setwarp", "delwarp", "portalmanager",
                "spawner", "clearlag", "cuboid", "amethysttool", "smpplugin", "maintenance", "playerwipe",
                "playerunwipe", "serverwipe", "amod", "coinflipadmin", "announcer", "border", "perms", "broadcast",
                "day", "night", "sun", "rain", "thunder", "smite", "butcher", "spawnmob", "more", "performance",
                "spook");
    }

    private CommandCatalog() {
    }

    private static void put(Category category, String... names) {
        for (String name : names) {
            CATEGORIES.put(name, category);
        }
    }

    public static Optional<Category> categoryOf(String command) {
        return Optional.ofNullable(CATEGORIES.get(command == null ? "" : command.toLowerCase(Locale.ROOT)));
    }

    public static Collection<String> catalogued() {
        return Collections.unmodifiableSet(CATEGORIES.keySet());
    }

    public static List<String> viewPermissions(String command) {
        return VIEW_PERMISSIONS.getOrDefault(command, List.of());
    }

    // Help entries for every catalogued command, read from plugin.yml's command map.
    public static List<Entry> entries(Map<String, Map<String, Object>> commands) {
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, Category> catalogued : CATEGORIES.entrySet()) {
            Map<String, Object> details = commands.get(catalogued.getKey());
            if (details != null) {
                entries.add(entry(catalogued.getKey(), catalogued.getValue(), details));
            }
        }
        return entries;
    }

    static Entry entry(String name, Category category, Map<String, Object> details) {
        String description = String.valueOf(details.getOrDefault("description", ""));
        String usage = String.valueOf(details.getOrDefault("usage", "/" + name)).replace("<command>", name);
        Object permission = details.get("permission");
        return new Entry(name, category, description, usage.isBlank() ? "/" + name : usage,
                aliases(details.get("aliases")), permission == null ? "" : String.valueOf(permission));
    }

    // The command a name or alias refers to.
    public static Optional<Entry> find(List<Entry> entries, String input) {
        String wanted = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        if (wanted.startsWith("/")) {
            wanted = wanted.substring(1);
        }
        for (Entry entry : entries) {
            if (entry.name().equals(wanted) || entry.aliases().contains(wanted)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    // Whether help may list the command for this sender. Running it is still checked by the command.
    public static boolean visibleTo(CommandSender sender, Entry entry, Command command) {
        if (command != null && !command.testPermissionSilent(sender)) {
            return false;
        }
        List<String> extra = viewPermissions(entry.name());
        if (!extra.isEmpty()) {
            return anyPermission(sender, extra);
        }
        // A staff or admin command without any permission node is listed for operators only.
        return !entry.category().privileged() || !entry.permission().isBlank() || sender.isOp();
    }

    private static boolean anyPermission(Permissible sender, List<String> nodes) {
        for (String node : nodes) {
            if (sender.hasPermission(node)) {
                return true;
            }
        }
        return false;
    }

    static List<String> aliases(Object raw) {
        List<String> aliases = new ArrayList<>();
        if (raw instanceof Collection<?> list) {
            for (Object alias : list) {
                aliases.add(String.valueOf(alias).toLowerCase(Locale.ROOT));
            }
        } else if (raw instanceof String alias && !alias.isBlank()) {
            aliases.add(alias.toLowerCase(Locale.ROOT));
        }
        return aliases;
    }

    public static Map<Category, List<Entry>> byCategory(List<Entry> entries) {
        Map<Category, List<Entry>> grouped = new EnumMap<>(Category.class);
        for (Entry entry : entries) {
            grouped.computeIfAbsent(entry.category(), ignored -> new ArrayList<>()).add(entry);
        }
        return grouped;
    }
}
