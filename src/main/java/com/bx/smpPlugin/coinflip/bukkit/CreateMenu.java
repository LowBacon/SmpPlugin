package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.coinflip.CoinFlipConfig;
import com.bx.smpPlugin.coinflip.CoinFlipPreferences;
import com.bx.smpPlugin.coinflip.CoinFlipService;
import com.bx.smpPlugin.coinflip.CoinSide;
import com.bx.smpPlugin.coinflip.StakeMath;
import com.bx.smpPlugin.utils.SignInputUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Stake, side and visibility selection. Nothing moves until the review screen is confirmed. */
public final class CreateMenu extends CoinFlipMenu {

    private static final int[] INCREMENT_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    /** The player's choices while navigating between create, challenge and review screens. */
    public static final class Draft {
        long stakeCents;
        CoinSide side;
        UUID targetId;
        String targetName;
        int recentIndex;

        public static Draft initial(CoinFlipPreferences prefs, CoinFlipConfig config) {
            Draft draft = new Draft();
            long saved = prefs.savedStakeCents();
            draft.stakeCents = saved > 0L ? Math.max(config.minStakeCents(), Math.min(config.maxStakeCents(), saved))
                    : config.minStakeCents();
            draft.side = prefs.savedSide() == null ? CoinSide.HEADS : prefs.savedSide();
            return draft;
        }

        public static Draft challenge(CoinFlipPreferences prefs, CoinFlipConfig config, UUID target, String targetName) {
            Draft draft = initial(prefs, config);
            draft.targetId = target;
            draft.targetName = targetName;
            return draft;
        }

        public Draft withStake(long cents) {
            Draft copy = copy();
            copy.stakeCents = cents;
            return copy;
        }

        Draft copy() {
            Draft copy = new Draft();
            copy.stakeCents = stakeCents;
            copy.side = side;
            copy.targetId = targetId;
            copy.targetName = targetName;
            copy.recentIndex = recentIndex;
            return copy;
        }
    }

    private final Draft draft;

    public CreateMenu(CoinFlipModule module, UUID viewer, Draft draft) {
        super(module, viewer, draft.targetId == null ? "Create match" : "Challenge " + draft.targetName, 54);
        this.draft = draft;
    }

    @Override
    protected void render(Player player) {
        CoinFlipConfig config = config();
        boolean readable = readable();
        long balance = StakeMath.fromEconomy(module.plugin().getEconomyManager().getBalance(player));
        String problem = problem(config, balance);

        List<String> summary = new ArrayList<>();
        summary.add(Gui.line(readable, "Your stake", money(draft.stakeCents)));
        summary.add(Gui.line(readable, "Opponent's stake", money(draft.stakeCents)));
        summary.add(Gui.line(readable, "Pot", money(StakeMath.pot(draft.stakeCents))));
        summary.add(Gui.line(readable, "Fee", config.feeBasisPoints() == 0 ? "none (0%)"
                : money(StakeMath.fee(StakeMath.pot(draft.stakeCents), config.feeBasisPoints())) + " (" + StakeMath.percent(config.feeBasisPoints()) + ")"));
        summary.add(Gui.line(readable, "If you win you receive", money(StakeMath.payout(draft.stakeCents, config.feeBasisPoints()))));
        summary.add(Gui.line(readable, "Net if you win", "+" + money(StakeMath.winnerNet(draft.stakeCents, config.feeBasisPoints()))));
        summary.add(Gui.line(readable, "If you lose", "-" + money(draft.stakeCents)));
        summary.add("");
        summary.add(Gui.line(readable, "Your balance", money(balance)));
        summary.add(Gui.line(readable, "Allowed", money(config.minStakeCents()) + " to " + money(config.maxStakeCents())));
        summary.add(Gui.MUTED + "Odds are always exactly 50/50.");
        if (problem != null) {
            summary.add("");
            summary.add(Gui.BAD + problem);
        }
        set(4, Gui.item(Material.SUNFLOWER, Gui.GOLD + "Stake " + money(draft.stakeCents), summary));

        List<Long> increments = config.incrementsCents();
        for (int i = 0; i < INCREMENT_SLOTS.length && i < increments.size(); i++) {
            long step = increments.get(i);
            button(INCREMENT_SLOTS[i], Gui.item(i < 3 ? Material.IRON_NUGGET : i < 5 ? Material.GOLD_NUGGET : Material.GOLD_INGOT,
                    Gui.GOLD + "± " + money(step),
                    Gui.MUTED + "Left-click: add " + money(step),
                    Gui.MUTED + "Right-click: remove " + money(step)),
                    click -> adjust(click.isRightClick() ? -step : step));
        }

        button(19, Gui.item(Material.WATER_BUCKET, Gui.INFO + "Reset", Gui.MUTED + "Back to the minimum stake."),
                () -> setStake(config.minStakeCents()));
        button(20, Gui.item(Material.OAK_SIGN, Gui.INFO + "Custom amount",
                Gui.MUTED + "Type an exact stake, e.g. 2500 or 2.5k.", Gui.MUTED + "Leave it empty to cancel."), this::customAmount);
        List<Long> recent = prefs().recentStakes();
        if (!recent.isEmpty()) {
            List<String> lore = new ArrayList<>();
            for (long value : recent) {
                lore.add(Gui.bullet(readable) + Gui.VALUE + money(value));
            }
            lore.add(Gui.MUTED + "Click to cycle through them.");
            button(21, Gui.item(Material.CLOCK, Gui.INFO + "Recent stakes", lore), () -> {
                draft.recentIndex = draft.recentIndex % recent.size();
                setStake(recent.get(draft.recentIndex++));
            });
        }
        if (prefs().savedStakeCents() > 0L) {
            button(22, Gui.item(Material.NAME_TAG, Gui.INFO + "Saved stake",
                    Gui.line(readable, "Saved", money(prefs().savedStakeCents()))), () -> setStake(prefs().savedStakeCents()));
        }
        button(23, Gui.item(Material.IRON_INGOT, Gui.INFO + "Minimum", Gui.line(readable, "Set to", money(config.minStakeCents()))),
                () -> setStake(config.minStakeCents()));
        if (config.allowFullBalance()) {
            long allIn = Math.min(config.maxStakeCents(), config.allowDecimals() ? balance : balance - balance % 100L);
            button(24, Gui.item(Material.GOLD_BLOCK, Gui.GOLD + "Whole balance",
                    Gui.line(readable, "Set to", money(allIn)), Gui.MUTED + "Still limited by the maximum stake."),
                    () -> setStake(allIn));
        }
        button(25, Gui.item(Material.WRITABLE_BOOK, Gui.INFO + "Remember these choices",
                Gui.MUTED + "Save this stake and side as", Gui.MUTED + "your preference for next time."), () -> {
            module.service().savePreferences(viewer, prefs().withSaved(draft.side, draft.stakeCents));
            message("settings-saved", Map.of());
        });

        sideButton(29, CoinSide.HEADS, config);
        button(31, Gui.glow(Gui.item(Material.ENDER_PEARL, (draft.side == null ? Gui.GOOD + "▶ " : Gui.INFO) + "Random side",
                Gui.MUTED + "A side is picked for you when the", Gui.MUTED + "match is created. Odds are the same."),
                draft.side == null), () -> {
            draft.side = null;
            refresh();
        });
        sideButton(33, CoinSide.TAILS, config);

        if (config.allowPrivate() && can(CoinFlipModule.PERM_CHALLENGE)) {
            if (draft.targetId == null) {
                button(38, Gui.item(Material.PAPER, Gui.INFO + "Public match",
                        Gui.MUTED + "Anyone can join.", "", Gui.MUTED + "Click to challenge one player instead."),
                        () -> openMenu(new ChallengeMenu(module, viewer, 0, draft)));
            } else {
                button(38, Gui.head(draft.targetId, Gui.GOLD + "Challenge: " + draft.targetName, List.of(
                                Gui.MUTED + "Only " + draft.targetName + " can accept.",
                                Gui.MUTED + "Expires in " + Gui.duration(config.challengeExpiryMillis()) + ".", "",
                                Gui.MUTED + "Left-click: pick someone else", Gui.MUTED + "Right-click: make it public")),
                        click -> {
                            if (click.isRightClick()) {
                                draft.targetId = null;
                                draft.targetName = null;
                                openMenu(new CreateMenu(module, viewer, draft));
                            } else {
                                openMenu(new ChallengeMenu(module, viewer, 0, draft));
                            }
                        });
            }
        }
        set(40, Gui.item(Material.BOOK, Gui.INFO + "How it works", List.of(
                Gui.MUTED + "Your stake is reserved when the",
                Gui.MUTED + "match is created and returned if",
                Gui.MUTED + "nobody joins within " + Gui.duration(draft.targetId == null ? config.expiryMillis() : config.challengeExpiryMillis()) + ".",
                Gui.MUTED + "You can cancel while it is waiting.")));
        set(42, Gui.item(Material.PLAYER_HEAD, Gui.INFO + "In-game currency only",
                Gui.MUTED + "No real money is involved."));

        back(() -> openMenu(new MainMenu(module, viewer)));
        if (problem == null) {
            button(49, Gui.glow(Gui.item(Material.LIME_CONCRETE, Gui.GOOD + "Review and create",
                    Gui.MUTED + "You will see every term once", Gui.MUTED + "more before anything is reserved."), true),
                    () -> openMenu(new ReviewMenu(module, viewer, draft.copy())));
        } else {
            button(49, Gui.item(Material.RED_CONCRETE, Gui.BAD + "Cannot create yet", Gui.MUTED + problem), () -> sound("ERROR"));
        }
        button(53, Gui.item(Material.BARRIER, Gui.BAD + "Close"), () -> close());
    }

    private void sideButton(int slot, CoinSide side, CoinFlipConfig config) {
        boolean selected = draft.side == side;
        button(slot, Gui.glow(Gui.item(Gui.sideMaterial(side, config.headsMaterial(), config.tailsMaterial()),
                (selected ? Gui.GOOD + "▶ " : "") + Gui.sideColour(side) + side.displayName(),
                Gui.MUTED + "You win if the coin lands on " + side.displayName() + ".",
                Gui.MUTED + "Your opponent gets " + side.opposite().displayName() + ".",
                Gui.MUTED + "Your pick does not change the odds."), selected), () -> {
            draft.side = side;
            refresh();
        });
    }

    /** Null when the draft can be submitted; otherwise the reason it cannot. */
    private String problem(CoinFlipConfig config, long balance) {
        if (!module.service().wageringOpen()) {
            return "Wagering is paused right now.";
        }
        if (!config.allowDecimals() && draft.stakeCents % 100L != 0L) {
            return "Stakes must be whole numbers.";
        }
        if (draft.stakeCents < config.minStakeCents()) {
            return "Below the minimum of " + money(config.minStakeCents()) + ".";
        }
        if (draft.stakeCents > config.maxStakeCents()) {
            return "Above the maximum of " + money(config.maxStakeCents()) + ".";
        }
        if (draft.stakeCents > balance) {
            return "Your balance is " + money(balance) + ".";
        }
        return null;
    }

    private void adjust(long delta) {
        long next = Math.max(0L, draft.stakeCents + delta);
        setStake(Math.min(next, config().maxStakeCents()));
    }

    private void setStake(long cents) {
        draft.stakeCents = Math.max(0L, Math.min(cents, StakeMath.HARD_MAX_STAKE_CENTS));
        refresh();
    }

    private void customAmount() {
        Player player = Bukkit.getPlayer(viewer);
        if (player == null) {
            return;
        }
        message("custom-amount-prompt", Map.of());
        SignInputUtil.open(module.plugin(), player, List.of("", "^^^^^^^^^^", "Stake amount", "e.g. 2.5k"), 0, text -> {
            Player current = Bukkit.getPlayer(viewer);
            if (current == null) {
                return;
            }
            if (text == null || text.isBlank() || text.trim().equalsIgnoreCase("cancel")) {
                message("custom-amount-cancelled", Map.of());
                new CreateMenu(module, viewer, draft).open(current);
                return;
            }
            StakeMath.ParseResult parsed = StakeMath.parse(text);
            if (!parsed.ok()) {
                module.presenter().sendOutcome(viewer, new CoinFlipService.Outcome(false, "invalid-amount",
                        CoinFlipService.vars("reason", parsed.error().name().toLowerCase().replace("too_many_decimals", "decimals")
                                .replace("too_large", "too-large")), null));
                new CreateMenu(module, viewer, draft).open(current);
                return;
            }
            new CreateMenu(module, viewer, draft.withStake(parsed.cents())).open(current);
        });
    }
}
