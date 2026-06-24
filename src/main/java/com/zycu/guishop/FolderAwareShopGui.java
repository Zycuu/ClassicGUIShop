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

public final class FolderAwareShopGui {
    private static final int PAGE_SIZE = 45;
    private static final int BACK_SLOT = 45;
    private static final int PREVIOUS_SLOT = 48;
    private static final int BALANCE_SLOT = 49;
    private static final int NEXT_SLOT = 50;
    private static final int MODE_SLOT = 53;

    private FolderAwareShopGui() {}

    public static void openCategories(ServerPlayer player, ShopGui.Mode requestedMode) {
        GuiShop.PLAYERS.remember(player);
        ShopGui.Mode mode = resolveAllowedMode(player, requestedMode);
        if (mode == null) {
            ShopMessages.error(player, "You do not have permission to use the shop.");
            return;
        }

        List<MenuButton> buttons = new ArrayList<>();
        for (ShopConfig.Category category : GuiShop.CONFIG.categories) {
            if (!ShopPermissions.category(player, category.id) || !hasAvailableItems(category, mode)) continue;
            buttons.add(new MenuButton(
                displayStack(category.icon, category.name + " | " + modeName(mode)),
                () -> openCategory(player, category.id, mode, 1)
            ));
        }

        if (GuiShop.CONFIG.enchantmentsEnabled() && ShopPermissions.user(player, "guishop.enchant")) {
            buttons.add(new MenuButton(
                displayStack("minecraft:enchanted_book", "Enchanted Books"),
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
            for (int offset = 0; offset < inThisRow; offset++) {
                MenuButton button = buttons.get(index++);
                container.bind(row * 9 + startColumn + offset, button.icon, false, ignored -> button.action.run());
            }
        }

        int bottom = (rows - 1) * 9;
        container.bind(bottom + 4,
            displayStack("minecraft:paper", "Balance: " + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))),
            false,
            ignored -> ShopMessages.info(player, "Balance: " + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID())))
        );
        if (ShopService.canUseMode(player, mode.opposite())) {
            container.bind(bottom + 8,
                displayStack(mode == ShopGui.Mode.BUY ? "minecraft:gold_ingot" : "minecraft:emerald",
                    "Switch to " + modeName(mode.opposite())),
                false,
                ignored -> openCategories(player, mode.opposite())
            );
        }

        openMenu(player, container, Component.literal("ClassicGUIShop - " + modeName(mode)), rows);
    }

    public static void openCategory(ServerPlayer player, String categoryId, ShopGui.Mode requestedMode, int requestedPage) {
        ShopConfig.Category category = GuiShop.CONFIG.category(categoryId);
        if (category == null || !ShopPermissions.category(player, categoryId)) {
            ShopMessages.error(player, "That shop category is unavailable.");
            return;
        }

        if ("colored_blocks".equalsIgnoreCase(categoryId)) {
            openColoredBlockGroups(player, requestedMode, requestedPage);
            return;
        }
        if (GuiShop.FOLDERS.hasFolders(categoryId)) {
            openFolders(player, categoryId, requestedMode, requestedPage);
            return;
        }

        ShopGui.Mode mode = resolveAllowedMode(player, requestedMode);
        if (mode == null) return;
        openItemPage(
            player,
            category.name,
            availableItems(category, mode),
            mode,
            requestedPage,
            ignored -> openCategories(player, mode),
            target -> openCategory(player, category.id, mode, target),
            targetMode -> openCategory(player, category.id, targetMode, 1)
        );
    }

    private static void openFolders(ServerPlayer player, String categoryId, ShopGui.Mode requestedMode, int requestedPage) {
        ShopGui.Mode mode = resolveAllowedMode(player, requestedMode);
        ShopConfig.Category category = GuiShop.CONFIG.category(categoryId);
        if (mode == null || category == null) return;

        List<FolderView> views = new ArrayList<>();
        int unsorted = countFolder(category, ShopFolderStore.UNSORTED, mode);
        if (unsorted > 0) views.add(new FolderView(ShopFolderStore.UNSORTED, "Unsorted", "minecraft:chest", unsorted));
        for (ShopFolderStore.Folder folder : GuiShop.FOLDERS.folders(category.id)) {
            int count = countFolder(category, folder.id, mode);
            if (count > 0) views.add(new FolderView(folder.id, folder.name, folder.icon, count));
        }

        if (views.isEmpty()) {
            openItemPage(
                player,
                category.name,
                availableItems(category, mode),
                mode,
                1,
                ignored -> openCategories(player, mode),
                target -> openCategory(player, category.id, mode, target),
                targetMode -> openCategory(player, category.id, targetMode, 1)
            );
            return;
        }

        int pages = Math.max(1, (views.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = clampPage(requestedPage, pages);
        ShopContainer container = new ShopContainer(54, player);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, views.size());

        for (int slot = 0; start + slot < end; slot++) {
            FolderView view = views.get(start + slot);
            container.bind(slot, displayStack(view.icon, view.name + " | " + view.count + " listings"), false,
                ignored -> openFolder(player, category.id, view.id, mode, 1, page));
        }

        container.bind(BACK_SLOT, displayStack("minecraft:barrier", "Back to Categories"), false,
            ignored -> openCategories(player, mode));
        if (page > 1) {
            int target = page - 1;
            container.bind(PREVIOUS_SLOT, displayStack("minecraft:arrow", "Previous Page"), false,
                ignored -> openFolders(player, category.id, mode, target));
        }
        container.bind(BALANCE_SLOT,
            displayStack("minecraft:paper", "Page " + page + "/" + pages + " | Balance "
                + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))),
            false,
            ignored -> ShopMessages.info(player, "Select a folder to browse " + category.name + ".")
        );
        if (page < pages) {
            int target = page + 1;
            container.bind(NEXT_SLOT, displayStack("minecraft:arrow", "Next Page"), false,
                ignored -> openFolders(player, category.id, mode, target));
        }
        if (ShopService.canUseMode(player, mode.opposite())) {
            container.bind(MODE_SLOT,
                displayStack(mode == ShopGui.Mode.BUY ? "minecraft:gold_ingot" : "minecraft:emerald",
                    "Switch to " + modeName(mode.opposite())),
                false,
                ignored -> openFolders(player, category.id, mode.opposite(), 1)
            );
        }

        openMenu(player, container, Component.literal(category.name + " Folders - " + modeName(mode) + " " + page + "/" + pages), 6);
    }

    private static void openFolder(
        ServerPlayer player,
        String categoryId,
        String folderId,
        ShopGui.Mode requestedMode,
        int requestedPage,
        int folderMenuPage
    ) {
        ShopGui.Mode mode = resolveAllowedMode(player, requestedMode);
        ShopConfig.Category category = GuiShop.CONFIG.category(categoryId);
        if (mode == null || category == null) return;

        List<ShopConfig.ShopItem> items = new ArrayList<>();
        for (ShopConfig.ShopItem item : category.items) {
            String assigned = GuiShop.FOLDERS.folderFor(category.id, item.listingId);
            if (folderId.equalsIgnoreCase(assigned) && price(item, mode) > 0) items.add(item);
        }

        String folderName = folderId.isBlank() ? "Unsorted" : folderName(category.id, folderId);
        openItemPage(
            player,
            folderName,
            items,
            mode,
            requestedPage,
            ignored -> openFolders(player, category.id, mode, folderMenuPage),
            target -> openFolder(player, category.id, folderId, mode, target, folderMenuPage),
            targetMode -> openFolders(player, category.id, targetMode, 1)
        );
    }

    private static void openColoredBlockGroups(ServerPlayer player, ShopGui.Mode requestedMode, int requestedPage) {
        ShopGui.Mode mode = resolveAllowedMode(player, requestedMode);
        ShopConfig.Category category = GuiShop.CONFIG.category("colored_blocks");
        if (mode == null || category == null) return;

        List<ColoredView> groups = new ArrayList<>();
        for (ColoredBlockGroups.Group group : ColoredBlockGroups.definitions()) {
            int count = 0;
            for (ShopConfig.ShopItem item : category.items) {
                if (ColoredBlockGroups.groupFor(item).id().equalsIgnoreCase(group.id()) && price(item, mode) > 0) count++;
            }
            if (count > 0) groups.add(new ColoredView(group, count));
        }

        int pages = Math.max(1, (groups.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = clampPage(requestedPage, pages);
        ShopContainer container = new ShopContainer(54, player);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, groups.size());

        for (int slot = 0; start + slot < end; slot++) {
            ColoredView view = groups.get(start + slot);
            container.bind(slot, displayStack(view.group.icon(), view.group.name() + " | " + view.count + " listings"), false,
                ignored -> openColoredBlockGroup(player, mode, view.group.id(), 1, page));
        }

        container.bind(BACK_SLOT, displayStack("minecraft:barrier", "Back to Categories"), false,
            ignored -> openCategories(player, mode));
        if (page > 1) {
            int target = page - 1;
            container.bind(PREVIOUS_SLOT, displayStack("minecraft:arrow", "Previous Page"), false,
                ignored -> openColoredBlockGroups(player, mode, target));
        }
        container.bind(BALANCE_SLOT,
            displayStack("minecraft:paper", "Page " + page + "/" + pages + " | Balance "
                + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))),
            false,
            ignored -> ShopMessages.info(player, "Select a colored block type, then choose a color.")
        );
        if (page < pages) {
            int target = page + 1;
            container.bind(NEXT_SLOT, displayStack("minecraft:arrow", "Next Page"), false,
                ignored -> openColoredBlockGroups(player, mode, target));
        }
        if (ShopService.canUseMode(player, mode.opposite())) {
            container.bind(MODE_SLOT,
                displayStack(mode == ShopGui.Mode.BUY ? "minecraft:gold_ingot" : "minecraft:emerald",
                    "Switch to " + modeName(mode.opposite())),
                false,
                ignored -> openColoredBlockGroups(player, mode.opposite(), 1)
            );
        }

        openMenu(player, container, Component.literal("Colored Blocks - " + modeName(mode) + " " + page + "/" + pages), 6);
    }

    private static void openColoredBlockGroup(
        ServerPlayer player,
        ShopGui.Mode mode,
        String groupId,
        int requestedPage,
        int groupMenuPage
    ) {
        ShopConfig.Category category = GuiShop.CONFIG.category("colored_blocks");
        if (category == null) return;
        ColoredBlockGroups.Group group = ColoredBlockGroups.byId(groupId);
        List<ShopConfig.ShopItem> items = new ArrayList<>();
        for (ShopConfig.ShopItem item : category.items) {
            if (ColoredBlockGroups.groupFor(item).id().equalsIgnoreCase(group.id()) && price(item, mode) > 0) items.add(item);
        }

        openItemPage(
            player,
            group.name(),
            items,
            mode,
            requestedPage,
            ignored -> openColoredBlockGroups(player, mode, groupMenuPage),
            target -> openColoredBlockGroup(player, mode, group.id(), target, groupMenuPage),
            targetMode -> openColoredBlockGroups(player, targetMode, 1)
        );
    }

    private static void openItemPage(
        ServerPlayer player,
        String title,
        List<ShopConfig.ShopItem> items,
        ShopGui.Mode mode,
        int requestedPage,
        GuiAction backAction,
        PageAction pageAction,
        ModeAction modeAction
    ) {
        int pages = Math.max(1, (items.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = clampPage(requestedPage, pages);
        ShopContainer container = new ShopContainer(54, player);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, items.size());

        for (int slot = 0; start + slot < end; slot++) {
            ShopConfig.ShopItem entry = items.get(start + slot);
            double unitPrice = price(entry, mode) * GuiShop.CONFIG.priceMultiplier;
            ItemStack icon = entry.createStack(player.registryAccess());
            if (icon.isEmpty()) icon = new ItemStack(Items.BARRIER);
            else icon = icon.copyWithCount(1);
            icon.set(DataComponents.CUSTOM_NAME, Component.literal(entry.name + " | " + modeName(mode) + " "
                + GuiShop.CONFIG.money(unitPrice) + " each"));
            container.bind(slot, icon, true, quantity -> {
                int requested = mode == ShopGui.Mode.SELL && quantity == 64 ? ShopService.SELL_ALL : quantity;
                ShopService.trade(player, entry, mode, requested);
            });
        }

        container.bind(BACK_SLOT, displayStack("minecraft:barrier", "Back"), false, backAction);
        if (page > 1) {
            int target = page - 1;
            container.bind(PREVIOUS_SLOT, displayStack("minecraft:arrow", "Previous Page"), false,
                ignored -> pageAction.open(target));
        }
        container.bind(BALANCE_SLOT,
            displayStack("minecraft:paper", "Page " + page + "/" + pages + " | Balance "
                + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))),
            false,
            ignored -> ShopMessages.info(player, "Page " + page + " of " + pages + ". Balance: "
                + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID())))
        );
        if (page < pages) {
            int target = page + 1;
            container.bind(NEXT_SLOT, displayStack("minecraft:arrow", "Next Page"), false,
                ignored -> pageAction.open(target));
        }
        if (ShopService.canUseMode(player, mode.opposite())) {
            container.bind(MODE_SLOT,
                displayStack(mode == ShopGui.Mode.BUY ? "minecraft:gold_ingot" : "minecraft:emerald",
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
        int page = clampPage(requestedPage, pages);
        ShopContainer container = new ShopContainer(54, player);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, enchantments.size());

        for (int slot = 0; start + slot < end; slot++) {
            EnchantmentShopService.EnchantmentView enchantment = enchantments.get(start + slot);
            ItemStack icon = EnchantmentShopService.createBook(enchantment.holder(), 1);
            String range = enchantment.maxLevel() == 1
                ? GuiShop.CONFIG.money(enchantment.firstLevelCost())
                : GuiShop.CONFIG.money(enchantment.firstLevelCost()) + " - " + GuiShop.CONFIG.money(enchantment.maximumLevelCost());
            icon.set(DataComponents.CUSTOM_NAME, Component.literal(enchantment.displayName() + " | Levels 1-"
                + enchantment.maxLevel() + " | " + range));
            container.bind(slot, icon, false,
                ignored -> openEnchantmentLevels(player, enchantment.enchantmentId(), 1, page));
        }

        container.bind(BACK_SLOT, displayStack("minecraft:barrier", "Back to Shop"), false,
            ignored -> openCategories(player, ShopGui.Mode.BUY));
        if (page > 1) {
            int target = page - 1;
            container.bind(PREVIOUS_SLOT, displayStack("minecraft:arrow", "Previous Page"), false,
                ignored -> openEnchantments(player, target));
        }
        container.bind(BALANCE_SLOT,
            displayStack("minecraft:paper", "Page " + page + "/" + pages + " | Balance "
                + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))),
            false,
            ignored -> ShopMessages.info(player, "Select an enchantment, then choose a level.")
        );
        if (page < pages) {
            int target = page + 1;
            container.bind(NEXT_SLOT, displayStack("minecraft:arrow", "Next Page"), false,
                ignored -> openEnchantments(player, target));
        }
        openMenu(player, container, Component.literal("Enchanted Books " + page + "/" + pages), 6);
    }

    private static void openEnchantmentLevels(ServerPlayer player, String enchantmentId, int requestedPage, int parentPage) {
        List<EnchantmentShopService.OfferView> offers = EnchantmentShopService.availableLevels(player, enchantmentId);
        if (offers.isEmpty()) {
            openEnchantments(player, parentPage);
            return;
        }

        int pages = Math.max(1, (offers.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = clampPage(requestedPage, pages);
        ShopContainer container = new ShopContainer(54, player);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, offers.size());

        for (int slot = 0; start + slot < end; slot++) {
            EnchantmentShopService.OfferView offer = offers.get(start + slot);
            ItemStack icon = EnchantmentShopService.createBook(offer.holder(), offer.targetLevel());
            icon.set(DataComponents.CUSTOM_NAME, Component.literal(offer.displayName() + " "
                + EnchantmentShopService.roman(offer.targetLevel()) + " | " + GuiShop.CONFIG.money(offer.cost())));
            container.bind(slot, icon, false, ignored -> {
                if (EnchantmentShopService.purchase(player, offer.enchantmentId(), offer.targetLevel())) {
                    openEnchantmentLevels(player, enchantmentId, page, parentPage);
                }
            });
        }

        container.bind(BACK_SLOT, displayStack("minecraft:barrier", "Back to Enchantments"), false,
            ignored -> openEnchantments(player, parentPage));
        if (page > 1) {
            int target = page - 1;
            container.bind(PREVIOUS_SLOT, displayStack("minecraft:arrow", "Previous Page"), false,
                ignored -> openEnchantmentLevels(player, enchantmentId, target, parentPage));
        }
        EnchantmentShopService.OfferView first = offers.get(0);
        container.bind(BALANCE_SLOT,
            displayStack("minecraft:paper", first.displayName() + " | Balance "
                + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))),
            false,
            ignored -> ShopMessages.info(player, "Choose which " + first.displayName() + " level to purchase.")
        );
        if (page < pages) {
            int target = page + 1;
            container.bind(NEXT_SLOT, displayStack("minecraft:arrow", "Next Page"), false,
                ignored -> openEnchantmentLevels(player, enchantmentId, target, parentPage));
        }
        openMenu(player, container, Component.literal(first.displayName() + " Levels"), 6);
    }

    private static int countFolder(ShopConfig.Category category, String folderId, ShopGui.Mode mode) {
        int count = 0;
        for (ShopConfig.ShopItem item : category.items) {
            if (price(item, mode) <= 0) continue;
            if (folderId.equalsIgnoreCase(GuiShop.FOLDERS.folderFor(category.id, item.listingId))) count++;
        }
        return count;
    }

    private static String folderName(String categoryId, String folderId) {
        ShopFolderStore.Folder folder = GuiShop.FOLDERS.folder(categoryId, folderId);
        return folder == null ? "Unsorted" : folder.name;
    }

    private static List<ShopConfig.ShopItem> availableItems(ShopConfig.Category category, ShopGui.Mode mode) {
        List<ShopConfig.ShopItem> items = new ArrayList<>();
        for (ShopConfig.ShopItem item : category.items) if (price(item, mode) > 0) items.add(item);
        return items;
    }

    private static boolean hasAvailableItems(ShopConfig.Category category, ShopGui.Mode mode) {
        for (ShopConfig.ShopItem item : category.items) if (price(item, mode) > 0) return true;
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
        Item item;
        try {
            item = BuiltInRegistries.ITEM.getValue(Identifier.parse(identifier));
            if (item == null || item == Items.AIR) item = Items.BARRIER;
        } catch (Exception ignored) {
            item = Items.BARRIER;
        }
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static void openMenu(ServerPlayer player, ShopContainer container, Component title, int rows) {
        player.openMenu(new SimpleMenuProvider(
            (containerId, inventory, ignored) -> new ShopMenu(containerId, inventory, container, rows),
            title
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
    private interface GuiAction { void run(int quantity); }
    @FunctionalInterface
    private interface PageAction { void open(int page); }
    @FunctionalInterface
    private interface ModeAction { void open(ShopGui.Mode mode); }

    private record MenuButton(ItemStack icon, Runnable action) {}
    private record FolderView(String id, String name, String icon, int count) {}
    private record ColoredView(ColoredBlockGroups.Group group, int count) {}
    private record Binding(GuiAction action, boolean trade) {}

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

        void activate(int slot, int quantity, boolean shift) {
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

        @Override public ItemStack removeItem(int slot, int amount) { return ItemStack.EMPTY; }
        @Override public ItemStack removeItemNoUpdate(int slot) { return ItemStack.EMPTY; }
        @Override public void setItem(int slot, ItemStack stack) {}
        @Override public boolean stillValid(Player player) { return player == owner && owner.isAlive(); }
    }

    private static final class ShopMenu extends ChestMenu {
        private final ShopContainer shop;

        ShopMenu(int containerId, Inventory inventory, ShopContainer shop, int rows) {
            super(menuType(rows), containerId, inventory, shop, rows);
            this.shop = shop;
        }

        @Override
        public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
            if (slotIndex >= 0 && slotIndex < shop.getContainerSize()) {
                if (input == ContainerInput.PICKUP) shop.activate(slotIndex, buttonNum == 1 ? 16 : 1, false);
                else if (input == ContainerInput.QUICK_MOVE) shop.activate(slotIndex, 64, true);
                return;
            }
            super.clicked(slotIndex, buttonNum, input, player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            if (index >= 0 && index < shop.getContainerSize()) shop.activate(index, 64, true);
            return ItemStack.EMPTY;
        }
    }
}
