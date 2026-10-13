package com.bx.smpPlugin.anticheat;

import java.util.Locale;
import java.util.Optional;

/** Every server-side check. Settings live under {@code CHECKS.<config key>} in anticheat.yml. */
public enum CheckType {
    FLY(CheckCategory.MOVEMENT, "Fly",
            "Rose higher than jump strength allows, hovered, glided, or jumped again in mid-air."),
    SPEED(CheckCategory.MOVEMENT, "Speed",
            "Moved horizontally faster than the movement-speed attribute allows."),
    REACH(CheckCategory.COMBAT, "Reach",
            "Hit an entity beyond the entity interaction range, with lag compensation."),
    WALL_HIT(CheckCategory.COMBAT, "Wall hit",
            "Hit an entity through solid, view-blocking blocks."),
    KILLAURA(CheckCategory.COMBAT, "Kill aura",
            "Hit entities far outside the view direction, or several entities in one tick."),
    AUTOCLICKER(CheckCategory.COMBAT, "Autoclicker",
            "Sustained click rate beyond human limits."),
    FAST_BREAK(CheckCategory.WORLD, "Fast break",
            "Broke a block faster than the tool, effects and enchantments allow."),
    NUKER(CheckCategory.WORLD, "Nuker",
            "Broke blocks at an impossible rate."),
    FAST_PLACE(CheckCategory.WORLD, "Fast place",
            "Placed blocks at an impossible rate."),
    BLOCK_REACH(CheckCategory.WORLD, "Block reach",
            "Broke or placed a block beyond the block interaction range."),
    XRAY(CheckCategory.WORLD, "X-ray pattern",
            "Mined hidden rare-ore veins at a rate far above normal mining."),
    TIMER(CheckCategory.MOVEMENT, "Timer",
            "Sent movement updates faster than the 20-per-second game clock (game speed-up)."),
    NOFALL(CheckCategory.MOVEMENT, "No fall",
            "Claimed to be on the ground while falling through the air, to skip fall damage."),
    STEP(CheckCategory.MOVEMENT, "Step",
            "Stepped up higher in one update than the step-height attribute or a jump allows."),
    JESUS(CheckCategory.MOVEMENT, "Water walk",
            "Walked on top of water or lava without anything to stand on."),
    VEHICLE_FLY(CheckCategory.MOVEMENT, "Boat fly",
            "Rose into the air in a boat with no water or bubble column underneath."),
    VELOCITY(CheckCategory.COMBAT, "Anti-knockback",
            "Took far less knockback than the server applied."),
    CRITICALS(CheckCategory.COMBAT, "Criticals",
            "Landed critical hits while standing on the ground (fake mini-jumps)."),
    SCAFFOLD(CheckCategory.WORLD, "Scaffold",
            "Placed blocks on faces that cannot be seen or aimed at, or towered up impossibly fast."),
    GHOST_HAND(CheckCategory.WORLD, "Through-wall interaction",
            "Opened containers or broke blocks that are completely hidden behind other blocks."),
    INVENTORY(CheckCategory.PLAYER, "Inventory",
            "Moved at full speed while using an inventory, or clicked items faster than humanly possible."),
    AUTO_TOTEM(CheckCategory.PLAYER, "Auto totem",
            "Moved a new totem into the off-hand faster than human reaction time after one was used."),
    BAD_CLIENT(CheckCategory.PLAYER, "Cheat client",
            "Joined with a client brand or mod channel that belongs to a known cheat client.");

    private final CheckCategory category;
    private final String displayName;
    private final String description;

    CheckType(CheckCategory category, String displayName, String description) {
        this.category = category;
        this.displayName = displayName;
        this.description = description;
    }

    public CheckCategory category() {
        return category;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /** Key in anticheat.yml, e.g. {@code FAST-BREAK}. */
    public String configKey() {
        return name().replace('_', '-');
    }

    public static Optional<CheckType> fromInput(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String normalized = input.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        for (CheckType type : values()) {
            if (type.name().equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
