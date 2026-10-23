// SmpPlugin by LowBacon
package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.coinflip.Achievement;
import com.bx.smpPlugin.coinflip.CoinFlipConfig;
import com.bx.smpPlugin.coinflip.CoinFlipMatch;
import com.bx.smpPlugin.coinflip.CoinFlipNotifier;
import com.bx.smpPlugin.coinflip.CoinFlipPreferences;
import com.bx.smpPlugin.coinflip.CoinFlipService;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import com.bx.smpPlugin.utils.TitleUtils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CoinFlipPresenter implements CoinFlipNotifier {

    private final SmpPlugin plugin;
    private final CoinFlipModule module;
    private static final long REMATCH_WINDOW_MILLIS = 300_000L;

    public record Rematch(UUID opponent, String opponentName, long stakeCents, long atMillis) {
    }

    private final Map<UUID, Rematch> rematches = new java.util.HashMap<>();
    private long lastBroadcastMillis;
    private boolean refreshScheduled;

    public CoinFlipPresenter(SmpPlugin plugin, CoinFlipModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public void send(UUID player, String key, Map<String, String> placeholders) {
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            online.sendMessage(ColorUtils.colorize(module.messages().chat(key, placeholders)));
        }
    }

    public void sendOutcome(UUID player, CoinFlipService.Outcome outcome) {
        Map<String, String> placeholders = new java.util.LinkedHashMap<>(outcome.placeholders());
        if ("invalid-amount".equals(outcome.messageKey()) && placeholders.containsKey("reason")) {
            String reasonKey = "amount-" + placeholders.get("reason");
            if (module.messages().has(reasonKey)) {
                placeholders.put("reason", module.messages().raw(reasonKey, Map.of()));
            }
        }
        send(player, outcome.messageKey(), placeholders);
        if (!outcome.success()) {
            sound(player, "ERROR");
        }
    }

    @Override
    public void challengeReceived(CoinFlipMatch match) {
        Player invitee = match.inviteeId() == null ? null : Bukkit.getPlayer(match.inviteeId());
        if (invitee == null) {
            return;
        }
        Map<String, String> vars = module.service().matchVars(match);
        invitee.sendMessage(ColorUtils.colorize(module.messages().chat("challenge-received", vars)));
        TextComponent line = ColorUtils.toBaseComponent(module.messages().raw("prefix", Map.of()));
        TextComponent review = ColorUtils.toBaseComponent("&a&l[REVIEW]");
        review.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/coinflip accept " + match.id()));
        review.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                ColorUtils.toBaseComponents("&7See the stake, fee and payout before you accept.")));
        TextComponent decline = ColorUtils.toBaseComponent("&c&l[DECLINE]");
        decline.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/coinflip deny " + match.id()));
        decline.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                ColorUtils.toBaseComponents("&7Decline; their stake is returned.")));
        line.addExtra(review);
        line.addExtra(" ");
        line.addExtra(decline);
        try {
            invitee.spigot().sendMessage(line);
        } catch (RuntimeException | LinkageError unsupported) {
            // clickable text is not available; the typed-command line below always works
        }
        invitee.sendMessage(ColorUtils.colorize(module.messages().chat("challenge-fallback", vars)));
        sound(invitee.getUniqueId(), "INVITE");
    }

    @Override
    public long beginReveal(CoinFlipMatch match) {
        long longest = 0L;
        for (UUID participant : new UUID[]{match.creatorId(), match.opponentId()}) {
            Player player = participant == null ? null : Bukkit.getPlayer(participant);
            if (player != null) {
                longest = Math.max(longest, module.animator().startMatch(player, match));
            }
        }
        // A short pause after the last frame so the coin is visible before the result message.
        return Math.min(220L, longest == 0L ? 0L : longest + 10L);
    }

    @Override
    public void matchCompleted(CoinFlipMatch match, boolean winnerPaidNow) {
        CoinFlipConfig config = module.config();
        Map<String, String> vars = module.service().matchVars(match);
        UUID winner = match.winnerId();
        UUID loser = match.loserId();
        vars.put("loser", winner.equals(match.creatorId()) ? match.opponentName() : match.creatorName());

        Player winnerPlayer = Bukkit.getPlayer(winner);
        if (winnerPlayer != null) {
            send(winner, winnerPaidNow ? "result-win" : "result-win-claim", vars);
            title(winnerPlayer, "title-win", "subtitle-win", vars);
            sound(winner, "WIN");
        }
        Player loserPlayer = loser == null ? null : Bukkit.getPlayer(loser);
        if (loserPlayer != null) {
            send(loser, "result-loss", vars);
            title(loserPlayer, "title-result", "subtitle-result", vars);
            sound(loser, "RESULT");
        }
        module.animator().finish(match.id());
        offerRematch(match, match.creatorId(), match.opponentId(), match.opponentName());
        offerRematch(match, match.opponentId(), match.creatorId(), match.creatorName());

        CoinFlipPreferences winnerPrefs = module.service().preferences(winner);
        long now = System.currentTimeMillis();
        if (config.broadcastEnabled() && match.potCents() >= config.broadcastMinPotCents() && winnerPrefs.announceMyWins()
                && now - lastBroadcastMillis >= config.broadcastCooldownMillis()) {
            lastBroadcastMillis = now;
            String text = ColorUtils.colorize(module.messages().chat("broadcast", vars));
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(text);
            }
        }
        module.webhook().matchCompleted(match);
    }

    public java.util.Optional<Rematch> rematchFor(UUID player) {
        Rematch rematch = rematches.get(player);
        if (rematch == null || System.currentTimeMillis() - rematch.atMillis() > REMATCH_WINDOW_MILLIS) {
            rematches.remove(player);
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(rematch);
    }

    public void forget(UUID player) {
        rematches.remove(player);
    }

    private void offerRematch(CoinFlipMatch match, UUID player, UUID opponent, String opponentName) {
        CoinFlipConfig config = module.config();
        Player online = Bukkit.getPlayer(player);
        if (!config.allowRematch() || !config.allowPrivate() || online == null || opponent == null) {
            return;
        }
        rematches.put(player, new Rematch(opponent, opponentName, match.stakeCents(), System.currentTimeMillis()));
        TextComponent line = ColorUtils.toBaseComponent(module.messages().raw("prefix", Map.of()));
        TextComponent button = ColorUtils.toBaseComponent("&d&l[REMATCH]");
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/coinflip rematch"));
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, ColorUtils.toBaseComponents(
                "&7Offer " + opponentName + " a new challenge for the same stake. You review it first; they must accept.")));
        line.addExtra(button);
        try {
            online.spigot().sendMessage(line);
        } catch (RuntimeException | LinkageError ignored) {
            // /coinflip rematch still works when typed
        }
    }

    @Override
    public void achievementsUnlocked(UUID player, List<Achievement> achievements) {
        CoinFlipConfig config = module.config();
        Player online = Bukkit.getPlayer(player);
        if (config.achievementNotify() && online != null) {
            for (Achievement achievement : achievements) {
                send(player, "achievement-unlocked", CoinFlipService.vars("name", achievement.displayName(),
                        "description", achievement.description()));
            }
            sound(player, "WIN");
        }
        if (!config.achievementRewardsEnabled()) {
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(player);
        String name = target.getName();
        if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) {
            return;
        }
        for (Achievement achievement : achievements) {
            for (String command : config.achievementRewards(achievement)) {
                String resolved = command.replace("{player}", name);
                plugin.getLogger().info("[CoinFlip] Achievement reward for " + name + " (" + achievement.name() + "): /" + resolved);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
            }
        }
    }

    @Override
    public void staffAlert(String message) {
        plugin.getLogger().warning("[CoinFlip] " + message);
        String text = ColorUtils.colorize("&8[&5CoinFlip&8] &c" + message);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (PermissionUtils.has(online, CoinFlipModule.PERM_ALERTS)) {
                online.sendMessage(text);
            }
        }
    }

    @Override
    public void matchesChanged() {
        if (refreshScheduled) {
            return;
        }
        refreshScheduled = true;
        plugin.getSpigotScheduler().runGlobalLater(() -> {
            refreshScheduled = false;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getOpenInventory().getTopInventory().getHolder() instanceof CoinFlipMenu menu && menu.live()) {
                    menu.refresh();
                }
            }
        }, 1L);
    }

    void sound(UUID player, String key) {
        Player online = Bukkit.getPlayer(player);
        CoinFlipConfig config = module.config();
        if (online != null && config.soundsAllowed() && module.service().preferences(player).sounds()) {
            SoundUtils.play(online, config.sound(key));
        }
    }

    private void title(Player player, String titleKey, String subtitleKey, Map<String, String> vars) {
        TitleUtils.sendTitle(player, module.messages().raw(titleKey, vars), module.messages().raw(subtitleKey, vars), 5, 50, 15);
    }
}
