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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ShopGui {
    private static final int PAGE_SIZE = 45;
    private static final int BACK_SLOT = 45;
    private static final int PREVIOUS_SLOT = 48;
    private static final int BALANCE_SLOT = 49;
    private static final int NEXT_SLOT = 50;
    private static final int MODE_SLOT = 53;

    private ShopGui() {}

    public enum Mode {
        BUY,
        SELL;

        public Mode opposite() {
            return this == BUY ? SELL : BUY;
        }
    }

    public static void openCategories(ServerPlayer player, Mode requestedMode) {
        GuiShop.PLAYERS.remember(player);
        Mode mode = resolveAllowedMode(player, requestedMode);
        if (mode == null) {
            ShopMessages.error(player, "You do not have permission to use the shop.");
            return;
        }

        List<MenuButton> buttons = new ArrayList<>();
        for (ShopConfig.Category category : GuiShop.CONFIG.categories) {
            if (!ShopPermissions.category(player, category.id) || !hasAvailableItems(category, mode)) continue;
            String categoryId = category.id;
            Runnable action = isColoredBlocks(category)
                ? () -> openColoredBlockGroups(player, mode, 1)
                : () -> openCategory(player, categoryId, mode, 1);
            buttons.add(new MenuButton(
                displayStack(category.icon, 1, category.name + " | " + modeName(mode)),
                action
            ));
        }

        if (GuiShop.CONFIG.enchantmentsEnabled() && ShopPermissions.user(player, "guishop.enchant")) {
            buttons.add(new MenuButton(
                displayStack("minecraft:enchanted_book", 1, "Enchanted Books"),
                () -> openEnchantments(player, 1)
            ));
        }

        int rows = categoryRows(buttons.size());
        ShopContainer container = new ShopContainer(rows * 9, player);
        int contentRows = Math.max(1, rows - 1);
        int shown = Math.min(contentRows * 7, buttons.size());
        int index = 0;

        for (int row = 0; row < contentRows && index < shown; row++) {
            int remaining = shown - index;
            int inThisRow = Math.min(7, remaining);
            int startColumn = 1 + (7 - inThisRow) / 2;
            for (int columnOffset = 0; columnOffset < inThisRow; columnOffset++) {
                MenuButton button = buttons.get(index++);
                int slot = row * 9 + startColumn + columnOffset;
                container.bind(slot, button.icon(), false, ignored -> button.action().run());
            }
        }

        int bottomRowStart = (rows - 1) * 9;
        int categoryBalanceSlot = bottomRowStart + 4;
        int categoryModeSlot = bottomRowStart + 8;

        container.bind(categoryBalanceSlot,
            displayStack("minecraft:paper", 1, "Balance: " + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))),
            false,
            quantity -> ShopMessages.info(player, "Balance: " + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID())))
        );

        if (ShopService.canUseMode(player, mode.opposite())) {
            container.bind(categoryModeSlot,
                displayStack(mode == Mode.BUY ? "minecraft:gold_ingot" : "minecraft:emerald", 1,
                    "Switch to " + modeName(mode.opposite())),
                false,
                quantity -> openCategories(player, mode.opposite())
            );
        }

        openMenu(player, container, Component.literal("ClassicGUIShop - " + modeName(mode)), rows);
    }

    public static void openCategory(ServerPlayer player, String categoryId, Mode requestedMode, int requestedPage) {
        if ("colored_blocks".equalsIgnoreCase(categoryId)) {
            openColoredBlockGroups(player, requestedMode, requestedPage);
            return;
        }

        Mode mode = resolveAllowedMode(player, requestedMode);
        if (mode == null) {
            ShopMessages.error(player, "You do not have permission to use the shop.");
            return;
        }

        ShopConfig.Category category = GuiShop.CONFIG.category(categoryId);
        if (category == null || !ShopPermissions.category(player, categoryId)) {
            ShopMessages.error(player, "That shop category is unavailable.");
            return;
        }

        List<ShopConfig.ShopItem> available = availableItems(category, mode);
        openItemPage(
            player,
            category.name,
            available,
            mode,
            requestedPage,
            quantity -> openCategories(player, mode),
            targetPage -> openCategory(player, categoryId, mode, targetPage),
            targetMode -> openCategory(player, categoryId, targetMode, 1)
        );
    }

    public static void openColoredBlockGroups(ServerPlayer player, Mode requestedMode, int requestedPage) {
        Mode mode = resolveAllowedMode(player, requestedMode);
        if (mode == null) {
            ShopMessages.error(player, "You do not have permission to use the shop.");
            return;
        }

        ShopConfig.Category category = GuiShop.CONFIG.category("colored_blocks");
        if (category == null || !ShopPermissions.category(player, category.id)) {
            ShopMessages.error(player, "The Colored Blocks category is unavailable.");
            return;
        }

        List<ColoredGroupView> groups = new ArrayList<>();
        for (ColoredBlockGroups.Group group : ColoredBlockGroups.definitions()) {
            int available = countGroupItems(category, group, mode);
            if (available > 0) groups.add(new ColoredGroupView(group, available));
        }

        if (groups.isEmpty()) {
            ShopMessages.warning(player, "No colored blocks are available for " + modeName(mode).toLowerCase() + ".");
            openCategories(player, mode);
            return;
        }

        int pages = Math.max(1, (groups.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(1, Math.min(requestedPage, pages));
        ShopContainer container = new ShopContainer(54, player);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, groups.size());

        for (int slot = 0; start + slot < end; slot++) {
            ColoredGroupView view = groups.get(start + slot);
            ColoredBlockGroups.Group group = view.group();
            container.bind(
                slot,
                displayStack(group.icon(), 1, group.name() + " | " + view.availableItems() + " listings"),
                false,
                ignored -> openColoredBlockGroup(player, mode, group.id(), 1, page)
            );
        }

        container.bind(BACK_SLOT,
            displayStack("minecraft:barrier", 1, "Back to Categories"),
            false,
            ignored -> openCategories(player, mode)
        );

        if (page > 1) {
            int target = page - 1;
            container.bind(PREVIOUS_SLOT,
                displayStack("minecraft:arrow", 1, "Previous Page"),
                false,
                ignored -> openColoredBlockGroups(player, mode, target)
            );
        }

        container.bind(BALANCE_SLOT,
            displayStack("minecraft:paper", 1,
                "Page " + page + "/" + pages + " | Balance " + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))),
            false,
            ignored -> ShopMessages.info(player, "Select a colored block type, then choose the color you want.")
        );

        if (page < pages) {
            int target = page + 1;
            container.bind(NEXT_SLOT,
                displayStack("minecraft:arrow", 1, "Next Page"),
                false,
                ignored -> openColoredBlockGroups(player, mode, target)
            );
        }

        if (ShopService.canUseMode(player, mode.opposite())) {
            container.bind(MODE_SLOT,
                displayStack(mode == Mode.BUY ? "minecraft:gold_ingot" : "minecraft:emerald", 1,
                    "Switch to " + modeName(mode.opposite())),
                false,
                ignored -> openColoredBlockGroups(player, mode.opposite(), 1)
            );
        }

        openMenu(player, container, Component.literal("Colored Blocks - " + modeName(mode) + " " + page + "/" + pages), 6);
    }

    private static void openColoredBlockGroup(
        ServerPlayer player,
        Mode requestedMode,
        String groupId,
        int requestedPage,
        int groupMenuPage
    ) {
        Mode mode = resolveAllowedMode(player, requestedMode);
        if (mode == null) {
            ShopMessages.error(player, "You do not have permission to use the shop.");
            return;
        }

        ShopConfig.Category category = GuiShop.CONFIG.category("colored_blocks");
        if (category == null || !ShopPermissions.category(player, category.id)) {
            ShopMessages.error(player, "The Colored Blocks category is unavailable.");
            return;
        }

        ColoredBlockGroups.Group group = ColoredBlockGroups.byId(groupId);
        List<ShopConfig.ShopItem> available = new ArrayList<>();
        for (ShopConfig.ShopItem item : category.items) {
            if (ColoredBlockGroups.groupFor(item).id().equalsIgnoreCase(group.id()) && price(item, mode) > 0) {
                available.add(item);
            }
        }

        if (available.isEmpty()) {
            ShopMessages.warning(player, group.name() + " has no listings available for " + modeName(mode).toLowerCase() + ".");
            openColoredBlockGroups(player, mode, groupMenuPage);
            return;
        }

        openItemPage(
            player,
            group.name(),
            available,
            mode,
            requestedPage,
            quantity -> openColoredBlockGroups(player, mode, groupMenuPage),
            targetPage -> openColoredBlockGroup(player, mode, group.id(), targetPage, groupMenuPage),
            targetMode -> {
                if (countGroupItems(category, group, targetMode) > 0) {
                    openColoredBlockGroup(player, targetMode, group.id(), 1, groupMenuPage);
                } else {
                    openColoredBlockGroups(player, targetMode, 1);
                }
            }
        );
    }

    private static void openItemPage(
        ServerPlayer player,
        String title,
        List<ShopConfig.ShopItem> available,
        Mode mode,
        int requestedPage,
        GuiAction backAction,
        PageAction pageAction,
        ModeAction modeAction
    ) {
        int pages = Math.max(1, (available.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(1, Math.min(requestedPage, pages));
        ShopContainer container = new ShopContainer(54, player);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, available.size());

        for (int slot = 0; start + slot < end; slot++) {
            ShopConfig.ShopItem entry = available.get(start + slot);
            double unitPrice = price(entry, mode) * GuiShop.CONFIG.priceMultiplier;
            String label = entry.name + " | " + modeName(mode) + " " + GuiShop.CONFIG.money(unitPrice) + " each";
            ItemStack icon = displayListingStack(entry, player, label);
            container.bind(slot, icon, true, quantity -> {
                int requested = mode == Mode.SELL && quantity == 64 ? ShopService.SELL_ALL : quantity;
                ShopService.trade(player, entry, mode, requested);
            });
        }

        container.bind(BACK_SLOT,
            displayStack("minecraft:barrier", 1, "Back"),
            false,
            backAction
        );

        if (page > 1) {
            int target = page - 1;
            container.bind(PREVIOUS_SLOT,
                displayStack("minecraft:arrow", 1, "Previous Page"),
                false,
                ignored -> pageAction.open(target)
            );
        }

        container.bind(BALANCE_SLOT,
            displayStack("minecraft:paper", 1,
                "Page " + page + "/" + pages + " | Balance " + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))),
            false,
            ignored -> ShopMessages.info(player,
                "Page " + page + " of " + pages + ". Balance: " + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID())))
        );

        if (page < pages) {
            int target = page + 1;
            container.bind(NEXT_SLOT,
                displayStack("minecraft:arrow", 1, "Next Page"),
                false,
                ignored -> pageAction.open(target)
            );
        }

        if (ShopService.canUseMode(player, mode.opposite())) {
            container.bind(MODE_SLOT,
                displayStack(mode == Mode.BUY ? "minecraft:gold_ingot" : "minecraft:emerald", 1,
                    "Switch to " + modeName(mode.opposite())),
                false,
                ignored -> modeAction.open(mode.opposite())
            );
        }

        openMenu(player, container, Component.literal(title + " - " + modeName(mode) + " " + page + "/" + pages), 6);
    }

    public static void openEnchantments(ServerPlayer player, int requestedPage) {
        if (!GuiShop.CONFIG.enchantmentsEnabled() || !ShopPermissions.user(player, "guishop.enchant")) {
            ShopMessages.warning(player, "The enchanted book shop is unavailable.");
            return;
        }

        List<EnchantmentShopService.EnchantmentView> enchantments = EnchantmentShopService.availableEnchantments(player);
        if (enchantments.isEmpty()) {
            ShopMessages.warning(player, "No enchanted books are currently available.");
            return;
        }

        int pages = Math.max(1, (enchantments.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(1, Math.min(requestedPage, pages));
        ShopContainer container = new ShopContainer(54, player);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, enchantments.size());

        for (int slot = 0; start + slot < end; slot++) {
            EnchantmentShopService.EnchantmentView enchantment = enchantments.get(start + slot);
            ItemStack icon = EnchantmentShopService.createBook(enchantment.holder(), 1);
            String priceRange = enchantment.maxLevel() == 1
                ? GuiShop.CONFIG.money(enchantment.firstLevelCost())
                : GuiShop.CONFIG.money(enchantment.firstLevelCost()) + " - " + GuiShop.CONFIG.money(enchantment.maximumLevelCost());
            icon.set(DataComponents.CUSTOM_NAME, Component.literal(
                enchantment.displayName() + " | Levels 1-" + enchantment.maxLevel() + " | " + priceRange
            ));
            container.bind(slot, icon, false,
                ignored -> openEnchantmentLevels(player, enchantment.enchantmentId(), 1, page));
        }

        container.bind(BACK_SLOT,
            displayStack("minecraft:barrier", 1, "Back to Shop"),
            false,
            ignored -> openCategories(player, Mode.BUY)
        );

        if (page > 1) {
            int target = page - 1;
            container.bind(PREVIOUS_SLOT,
                displayStack("minecraft:arrow", 1, "Previous Page"),
                false,
                ignored -> openEnchantments(player, target)
            );
        }

        container.bind(BALANCE_SLOT,
            displayStack("minecraft:paper", 1,
                "Page " + page + "/" + pages + " | Balance " + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))),
            false,
            ignored -> ShopMessages.info(player, "Select an enchantment, then choose the book level you want.")
        );

        if (page < pages) {
            int target = page + 1;
            container.bind(NEXT_SLOT,
                displayStack("minecraft:arrow", 1, "Next Page"),
                false,
                ignored -> openEnchantments(player, target)
            );
        }

        openMenu(player, container, Component.literal("Enchanted Books " + page + "/" + pages), 6);
    }

    private static void openEnchantmentLevels(ServerPlayer player, String enchantmentId, int requestedPage, int categoryPage) {
        List<EnchantmentShopService.OfferView> offers = EnchantmentShopService.availableLevels(player, enchantmentId);
        if (offers.isEmpty()) {
            ShopMessages.warning(player, "That enchanted book is no longer available.");
            openEnchantments(player, categoryPage);
            return;
        }

        int pages = Math.max(1, (offers.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(1, Math.min(requestedPage, pages));
        ShopContainer container = new ShopContainer(54, player);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, offers.size());

        for (int slot = 0; start + slot < end; slot++) {
            EnchantmentShopService.OfferView offer = offers.get(start + slot);
            String label = offer.displayName() + " " + EnchantmentShopService.roman(offer.targetLevel())
                + " | " + GuiShop.CONFIG.money(offer.cost());
            ItemStack icon = EnchantmentShopService.createBook(offer.holder(), offer.targetLevel());
            icon.set(DataComponents.CUSTOM_NAME, Component.literal(label));
            container.bind(slot, icon, false, ignored -> {
                if (EnchantmentShopService.purchase(player, offer.enchantmentId(), offer.targetLevel())) {
                    openEnchantmentLevels(player, enchantmentId, page, categoryPage);
                }
            });
        }

        container.bind(BACK_SLOT,
            displayStack("minecraft:barrier", 1, "Back to Enchantments"),
            false,
            ignored -> openEnchantments(player, categoryPage)
        );

        if (page > 1) {
            int target = page - 1;
            container.bind(PREVIOUS_SLOT,
                displayStack("minecraft:arrow", 1, "Previous Page"),
                false,
                ignored -> openEnchantmentLevels(player, enchantmentId, target, categoryPage)
            );
        }

        EnchantmentShopService.OfferView first = offers.get(0);
        container.bind(BALANCE_SLOT,
            displayStack("minecraft:paper", 1,
                first.displayName() + " | Balance " + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))),
            false,
            ignored -> ShopMessages.info(player, "Choose which " + first.displayName() + " level you want to purchase.")
        );

        if (page < pages) {
            int target = page + 1;
            container.bind(NEXT_SLOT,
                displayStack("minecraft:arrow", 1, "Next Page"),
                false,
                ignored -> openEnchantmentLevels(player, enchantmentId, target, categoryPage)
            );
        }

        openMenu(player, container, Component.literal(first.displayName() + " Levels"), 6);
    }

    private static Mode resolveAllowedMode(ServerPlayer player, Mode requested) {
        if (ShopService.canUseMode(player, requested)) return requested;
        if (ShopService.canUseMode(player, requested.opposite())) return requested.opposite();
        return null;
    }

    private static void openMenu(ServerPlayer player, ShopContainer container, Component title, int rows) {
        player.openMenu(new SimpleMenuProvider(
            (containerId, inventory, ignored) -> new ShopMenu(containerId, inventory, container, rows),
            title
        ));
    }

    private static int categoryRows(int categoryCount) {
        if (categoryCount <= 7) return 2;
        if (categoryCount <= 14) return 3;
        if (categoryCount <= 21) return 4;
        if (categoryCount <= 28) return 5;
        return 6;
    }

    private static MenuType<?> menuTypeForRows(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    private static List<ShopConfig.ShopItem> availableItems(ShopConfig.Category category, Mode mode) {
        List<ShopConfig.ShopItem> available = new ArrayList<>();
        for (ShopConfig.ShopItem entry : category.items) {
            if (price(entry, mode) > 0) available.add(entry);
        }
        return available;
    }

    private static boolean hasAvailableItems(ShopConfig.Category category, Mode mode) {
        for (ShopConfig.ShopItem item : category.items) {
            if (price(item, mode) > 0) return true;
        }
        return false;
    }

    private static int countGroupItems(ShopConfig.Category category, ColoredBlockGroups.Group group, Mode mode) {
        int count = 0;
        for (ShopConfig.ShopItem item : category.items) {
            if (ColoredBlockGroups.groupFor(item).id().equalsIgnoreCase(group.id()) && price(item, mode) > 0) count++;
        }
        return count;
    }

    private static boolean isColoredBlocks(ShopConfig.Category category) {
        return category != null && "colored_blocks".equalsIgnoreCase(category.id);
    }

    private static double price(ShopConfig.ShopItem item, Mode mode) {
        return mode == Mode.BUY ? item.buy : item.sell;
    }

    private static String modeName(Mode mode) {
        return mode == Mode.BUY ? "Buy" : "Sell";
    }

    private static ItemStack displayListingStack(ShopConfig.ShopItem entry, ServerPlayer player, String label) {
        ItemStack stack = entry.createStack(player.registryAccess());
        if (stack.isEmpty()) stack = new ItemStack(Items.BARRIER);
        else stack = stack.copyWithCount(1);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(label));
        return stack;
    }

    private static ItemStack displayStack(String identifier, int count, String name) {
        Item item = resolveItem(identifier);
        if (item == Items.AIR && !identifier.equals("minecraft:air")) item = Items.BARRIER;
        ItemStack stack = new ItemStack(item, Math.max(1, count));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static Item resolveItem(String identifier) {
        try {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(identifier));
            return item == null ? Items.AIR : item;
        } catch (Exception ignored) {
            return Items.AIR;
        }
    }

    @FunctionalInterface
    private interface GuiAction {
        void run(int quantity);
    }

    @FunctionalInterface
    private interface PageAction {
        void open(int page);
    }

    @FunctionalInterface
    private interface ModeAction {
        void open(Mode mode);
    }

    private record MenuButton(ItemStack icon, Runnable action) {}
    private record Binding(GuiAction action, boolean trade) {}
    private record ColoredGroupView(ColoredBlockGroups.Group group, int availableItems) {}

    private static final class ShopContainer extends SimpleContainer {
        private final Map<Integer, Binding> bindings = new HashMap<>();
        private final ServerPlayer owner;
        private boolean processing;

        ShopContainer(int size, ServerPlayer owner) {
            super(size);
            this.owner = owner;
        }

        void bind(int slot, ItemStack stack, boolean trade, GuiAction action) {
            super.setItem(slot, stack.copy());
            bindings.put(slot, new Binding(action, trade));
        }

        void activate(int slot, int quantity, boolean shiftClick) {
            Binding binding = bindings.get(slot);
            if (binding == null || processing) return;
            processing = true;
            try {
                if (!binding.trade()) binding.action().run(1);
                else if (shiftClick) binding.action().run(64);
                else if (quantity >= 16) binding.action().run(16);
                else binding.action().run(1);
            } finally {
                processing = false;
            }
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
            // Read-only server menu.
        }

        @Override
        public boolean stillValid(Player player) {
            return player == owner && owner.isAlive();
        }
    }

    private static final class ShopMenu extends ChestMenu {
        private final ShopContainer shop;

        ShopMenu(int containerId, Inventory inventory, ShopContainer shop, int rows) {
            super(menuTypeForRows(rows), containerId, inventory, shop, rows);
            this.shop = shop;
        }

        @Override
        public void clicked(int slotIndex, int buttonNum, ContainerInput containerInput, Player player) {
            if (slotIndex >= 0 && slotIndex < shop.getContainerSize()) {
                if (containerInput == ContainerInput.PICKUP) {
                    shop.activate(slotIndex, buttonNum == 1 ? 16 : 1, false);
                } else if (containerInput == ContainerInput.QUICK_MOVE) {
                    shop.activate(slotIndex, 64, true);
                }
                return;
            }
            super.clicked(slotIndex, buttonNum, containerInput, player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            if (index >= 0 && index < shop.getContainerSize()) shop.activate(index, 64, true);
            return ItemStack.EMPTY;
        }
    }
}
