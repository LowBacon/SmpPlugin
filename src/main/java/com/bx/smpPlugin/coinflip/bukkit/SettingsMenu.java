package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.coinflip.AnimationPreset;
import com.bx.smpPlugin.coinflip.CoinFlipPreferences;
import com.bx.smpPlugin.coinflip.CoinFlipService;
import com.bx.smpPlugin.coinflip.CoinSide;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Personal settings: presentation, notifications, privacy, quick confirm, and self-exclusion. */
public final class SettingsMenu extends CoinFlipMenu {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final int[] EXCLUSION_SLOTS = {37, 38, 39, 40};

    public SettingsMenu(CoinFlipModule module, UUID viewer) {
        super(module, viewer, "Settings", 54);
    }

    @Override
    protected void render(Player player) {
        CoinFlipPreferences prefs = prefs();
        boolean readable = readable();

        button(10, Gui.item(Material.CLOCK, Gui.INFO + "Animation: " + Gui.VALUE + prefs.animation().displayName(),
                Gui.MUTED + "Fast, Subtle, Classic, Cinematic,", Gui.MUTED + "Reduced motion or Off.",
                Gui.MUTED + "Every option shows the same result."), () -> update(p -> p.withAnimation(p.animation().next())));
        toggle(11, Material.NOTE_BLOCK, "Sounds", prefs.sounds(), "Clicks, flip ticks and result sounds.",
                p -> p.withSounds(!p.sounds()));
        toggle(12, Material.BLAZE_POWDER, "Particles", prefs.particles(), "A few particles when a coin lands.",
                p -> p.withParticles(!p.particles()));
        toggle(13, Material.OAK_SIGN, "Readable menus", prefs.readableMode(), "Fewer symbols and plain borders.",
                p -> p.withReadableMode(!p.readableMode()));
        toggle(14, Material.BELL, "Challenge invitations", prefs.inviteNotifications(),
                "When off, nobody can send you challenges.", p -> p.withInviteNotifications(!p.inviteNotifications()));
        toggle(15, Material.GOAT_HORN, "Announce my big wins", prefs.announceMyWins(),
                "Whether your large wins appear in chat.", p -> p.withAnnounceMyWins(!p.announceMyWins()));
        toggle(16, Material.SPYGLASS, "Public profile", prefs.publicProfile(),
                "Others see your stats; you appear on leaderboards.", p -> p.withPublicProfile(!p.publicProfile()));
        toggle(22, Material.FEATHER, "Quick confirm", prefs.quickConfirm(),
                "Join from the browser without the review screen.", p -> p.withQuickConfirm(!p.quickConfirm()));

        CoinSide saved = prefs.savedSide();
        button(28, Gui.item(saved == null ? Material.ENDER_PEARL : Gui.sideMaterial(saved, config().headsMaterial(), config().tailsMaterial()),
                Gui.INFO + "Preferred side: " + Gui.VALUE + (saved == null ? "none" : saved.displayName()),
                Gui.MUTED + "Pre-selected when you create a match."),
                () -> update(p -> p.withSaved(p.savedSide() == null ? CoinSide.HEADS
                        : p.savedSide() == CoinSide.HEADS ? CoinSide.TAILS : null, p.savedStakeCents())));
        button(29, Gui.item(Material.NAME_TAG, Gui.INFO + "Preferred stake: " + Gui.VALUE
                        + (prefs.savedStakeCents() > 0 ? money(prefs.savedStakeCents()) : "none"),
                Gui.MUTED + "Set it from the create screen.", Gui.MUTED + "Click to clear."),
                () -> update(p -> p.withSaved(p.savedSide(), 0L)));

        long now = System.currentTimeMillis();
        if (prefs.selfExcluded(now)) {
            set(31, Gui.item(Material.IRON_DOOR, Gui.GOLD + "Wagering paused",
                    Gui.MUTED + "Until " + TIME.format(Instant.ofEpochMilli(prefs.selfExcludedUntilMillis())) + ".",
                    Gui.MUTED + "Practice mode still works."));
        } else {
            set(31, Gui.item(Material.OAK_DOOR, Gui.INFO + "Take a break",
                    Gui.MUTED + "Pause CoinFlip wagering for yourself.", Gui.MUTED + "Pick a length below.",
                    Gui.MUTED + "It cannot be shortened once set."));
        }
        List<Integer> options = config().selfExclusionHours();
        for (int i = 0; i < options.size() && i < EXCLUSION_SLOTS.length; i++) {
            int hours = options.get(i);
            String label = hours % 24 == 0 ? (hours / 24) + " day" + (hours / 24 == 1 ? "" : "s") : hours + " hour" + (hours == 1 ? "" : "s");
            button(EXCLUSION_SLOTS[i], Gui.item(Material.BARRIER, Gui.GOLD + "Pause for " + label), () -> openMenu(
                    new ConfirmMenu(module, viewer, "Pause wagering for " + label + "?",
                            List.of(Gui.MUTED + "You will not be able to create or join", Gui.MUTED + "matches until it ends.",
                                    Gui.MUTED + "This cannot be undone early."),
                            () -> {
                                long until = System.currentTimeMillis() + hours * 3_600_000L;
                                CoinFlipPreferences updated = prefs().withSelfExclusionUntil(until);
                                module.service().savePreferences(viewer, updated);
                                message("self-exclusion-set", CoinFlipService.vars("time",
                                        TIME.format(Instant.ofEpochMilli(updated.selfExcludedUntilMillis()))));
                                openMenu(new SettingsMenu(module, viewer));
                            },
                            () -> openMenu(new SettingsMenu(module, viewer)))));
        }
        set(43, Gui.item(Material.BOOK, Gui.INFO + "About your data",
                Gui.MUTED + "Settings are stored per player.", Gui.MUTED + "A private profile hides you from",
                Gui.MUTED + "leaderboards and other players."));
        back(() -> openMenu(new MainMenu(module, viewer)));
        if (readable) {
            set(4, Gui.item(Material.COMPARATOR, Gui.INFO + "Settings"));
        } else {
            set(4, Gui.item(Material.COMPARATOR, Gui.INFO + "Settings", Gui.MUTED + "Changes are saved immediately."));
        }
    }

    private void toggle(int slot, Material icon, String name, boolean on, String description,
                        UnaryOperator<CoinFlipPreferences> change) {
        button(slot, Gui.glow(Gui.item(icon, (on ? Gui.GOOD : Gui.MUTED) + name + ": " + (on ? "on" : "off"),
                Gui.MUTED + description, Gui.MUTED + "Click to switch."), on), () -> update(change));
    }

    private void update(UnaryOperator<CoinFlipPreferences> change) {
        module.service().savePreferences(viewer, change.apply(prefs()));
        refresh();
    }
}
