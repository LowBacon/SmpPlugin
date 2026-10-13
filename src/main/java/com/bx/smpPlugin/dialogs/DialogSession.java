package com.bx.smpPlugin.dialogs;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The scratch state a player accumulates while walking through dialogs: who they picked, what
 * they typed, which filter is active.
 *
 * <p>None of this is worth persisting — it is rebuilt the moment the player opens a screen again
 * — so it lives in memory and is dropped on quit. The pay and stats shortlists are the one part
 * players notice, and they are capped so a session cannot grow without bound.
 */
public final class DialogSession {

    /** How the friends list is filtered. */
    public enum FriendFilter {
        ALL, FRIENDS, FOLLOWING, FOLLOWERS;

        public FriendFilter next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** A remembered player, kept by uuid so a rename does not orphan the entry. */
    public record Shortcut(UUID uuid, String name) {
    }

    private static final int MAX_SHORTCUTS = 12;
    private static final int MAX_ICON_RESULTS = 32;
    private static final int MAX_COMMAND_BUTTONS = 64;

    private final Map<UUID, Shortcut> payList = new LinkedHashMap<>();
    private final Map<UUID, Shortcut> statsList = new LinkedHashMap<>();
    private final Map<UUID, Shortcut> teleportList = new LinkedHashMap<>();
    private final Deque<String> iconResults = new ArrayDeque<>();

    private final Map<String, String> commandsById = new LinkedHashMap<>();
    private final Map<String, String> idsByCommand = new LinkedHashMap<>();

    private FriendFilter friendFilter = FriendFilter.ALL;
    private String friendSearch;
    private String overlayReturnAction;
    private UUID payTarget;
    private UUID teleportTarget;
    private double payAmount;
    private String statsTarget;
    private String homeSlot;
    private String iconQuery;
    private List<Shortcut> searchResults = List.of();
    private int quickBuySlot = -1;
    private org.bukkit.Material quickBuyMaterial;
    private final Map<org.bukkit.enchantments.Enchantment, Integer> quickBuyEnchantments = new LinkedHashMap<>();

    public int getQuickBuySlot() {
        return quickBuySlot;
    }

    public org.bukkit.Material getQuickBuyMaterial() {
        return quickBuyMaterial;
    }

    public Map<org.bukkit.enchantments.Enchantment, Integer> getQuickBuyEnchantments() {
        return quickBuyEnchantments;
    }

    public void startQuickBuy(int slot, org.bukkit.Material material) {
        this.quickBuySlot = slot;
        this.quickBuyMaterial = material;
        this.quickBuyEnchantments.clear();
    }

    public void setQuickBuyEnchantment(org.bukkit.enchantments.Enchantment ench, int level) {
        if (ench == null) return;
        if (level <= 0) {
            quickBuyEnchantments.remove(ench);
            return;
        }
        quickBuyEnchantments.keySet().removeIf(existing -> !existing.equals(ench) && (ench.conflictsWith(existing) || existing.conflictsWith(ench)));
        quickBuyEnchantments.put(ench, level);
    }

    public void toggleQuickBuyEnchantment(org.bukkit.enchantments.Enchantment ench, int level) {
        if (ench == null) return;
        Integer current = quickBuyEnchantments.get(ench);
        if (current != null && current == level) {
            quickBuyEnchantments.remove(ench);
        } else {
            setQuickBuyEnchantment(ench, level);
        }
    }

    public void clearQuickBuy() {
        this.quickBuySlot = -1;
        this.quickBuyMaterial = null;
        this.quickBuyEnchantments.clear();
    }

    public void clearQuickBuyEnchantments() {
        this.quickBuyEnchantments.clear();
    }

    public FriendFilter getFriendFilter() {
        return friendFilter;
    }

    public void setFriendFilter(FriendFilter friendFilter) {
        this.friendFilter = friendFilter == null ? FriendFilter.ALL : friendFilter;
    }

    public FriendFilter cycleFriendFilter() {
        friendFilter = friendFilter.next();
        return friendFilter;
    }

    public String getFriendSearch() {
        return friendSearch;
    }

    public void setFriendSearch(String friendSearch) {
        this.friendSearch = friendSearch == null || friendSearch.isBlank() ? null : friendSearch.trim();
    }

    /**
     * Action id to reopen when Pay / Stats / Teleport was launched from another screen
     * (Friends details). Null means each of those screens uses its own list as Back.
     */
    public String overlayReturnAction() {
        return overlayReturnAction;
    }

    public void setOverlayReturnAction(String overlayReturnAction) {
        this.overlayReturnAction = overlayReturnAction == null || overlayReturnAction.isBlank()
                ? null
                : overlayReturnAction;
    }

    public String namespacedOverlayReturn() {
        if (overlayReturnAction == null) {
            return null;
        }
        return overlayReturnAction.indexOf(':') >= 0
                ? overlayReturnAction
                : DialogActions.NAMESPACE + ':' + overlayReturnAction;
    }

    public UUID getPayTarget() {
        return payTarget;
    }

    public void setPayTarget(UUID payTarget) {
        this.payTarget = payTarget;
    }

    public UUID getTeleportTarget() {
        return teleportTarget;
    }

    public void setTeleportTarget(UUID teleportTarget) {
        this.teleportTarget = teleportTarget;
    }

    public double getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(double payAmount) {
        this.payAmount = payAmount;
    }

    public String getStatsTarget() {
        return statsTarget;
    }

    public void setStatsTarget(String statsTarget) {
        this.statsTarget = statsTarget;
    }

    public String getHomeSlot() {
        return homeSlot;
    }

    public void setHomeSlot(String homeSlot) {
        this.homeSlot = homeSlot;
    }

    public String getIconQuery() {
        return iconQuery;
    }

    public void setIconQuery(String iconQuery) {
        this.iconQuery = iconQuery;
    }

    public List<Shortcut> getSearchResults() {
        return searchResults;
    }

    public void setSearchResults(List<Shortcut> searchResults) {
        this.searchResults = searchResults == null ? List.of() : List.copyOf(searchResults);
    }

    /**
     * Assigns a short id to a command so a dialog button can carry it.
     *
     * <p>A command cannot live inside a resource location — arguments bring spaces and capitals
     * that get stripped — so the button holds an id and the command is looked up when it is
     * clicked. The same command always gets the same id, and the oldest is dropped once the map
     * is full, which only a player who opens dozens of distinct screens would ever reach.
     */
    public String registerCommand(String command) {
        if (command == null || command.isBlank()) {
            return null;
        }
        String existing = idsByCommand.get(command);
        if (existing != null) {
            return existing;
        }
        String id = Integer.toHexString(commandsById.size() + 1);
        while (commandsById.containsKey(id)) {
            id = id + "_";
        }
        commandsById.put(id, command);
        idsByCommand.put(command, id);
        while (commandsById.size() > MAX_COMMAND_BUTTONS) {
            String oldest = commandsById.keySet().iterator().next();
            idsByCommand.remove(commandsById.remove(oldest));
        }
        return id;
    }

    public String commandFor(String id) {
        return id == null ? null : commandsById.get(id);
    }

    public List<Shortcut> getPayList() {
        return List.copyOf(payList.values());
    }

    public void rememberPay(UUID uuid, String name) {
        remember(payList, uuid, name);
    }

    public void forgetPay(UUID uuid) {
        payList.remove(uuid);
    }

    public List<Shortcut> getStatsList() {
        return List.copyOf(statsList.values());
    }

    public void rememberStats(UUID uuid, String name) {
        remember(statsList, uuid, name);
    }

    public List<Shortcut> getTeleportList() {
        return List.copyOf(teleportList.values());
    }

    public void rememberTeleport(UUID uuid, String name) {
        remember(teleportList, uuid, name);
    }

    public void forgetTeleport(UUID uuid) {
        teleportList.remove(uuid);
    }

    public List<String> getIconResults() {
        return List.copyOf(iconResults);
    }

    public void setIconResults(List<String> results) {
        iconResults.clear();
        if (results == null) {
            return;
        }
        for (String result : results) {
            if (iconResults.size() >= MAX_ICON_RESULTS) {
                break;
            }
            iconResults.add(result);
        }
    }

    private static void remember(Map<UUID, Shortcut> target, UUID uuid, String name) {
        if (uuid == null) {
            return;
        }
        // Re-inserting moves the entry to the end, so the oldest shortcut is the one evicted.
        target.remove(uuid);
        target.put(uuid, new Shortcut(uuid, name == null ? uuid.toString() : name));
        while (target.size() > MAX_SHORTCUTS) {
            UUID oldest = target.keySet().iterator().next();
            target.remove(oldest);
        }
    }

    /** Session store keyed by player, cleared on quit. */
    public static final class Store {

        private final Map<UUID, DialogSession> sessions = new ConcurrentHashMap<>();

        public DialogSession get(UUID uuid) {
            return sessions.computeIfAbsent(uuid, ignored -> new DialogSession());
        }

        public void remove(UUID uuid) {
            sessions.remove(uuid);
        }

        public void clear() {
            sessions.clear();
        }
    }
}
