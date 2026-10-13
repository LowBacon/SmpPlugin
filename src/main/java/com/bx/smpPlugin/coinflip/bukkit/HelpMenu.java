package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.coinflip.CoinFlipConfig;
import com.bx.smpPlugin.coinflip.CoinFlipService;
import com.bx.smpPlugin.coinflip.StakeMath;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Rules, odds, fees, limits and commands in plain language. */
public final class HelpMenu extends CoinFlipMenu {

    public HelpMenu(CoinFlipModule module, UUID viewer) {
        super(module, viewer, "Help and rules", 54);
    }

    @Override
    protected void render(Player player) {
        CoinFlipConfig config = config();
        boolean readable = readable();
        Map<String, String> vars = CoinFlipService.vars("fee", config.feeBasisPoints() == 0 ? "none" : StakeMath.percent(config.feeBasisPoints()));
        String[] keys = {"help-1", "help-2", "help-3", "help-4", "help-5", "help-6"};
        Material[] icons = {Material.SUNFLOWER, Material.COMPASS, Material.GOLD_INGOT, Material.CLOCK, Material.EMERALD, Material.FEATHER};
        String[] titles = {"How a match works", "Odds", "Payout and fee", "Expiry and refunds", "In-game currency only", "Practice and breaks"};
        int[] slots = {19, 20, 21, 23, 24, 25};
        for (int i = 0; i < keys.length; i++) {
            set(slots[i], Gui.item(icons[i], Gui.INFO + titles[i], wrap(module.messages().raw(keys[i], vars))));
        }
        List<String> limits = new ArrayList<>();
        limits.add(Gui.line(readable, "Stake", money(config.minStakeCents()) + " to " + money(config.maxStakeCents())));
        limits.add(Gui.line(readable, "Open matches each", Integer.toString(config.maxActivePerPlayer())));
        limits.add(Gui.line(readable, "Match expiry", Gui.duration(config.expiryMillis())));
        limits.add(Gui.line(readable, "Challenge expiry", Gui.duration(config.challengeExpiryMillis())));
        if (config.dailyWagerCapCents() > 0) {
            limits.add(Gui.line(readable, "Daily staking limit", money(config.dailyWagerCapCents())));
        }
        if (config.dailyNetLossCapCents() > 0) {
            limits.add(Gui.line(readable, "Daily net-loss limit", money(config.dailyNetLossCapCents())));
        }
        if (config.dailyMatchCap() > 0) {
            limits.add(Gui.line(readable, "Matches per day", Integer.toString(config.dailyMatchCap())));
        }
        set(31, Gui.item(Material.IRON_BARS, Gui.INFO + "Server limits", limits));
        set(40, Gui.item(Material.COMMAND_BLOCK, Gui.INFO + "Commands", List.of(
                Gui.MUTED + "/cf - this menu",
                Gui.MUTED + "/cf create <amount> [heads|tails]",
                Gui.MUTED + "/cf browse, /cf join <id>, /cf cancel <id>",
                Gui.MUTED + "/cf challenge <player> <amount>",
                Gui.MUTED + "/cf accept <id>, /cf deny <id>",
                Gui.MUTED + "/cf stats [player], /cf history [player]",
                Gui.MUTED + "/cf top [category], /cf practice, /cf settings")));
        back(() -> openMenu(new MainMenu(module, viewer)));
    }

    /** Splits a long message into lore lines of about 34 visible characters, keeping its colour. */
    private static List<String> wrap(String text) {
        String colour = text.startsWith("&") && text.length() > 1 ? text.substring(0, 2) : Gui.MUTED;
        String plain = ColorUtils.strip(ColorUtils.colorize(text));
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : plain.split(" ")) {
            if (current.length() + word.length() + 1 > 34 && !current.isEmpty()) {
                lines.add(colour + current);
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(word);
        }
        if (!current.isEmpty()) {
            lines.add(colour + current);
        }
        return lines;
    }
}
