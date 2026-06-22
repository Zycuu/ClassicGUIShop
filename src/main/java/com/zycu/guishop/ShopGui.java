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

    public static void openCategories(ServerPlayer player, Mode mode) {
        List<ShopConfig.Category> availableCategories = new ArrayList<>();
        for (ShopConfig.Category category : GuiShop.CONFIG.categories) {
            if (hasAvailableItems(category, mode)) {
                availableCategories.add(category);
            }
        }

        int rows = categoryRows(availableCategories.size());
        int size = rows * 9;
        ShopContainer container = new ShopContainer(size, player);

        int contentRows = Math.max(1, rows - 1);
        int maxShown = contentRows * 7;
        int shown = Math.min(maxShown, availableCategories.size());
        int index = 0;

        for (int row = 0; row < contentRows && index < shown; row++) {
            int remaining = shown - index;
            int inThisRow = Math.min(7, remaining);
            int startColumn = 1 + (7 - inThisRow) / 2;

            for (int columnOffset = 0; columnOffset < inThisRow; columnOffset++) {
                ShopConfig.Category category = availableCategories.get(index++);
                int slot = row * 9 + startColumn + columnOffset;
                ItemStack icon = displayStack(category.icon, 1, category.name + " | " + modeName(mode));
                final String categoryId = category.id;
                container.bind(slot, icon, false, quantity -> openCategory(player, categoryId, mode, 1));
            }
        }

        int bottomRowStart = (rows - 1) * 9;
        int categoryBalanceSlot = bottomRowStart + 4;
        int categoryModeSlot = bottomRowStart + 8;

        container.bind(categoryBalanceSlot,
            displayStack("minecraft:paper", 1, "Balance: " + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))),
            false,
            quantity -> player.sendSystemMessage(Component.literal("Balance: " + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))))
        );

        container.bind(categoryModeSlot,
            displayStack(mode == Mode.BUY ? "minecraft:gold_ingot" : "minecraft:emerald", 1,
                "Switch to " + modeName(mode.opposite())),
            false,
            quantity -> openCategories(player, mode.opposite())
        );

        openMenu(player, container, Component.literal("ClassicGUIShop - " + modeName(mode)), rows);
    }

    public static void openCategory(ServerPlayer player, String categoryId, Mode mode, int requestedPage) {
        ShopConfig.Category category = GuiShop.CONFIG.category(categoryId);
        if (category == null) {
            player.sendSystemMessage(Component.literal("That shop category no longer exists."));
            return;
        }

        List<ShopConfig.ShopItem> available = new ArrayList<>();
        for (ShopConfig.ShopItem entry : category.items) {
            if (price(entry, mode) > 0) available.add(entry);
        }

        int pages = Math.max(1, (available.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(1, Math.min(requestedPage, pages));
        ShopContainer container = new ShopContainer(54, player);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, available.size());

        for (int slot = 0; start + slot < end; slot++) {
            ShopConfig.ShopItem entry = available.get(start + slot);
            double unitPrice = price(entry, mode) * GuiShop.CONFIG.priceMultiplier;
            String label = entry.name + " | " + modeName(mode) + " " + GuiShop.CONFIG.money(unitPrice) + " each";
            ItemStack icon = displayStack(entry.item, 1, label);
            container.bind(slot, icon, true, quantity -> trade(player, entry, mode, quantity));
        }

        container.bind(BACK_SLOT,
            displayStack("minecraft:barrier", 1, "Back to Categories"),
            false,
            quantity -> openCategories(player, mode)
        );

        if (page > 1) {
            int target = page - 1;
            container.bind(PREVIOUS_SLOT,
                displayStack("minecraft:arrow", 1, "Previous Page"),
                false,
                quantity -> openCategory(player, categoryId, mode, target)
            );
        }

        container.bind(BALANCE_SLOT,
            displayStack("minecraft:paper", 1,
                "Page " + page + "/" + pages + " | Balance " + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))),
            false,
            quantity -> player.sendSystemMessage(Component.literal(
                "Page " + page + " of " + pages + ". Balance: " + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))
            ))
        );

        if (page < pages) {
            int target = page + 1;
            container.bind(NEXT_SLOT,
                displayStack("minecraft:arrow", 1, "Next Page"),
                false,
                quantity -> openCategory(player, categoryId, mode, target)
            );
        }

        container.bind(MODE_SLOT,
            displayStack(mode == Mode.BUY ? "minecraft:gold_ingot" : "minecraft:emerald", 1,
                "Switch to " + modeName(mode.opposite())),
            false,
            quantity -> openCategory(player, categoryId, mode.opposite(), 1)
        );

        openMenu(player, container, Component.literal(category.name + " - " + modeName(mode) + " " + page + "/" + pages), 6);
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

    private static boolean hasAvailableItems(ShopConfig.Category category, Mode mode) {
        for (ShopConfig.ShopItem item : category.items) {
            if (price(item, mode) > 0) return true;
        }
        return false;
    }

    private static double price(ShopConfig.ShopItem item, Mode mode) {
        return mode == Mode.BUY ? item.buy : item.sell;
    }

    private static String modeName(Mode mode) {
        return mode == Mode.BUY ? "Buy" : "Sell";
    }

    private static void trade(ServerPlayer player, ShopConfig.ShopItem entry, Mode mode, int quantity) {
        Item item = resolveItem(entry.item);
        if (item == Items.AIR) {
            player.sendSystemMessage(Component.literal("Invalid configured item: " + entry.item));
            return;
        }

        quantity = switch (quantity) {
            case 16 -> 16;
            case 64 -> 64;
            default -> 1;
        };

        double unitPrice = price(entry, mode) * GuiShop.CONFIG.priceMultiplier;
        double total = Math.round(unitPrice * quantity * 100.0) / 100.0;
        if (unitPrice <= 0 || total <= 0) return;

        if (mode == Mode.BUY) {
            if (!GuiShop.ECONOMY.withdraw(player.getUUID(), total)) {
                player.sendSystemMessage(Component.literal("You cannot afford " + quantity + "x " + entry.name
                    + " for " + GuiShop.CONFIG.money(total) + "."));
                return;
            }

            give(player, item, quantity);
            player.sendSystemMessage(Component.literal("Purchased " + quantity + "x " + entry.name
                + " for " + GuiShop.CONFIG.money(total) + ". Balance: "
                + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))));
        } else {
            int available = count(player, item);
            if (available < quantity) {
                player.sendSystemMessage(Component.literal("You need " + quantity + "x " + entry.name
                    + " but only have " + available + "."));
                return;
            }

            remove(player, item, quantity);
            GuiShop.ECONOMY.deposit(player.getUUID(), total);
            player.sendSystemMessage(Component.literal("Sold " + quantity + "x " + entry.name
                + " for " + GuiShop.CONFIG.money(total) + ". Balance: "
                + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))));
        }
    }

    private static void give(ServerPlayer player, Item item, int quantity) {
        int remaining = quantity;
        while (remaining > 0) {
            ItemStack probe = new ItemStack(item);
            int batch = Math.min(remaining, probe.getMaxStackSize());
            ItemStack stack = new ItemStack(item, batch);
            remaining -= batch;
            if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
    }

    private static int count(ServerPlayer player, Item item) {
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void remove(ServerPlayer player, Item item, int quantity) {
        int remaining = quantity;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(item)) continue;
            int removed = Math.min(stack.getCount(), remaining);
            stack.shrink(removed);
            remaining -= removed;
            if (stack.isEmpty()) inventory.setItem(slot, ItemStack.EMPTY);
        }
        inventory.setChanged();
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

        void activate(int slot, int quantity, boolean shiftClick) {
            Binding binding = bindings.get(slot);
            if (binding == null || processing) return;
            processing = true;
            try {
                if (!binding.trade()) {
                    binding.action().run(1);
                } else if (shiftClick) {
                    binding.action().run(64);
                } else if (quantity >= 16) {
                    binding.action().run(16);
                } else {
                    binding.action().run(1);
                }
            } finally {
                processing = false;
            }
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            // Vanilla routes right-click removal through this method.
            activate(slot, 16, false);
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            activate(slot, 1, false);
            return ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            // The GUI is a read-only server menu. Player clicks never replace its display stacks.
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
        public ItemStack quickMoveStack(Player player, int index) {
            if (index >= 0 && index < shop.getContainerSize()) {
                shop.activate(index, 64, true);
            }
            return ItemStack.EMPTY;
        }
    }
}
