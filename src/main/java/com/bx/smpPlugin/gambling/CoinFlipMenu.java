package com.bx.smpPlugin.gambling;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.menus.BaseMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The /cf screen: three rows.
 *
 * <pre>
 *  rows 1-2  the open games: each is the creator's head with the bet. Click one to flip.
 *            Your own games come first; click one of them to take it back.
 *  row 3     how it works | five quick bets that start a game | another amount | next page | close
 * </pre>
 * The screen only shows; creating, joining and cancelling go through {@link CoinFlip}, which checks
 * everything again.
 */
final class CoinFlipMenu extends BaseMenu {

    private static final int GAME_SLOTS = 18;
    private static final int INFO_SLOT = 18;
    private static final int[] QUICK_SLOTS = {19, 20, 21, 22, 23};
    private static final int CUSTOM_SLOT = 24;
    private static final int PAGE_SLOT = 25;
    private static final int CLOSE_SLOT = 26;
    private static final Material[] QUICK_ICONS = {Material.GOLD_NUGGET, Material.RAW_GOLD, Material.GOLD_INGOT,
            Material.GOLD_BLOCK, Material.DIAMOND_BLOCK};

    private final CoinFlip coinFlip;
    private final UUID viewer;
    private final Map<Integer, Integer> gameAt = new HashMap<>();
    private final Map<Integer, Long> betAt = new HashMap<>();
    private int page;

    CoinFlipMenu(SmpPlugin plugin, CoinFlip coinFlip, UUID viewer) {
        super(plugin, "&6&lCoinFlip", 27);
        this.coinFlip = coinFlip;
        this.viewer = viewer;
    }

    void refresh(Player player) {
        build(player);
    }

    @Override
    public void build(Player player) {
        inventory.clear();
        gameAt.clear();
        betAt.clear();
        Wallet wallet = coinFlip.wallet();
        long balance = wallet.balance(player);

        List<CoinFlip.Game> games = coinFlip.openGames(viewer);
        int pages = Math.max(1, (games.size() + GAME_SLOTS - 1) / GAME_SLOTS);
        page = Math.floorMod(page, pages);
        for (int i = 0; i < GAME_SLOTS; i++) {
            int index = page * GAME_SLOTS + i;
            if (index >= games.size()) {
                break;
            }
            CoinFlip.Game game = games.get(index);
            boolean own = game.creator.equals(viewer);
            List<String> lore = new ArrayList<>();
            lore.add("&7Bet: &6" + wallet.money(game.stake));
            lore.add("&7Winner gets: &a" + wallet.money(coinFlip.payoutFor(game.stake)));
            lore.add("&7Chance: &f50%");
            lore.add("");
            if (own) {
                lore.add("&cClick to cancel and get your bet back");
            } else if (balance < game.stake) {
                lore.add("&cYou need " + wallet.money(game.stake));
            } else {
                lore.add("&a&lClick to flip now!");
            }
            set(i, Icons.glow(Icons.head(game.creator, (own ? "&bYour game" : "&f" + game.creatorName) + " &8· &6"
                    + wallet.money(game.stake), lore), own));
            gameAt.put(i, game.id);
        }
        if (games.isEmpty()) {
            set(4, Icons.item(Material.STRUCTURE_VOID, "&7No open games yet",
                    "&7Start one with a bet below,", "&7or type &f/cf <amount>&7."));
        }

        set(INFO_SLOT, Icons.item(Material.BOOK, "&eHow it works",
                "&7Start a game with a bet, or click", "&7someone's game to bet the same.",
                "&7The flip starts at once: both heads", "&7spin and the middle one wins the pot.",
                "", "&7Fair 50/50" + (coinFlip.feeBasisPoints() == 0 ? ", no fee." : ", fee "
                        + SpinMath.percent(coinFlip.feeBasisPoints()) + "."),
                "&7Your balance: &f" + wallet.money(balance)));
        List<Long> bets = coinFlip.quickBets();
        for (int i = 0; i < QUICK_SLOTS.length; i++) {
            if (i >= bets.size()) {
                continue;
            }
            long bet = bets.get(i);
            boolean affordable = balance >= bet;
            set(QUICK_SLOTS[i], Icons.item(affordable ? QUICK_ICONS[i] : Material.GRAY_DYE, "&6Start a " + wallet.money(bet) + " game",
                    affordable ? "&7Click to put up " + wallet.money(bet) + "." : "&cYou need " + wallet.money(bet) + "."));
            betAt.put(QUICK_SLOTS[i], bet);
        }
        set(CUSTOM_SLOT, Icons.item(Material.OAK_SIGN, "&bAnother amount", "&7Click, then type the amount", "&7after &f/cf &7in chat."));
        if (pages > 1) {
            set(PAGE_SLOT, Icons.item(Material.ARROW, "&ePage " + (page + 1) + "/" + pages, "&7Click for the next page."));
        }
        set(CLOSE_SLOT, Icons.item(Material.BARRIER, "&cClose"));
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        Integer gameId = gameAt.get(slot);
        if (gameId != null) {
            CoinFlip.Game game = coinFlip.openGames(viewer).stream().filter(g -> g.id == gameId).findFirst().orElse(null);
            if (game != null && game.creator.equals(player.getUniqueId())) {
                coinFlip.cancel(player, gameId);
            } else {
                coinFlip.join(player, gameId);
            }
            return;
        }
        Long bet = betAt.get(slot);
        if (bet != null) {
            coinFlip.create(player, bet);
            return;
        }
        switch (slot) {
            case CUSTOM_SLOT -> {
                player.closeInventory();
                Text.line(player, Text.prefix(), Text.suggest(Text.get("COINFLIP.CUSTOM-PROMPT",
                        "&b[Click here] &7and type your bet after &f/cf"), "/cf ", "&7Fills in the command for you"));
            }
            case PAGE_SLOT -> {
                page++;
                build(player);
            }
            case INFO_SLOT -> {
                player.closeInventory();
                player.performCommand("cf help");
            }
            case CLOSE_SLOT -> player.closeInventory();
            default -> {
                // empty slot
            }
        }
    }
}
