package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.menus.QuickBuyCatalog;
import com.bx.smpPlugin.menus.QuickBuyItemSelectMenu;
import com.bx.smpPlugin.menus.QuickBuyMenu;
import com.bx.smpPlugin.menus.QuickBuySounds;
import com.bx.smpPlugin.models.QuickBuyEntry;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.SimpleForm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paged Floodgate catalogue for pinning a Quick Buy item. The Java Choose Item dialog sends one
 * button per material, and that form freezes a Bedrock client.
 */
public final class QuickBuyBedrockManager implements Listener {

    /**
     * A chest opened in the same moment a Floodgate form closes leaves Bedrock unable to open
     * any container until the player rejoins.
     */
    static final long CHEST_AFTER_FORM_TICKS = 10L;

    private final SmpPlugin plugin;
    private final Map<UUID, Draft> drafts = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> listingGeneration = new ConcurrentHashMap<>();

    public QuickBuyBedrockManager(SmpPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        drafts.remove(playerId);
        listingGeneration.remove(playerId);
    }

    public boolean openCatalogue(Player player, int targetSlot, String query, int page) {
        if (!accepts(player)) {
            return false;
        }
        Draft draft = draft(player);
        draft.slot = targetSlot;
        draft.query = query == null ? "" : query.trim();
        draft.page = Math.max(0, page);
        draft.material = null;
        draft.enchants.clear();
        if (draft.formOpen) {
            replaceCatalogue(player, draft);
            return true;
        }
        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            closeMenu(player);
            plugin.getSpigotScheduler().runEntityLater(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                replaceCatalogue(player, draft);
            }, 1L);
        }, 1L);
        return true;
    }

    /**
     * Search from the chest catalogue. Returns false when Floodgate cannot take the form, so the
     * caller can ask in chat instead of opening a sign.
     */
    public boolean openItemSearch(Player player, int targetSlot, String query) {
        if (!accepts(player)) {
            return false;
        }
        Draft draft = draft(player);
        draft.slot = targetSlot;
        draft.query = query == null ? "" : query.trim();
        draft.page = 0;
        draft.material = null;
        draft.enchants.clear();
        draft.formOpen = false;
        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            closeMenu(player);
            plugin.getSpigotScheduler().runEntityLater(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                draft.formOpen = true;
                openSearch(player);
            }, 1L);
        }, 1L);
        return true;
    }

    /**
     * Search on the Quick Buy grid. The chest reopens only after the form has been gone for a bit.
     */
    public boolean openListingSearch(
            Player player,
            QuickBuyMenu.Filter filter,
            boolean editMode,
            String currentQuery
    ) {
        if (!accepts(player)) {
            return false;
        }
        QuickBuyMenu.Filter safeFilter = filter == null ? QuickBuyMenu.Filter.DEFAULT : filter;
        String query = currentQuery == null ? "" : currentQuery;
        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            closeMenu(player);
            plugin.getSpigotScheduler().runEntityLater(player, () -> {
                if (player.isOnline()) {
                    sendListingForm(player, safeFilter, editMode, query);
                }
            }, 1L);
        }, 1L);
        return true;
    }

    private SimpleForm catalogueForm(Player player, Draft draft) {
        List<Material> items = QuickBuyCatalog.getSelectableMaterials(plugin, draft.query);
        int pageSize = pageSize();
        int pages = QuickBuyBedrockPages.pageCount(items.size(), pageSize);
        int page = QuickBuyBedrockPages.clampPage(draft.page, pages);
        draft.page = page;
        List<Material> shown = QuickBuyBedrockPages.slice(items, page, pageSize);
        int shownPage = page + 1;

        String content;
        if (items.isEmpty() && !draft.query.isBlank()) {
            content = text("QUICK_BUY.BEDROCK.CATALOGUE_EMPTY", "Nothing matches {query}.",
                    "{query}", draft.query);
        } else if (!draft.query.isBlank()) {
            content = text("QUICK_BUY.BEDROCK.CATALOGUE_SEARCHING",
                    "Search: {query}. Page {page}/{pages}.",
                    "{query}", draft.query,
                    "{page}", String.valueOf(shownPage),
                    "{pages}", String.valueOf(pages));
        } else {
            content = text("QUICK_BUY.BEDROCK.CATALOGUE_PAGE", "Page {page}/{pages}.",
                    "{page}", String.valueOf(shownPage),
                    "{pages}", String.valueOf(pages));
        }

        SimpleForm.Builder form = SimpleForm.builder()
                .title(text("QUICK_BUY.BEDROCK.CATALOGUE_TITLE", "Choose Item"))
                .content(content);
        List<Runnable> actions = new ArrayList<>();

        form.button(text("QUICK_BUY.BEDROCK.BUTTON_SEARCH", "Search"));
        actions.add(() -> openSearch(player));

        form.button(text("QUICK_BUY.BEDROCK.BUTTON_BACK", "Back"));
        actions.add(() -> returnToShop(player));

        if (page > 0) {
            form.button(text("QUICK_BUY.BEDROCK.BUTTON_PREVIOUS", "Previous page"));
            actions.add(() -> {
                QuickBuySounds.pageTurn(player, plugin);
                openCatalogue(player, draft.slot, draft.query, page - 1);
            });
        }
        if (page + 1 < pages && !shown.isEmpty()) {
            form.button(text("QUICK_BUY.BEDROCK.BUTTON_NEXT", "Next page"));
            actions.add(() -> {
                QuickBuySounds.pageTurn(player, plugin);
                openCatalogue(player, draft.slot, draft.query, page + 1);
            });
        }
        for (Material material : shown) {
            Material chosen = material;
            form.button(itemLabel(player, chosen));
            actions.add(() -> select(player, chosen));
        }

        bind(player, draft, form, actions, () -> returnToShop(player));
        return form.build();
    }

    private void openSearch(Player player) {
        Draft draft = draft(player);
        CustomForm.Builder form = CustomForm.builder()
                .title(text("QUICK_BUY.BEDROCK.SEARCH_TITLE", "Search items"))
                .input(text("QUICK_BUY.BEDROCK.SEARCH_INPUT", "Item name"), "", draft.query);
        int generation = draft.generation;
        form.validResultHandler(response -> {
            draft.generation++;
            schedule(player, () -> {
                String typed = readInput(response);
                QuickBuySounds.play(player, plugin, QuickBuySounds.SEARCH);
                openCatalogue(player, draft.slot, typed, 0);
            });
        });
        form.closedResultHandler(() -> {
            if (draft.generation != generation) {
                return;
            }
            schedule(player, () -> openCatalogue(player, draft.slot, draft.query, draft.page));
        });
        if (!send(player, form.build())) {
            draft.formOpen = false;
            openPickerLater(player, draft);
        }
    }

    private void sendListingForm(Player player, QuickBuyMenu.Filter filter, boolean editMode, String currentQuery) {
        CustomForm.Builder form = CustomForm.builder()
                .title(text("QUICK_BUY.BEDROCK.SEARCH_TITLE", "Search items"))
                .input(text("QUICK_BUY.BEDROCK.SEARCH_INPUT", "Item name"), "", currentQuery);
        UUID playerId = player.getUniqueId();
        int generation = listingGeneration.getOrDefault(playerId, 0);
        form.validResultHandler(response -> {
            listingGeneration.put(playerId, generation + 1);
            schedule(player, () -> {
                String query = readInput(response);
                QuickBuySounds.play(player, plugin, QuickBuySounds.SEARCH);
                openMenuLater(player, () -> new QuickBuyMenu(plugin, filter, editMode, query).open(player));
            });
        });
        form.closedResultHandler(() -> {
            if (listingGeneration.getOrDefault(playerId, 0) != generation) {
                return;
            }
            schedule(player, () -> openMenuLater(player, () ->
                    new QuickBuyMenu(plugin, filter, editMode, currentQuery).open(player)));
        });
        if (!send(player, form.build())) {
            openMenuLater(player, () -> new QuickBuyMenu(plugin, filter, editMode, currentQuery).open(player));
        }
    }

    private void replaceCatalogue(Player player, Draft draft) {
        Form form;
        try {
            form = catalogueForm(player, draft);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not build Quick Buy Bedrock catalogue for " + player.getName()
                    + ": " + throwable.getMessage());
            draft.formOpen = false;
            openPickerLater(player, draft);
            return;
        }
        if (!send(player, form)) {
            draft.formOpen = false;
            openPickerLater(player, draft);
            return;
        }
        draft.formOpen = true;
    }

    private void openPickerLater(Player player, Draft draft) {
        openMenuLater(player, () ->
                new QuickBuyItemSelectMenu(plugin, draft.slot, 0, draft.query).open(player));
    }

    private void openMenuLater(Player player, Runnable open) {
        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            if (player.isOnline()) {
                open.run();
            }
        }, CHEST_AFTER_FORM_TICKS);
    }

    private static String readInput(org.geysermc.cumulus.response.CustomFormResponse response) {
        if (response == null) {
            return "";
        }
        try {
            String typed = response.asInput(0);
            return typed == null ? "" : typed.trim();
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private void select(Player player, Material material) {
        if (material == null || QuickBuyCatalog.isBlacklisted(plugin, material)) {
            QuickBuySounds.fail(player, plugin);
            Draft draft = draft(player);
            openCatalogue(player, draft.slot, draft.query, draft.page);
            return;
        }
        Draft draft = draft(player);
        draft.material = material;
        draft.enchants.clear();
        QuickBuySounds.click(player, plugin);
        if (QuickBuyCatalog.isEnchantable(material)) {
            openEnchantments(player);
        } else {
            openAmount(player);
        }
    }

    private void openEnchantments(Player player) {
        Draft draft = draft(player);
        Material material = draft.material;
        if (material == null) {
            openCatalogue(player, draft.slot, draft.query, draft.page);
            return;
        }
        List<QuickBuyCatalog.EnchantmentSpec> specs = QuickBuyCatalog.getEnchantmentSpecs(plugin, material);
        if (specs.isEmpty()) {
            openAmount(player);
            return;
        }

        SimpleForm.Builder form = SimpleForm.builder()
                .title(text("QUICK_BUY.BEDROCK.ENCHANT_TITLE", "Choose Enchantments"))
                .content(text("QUICK_BUY.BEDROCK.ENCHANT_CONTENT", "Tap an enchantment to change its level.")
                        + "\n" + itemName(material));
        List<Runnable> actions = new ArrayList<>();
        for (QuickBuyCatalog.EnchantmentSpec spec : specs) {
            QuickBuyCatalog.EnchantmentSpec chosen = spec;
            form.button(enchantLabel(draft, chosen));
            actions.add(() -> cycleEnchant(player, chosen));
        }
        form.button(text("QUICK_BUY.BEDROCK.BUTTON_SKIP", "Skip enchantments"));
        actions.add(() -> {
            draft.enchants.clear();
            QuickBuySounds.click(player, plugin);
            openAmount(player);
        });
        if (draft.enchants.values().stream().anyMatch(level -> level != null && level > 0)) {
            form.button(text("QUICK_BUY.BEDROCK.BUTTON_CONFIRM", "Confirm enchantments"));
            actions.add(() -> {
                QuickBuySounds.click(player, plugin);
                openAmount(player);
            });
        }
        form.button(text("QUICK_BUY.BEDROCK.BUTTON_BACK", "Back"));
        actions.add(() -> {
            QuickBuySounds.play(player, plugin, QuickBuySounds.CANCEL);
            openCatalogue(player, draft.slot, draft.query, draft.page);
        });
        bind(player, draft, form, actions, () -> openCatalogue(player, draft.slot, draft.query, draft.page));
        send(player, form.build());
    }

    private void cycleEnchant(Player player, QuickBuyCatalog.EnchantmentSpec spec) {
        Draft draft = draft(player);
        Enchantment enchantment = spec.enchantment() != null
                ? spec.enchantment()
                : QuickBuyEntry.findEnchantment(spec.key());
        if (enchantment == null) {
            openEnchantments(player);
            return;
        }
        int current = draft.enchants.getOrDefault(enchantment, 0);
        int next = current >= spec.maxLevel() ? 0 : current + 1;
        if (next <= 0) {
            draft.enchants.remove(enchantment);
        } else {
            draft.enchants.put(enchantment, next);
        }
        QuickBuySounds.click(player, plugin);
        openEnchantments(player);
    }

    private void openAmount(Player player) {
        Draft draft = draft(player);
        Material material = draft.material;
        if (material == null || QuickBuyCatalog.isBlacklisted(plugin, material)) {
            openCatalogue(player, draft.slot, draft.query, draft.page);
            return;
        }
        int max = maxStack(material);
        CustomForm.Builder form = CustomForm.builder()
                .title(text("QUICK_BUY.BEDROCK.AMOUNT_TITLE", "How many to buy?"))
                .label(text("QUICK_BUY.BEDROCK.BUTTON_ADD", "Add to Quick Buy") + "\n" + itemName(material))
                .input(text("QUICK_BUY.BEDROCK.AMOUNT_INPUT", "Amount (max {max})",
                        "{max}", String.valueOf(max)), "1", "1");
        int generation = draft.generation;
        form.validResultHandler(response -> {
            draft.generation++;
            String typed = response.asInput(1);
            schedule(player, () -> confirmAmount(player, typed));
        });
        form.closedResultHandler(() -> {
            if (draft.generation != generation) {
                return;
            }
            schedule(player, () -> {
                if (QuickBuyCatalog.isEnchantable(material)) {
                    openEnchantments(player);
                } else {
                    openCatalogue(player, draft.slot, draft.query, draft.page);
                }
            });
        });
        send(player, form.build());
    }

    private void confirmAmount(Player player, String raw) {
        Draft draft = draft(player);
        Material material = draft.material;
        if (material == null || QuickBuyCatalog.isBlacklisted(plugin, material)) {
            QuickBuySounds.fail(player, plugin);
            returnToShop(player);
            return;
        }
        int max = maxStack(material);
        int amount;
        try {
            if (raw == null || raw.isBlank()) {
                throw new NumberFormatException("blank");
            }
            amount = Integer.parseInt(raw.trim());
            if (amount < 1) {
                throw new NumberFormatException("below one");
            }
        } catch (NumberFormatException exception) {
            QuickBuySounds.fail(player, plugin);
            player.sendMessage(ColorUtils.toComponent("&c" + text(
                    "QUICK_BUY.BEDROCK.AMOUNT_INVALID", "Enter a whole number.")));
            openAmount(player);
            return;
        }
        amount = Math.min(amount, max);
        String serialized = QuickBuyEntry.serializeEnchantments(draft.enchants);
        plugin.getShopManager().setQuickBuyEntry(
                player.getUniqueId(),
                new QuickBuyEntry(draft.slot, material, amount, serialized, true));
        drafts.remove(player.getUniqueId());
        QuickBuySounds.play(player, plugin, QuickBuySounds.PIN);
        openMenuLater(player, () -> new QuickBuyMenu(plugin).open(player));
    }

    private void returnToShop(Player player) {
        drafts.remove(player.getUniqueId());
        QuickBuySounds.play(player, plugin, QuickBuySounds.CANCEL);
        openMenuLater(player, () -> new QuickBuyMenu(plugin).open(player));
    }

    private String itemLabel(Player player, Material material) {
        String name = itemName(material);
        WorthManager worth = plugin.getWorthManager();
        if (worth == null || !worth.isWorthLoreEnabled()) {
            return name;
        }
        if (player != null
                && plugin.getPlayerDataManager() != null
                && !worth.isWorthDisplayEnabledFor(player)) {
            return name;
        }
        String lore = worth.getWorthLoreLine(new ItemStack(material));
        if (lore == null || lore.isBlank()) {
            return name;
        }
        String plainLore = ColorUtils.strip(lore).trim();
        if (plainLore.isEmpty()) {
            return name;
        }
        return name + "\n" + plainLore;
    }

    private String enchantLabel(Draft draft, QuickBuyCatalog.EnchantmentSpec spec) {
        Enchantment enchantment = spec.enchantment() != null
                ? spec.enchantment()
                : QuickBuyEntry.findEnchantment(spec.key());
        int level = enchantment == null ? 0 : draft.enchants.getOrDefault(enchantment, 0);
        if (level <= 0) {
            return text("QUICK_BUY.BEDROCK.ENCHANT_OFF", "{name}", "{name}", spec.displayName());
        }
        return text("QUICK_BUY.BEDROCK.ENCHANT_ON", "{name} {level}",
                "{name}", spec.displayName(),
                "{level}", QuickBuyCatalog.toRoman(level));
    }

    private String itemName(Material material) {
        if (material == null) {
            return "";
        }
        WorthManager worth = plugin.getWorthManager();
        String raw = worth == null ? material.name() : worth.prettifyMaterial(material);
        String plain = ColorUtils.strip(raw).trim();
        return plain.isEmpty() ? material.name() : plain;
    }

    private static int maxStack(Material material) {
        try {
            return Math.max(1, material.getMaxStackSize());
        } catch (Throwable ignored) {
            return 64;
        }
    }

    private void bind(Player player, Draft draft, SimpleForm.Builder form, List<Runnable> actions, Runnable onClose) {
        int generation = draft.generation;
        form.validResultHandler(response -> {
            draft.generation++;
            int index = response.clickedButtonId();
            schedule(player, () -> runAction(actions, index));
        });
        form.closedResultHandler(() -> {
            if (draft.generation != generation) {
                return;
            }
            schedule(player, onClose);
        });
    }

    private static void runAction(List<Runnable> actions, int index) {
        if (index >= 0 && index < actions.size()) {
            actions.get(index).run();
        }
    }

    private Draft draft(Player player) {
        return drafts.computeIfAbsent(player.getUniqueId(), ignored -> new Draft());
    }

    private boolean accepts(Player player) {
        if (player == null || !enabled()) {
            return false;
        }
        try {
            return com.bx.smpPlugin.utils.BedrockPlayers.isBedrock(player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean enabled() {
        boolean configured = true;
        if (plugin.getConfigManager() != null && plugin.getConfigManager().getShop() != null) {
            configured = plugin.getConfigManager().getShop().getBoolean("QUICK-BUY.BEDROCK.ENABLED", true);
        }
        return configured && plugin.getServer().getPluginManager().isPluginEnabled("floodgate");
    }

    private int pageSize() {
        int configured = QuickBuyBedrockPages.DEFAULT_PAGE_SIZE;
        if (plugin.getConfigManager() != null && plugin.getConfigManager().getShop() != null) {
            configured = plugin.getConfigManager().getShop().getInt(
                    "QUICK-BUY.BEDROCK.PAGE-SIZE", QuickBuyBedrockPages.DEFAULT_PAGE_SIZE);
        }
        return QuickBuyBedrockPages.pageSize(configured);
    }

    private boolean send(Player player, Form form) {
        try {
            return floodgateSendForm(player, form);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not send Quick Buy Bedrock form to " + player.getName()
                    + ": " + throwable.getMessage());
            return false;
        }
    }

    private void schedule(Player player, Runnable runnable) {
        plugin.getSpigotScheduler().runEntity(player, () -> {
            if (player.isOnline()) {
                runnable.run();
            }
        });
    }

    private void closeMenu(Player player) {
        try {
            if (player.getOpenInventory() == null) {
                return;
            }
            String type = player.getOpenInventory().getType().name();
            if (!"CRAFTING".equals(type) && !"CREATIVE".equals(type)) {
                player.closeInventory();
            }
        } catch (Throwable ignored) {
        }
    }

    private String text(String path, String fallback, String... placeholders) {
        String value = plugin.getLanguageManager().text(path, null, fallback, placeholders);
        return ColorUtils.strip(value == null ? fallback : value);
    }

    private static final class Draft {
        private int slot;
        private String query = "";
        private int page;
        private int generation;
        private boolean formOpen;
        private Material material;
        private final Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
    }

    /**
     * Sends a Cumulus form through Floodgate by reflection, so Floodgate stays an optional runtime
     * integration and the build does not depend on its Maven repository.
     */
    private static boolean floodgateSendForm(Player player, Form form) throws ReflectiveOperationException {
        Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
        Object api = apiClass.getMethod("getInstance").invoke(null);
        for (java.lang.reflect.Method method : apiClass.getMethods()) {
            if (!method.getName().equals("sendForm") || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (!parameters[0].isAssignableFrom(UUID.class) || !parameters[1].isInstance(form)) {
                continue;
            }
            Object result = method.invoke(api, player.getUniqueId(), form);
            return !(result instanceof Boolean sent) || sent;
        }
        return false;
    }
}
