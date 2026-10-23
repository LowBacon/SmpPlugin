// SmpPlugin by LowBacon
package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.OffenseManager;
import com.bx.smpPlugin.managers.PunishmentManager;
import com.bx.smpPlugin.models.PunishmentRecord;
import com.bx.smpPlugin.models.PunishmentScope;
import com.bx.smpPlugin.models.PunishmentType;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import com.bx.smpPlugin.utils.PunishmentExemptPolicy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class OffendCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "smpplugin.staff.punishments.offend";
    private static final List<String> DURATION_SUGGESTIONS =
            List.of("24h", "3d", "7d", "14d", "30d", "60d", "180d", "360d", "perm");

    private final SmpPlugin plugin;
    private final PunishmentMessages messages;

    public OffendCommand(SmpPlugin plugin) {
        this.plugin = plugin;
        this.messages = new PunishmentMessages(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player && !PermissionUtils.has(sender, PERMISSION)) {
            send(sender, plugin.getConfigManager().getMessageOrDefault(
                    "PUNISHMENTS.NO-CREATE-PERMISSION",
                    "&cYou do not have permission to create punishments."
            ));
            return true;
        }

        if (args.length == 0) {
            send(sender, plugin.getConfigManager().getMessageOrDefault(
                    "PUNISHMENTS.OFFEND-USAGE",
                    "&cUsage: /offend <player> <offense> [time] &7| /offend <player> &7| /offend list"
            ));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            sendOffenseList(sender);
            return true;
        }

        ResolvedTarget target = resolveTarget(args[0]);
        if (target == null) {
            send(sender, plugin.getConfigManager().getMessageOrDefault("PUNISHMENTS.NOT-FOUND", "&cPlayer not found."));
            return true;
        }

        if (args.length == 1) {
            sendPreview(sender, target);
            return true;
        }

        Player onlineTarget = Bukkit.getPlayer(target.uuid());
        if (!PunishmentExemptPolicy.canPunish(sender, onlineTarget)) {
            send(sender, plugin.getConfigManager().getMessageOrDefault(
                    "PUNISHMENTS.TARGET-EXEMPT",
                    "&cYou cannot punish that player."
            ));
            return true;
        }
        if (sender instanceof Player player && player.getUniqueId().equals(target.uuid())) {
            send(sender, plugin.getConfigManager().getMessageOrDefault(
                    "PUNISHMENTS.OFFEND-SELF",
                    "&cYou cannot punish yourself."
            ));
            return true;
        }

        OffenseManager offenseManager = plugin.getOffenseManager();
        Optional<OffenseManager.OffenseRule> ruleOpt = offenseManager.getOffenseRule(args[1]);
        if (ruleOpt.isEmpty()) {
            send(sender, plugin.getConfigManager().getMessageOrDefault(
                    "PUNISHMENTS.OFFEND-UNKNOWN",
                    "&cUnknown offense &f{offense}&c. Use &f/offend list &cto see them.",
                    "{offense}", args[1]
            ));
            return true;
        }

        OffenseManager.OffenseRule rule = ruleOpt.get();
        PunishmentType type = rule.type();
        int previous = offenseManager.countPreviousOffenses(target.uuid(), rule);
        String durationText = args.length >= 3 ? args[2] : rule.getDurationForTier(previous);

        Long durationMillis = OffenseManager.parseDurationToMillis(durationText);
        if (durationMillis != null && durationMillis == 0L) {
            // A "0s" rung is a warning, whatever the offense's type.
            type = PunishmentType.WARN;
        }
        if (type == PunishmentType.KICK || type == PunishmentType.WARN) {
            durationMillis = null;
        }
        if (type == PunishmentType.KICK && onlineTarget == null) {
            send(sender, plugin.getConfigManager().getMessageOrDefault(
                    "PUNISHMENTS.TARGET-OFFLINE",
                    "&cThat player is not online."
            ));
            return true;
        }

        long now = System.currentTimeMillis();
        Long expiresAt = durationMillis != null && durationMillis > 0L ? now + durationMillis : null;
        Actor actor = resolveActor(sender);
        PunishmentRecord record = plugin.getPunishmentManager().createRecord(new PunishmentManager.PunishmentCreateRequest(
                target.uuid(),
                target.name(),
                type,
                rule.reasonText(),
                actor.uuid(),
                actor.name(),
                now,
                expiresAt,
                "local",
                PunishmentScope.SERVER
        ));

        if (record == null) {
            send(sender, plugin.getConfigManager().getMessageOrDefault(
                    "PUNISHMENTS.CREATE-FAILED",
                    "&cFailed to create punishment record."
            ));
            return true;
        }

        messages.applyRuntimeEffect(type, onlineTarget, record);
        if (plugin.getDiscordWebhookManager() != null) {
            plugin.getDiscordWebhookManager().sendPunishment(record);
        }

        send(sender, plugin.getConfigManager().getMessageOrDefault(
                "PUNISHMENTS.OFFEND-SUCCESS",
                "&aIssued &f{type} &ato &b{player}&a for &f{offense}&a. &7(offense #{count}, duration: &f{duration}&7, id: &f#{id}&7)",
                "{type}", plugin.getPunishmentManager().getDisplayType(record),
                "{player}", target.name(),
                "{offense}", rule.name(),
                "{count}", String.valueOf(previous + 1),
                "{duration}", describeDuration(type, expiresAt, now),
                "{id}", String.valueOf(record.getId())
        ));
        return true;
    }

    private void sendOffenseList(CommandSender sender) {
        send(sender, plugin.getConfigManager().getMessageOrDefault(
                "PUNISHMENTS.OFFEND-LIST-HEADER", "&8&m----&r &bOffenses &8&m----"));
        for (OffenseManager.OffenseRule rule : plugin.getOffenseManager().getOffenses().values()) {
            send(sender, "&f" + rule.key() + " &8- &7" + rule.name() + " &8(&e" + rule.type().name()
                    + "&8: &7" + String.join(" &8> &7", rule.durations().isEmpty() ? List.of("perm") : rule.durations()) + "&8)");
        }
    }

    private void sendPreview(CommandSender sender, ResolvedTarget target) {
        send(sender, plugin.getConfigManager().getMessageOrDefault(
                "PUNISHMENTS.OFFEND-PREVIEW-HEADER",
                "&8&m----&r &bNext punishment for &f{player} &8&m----",
                "{player}", target.name()));
        OffenseManager offenseManager = plugin.getOffenseManager();
        for (OffenseManager.OffenseRule rule : offenseManager.getOffenses().values()) {
            int previous = offenseManager.countPreviousOffenses(target.uuid(), rule);
            String next = rule.getDurationForTier(previous);
            PunishmentType type = "0s".equalsIgnoreCase(next) || "0".equals(next) ? PunishmentType.WARN : rule.type();
            String count = previous > 0 ? " &c(" + previous + " before)" : "";
            send(sender, "&f" + rule.key() + " &8- &e" + type.name() + " &7" + next + count);
        }
    }

    private String describeDuration(PunishmentType type, Long expiresAt, long now) {
        if (type == PunishmentType.WARN || type == PunishmentType.KICK) {
            return "-";
        }
        if (expiresAt == null) {
            return "Permanent";
        }
        return NumberUtils.formatCountdown(Math.max(0L, (expiresAt - now) / 1000L));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (sender instanceof Player && !PermissionUtils.has(sender, PERMISSION)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            String partial = args[0].toLowerCase(Locale.ROOT);
            if ("list".startsWith(partial)) {
                options.add("list");
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(partial)) {
                    options.add(player.getName());
                }
            }
            return options;
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("list")) {
            return filter(new ArrayList<>(plugin.getOffenseManager().getOffenseKeys()), args[1]);
        }
        if (args.length == 3) {
            return filter(DURATION_SUGGESTIONS, args[2]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> options, String partial) {
        String lower = partial.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }

    private ResolvedTarget resolveTarget(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online == null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().equalsIgnoreCase(input)) {
                    online = player;
                    break;
                }
            }
        }
        if (online != null) {
            return new ResolvedTarget(online.getUniqueId(), online.getName());
        }

        UUID knownUuid = plugin.getPunishmentManager().resolveTargetUuid(input, true).orElse(null);
        if (knownUuid != null) {
            return new ResolvedTarget(knownUuid, plugin.getPunishmentManager().resolveTargetName(knownUuid, input));
        }
        return null;
    }

    private Actor resolveActor(CommandSender sender) {
        if (sender instanceof Player player) {
            return new Actor(player.getUniqueId(), player.getName());
        }
        return new Actor(null, "console");
    }

    private void send(CommandSender sender, String message) {
        if (sender != null && message != null && !message.isBlank()) {
            sender.sendMessage(ColorUtils.toComponent(message));
        }
    }

    private record ResolvedTarget(UUID uuid, String name) {
    }

    private record Actor(UUID uuid, String name) {
    }
}
