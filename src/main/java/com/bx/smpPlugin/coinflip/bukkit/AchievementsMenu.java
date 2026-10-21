package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.coinflip.Achievement;
import com.bx.smpPlugin.coinflip.CoinFlipStats;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Every achievement with progress, and the date it was unlocked. */
public final class AchievementsMenu extends CoinFlipMenu {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private record Loaded(CoinFlipStats stats, Map<Achievement, Long> unlocked) {
    }

    private Loaded loaded;
    private boolean failed;

    public AchievementsMenu(CoinFlipModule module, UUID viewer) {
        super(module, viewer, "Achievements", 54);
    }

    @Override
    public void open(Player player) {
        super.open(player);
        String name = player.getName();
        module.repository().transaction(dao -> new Loaded(dao.loadStats(viewer, name), dao.achievements(viewer)))
                .whenComplete((result, error) -> module.plugin().getSpigotScheduler().runGlobal(() -> {
                    loaded = result;
                    failed = error != null;
                    refresh();
                }));
    }

    @Override
    protected void render(Player player) {
        boolean readable = readable();
        back(() -> openMenu(new MainMenu(module, viewer)));
        if (failed) {
            set(22, Gui.item(Material.BARRIER, Gui.BAD + "Achievements are unavailable"));
            return;
        }
        if (loaded == null) {
            set(22, Gui.item(Material.CLOCK, Gui.INFO + "Loading..."));
            return;
        }
        set(4, Gui.item(Material.NETHER_STAR, Gui.GOLD + "Unlocked: " + Gui.VALUE + loaded.unlocked().size() + "/" + Achievement.values().length,
                Gui.MUTED + "Cosmetic milestones. None of them", Gui.MUTED + "reward losing or larger stakes."));
        Achievement[] all = Achievement.values();
        for (int i = 0; i < all.length && i < Gui.INNER_54.length; i++) {
            Achievement achievement = all[i];
            Long unlockedAt = loaded.unlocked().get(achievement);
            List<String> lore = new ArrayList<>();
            lore.add(Gui.MUTED + achievement.description());
            lore.add(Gui.line(readable, "Progress", achievement.progress(loaded.stats()) + " / " + achievement.target()));
            if (unlockedAt != null) {
                lore.add(Gui.GOOD + "Unlocked " + DATE.format(Instant.ofEpochMilli(unlockedAt)));
            }
            Material icon = unlockedAt != null ? Gui.material(achievement.icon(), Material.EMERALD) : Material.GRAY_DYE;
            set(Gui.INNER_54[i], Gui.glow(Gui.item(icon, (unlockedAt != null ? Gui.GOLD : Gui.MUTED) + achievement.displayName(), lore),
                    unlockedAt != null));
        }
    }
}
