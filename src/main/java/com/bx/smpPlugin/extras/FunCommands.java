package com.bx.smpPlugin.extras;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Social and silly commands: emotes (/hug /slap /highfive /poke /wave /cheer), /dice, /8ball,
 * /rps, /rate, /firework, /sit and /hat. All of them are cosmetic: none can hurt a player, move
 * their items to someone else or be used to farm money.
 */
public final class FunCommands implements CommandExecutor, TabCompleter {

    public static final List<String> EMOTES = List.of("hug", "slap", "highfive", "poke", "wave", "cheer");
    private static final String[] EIGHT_BALL = {
            "It is certain.", "Without a doubt.", "You may rely on it.", "Yes, definitely.", "Most likely.",
            "Outlook good.", "Signs point to yes.", "Reply hazy, try again.", "Ask again later.",
            "Better not tell you now.", "Don't count on it.", "My reply is no.", "Very doubtful.",
            "The creepers say no.", "The villagers whisper yes."
    };

    private final SmpPlugin plugin;
    private final Map<String, Map<UUID, Long>> cooldowns = new ConcurrentHashMap<>();
    /** Seats from /sit, removed as soon as nobody sits on them. */
    private final Map<UUID, ArmorStand> seats = new ConcurrentHashMap<>();

    public FunCommands(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration config() {
        FileConfiguration extras = plugin.getConfigManager().getExtras();
        return extras == null ? new YamlConfiguration() : extras;
    }

    public static String permission(String command) {
        return switch (command) {
            case "firework" -> "smpplugin.fun.firework";
            case "sit" -> "smpplugin.fun.sit";
            case "hat" -> "smpplugin.fun.hat";
            default -> "smpplugin.fun." + (EMOTES.contains(command) ? "emotes" : "games");
        };
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!(sender instanceof Player player)) {
            Text.send(sender, "GENERAL.PLAYERS-ONLY", "&cOnly players can do that.");
            return true;
        }
        if (!PermissionUtils.has(player, permission(name))) {
            Text.send(player, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        switch (name) {
            case "dice" -> dice(player, args);
            case "8ball" -> eightBall(player, args);
            case "rps" -> rps(player, args);
            case "rate" -> rate(player, args);
            case "firework" -> firework(player);
            case "sit" -> sit(player);
            case "hat" -> hat(player);
            default -> emote(player, name, args);
        }
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private boolean onCooldown(Player player, String key, long seconds) {
        if (seconds <= 0 || PermissionUtils.has(player, "smpplugin.fun.bypasscooldown")) {
            return false;
        }
        Map<UUID, Long> map = cooldowns.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
        long now = System.currentTimeMillis();
        Long last = map.get(player.getUniqueId());
        if (last != null && now - last < seconds * 1000L) {
            Text.send(player, "FUN.COOLDOWN", "&7Slow down! Try again in &f{time}&7.",
                    "{time}", Text.duration((seconds * 1000L - (now - last)) / 1000L + 1));
            return true;
        }
        map.put(player.getUniqueId(), now);
        return false;
    }

    /** Players who see a social message: nearby players, or the whole server, per extras.yml. */
    private List<Player> audience(Player source) {
        List<Player> out = new ArrayList<>();
        boolean server = "SERVER".equalsIgnoreCase(config().getString("EMOTES.AUDIENCE", "NEARBY"));
        double radius = Math.max(4D, config().getDouble("EMOTES.RADIUS", 48D));
        double radiusSquared = radius * radius;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (server || (player.getWorld().equals(source.getWorld())
                    && player.getLocation().distanceSquared(source.getLocation()) <= radiusSquared)) {
                out.add(player);
            }
        }
        return out;
    }

    private void tell(Player source, String message) {
        for (Player viewer : audience(source)) {
            if (plugin.getIgnoreManager() == null || !plugin.getIgnoreManager().isIgnoring(viewer.getUniqueId(), source.getUniqueId())) {
                Text.raw(viewer, message);
            }
        }
    }

    private Player findTarget(Player sender, String name) {
        Player target = plugin.getHideManager() == null ? Bukkit.getPlayerExact(name)
                : plugin.getHideManager().findOnlinePlayer(sender, name);
        return target != null && sender.canSee(target) ? target : null;
    }

    private String shownName(Player viewer, Player target) {
        return plugin.getHideManager() == null ? target.getName() : plugin.getHideManager().visibleName(viewer, target);
    }

    // ------------------------------------------------------------------ emotes

    private void emote(Player player, String name, String[] args) {
        ConfigurationSection emote = config().getConfigurationSection("EMOTES.LIST." + name);
        boolean needsTarget = emote == null ? !name.equals("wave") && !name.equals("cheer") : emote.getBoolean("TARGET", false);
        Player target = null;
        if (needsTarget) {
            if (args.length < 1) {
                Text.send(player, "FUN.EMOTE-USAGE", "&7/{emote} <player>", "{emote}", name);
                return;
            }
            target = findTarget(player, args[0]);
            if (target == null) {
                Text.send(player, "GENERAL.PLAYER-NOT-FOUND", "&cThat player is not online.");
                return;
            }
            if (target.equals(player)) {
                Text.send(player, "FUN.EMOTE-SELF", "&7You can't {emote} yourself... or can you?", "{emote}", name);
                return;
            }
            if (plugin.getIgnoreManager() != null && plugin.getIgnoreManager().isIgnoring(target.getUniqueId(), player.getUniqueId())) {
                Text.send(player, "FUN.EMOTE-IGNORED", "&cThat player doesn't want emotes from you.");
                return;
            }
        }
        if (onCooldown(player, "emote", config().getLong("EMOTES.COOLDOWN-SECONDS", 10L))) {
            return;
        }
        String template = emote == null ? "&7{player} " + name + "s {target}" : emote.getString("MESSAGE", "&7{player} " + name + "s {target}");
        String message = Text.fill(template, "{player}", player.getName(),
                "{target}", target == null ? "" : target.getName());
        tell(player, message);

        Player focus = target == null ? player : target;
        Particle particle = Text.particle(emote == null ? "HEART" : emote.getString("PARTICLE", "HEART"));
        if (particle != null) {
            try {
                focus.getWorld().spawnParticle(particle, focus.getLocation().add(0, 2.1, 0), 8, 0.4, 0.3, 0.4, 0.02);
            } catch (IllegalArgumentException ignored) {
                // A particle that needs extra data; skip it rather than fail the emote.
            }
        }
        Sound sound = Text.sound(emote == null ? null : emote.getString("SOUND"));
        if (sound != null) {
            focus.getWorld().playSound(focus.getLocation(), sound, 0.8F, 1.0F);
        }
        double push = emote == null ? 0D : emote.getDouble("PUSH", 0D);
        if (target != null && push > 0D) {
            Vector away = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
            if (away.lengthSquared() > 0.0001) {
                target.setVelocity(target.getVelocity().add(away.normalize().multiply(push).setY(0.2)));
            }
        }
    }

    // ------------------------------------------------------------------ games

    private void dice(Player player, String[] args) {
        int sides = 6;
        if (args.length > 0) {
            try {
                sides = Math.max(2, Math.min(1000, Integer.parseInt(args[0].replace("d", ""))));
            } catch (NumberFormatException ignored) {
                sides = 6;
            }
        }
        if (onCooldown(player, "dice", 3L)) {
            return;
        }
        int result = ThreadLocalRandom.current().nextInt(1, sides + 1);
        int finalSides = sides;
        for (int frame = 0; frame < 8; frame++) {
            int shown = ThreadLocalRandom.current().nextInt(1, sides + 1);
            boolean last = frame == 7;
            plugin.getSpigotScheduler().runEntityLater(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (last) {
                    Text.title(player, "&f&l🎲 " + result, "&7d" + finalSides, 0, 30, 10);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5F, 1.6F);
                    tell(player, Text.get("FUN.DICE", "&7🎲 &f{player} &7rolled a &e&l{result} &8(d{sides})",
                            "{player}", player.getName(), "{result}", Integer.toString(result), "{sides}", Integer.toString(finalSides)));
                } else {
                    Text.title(player, "&7🎲 " + shown, "&8rolling...", 0, 5, 0);
                    player.playSound(player.getLocation(), Sound.BLOCK_STONE_HIT, 0.6F, 1.0F + 0.1F * (shown % 5));
                }
            }, 1L + frame * 3L);
        }
    }

    private void eightBall(Player player, String[] args) {
        if (args.length == 0) {
            Text.send(player, "FUN.8BALL-USAGE", "&7/8ball <question>");
            return;
        }
        if (onCooldown(player, "8ball", 5L)) {
            return;
        }
        String question = String.join(" ", args);
        String answer = EIGHT_BALL[ThreadLocalRandom.current().nextInt(EIGHT_BALL.length)];
        Text.actionBar(player, Text.get("FUN.8BALL-SHAKE", "&8🎱 &7shaking..."));
        player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.6F, 1.4F);
        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            if (player.isOnline()) {
                tell(player, Text.get("FUN.8BALL", "&8🎱 &f{player} &7asked: &f{question}\n&8🎱 &d{answer}",
                        "{player}", player.getName(), "{question}", question.replace('&', '＆'), "{answer}", answer));
            }
        }, 30L);
    }

    private void rps(Player player, String[] args) {
        List<String> moves = List.of("rock", "paper", "scissors");
        String move = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (!moves.contains(move)) {
            Text.send(player, "FUN.RPS-USAGE", "&7/rps <rock|paper|scissors>");
            return;
        }
        if (onCooldown(player, "rps", 3L)) {
            return;
        }
        String bot = moves.get(ThreadLocalRandom.current().nextInt(3));
        String[] countdown = {"&fRock...", "&fPaper...", "&fScissors...", "&e&lSHOOT!"};
        for (int i = 0; i < countdown.length; i++) {
            String frame = countdown[i];
            plugin.getSpigotScheduler().runEntityLater(player, () -> {
                if (player.isOnline()) {
                    Text.title(player, frame, "", 0, 12, 0);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_SNARE, 0.6F, 1.0F);
                }
            }, 1L + i * 10L);
        }
        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            int outcome = (moves.indexOf(move) - moves.indexOf(bot) + 3) % 3; // 0 draw, 1 win, 2 lose
            String result = switch (outcome) {
                case 1 -> Text.get("FUN.RPS-WIN", "&a&lYou win!");
                case 2 -> Text.get("FUN.RPS-LOSE", "&c&lYou lose!");
                default -> Text.get("FUN.RPS-DRAW", "&e&lDraw!");
            };
            Text.title(player, result, "&7" + icon(move) + " &8vs &7" + icon(bot), 0, 40, 10);
            player.playSound(player.getLocation(), outcome == 1 ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO, 0.6F, 1.2F);
            Text.send(player, "FUN.RPS", "&7You threw &f{you}&7, the server threw &f{bot}&7. {result}",
                    "{you}", move, "{bot}", bot, "{result}", result);
        }, 42L);
    }

    private static String icon(String move) {
        return switch (move) {
            case "rock" -> "🪨 rock";
            case "paper" -> "📄 paper";
            default -> "✂ scissors";
        };
    }

    private void rate(Player player, String[] args) {
        if (args.length == 0) {
            Text.send(player, "FUN.RATE-USAGE", "&7/rate <anything>");
            return;
        }
        if (onCooldown(player, "rate", 5L)) {
            return;
        }
        String thing = String.join(" ", args).replace('&', '＆');
        int score = Math.floorMod(thing.toLowerCase(Locale.ROOT).trim().hashCode(), 11);
        tell(player, Text.get("FUN.RATE", "&7{player} asked me to rate &f{thing}&7: &e{score}/10 {stars}",
                "{player}", player.getName(), "{thing}", thing, "{score}", Integer.toString(score),
                "{stars}", "&6" + "★".repeat(score / 2) + "&8" + "★".repeat(5 - score / 2)));
    }

    private void firework(Player player) {
        if (onCooldown(player, "firework", config().getLong("UTILITIES.FIREWORK-COOLDOWN-SECONDS", 30L))) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Firework firework = player.getWorld().spawn(player.getLocation().add(0, 1, 0), Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        FireworkEffect.Type[] types = FireworkEffect.Type.values();
        meta.addEffect(FireworkEffect.builder()
                .with(types[random.nextInt(types.length)])
                .withColor(Color.fromRGB(random.nextInt(0xFFFFFF)), Color.fromRGB(random.nextInt(0xFFFFFF)))
                .withFade(Color.fromRGB(random.nextInt(0xFFFFFF)))
                .flicker(random.nextBoolean())
                .trail(true)
                .build());
        meta.setPower(1);
        firework.setFireworkMeta(meta);
        // Mark it so it can never hurt anyone (checked by ExtrasListener).
        firework.addScoreboardTag(ExtrasListener.HARMLESS_FIREWORK_TAG);
    }

    // ------------------------------------------------------------------ sit and hat

    private void sit(Player player) {
        ArmorStand existing = seats.remove(player.getUniqueId());
        if (existing != null) {
            existing.eject();
            existing.remove();
            return;
        }
        if (player.isInsideVehicle() || !player.isOnGround() || player.isFlying() || player.isSwimming()) {
            Text.send(player, "FUN.SIT-NOT-NOW", "&7You need to stand on solid ground to sit.");
            return;
        }
        if (plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            Text.send(player, "GENERAL.IN-COMBAT", "&cYou can't do that in combat.");
            return;
        }
        Location at = player.getLocation().clone();
        at.setY(Math.floor(at.getY() + 0.01) - 0.05);
        ArmorStand seat = player.getWorld().spawn(at, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setPersistent(false);
            stand.addScoreboardTag(ExtrasListener.SEAT_TAG);
        });
        seat.addPassenger(player);
        seats.put(player.getUniqueId(), seat);
        Text.actionBar(player, Text.get("FUN.SITTING", "&7You sit down. &8(sneak to stand up)"));
    }

    /** Removes seats nobody sits on any more. Runs every second. */
    public void cleanSeats() {
        seats.entrySet().removeIf(entry -> {
            ArmorStand seat = entry.getValue();
            if (seat.isValid() && !seat.getPassengers().isEmpty()) {
                return false;
            }
            plugin.getSpigotScheduler().runEntity(seat, seat::remove);
            return true;
        });
    }

    public void removeSeats() {
        for (ArmorStand seat : seats.values()) {
            for (Entity passenger : seat.getPassengers()) {
                seat.removePassenger(passenger);
            }
            seat.remove();
        }
        seats.clear();
    }

    private void hat(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            Text.send(player, "FUN.HAT-EMPTY", "&7Hold the item you want to wear.");
            return;
        }
        ItemStack helmet = player.getInventory().getHelmet();
        ItemStack wear = hand.clone();
        wear.setAmount(1);
        hand.setAmount(hand.getAmount() - 1);
        player.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
        player.getInventory().setHelmet(wear);
        if (helmet != null && !helmet.getType().isAir()) {
            for (ItemStack rest : player.getInventory().addItem(helmet).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8F, 1.2F);
        Text.send(player, "FUN.HAT", "&7Looking sharp! You are now wearing &f{item}&7.",
                "{item}", com.bx.smpPlugin.core.Reward.itemName(wear));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length != 1) {
            return List.of();
        }
        String typed = args[0].toLowerCase(Locale.ROOT);
        if (name.equals("rps")) {
            return List.of("rock", "paper", "scissors").stream().filter(o -> o.startsWith(typed)).toList();
        }
        if (name.equals("dice")) {
            return List.of("6", "12", "20", "100").stream().filter(o -> o.startsWith(typed)).toList();
        }
        if (EMOTES.contains(name) && sender instanceof Player player) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player) && player.canSee(online)) {
                    String shown = shownName(player, online);
                    if (shown.toLowerCase(Locale.ROOT).startsWith(typed)) {
                        names.add(shown);
                    }
                }
            }
            return names;
        }
        return List.of();
    }
}
