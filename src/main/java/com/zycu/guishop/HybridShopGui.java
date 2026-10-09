package com.zycu.guishop;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HybridShopGui {
    private static final int PAGE_SIZE = 45;
    private static final int BACK_SLOT = 45;
    private static final int PREVIOUS_SLOT = 48;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 50;
    private static final int MODE_SLOT = 53;

    private HybridShopGui() {}

    public static void openCategories(ServerPlayer player, ShopGui.Mode requestedMode) {
        openCategories(player, requestedMode, 1);
    }

    private static void openCategories(ServerPlayer player, ShopGui.Mode requestedMode, int requestedPage) {
        GuiShop.PLAYERS.remember(player);
        ShopGui.Mode mode = resolveAllowedMode(player, requestedMode);
        if (mode == null) return;
        EnchantmentShopService.rememberMode(player, mode);

        List<MenuButton> buttons = new ArrayList<>();
        for (ShopConfig.Category category : GuiShop.CONFIG.categories) {
            if (!ShopPermissions.category(player, category.id) || !hasAvailableItems(category, mode)) continue;
            buttons.add(new MenuButton(
                ShopIcons.category(category, player.registryAccess(), category.name + " | " + modeName(mode)),
                () -> openCategory(player, category.id, mode, 1)
            ));
        }
        if (GuiShop.CONFIG.enchantmentsEnabled() && ShopPermissions.user(player, "guishop.enchant")) {
            buttons.add(new MenuButton(
                displayStack("minecraft:enchanted_book", "Enchanted Books"),
                () -> openEnchantments(player, mode, 1)
            ));
        }

        int pages = Math.max(1, (buttons.size() + CategoryLayout.PAGE_SIZE - 1) / CategoryLayout.PAGE_SIZE);
        int page = Math.max(1, Math.min(requestedPage, pages));
        int offset = (page - 1) * CategoryLayout.PAGE_SIZE;
        int shown = Math.min(CategoryLayout.PAGE_SIZE, buttons.size() - offset);
        int rows = CategoryLayout.rows(shown);
        ShopContainer container = new ShopContainer(rows * 9, player);
        container.beginRender();
        List<Integer> slots = CategoryLayout.slots(shown);
        for (int index = 0; index < shown; index++) {
            MenuButton button = buttons.get(offset + index);
            container.bind(slots.get(index), button.icon, false, ignored -> button.action.run());
        }

        int bottom = (rows - 1) * 9;
        if (page > 1) container.bind(bottom,
            displayStack("minecraft:arrow", "Previous category page"), false,
            ignored -> openCategories(player, mode, page - 1));
        if (page < pages) container.bind(bottom + 7,
            displayStack("minecraft:arrow", "Next category page"), false,
            ignored -> openCategories(player, mode, page + 1));
        container.bind(bottom + 4,
            displayStack("minecraft:paper", "Balance: " + money(player)),
            false,
            ignored -> ShopMessages.info(player, "Balance: " + money(player))
        );
        if (ShopService.canUseMode(player, mode.opposite())) {
            container.bind(bottom + 8,
                displayStack(mode == ShopGui.Mode.BUY ? "minecraft:gold_ingot" : "minecraft:emerald",
                    "Switch to " + modeName(mode.opposite())),
                false,
                ignored -> openCategories(player, mode.opposite())
            );
        }
        container.endRender();
        openMenu(player, container, Component.literal(GuiShop.CONFIG.shopTitle + " - " + modeName(mode)), rows);
    }

    public static void openCategory(
        ServerPlayer player,
        String categoryId,
        ShopGui.Mode requestedMode,
        int requestedPage
    ) {
        ShopConfig.Category category = GuiShop.CONFIG.category(categoryId);
        if (category == null || !ShopPermissions.category(player, categoryId)) {
            ShopMessages.error(player, "That shop category is unavailable.");
            return;
        }

        ShopGui.Mode mode = resolveAllowedMode(player, requestedMode);
        if (mode == null) return;
        EnchantmentShopService.rememberMode(player, mode);
        openItemScreen(
            player,
            category.name,
            availableItems(category, mode),
            mode,
            requestedPage,
            ignored -> openCategories(player, mode),
            targetMode -> openCategory(player, category.id, targetMode, 1)
        );
    }

    private static void openItemScreen(
        ServerPlayer player,
        String title,
        List<ShopConfig.ShopItem> items,
        ShopGui.Mode mode,
        int requestedPage,
        GuiAction backAction,
        ModeAction modeAction
    ) {
        EnchantmentShopService.rememberMode(player, mode);
        PageSession session = new PageSession(
            player,
            Component.literal(title + " - " + modeName(mode)),
            (screen, requested) -> {
                int pages = pageCount(items.size());
                int page = clampPage(requested, pages);
                int start = (page - 1) * PAGE_SIZE;
                int end = Math.min(start + PAGE_SIZE, items.size());

                screen.begin();
                for (int slot = 0; start + slot < end; slot++) {
                    ShopConfig.ShopItem entry = items.get(start + slot);
                    double unitPrice = price(entry, mode) * GuiShop.CONFIG.priceMultiplier;
                    ItemStack icon = entry.createStack(player.registryAccess());
                    if (icon.isEmpty()) icon = new ItemStack(Items.BARRIER);
                    else icon = icon.copyWithCount(1);
                    icon.set(DataComponents.CUSTOM_NAME,
                        Component.literal(entry.name + " | " + modeName(mode) + " "
                            + GuiShop.CONFIG.money(unitPrice) + " each"));
                    description(icon, entry.description);

                    screen.bind(slot, icon, true, quantity -> {
                        int requestedAmount = mode == ShopGui.Mode.SELL && quantity == 64
                            ? ShopService.SELL_ALL
                            : quantity;
                        if (ShopService.trade(player, entry, mode, requestedAmount)) screen.show(page);
                    });
                }
                screen.bind(BACK_SLOT, displayStack("minecraft:barrier", "Back"), false, backAction);
                screen.pageControls(page, pages,
                    "Page " + page + "/" + pages + " | Balance " + money(player),
                    target -> screen.show(target));
                if (ShopService.canUseMode(player, mode.opposite())) {
                    screen.bind(MODE_SLOT,
                        displayStack(mode == ShopGui.Mode.BUY ? "minecraft:gold_ingot" : "minecraft:emerald",
                            "Switch to " + modeName(mode.opposite())),
                        false,
                        ignored -> modeAction.open(mode.opposite())
                    );
                }
                screen.finish();
            }
        );
        session.open(requestedPage);
    }

    public static void openEnchantments(ServerPlayer player, int requestedPage) {
        openEnchantments(player, ShopGui.Mode.BUY, requestedPage);
    }

    public static void openEnchantments(
        ServerPlayer player,
        ShopGui.Mode requestedMode,
        int requestedPage
    ) {
        if (!GuiShop.CONFIG.enchantmentsEnabled() || !ShopPermissions.user(player, "guishop.enchant")) {
            ShopMessages.warning(player, "The enchanted book shop is unavailable.");
            return;
        }

        ShopGui.Mode mode = resolveAllowedMode(player, requestedMode);
        if (mode == null) return;
        EnchantmentShopService.rememberMode(player, mode);

        PageSession session = new PageSession(
            player,
            Component.literal("Enchanted Books - " + modeName(mode)),
            (screen, requested) -> {
                List<EnchantmentShopService.EnchantmentView> enchantments =
                    EnchantmentShopService.availableEnchantments(player, mode);
                if (enchantments.isEmpty()) {
                    ShopMessages.warning(player, "No enchanted books are currently available for "
                        + modeName(mode).toLowerCase() + ".");
                    return;
                }

                int pages = pageCount(enchantments.size());
                int page = clampPage(requested, pages);
                int start = (page - 1) * PAGE_SIZE;
                int end = Math.min(start + PAGE_SIZE, enchantments.size());

                screen.begin();
                for (int slot = 0; start + slot < end; slot++) {
                    EnchantmentShopService.EnchantmentView enchantment = enchantments.get(start + slot);
                    ItemStack icon = EnchantmentShopService.createBook(enchantment.holder(), 1);
                    String range = enchantment.maxLevel() == 1
                        ? GuiShop.CONFIG.money(enchantment.firstLevelCost())
                        : GuiShop.CONFIG.money(enchantment.firstLevelCost()) + " - "
                            + GuiShop.CONFIG.money(enchantment.maximumLevelCost());
                    icon.set(DataComponents.CUSTOM_NAME,
                        Component.literal(enchantment.displayName() + " | " + modeName(mode) + " levels 1-"
                            + enchantment.maxLevel() + " | " + range));
                    screen.bind(slot, icon, false,
                        ignored -> openEnchantmentLevels(player, mode, enchantment.enchantmentId(), 1, page));
                }
                screen.bind(BACK_SLOT, displayStack("minecraft:barrier", "Back to Shop"), false,
                    ignored -> openCategories(player, mode));
                screen.pageControls(page, pages,
                    modeName(mode) + " | Page " + page + "/" + pages + " | Balance " + money(player),
                    target -> screen.show(target));
                if (ShopService.canUseMode(player, mode.opposite())) {
                    screen.bind(MODE_SLOT,
                        displayStack(mode == ShopGui.Mode.BUY ? "minecraft:gold_ingot" : "minecraft:emerald",
                            "Switch to " + modeName(mode.opposite())),
                        false,
                        ignored -> openEnchantments(player, mode.opposite(), 1)
                    );
                }
                screen.finish();
            }
        );
        session.open(requestedPage);
    }

    private static void openEnchantmentLevels(
        ServerPlayer player,
        ShopGui.Mode mode,
        String enchantmentId,
        int requestedPage,
        int parentPage
    ) {
        EnchantmentShopService.rememberMode(player, mode);
        List<EnchantmentShopService.OfferView> initial =
            EnchantmentShopService.availableLevels(player, enchantmentId, mode);
        if (initial.isEmpty()) {
            openEnchantments(player, mode, parentPage);
            return;
        }
        String title = initial.get(0).displayName() + " Levels - " + modeName(mode);

        PageSession session = new PageSession(
            player,
            Component.literal(title),
            (screen, requested) -> {
                List<EnchantmentShopService.OfferView> offers =
                    EnchantmentShopService.availableLevels(player, enchantmentId, mode);
                if (offers.isEmpty()) {
                    openEnchantments(player, mode, parentPage);
                    return;
                }

                int pages = pageCount(offers.size());
                int page = clampPage(requested, pages);
                int start = (page - 1) * PAGE_SIZE;
                int end = Math.min(start + PAGE_SIZE, offers.size());

                screen.begin();
                for (int slot = 0; start + slot < end; slot++) {
                    EnchantmentShopService.OfferView offer = offers.get(start + slot);
                    ItemStack icon = EnchantmentShopService.createBook(offer.holder(), offer.targetLevel());
                    icon.set(DataComponents.CUSTOM_NAME,
                        Component.literal(offer.displayName() + " "
                            + EnchantmentShopService.roman(offer.targetLevel()) + " | "
                            + modeName(mode) + " " + GuiShop.CONFIG.money(offer.cost())));
                    screen.bind(slot, icon, mode == ShopGui.Mode.SELL, quantity -> {
                        boolean success;
                        if (mode == ShopGui.Mode.SELL) {
                            int requestedAmount = quantity == 64 ? ShopService.SELL_ALL : 1;
                            success = EnchantmentShopService.sell(
                                player,
                                offer.enchantmentId(),
                                offer.targetLevel(),
                                requestedAmount
                            );
                        } else {
                            success = EnchantmentShopService.purchase(
                                player,
                                offer.enchantmentId(),
                                offer.targetLevel()
                            );
                        }
                        if (success) screen.show(page);
                    });
                }
                screen.bind(BACK_SLOT, displayStack("minecraft:barrier", "Back to Enchantments"), false,
                    ignored -> openEnchantments(player, mode, parentPage));
                screen.pageControls(page, pages,
                    modeName(mode) + " | Page " + page + "/" + pages + " | Balance " + money(player),
                    target -> screen.show(target));
                if (ShopService.canUseMode(player, mode.opposite())) {
                    screen.bind(MODE_SLOT,
                        displayStack(mode == ShopGui.Mode.BUY ? "minecraft:gold_ingot" : "minecraft:emerald",
                            "Switch to " + modeName(mode.opposite())),
                        false,
                        ignored -> openEnchantmentLevels(player, mode.opposite(), enchantmentId, 1, parentPage)
                    );
                }
                screen.finish();
            }
        );
        session.open(requestedPage);
    }

    private static List<ShopConfig.ShopItem> availableItems(
        ShopConfig.Category category,
        ShopGui.Mode mode
    ) {
        List<ShopConfig.ShopItem> items = new ArrayList<>();
        for (ShopConfig.ShopItem item : category.items) {
            if (price(item, mode) > 0) items.add(item);
        }
        return items;
    }

    private static boolean hasAvailableItems(ShopConfig.Category category, ShopGui.Mode mode) {
        for (ShopConfig.ShopItem item : category.items) {
            if (price(item, mode) > 0) return true;
        }
        return false;
    }

    private static ShopGui.Mode resolveAllowedMode(ServerPlayer player, ShopGui.Mode requested) {
        if (ShopService.canUseMode(player, requested)) return requested;
        if (ShopService.canUseMode(player, requested.opposite())) return requested.opposite();
        ShopMessages.error(player, "You do not have permission to use the shop.");
        return null;
    }

    private static double price(ShopConfig.ShopItem item, ShopGui.Mode mode) {
        return mode == ShopGui.Mode.BUY ? item.buy : item.sell;
    }

    private static String modeName(ShopGui.Mode mode) {
        return mode == ShopGui.Mode.BUY ? "Buy" : "Sell";
    }

    private static String money(ServerPlayer player) {
        return GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()));
    }

    private static int pageCount(int itemCount) {
        return Math.max(1, (itemCount + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static int clampPage(int requested, int pages) {
        return Math.max(1, Math.min(requested, pages));
    }

    private static int categoryRows(int count) {
        if (count <= 7) return 2;
        if (count <= 14) return 3;
        if (count <= 21) return 4;
        if (count <= 28) return 5;
        return 6;
    }

    private static ItemStack displayStack(String identifier, String name) {
        return displayStack(identifier, name, "");
    }

    private static ItemStack displayStack(String identifier, String name, String detail) {
        Item item;
        try {
            item = BuiltInRegistries.ITEM.getValue(Identifier.parse(identifier));
            if (item == null || item == Items.AIR) item = Items.BARRIER;
        } catch (Exception ignored) {
            item = Items.BARRIER;
        }
        return ShopIcons.display(new ItemStack(item), name, detail);
    }

    private static void description(ItemStack icon, String detail) {
        if (detail == null || detail.isBlank()) return;
        var lore = icon.getOrDefault(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of()));
        if (lore.lines().size() < net.minecraft.world.item.component.ItemLore.MAX_LINES) {
            icon.set(DataComponents.LORE, lore.withLineAdded(Component.literal(detail).withStyle(net.minecraft.ChatFormatting.GRAY)));
        }
    }

    private static Component shopTitle(Component title) {
        return Component.literal(title.getString())
            .withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.BOLD);
    }

    private static void openMenu(
        ServerPlayer player,
        ShopContainer container,
        Component title,
        int rows
    ) {
        player.openMenu(new SimpleMenuProvider(
            (containerId, inventory, ignored) -> {
                ShopMenu menu = new ShopMenu(containerId, inventory, container, rows);
                container.attach(menu);
                return menu;
            },
            shopTitle(title)
        ));
    }

    private static MenuType<?> menuType(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    @FunctionalInterface
    private interface GuiAction {
        void run(int quantity);
    }

    @FunctionalInterface
    private interface ModeAction {
        void open(ShopGui.Mode mode);
    }

    @FunctionalInterface
    private interface PageAction {
        void open(int page);
    }

    @FunctionalInterface
    private interface PageRenderer {
        void render(PageSession screen, int requestedPage);
    }

    private record MenuButton(ItemStack icon, Runnable action) {}
    private record Binding(GuiAction action, boolean trade) {}

    private static final class PageSession {
        private final ServerPlayer player;
        private final Component title;
        private final PageRenderer renderer;
        private final ShopContainer container;

        private PageSession(ServerPlayer player, Component title, PageRenderer renderer) {
            this.player = player;
            this.title = title;
            this.renderer = renderer;
            this.container = new ShopContainer(54, player);
        }

        private void open(int page) {
            show(page);
            openMenu(player, container, title, 6);
        }

        private void show(int page) {
            renderer.render(this, page);
        }

        private void begin() {
            container.beginRender();
        }

        private void bind(int slot, ItemStack stack, boolean trade, GuiAction action) {
            container.bind(slot, stack, trade, action);
        }

        private void pageControls(
            int page,
            int pages,
            String info,
            PageAction pageAction
        ) {
            if (page > 1) {
                int target = page - 1;
                bind(PREVIOUS_SLOT, displayStack("minecraft:arrow", "Previous Page"), false,
                    ignored -> pageAction.open(target));
            }
            bind(INFO_SLOT, displayStack("minecraft:paper", info), false,
                ignored -> ShopMessages.info(player, info));
            if (page < pages) {
                int target = page + 1;
                bind(NEXT_SLOT, displayStack("minecraft:arrow", "Next Page"), false,
                    ignored -> pageAction.open(target));
            }
        }

        private void finish() {
            container.endRender();
        }
    }

    private static final class ShopContainer extends SimpleContainer {
        private final Map<Integer, Binding> bindings = new HashMap<>();
        private final Map<Integer, Binding> pendingBindings = new HashMap<>();
        private final ItemStack[] pending;
        private final ServerPlayer owner;
        private ShopMenu menu;
        private boolean processing;

        private ShopContainer(int size, ServerPlayer owner) {
            super(size);
            this.owner = owner;
            this.pending = new ItemStack[size];
            Arrays.fill(this.pending, ItemStack.EMPTY);
        }

        private void attach(ShopMenu menu) {
            this.menu = menu;
            menu.broadcastChanges();
        }

        private void beginRender() {
            pendingBindings.clear();
            Arrays.fill(pending, ItemStack.EMPTY);
        }

        private void bind(int slot, ItemStack stack, boolean trade, GuiAction action) {
            pending[slot] = stack.copy();
            pendingBindings.put(slot, new Binding(action, trade));
        }

        private void endRender() {
            for (int slot = 0; slot < getContainerSize(); slot++) {
                ItemStack current = super.getItem(slot);
                ItemStack replacement = pending[slot];
                if (!sameStack(current, replacement)) super.setItem(slot, replacement.copy());
            }
            bindings.clear();
            bindings.putAll(pendingBindings);
            setChanged();
            if (menu != null) menu.broadcastChanges();
        }

        private void activate(int slot, int quantity, boolean shift) {
            Binding binding = bindings.get(slot);
            if (binding == null || processing) return;
            processing = true;
            try {
                if (!binding.trade) binding.action.run(1);
                else if (shift) binding.action.run(64);
                else if (quantity >= 16) binding.action.run(16);
                else binding.action.run(1);
            } finally {
                processing = false;
            }
        }

        private static boolean sameStack(ItemStack first, ItemStack second) {
            if (first.isEmpty() && second.isEmpty()) return true;
            return first.getCount() == second.getCount()
                && ItemStack.isSameItemSameComponents(first, second);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
        }

        @Override
        public boolean stillValid(Player player) {
            return player == owner && owner.isAlive();
        }
    }

    private static final class ShopMenu extends ChestMenu {
        private final ShopContainer shop;

        private ShopMenu(
            int containerId,
            Inventory inventory,
            ShopContainer shop,
            int rows
        ) {
            super(menuType(rows), containerId, inventory, shop, rows);
            this.shop = shop;
        }

        @Override
        public void clicked(
            int slotIndex,
            int buttonNum,
            ContainerInput input,
            Player player
        ) {
            if (slotIndex >= 0 && slotIndex < shop.getContainerSize()) {
                if (input == ContainerInput.PICKUP) {
                    shop.activate(slotIndex, buttonNum == 1 ? 16 : 1, false);
                } else if (input == ContainerInput.QUICK_MOVE) {
                    shop.activate(slotIndex, 64, true);
                }
                return;
            }
            super.clicked(slotIndex, buttonNum, input, player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            if (index >= 0 && index < shop.getContainerSize()) {
                shop.activate(index, 64, true);
            }
            return ItemStack.EMPTY;
        }
    }
}
