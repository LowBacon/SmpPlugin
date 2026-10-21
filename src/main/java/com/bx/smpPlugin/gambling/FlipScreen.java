package com.bx.smpPlugin.gambling;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.menus.BaseMenu;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * The spin of one coin flip, shared by both players (one inventory, so each step is drawn once).
 *
 * <pre>
 *  row 1   creator's head | frame | ▼ | frame | joiner's head
 *  row 2   the reel: the two heads scroll past; the one that stops in the middle (slot 13) wins
 *  row 3   pot | frame | ▲ | frame | close
 * </pre>
 * The winner was drawn before the screen opened; the reel is built to stop on them.
 */
final class FlipScreen extends BaseMenu {

    static final int WINDOW = 9;
    private static final int REEL_START = 9;
    private static final int MIDDLE = 13;
    private static final int CREATOR_SLOT = 0;
    private static final int JOINER_SLOT = 8;
    private static final int TOP_MARKER = 4;
    private static final int BOTTOM_MARKER = 22;
    private static final int POT_SLOT = 18;
    private static final int CLOSE_SLOT = 26;
    private static final Material[] FLASH = {Material.YELLOW_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE};

    private final CoinFlip coinFlip;
    private final CoinFlip.Game game;
    private final ItemStack[] heads = new ItemStack[2];

    FlipScreen(SmpPlugin plugin, CoinFlip coinFlip, CoinFlip.Game game) {
        super(plugin, "&6&lCoinFlip &8» &f" + game.creatorName + " &7vs &f" + game.joinerName, 27);
        this.coinFlip = coinFlip;
        this.game = game;
        Wallet wallet = coinFlip.wallet();
        heads[0] = Icons.head(game.creator, "&f" + game.creatorName, List.of("&7Bet: &6" + wallet.money(game.stake)));
        heads[1] = Icons.head(game.joiner, "&f" + game.joinerName, List.of("&7Bet: &6" + wallet.money(game.stake)));
        drawFrame(Material.BLACK_STAINED_GLASS_PANE);
        set(CREATOR_SLOT, heads[0]);
        set(JOINER_SLOT, heads[1]);
        set(POT_SLOT, Icons.item(Material.SUNFLOWER, "&6Pot: &f" + wallet.money(game.stake * 2L),
                "&7Winner gets: &a" + wallet.money(game.payout), "&7Fair 50/50, drawn by the server", "&7before the spin started."));
        set(CLOSE_SLOT, Icons.item(Material.BARRIER, "&cClose", "&7The result still arrives in chat."));
        drawReel(0);
    }

    @Override
    public void build(Player player) {
        // Drawn as the game goes; opening only shows the current state.
    }

    private void drawFrame(Material pane) {
        ItemStack glass = Icons.item(pane, " ");
        for (int slot = 0; slot < 27; slot++) {
            boolean reel = slot >= REEL_START && slot < REEL_START + WINDOW;
            if (!reel && slot != CREATOR_SLOT && slot != JOINER_SLOT && slot != POT_SLOT && slot != CLOSE_SLOT) {
                set(slot, glass);
            }
        }
        set(TOP_MARKER, Icons.item(Material.LIME_STAINED_GLASS_PANE, "&a&l▼ &7The head that stops here wins"));
        set(BOTTOM_MARKER, Icons.item(Material.LIME_STAINED_GLASS_PANE, "&a&l▲ &7The head that stops here wins"));
    }

    private void drawReel(int position) {
        for (int i = 0; i < WINDOW; i++) {
            int index = Math.min(game.reel.length - 1, position + i);
            set(REEL_START + i, heads[game.reel[index]]);
        }
    }

    /** One reel step: move the heads, flash the frame, click with a pitch that drops as it slows. */
    void step(int position, int steps) {
        drawReel(position);
        ItemStack flash = Icons.item(FLASH[position % 2], " ");
        for (int slot = 1; slot < 8; slot++) {
            if (slot != TOP_MARKER) {
                set(slot, flash);
            }
        }
        for (int slot = 19; slot < 26; slot++) {
            if (slot != BOTTOM_MARKER) {
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

    void showWinner() {
        int winner = game.winner;
        for (int i = 0; i < WINDOW; i++) {
            if (REEL_START + i != MIDDLE) {
                set(REEL_START + i, Icons.item(Material.GRAY_STAINED_GLASS_PANE, " "));
            }
        }
        set(MIDDLE, Icons.glow(Icons.head(game.winnerId(), "&6&l" + game.winnerName() + " &awins!",
                List.of("&7Won: &a" + coinFlip.wallet().money(game.payout))), true));
        set(winner == 0 ? JOINER_SLOT : CREATOR_SLOT, Icons.item(Material.SKELETON_SKULL,
                "&7" + (winner == 0 ? game.joinerName : game.creatorName)));
        ItemStack lime = Icons.item(Material.LIME_STAINED_GLASS_PANE, "&a" + game.winnerName() + " won the flip!");
        for (int slot = 1; slot < 8; slot++) {
            set(slot, lime);
        }
        for (int slot = 19; slot < 26; slot++) {
            set(slot, lime);
        }
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    @Override
    public boolean isEscBackTarget() {
        return false;
    }
}
