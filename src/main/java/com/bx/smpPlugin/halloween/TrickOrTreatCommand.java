package com.bx.smpPlugin.halloween;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.core.Reward;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /trickortreat: knock on a door once an hour during the Halloween season. Most knocks give a
 * treat picked by weight from halloween.yml; the rest play a harmless trick (a scare, bats, a
 * launch into the air...). Nothing a trick does can hurt the player or take their items.
 */
public final class TrickOrTreatCommand implements CommandExecutor {

    public static final String PERMISSION = "smpplugin.halloween.trickortreat";
    private static final String SECTION = "halloween.trickortreat";

    private final SmpPlugin plugin;
    private final HalloweenManager halloween;
    private final PlayerStore store;

    public TrickOrTreatCommand(SmpPlugin plugin, HalloweenManager halloween, PlayerStore store) {
        this.plugin = plugin;
        this.halloween = halloween;
        this.store = store;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "GENERAL.PLAYERS-ONLY", "&cOnly players can do that.");
            return true;
        }
        if (!PermissionUtils.has(player, PERMISSION)) {
            Text.send(player, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        if (!halloween.config().getBoolean("TRICK-OR-TREAT.ENABLED", true) || !halloween.isEnabled()) {
            Text.send(player, "TRICK-OR-TREAT.DISABLED", "&7Nobody is answering the door right now.");
            return true;
        }
        if (!halloween.inSeason()) {
            long wait = halloween.rules().secondsUntilSeason(halloween.now());
            Text.send(player, "TRICK-OR-TREAT.OUT-OF-SEASON",
                    "&7Trick or Treat opens with the Halloween season{when}.",
                    "{when}", wait > 0 ? " in &f" + Text.duration(wait) + "&7" : "");
            return true;
        }
        UUID uuid = player.getUniqueId();
        long cooldownMillis = Math.max(0L, halloween.config().getLong("TRICK-OR-TREAT.COOLDOWN-MINUTES", 60L)) * 60_000L;
        long last = parseLong(store.get(uuid, SECTION));
        long now = System.currentTimeMillis();
        if (now - last < cooldownMillis) {
            Text.send(player, "TRICK-OR-TREAT.COOLDOWN", "&7You knocked recently. Try again in &f{time}&7.",
                    "{time}", Text.duration((cooldownMillis - (now - last)) / 1000L));
            return true;
        }
        store.set(uuid, SECTION, Long.toString(now));

        Text.actionBar(player, Text.get("TRICK-OR-TREAT.KNOCK", "&6&lKnock, knock..."));
        player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.6F, 1.3F);
        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_OPEN, 0.8F, 0.7F);
            int treatChance = Math.max(0, Math.min(100, halloween.config().getInt("TRICK-OR-TREAT.TREAT-CHANCE", 70)));
            if (ThreadLocalRandom.current().nextInt(100) < treatChance) {
                treat(player);
            } else {
                trick(player);
            }
        }, 30L);
        return true;
    }

    private void treat(Player player) {
        ConfigurationSection treats = halloween.config().getConfigurationSection("TRICK-OR-TREAT.TREATS");
        ConfigurationSection picked = pickWeighted(treats);
        Reward reward = picked == null ? new Reward(100D, 0L, List.of(), List.of(), List.of()) : Reward.read(picked);
        reward.give(plugin, player);
        String prize = String.join("&7, ", reward.describe(plugin));
        Text.title(player, Text.get("TRICK-OR-TREAT.TREAT-TITLE", "&6&lTREAT!"), prize, 5, 40, 10);
        Text.send(player, "TRICK-OR-TREAT.TREAT", "&6Treat! &7You got {prize}&7.", "{prize}", prize);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.5F);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 20, 0.5, 0.6, 0.5, 0.0);
        if (picked != null && picked.getBoolean("ANNOUNCE", false)) {
            Text.broadcast(Text.prefix() + Text.get("TRICK-OR-TREAT.JACKPOT-BROADCAST",
                    "&6{player} &7got a rare treat: {prize}&7! &8(/trickortreat)", "{player}", player.getName(), "{prize}", prize));
        }
    }

    private ConfigurationSection pickWeighted(ConfigurationSection treats) {
        if (treats == null) {
            return null;
        }
        List<ConfigurationSection> options = new ArrayList<>();
        int total = 0;
        for (String key : treats.getKeys(false)) {
            ConfigurationSection option = treats.getConfigurationSection(key);
            if (option != null && option.getInt("WEIGHT", 1) > 0) {
                options.add(option);
                total += option.getInt("WEIGHT", 1);
            }
        }
        if (options.isEmpty()) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (ConfigurationSection option : options) {
            roll -= option.getInt("WEIGHT", 1);
            if (roll < 0) {
                return option;
            }
        }
        return options.get(options.size() - 1);
    }

    private void trick(Player player) {
        List<String> tricks = new ArrayList<>();
        for (String raw : halloween.config().getStringList("TRICK-OR-TREAT.TRICKS")) {
            tricks.add(raw.trim().toUpperCase(Locale.ROOT));
        }
        if (tricks.isEmpty()) {
            tricks.add("SCARE");
        }
        String trick = tricks.get(ThreadLocalRandom.current().nextInt(tricks.size()));
        Text.title(player, Text.get("TRICK-OR-TREAT.TRICK-TITLE", "&5&lTRICK!"), "", 5, 30, 10);
        Location at = player.getLocation();
        switch (trick) {
            case "BATS" -> {
                Text.send(player, "TRICK-OR-TREAT.TRICK-BATS", "&5Trick! &7A swarm of bats flies out of the door!");
                List<Bat> bats = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    bats.add(player.getWorld().spawn(at.clone().add(0, 1.5, 0), Bat.class, bat -> {
                        bat.setPersistent(false);
                        bat.setRemoveWhenFarAway(true);
                    }));
                }
                plugin.getSpigotScheduler().runEntityLater(player, () -> bats.forEach(bat -> {
                    if (bat.isValid()) {
                        bat.getWorld().spawnParticle(Particle.SMOKE, bat.getLocation(), 6, 0.1, 0.1, 0.1, 0.01);
                        bat.remove();
                    }
                }), 100L);
            }
            case "LAUNCH" -> {
                Text.send(player, "TRICK-OR-TREAT.TRICK-LAUNCH", "&5Trick! &7Up you go!");
                player.setVelocity(new Vector(0, 1.3, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 160, 0, false, false, true));
                player.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1F, 0.8F);
            }
            case "SPIN" -> {
                Text.send(player, "TRICK-OR-TREAT.TRICK-SPIN", "&5Trick! &7The world spins...");
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0, false, false, true));
                Location turned = at.clone();
                turned.setYaw(at.getYaw() + 180F);
                plugin.getSpigotScheduler().teleport(player, turned);
            }
            case "DARKNESS" -> {
                Text.send(player, "TRICK-OR-TREAT.TRICK-DARKNESS", "&5Trick! &7The lights go out...");
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0, false, false, true));
                player.playSound(at, Sound.AMBIENT_CAVE, 1F, 0.8F);
            }
            case "FAKE_CREEPER" -> {
                Text.send(player, "TRICK-OR-TREAT.TRICK-CREEPER", "&5Trick! &7Did you hear that?");
                Location behind = at.clone().subtract(at.getDirection().setY(0).normalize().multiply(2));
                player.playSound(behind, Sound.ENTITY_CREEPER_PRIMED, 1F, 1F);
            }
            default -> {
                Text.send(player, "TRICK-OR-TREAT.TRICK-SCARE", "&5Trick! &7BOO!");
                player.spawnParticle(Particle.ELDER_GUARDIAN, at, 1);
                player.playSound(at, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1F, 1F);
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0, false, false, true));
            }
        }
    }

    private static long parseLong(String raw) {
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
