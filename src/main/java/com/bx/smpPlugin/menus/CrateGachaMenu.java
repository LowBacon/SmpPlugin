package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.CrateManager;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class CrateGachaMenu extends BaseMenu {

    private final CrateManager.CrateDefinition crate;
    private final List<CrateManager.CrateReward> rollingRewards = new ArrayList<>();
    private final CrateManager.CrateReward winningReward;
    private final List<Integer> previewSlots;
    private final int pointerSlot;
    private final CrateManager.SpinDirection spinDirection;

    private BukkitTask spinTask;
    private boolean finished;
    private boolean allowClose;
    private int particleTickCounter;

    public CrateGachaMenu(SmpPlugin plugin, CrateManager.CrateDefinition crate) {
        super(plugin, crate.gachaSettings().title(), crate.menuSettings().size());
        this.crate = crate;
        this.winningReward = plugin.getCrateManager().rollReward(crate);
        this.previewSlots = sanitizePreviewSlots(crate);
        this.pointerSlot = previewSlots.contains(crate.gachaSettings().pointerSlot())
                ? crate.gachaSettings().pointerSlot()
                : previewSlots.get(previewSlots.size() / 2);
        this.spinDirection = resolveSpinDirection(crate);
        this.particleTickCounter = 0;
    }

    @Override
    public void build(Player player) {
        clear();
        fill(crate.gachaSettings().filler());
        initializeRoll();
        render(player);
    }

    @Override
    public void open(Player player) {
        finished = false;
        allowClose = false;
        particleTickCounter = 0;
        build(player);
        player.openInventory(inventory);
        startSpin(player);
        playOpeningEffects(player);
    }

    private void playOpeningEffects(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        player.spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.2, 0), 30, 0.5, 0.5, 0.5, 0.15);
    }

    @Override
    public void handleClick(int slot, Player player, org.bukkit.event.inventory.ClickType clickType) {
        if (!finished) {
            SoundUtils.play(player, plugin.getConfigManager().getSound("MENUS.BUTTON-CLICK"));
            player.sendMessage(ColorUtils.toComponent("&7ᴘʟᴇᴀѕᴇ ᴡᴀɪᴛ ꜰᴏʀ ᴛʜᴇ ʀᴏʟʟ ᴛᴏ ꜰɪɴɪѕʜ..."));
            return;
        }
    }

    @Override
    public void onClose(Player player) {
        if (allowClose) {
            if (spinTask != null) {
                spinTask.cancel();
                spinTask = null;
            }
            return;
        }

        if (spinTask != null) {
            spinTask.cancel();
            spinTask = null;
        }

        plugin.getSpigotScheduler().runEntity(player, () -> {
            if (!plugin.isEnabled() || allowClose || finished || !player.isOnline()) {
                return;
            }
            player.openInventory(inventory);
            player.updateInventory();
        });
    }

    private void initializeRoll() {
        rollingRewards.clear();
        for (int ignored = 0; ignored < previewSlots.size(); ignored++) {
            CrateManager.CrateReward reward = plugin.getCrateManager().rollReward(crate);
            rollingRewards.add(reward == null ? winningReward : reward);
        }
    }

    private void startSpin(Player player) {
        if (winningReward == null) {
            finished = true;
            allowClose = true;
            player.closeInventory();
            player.sendMessage(ColorUtils.toComponent("&cᴛʜɪѕ ᴄʀᴀᴛᴇ ʜᴀѕ ɴᴏ ᴠᴀʟɪᴅ ʀᴇᴡᴀʀᴅѕ ᴄᴏɴꜰɪɢᴜʀᴇᴅ."));
            plugin.getCrateManager().clearSession(player.getUniqueId());
            return;
        }

        final int[] steps = {0};
        spinTask = plugin.getSpigotScheduler().runEntityTimer(player, () -> {
            if (!player.isOnline()) {
                cancelSpin();
                plugin.getCrateManager().clearSession(player.getUniqueId());
                return;
            }

            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof CrateGachaMenu menu) || menu != this) {
                if (allowClose || finished) {
                    cancelSpin();
                }
                return;
            }

            steps[0]++;
            advanceRoll();
            emitSpinParticles(player);
            render(player);

            if (steps[0] >= crate.gachaSettings().totalSteps()) {
                lockWinningReward();
                render(player);
                SoundUtils.play(player, plugin.getConfigManager().getSound("CRATES.SPIN-END"));
                playWinEffects(player);
                cancelSpin();
                plugin.getSpigotScheduler().runEntityLater(player, () -> finish(player), 14L);
                return;
            }

            SoundUtils.play(player, plugin.getConfigManager().getSound("CRATES.SPIN-TICK"));
        }, 0L, crate.gachaSettings().tickInterval());
    }

    private void emitSpinParticles(Player player) {
        particleTickCounter++;
        if (particleTickCounter % 3 != 0) {
            return;
        }
        int pointerIndex = previewSlots.indexOf(pointerSlot);
        if (pointerIndex < 0 || pointerIndex >= rollingRewards.size()) {
            return;
        }
        CrateManager.CrateReward reward = rollingRewards.get(pointerIndex);
        if (reward == null) {
            return;
        }
        Particle particleType = Particle.ENCHANT;
        player.spawnParticle(particleType, player.getLocation().add(0, 1.3, 0), 6, 0.4, 0.15, 0.4, 0.02);
    }

    private void playWinEffects(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.8f);
        player.spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.0, 0), 40, 0.6, 0.6, 0.6, 0.2);
        player.spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1.5, 0), 25, 0.5, 0.5, 0.5, 0.08);
    }

    private void finish(Player player) {
        if (!player.isOnline()) {
            return;
        }

        finished = true;
        allowClose = true;
        CrateManager.ClaimResult result = plugin.getCrateManager().claimReward(player, winningReward);
        player.sendMessage(ColorUtils.toComponent(result.message()));

        if (!result.success() && result.reason() == CrateManager.FailureReason.NO_KEYS) {
            plugin.getCrateVisualManager().playNoKeyEffects(player);
        }

        if (result.success()) {
            plugin.getCrateVisualManager().playClaimEffects(player, crate);
        } else {
            plugin.getCrateManager().clearSession(player.getUniqueId());
        }

        player.closeInventory();
    }

    private void advanceRoll() {
        if (rollingRewards.isEmpty()) {
            return;
        }

        CrateManager.CrateReward nextReward = plugin.getCrateManager().rollReward(crate);
        if (nextReward == null) {
            nextReward = winningReward;
        }

        if (spinDirection == CrateManager.SpinDirection.RIGHT) {
            rollingRewards.add(0, nextReward);
            rollingRewards.remove(rollingRewards.size() - 1);
            return;
        }

        rollingRewards.add(nextReward);
        rollingRewards.remove(0);
    }

    private void lockWinningReward() {
        int pointerIndex = previewSlots.indexOf(pointerSlot);
        if (pointerIndex < 0) {
            return;
        }

        while (rollingRewards.size() < previewSlots.size()) {
            rollingRewards.add(plugin.getCrateManager().rollReward(crate));
        }
        rollingRewards.set(pointerIndex, winningReward);
    }

    private void render(Player player) {
        fill(crate.gachaSettings().filler());
        for (int i = 0; i < previewSlots.size() && i < rollingRewards.size(); i++) {
            CrateManager.CrateReward reward = rollingRewards.get(i);
            if (reward == null) {
                continue;
            }
            set(previewSlots.get(i), plugin.getCrateManager().createRewardDisplayItem(player, crate, reward));
        }

        renderPointerIndicators();
    }

    private void cancelSpin() {
        if (spinTask != null) {
            spinTask.cancel();
            spinTask = null;
        }
    }

    private List<Integer> sanitizePreviewSlots(CrateManager.CrateDefinition crate) {
        List<Integer> slots = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        int maxSize = crate.menuSettings().size();

        for (Integer slot : crate.gachaSettings().previewSlots()) {
            if (slot == null || slot < 0 || slot >= maxSize || !seen.add(slot)) {
                continue;
            }
            slots.add(slot);
        }

        if (slots.isEmpty()) {
            slots.addAll(List.of(10, 11, 12, 13, 14, 15, 16));
        }
        return slots;
    }

    private void renderPointerIndicators() {
        int topSlot = findIndicatorSlot(-9);
        if (topSlot >= 0) {
            set(topSlot, createIndicatorItem("&c▼"));
        }

        int bottomSlot = findIndicatorSlot(9);
        if (bottomSlot >= 0) {
            set(bottomSlot, createIndicatorItem("&c▲"));
        }
    }

    private int findIndicatorSlot(int offset) {
        int slot = pointerSlot + offset;
        if (slot < 0 || slot >= inventory.getSize()) {
            return -1;
        }
        if (previewSlots.contains(slot)) {
            return -1;
        }
        return slot;
    }

    private ItemStack createIndicatorItem(String arrow) {
        return ItemUtils.createItem(
                Material.RED_STAINED_GLASS_PANE,
                arrow,
                List.of("&7ᴡɪɴɴɪɴɢ ѕʟᴏᴛ")
        );
    }

    private CrateManager.SpinDirection resolveSpinDirection(CrateManager.CrateDefinition crate) {
        CrateManager.SpinDirection configured = crate.gachaSettings().direction();
        if (configured == CrateManager.SpinDirection.RANDOM) {
            return ThreadLocalRandom.current().nextBoolean()
                    ? CrateManager.SpinDirection.LEFT
                    : CrateManager.SpinDirection.RIGHT;
        }
        return configured;
    }
}
