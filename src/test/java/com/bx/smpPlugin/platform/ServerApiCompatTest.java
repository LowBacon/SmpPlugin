// SmpPlugin by LowBacon
package com.bx.smpPlugin.platform;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerApiCompatTest {

    @Test
    void theCompiledServerApiHasOneOfTheAdvancementRuleNames() throws Exception {
        // Load without initialising: GameRule's static fields need a running server.
        Class<?> gameRule = Class.forName("org.bukkit.GameRule", false, getClass().getClassLoader());
        boolean found = Arrays.stream(ServerApiCompat.ADVANCEMENT_MESSAGE_RULE_FIELDS).anyMatch(name -> {
            try {
                gameRule.getField(name);
                return true;
            } catch (NoSuchFieldException missing) {
                return false;
            }
        });
        assertTrue(found, "GameRule has none of " + Arrays.toString(ServerApiCompat.ADVANCEMENT_MESSAGE_RULE_FIELDS)
                + "; vanilla advancement messages could not be turned off.");
    }

    @Test
    void readsSpigotShapedAdvancements() {
        Optional<ServerApiCompat.AdvancementInfo> info =
                ServerApiCompat.describeAdvancement(new FakeAdvancement(new SpigotDisplay("Stone Age", Frame.GOAL)));

        assertEquals(Optional.of(new ServerApiCompat.AdvancementInfo("Stone Age", "GOAL")), info);
    }

    @Test
    void readsPaperShapedAdvancements() {
        Optional<ServerApiCompat.AdvancementInfo> info =
                ServerApiCompat.describeAdvancement(new FakeAdvancement(new PaperDisplay(Component.text("Diamonds!"), Frame.CHALLENGE)));

        assertEquals(Optional.of(new ServerApiCompat.AdvancementInfo("Diamonds!", "CHALLENGE")), info);
    }

    @Test
    void ignoresAdvancementsWithoutADisplay() {
        assertTrue(ServerApiCompat.describeAdvancement(new FakeAdvancement(null)).isEmpty());
        assertTrue(ServerApiCompat.describeAdvancement(null).isEmpty());
    }

    public enum Frame { TASK, GOAL, CHALLENGE }

    public static final class FakeAdvancement {
        private final Object display;

        FakeAdvancement(Object display) {
            this.display = display;
        }

        public Object getDisplay() {
            return display;
        }
    }

    public static final class SpigotDisplay {
        private final String title;
        private final Frame type;

        SpigotDisplay(String title, Frame type) {
            this.title = title;
            this.type = type;
        }

        public String getTitle() {
            return title;
        }

        public Frame getType() {
            return type;
        }
    }

    public static final class PaperDisplay {
        private final Component title;
        private final Frame frame;

        PaperDisplay(Component title, Frame frame) {
            this.title = title;
            this.frame = frame;
        }

        public Component title() {
            return title;
        }

        public Frame frame() {
            return frame;
        }
    }
}
