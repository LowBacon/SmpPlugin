package com.bx.smpPlugin.engagement;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Reward;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * /giveaway: staff give away the item in their hand, or an amount of money. Players join with a
 * click; when the timer ends, winners are drawn from those still online.
 *
 * <pre>
 * /giveaway start [seconds]                     give away the item in your hand (one winner)
 * /giveaway money &lt;amount&gt; [seconds] [winners]   give away money (each winner gets the amount)
 * /giveaway join | cancel
 * </pre>
 * An item prize is the real item, taken from the host's hand and given back if the giveaway is
 * cancelled or nobody joins (kept for them until they next join if they are offline by then).
 */
public final class Giveaways implements CommandExecutor, TabCompleter {

    public static final String JOIN_PERMISSION = "smpplugin.giveaway.join";
    public static final String HOST_PERMISSION = "smpplugin.staff.giveaway";
    /** Money giveaways create the money, so they are an admin tool. */
    public static final String MONEY_PERMISSION = "smpplugin.admin.giveaway.money";

    private final SmpPlugin plugin;
    private Giveaway active;

    private static final class Giveaway {
        final UUID host;
        final String hostName;
        final ItemStack item;
        final double money;
        final int winners;
        final long endsAt;
        final long length;
        final Set<UUID> entrants = new LinkedHashSet<>();
        final BossBar bar;
        BukkitTask ticker;

        Giveaway(Player host, ItemStack item, double money, int winners, long seconds, String prizeText) {
            this.host = host == null ? null : host.getUniqueId();
            this.hostName = host == null ? "Console" : host.getName();
            this.item = item;
            this.money = money;
            this.winners = winners;
            this.length = seconds;
            this.endsAt = System.currentTimeMillis() + seconds * 1000L;
            this.bar = Bukkit.createBossBar(Text.color("&d🎁 Giveaway: " + prizeText), BarColor.PINK, BarStyle.SOLID);
        }
    }

    public Giveaways(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "join" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "start" -> startItem(sender, args);
            case "money" -> startMoney(sender, args);
            case "cancel" -> cancel(sender);
            default -> join(sender);
        }
        return true;
    }

    private long defaultSeconds() {
        return plugin.getConfigManager().getExtras() == null ? 60L
                : plugin.getConfigManager().getExtras().getLong("GIVEAWAYS.DEFAULT-SECONDS", 60L);
    }

    private boolean canHost(CommandSender sender) {
        if (!PermissionUtils.has(sender, HOST_PERMISSION)) {
            Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return false;
        }
        if (active != null) {
            Text.send(sender, "GIVEAWAY.ALREADY", "&7A giveaway is already running.");
            return false;
        }
        return true;
    }

    private void startItem(CommandSender sender, String[] args) {
        if (!canHost(sender)) {
            return;
        }
        if (!(sender instanceof Player host)) {
            Text.send(sender, "GIVEAWAY.ITEM-PLAYERS-ONLY", "&7The console can only give away money: &f/giveaway money <amount>");
            return;
        }
        ItemStack hand = host.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            Text.send(host, "GIVEAWAY.NO-ITEM", "&7Hold the item you want to give away.");
            return;
        }
        long seconds = args.length > 1 ? parseSeconds(args[1]) : defaultSeconds();
        ItemStack prize = hand.clone();
        host.getInventory().setItemInMainHand(null);
        begin(new Giveaway(host, prize, 0D, 1, seconds, prize.getAmount() + "x " + Reward.itemName(prize)));
    }

    private void startMoney(CommandSender sender, String[] args) {
        if (!PermissionUtils.has(sender, MONEY_PERMISSION)) {
            Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return;
        }
        if (!canHost(sender)) {
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(args.length > 1 ? args[1] : "");
        } catch (NumberFormatException ignored) {
            amount = -1D;
        }
        if (amount <= 0D) {
            Text.send(sender, "GIVEAWAY.USAGE", "&7/giveaway start [seconds] &8(held item) &7or /giveaway money <amount> [seconds] [winners]");
            return;
        }
        long seconds = args.length > 2 ? parseSeconds(args[2]) : defaultSeconds();
        int maxWinners = plugin.getConfigManager().getExtras() == null ? 10
                : plugin.getConfigManager().getExtras().getInt("GIVEAWAYS.MAX-WINNERS", 10);
        int winners = 1;
        if (args.length > 3) {
            try {
                winners = Math.max(1, Math.min(maxWinners, Integer.parseInt(args[3])));
            } catch (NumberFormatException ignored) {
                winners = 1;
            }
        }
        String prizeText = "$" + com.bx.smpPlugin.utils.NumberUtils.formatNice(amount)
                + (winners > 1 ? " x" + winners + " winners" : "");
        begin(new Giveaway(sender instanceof Player player ? player : null, null, amount, winners, seconds, prizeText));
    }

    private long parseSeconds(String raw) {
        try {
            return Math.max(10L, Math.min(3600L, Long.parseLong(raw)));
        } catch (NumberFormatException ignored) {
            return defaultSeconds();
        }
    }

    private String prizeText(Giveaway giveaway) {
        return giveaway.item != null
                ? "&f" + giveaway.item.getAmount() + "x " + Reward.itemName(giveaway.item)
                : "&a$" + com.bx.smpPlugin.utils.NumberUtils.formatNice(giveaway.money)
                + (giveaway.winners > 1 ? " &7each for &f" + giveaway.winners + " &7winners" : "");
    }

    private void begin(Giveaway giveaway) {
        active = giveaway;
        Bukkit.getOnlinePlayers().forEach(giveaway.bar::addPlayer);
        giveaway.ticker = plugin.getSpigotScheduler().runGlobalTimer(() -> tick(giveaway), 20L, 20L);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Text.raw(player, "");
            Text.raw(player, Text.get("GIVEAWAY.STARTED", "&d&l🎁 GIVEAWAY &8» &f{host} &7is giving away {prize}&7!",
                    "{host}", giveaway.hostName, "{prize}", prizeText(giveaway)));
            Text.line(player, "  ", Text.button(Text.get("GIVEAWAY.JOIN-BUTTON", "&d&l[Click to join]"), "giveaway join",
                    Text.get("GIVEAWAY.JOIN-HOVER", "&7Join the giveaway")),
                    Text.get("GIVEAWAY.ENDS", " &7Ends in &f{time}&7.", "{time}", Text.duration(giveaway.length)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7F, 1.2F);
        }
    }

    private void join(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return;
        }
        if (!PermissionUtils.has(player, JOIN_PERMISSION)) {
            Text.send(player, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return;
        }
        Giveaway giveaway = active;
        if (giveaway == null) {
            Text.send(player, "GIVEAWAY.NONE", "&7There is no giveaway running right now.");
            return;
        }
        if (player.getUniqueId().equals(giveaway.host)) {
            Text.send(player, "GIVEAWAY.OWN", "&7You can't join your own giveaway.");
            return;
        }
        if (!giveaway.entrants.add(player.getUniqueId())) {
            Text.send(player, "GIVEAWAY.ALREADY-JOINED", "&7You're already in. Good luck!");
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.3F);
        Text.send(player, "GIVEAWAY.JOINED", "&dYou joined the giveaway! &7({count} entered)",
                "{count}", Integer.toString(giveaway.entrants.size()));
    }

    private void tick(Giveaway giveaway) {
        if (active != giveaway) {
            giveaway.ticker.cancel();
            return;
        }
        long left = Math.max(0L, (giveaway.endsAt - System.currentTimeMillis()) / 1000L);
        giveaway.bar.setProgress(Math.max(0D, Math.min(1D, left / (double) giveaway.length)));
        giveaway.bar.setTitle(Text.color("&d🎁 " + prizeText(giveaway) + " &7- " + Text.duration(left)
                + " &8| &f" + giveaway.entrants.size() + " &7joined &8| &d/giveaway"));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!giveaway.bar.getPlayers().contains(player)) {
                giveaway.bar.addPlayer(player);
            }
        }
        if (left <= 0L) {
            draw(giveaway);
        }
    }

    private void draw(Giveaway giveaway) {
        active = null;
        giveaway.ticker.cancel();
        giveaway.bar.removeAll();
        List<Player> pool = new ArrayList<>();
        for (UUID uuid : giveaway.entrants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                pool.add(player);
            }
        }
        if (pool.isEmpty()) {
            Text.broadcast(Text.prefix() + Text.get("GIVEAWAY.NOBODY", "&7Nobody joined the giveaway, so the prize goes back."));
            refund(giveaway);
            return;
        }
        Collections.shuffle(pool);
        List<Player> winners = pool.subList(0, Math.min(giveaway.winners, pool.size()));
        List<String> names = new ArrayList<>();
        for (Player winner : winners) {
            names.add(winner.getName());
            plugin.getSpigotScheduler().runEntity(winner, () -> {
                if (giveaway.item != null) {
                    for (ItemStack rest : winner.getInventory().addItem(giveaway.item.clone()).values()) {
                        winner.getWorld().dropItemNaturally(winner.getLocation(), rest);
                    }
                } else {
                    new Reward(giveaway.money, 0L, List.of(), List.of(), List.of()).give(plugin, winner);
                }
                winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
                winner.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, winner.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.3);
                Text.title(winner, Text.get("GIVEAWAY.WON-TITLE", "&d&lYOU WON!"), prizeText(giveaway), 5, 60, 15);
            });
        }
        Text.broadcast(Text.prefix() + Text.get("GIVEAWAY.WINNERS", "&d🎁 Giveaway winner{s}: &f{names} &7won {prize}&7!",
                "{s}", winners.size() == 1 ? "" : "s", "{names}", String.join("&7, &f", names), "{prize}", prizeText(giveaway)));
    }

    private void cancel(CommandSender sender) {
        if (!PermissionUtils.has(sender, HOST_PERMISSION)) {
            Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return;
        }
        Giveaway giveaway = active;
        if (giveaway == null) {
            Text.send(sender, "GIVEAWAY.NONE", "&7There is no giveaway running right now.");
            return;
        }
        active = null;
        giveaway.ticker.cancel();
        giveaway.bar.removeAll();
        refund(giveaway);
        Text.broadcast(Text.prefix() + Text.get("GIVEAWAY.CANCELLED", "&7The giveaway was cancelled."));
    }

    /** Gives an item prize back to the host, now or the next time they join. */
    private void refund(Giveaway giveaway) {
        refund(giveaway, false);
    }

    /** {@code now}: give it back on this thread (used while the plugin shuts down, when nothing can be scheduled). */
    private void refund(Giveaway giveaway, boolean now) {
        if (giveaway.item == null) {
            return;
        }
        Player host = giveaway.host == null ? null : Bukkit.getPlayer(giveaway.host);
        if (host != null) {
            Runnable giveBack = () -> {
                for (ItemStack rest : host.getInventory().addItem(giveaway.item.clone()).values()) {
                    host.getWorld().dropItemNaturally(host.getLocation(), rest);
                }
            };
            if (now) {
                giveBack.run();
            } else {
                plugin.getSpigotScheduler().runEntity(host, giveBack);
            }
            return;
        }
        // The host logged off: keep the item for them until they join again.
        if (giveaway.host != null) {
            PendingItems.add(plugin, giveaway.host, giveaway.item);
        }
    }

    public void shutdown() {
        Giveaway giveaway = active;
        active = null;
        if (giveaway != null) {
            giveaway.ticker.cancel();
            giveaway.bar.removeAll();
            refund(giveaway, true);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("join"));
            if (PermissionUtils.has(sender, HOST_PERMISSION)) {
                options.addAll(List.of("start", "money", "cancel"));
            }
            return options.stream().filter(o -> o.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
