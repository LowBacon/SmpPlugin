package com.bx.smpPlugin.gambling;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.coinflip.bukkit.Gui;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.menus.BaseMenu;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The roulette screen. There is one screen for the whole server and everyone who opens it looks at
 * the same inventory, so a reel step is drawn once however many people are watching.
 *
 * <pre>
 *  row 0   frame, pot in the middle
 *  row 1   frame, ▼ marker above the middle of the reel
 *  row 2   the reel: nine heads; the one in the middle (slot 22) wins
 *  row 3   frame, ▲ marker below the middle
 *  row 4   everyone in the round, biggest bet first
 *  row 5   rules, own amount, quick bets, status, leave, close
 * </pre>
 * The menu only shows the round; joining and leaving go through {@link Roulette}, which checks
 * everything again.
 */
final class RouletteMenu extends BaseMenu {

    private static final int POT_SLOT = 4;
    private static final int TOP_MARKER = 13;
    private static final int BOTTOM_MARKER = 31;
    private static final int REEL_START = 18;
    private static final int PLAYERS_START = 36;
    private static final int RULES_SLOT = 45;
    private static final int CUSTOM_SLOT = 46;
    private static final int[] QUICK_SLOTS = {47, 48, 50, 51};
    private static final int STATUS_SLOT = 49;
    private static final int LEAVE_SLOT = 52;
    private static final int CLOSE_SLOT = 53;
    private static final Material[] QUICK_ICONS = {Material.GOLD_NUGGET, Material.GOLD_INGOT, Material.GOLD_BLOCK, Material.DIAMOND_BLOCK};
    private static final Material[] FLASH = {Material.YELLOW_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE};

    private final Roulette roulette;
    /** Heads for the round being spun, built once when the spin starts. */
    private final Map<Integer, ItemStack> reelHeads = new HashMap<>();
    private final Map<Integer, Long> quickBets = new HashMap<>();

    RouletteMenu(SmpPlugin plugin, Roulette roulette) {
        super(plugin, "&6&l🎰 Head Roulette", 54);
        this.roulette = roulette;
    }

    @Override
    public void build(Player player) {
        drawFrame(Material.BLACK_STAINED_GLASS_PANE);
        Roulette.Round round = roulette.round();
        if (round != null && (round.state == Roulette.State.SPINNING || round.state == Roulette.State.SETTLED)) {
            renderReel(round, round.position);
            if (round.state == Roulette.State.SETTLED) {
                showWinner(round);
            }
        } else {
            renderIdleReel(round);
        }
        drawStatus();
    }

    /** Redraws for everyone who has the screen open. */
    void refresh() {
        if (!inventory.getViewers().isEmpty()) {
            build(null);
        }
    }

    // ------------------------------------------------------------------ static parts

    private void drawFrame(Material pane) {
        ItemStack glass = Gui.item(pane, " ");
        for (int slot = 0; slot < 36; slot++) {
            if (slot < REEL_START || slot >= REEL_START + Roulette.WINDOW) {
                set(slot, glass);
            }
        }
        set(TOP_MARKER, Gui.item(Material.LIME_STAINED_GLASS_PANE, "&a&l▼ &7The head that stops here wins"));
        set(BOTTOM_MARKER, Gui.item(Material.LIME_STAINED_GLASS_PANE, "&a&l▲ &7The head that stops here wins"));
    }

    /** Pot, players, buttons and the countdown: everything except the reel. Cheap; runs once a second. */
    void renderStatus() {
        if (!inventory.getViewers().isEmpty()) {
            drawStatus();
        }
    }

    private void drawStatus() {
        Wallet wallet = roulette.wallet();
        Roulette.Round round = roulette.round();
        long pot = round == null ? 0L : round.pot();
        long fee = RouletteMath.fee(pot, roulette.feeBasisPoints());
        List<String> potLore = new ArrayList<>();
        potLore.add("&7Players: &f" + (round == null ? 0 : round.entries.size()) + "&8/&7" + roulette.maxPlayers());
        potLore.add("&7Fee: &f" + (roulette.feeBasisPoints() == 0 ? "none" : RouletteMath.percent(roulette.feeBasisPoints())
                + " (" + wallet.money(fee) + ")"));
        potLore.add("&7Winner gets: &a" + wallet.money(pot - fee));
        potLore.add("");
        potLore.add("&7Chance = &fyour bet ÷ the pot");
        set(POT_SLOT, Gui.glow(Gui.item(Material.SUNFLOWER, "&6&lPot: &f" + wallet.money(pot), potLore), pot > 0));

        List<Roulette.Entry> entries = round == null ? List.of() : new ArrayList<>(round.entries);
        List<Roulette.Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingLong(Roulette.Entry::stake).reversed());
        for (int i = 0; i < 9; i++) {
            int slot = PLAYERS_START + i;
            if (i == 8 && sorted.size() > 9) {
                set(slot, Gui.item(Material.PLAYER_HEAD, "&7+" + (sorted.size() - 8) + " more"));
            } else if (i < sorted.size()) {
                Roulette.Entry entry = sorted.get(i);
                set(slot, Gui.head(entry.player(), "&f" + entry.name(), List.of(
                        "&7Bet: &6" + wallet.money(entry.stake()),
                        "&7Chance: &f" + RouletteMath.chance(entry.stake(), pot))));
            } else {
                set(slot, Gui.item(Material.GRAY_STAINED_GLASS_PANE, "&8Free spot"));
            }
        }

        set(RULES_SLOT, Gui.item(Material.BOOK, "&eHow it works",
                "&7Everyone's money goes in one pot.",
                "&7The heads spin and the one that",
                "&7stops in the middle wins it all.",
                "&7Your chance is your bet ÷ the pot.",
                "&7The server draws the winner first;",
                "&7the spin only shows the result.",
                "",
                "&7Bets: &f" + wallet.money(roulette.minBet()) + " &7to &f" + wallet.money(roulette.maxBet()),
                "&eClick for the full rules in chat"));
        set(CUSTOM_SLOT, Gui.item(Material.OAK_SIGN, "&bBet another amount", "&7Type it in chat:", "&f/roulette join <amount>"));
        quickBets.clear();
        List<Long> bets = roulette.quickBets();
        boolean locked = roulette.locked();
        for (int i = 0; i < QUICK_SLOTS.length; i++) {
            if (i >= bets.size()) {
                set(QUICK_SLOTS[i], Gui.item(Material.BLACK_STAINED_GLASS_PANE, " "));
                continue;
            }
            long bet = bets.get(i);
            quickBets.put(QUICK_SLOTS[i], bet);
            set(QUICK_SLOTS[i], Gui.item(locked ? Material.GRAY_DYE : QUICK_ICONS[i], (locked ? "&7" : "&6") + "Bet " + wallet.money(bet),
                    locked ? "&7Bets are locked for this round." : "&7Click to join with " + wallet.money(bet) + "."));
        }
        set(STATUS_SLOT, statusItem(round));
        set(LEAVE_SLOT, round != null && round.state == Roulette.State.WAITING
                ? Gui.item(Material.RED_DYE, "&cLeave", "&7Get your bet back.", "&7Only possible until a", "&7second player joins.")
                : Gui.item(Material.BLACK_STAINED_GLASS_PANE, " "));
        set(CLOSE_SLOT, Gui.item(Material.BARRIER, "&cClose"));
    }

    private ItemStack statusItem(Roulette.Round round) {
        if (round == null) {
            Roulette.Round last = roulette.lastRound();
            List<String> lore = new ArrayList<>(List.of("&7Place a bet to open a round.",
                    "&7It starts when " + roulette.minPlayers() + " players are in."));
            if (last != null && last.winner >= 0) {
                lore.add("");
                lore.add("&7Last winner: &f" + last.entries.get(last.winner).name() + " &7(" + roulette.wallet().money(last.payout) + ")");
            }
            return Gui.item(Material.CLOCK, "&eNo round yet", lore);
        }
        return switch (round.state) {
            case WAITING -> Gui.item(Material.CLOCK, "&eWaiting for players",
                    "&7The countdown starts when", "&7" + roulette.minPlayers() + " players are in.");
            case COUNTDOWN -> Gui.glow(Gui.item(Material.CLOCK, "&aSpinning in &f" + roulette.secondsLeft() + "s",
                    roulette.locked() ? "&cBets are locked." : "&7Join now; bets lock " + roulette.lockSeconds() + "s before."), true);
            case SPINNING -> Gui.item(Material.COMPASS, "&6Spinning...", "&7The winner was drawn when", "&7the countdown ended.");
            case SETTLED -> Gui.item(Material.EMERALD, "&aRound over", "&7A new round opens in a moment.");
        };
    }

    // ------------------------------------------------------------------ the reel

    private void renderIdleReel(Roulette.Round round) {
        List<Roulette.Entry> entries = round == null ? List.of() : round.entries;
        for (int i = 0; i < Roulette.WINDOW; i++) {
            if (entries.isEmpty()) {
                set(REEL_START + i, Gui.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&7Waiting for bets"));
            } else {
                Roulette.Entry entry = entries.get(i % entries.size());
                set(REEL_START + i, Gui.head(entry.player(), "&f" + entry.name(), List.of()));
            }
        }
    }

    /** Builds each player's head once for the spin, so the steps only move ready items around. */
    void prepareSpin(Roulette.Round round) {
        reelHeads.clear();
        long pot = round.pot();
        for (int i = 0; i < round.entries.size(); i++) {
            Roulette.Entry entry = round.entries.get(i);
            reelHeads.put(i, Gui.head(entry.player(), "&f" + entry.name(), List.of(
                    "&7Bet: &6" + roulette.wallet().money(entry.stake()),
                    "&7Chance: &f" + RouletteMath.chance(entry.stake(), pot))));
        }
        renderReel(round, 0);
    }

    void renderReel(Roulette.Round round, int position) {
        if (round.reel == null) {
            return;
        }
        for (int i = 0; i < Roulette.WINDOW; i++) {
            int index = Math.min(round.reel.length - 1, position + i);
            ItemStack head = reelHeads.get(round.reel[index]);
            if (head == null) {
                head = Gui.head(round.entries.get(round.reel[index]).player(), "&f" + round.entries.get(round.reel[index]).name(), List.of());
            }
            set(REEL_START + i, head);
        }
    }

    /** Frame flash and a click that drops in pitch as the reel slows. */
    void tick(int position, int steps) {
        if (inventory.getViewers().isEmpty()) {
            return;
        }
        ItemStack flash = Gui.item(FLASH[position % 2], " ");
        for (int slot = 9; slot < 36; slot += 1) {
            if ((slot >= 9 && slot <= 17 && slot != TOP_MARKER) || (slot >= 27 && slot <= 35 && slot != BOTTOM_MARKER)) {
                set(slot, flash);
            }
        }
        float pitch = 2.0F - 1.2F * position / Math.max(1, steps);
        for (HumanEntity viewer : List.copyOf(inventory.getViewers())) {
            if (viewer instanceof Player player) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5F, pitch);
            }
        }
    }

    void showWinner(Roulette.Round round) {
        if (round.winner < 0) {
            return;
        }
        Roulette.Entry winner = round.entries.get(round.winner);
        int middle = REEL_START + Roulette.WINDOW / 2;
        for (int i = 0; i < Roulette.WINDOW; i++) {
            if (REEL_START + i != middle) {
                set(REEL_START + i, Gui.item(Material.GRAY_STAINED_GLASS_PANE, " "));
            }
        }
        set(middle, Gui.glow(Gui.head(winner.player(), "&6&l" + winner.name() + " &awins!", List.of(
                "&7Won: &a" + roulette.wallet().money(round.payout),
                "&7Bet: &6" + roulette.wallet().money(winner.stake()),
                "&7Chance: &f" + RouletteMath.chance(winner.stake(), round.pot()))), true));
        ItemStack gold = Gui.item(Material.LIME_STAINED_GLASS_PANE, "&a" + winner.name() + " won the pot!");
        for (int slot = 9; slot <= 17; slot++) {
            set(slot, gold);
        }
        for (int slot = 27; slot <= 35; slot++) {
            set(slot, gold);
        }
        drawStatus();
    }

    // ------------------------------------------------------------------ clicks

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        Long bet = quickBets.get(slot);
        if (bet != null) {
            roulette.join(player, bet);
            return;
        }
        switch (slot) {
            case CUSTOM_SLOT -> {
                player.closeInventory();
                Text.line(player, Text.prefix(), Text.suggest(Text.get("ROULETTE.CUSTOM-PROMPT",
                                "&b[Click here] &7and type your amount after &f/roulette join"),
                        "/roulette join ", "&7Fills in the command for you"));
            }
            case RULES_SLOT -> {
                player.closeInventory();
                player.performCommand("roulette info");
            }
            case LEAVE_SLOT -> roulette.leave(player);
            case CLOSE_SLOT -> player.closeInventory();
            default -> {
                // decoration
            }
        }
    }

    @Override
    public boolean isEscBackTarget() {
        return false;
    }
}
