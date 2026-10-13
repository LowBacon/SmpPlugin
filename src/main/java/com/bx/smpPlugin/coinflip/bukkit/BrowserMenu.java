package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.coinflip.CoinFlipConfig;
import com.bx.smpPlugin.coinflip.CoinFlipMatch;
import com.bx.smpPlugin.coinflip.CoinFlipService;
import com.bx.smpPlugin.coinflip.StakeMath;
import com.bx.smpPlugin.utils.SignInputUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Public match browser: paged, sortable, filterable and searchable. It is redrawn from live service state whenever a
 * match changes, and a join always re-checks the match on the server, so a stale page can never join a match that
 * is no longer open.
 */
public final class BrowserMenu extends CoinFlipMenu {

    enum Sort {
        NEWEST("Newest first", Comparator.comparingLong(CoinFlipMatch::createdAtMillis).reversed()),
        OLDEST("Oldest first", Comparator.comparingLong(CoinFlipMatch::createdAtMillis)),
        STAKE_HIGH("Highest stake", Comparator.comparingLong(CoinFlipMatch::stakeCents).reversed()),
        STAKE_LOW("Lowest stake", Comparator.comparingLong(CoinFlipMatch::stakeCents)),
        EXPIRING("Expiring soonest", Comparator.comparingLong(CoinFlipMatch::expiresAtMillis));

        final String label;
        final Comparator<CoinFlipMatch> order;

        Sort(String label, Comparator<CoinFlipMatch> order) {
            this.label = label;
            this.order = order.thenComparing(CoinFlipMatch::id);
        }
    }

    enum Filter {
        ALL("All"), JOINABLE("Joinable by me"), LOW("Low stakes"), MEDIUM("Medium stakes"), HIGH("High stakes"),
        FRIENDS("Friends"), MINE("My matches");

        final String label;

        Filter(String label) {
            this.label = label;
        }
    }

    private int page;
    private Sort sort = Sort.NEWEST;
    private Filter filter = Filter.ALL;
    private String search;
    private long lastRefreshMillis;

    public BrowserMenu(CoinFlipModule module, UUID viewer) {
        super(module, viewer, "Browse", 54);
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    protected void render(Player player) {
        CoinFlipConfig config = config();
        boolean readable = readable();
        long now = System.currentTimeMillis();
        long balance = StakeMath.fromEconomy(module.plugin().getEconomyManager().getBalance(player));
        List<CoinFlipMatch> all = module.service().publicListings();
        List<CoinFlipMatch> shown = new ArrayList<>();
        for (CoinFlipMatch match : all) {
            if (matches(match, balance) && matchesSearch(match)) {
                shown.add(match);
            }
        }
        shown.sort(sort.order);
        int perPage = Gui.INNER_54.length;
        int pages = Math.max(1, (shown.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));

        set(4, Gui.item(Material.SPYGLASS, Gui.INFO + "Open matches: " + Gui.VALUE + all.size(), List.of(
                Gui.line(readable, "Showing", shown.size() + " (" + filter.label + ")"),
                Gui.line(readable, "Sorted by", sort.label),
                Gui.line(readable, "Search", search == null ? "none" : search),
                Gui.line(readable, "Page", (page + 1) + " / " + pages))));

        if (shown.isEmpty()) {
            set(22, Gui.item(Material.STRUCTURE_VOID, Gui.MUTED + "No matches to show",
                    all.isEmpty() ? Gui.MUTED + "Nobody has an open match right now." : Gui.MUTED + "Try another filter or search.",
                    Gui.MUTED + "You can create one from the home menu."));
        }
        for (int i = 0; i < perPage; i++) {
            int index = page * perPage + i;
            if (index >= shown.size()) {
                break;
            }
            CoinFlipMatch match = shown.get(index);
            List<String> lore = Gui.matchLore(match, module, readable, now);
            lore.add(Gui.line(readable, "Created", Gui.duration(now - match.createdAtMillis()) + " ago"));
            lore.add(Gui.line(readable, "You would be", match.creatorSide().opposite().displayName()));
            String blocker = blocker(match, balance);
            lore.add("");
            lore.add(blocker == null ? Gui.GOOD + "Click to review and join" : Gui.BAD + blocker);
            String id = match.id();
            button(Gui.INNER_54[i], Gui.head(match.creatorId(), Gui.VALUE + match.creatorName() + Gui.DIM + " · " + Gui.GOLD
                    + money(match.stakeCents()), lore), () -> {
                if (match.creatorId().equals(viewer)) {
                    openMenu(new MyMatchesMenu(module, viewer));
                    return;
                }
                CoinFlipMatch live = module.service().findActive(id).orElse(null);
                if (live == null) {
                    message("match-not-found", CoinFlipService.vars("id", id));
                    refresh();
                    return;
                }
                if (prefs().quickConfirm() && can(CoinFlipModule.PERM_JOIN)) {
                    // The player turned on quick confirm in Settings; the server still checks every limit.
                    setBusy(true);
                    module.service().join(viewer, player.getName(), id).thenAccept(outcome -> {
                        setBusy(false);
                        if (!outcome.success()) {
                            report(outcome);
                            refresh();
                        }
                    });
                    return;
                }
                openMenu(new JoinConfirmMenu(module, viewer, id));
            });
        }

        if (page > 0) {
            button(45, Gui.item(Material.ARROW, Gui.INFO + "Previous page"), () -> {
                page--;
                refresh();
            });
        } else {
            back(() -> openMenu(new MainMenu(module, viewer)));
        }
        button(46, Gui.item(Material.HOPPER, Gui.INFO + "Sort: " + Gui.VALUE + sort.label, Gui.MUTED + "Click to change."), () -> {
            sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
            page = 0;
            refresh();
        });
        button(47, Gui.item(Material.COMPARATOR, Gui.INFO + "Filter: " + Gui.VALUE + filter.label,
                Gui.MUTED + "Low: under " + money(lowLimit(config)), Gui.MUTED + "High: from " + money(highLimit(config)),
                Gui.MUTED + "Click to change."), () -> {
            filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length];
            page = 0;
            refresh();
        });
        button(48, Gui.item(Material.OAK_SIGN, Gui.INFO + "Search", Gui.MUTED + "By player name or match ID.",
                Gui.MUTED + "Right-click to clear."), click -> {
            if (click.isRightClick()) {
                search = null;
                refresh();
            } else {
                promptSearch();
            }
        });
        button(49, Gui.item(Material.SUNFLOWER, Gui.INFO + "Refresh"), () -> {
            long nowMillis = System.currentTimeMillis();
            if (nowMillis - lastRefreshMillis < config.browserRefreshCooldownMillis()) {
                return;
            }
            lastRefreshMillis = nowMillis;
            refresh();
        });
        button(50, Gui.item(Material.BOOK, Gui.INFO + "My matches"), () -> openMenu(new MyMatchesMenu(module, viewer)));
        button(51, Gui.item(Material.WRITABLE_BOOK, Gui.INFO + "Create a match"),
                () -> openMenu(new CreateMenu(module, viewer, CreateMenu.Draft.initial(prefs(), config))));
        if (page < pages - 1) {
            button(53, Gui.item(Material.ARROW, Gui.INFO + "Next page"), () -> {
                page++;
                refresh();
            });
        }
    }

    private boolean matches(CoinFlipMatch match, long balance) {
        CoinFlipConfig config = config();
        return switch (filter) {
            case ALL -> true;
            case JOINABLE -> blocker(match, balance) == null;
            case LOW -> match.stakeCents() < lowLimit(config);
            case HIGH -> match.stakeCents() >= highLimit(config);
            case MEDIUM -> match.stakeCents() >= lowLimit(config) && match.stakeCents() < highLimit(config);
            case FRIENDS -> module.plugin().getFriendsManager() != null
                    && module.plugin().getFriendsManager().isFriend(viewer, match.creatorId());
            case MINE -> match.creatorId().equals(viewer);
        };
    }

    private boolean matchesSearch(CoinFlipMatch match) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String needle = search.toLowerCase(Locale.ROOT);
        return match.creatorName().toLowerCase(Locale.ROOT).contains(needle)
                || match.id().equalsIgnoreCase(CoinFlipService.normalizeId(search));
    }

    /** Why the viewer cannot join, or null. Display only; the service decides. */
    private String blocker(CoinFlipMatch match, long balance) {
        if (match.creatorId().equals(viewer)) {
            return "Your own match";
        }
        if (!can(CoinFlipModule.PERM_JOIN)) {
            return "You cannot join matches";
        }
        if (!module.service().wageringOpen()) {
            return "Wagering is paused";
        }
        if (balance < match.stakeCents()) {
            return "Needs " + money(match.stakeCents());
        }
        return null;
    }

    private static long lowLimit(CoinFlipConfig config) {
        return Math.min(config.maxStakeCents(), config.minStakeCents() * 10L);
    }

    private static long highLimit(CoinFlipConfig config) {
        return Math.max(lowLimit(config), config.maxStakeCents() / 10L);
    }

    private void promptSearch() {
        Player player = Bukkit.getPlayer(viewer);
        if (player == null) {
            return;
        }
        message("search-prompt", Map.of());
        SignInputUtil.open(module.plugin(), player, List.of("", "^^^^^^^^^^", "Player or ID", ""), 0, text -> {
            Player current = Bukkit.getPlayer(viewer);
            if (current == null) {
                return;
            }
            String cleaned = text == null ? "" : text.trim();
            search = cleaned.isEmpty() || cleaned.equalsIgnoreCase("cancel") ? null
                    : cleaned.replaceAll("[^A-Za-z0-9_#]", "").substring(0, Math.min(16, cleaned.replaceAll("[^A-Za-z0-9_#]", "").length()));
            page = 0;
            open(current);
        });
    }
}
