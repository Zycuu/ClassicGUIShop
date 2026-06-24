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

public final class CursorStableShopGui {
    private static final int PAGE_SIZE = 45;
    private static final int BACK_SLOT = 45;
    private static final int PREVIOUS_SLOT = 48;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 50;
    private static final int MODE_SLOT = 53;

    private CursorStableShopGui() {}

    public static void openCategories(ServerPlayer player, ShopGui.Mode requestedMode) {
        Session session = new Session(player);
        session.renderCategories(requestedMode);
        session.open();
    }

    public static void openEnchantments(ServerPlayer player, int requestedPage) {
        Session session = new Session(player);
        session.renderEnchantments(requestedPage);
        session.open();
    }

    private static final class Session {
        private final ServerPlayer player;
        private final ShopContainer container;

        private Session(ServerPlayer player) {
            this.player = player;
            this.container = new ShopContainer(54, player);
        }

        private void open() {
            player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> {
                    ShopMenu menu = new ShopMenu(containerId, inventory, container);
                    container.attach(menu);
                    return menu;
                },
                Component.literal("ClassicGUIShop")
            ));
        }

        private void renderCategories(ShopGui.Mode requestedMode) {
            GuiShop.PLAYERS.remember(player);
            ShopGui.Mode mode = resolveAllowedMode(player, requestedMode);
            if (mode == null) return;

            container.beginRender();
            int slot = 0;
            for (ShopConfig.Category category : GuiShop.CONFIG.categories) {
                if (slot >= PAGE_SIZE) break;
                if (!ShopPermissions.category(player, category.id) || !hasAvailableItems(category, mode)) continue;
                container.bind(slot++, displayStack(category.icon, category.name + " | " + modeName(mode)), false,
                    ignored -> renderCategory(category.id, mode, 1));
            }

            if (slot < PAGE_SIZE
                && GuiShop.CONFIG.enchantmentsEnabled()
                && ShopPermissions.user(player, "guishop.enchant")) {
                container.bind(slot, displayStack("minecraft:enchanted_book", "Enchanted Books"), false,
                    ignored -> renderEnchantments(1));
            }

            container.bind(INFO_SLOT,
                displayStack("minecraft:paper", "Categories | " + modeName(mode) + " | Balance "
                    + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))),
                false,
                ignored -> ShopMessages.info(player,
                    "Balance: " + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID())))
            );
            if (ShopService.canUseMode(player, mode.opposite())) {
                container.bind(MODE_SLOT,
                    displayStack(mode == ShopGui.Mode.BUY ? "minecraft:gold_ingot" : "minecraft:emerald",
                        "Switch to " + modeName(mode.opposite())),
                    false,
                    ignored -> renderCategories(mode.opposite())
                );
            }
            container.endRender();
        }

        private void renderCategory(
            String categoryId,
            ShopGui.Mode requestedMode,
            int requestedPage
        ) {
            ShopConfig.Category category = GuiShop.CONFIG.category(categoryId);
            if (category == null || !ShopPermissions.category(player, categoryId)) {
                ShopMessages.error(player, "That shop category is unavailable.");
                renderCategories(requestedMode);
                return;
            }

            if ("colored_blocks".equalsIgnoreCase(categoryId)) {
                renderColoredBlockGroups(requestedMode, requestedPage);
                return;
            }
            if (GuiShop.FOLDERS.hasFolders(categoryId)) {
                renderFolders(categoryId, requestedMode, requestedPage);
                return;
            }

            ShopGui.Mode mode = resolveAllowedMode(player, requestedMode);
            if (mode == null) return;
            renderItemPage(
                category.name,
                availableItems(category, mode),
                mode,
                requestedPage,
                ignored -> renderCategories(mode),
                target -> renderCategory(category.id, mode, target),
                targetMode -> renderCategory(category.id, targetMode, 1)
            );
        }

        private void renderFolders(
            String categoryId,
            ShopGui.Mode requestedMode,
            int requestedPage
        ) {
            ShopGui.Mode mode = resolveAllowedMode(player, requestedMode);
            ShopConfig.Category category = GuiShop.CONFIG.category(categoryId);
            if (mode == null || category == null) return;

            List<FolderView> views = new ArrayList<>();
            int unsorted = countFolder(category, ShopFolderStore.UNSORTED, mode);
            if (unsorted > 0) {
                views.add(new FolderView(ShopFolderStore.UNSORTED, "Unsorted", "minecraft:chest", unsorted));
            }
            for (ShopFolderStore.Folder folder : GuiShop.FOLDERS.folders(category.id)) {
                int count = countFolder(category, folder.id, mode);
                if (count > 0) views.add(new FolderView(folder.id, folder.name, folder.icon, count));
            }

            if (views.isEmpty()) {
                renderItemPage(
                    category.name,
                    availableItems(category, mode),
                    mode,
                    1,
                    ignored -> renderCategories(mode),
                    target -> renderCategory(category.id, mode, target),
                    targetMode -> renderCategory(category.id, targetMode, 1)
                );
                return;
            }

            int pages = Math.max(1, (views.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            int page = clampPage(requestedPage, pages);
            int start = (page - 1) * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, views.size());

            container.beginRender();
            for (int slot = 0; start + slot < end; slot++) {
                FolderView view = views.get(start + slot);
                container.bind(slot,
                    displayStack(view.icon, view.name + " | " + view.count + " listings"),
                    false,
                    ignored -> renderFolder(category.id, view.id, mode, 1, page));
            }

            container.bind(BACK_SLOT, displayStack("minecraft:barrier", "Back to Categories"), false,
                ignored -> renderCategories(mode));
            if (page > 1) {
                int target = page - 1;
                container.bind(PREVIOUS_SLOT, displayStack("minecraft:arrow", "Previous Page"), false,
                    ignored -> renderFolders(category.id, mode, target));
            }
            container.bind(INFO_SLOT,
                displayStack("minecraft:paper", category.name + " Folders | " + modeName(mode)
                    + " | Page " + page + "/" + pages + " | Balance "
                    + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))),
                false,
                ignored -> ShopMessages.info(player, "Select a folder to browse " + category.name + ".")
            );
            if (page < pages) {
                int target = page + 1;
                container.bind(NEXT_SLOT, displayStack("minecraft:arrow", "Next Page"), false,
                    ignored -> renderFolders(category.id, mode, target));
            }
            if (ShopService.canUseMode(player, mode.opposite())) {
                container.bind(MODE_SLOT,
                    displayStack(mode == ShopGui.Mode.BUY ? "minecraft:gold_ingot" : "minecraft:emerald",
                        "Switch to " + modeName(mode.opposite())),
                    false,
                    ignored -> renderFolders(category.id, mode.opposite(), 1)
                );
            }
            container.endRender();
        }

        private void renderFolder(
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
            renderItemPage(
                folderName,
                items,
                mode,
                requestedPage,
                ignored -> renderFolders(category.id, mode, folderMenuPage),
                target -> renderFolder(category.id, folderId, mode, target, folderMenuPage),
                targetMode -> renderFolders(category.id, targetMode, 1)
            );
        }

        private void renderColoredBlockGroups(
            ShopGui.Mode requestedMode,
            int requestedPage
        ) {
            ShopGui.Mode mode = resolveAllowedMode(player, requestedMode);
            ShopConfig.Category category = GuiShop.CONFIG.category("colored_blocks");
            if (mode == null || category == null) return;

            List<ColoredView> groups = new ArrayList<>();
            for (ColoredBlockGroups.Group group : ColoredBlockGroups.definitions()) {
                int count = 0;
                for (ShopConfig.ShopItem item : category.items) {
                    if (ColoredBlockGroups.groupFor(item).id().equalsIgnoreCase(group.id())
                        && price(item, mode) > 0) {
                        count++;
                    }
                }
                if (count > 0) groups.add(new ColoredView(group, count));
            }

            int pages = Math.max(1, (groups.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            int page = clampPage(requestedPage, pages);
            int start = (page - 1) * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, groups.size());

            container.beginRender();
            for (int slot = 0; start + slot < end; slot++) {
                ColoredView view = groups.get(start + slot);
                container.bind(slot,
                    displayStack(view.group.icon(), view.group.name() + " | " + view.count + " listings"),
                    false,
                    ignored -> renderColoredBlockGroup(mode, view.group.id(), 1, page));
            }

            container.bind(BACK_SLOT, displayStack("minecraft:barrier", "Back to Categories"), false,
                ignored -> renderCategories(mode));
            if (page > 1) {
                int target = page - 1;
                container.bind(PREVIOUS_SLOT, displayStack("minecraft:arrow", "Previous Page"), false,
                    ignored -> renderColoredBlockGroups(mode, target));
            }
            container.bind(INFO_SLOT,
                displayStack("minecraft:paper", "Colored Blocks | " + modeName(mode) + " | Page "
                    + page + "/" + pages + " | Balance "
                    + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))),
                false,
                ignored -> ShopMessages.info(player, "Select a colored block type, then choose a color.")
            );
            if (page < pages) {
                int target = page + 1;
                container.bind(NEXT_SLOT, displayStack("minecraft:arrow", "Next Page"), false,
                    ignored -> renderColoredBlockGroups(mode, target));
            }
            if (ShopService.canUseMode(player, mode.opposite())) {
                container.bind(MODE_SLOT,
                    displayStack(mode == ShopGui.Mode.BUY ? "minecraft:gold_ingot" : "minecraft:emerald",
                        "Switch to " + modeName(mode.opposite())),
                    false,
                    ignored -> renderColoredBlockGroups(mode.opposite(), 1)
                );
            }
            container.endRender();
        }

        private void renderColoredBlockGroup(
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
                if (ColoredBlockGroups.groupFor(item).id().equalsIgnoreCase(group.id())
                    && price(item, mode) > 0) {
                    items.add(item);
                }
            }

            renderItemPage(
                group.name(),
                items,
                mode,
                requestedPage,
                ignored -> renderColoredBlockGroups(mode, groupMenuPage),
                target -> renderColoredBlockGroup(mode, group.id(), target, groupMenuPage),
                targetMode -> renderColoredBlockGroups(targetMode, 1)
            );
        }

        private void renderItemPage(
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
            int start = (page - 1) * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, items.size());

            container.beginRender();
            for (int slot = 0; start + slot < end; slot++) {
                ShopConfig.ShopItem entry = items.get(start + slot);
                double unitPrice = price(entry, mode) * GuiShop.CONFIG.priceMultiplier;
                ItemStack icon = entry.createStack(player.registryAccess());
                if (icon.isEmpty()) icon = new ItemStack(Items.BARRIER);
                else icon = icon.copyWithCount(1);
                icon.set(DataComponents.CUSTOM_NAME,
                    Component.literal(entry.name + " | " + modeName(mode) + " "
                        + GuiShop.CONFIG.money(unitPrice) + " each"));
                container.bind(slot, icon, true, quantity -> {
                    int requested = mode == ShopGui.Mode.SELL && quantity == 64
                        ? ShopService.SELL_ALL
                        : quantity;
                    if (ShopService.trade(player, entry, mode, requested)) {
                        renderItemPage(title, items, mode, page, backAction, pageAction, modeAction);
                    }
                });
            }

            container.bind(BACK_SLOT, displayStack("minecraft:barrier", "Back"), false, backAction);
            if (page > 1) {
                int target = page - 1;
                container.bind(PREVIOUS_SLOT, displayStack("minecraft:arrow", "Previous Page"), false,
                    ignored -> pageAction.open(target));
            }
            container.bind(INFO_SLOT,
                displayStack("minecraft:paper", title + " | " + modeName(mode) + " | Page "
                    + page + "/" + pages + " | Balance "
                    + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))),
                false,
                ignored -> ShopMessages.info(player,
                    "Page " + page + " of " + pages + ". Balance: "
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
            container.endRender();
        }

        private void renderEnchantments(int requestedPage) {
            if (!GuiShop.CONFIG.enchantmentsEnabled()
                || !ShopPermissions.user(player, "guishop.enchant")) {
                ShopMessages.warning(player, "The enchanted book shop is unavailable.");
                renderCategories(ShopGui.Mode.BUY);
                return;
            }

            List<EnchantmentShopService.EnchantmentView> enchantments =
                EnchantmentShopService.availableEnchantments(player);
            if (enchantments.isEmpty()) {
                ShopMessages.warning(player, "No enchanted books are currently available.");
                renderCategories(ShopGui.Mode.BUY);
                return;
            }

            int pages = Math.max(1, (enchantments.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            int page = clampPage(requestedPage, pages);
            int start = (page - 1) * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, enchantments.size());

            container.beginRender();
            for (int slot = 0; start + slot < end; slot++) {
                EnchantmentShopService.EnchantmentView enchantment = enchantments.get(start + slot);
                ItemStack icon = EnchantmentShopService.createBook(enchantment.holder(), 1);
                String range = enchantment.maxLevel() == 1
                    ? GuiShop.CONFIG.money(enchantment.firstLevelCost())
                    : GuiShop.CONFIG.money(enchantment.firstLevelCost()) + " to "
                        + GuiShop.CONFIG.money(enchantment.maximumLevelCost());
                icon.set(DataComponents.CUSTOM_NAME,
                    Component.literal(enchantment.displayName() + " | Levels 1 to "
                        + enchantment.maxLevel() + " | " + range));
                container.bind(slot, icon, false,
                    ignored -> renderEnchantmentLevels(enchantment.enchantmentId(), 1, page));
            }

            container.bind(BACK_SLOT, displayStack("minecraft:barrier", "Back to Shop"), false,
                ignored -> renderCategories(ShopGui.Mode.BUY));
            if (page > 1) {
                int target = page - 1;
                container.bind(PREVIOUS_SLOT, displayStack("minecraft:arrow", "Previous Page"), false,
                    ignored -> renderEnchantments(target));
            }
            container.bind(INFO_SLOT,
                displayStack("minecraft:paper", "Enchanted Books | Page " + page + "/" + pages
                    + " | Balance " + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))),
                false,
                ignored -> ShopMessages.info(player, "Select an enchantment, then choose a level.")
            );
            if (page < pages) {
                int target = page + 1;
                container.bind(NEXT_SLOT, displayStack("minecraft:arrow", "Next Page"), false,
                    ignored -> renderEnchantments(target));
            }
            container.endRender();
        }

        private void renderEnchantmentLevels(
            String enchantmentId,
            int requestedPage,
            int parentPage
        ) {
            List<EnchantmentShopService.OfferView> offers =
                EnchantmentShopService.availableLevels(player, enchantmentId);
            if (offers.isEmpty()) {
                renderEnchantments(parentPage);
                return;
            }

            int pages = Math.max(1, (offers.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            int page = clampPage(requestedPage, pages);
            int start = (page - 1) * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, offers.size());
            EnchantmentShopService.OfferView first = offers.get(0);

            container.beginRender();
            for (int slot = 0; start + slot < end; slot++) {
                EnchantmentShopService.OfferView offer = offers.get(start + slot);
                ItemStack icon = EnchantmentShopService.createBook(offer.holder(), offer.targetLevel());
                icon.set(DataComponents.CUSTOM_NAME,
                    Component.literal(offer.displayName() + " "
                        + EnchantmentShopService.roman(offer.targetLevel()) + " | "
                        + GuiShop.CONFIG.money(offer.cost())));
                container.bind(slot, icon, false, ignored -> {
                    if (EnchantmentShopService.purchase(player, offer.enchantmentId(), offer.targetLevel())) {
                        renderEnchantmentLevels(enchantmentId, page, parentPage);
                    }
                });
            }

            container.bind(BACK_SLOT, displayStack("minecraft:barrier", "Back to Enchantments"), false,
                ignored -> renderEnchantments(parentPage));
            if (page > 1) {
                int target = page - 1;
                container.bind(PREVIOUS_SLOT, displayStack("minecraft:arrow", "Previous Page"), false,
                    ignored -> renderEnchantmentLevels(enchantmentId, target, parentPage));
            }
            container.bind(INFO_SLOT,
                displayStack("minecraft:paper", first.displayName() + " Levels | Page " + page + "/" + pages
                    + " | Balance " + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))),
                false,
                ignored -> ShopMessages.info(player,
                    "Choose which " + first.displayName() + " level to purchase.")
            );
            if (page < pages) {
                int target = page + 1;
                container.bind(NEXT_SLOT, displayStack("minecraft:arrow", "Next Page"), false,
                    ignored -> renderEnchantmentLevels(enchantmentId, target, parentPage));
            }
            container.endRender();
        }
    }

    private static int countFolder(
        ShopConfig.Category category,
        String folderId,
        ShopGui.Mode mode
    ) {
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

    private static boolean hasAvailableItems(
        ShopConfig.Category category,
        ShopGui.Mode mode
    ) {
        for (ShopConfig.ShopItem item : category.items) {
            if (price(item, mode) > 0) return true;
        }
        return false;
    }

    private static ShopGui.Mode resolveAllowedMode(
        ServerPlayer player,
        ShopGui.Mode requested
    ) {
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
        void open(ShopGui.Mode mode);
    }

    private record FolderView(String id, String name, String icon, int count) {}
    private record ColoredView(ColoredBlockGroups.Group group, int count) {}
    private record Binding(GuiAction action, boolean trade) {}

    private static final class ShopContainer extends SimpleContainer {
        private final Map<Integer, Binding> bindings = new HashMap<>();
        private final ServerPlayer owner;
        private ShopMenu menu;
        private boolean processing;

        private ShopContainer(int size, ServerPlayer owner) {
            super(size);
            this.owner = owner;
        }

        private void attach(ShopMenu menu) {
            this.menu = menu;
            menu.broadcastChanges();
        }

        private void beginRender() {
            bindings.clear();
            for (int slot = 0; slot < getContainerSize(); slot++) {
                super.setItem(slot, ItemStack.EMPTY);
            }
        }

        private void bind(int slot, ItemStack stack, boolean trade, GuiAction action) {
            super.setItem(slot, stack.copy());
            bindings.put(slot, new Binding(action, trade));
        }

        private void endRender() {
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

        private ShopMenu(int containerId, Inventory inventory, ShopContainer shop) {
            super(MenuType.GENERIC_9x6, containerId, inventory, shop, 6);
            this.shop = shop;
        }

        @Override
        public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
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
            if (index >= 0 && index < shop.getContainerSize()) shop.activate(index, 64, true);
            return ItemStack.EMPTY;
        }
    }
}
