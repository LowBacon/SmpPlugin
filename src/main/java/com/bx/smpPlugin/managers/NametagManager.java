package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.RolePrefixResolver;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The name above each player's head: their rank prefix (the same LuckPerms, PlaceholderAPI or Vault prefix the tab
 * list and chat show), set in config.yml {@code NAMETAGS}.
 *
 * <p>Minecraft draws text around a name tag only from a scoreboard team's prefix and suffix, read from the scoreboard
 * the viewer's client uses. Every player who sees the sidebar has a private scoreboard ({@link ScoreboardManager}), so
 * each player's team is kept on the main scoreboard and on every one of those private boards. Scoreboards of other
 * plugins are never touched, and on the main scoreboard a player who is already in another plugin's team stays there
 * unless {@code OVERRIDE-OTHER-TEAMS} is on. Minecraft saves the main scoreboard with the world, so the teams are
 * taken off it at shutdown, and any left by a crash are cleared at start.
 *
 * <p>Players hidden with /hide get no team (the Hide feature owns their name tag), and nothing is done while TAB or
 * NametagEdit is installed, since they manage name tags themselves. Main thread only.
 */
public final class NametagManager {

    /** Every team of this class starts with this, followed by the player's whole UUID. */
    static final String TEAM_PREFIX = "smpnt";
    private static final List<String> NAMETAG_PLUGINS = List.of("TAB", "NametagEdit");

    /** What one player's team shows, and the name it holds. */
    record Tag(String team, String entry, String prefix, String suffix, ChatColor color) {
    }

    private final Supplier<FileConfiguration> config;
    private final Supplier<Scoreboard> mainBoard;
    private final Supplier<Collection<Scoreboard>> ownedBoards;
    private final Function<Player, String> rankPrefix;
    private final Predicate<Player> hidden;
    private final BooleanSupplier handledElsewhere;
    private final BiFunction<String, Player, String> colorize;
    private final Logger logger;
    private final Map<UUID, Tag> applied = new HashMap<>();
    private boolean warnedWrite;

    public NametagManager(SmpPlugin plugin) {
        this(() -> plugin.getConfigManager().getConfig(),
                () -> Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getMainScoreboard(),
                () -> plugin.getScoreboardManager() == null ? List.of() : plugin.getScoreboardManager().ownedBoards(),
                RolePrefixResolver::resolve,
                player -> plugin.getHideManager() != null && plugin.getHideManager().isHidden(player.getUniqueId()),
                handledElsewhere(plugin),
                (text, player) -> ColorUtils.colorize(text, player),
                plugin.getLogger());
    }

    NametagManager(Supplier<FileConfiguration> config, Supplier<Scoreboard> mainBoard,
                   Supplier<Collection<Scoreboard>> ownedBoards, Function<Player, String> rankPrefix,
                   Predicate<Player> hidden, BooleanSupplier handledElsewhere,
                   BiFunction<String, Player, String> colorize) {
        this(config, mainBoard, ownedBoards, rankPrefix, hidden, handledElsewhere, colorize,
                Logger.getLogger(NametagManager.class.getName()));
    }

    private NametagManager(Supplier<FileConfiguration> config, Supplier<Scoreboard> mainBoard,
                           Supplier<Collection<Scoreboard>> ownedBoards, Function<Player, String> rankPrefix,
                           Predicate<Player> hidden, BooleanSupplier handledElsewhere,
                           BiFunction<String, Player, String> colorize, Logger logger) {
        this.config = config;
        this.mainBoard = mainBoard;
        this.ownedBoards = ownedBoards;
        this.rankPrefix = rankPrefix;
        this.hidden = hidden;
        this.handledElsewhere = handledElsewhere;
        this.colorize = colorize;
        this.logger = logger;
    }

    /** True on Folia (no scoreboard teams there) or while another name tag plugin is enabled; says so once. */
    private static BooleanSupplier handledElsewhere(SmpPlugin plugin) {
        AtomicBoolean told = new AtomicBoolean();
        return () -> {
            if (plugin.getSpigotScheduler() != null && plugin.getSpigotScheduler().isFolia()) {
                return true;
            }
            for (String name : NAMETAG_PLUGINS) {
                if (Bukkit.getPluginManager().isPluginEnabled(name)) {
                    if (told.compareAndSet(false, true)) {
                        plugin.getLogger().info("Name tags: " + name + " is installed and draws the names above heads, so"
                                + " SmpPlugin leaves them to it (NAMETAGS in config.yml).");
                    }
                    return true;
                }
            }
            return false;
        };
    }

    public boolean isEnabled() {
        FileConfiguration c = config.get();
        return (c == null || c.getBoolean("NAMETAGS.ENABLED", true)) && !handledElsewhere.getAsBoolean();
    }

    /** How often the periodic pass runs, in ticks (NAMETAGS.UPDATE-TICKS, at least 10). */
    public long updateTicks() {
        FileConfiguration c = config.get();
        return Math.max(10L, c == null ? 40L : c.getLong("NAMETAGS.UPDATE-TICKS", 40L));
    }

    /** At start: clears teams a crash left on the main scoreboard, then sets everyone online. */
    public void start(Collection<? extends Player> online) {
        Scoreboard main = mainBoard.get();
        if (main != null) {
            removeOurTeams(main);
        }
        applied.clear();
        refreshAll(online);
    }

    /** After /smpplugin reload: the settings may have changed, so every team is set again (or removed). */
    public void reload(Collection<? extends Player> online) {
        if (!isEnabled()) {
            shutdown();
            return;
        }
        applied.clear();
        refreshAll(online);
    }

    /** The periodic pass: picks up rank changes, /hide and new players. Writes only what changed. */
    public void refreshAll(Collection<? extends Player> online) {
        for (Player player : online) {
            refresh(player);
        }
    }

    /** Sets (or removes) one player's name tag on every board. */
    public void refresh(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Tag tag = isEnabled() && !hidden.test(player) ? tagFor(player) : null;
        if (tag == null) {
            if (applied.remove(uuid) != null) {
                removeEverywhere(uuid);
            }
            return;
        }
        if (tag.equals(applied.get(uuid))) {
            // Unchanged. Only the main board can drift: another plugin's team may have let the player go meanwhile.
            Scoreboard main = mainBoard.get();
            Team current = main == null ? null : main.getEntryTeam(player.getName());
            if (main != null && (current == null || !current.getName().equals(tag.team()))) {
                apply(main, player.getName(), tag, respectOtherTeams());
            }
            return;
        }
        applied.put(uuid, tag);
        Scoreboard main = mainBoard.get();
        for (Scoreboard board : boards()) {
            apply(board, player.getName(), tag, board == main && respectOtherTeams());
        }
    }

    /** A new private board (ScoreboardManager just made one): it gets every online player's team. */
    public void applyAllTo(Scoreboard board) {
        if (board == null || !isEnabled()) {
            return;
        }
        for (Tag tag : applied.values()) {
            apply(board, tag.entry(), tag, false);
        }
    }

    /** A player left: their team goes from every board. */
    public void remove(Player player) {
        if (player != null && applied.remove(player.getUniqueId()) != null) {
            removeEverywhere(player.getUniqueId());
        }
    }

    /** At shutdown (or when switched off): every team of this class goes from every board. */
    public void shutdown() {
        for (Scoreboard board : boards()) {
            removeOurTeams(board);
        }
        applied.clear();
    }

    // ------------------------------------------------------------------ the name tag itself

    Tag tagFor(Player player) {
        FileConfiguration c = config.get();
        String rank = Objects.requireNonNullElse(rankPrefix.apply(player), "");
        String prefix = render(c == null ? "{prefix}" : c.getString("NAMETAGS.PREFIX", "{prefix}"), player, rank);
        String suffix = render(c == null ? "" : c.getString("NAMETAGS.SUFFIX", ""), player, rank);
        ChatColor color = nameColor(c == null ? "WHITE" : c.getString("NAMETAGS.NAME-COLOR", "WHITE"), prefix);
        return new Tag(teamName(player.getUniqueId()), player.getName(), prefix, suffix, color);
    }

    private String render(String template, Player player, String rank) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        String filled = template.replace("{prefix}", rank).replace("{player}", player.getName());
        return Objects.requireNonNullElse(colorize.apply(filled, player), "");
    }

    /**
     * The whole UUID, so two players can never share a team (a shortened one can). Team names are no longer limited to
     * 16 characters since Minecraft 1.18, and the plugin needs 1.21.11.
     */
    static String teamName(UUID uuid) {
        return TEAM_PREFIX + uuid.toString().replace("-", "");
    }

    /**
     * The colour of the name itself (Minecraft colours a name tag by its team colour): a colour name such as WHITE or
     * GOLD, or AUTO for the last colour used in the prefix. Anything else is white.
     */
    static ChatColor nameColor(String setting, String prefix) {
        String wanted = setting == null ? "WHITE" : setting.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (wanted.equals("AUTO")) {
            return lastColor(prefix == null ? "" : prefix);
        }
        try {
            ChatColor color = ChatColor.valueOf(wanted);
            return color.isColor() ? color : ChatColor.WHITE;
        } catch (IllegalArgumentException unknown) {
            return ChatColor.WHITE;
        }
    }

    /** The last colour in a legacy text (§c, or §x§r§r§g§g§b§b hex, which becomes the nearest of the 16 colours). */
    private static ChatColor lastColor(String legacy) {
        ChatColor found = ChatColor.WHITE;
        for (int i = 0; i + 1 < legacy.length(); i++) {
            if (legacy.charAt(i) != ChatColor.COLOR_CHAR) {
                continue;
            }
            char code = Character.toLowerCase(legacy.charAt(i + 1));
            if (code == 'x' && i + 13 < legacy.length()) {
                StringBuilder hex = new StringBuilder(6);
                for (int digit = 0; digit < 6; digit++) {
                    hex.append(legacy.charAt(i + 3 + digit * 2));
                }
                try {
                    found = nearest(Integer.parseInt(hex.toString(), 16));
                } catch (NumberFormatException ignored) {
                    // Not a hex colour after all.
                }
                i += 13;
                continue;
            }
            ChatColor color = ChatColor.getByChar(code);
            if (color != null && color.isColor()) {
                found = color;
            }
        }
        return found;
    }

    private static final int[] LEGACY_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
    private static final String LEGACY_CODES = "0123456789abcdef";

    private static ChatColor nearest(int rgb) {
        int best = 15;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < LEGACY_RGB.length; i++) {
            int dr = ((rgb >> 16) & 0xFF) - ((LEGACY_RGB[i] >> 16) & 0xFF);
            int dg = ((rgb >> 8) & 0xFF) - ((LEGACY_RGB[i] >> 8) & 0xFF);
            int db = (rgb & 0xFF) - (LEGACY_RGB[i] & 0xFF);
            long distance = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return ChatColor.getByChar(LEGACY_CODES.charAt(best));
    }

    // ------------------------------------------------------------------ boards and teams

    private boolean respectOtherTeams() {
        FileConfiguration c = config.get();
        return c == null || !c.getBoolean("NAMETAGS.OVERRIDE-OTHER-TEAMS", false);
    }

    /** The main scoreboard and the plugin's own sidebar boards, each once. */
    private Set<Scoreboard> boards() {
        Set<Scoreboard> boards = Collections.newSetFromMap(new IdentityHashMap<>());
        Scoreboard main = mainBoard.get();
        if (main != null) {
            boards.add(main);
        }
        Collection<Scoreboard> owned = ownedBoards.get();
        if (owned != null) {
            for (Scoreboard board : owned) {
                if (board != null) {
                    boards.add(board);
                }
            }
        }
        return boards;
    }

    /** Puts one player's name into their team on one board. False when another plugin's team keeps them. */
    private boolean apply(Scoreboard board, String entry, Tag tag, boolean respectOthers) {
        try {
            Team current = board.getEntryTeam(entry);
            if (current != null && !current.getName().equals(tag.team())
                    && !current.getName().startsWith(TEAM_PREFIX) && respectOthers) {
                return false;
            }
            Team team = board.getTeam(tag.team());
            if (team == null) {
                team = board.registerNewTeam(tag.team());
            }
            if (!tag.prefix().equals(team.getPrefix())) {
                team.setPrefix(tag.prefix());
            }
            if (!tag.suffix().equals(team.getSuffix())) {
                team.setSuffix(tag.suffix());
            }
            if (team.getColor() != tag.color()) {
                team.setColor(tag.color());
            }
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
            return true;
        } catch (RuntimeException failure) {
            if (!warnedWrite) {
                warnedWrite = true;
                logger.log(Level.WARNING, "Could not set the name tag of " + entry + " (prefix \"" + tag.prefix()
                        + "\"); check NAMETAGS in config.yml.", failure);
            }
            return false;
        }
    }

    private void removeEverywhere(UUID uuid) {
        String name = teamName(uuid);
        for (Scoreboard board : boards()) {
            Team team = board.getTeam(name);
            if (team != null) {
                team.unregister();
            }
        }
    }

    private static void removeOurTeams(Scoreboard board) {
        for (Team team : new LinkedHashSet<>(board.getTeams())) {
            if (team.getName().startsWith(TEAM_PREFIX)) {
                team.unregister();
            }
        }
    }
}
