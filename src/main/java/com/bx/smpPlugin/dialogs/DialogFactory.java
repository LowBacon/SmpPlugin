// SmpPlugin by LowBacon
package com.bx.smpPlugin.dialogs;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DialogFactory {

    public static final class Screen {

        private final Player viewer;
        private final CommandRegistrar commands;
        private final List<DialogBody> body = new ArrayList<>();
        private final List<DialogInput> inputs = new ArrayList<>();
        private final List<ActionButton> buttons = new ArrayList<>();
        private Component title = Component.empty();
        private Component externalTitle;
        private ActionButton exitAction;
        private int columns = 1;
        private boolean canCloseWithEscape = true;
        private boolean pause = false;
        private DialogBase.DialogAfterAction afterAction = DialogBase.DialogAfterAction.NONE;

        private Screen(Player viewer, CommandRegistrar commands) {
            this.viewer = viewer;
            this.commands = commands;
        }

        public Screen title(String raw) {
            this.title = DialogText.of(raw, viewer);
            return this;
        }

        public Screen externalTitle(String raw) {
            this.externalTitle = raw == null || raw.isBlank() ? null : DialogText.of(raw, viewer);
            return this;
        }

        public Screen columns(int columns) {
            this.columns = Math.max(1, Math.min(8, columns));
            return this;
        }

        public Screen canCloseWithEscape(boolean value) {
            this.canCloseWithEscape = value;
            return this;
        }

        public Screen pause(boolean value) {
            this.pause = value;
            return this;
        }

        public Screen afterAction(String raw) {
            this.afterAction = parseAfterAction(raw, DialogBase.DialogAfterAction.NONE);
            return this;
        }

        public Screen afterAction(DialogBase.DialogAfterAction value) {
            if (value != null) {
                this.afterAction = value;
            }
            return this;
        }

        public Screen text(String raw) {
            return text(raw, 0);
        }

        public Screen text(String raw, int width) {
            if (raw != null && !raw.isBlank()) {
                for (Component line : DialogText.lines(raw, viewer)) {
                    body.add(width > 0
                            ? DialogBody.plainMessage(line, DialogConfig.clampWidth(width))
                            : DialogBody.plainMessage(line));
                }
            }
            return this;
        }

        public Screen text(Component component) {
            if (component != null) {
                body.add(DialogBody.plainMessage(component));
            }
            return this;
        }

        public Screen item(String materialName) {
            Material material = parseMaterial(materialName);
            if (material != null) {
                item(new ItemStack(material));
            }
            return this;
        }

        public Screen item(ItemStack stack) {
            return item(stack, false);
        }

        // With the tooltip the player can hover the item and read its name, enchantments and lore.
        public Screen item(ItemStack stack, boolean tooltip) {
            if (stack != null && !stack.getType().isAir()) {
                body.add(DialogBody.item(stack)
                        .showDecorations(tooltip)
                        .showTooltip(tooltip)
                        .build());
            }
            return this;
        }

        public Screen inputs(List<DialogConfig.InputSpec> specs) {
            if (specs != null) {
                specs.forEach(this::input);
            }
            return this;
        }

        public Screen input(DialogConfig.InputSpec spec) {
            if (spec == null) {
                return this;
            }
            if (spec.isDropdown()) {
                List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
                boolean anyDefault = spec.options().stream().anyMatch(DialogConfig.OptionSpec::isDefault);
                for (int index = 0; index < spec.options().size(); index++) {
                    DialogConfig.OptionSpec option = spec.options().get(index);
                    // The client refuses a dropdown with no selected entry, so an unmarked list
                    // falls back to selecting its first option.
                    boolean initial = anyDefault ? option.isDefault() : index == 0;
                    entries.add(SingleOptionDialogInput.OptionEntry.create(
                            option.id(),
                            DialogText.ofOrEmpty(option.label(), viewer),
                            initial
                    ));
                }
                inputs.add(DialogInput.singleOption(spec.id(), DialogText.ofOrEmpty(spec.label(), viewer), entries)
                        .width(spec.width())
                        .labelVisible(false)
                        .build());
                return this;
            }

            var builder = DialogInput.text(spec.id(), DialogText.ofOrEmpty(spec.label(), viewer))
                    .width(spec.width())
                    .maxLength(spec.maxLength());
            if (spec.initial() != null && !spec.initial().isEmpty()) {
                builder.initial(spec.initial());
            }
            inputs.add(builder.build());
            return this;
        }

        public Screen buttons(List<DialogConfig.ButtonSpec> specs) {
            if (specs != null) {
                specs.forEach(this::button);
            }
            return this;
        }

        public Screen button(DialogConfig.ButtonSpec spec) {
            ActionButton button = toButton(spec, viewer, commands);
            if (button != null) {
                buttons.add(button);
            }
            return this;
        }

        public Screen button(String label, String tooltip, int width, String action) {
            return button(new DialogConfig.ButtonSpec(label, tooltip, DialogConfig.clampWidth(width), action, null));
        }

        public Screen button(Component label, Component tooltip, int width, String action) {
            if (label == null) {
                return this;
            }
            var builder = ActionButton.builder(label)
                    .width(DialogConfig.clampWidth(width));
            if (tooltip != null) {
                builder.tooltip(tooltip);
            }
            if (action != null) {
                Key key = DialogActions.key(action);
                if (key != null) {
                    builder.action(DialogAction.customClick(key, null));
                }
            }
            buttons.add(builder.build());
            return this;
        }

        public Screen commandButton(String label, String tooltip, int width, String command) {
            return button(new DialogConfig.ButtonSpec(
                    label, tooltip, DialogConfig.clampWidth(width), null, command));
        }

        public Screen inertButton(String label, String tooltip, int width) {
            if (label == null) {
                return this;
            }
            var builder = ActionButton.builder(DialogText.of(label, viewer))
                    .width(DialogConfig.clampWidth(width));
            applyTooltip(builder, tooltip, viewer);
            buttons.add(builder.build());
            return this;
        }

        public Screen exit(String label, String action, int width) {
            if (label == null || label.isBlank()) {
                return this;
            }
            this.exitAction = toButton(
                    new DialogConfig.ButtonSpec(label, null, DialogConfig.clampWidth(width), action, null),
                    viewer,
                    commands
            );
            return this;
        }

        public Screen exit(DialogConfig.ButtonSpec spec) {
            this.exitAction = toButton(spec, viewer, commands);
            return this;
        }

        public boolean isEmpty() {
            return buttons.isEmpty();
        }

        public Dialog build() {
            DialogBase base = base();

            DialogType type;
            if (buttons.isEmpty()) {
                type = exitAction == null ? DialogType.notice() : DialogType.notice(exitAction);
            } else {
                var multi = DialogType.multiAction(List.copyOf(buttons)).columns(columns);
                if (exitAction != null) {
                    multi.exitAction(exitAction);
                }
                type = multi.build();
            }

            DialogBase builtBase = base;
            DialogType builtType = type;
            return Dialog.create(factory -> factory.empty().base(builtBase).type(builtType));
        }

        private DialogBase base() {
            DialogBase.Builder builder = DialogBase.builder(title)
                    .canCloseWithEscape(canCloseWithEscape)
                    .pause(pause)
                    .afterAction(afterAction)
                    .body(List.copyOf(body))
                    .inputs(List.copyOf(inputs));
            if (externalTitle != null) {
                builder.externalTitle(externalTitle);
            }
            return builder.build();
        }

        public Dialog buildConfirmation(DialogConfig.ButtonSpec yes, DialogConfig.ButtonSpec no) {
            DialogBase base = base();
            ActionButton yesButton = toButton(yes, viewer, commands);
            ActionButton noButton = toButton(no, viewer, commands);
            return Dialog.create(factory -> factory.empty()
                    .base(base)
                    .type(DialogType.confirmation(yesButton, noButton)));
        }
    }

    private DialogFactory() {
    }

    @FunctionalInterface
    public interface CommandRegistrar {
        String register(String command);
    }

    public static Screen screen(Player viewer, CommandRegistrar commands) {
        return new Screen(viewer, commands);
    }

    public static Screen screen(Player viewer) {
        return new Screen(viewer, null);
    }

    private static ActionButton toButton(DialogConfig.ButtonSpec spec, Player viewer, CommandRegistrar commands) {
        if (spec == null || spec.label() == null) {
            return null;
        }
        var builder = ActionButton.builder(DialogText.of(spec.label(), viewer))
                .width(DialogConfig.clampWidth(spec.width()));
        applyTooltip(builder, spec.tooltip(), viewer);

        if (spec.hasAction()) {
            Key key = DialogActions.key(spec.action());
            if (key != null) {
                builder.action(DialogAction.customClick(key, null));
            }
        } else if (spec.hasCommand()) {
            DialogAction action = commandAction(spec.command(), commands);
            if (action != null) {
                builder.action(action);
            }
        }
        return builder.build();
    }

    private static DialogAction commandAction(String configured, CommandRegistrar commands) {
        String command = configured.trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        // A template substitutes the dialog's own inputs, so the client has to run that one.
        if (usesInputSubstitution(command)) {
            return DialogAction.commandTemplate(command);
        }
        if (commands != null) {
            String id = commands.register(command);
            if (id != null) {
                return DialogAction.customClick(DialogActions.of(DialogActions.RUN_COMMAND, id), null);
            }
        }
        return DialogAction.staticAction(ClickEvent.runCommand('/' + command));
    }

    static boolean usesInputSubstitution(String command) {
        if (command == null) {
            return false;
        }
        int open = command.indexOf("$(");
        return open >= 0 && command.indexOf(')', open + 2) > open + 2;
    }

    private static void applyTooltip(ActionButton.Builder builder, String tooltip, Player viewer) {
        Component rendered = DialogText.tooltip(tooltip, viewer);
        if (rendered != null) {
            builder.tooltip(rendered);
        }
    }

    private static DialogBase.DialogAfterAction parseAfterAction(String raw, DialogBase.DialogAfterAction fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return DialogBase.DialogAfterAction.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static Material parseMaterial(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return Material.matchMaterial(name.trim().toUpperCase(java.util.Locale.ROOT));
    }

    public static Map<String, String> tokens(String... pairs) {
        return DialogConfig.tokens(pairs);
    }
}
