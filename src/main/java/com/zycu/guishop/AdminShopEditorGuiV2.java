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

public final class AdminShopEditorGuiV2 {
    private static final int PAGE_SIZE = 45;
    private static final int BACK_SLOT = 45;
    private static final int PREVIOUS_SLOT = 48;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 50;
    private static final int SECONDARY_ACTION_SLOT = 52;
    private static final int ACTION_SLOT = 53;

    private AdminShopEditorGuiV2() {}

    public static void open(ServerPlayer player) {
        openCategories(player, 1);
    }

    private static void openCategories(ServerPlayer player, int requestedPage) {
        List<ShopConfig.Category> categories = GuiShop.CONFIG.categories;
        int pages = Math.max(1, (categories.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = clampPage(requestedPage, pages);
        EditorContainer container = new EditorContainer(54, player);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, categories.size());

        for (int slot = 0; start + slot < end; slot++) {
            ShopConfig.Category category = categories.get(start + slot);
            int buyEnabled = 0;
            int sellEnabled = 0;
            int hidden = 0;
            for (ShopConfig.ShopItem item : category.items) {
                if (item.buy > 0) buyEnabled++;
                if (item.sell > 0) sellEnabled++;
                if (item.buy <= 0 && item.sell <= 0) hidden++;
            }
            String label = category.name + " | " + category.items.size() + " items | Buy " + buyEnabled
                + " | Sell " + sellEnabled + " | Hidden " + hidden;
            container.bind(slot, displayStack(category.icon, label), () -> openCategory(player, category.id, 1));
        }

        addPager(container, page, pages,
            target -> openCategories(player, target),
            "All categories are shown here, including empty and fully unpriced shops.");
        openMenu(player, container, Component.literal("Shop Editor - Categories " + page + "/" + pages), 6);
    }

    private static void openCategory(ServerPlayer player, String categoryId, int requestedPage) {
        ShopConfig.Category category = GuiShop.CONFIG.category(categoryId);
        if (category == null) {
            ShopMessages.error(player, "Category no longer exists: " + categoryId);
            openCategories(player, 1);
            return;
        }

        List<FolderEntry> folders = new ArrayList<>();
        folders.add(new FolderEntry(null, "All Items", category.icon, category.items.size()));
        folders.add(new FolderEntry(ShopFolderStore.UNSORTED, "Unsorted", "minecraft:chest",
            countFolder(category, ShopFolderStore.UNSORTED)));
        for (ShopFolderStore.Folder folder : GuiShop.FOLDERS.folders(category.id)) {
            folders.add(new FolderEntry(folder.id, folder.name, folder.icon, countFolder(category, folder.id)));
        }

        int pages = Math.max(1, (folders.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = clampPage(requestedPage, pages);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, folders.size());
        EditorContainer container = new EditorContainer(54, player);

        for (int slot = 0; start + slot < end; slot++) {
            FolderEntry folder = folders.get(start + slot);
            String label = folder.name + " | " + folder.itemCount + " items";
            container.bind(slot, displayStack(folder.icon, label),
                () -> openItems(player, category.id, folder.folderId, 1, page));
        }

        container.bind(BACK_SLOT, displayStack("minecraft:barrier", "Back to Categories"),
            () -> openCategories(player, 1));
        if (page > 1) {
            int target = page - 1;
            container.bind(PREVIOUS_SLOT, displayStack("minecraft:arrow", "Previous Folder Page"),
                () -> openCategory(player, category.id, target));
        }
        container.bind(INFO_SLOT, displayStack("minecraft:paper",
            category.name + " | " + category.items.size() + " listings | "
                + GuiShop.FOLDERS.folders(category.id).size() + " folders | Page " + page + "/" + pages),
            () -> ShopMessages.info(player,
                "Folders organize the player shop. Unpriced items remain visible in this editor."));
        if (page < pages) {
            int target = page + 1;
            container.bind(NEXT_SLOT, displayStack("minecraft:arrow", "Next Folder Page"),
                () -> openCategory(player, category.id, target));
        }
        container.bind(ACTION_SLOT, displayStack("minecraft:lime_dye", "Create Folder"),
            () -> promptCreateFolder(player, category.id));

        openMenu(player, container,
            Component.literal("Shop Editor - " + category.name + " " + page + "/" + pages), 6);
    }

    private static void openItems(
        ServerPlayer player,
        String categoryId,
        String folderId,
        int requestedPage,
        int folderMenuPage
    ) {
        ShopConfig.Category category = GuiShop.CONFIG.category(categoryId);
        if (category == null) {
            openCategories(player, 1);
            return;
        }

        List<ShopConfig.ShopItem> items = filteredItems(category, folderId);
        int pages = Math.max(1, (items.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = clampPage(requestedPage, pages);
        EditorContainer container = new EditorContainer(54, player);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, items.size());

        for (int slot = 0; start + slot < end; slot++) {
            ShopConfig.ShopItem item = items.get(start + slot);
            ItemStack icon = item.createStack(player.registryAccess());
            if (icon.isEmpty()) icon = new ItemStack(Items.BARRIER);
            else icon = icon.copyWithCount(1);
            String state = item.buy <= 0 && item.sell <= 0 ? "HIDDEN" : "ACTIVE";
            icon.set(DataComponents.CUSTOM_NAME, Component.literal(item.name + " | " + state + " | Buy "
                + moneyOrDisabled(item.buy) + " | Sell " + moneyOrDisabled(item.sell)));
            container.bind(slot, icon,
                () -> openItem(player, category.id, item, folderId, page, folderMenuPage));
        }

        String folderName = folderDisplayName(category.id, folderId);
        container.bind(BACK_SLOT, displayStack("minecraft:barrier", "Back to Folders"),
            () -> openCategory(player, category.id, folderMenuPage));
        if (page > 1) {
            int target = page - 1;
            container.bind(PREVIOUS_SLOT, displayStack("minecraft:arrow", "Previous Page"),
                () -> openItems(player, category.id, folderId, target, folderMenuPage));
        }
        container.bind(INFO_SLOT, displayStack("minecraft:paper",
            folderName + " | " + items.size() + " items | Page " + page + "/" + pages),
            () -> ShopMessages.info(player,
                "Every listing is revealed here, including items with both prices set to 0."));
        if (page < pages) {
            int target = page + 1;
            container.bind(NEXT_SLOT, displayStack("minecraft:arrow", "Next Page"),
                () -> openItems(player, category.id, folderId, target, folderMenuPage));
        }

        if (folderId != null && !folderId.isBlank()) {
            container.bind(SECONDARY_ACTION_SLOT, displayStack("minecraft:name_tag", "Rename Folder"),
                () -> promptRenameFolder(player, category.id, folderId, folderMenuPage));
            container.bind(ACTION_SLOT,
                displayStack("minecraft:red_dye", "Delete Folder and move items to Unsorted"),
                () -> confirmDeleteFolder(player, category.id, folderId, page, folderMenuPage));
        }

        openMenu(player, container,
            Component.literal("Editor - " + category.name + " / " + folderName + " " + page + "/" + pages), 6);
    }

    private static void openItem(
        ServerPlayer player,
        String categoryId,
        ShopConfig.ShopItem item,
        String returnFolder,
        int returnPage,
        int folderMenuPage
    ) {
        ShopConfig.Category category = GuiShop.CONFIG.category(categoryId);
        if (category == null || !category.items.contains(item)) {
            openCategory(player, categoryId, folderMenuPage);
            return;
        }

        EditorContainer container = new EditorContainer(27, player);
        ItemStack preview = item.createStack(player.registryAccess());
        if (preview.isEmpty()) preview = new ItemStack(Items.BARRIER);
        else preview = preview.copyWithCount(1);
        preview.set(DataComponents.CUSTOM_NAME, Component.literal(item.name + " | " + item.listingId));
        container.bind(4, preview, () -> {
            ShopMessages.info(player, "Item ID: " + item.item);
            ShopMessages.info(player, "Listing ID: " + item.listingId);
        });

        container.bind(10,
            displayStack("minecraft:gold_ingot", "Set Buy Price | Current " + moneyOrDisabled(item.buy)),
            () -> promptPrice(player, category, item, true, returnFolder, returnPage, folderMenuPage));
        container.bind(11, displayStack("minecraft:redstone_torch", "Disable Buying | Set buy to 0"),
            () -> {
                updatePrice(item, true, 0.0);
                openItem(player, category.id, item, returnFolder, returnPage, folderMenuPage);
            });
        container.bind(13, displayStack("minecraft:chest", "Move to Folder | Current "
                + folderDisplayName(category.id, GuiShop.FOLDERS.folderFor(category.id, item.listingId))),
            () -> openFolderChooser(player, category, item, returnFolder, returnPage, folderMenuPage, 1));
        container.bind(15, displayStack("minecraft:redstone_torch", "Disable Selling | Set sell to 0"),
            () -> {
                updatePrice(item, false, 0.0);
                openItem(player, category.id, item, returnFolder, returnPage, folderMenuPage);
            });
        container.bind(16,
            displayStack("minecraft:emerald", "Set Sell Price | Current " + moneyOrDisabled(item.sell)),
            () -> promptPrice(player, category, item, false, returnFolder, returnPage, folderMenuPage));

        container.bind(22, displayStack("minecraft:barrier", "Back to Item List"),
            () -> openItems(player, category.id, returnFolder, returnPage, folderMenuPage));
        container.bind(24, displayStack("minecraft:paper", item.buy <= 0 && item.sell <= 0
                ? "Hidden from players: both prices are 0"
                : "Visible in enabled shop modes"),
            () -> ShopMessages.info(player,
                "Buy " + moneyOrDisabled(item.buy) + " | Sell " + moneyOrDisabled(item.sell)));

        openMenu(player, container, Component.literal("Edit Listing - " + item.name), 3);
    }

    private static void openFolderChooser(
        ServerPlayer player,
        ShopConfig.Category category,
        ShopConfig.ShopItem item,
        String returnFolder,
        int returnPage,
        int folderMenuPage,
        int requestedPage
    ) {
        List<FolderEntry> choices = new ArrayList<>();
        choices.add(new FolderEntry(ShopFolderStore.UNSORTED, "Unsorted", "minecraft:chest", 0));
        for (ShopFolderStore.Folder folder : GuiShop.FOLDERS.folders(category.id)) {
            choices.add(new FolderEntry(folder.id, folder.name, folder.icon, 0));
        }

        int pages = Math.max(1, (choices.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = clampPage(requestedPage, pages);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, choices.size());
        EditorContainer container = new EditorContainer(54, player);

        for (int slot = 0; start + slot < end; slot++) {
            FolderEntry choice = choices.get(start + slot);
            container.bind(slot, displayStack(choice.icon, choice.name), () -> {
                GuiShop.FOLDERS.assign(category.id, item.listingId, choice.folderId);
                openItem(player, category.id, item, returnFolder, returnPage, folderMenuPage);
            });
        }

        container.bind(BACK_SLOT, displayStack("minecraft:barrier", "Cancel"),
            () -> openItem(player, category.id, item, returnFolder, returnPage, folderMenuPage));
        if (page > 1) {
            int target = page - 1;
            container.bind(PREVIOUS_SLOT, displayStack("minecraft:arrow", "Previous Folder Page"),
                () -> openFolderChooser(player, category, item, returnFolder, returnPage,
                    folderMenuPage, target));
        }
        container.bind(INFO_SLOT,
            displayStack("minecraft:paper", "Choose Folder | Page " + page + "/" + pages),
            () -> ShopMessages.info(player, "Choose a folder for " + item.name + "."));
        if (page < pages) {
            int target = page + 1;
            container.bind(NEXT_SLOT, displayStack("minecraft:arrow", "Next Folder Page"),
                () -> openFolderChooser(player, category, item, returnFolder, returnPage,
                    folderMenuPage, target));
        }
        container.bind(ACTION_SLOT, displayStack("minecraft:lime_dye", "Create Folder"), () ->
            AnvilTextPrompt.open(player, "Create Folder", "New Folder", value -> {
                ShopFolderStore.Folder folder = GuiShop.FOLDERS.createFolder(category.id, value, item.item);
                if (folder == null) {
                    ShopMessages.error(player, "Could not create that folder.");
                    openFolderChooser(player, category, item, returnFolder, returnPage,
                        folderMenuPage, page);
                    return;
                }
                GuiShop.FOLDERS.assign(category.id, item.listingId, folder.id);
                openItem(player, category.id, item, returnFolder, returnPage, folderMenuPage);
            }));

        openMenu(player, container,
            Component.literal("Move " + item.name + " to Folder " + page + "/" + pages), 6);
    }

    private static void confirmDeleteFolder(
        ServerPlayer player,
        String categoryId,
        String folderId,
        int itemPage,
        int folderMenuPage
    ) {
        ShopConfig.Category category = GuiShop.CONFIG.category(categoryId);
        ShopFolderStore.Folder folder = GuiShop.FOLDERS.folder(categoryId, folderId);
        if (category == null || folder == null) {
            ShopMessages.error(player, "That folder no longer exists.");
            openCategory(player, categoryId, folderMenuPage);
            return;
        }

        EditorContainer container = new EditorContainer(27, player);
        container.bind(4, displayStack(folder.icon,
            "Delete " + folder.name + "? Its listings will move to Unsorted."),
            () -> ShopMessages.warning(player,
                "This action removes only the folder. It does not delete listings or prices."));
        container.bind(11, displayStack("minecraft:red_dye", "Confirm Folder Deletion"), () -> {
            if (GuiShop.FOLDERS.deleteFolder(category.id, folder.id)) {
                ShopMessages.warning(player, "Folder deleted. Its listings are now Unsorted.");
            } else {
                ShopMessages.error(player, "Could not delete that folder.");
            }
            openCategory(player, category.id, folderMenuPage);
        });
        container.bind(15, displayStack("minecraft:barrier", "Cancel"),
            () -> openItems(player, category.id, folder.id, itemPage, folderMenuPage));

        openMenu(player, container, Component.literal("Confirm Folder Deletion"), 3);
    }

    private static void promptPrice(
        ServerPlayer player,
        ShopConfig.Category category,
        ShopConfig.ShopItem item,
        boolean buy,
        String returnFolder,
        int returnPage,
        int folderMenuPage
    ) {
        double current = buy ? item.buy : item.sell;
        String title = buy ? "Set Buy Price" : "Set Sell Price";
        AnvilTextPrompt.open(player, title, formatNumber(current), value -> {
            try {
                double parsed = Double.parseDouble(value.replace(",", "")
                    .replace(GuiShop.CONFIG.currencySymbol, "").trim());
                if (!Double.isFinite(parsed) || parsed < 0) throw new NumberFormatException();
                updatePrice(item, buy, parsed);
                ShopMessages.success(player,
                    (buy ? "Buy" : "Sell") + " price set to " + GuiShop.CONFIG.money(parsed) + ".");
            } catch (NumberFormatException exception) {
                ShopMessages.error(player,
                    "Invalid price. Enter a number of 0 or greater, such as 25 or 19.95.");
            }
            openItem(player, category.id, item, returnFolder, returnPage, folderMenuPage);
        });
    }

    private static void promptCreateFolder(ServerPlayer player, String categoryId) {
        ShopConfig.Category category = GuiShop.CONFIG.category(categoryId);
        if (category == null) return;
        AnvilTextPrompt.open(player, "Create Shop Folder", "New Folder", value -> {
            ShopFolderStore.Folder folder = GuiShop.FOLDERS.createFolder(category.id, value, "minecraft:chest");
            if (folder == null) ShopMessages.error(player, "Could not create that folder.");
            else ShopMessages.success(player, "Created folder " + folder.name + ".");
            openCategory(player, category.id, Integer.MAX_VALUE);
        });
    }

    private static void promptRenameFolder(
        ServerPlayer player,
        String categoryId,
        String folderId,
        int folderMenuPage
    ) {
        ShopFolderStore.Folder folder = GuiShop.FOLDERS.folder(categoryId, folderId);
        if (folder == null) {
            openCategory(player, categoryId, folderMenuPage);
            return;
        }
        AnvilTextPrompt.open(player, "Rename Shop Folder", folder.name, value -> {
            if (!GuiShop.FOLDERS.renameFolder(categoryId, folderId, value)) {
                ShopMessages.error(player, "Folder name cannot be blank.");
            }
            openCategory(player, categoryId, folderMenuPage);
        });
    }

    private static void updatePrice(ShopConfig.ShopItem item, boolean buy, double value) {
        if (buy) item.buy = value;
        else item.sell = value;
        item.manualPrice = true;
        item.pricingModelVersion = BalancedPricing.MODEL_VERSION;
        GuiShop.CONFIG.save();
    }

    private static List<ShopConfig.ShopItem> filteredItems(ShopConfig.Category category, String folderId) {
        if (folderId == null) return new ArrayList<>(category.items);
        List<ShopConfig.ShopItem> items = new ArrayList<>();
        for (ShopConfig.ShopItem item : category.items) {
            String assigned = GuiShop.FOLDERS.folderFor(category.id, item.listingId);
            if (folderId.equalsIgnoreCase(assigned)) items.add(item);
        }
        return items;
    }

    private static int countFolder(ShopConfig.Category category, String folderId) {
        return filteredItems(category, folderId).size();
    }

    private static String folderDisplayName(String categoryId, String folderId) {
        if (folderId == null) return "All Items";
        if (folderId.isBlank()) return "Unsorted";
        ShopFolderStore.Folder folder = GuiShop.FOLDERS.folder(categoryId, folderId);
        return folder == null ? "Unsorted" : folder.name;
    }

    private static String moneyOrDisabled(double value) {
        return value > 0 ? GuiShop.CONFIG.money(value) : "Disabled";
    }

    private static String formatNumber(double value) {
        if (Math.rint(value) == value) return Long.toString((long)value);
        return Double.toString(value);
    }

    private static int clampPage(int page, int pages) {
        return Math.max(1, Math.min(page, pages));
    }

    private static void addPager(
        EditorContainer container,
        int page,
        int pages,
        PageAction action,
        String info
    ) {
        if (page > 1) {
            int target = page - 1;
            container.bind(PREVIOUS_SLOT, displayStack("minecraft:arrow", "Previous Page"),
                () -> action.open(target));
        }
        container.bind(INFO_SLOT, displayStack("minecraft:paper", "Page " + page + "/" + pages),
            () -> ShopMessages.info(container.owner, info));
        if (page < pages) {
            int target = page + 1;
            container.bind(NEXT_SLOT, displayStack("minecraft:arrow", "Next Page"),
                () -> action.open(target));
        }
    }

    private static ItemStack displayStack(String identifier, String name) {
        Item item;
        try {
            item = BuiltInRegistries.ITEM.getValue(Identifier.parse(identifier));
            if (item == null || item == Items.AIR) item = Items.CHEST;
        } catch (Exception ignored) {
            item = Items.CHEST;
        }
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static void openMenu(ServerPlayer player, EditorContainer container, Component title, int rows) {
        player.openMenu(new SimpleMenuProvider(
            (containerId, inventory, ignored) -> new EditorMenu(containerId, inventory, container, rows),
            title
        ));
    }

    @FunctionalInterface
    private interface PageAction {
        void open(int page);
    }

    private record FolderEntry(String folderId, String name, String icon, int itemCount) {}

    private static final class EditorContainer extends SimpleContainer {
        private final Map<Integer, Runnable> actions = new HashMap<>();
        private final ServerPlayer owner;
        private boolean processing;

        EditorContainer(int size, ServerPlayer owner) {
            super(size);
            this.owner = owner;
        }

        void bind(int slot, ItemStack stack, Runnable action) {
            super.setItem(slot, stack.copy());
            actions.put(slot, action);
        }

        void activate(int slot) {
            Runnable action = actions.get(slot);
            if (action == null || processing) return;
            processing = true;
            try {
                action.run();
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

    private static final class EditorMenu extends ChestMenu {
        private final EditorContainer editor;

        EditorMenu(int containerId, Inventory inventory, EditorContainer editor, int rows) {
            super(menuType(rows), containerId, inventory, editor, rows);
            this.editor = editor;
        }

        @Override
        public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
            if (slotIndex >= 0 && slotIndex < editor.getContainerSize()) {
                if (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE) {
                    editor.activate(slotIndex);
                }
                return;
            }
            super.clicked(slotIndex, buttonNum, input, player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            if (index >= 0 && index < editor.getContainerSize()) editor.activate(index);
            return ItemStack.EMPTY;
        }
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
}
