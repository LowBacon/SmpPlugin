package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.coinflip.AnimationPreset;
import com.bx.smpPlugin.coinflip.CoinFlipConfig;
import com.bx.smpPlugin.coinflip.CoinFlipMatch;
import com.bx.smpPlugin.coinflip.CoinFlipPreferences;
import com.bx.smpPlugin.coinflip.CoinFlipService;
import com.bx.smpPlugin.coinflip.CoinSide;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives every reveal from one repeating task, so the number of running tasks never grows with the number of
 * players. Each reveal is a small state machine (frame list, position, countdown). A reveal stops by itself when
 * its viewer closes the menu or leaves; that has no effect on the stored result or on the payout.
 */
public final class CoinFlipAnimator {

    private final SmpPlugin plugin;
    private final CoinFlipModule module;
    private final Map<UUID, Reveal> reveals = new HashMap<>();
    private BukkitTask task;

    private static final class Reveal {
        final Player viewer;
        final FlipMenu menu;
        final List<Integer> delays;
        final boolean reducedMotion;
        final boolean practice;
        final CoinSide practicePick;
        int frame;
        int wait;

        Reveal(Player viewer, FlipMenu menu, List<Integer> delays, boolean reducedMotion, boolean practice, CoinSide practicePick) {
            this.viewer = viewer;
            this.menu = menu;
            this.delays = delays;
            this.reducedMotion = reducedMotion;
            this.practice = practice;
            this.practicePick = practicePick;
            this.wait = delays.isEmpty() ? 0 : delays.getFirst();
        }
    }

    public CoinFlipAnimator(SmpPlugin plugin, CoinFlipModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    public void start() {
        task = plugin.getSpigotScheduler().runGlobalTimer(this::tick, 1L, 1L);
    }

    /** Opens the reveal for a match participant. Returns the animation length in ticks (0 = shown at once). */
    public long startMatch(Player viewer, CoinFlipMatch match) {
        boolean idle = viewer.getOpenInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING
                || viewer.getOpenInventory().getTopInventory().getHolder() instanceof CoinFlipMenu;
        if (!idle || module.service().preferences(viewer.getUniqueId()).animation() == AnimationPreset.OFF) {
            // Never take over a chest or other screen the player is using; the result arrives in chat.
            return 0L;
        }
        FlipMenu menu = new FlipMenu(module, viewer.getUniqueId(), match);
        return begin(viewer, menu, false, null);
    }

    /** Opens a practice reveal; the result message is sent when it ends. */
    public void startPractice(Player viewer, CoinSide pick, CoinSide outcome) {
        FlipMenu menu = new FlipMenu(module, viewer.getUniqueId(), pick, outcome);
        if (begin(viewer, menu, true, pick) == 0L) {
            practiceResult(viewer, pick, outcome);
        }
    }

    private long begin(Player viewer, FlipMenu menu, boolean practice, CoinSide pick) {
        stop(viewer.getUniqueId());
        CoinFlipConfig config = module.config();
        CoinFlipPreferences prefs = module.service().preferences(viewer.getUniqueId());
        AnimationPreset preset = reveals.size() >= config.maxConcurrentAnimations() ? AnimationPreset.OFF : prefs.animation();
        menu.open(viewer);
        if (preset == AnimationPreset.OFF) {
            menu.reveal();
            finished(viewer, menu, practice);
            return 0L;
        }
        reveals.put(viewer.getUniqueId(), new Reveal(viewer, menu, preset.frameDelays(), preset.reducedMotion(), practice, pick));
        return preset.totalTicks();
    }

    /** Settlement finished: show the result at once to anyone still watching that match. */
    public void finish(String matchId) {
        Iterator<Reveal> iterator = reveals.values().iterator();
        while (iterator.hasNext()) {
            Reveal reveal = iterator.next();
            if (matchId.equals(reveal.menu.matchId())) {
                iterator.remove();
                if (open(reveal) && !reveal.menu.revealed()) {
                    reveal.menu.reveal();
                }
            }
        }
    }

    public void stop(UUID viewer) {
        reveals.remove(viewer);
    }

    public void stopAll() {
        if (task != null) {
            task.cancel();
        }
        reveals.clear();
    }

    public int running() {
        return reveals.size();
    }

    private void tick() {
        if (reveals.isEmpty()) {
            return;
        }
        Iterator<Reveal> iterator = reveals.values().iterator();
        while (iterator.hasNext()) {
            Reveal reveal = iterator.next();
            if (!reveal.viewer.isOnline() || !open(reveal)) {
                iterator.remove();
                if (reveal.practice && reveal.viewer.isOnline()) {
                    practiceResult(reveal.viewer, reveal.practicePick, outcomeOf(reveal));
                }
                continue;
            }
            if (--reveal.wait > 0) {
                continue;
            }
            reveal.frame++;
            if (reveal.frame >= reveal.delays.size()) {
                iterator.remove();
                reveal.menu.reveal();
                finished(reveal.viewer, reveal.menu, reveal.practice);
                if (reveal.practice) {
                    practiceResult(reveal.viewer, reveal.practicePick, outcomeOf(reveal));
                }
                continue;
            }
            reveal.wait = reveal.delays.get(reveal.frame);
            reveal.menu.frame(reveal.reducedMotion);
            playTick(reveal);
        }
    }

    private void playTick(Reveal reveal) {
        CoinFlipConfig config = module.config();
        if (!config.soundsAllowed() || !module.service().preferences(reveal.viewer.getUniqueId()).sounds()) {
            return;
        }
        String sound = config.sound("TICK");
        String[] parts = sound.split("\\|");
        if (parts.length == 3) {
            // Pitch rises as the coin slows, so the reveal is audible without watching.
            double progress = (double) reveal.frame / Math.max(1, reveal.delays.size());
            sound = parts[0] + "|" + parts[1] + "|" + String.format(java.util.Locale.ROOT, "%.2f", 1.0D + progress * 0.8D);
        }
        SoundUtils.play(reveal.viewer, sound);
    }

    private void finished(Player viewer, FlipMenu menu, boolean practice) {
        CoinFlipConfig config = module.config();
        CoinFlipPreferences prefs = module.service().preferences(viewer.getUniqueId());
        if (config.soundsAllowed() && prefs.sounds()) {
            SoundUtils.play(viewer, config.sound("REVEAL"));
        }
        if (config.particlesAllowed() && prefs.particles() && !prefs.animation().reducedMotion()) {
            try {
                viewer.spawnParticle(Particle.HAPPY_VILLAGER, viewer.getLocation().add(0D, 1.2D, 0D), 8, 0.4D, 0.4D, 0.4D, 0D);
            } catch (RuntimeException | LinkageError ignored) {
                // particles are decoration only
            }
        }
    }

    private void practiceResult(Player viewer, CoinSide pick, CoinSide outcome) {
        boolean right = pick == outcome;
        String verdict = module.messages().raw(right ? "practice-right" : "practice-wrong", Map.of());
        viewer.sendMessage(ColorUtils.colorize(module.messages().chat("practice-result", CoinFlipService.vars(
                "outcome", outcome.displayName(), "side", pick.displayName(), "verdict", verdict))));
    }

    private static CoinSide outcomeOf(Reveal reveal) {
        return reveal.menu.practiceOutcome();
    }

    private boolean open(Reveal reveal) {
        return reveal.viewer.getOpenInventory().getTopInventory().getHolder() == reveal.menu;
    }
}
