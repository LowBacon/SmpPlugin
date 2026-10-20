package com.bx.smpPlugin.engagement;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /poll: staff ask a question, everyone votes with a click. A boss bar counts down and the result
 * is shown as a bar chart in chat.
 *
 * <pre>
 * /poll start 60 Best biome? | Desert | Jungle | Taiga
 * /poll vote 2        (what the chat buttons run)
 * /poll end           (finish early)
 * /poll               (see the question again)
 * </pre>
 */
public final class Polls implements CommandExecutor, TabCompleter {

    public static final String VOTE_PERMISSION = "smpplugin.poll.vote";
    public static final String CREATE_PERMISSION = "smpplugin.staff.poll";

    private final SmpPlugin plugin;
    private Poll active;

    private static final class Poll {
        final String question;
        final List<String> options;
        final Map<UUID, Integer> votes = new ConcurrentHashMap<>();
        final long endsAt;
        final long length;
        final BossBar bar;
        BukkitTask ticker;

        Poll(String question, List<String> options, long seconds) {
            this.question = question;
            this.options = options;
            this.length = seconds;
            this.endsAt = System.currentTimeMillis() + seconds * 1000L;
            this.bar = Bukkit.createBossBar(Text.color("&e📊 " + question), BarColor.YELLOW, BarStyle.SEGMENTED_10);
        }

        int[] counts() {
            int[] counts = new int[options.size()];
            for (int choice : votes.values()) {
                if (choice >= 0 && choice < counts.length) {
                    counts[choice]++;
                }
            }
            return counts;
        }
    }

    public Polls(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "show" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "start", "create" -> start(sender, args);
            case "vote" -> vote(sender, args);
            case "end", "stop" -> {
                if (!PermissionUtils.has(sender, CREATE_PERMISSION)) {
                    Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
                } else if (active == null) {
                    Text.send(sender, "POLL.NONE", "&7There is no poll running.");
                } else {
                    finish();
                }
            }
            default -> {
                if (active == null) {
                    Text.send(sender, "POLL.NONE", "&7There is no poll running.");
                    if (PermissionUtils.has(sender, CREATE_PERMISSION)) {
                        Text.send(sender, "POLL.USAGE", "&7Start one: &f/poll start <seconds> <question> | <option> | <option>");
                    }
                } else {
                    announce(sender);
                }
            }
        }
        return true;
    }

    private void start(CommandSender sender, String[] args) {
        if (!PermissionUtils.has(sender, CREATE_PERMISSION)) {
            Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return;
        }
        if (active != null) {
            Text.send(sender, "POLL.ALREADY", "&7A poll is already running. End it with &f/poll end&7.");
            return;
        }
        long seconds = plugin.getConfigManager().getExtras() == null ? 60L
                : plugin.getConfigManager().getExtras().getLong("POLLS.DEFAULT-SECONDS", 60L);
        int from = 1;
        if (args.length > 1 && args[1].matches("\\d+")) {
            seconds = Math.max(10L, Math.min(3600L, Long.parseLong(args[1])));
            from = 2;
        }
        String joined = String.join(" ", Arrays.copyOfRange(args, Math.min(from, args.length), args.length));
        List<String> parts = new ArrayList<>();
        for (String part : joined.split("\\|")) {
            if (!part.isBlank()) {
                parts.add(part.trim());
            }
        }
        int max = plugin.getConfigManager().getExtras() == null ? 6 : plugin.getConfigManager().getExtras().getInt("POLLS.MAX-OPTIONS", 6);
        if (parts.size() < 3 || parts.size() - 1 > Math.max(2, max)) {
            Text.send(sender, "POLL.USAGE", "&7Start one: &f/poll start <seconds> <question> | <option> | <option>");
            return;
        }
        Poll poll = new Poll(parts.get(0), List.copyOf(parts.subList(1, parts.size())), seconds);
        active = poll;
        Bukkit.getOnlinePlayers().forEach(poll.bar::addPlayer);
        poll.ticker = plugin.getSpigotScheduler().runGlobalTimer(() -> tick(poll), 20L, 20L);
        for (Player player : Bukkit.getOnlinePlayers()) {
            announce(player);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6F, 1.4F);
        }
    }

    private void tick(Poll poll) {
        if (active != poll) {
            poll.ticker.cancel();
            return;
        }
        long left = Math.max(0L, (poll.endsAt - System.currentTimeMillis()) / 1000L);
        poll.bar.setProgress(Math.max(0D, Math.min(1D, left / (double) poll.length)));
        poll.bar.setTitle(Text.color("&e📊 " + poll.question + " &7(" + Text.duration(left) + ", " + poll.votes.size() + " votes)"));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!poll.bar.getPlayers().contains(player)) {
                poll.bar.addPlayer(player);
            }
        }
        if (left <= 0L) {
            finish();
        }
    }

    private void announce(CommandSender to) {
        Poll poll = active;
        if (poll == null) {
            return;
        }
        Text.raw(to, "");
        Text.raw(to, Text.get("POLL.HEADER", "&e&l📊 POLL &8» &f{question}", "{question}", poll.question));
        for (int i = 0; i < poll.options.size(); i++) {
            Text.line(to, "  ", Text.button("&e[" + (i + 1) + "] &f" + poll.options.get(i), "poll vote " + (i + 1),
                    Text.get("POLL.HOVER", "&7Click to vote for &f{option}", "{option}", poll.options.get(i))));
        }
        Text.raw(to, Text.get("POLL.FOOTER", "&7Click an option to vote. You can change your vote."));
    }

    private void vote(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return;
        }
        if (!PermissionUtils.has(player, VOTE_PERMISSION)) {
            Text.send(player, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return;
        }
        Poll poll = active;
        if (poll == null) {
            Text.send(player, "POLL.NONE", "&7There is no poll running.");
            return;
        }
        int choice;
        try {
            choice = Integer.parseInt(args.length > 1 ? args[1] : "") - 1;
        } catch (NumberFormatException ignored) {
            choice = -1;
        }
        if (choice < 0 || choice >= poll.options.size()) {
            announce(player);
            return;
        }
        Integer before = poll.votes.put(player.getUniqueId(), choice);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6F, 1.5F);
        Text.send(player, before == null ? "POLL.VOTED" : "POLL.CHANGED",
                before == null ? "&aYou voted for &f{option}&a." : "&aYou changed your vote to &f{option}&a.",
                "{option}", poll.options.get(choice));
    }

    private void finish() {
        Poll poll = active;
        if (poll == null) {
            return;
        }
        active = null;
        if (poll.ticker != null) {
            poll.ticker.cancel();
        }
        poll.bar.removeAll();
        int[] counts = poll.counts();
        int total = Math.max(1, poll.votes.size());
        int best = 0;
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] > counts[best]) {
                best = i;
            }
        }
        Text.broadcast("");
        Text.broadcast(Text.get("POLL.RESULT-HEADER", "&e&l📊 POLL RESULTS &8» &f{question}", "{question}", poll.question));
        for (int i = 0; i < counts.length; i++) {
            int percent = Math.round(counts[i] * 100F / total);
            Text.broadcast("  " + (i == best && counts[i] > 0 ? "&a" : "&7") + poll.options.get(i) + " "
                    + Text.bar(counts[i] / (double) total, 12, "&e", "&8") + " &f" + percent + "% &8(" + counts[i] + ")");
        }
        Text.broadcast(Text.get("POLL.RESULT-FOOTER", "&7{votes} vote(s) in total.", "{votes}", Integer.toString(poll.votes.size())));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5F, 1.6F);
        }
    }

    public void shutdown() {
        Poll poll = active;
        active = null;
        if (poll != null) {
            if (poll.ticker != null) {
                poll.ticker.cancel();
            }
            poll.bar.removeAll();
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("vote"));
            if (PermissionUtils.has(sender, CREATE_PERMISSION)) {
                options.addAll(List.of("start", "end"));
            }
            return options.stream().filter(o -> o.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("vote") && active != null) {
            List<String> numbers = new ArrayList<>();
            for (int i = 1; i <= active.options.size(); i++) {
                numbers.add(Integer.toString(i));
            }
            return numbers;
        }
        return List.of();
    }
}
