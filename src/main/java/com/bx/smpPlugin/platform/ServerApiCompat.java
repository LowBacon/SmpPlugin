package com.bx.smpPlugin.platform;

import org.bukkit.GameRule;
import org.bukkit.advancement.Advancement;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

/**
 * Small adapters for API that differs between Spigot and Paper/Purpur 26.3.
 *
 * <p>Paper hard-forked from Spigot, so the same feature can have different names, return types or
 * even class kinds (Spigot's {@code GameRule} is an interface, Paper's is a class). Code compiled
 * against one API can then fail on the other with {@code NoSuchFieldError} or
 * {@code NoSuchMethodError}. Everything here is resolved by name at runtime and works on both.</p>
 */
public final class ServerApiCompat {

    /** Spigot 26.3 name first, then the Paper/Purpur name. */
    static final String[] ADVANCEMENT_MESSAGE_RULE_FIELDS = {"SHOW_ADVANCEMENT_MESSAGES", "ANNOUNCE_ADVANCEMENTS"};

    private static volatile Optional<GameRule<Boolean>> advancementMessagesRule;

    /**
     * What a completed advancement shows in chat.
     *
     * @param title plain-text title
     * @param frame {@code TASK}, {@code GOAL} or {@code CHALLENGE}; empty when unknown
     */
    public record AdvancementInfo(String title, String frame) {
    }

    private ServerApiCompat() {
    }

    /**
     * The boolean game rule controlling vanilla advancement chat messages
     * ({@code SHOW_ADVANCEMENT_MESSAGES} on Spigot, {@code ANNOUNCE_ADVANCEMENTS} on Paper/Purpur).
     */
    public static Optional<GameRule<Boolean>> advancementMessagesRule() {
        Optional<GameRule<Boolean>> cached = advancementMessagesRule;
        if (cached == null) {
            cached = findBooleanGameRule(GameRule.class, ADVANCEMENT_MESSAGE_RULE_FIELDS);
            advancementMessagesRule = cached;
        }
        return cached;
    }

    @SuppressWarnings("unchecked")
    static Optional<GameRule<Boolean>> findBooleanGameRule(Class<?> holder, String... fieldNames) {
        for (String fieldName : fieldNames) {
            try {
                Field field = holder.getField(fieldName);
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Object value = field.get(null);
                if (value instanceof GameRule<?> rule) {
                    return Optional.of((GameRule<Boolean>) rule);
                }
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // Try the next name.
            }
        }
        return Optional.empty();
    }

    /**
     * Reads an advancement's chat title and frame on either API. Empty for advancements without a
     * display (recipes and other hidden ones), or when neither API shape is present.
     */
    public static Optional<AdvancementInfo> describe(Advancement advancement) {
        return describeAdvancement(advancement);
    }

    /** Works on any object with a {@code getDisplay()} method; separated so it can be tested without a server. */
    static Optional<AdvancementInfo> describeAdvancement(Object advancement) {
        if (advancement == null) {
            return Optional.empty();
        }
        Object display = invoke(advancement, "getDisplay");
        if (display == null) {
            return Optional.empty();
        }

        // Spigot: getTitle() -> String, getType() -> AdvancementDisplayType
        Object spigotTitle = invoke(display, "getTitle");
        if (spigotTitle instanceof String title) {
            return Optional.of(new AdvancementInfo(title, enumName(invoke(display, "getType"))));
        }

        // Paper/Purpur: title() -> Component, frame() -> AdvancementDisplay.Frame
        Object component = invoke(display, "title");
        if (component != null) {
            String title = plainText(component);
            if (title != null) {
                return Optional.of(new AdvancementInfo(title, enumName(invoke(display, "frame"))));
            }
        }
        return Optional.empty();
    }

    /** Serialises an Adventure component to plain text with the server's own serializer. */
    static String plainText(Object component) {
        try {
            ClassLoader loader = component.getClass().getClassLoader();
            Class<?> serializerType = Class.forName(
                    "net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer", true, loader);
            Object serializer = serializerType.getMethod("plainText").invoke(null);
            for (Method method : serializerType.getMethods()) {
                if (method.getName().equals("serialize") && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isInstance(component)) {
                    return (String) method.invoke(serializer, component);
                }
            }
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
            // Fall through to the component's own text, if it has any.
        }
        Object content = invoke(component, "content");
        return content instanceof String text ? text : null;
    }

    private static String enumName(Object value) {
        return value instanceof Enum<?> constant ? constant.name() : "";
    }

    private static Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }
}
