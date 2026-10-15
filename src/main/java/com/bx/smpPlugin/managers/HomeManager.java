package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.utils.PermissionUtils;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.menus.HomeMenu;
import com.bx.smpPlugin.models.Home;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

public class HomeManager {

    private static final int HOMES_PER_PAGE = 5;
    private static final int MAX_PERMISSION_VALUE = 100;
    /** Letters, digits, _ and -, up to 16 characters: fits a menu title and a command argument. */
    private static final Pattern HOME_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,16}$");

    private final SmpPlugin plugin;
    /** UUID → list of homes */
    private final Map<UUID, List<Home>> cache = new ConcurrentHashMap<>();
    private final Map<UUID, PendingHomeInput> pendingInputs = new ConcurrentHashMap<>();

    public HomeManager(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    /** Uses the rows read at login when there are any, so the join does not touch the database. */
    public void loadHomes(Player player) {
        UUID uuid = player.getUniqueId();
        List<Home> homes = plugin.getJoinPreloadListener() == null
                ? null
                : plugin.getJoinPreloadListener().takeHomes(uuid);
        if (homes == null) {
            homes = plugin.getDatabaseManager().loadHomes(uuid);
        }
        cache.put(uuid, new CopyOnWriteArrayList<>(homes == null ? List.of() : homes));
    }

    /** Reads someone else's homes, live when they are online. Runs on the database thread. */
    public List<Home> loadHomesFor(UUID uuid) {
        List<Home> live = cache.get(uuid);
        return live != null ? List.copyOf(live) : plugin.getDatabaseManager().loadHomes(uuid);
    }

    public void unloadHomes(UUID uuid) {
        cache.remove(uuid);
        pendingInputs.remove(uuid);
    }

    public void clearAllCaches() {
        cache.clear();
        pendingInputs.clear();
    }

    public List<Home> getHomes(UUID uuid) {
        return cache.getOrDefault(uuid, Collections.emptyList());
    }

    public Home getHome(UUID uuid, String name) {
        for (Home h : getHomes(uuid)) {
            if (h.getName().equalsIgnoreCase(name)) return h;
        }
        return null;
    }

    public int getHomeCount(UUID uuid) {
        return getHomes(uuid).size();
    }

    public int getMaxHomes(Player player) {
        int defaultHomes = Math.max(1, plugin.getConfigManager().getConfig().getInt("SETTINGS.HOME-DEFAULT", 5));
        int homesByAmountPermission = PermissionUtils.resolveHighestExactNumberedPermission(
                player, "smpplugin.homes.", MAX_PERMISSION_VALUE);
        int pagesByPermission = PermissionUtils.resolveHighestExactNumberedPermission(
                player, "smpplugin.homes.page.", MAX_PERMISSION_VALUE);
        int homesByPagePermission = pagesByPermission * HOMES_PER_PAGE;
        int permissionHomes = Math.max(homesByAmountPermission, homesByPagePermission);

        // Rank tiers from config.yml, e.g. "group.vip: 10", so ranks need no numbered nodes.
        org.bukkit.configuration.ConfigurationSection tiers = plugin.getConfigManager().getConfig()
                .getConfigurationSection("SETTINGS.HOME-PERMISSIONS");
        if (tiers != null) {
            for (String node : tiers.getKeys(true)) {
                if (tiers.isInt(node) && PermissionUtils.hasExact(player, node)) {
                    permissionHomes = Math.max(permissionHomes, Math.min(MAX_PERMISSION_VALUE, tiers.getInt(node)));
                }
            }
        }

        return permissionHomes > 0 ? permissionHomes : defaultHomes;
    }

    public int getMaxHomePages(Player player) {
        int maxHomes = getMaxHomes(player);
        return Math.max(1, (int) Math.ceil(maxHomes / (double) HOMES_PER_PAGE));
    }

    public boolean canSetHome(Player player) {
        return getHomeCount(player.getUniqueId()) < getMaxHomes(player);
    }

    public boolean setHome(Player player, String name) {
        return setHome(player.getUniqueId(), name, player.getLocation());
    }

    public boolean setHome(UUID uuid, String name, Location location) {
        List<Home> homes = cache.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>());
        // Update existing
        for (Home h : homes) {
            if (h.getName().equalsIgnoreCase(name)) {
                h.setLocation(location.clone());
                saveAsync(h);
                return true;
            }
        }
        // Create new
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null && homes.size() >= getMaxHomes(player)) return false;
        Home home = new Home(uuid, name, location.clone());
        homes.add(home);
        saveAsync(home);
        return true;
    }

    /**
     * The cache is what players see, so writes go to the database thread. They queue there in
     * order, which keeps a rename's delete ahead of its save.
     */
    private void saveAsync(Home home) {
        if (home.getLocation() == null) {
            return;
        }
        Home copy = new Home(home.getOwnerUuid(), home.getName(), home.getLocation().clone(), home.getCreatedAt());
        plugin.getDatabaseManager().executeAsync(() -> plugin.getDatabaseManager().saveHome(copy));
    }

    private void deleteAsync(UUID uuid, String name) {
        plugin.getDatabaseManager().executeAsync(() -> plugin.getDatabaseManager().deleteHome(uuid, name));
    }

    public boolean deleteHome(UUID uuid, String name) {
        List<Home> homes = cache.get(uuid);
        if (homes == null) return false;
        boolean removed = homes.removeIf(h -> h.getName().equalsIgnoreCase(name));
        if (removed) deleteAsync(uuid, name);
        return removed;
    }

    public boolean renameHome(UUID uuid, String oldName, String newName) {
        // Check newName not already taken
        if (getHome(uuid, newName) != null) return false;
        Home home = getHome(uuid, oldName);
        if (home == null) return false;
        deleteAsync(uuid, home.getName());
        home.setName(newName);
        saveAsync(home);
        return true;
    }

    public void promptCreateHome(Player player, Location location, String suggestedName) {
        pendingInputs.put(player.getUniqueId(), PendingHomeInput.create(location, suggestedName));
        player.closeInventory();
        player.sendMessage(ColorUtils.toComponent(
                plugin.getConfigManager().getMessage("HOME.NAME-PROMPT", "{name}", suggestedName)));
    }

    public void promptRenameHome(Player player, String oldName) {
        pendingInputs.put(player.getUniqueId(), PendingHomeInput.rename(oldName));
        player.closeInventory();
        player.sendMessage(ColorUtils.toComponent(
                plugin.getConfigManager().getMessage("HOME.RENAME-PROMPT", "{name}", oldName)));
    }

    public boolean hasPendingInput(UUID uuid) {
        return pendingInputs.containsKey(uuid);
    }

    public void handlePendingInput(Player player, String rawInput) {
        PendingHomeInput pending = pendingInputs.get(player.getUniqueId());
        if (pending == null) return;

        String input = rawInput == null ? "" : rawInput.trim();
        if (input.equalsIgnoreCase("cancel")) {
            pendingInputs.remove(player.getUniqueId());
            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("HOME.CANCELLED")));
            return;
        }

        if (!isValidHomeName(input)) {
            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("HOME.INVALID-NAME")));
            resendPrompt(player, pending);
            return;
        }

        if (pending.type == PendingHomeInput.Type.CREATE) {
            if (getHome(player.getUniqueId(), input) != null) {
                player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("HOME.ALREADY-EXISTS")));
                resendPrompt(player, pending);
                return;
            }
            if (!setHome(player.getUniqueId(), input, pending.location)) {
                pendingInputs.remove(player.getUniqueId());
                player.sendMessage(ColorUtils.toComponent("&cʏᴏᴜ ᴄᴀɴɴᴏᴛ ᴄʀᴇᴀᴛᴇ ᴀɴᴏᴛʜᴇʀ ʜᴏᴍᴇ ʀɪɢʜᴛ ɴᴏᴡ."));
                return;
            }

            pendingInputs.remove(player.getUniqueId());
            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("HOME.SET")));
            new HomeMenu(plugin).open(player);
            return;
        }

        if (!renameHome(player.getUniqueId(), pending.oldName, input)) {
            player.sendMessage(ColorUtils.toComponent("&cꜰᴀɪʟᴇᴅ ᴛᴏ ʀᴇɴᴀᴍᴇ ʜᴏᴍᴇ. ᴛʀʏ ᴀɴᴏᴛʜᴇʀ ɴᴀᴍᴇ."));
            resendPrompt(player, pending);
            return;
        }

        pendingInputs.remove(player.getUniqueId());
        player.sendMessage(ColorUtils.toComponent(
                plugin.getConfigManager().getMessage("HOME.RENAME-SUCCESS", "{name}", input)));
        new HomeMenu(plugin).open(player);
    }

    public static boolean isValidHomeName(String input) {
        return input != null && HOME_NAME.matcher(input).matches();
    }

    private void resendPrompt(Player player, PendingHomeInput pending) {
        if (pending.type == PendingHomeInput.Type.CREATE) {
            player.sendMessage(ColorUtils.toComponent(
                    plugin.getConfigManager().getMessage("HOME.NAME-PROMPT", "{name}", pending.suggestedName)));
            return;
        }

        player.sendMessage(ColorUtils.toComponent(
                plugin.getConfigManager().getMessage("HOME.RENAME-PROMPT", "{name}", pending.oldName)));
    }

    private static final class PendingHomeInput {
        private final Type type;
        private final String oldName;
        private final String suggestedName;
        private final Location location;

        private PendingHomeInput(Type type, String oldName, String suggestedName, Location location) {
            this.type = type;
            this.oldName = oldName;
            this.suggestedName = suggestedName;
            this.location = location == null ? null : location.clone();
        }

        private static PendingHomeInput create(Location location, String suggestedName) {
            return new PendingHomeInput(Type.CREATE, null, suggestedName, location);
        }

        private static PendingHomeInput rename(String oldName) {
            return new PendingHomeInput(Type.RENAME, oldName, null, null);
        }

        private enum Type {
            CREATE,
            RENAME
        }
    }
}
