package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.coinflip.CoinFlipConfig;
import com.bx.smpPlugin.coinflip.CoinFlipMatch;
import com.bx.smpPlugin.coinflip.CoinFlipService;
import com.bx.smpPlugin.coinflip.CoinSide;
import com.bx.smpPlugin.coinflip.StakeMath;
import com.bx.smpPlugin.utils.SignInputUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The CoinFlip screen since 3.1: everything a player needs on one page.
 *
 * <ul>
 *   <li>The middle shows every open flip as the creator's head. Click one to arm it, click it again
 *       to join. Your own flips are marked; clicking one twice cancels it.</li>
 *   <li>The bottom row picks your side and creates a flip at one of the quick stakes (again: click
 *       to arm, click again to create), or at any amount through the sign.</li>
 *   <li>History, statistics, leaderboards, settings and the rules are one click away under "More".</li>
 * </ul>
 * The second click replaces the old review and confirm screens, so a flip takes two clicks
 * instead of four screens; the stake, pot, fee and payout are on the item before you click. The
 * service still checks every limit and balance on the server when the action runs.
 */
public final class QuickMenu extends CoinFlipMenu {

    private static final int[] STAKE_SLOTS = {46, 47, 48, 50, 51};
    private static final int SIDE_SLOT = 45;
    private static final int CUSTOM_SLOT = 49;
    private static final int MORE_SLOT = 52;
    private static final int CLOSE_SLOT = 53;
    private static final long ARM_MILLIS = 4_000L;

    private CoinSide side;
    private boolean randomSide;
    private int armedSlot = -1;
    private long armedAt;

    public QuickMenu(CoinFlipModule module, UUID viewer) {
        super(module, viewer, "CoinFlip", 54);
        CoinSide saved = module.service().preferences(viewer).savedSide();
        this.side = saved == null ? CoinSide.HEADS : saved;
    }

    @Override
    public boolean live() {
        return true;
    }

    private boolean armed(int slot) {
        return armedSlot == slot && System.currentTimeMillis() - armedAt <= ARM_MILLIS;
    }

    /** First click arms the slot and returns false; a second click within a few seconds returns true. */
    private boolean confirmClick(int slot) {
        if (armed(slot)) {
            armedSlot = -1;
            return true;
        }
        armedSlot = slot;
        armedAt = System.currentTimeMillis();
        sound("CLICK");
        refresh();
        return false;
    }

    @Override
    protected void render(Player player) {
        CoinFlipConfig config = config();
        boolean readable = readable();
        boolean open = module.service().wageringOpen();
        long now = System.currentTimeMillis();
        long balance = StakeMath.fromEconomy(module.plugin().getEconomyManager().getBalance(player));

        List<String> head = new ArrayList<>();
        head.add(Gui.line(readable, "Balance", money(balance)));
        head.add(Gui.line(readable, "Stakes", money(config.minStakeCents()) + " to " + money(config.maxStakeCents())));
        head.add(Gui.line(readable, "Fee", config.feeBasisPoints() == 0 ? "none" : StakeMath.percent(config.feeBasisPoints()) + " of the pot"));
        head.add("");
        head.add(Gui.MUTED + "Every flip is a fair 50/50, drawn");
        head.add(Gui.MUTED + "by the server once both stakes are in.");
        if (!open) {
            head.add("");
            head.add(Gui.BAD + "Wagering is paused right now.");
        }
        set(4, Gui.head(viewer, Gui.GOLD + player.getName(), head));

        // ---- open flips
        List<CoinFlipMatch> listings = new ArrayList<>(module.service().publicListings());
        for (CoinFlipMatch mine : module.service().matchesOf(viewer)) {
            if (mine.creatorId().equals(viewer) && !mine.isPrivate() && mine.state() == com.bx.smpPlugin.coinflip.MatchState.WAITING
                    && !listings.contains(mine)) {
                listings.add(mine);
            }
        }
        // Your own flips first, then the biggest stakes.
        listings.sort(Comparator.comparing((CoinFlipMatch match) -> !match.creatorId().equals(viewer))
                .thenComparing(Comparator.comparingLong(CoinFlipMatch::stakeCents).reversed())
                .thenComparing(CoinFlipMatch::id));
        int shown = Math.min(listings.size(), Gui.INNER_54.length);
        boolean more = listings.size() > Gui.INNER_54.length;
        if (more) {
            shown = Gui.INNER_54.length - 1;
        }
        for (int i = 0; i < shown; i++) {
            CoinFlipMatch match = listings.get(i);
            int slot = Gui.INNER_54[i];
            boolean own = match.creatorId().equals(viewer);
            List<String> lore = new ArrayList<>();
            lore.add(Gui.line(readable, "Stake", money(match.stakeCents()) + " each"));
            lore.add(Gui.line(readable, "Winner gets", money(match.payoutCents())));
            lore.add(Gui.line(readable, own ? "Your side" : "You would be", own ? match.creatorSide().displayName()
                    : match.creatorSide().opposite().displayName()));
            lore.add(Gui.line(readable, "Expires in", Gui.duration(Math.max(0L, match.expiresAtMillis() - now))));
            lore.add("");
            String blocker = own ? null : blocker(match, balance);
            if (own) {
                lore.add(armed(slot) ? Gui.BAD + "Click again to cancel it" : Gui.MUTED + "Your flip. Click twice to cancel.");
            } else if (blocker != null) {
                lore.add(Gui.BAD + blocker);
            } else {
                lore.add(armed(slot) ? Gui.GOOD + "&lClick again to flip for " + money(match.stakeCents())
                        : Gui.GOOD + "Click to join");
            }
            String title = (own ? Gui.INFO + "Your flip" : Gui.VALUE + match.creatorName()) + Gui.DIM + " · " + Gui.GOLD + money(match.stakeCents());
            String id = match.id();
            button(slot, Gui.glow(Gui.head(match.creatorId(), title, lore), armed(slot)), () -> {
                if (own) {
                    if (confirmClick(slot)) {
                        setBusy(true);
                        module.service().cancel(viewer, id).thenAccept(outcome -> {
                            setBusy(false);
                            report(outcome);
                            refresh();
                        });
                    }
                    return;
                }
                if (blocker(match, StakeMath.fromEconomy(module.plugin().getEconomyManager().getBalance(player))) != null) {
                    sound("ERROR");
                    return;
                }
                if (confirmClick(slot)) {
                    join(player, id);
                }
            });
        }
        if (more) {
            int slot = Gui.INNER_54[Gui.INNER_54.length - 1];
            button(slot, Gui.item(Material.SPYGLASS, Gui.INFO + "+" + (listings.size() - shown) + " more flips",
                    Gui.MUTED + "Open the full list with", Gui.MUTED + "sorting and search."), () -> openMenu(new BrowserMenu(module, viewer)));
        }
        if (listings.isEmpty()) {
            set(22, Gui.item(Material.STRUCTURE_VOID, Gui.MUTED + "No open flips right now",
                    Gui.MUTED + "Start one with a stake below,", Gui.MUTED + "or type " + Gui.VALUE + "/cf <amount>"));
        }

        // ---- create
        boolean canCreate = open && can(CoinFlipModule.PERM_CREATE);
        String sideName = randomSide ? "Random" : side.displayName();
        button(SIDE_SLOT, Gui.item(randomSide ? Material.ENDER_EYE : Gui.sideMaterial(side, config.headsMaterial(), config.tailsMaterial()),
                Gui.INFO + "Your side: " + Gui.sideColour(randomSide ? CoinSide.HEADS : side) + sideName,
                Gui.MUTED + "Click to switch between", Gui.MUTED + "heads, tails and random."), () -> {
            if (randomSide) {
                randomSide = false;
                side = CoinSide.HEADS;
            } else if (side == CoinSide.HEADS) {
                side = CoinSide.TAILS;
            } else {
                randomSide = true;
            }
            refresh();
        });
        List<Long> stakes = config.incrementsCents().stream()
                .filter(cents -> cents >= config.minStakeCents() && cents <= config.maxStakeCents()).limit(STAKE_SLOTS.length).toList();
        Material[] icons = {Material.GOLD_NUGGET, Material.RAW_GOLD, Material.GOLD_INGOT, Material.GOLD_BLOCK, Material.DIAMOND_BLOCK};
        for (int i = 0; i < STAKE_SLOTS.length; i++) {
            int slot = STAKE_SLOTS[i];
            if (i >= stakes.size()) {
                continue;
            }
            long stake = stakes.get(i);
            boolean affordable = balance >= stake;
            List<String> lore = new ArrayList<>();
            lore.add(Gui.line(readable, "Pot", money(StakeMath.pot(stake))));
            lore.add(Gui.line(readable, "Winner gets", money(StakeMath.payout(stake, config.feeBasisPoints()))));
            lore.add(Gui.line(readable, "Your side", sideName));
            lore.add("");
            if (!canCreate) {
                lore.add(Gui.BAD + (open ? "You cannot create flips." : "Wagering is paused."));
            } else if (!affordable) {
                lore.add(Gui.BAD + "You need " + money(stake) + ".");
            } else {
                lore.add(armed(slot) ? Gui.GOOD + "&lClick again to create" : Gui.GOOD + "Click to create a flip");
            }
            button(slot, Gui.glow(Gui.item(affordable && canCreate ? icons[i] : Material.GRAY_DYE,
                    Gui.GOLD + "Flip for " + money(stake), lore), armed(slot)), () -> {
                if (!canCreate || balance < stake) {
                    sound("ERROR");
                    return;
                }
                if (confirmClick(slot)) {
                    create(player, stake);
                }
            });
        }
        button(CUSTOM_SLOT, Gui.item(Material.OAK_SIGN, Gui.INFO + "Another amount",
                Gui.MUTED + "Type any stake on a sign.", Gui.MUTED + "Or in chat: " + Gui.VALUE + "/cf <amount> [heads|tails]"), () -> {
            if (!canCreate) {
                sound("ERROR");
                return;
            }
            promptAmount(player);
        });
        button(MORE_SLOT, Gui.item(Material.BOOK, Gui.INFO + "More",
                Gui.MUTED + "Challenges, your flips, history,", Gui.MUTED + "statistics, leaderboards, settings",
                Gui.MUTED + "and the rules."), () -> openMenu(new MainMenu(module, viewer)));
        button(CLOSE_SLOT, Gui.item(Material.BARRIER, Gui.BAD + "Close"), () -> close());
    }

    private String blocker(CoinFlipMatch match, long balance) {
        if (!can(CoinFlipModule.PERM_JOIN)) {
            return "You cannot join flips";
        }
        if (!module.service().wageringOpen()) {
            return "Wagering is paused";
        }
        if (balance < match.stakeCents()) {
            return "Needs " + money(match.stakeCents());
        }
        return null;
    }

    private void join(Player player, String id) {
        CoinFlipMatch live = module.service().findActive(id).orElse(null);
        if (live == null) {
            message("match-not-found", CoinFlipService.vars("id", id));
            refresh();
            return;
        }
        setBusy(true);
        module.service().join(viewer, player.getName(), id).thenAccept(outcome -> {
            setBusy(false);
            if (!outcome.success()) {
                report(outcome);
                refresh();
            }
        });
    }

    private void create(Player player, long stake) {
        CoinSide chosen = randomSide ? (java.util.concurrent.ThreadLocalRandom.current().nextBoolean() ? CoinSide.HEADS : CoinSide.TAILS) : side;
        setBusy(true);
        module.service().create(new CoinFlipService.CreateRequest(viewer, player.getName(), stake, chosen, null, null))
                .thenAccept(outcome -> {
                    setBusy(false);
                    report(outcome);
                    refresh();
                });
    }

    private void promptAmount(Player player) {
        message("custom-amount-prompt", Map.of());
        SignInputUtil.open(module.plugin(), player, List.of("", "^^^^^^^^^^", "Stake to flip", ""), 0, text -> {
            Player current = Bukkit.getPlayer(viewer);
            if (current == null) {
                return;
            }
            String typed = text == null ? "" : text.trim();
            if (typed.isEmpty() || typed.equalsIgnoreCase("cancel")) {
                open(current);
                return;
            }
            StakeMath.ParseResult parsed = StakeMath.parse(typed);
            if (!parsed.ok()) {
                module.presenter().sendOutcome(viewer, new CoinFlipService.Outcome(false, "invalid-amount",
                        CoinFlipService.vars("reason", parsed.error().name().toLowerCase(java.util.Locale.ROOT)), null));
                open(current);
                return;
            }
            open(current);
            create(current, parsed.cents());
        });
    }
}
