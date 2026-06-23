package com.zycu.guishop;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

public final class ImportPreviewGui {
    private static final int PAGE_SIZE = 45;
    private static final int CLOSE_SLOT = 45;
    private static final int PREVIOUS_SLOT = 48;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 50;

    private ImportPreviewGui() {}

    public static boolean open(ServerPlayer player, String categoryId, int requestedPage) {
        ShopConfig.Category category = GuiShop.CONFIG.category(categoryId);
        if (category == null) {
            ShopMessages.error(player, "Unknown category: " + categoryId);
            return false;
        }
        if (category.items.isEmpty()) {
            ShopMessages.warning(player, "Category " + category.id + " has no imported listings to preview.");
            return false;
        }

        int pages = Math.max(1, (category.items.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(1, Math.min(requestedPage, pages));
        PreviewContainer container = new PreviewContainer(player);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, category.items.size());

        for (int slot = 0; start + slot < end; slot++) {
            ShopConfig.ShopItem listing = category.items.get(start + slot);
            ItemStack icon = listing.createStack(player.registryAccess());
            if (icon.isEmpty()) icon = new ItemStack(Items.BARRIER);
            else icon = icon.copyWithCount(1);

            String pricing;
            if (listing.buy <= 0 && listing.sell <= 0) {
                pricing = "UNPRICED - hidden from player shops";
            } else {
                String buy = listing.buy > 0 ? GuiShop.CONFIG.money(listing.buy) : "disabled";
                String sell = listing.sell > 0 ? GuiShop.CONFIG.money(listing.sell) : "disabled";
                pricing = "Buy " + buy + " | Sell " + sell;
            }
            icon.set(DataComponents.CUSTOM_NAME, Component.literal(listing.name + " | " + pricing));

            container.bind(slot, icon, () -> {
                ShopMessages.info(player, "Listing ID: " + listing.listingId);
                ShopMessages.info(player, "Item ID: " + listing.item);
                ShopMessages.info(player, "Buy: " + (listing.buy > 0 ? GuiShop.CONFIG.money(listing.buy) : "disabled")
                    + " | Sell: " + (listing.sell > 0 ? GuiShop.CONFIG.money(listing.sell) : "disabled"));
            });
        }

        container.bind(CLOSE_SLOT, named(new ItemStack(Items.BARRIER), "Close Preview"), player::closeContainer);
        if (page > 1) {
            int target = page - 1;
            container.bind(PREVIOUS_SLOT, named(new ItemStack(Items.ARROW), "Previous Page"),
                () -> open(player, categoryId, target));
        }
        container.bind(INFO_SLOT, named(new ItemStack(Items.PAPER),
            category.name + " | " + category.items.size() + " listings | Page " + page + "/" + pages),
            () -> ShopMessages.info(player, "Use /adminshop import price " + category.id
                + " <buy> <sell> to bulk-enable these listings, or price individual listing IDs."));
        if (page < pages) {
            int target = page + 1;
            container.bind(NEXT_SLOT, named(new ItemStack(Items.ARROW), "Next Page"),
                () -> open(player, categoryId, target));
        }

        player.openMenu(new SimpleMenuProvider(
            (containerId, inventory, ignored) -> new PreviewMenu(containerId, inventory, container),
            Component.literal("Import Preview - " + category.name + " " + page + "/" + pages)
        ));
        return true;
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static final class PreviewContainer extends SimpleContainer {
        private final Map<Integer, Runnable> actions = new HashMap<>();
        private final ServerPlayer owner;
        private boolean processing;

        PreviewContainer(ServerPlayer owner) {
            super(54);
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
            // Read-only administrator preview.
        }

        @Override
        public boolean stillValid(Player player) {
            return player == owner && owner.isAlive();
        }
    }

    private static final class PreviewMenu extends ChestMenu {
        private final PreviewContainer preview;

        PreviewMenu(int containerId, Inventory inventory, PreviewContainer preview) {
            super(MenuType.GENERIC_9x6, containerId, inventory, preview, 6);
            this.preview = preview;
        }

        @Override
        public void clicked(int slotIndex, int buttonNum, ContainerInput containerInput, Player player) {
            if (slotIndex >= 0 && slotIndex < preview.getContainerSize()) {
                if (containerInput == ContainerInput.PICKUP || containerInput == ContainerInput.QUICK_MOVE) {
                    preview.activate(slotIndex);
                }
                return;
            }
            super.clicked(slotIndex, buttonNum, containerInput, player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            if (index >= 0 && index < preview.getContainerSize()) preview.activate(index);
            return ItemStack.EMPTY;
        }
    }
}
