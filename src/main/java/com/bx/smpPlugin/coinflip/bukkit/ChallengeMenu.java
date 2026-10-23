// SmpPlugin by LowBacon
package com.bx.smpPlugin.coinflip.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChallengeMenu extends CoinFlipMenu {

    private final int page;
    private final CreateMenu.Draft draft;

    public ChallengeMenu(CoinFlipModule module, UUID viewer, int page) {
        this(module, viewer, page, null);
    }

    public ChallengeMenu(CoinFlipModule module, UUID viewer, int page, CreateMenu.Draft draft) {
        super(module, viewer, "Challenge a player", 54);
        this.page = page;
        this.draft = draft;
    }

    @Override
    protected void render(Player player) {
        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(viewer) && challengeable(online)) {
                candidates.add(online);
            }
        }
        candidates.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int perPage = Gui.INNER_54.length;
        int pages = Math.max(1, (candidates.size() + perPage - 1) / perPage);
        int current = Math.max(0, Math.min(page, pages - 1));
        set(4, Gui.item(Material.PAPER, Gui.GOLD + "Challenge a player",
                Gui.MUTED + "Only they can accept. Your stake is", Gui.MUTED + "reserved when you send it and",
                Gui.MUTED + "returned if they decline or ignore it."));
        if (candidates.isEmpty()) {
            set(22, Gui.item(Material.STRUCTURE_VOID, Gui.MUTED + "Nobody to challenge right now",
                    Gui.MUTED + "Players can turn invitations off."));
        }
        for (int i = 0; i < perPage; i++) {
            int index = current * perPage + i;
            if (index >= candidates.size()) {
                break;
            }
            Player target = candidates.get(index);
            UUID targetId = target.getUniqueId();
            String targetName = target.getName();
            button(Gui.INNER_54[i], Gui.head(targetId, Gui.VALUE + targetName, List.of(Gui.GOOD + "Click to set up the challenge")), () -> {
                Player still = Bukkit.getPlayer(targetId);
                if (still == null) {
                    message("player-offline", Map.of("player", targetName));
                    refresh();
                    return;
                }
                CreateMenu.Draft next = draft == null
                        ? CreateMenu.Draft.challenge(prefs(), config(), targetId, targetName)
                        : draft.copy();
                next.targetId = targetId;
                next.targetName = targetName;
                openMenu(new CreateMenu(module, viewer, next));
            });
        }
        back(() -> openMenu(draft == null ? new MainMenu(module, viewer) : new CreateMenu(module, viewer, draft)));
        if (current > 0) {
            button(48, Gui.item(Material.ARROW, Gui.INFO + "Previous page"), () -> openMenu(new ChallengeMenu(module, viewer, current - 1, draft)));
        }
        if (current < pages - 1) {
            button(50, Gui.item(Material.ARROW, Gui.INFO + "Next page"), () -> openMenu(new ChallengeMenu(module, viewer, current + 1, draft)));
        }
    }

    private boolean challengeable(Player target) {
        if (!module.service().preferences(target.getUniqueId()).inviteNotifications()) {
            return false;
        }
        return module.plugin().getIgnoreManager() == null
                || !module.plugin().getIgnoreManager().isIgnoring(target.getUniqueId(), viewer);
    }
}
