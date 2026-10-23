// SmpPlugin by LowBacon
package com.bx.smpPlugin.moderation;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.managers.EconomyManager;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.models.PunishmentType;
import com.bx.smpPlugin.models.Team;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import com.bx.smpPlugin.utils.RolePrefixResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class AdminCommands implements CommandExecutor, TabCompleter {

    private final SmpPlugin plugin;
    private BossBar countdownBar;

    public AdminCommands(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    public static String permission(String command) {
        return switch (command) {
            case "broadcast" -> "smpplugin.admin.broadcast";
            case "day", "night" -> "smpplugin.admin.time";
            case "sun", "rain", "thunder" -> "smpplugin.admin.weather";
            case "smite" -> "smpplugin.admin.smite";
            case "butcher" -> "smpplugin.admin.butcher";
            case "spawnmob" -> "smpplugin.admin.spawnmob";
            case "more" -> "smpplugin.admin.more";
            case "countdown" -> "smpplugin.staff.countdown";
            case "performance" -> "smpplugin.admin.performance";
            case "speed" -> "smpplugin.staff.speed";
            case "whois" -> "smpplugin.staff.whois";
            case "spook" -> "smpplugin.admin.spook";
            default -> "smpplugin.admin";
        };
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!PermissionUtils.has(sender, permission(name))) {
            Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        switch (name) {
            case "broadcast" -> broadcast(sender, args);
            case "day" -> setTime(sender, args, 1000L, "day");
            case "night" -> setTime(sender, args, 14000L, "night");
            case "sun" -> setWeather(sender, args, false, false);
            case "rain" -> setWeather(sender, args, true, false);
            case "thunder" -> setWeather(sender, args, true, true);
            case "smite" -> smite(sender, args);
            case "butcher" -> butcher(sender, args);
            case "spawnmob" -> spawnMob(sender, args);
            case "more" -> more(sender);
            case "countdown" -> countdown(sender, args);
            case "performance" -> performance(sender);
            case "speed" -> speed(sender, args);
            case "whois" -> whois(sender, args);
            case "spook" -> spook(sender, args);
            default -> {
            }
        }
        return true;
    }

    private Player findOnline(CommandSender sender, String name) {
        Player target = plugin.getHideManager() == null ? Bukkit.getPlayerExact(name)
                : plugin.getHideManager().findOnlinePlayer(sender, name);
        if (target == null) {
            target = Bukkit.getPlayerExact(name);
        }
        if (target == null) {
            Text.send(sender, "GENERAL.PLAYER-NOT-FOUND", "&cThat player is not online.");
        }
        return target;
    }

    // ------------------------------------------------------------------ broadcast and countdown

    private void broadcast(CommandSender sender, String[] args) {
        if (args.length == 0) {
            Text.send(sender, "ADMIN.BROADCAST-USAGE", "&7/broadcast [-title] <message>");
            return;
        }
        boolean asTitle = args[0].equalsIgnoreCase("-title") || args[0].equalsIgnoreCase("-t");
        String message = String.join(" ", Arrays.copyOfRange(args, asTitle ? 1 : 0, args.length));
        if (message.isBlank()) {
            return;
        }
        Text.broadcast("");
        Text.broadcast(Text.get("ADMIN.BROADCAST", "&8[&6&lANNOUNCEMENT&8] &f{message}", "{message}", message));
        Text.broadcast("");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8F, 1.0F);
            if (asTitle) {
                Text.title(player, Text.get("ADMIN.BROADCAST-TITLE", "&6&lANNOUNCEMENT"), "&f" + message, 10, 70, 20);
            }
        }
    }

    private void countdown(CommandSender sender, String[] args) {
        int seconds;
        try {
            seconds = Math.max(1, Math.min(600, Integer.parseInt(args.length > 0 ? args[0] : "")));
        } catch (NumberFormatException ignored) {
            Text.send(sender, "ADMIN.COUNTDOWN-USAGE", "&7/countdown <seconds> [what is starting]");
            return;
        }
        String what = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "";
        if (countdownBar != null) {
            countdownBar.removeAll();
        }
        BossBar bar = Bukkit.createBossBar(Text.color("&e" + (what.isBlank() ? "Countdown" : what)), BarColor.YELLOW, BarStyle.SOLID);
        countdownBar = bar;
        Bukkit.getOnlinePlayers().forEach(bar::addPlayer);
        AtomicInteger left = new AtomicInteger(seconds);
        org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
        task[0] = plugin.getSpigotScheduler().runGlobalTimer(() -> {
            int now = left.getAndDecrement();
            if (countdownBar != bar) {
                task[0].cancel();
                return;
            }
            bar.setProgress(Math.max(0D, Math.min(1D, now / (double) seconds)));
            bar.setTitle(Text.color("&e" + (what.isBlank() ? "Starting" : what) + " &7in &f" + Text.duration(Math.max(0, now))));
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!bar.getPlayers().contains(player)) {
                    bar.addPlayer(player);
                }
                if (now <= 5 && now > 0) {
                    Text.title(player, "&e&l" + now, what.isBlank() ? "" : "&7" + what, 0, 22, 0);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 0.8F + (5 - now) * 0.1F);
                } else if (now == 0) {
                    Text.title(player, Text.get("ADMIN.COUNTDOWN-GO", "&a&lGO!"), what.isBlank() ? "" : "&7" + what, 0, 30, 10);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9F, 1.2F);
                }
            }
            if (now <= 0) {
                bar.removeAll();
                countdownBar = null;
                task[0].cancel();
            }
        }, 1L, 20L);
    }

    public void shutdown() {
        if (countdownBar != null) {
            countdownBar.removeAll();
            countdownBar = null;
        }
    }

    // ------------------------------------------------------------------ world

    private List<World> worlds(CommandSender sender, String[] args) {
        if (args.length > 0) {
            World world = Bukkit.getWorld(args[0]);
            if (world == null) {
                Text.send(sender, "ADMIN.NO-WORLD", "&cThere is no world called &f{world}&c.", "{world}", args[0]);
                return List.of();
            }
            return List.of(world);
        }
        if (sender instanceof Player player) {
            return List.of(player.getWorld());
        }
        return List.of(Bukkit.getWorlds().get(0));
    }

    private void setTime(CommandSender sender, String[] args, long time, String label) {
        for (World world : worlds(sender, args)) {
            plugin.getSpigotScheduler().runGlobal(() -> world.setTime(time));
            Text.send(sender, "ADMIN.TIME", "&7Set the time in &f{world} &7to &f{time}&7.", "{world}", world.getName(), "{time}", label);
        }
    }

    private void setWeather(CommandSender sender, String[] args, boolean storm, boolean thunder) {
        for (World world : worlds(sender, args)) {
            plugin.getSpigotScheduler().runGlobal(() -> {
                world.setStorm(storm);
                world.setThundering(thunder);
                if (!storm) {
                    world.setClearWeatherDuration(20 * 60 * 15);
                }
            });
            Text.send(sender, "ADMIN.WEATHER", "&7Set the weather in &f{world} &7to &f{weather}&7.",
                    "{world}", world.getName(), "{weather}", thunder ? "thunder" : storm ? "rain" : "clear");
        }
    }

    private void smite(CommandSender sender, String[] args) {
        if (args.length == 0) {
            Text.send(sender, "ADMIN.SMITE-USAGE", "&7/smite <player> [-damage]");
            return;
        }
        Player target = findOnline(sender, args[0]);
        if (target == null) {
            return;
        }
        boolean damage = args.length > 1 && args[1].equalsIgnoreCase("-damage");
        plugin.getSpigotScheduler().runEntity(target, () -> {
            if (damage) {
                target.getWorld().strikeLightning(target.getLocation());
            } else {
                target.getWorld().strikeLightningEffect(target.getLocation());
            }
        });
        Text.send(sender, "ADMIN.SMITE", "&eSmote &f{player}&e{how}.", "{player}", target.getName(), "{how}", damage ? "" : " &7(harmless)");
    }

    private void butcher(CommandSender sender, String[] args) {
        int radius = 64;
        boolean everything = false;
        for (String arg : args) {
            if (arg.equalsIgnoreCase("all")) {
                everything = true;
            } else {
                try {
                    radius = Math.max(1, Math.min(500, Integer.parseInt(arg)));
                } catch (NumberFormatException ignored) {
                    Text.send(sender, "ADMIN.BUTCHER-USAGE", "&7/butcher [radius] [all] &8(all = animals too)");
                    return;
                }
            }
        }
        int removed = 0;
        Location center = sender instanceof Player player ? player.getLocation() : null;
        double radiusSquared = (double) radius * radius;
        for (World world : center == null ? Bukkit.getWorlds() : List.of(center.getWorld())) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity instanceof Player || !removable(entity, everything)) {
                    continue;
                }
                if (center != null && entity.getLocation().distanceSquared(center) > radiusSquared) {
                    continue;
                }
                entity.remove();
                removed++;
            }
        }
        Text.send(sender, "ADMIN.BUTCHER", "&7Removed &f{count} &7mob(s).", "{count}", Integer.toString(removed));
    }

    static boolean removable(LivingEntity entity, boolean animalsToo) {
        if (entity.getCustomName() != null || entity.isLeashed()) {
            return false;
        }
        if (entity instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }
        EntityType type = entity.getType();
        if (type == EntityType.VILLAGER || type == EntityType.WANDERING_TRADER || type == EntityType.IRON_GOLEM
                || type == EntityType.ARMOR_STAND || type == EntityType.SNOW_GOLEM || type == EntityType.ALLAY) {
            return false;
        }
        if (entity instanceof Monster) {
            return true;
        }
        return animalsToo && (entity instanceof Animals || entity instanceof Ageable);
    }

    private void spawnMob(CommandSender sender, String[] args) {
        if (args.length == 0) {
            Text.send(sender, "ADMIN.SPAWNMOB-USAGE", "&7/spawnmob <type> [amount] [player]");
            return;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(args[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            type = null;
        }
        if (type == null || !type.isSpawnable() || !type.isAlive() || type == EntityType.PLAYER
                || type == EntityType.ENDER_DRAGON || type == EntityType.WITHER) {
            Text.send(sender, "ADMIN.SPAWNMOB-TYPE", "&cYou can't spawn &f{type}&c this way.", "{type}", args[0]);
            return;
        }
        int amount = 1;
        if (args.length > 1) {
            try {
                amount = Math.max(1, Math.min(50, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
                amount = 1;
            }
        }
        Location at;
        if (args.length > 2) {
            Player target = findOnline(sender, args[2]);
            if (target == null) {
                return;
            }
            at = target.getLocation();
        } else if (sender instanceof Player player) {
            Block looking = player.getTargetBlockExact(40);
            at = looking == null ? player.getLocation() : looking.getLocation().add(0.5, 1, 0.5);
        } else {
            Text.send(sender, "ADMIN.SPAWNMOB-USAGE", "&7/spawnmob <type> [amount] [player]");
            return;
        }
        EntityType finalType = type;
        int finalAmount = amount;
        Location finalAt = at;
        plugin.getSpigotScheduler().runRegion(at, () -> {
            for (int i = 0; i < finalAmount; i++) {
                finalAt.getWorld().spawnEntity(finalAt, finalType);
            }
        });
        Text.send(sender, "ADMIN.SPAWNMOB", "&7Spawned &f{amount}x {type}&7.", "{amount}", Integer.toString(amount),
                "{type}", type.name().toLowerCase(Locale.ROOT));
    }

    private void more(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            Text.send(player, "ADMIN.MORE-EMPTY", "&7Hold the item you want more of.");
            return;
        }
        hand.setAmount(hand.getMaxStackSize());
        Text.send(player, "ADMIN.MORE", "&7Filled the stack to &f{amount}&7.", "{amount}", Integer.toString(hand.getMaxStackSize()));
    }

    // ------------------------------------------------------------------ information

    private void performance(CommandSender sender) {
        double tps = plugin.getOptimizationManager() == null ? -1D : plugin.getOptimizationManager().getLastTps();
        double mspt = plugin.getOptimizationManager() == null ? -1D : plugin.getOptimizationManager().getLastMspt();
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
        long max = runtime.maxMemory() / (1024L * 1024L);
        Text.send(sender, "ADMIN.PERF-HEADER", "&6&lPerformance");
        Text.raw(sender, "&7TPS: " + (tps < 0 ? "&7measuring" : (tps >= 18 ? "&a" : tps >= 15 ? "&e" : "&c")
                + String.format(Locale.ROOT, "%.2f", Math.min(20D, tps)))
                + " &8| &7MSPT: " + (mspt < 0 ? "&7?" : (mspt <= 40 ? "&a" : mspt <= 50 ? "&e" : "&c") + String.format(Locale.ROOT, "%.1f", mspt))
                + " &8| &7Memory: &f" + used + "&7/&f" + max + " MB " + Text.bar(used / (double) Math.max(1L, max), 10, "&c", "&8"));
        Map<EntityType, Integer> types = new EnumMap<>(EntityType.class);
        for (World world : Bukkit.getWorlds()) {
            List<Entity> entities = world.getEntities();
            for (Entity entity : entities) {
                types.merge(entity.getType(), 1, Integer::sum);
            }
            Text.raw(sender, "&7" + world.getName() + ": &f" + world.getLoadedChunks().length + " &7chunks, &f"
                    + entities.size() + " &7entities, &f" + world.getPlayers().size() + " &7players");
        }
        List<Map.Entry<EntityType, Integer>> top = new ArrayList<>(types.entrySet());
        top.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        StringBuilder line = new StringBuilder("&7Most common: ");
        for (int i = 0; i < Math.min(5, top.size()); i++) {
            line.append("&f").append(top.get(i).getKey().name().toLowerCase(Locale.ROOT)).append(" &8x").append(top.get(i).getValue()).append("  ");
        }
        Text.raw(sender, line.toString());
    }

    private void speed(CommandSender sender, String[] args) {
        if (args.length == 0) {
            Text.send(sender, "ADMIN.SPEED-USAGE", "&7/speed <1-10> [walk|fly] [player]");
            return;
        }
        float level;
        try {
            level = Math.max(0F, Math.min(10F, Float.parseFloat(args[0])));
        } catch (NumberFormatException ignored) {
            Text.send(sender, "ADMIN.SPEED-USAGE", "&7/speed <1-10> [walk|fly] [player]");
            return;
        }
        Player target = sender instanceof Player player ? player : null;
        String mode = null;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("walk") || args[i].equalsIgnoreCase("fly")) {
                mode = args[i].toLowerCase(Locale.ROOT);
            } else {
                target = findOnline(sender, args[i]);
                if (target == null) {
                    return;
                }
            }
        }
        if (target == null) {
            Text.send(sender, "ADMIN.SPEED-USAGE", "&7/speed <1-10> [walk|fly] [player]");
            return;
        }
        boolean fly = mode == null ? target.isFlying() : mode.equals("fly");
        float value = speedValue(level, fly);
        Player finalTarget = target;
        float finalValue = value;
        plugin.getSpigotScheduler().runEntity(target, () -> {
            if (fly) {
                finalTarget.setFlySpeed(finalValue);
            } else {
                finalTarget.setWalkSpeed(finalValue);
            }
        });
        Text.send(sender, "ADMIN.SPEED", "&7Set &f{player}&7's {mode} speed to &f{level}&7.",
                "{player}", target.getName(), "{mode}", fly ? "fly" : "walk", "{level}", String.format(Locale.ROOT, "%.1f", level));
    }

    static float speedValue(float level, boolean fly) {
        float normal = fly ? 0.1F : 0.2F;
        if (level < 1F) {
            return normal * Math.max(0F, level);
        }
        return Math.min(1F, normal + (level - 1F) / 9F * (1F - normal));
    }

    @SuppressWarnings("deprecation")
    private void whois(CommandSender sender, String[] args) {
        if (args.length == 0) {
            Text.send(sender, "ADMIN.WHOIS-USAGE", "&7/whois <player>");
            return;
        }
        com.bx.smpPlugin.core.Lookup.account(plugin, sender, args[0], account -> {
            if (account == null) {
                Text.send(sender, "UTIL.SEEN-UNKNOWN", "&cNo player called &f{player} &chas joined this server.", "{player}", args[0]);
            } else {
                whois(sender, account);
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void whois(CommandSender sender, EconomyManager.AccountReference account) {
        Player online = Bukkit.getPlayer(account.uuid());
        OfflinePlayer offline = Bukkit.getOfflinePlayer(account.uuid());
        PlayerData data = plugin.getPlayerDataManager().get(account.uuid());
        Text.send(sender, "ADMIN.WHOIS-HEADER", "&6&lWho is &f{player}&6&l?", "{player}", account.displayName());
        Text.raw(sender, "&7UUID: &f" + account.uuid());
        if (online != null) {
            String rank = RolePrefixResolver.resolve(online);
            Location at = online.getLocation();
            Text.raw(sender, "&7Status: &aonline &8| &7Ping: &f" + online.getPing() + "ms &8| &7Mode: &f"
                    + online.getGameMode().name().toLowerCase(Locale.ROOT) + (rank == null || rank.isBlank() ? "" : " &8| &7Rank: " + rank.trim()));
            Text.raw(sender, "&7Location: &f" + at.getWorld().getName() + " " + at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ()
                    + " &8| &7Health: &f" + (int) Math.ceil(online.getHealth()) + " &8| &7Food: &f" + online.getFoodLevel());
            if (PermissionUtils.has(sender, "smpplugin.staff.alts") && online.getAddress() != null) {
                Text.raw(sender, "&7IP: &f" + online.getAddress().getAddress().getHostAddress());
            }
        } else {
            long last = offline.getLastPlayed();
            Text.raw(sender, "&7Status: &coffline &7(last seen &f" + (last > 0 ? Text.duration((System.currentTimeMillis() - last) / 1000L) + " ago" : "?") + "&7)");
        }
        long first = offline.getFirstPlayed();
        Text.raw(sender, "&7First joined: &f" + (first > 0 ? Text.duration((System.currentTimeMillis() - first) / 1000L) + " ago" : "?"));
        if (data != null) {
            Text.raw(sender, "&7Money: &a$" + NumberUtils.formatNice(data.getMoney()) + " &8| &7Shards: &d" + NumberUtils.format(data.getShards())
                    + " &8| &7Playtime: &f" + Text.duration(data.getTotalPlaytimeSeconds())
                    + " &8| &7K/D: &f" + data.getKills() + "/" + data.getDeaths());
        }
        Team team = plugin.getTeamManager() == null ? null : plugin.getTeamManager().getTeam(account.uuid());
        if (team != null) {
            Text.raw(sender, "&7Team: &b" + team.getName());
        }
        if (plugin.getPunishmentManager() != null) {
            boolean banned = plugin.getPunishmentManager().getActiveRecord(account.uuid(), PunishmentType.BAN).isPresent();
            boolean muted = plugin.getPunishmentManager().getActiveRecord(account.uuid(), PunishmentType.MUTE).isPresent();
            Text.raw(sender, "&7Banned: " + (banned ? "&cyes" : "&ano") + " &8| &7Muted: " + (muted ? "&cyes" : "&ano"));
        }
    }

    private void spook(CommandSender sender, String[] args) {
        if (args.length == 0) {
            Text.send(sender, "ADMIN.SPOOK-USAGE", "&7/spook <player|all>");
            return;
        }
        List<Player> targets = new ArrayList<>();
        if (args[0].equalsIgnoreCase("all")) {
            targets.addAll(Bukkit.getOnlinePlayers());
        } else {
            Player target = findOnline(sender, args[0]);
            if (target == null) {
                return;
            }
            targets.add(target);
        }
        for (Player target : targets) {
            plugin.getSpigotScheduler().runEntity(target, () -> {
                Location at = target.getLocation();
                target.spawnParticle(Particle.ELDER_GUARDIAN, at, 1);
                target.playSound(at, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1F, 1F);
                target.playSound(at, Sound.ENTITY_GHAST_SCREAM, 0.6F, 0.6F);
                target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, false));
                Text.title(target, "&4&lBOO!", "&8Happy Halloween...", 0, 30, 20);
            });
        }
        Text.send(sender, "ADMIN.SPOOK", "&5Spooked &f{count} &5player(s). &8Muahaha.", "{count}", Integer.toString(targets.size()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!PermissionUtils.has(sender, permission(name)) || args.length == 0) {
            return List.of();
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        switch (name) {
            case "day", "night", "sun", "rain", "thunder" -> Bukkit.getWorlds().forEach(world -> options.add(world.getName()));
            case "spawnmob" -> {
                if (args.length == 1) {
                    for (EntityType type : EntityType.values()) {
                        if (type.isAlive() && type.isSpawnable() && type != EntityType.PLAYER) {
                            options.add(type.name().toLowerCase(Locale.ROOT));
                        }
                    }
                } else if (args.length == 2) {
                    options.addAll(List.of("1", "5", "10"));
                } else {
                    Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
                }
            }
            case "butcher" -> options.addAll(List.of("32", "64", "128", "all"));
            case "speed" -> {
                if (args.length == 1) {
                    options.addAll(List.of("1", "2", "5", "10"));
                } else {
                    options.addAll(List.of("walk", "fly"));
                    Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
                }
            }
            case "spook" -> {
                options.add("all");
                Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
            }
            case "smite", "whois" -> {
                if (args.length == 1) {
                    Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
                } else if (name.equals("smite")) {
                    options.add("-damage");
                }
            }
            case "broadcast" -> {
                if (args.length == 1) {
                    options.add("-title");
                }
            }
            default -> {
            }
        }
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(typed)).limit(80).toList();
    }
}
