// SmpPlugin by LowBacon
package com.bx.smpPlugin.platform;

import org.bukkit.GameRule;
import org.bukkit.advancement.Advancement;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

public final class ServerApiCompat {

    static final String[] ADVANCEMENT_MESSAGE_RULE_FIELDS = {"SHOW_ADVANCEMENT_MESSAGES", "ANNOUNCE_ADVANCEMENTS"};

    private static volatile Optional<GameRule<Boolean>> advancementMessagesRule;

    public record AdvancementInfo(String title, String frame) {
    }

    private ServerApiCompat() {
    }

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

    public static Optional<AdvancementInfo> describe(Advancement advancement) {
        return describeAdvancement(advancement);
    }

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
