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
    private static final Map<java.util.UUID, ServerPlayer> SYNC = new HashMap<>();

    private HybridShopGui() {}

    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Vanilla clients predict chest pickup. Reassert real inventory/cursor after packet processing.
            for (var player : SYNC.values()) if (server.getPlayerList().getPlayer(player.getUUID()) == player) player.containerMenu.sendAllDataToRemote();
            SYNC.clear();
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> SYNC.clear());
    }

    /** Legacy mode arguments are compatibility aliases for the same unified shop. */
    public static void openCategories(ServerPlayer player, ShopGui.Mode ignoredMode) { openCategories(player, 1); }

    private static boolean allowed(ServerPlayer player) {
        if (ShopService.canUseMode(player, ShopGui.Mode.BUY) || ShopService.canUseMode(player, ShopGui.Mode.SELL)) return true;
        ShopMessages.error(player, "You do not have permission to use the shop."); return false;
    }
    private static void openCategories(ServerPlayer player, int requestedPage) {
        GuiShop.PLAYERS.remember(player); if (!allowed(player)) return;
        List<MenuButton> buttons = new ArrayList<>();
        for (var category : GuiShop.CONFIG.categories) {
            if (!ShopPermissions.category(player, category.id) || availableItems(player, category).isEmpty()) continue;
            buttons.add(new MenuButton(ShopIcons.category(category, player.registryAccess(), category.name),
                () -> openCategory(player, category.id, ShopGui.Mode.BUY, 1)));
        }
        int pages = Math.max(1, (buttons.size() + CategoryLayout.PAGE_SIZE - 1) / CategoryLayout.PAGE_SIZE);
        int page = clampPage(requestedPage, pages), offset = (page - 1) * CategoryLayout.PAGE_SIZE;
        int shown = Math.min(CategoryLayout.PAGE_SIZE, buttons.size() - offset), rows = CategoryLayout.rows(shown);
        var container = new ShopContainer(rows * 9, player); container.beginRender();
        var slots = CategoryLayout.slots(shown);
        for (int index = 0; index < shown; index++) {
            var button = buttons.get(offset + index); container.bind(slots.get(index), button.icon, false, ignored -> button.action.run());
        }
        int bottom = (rows - 1) * 9;
        if (page > 1) container.bind(bottom, displayStack("minecraft:arrow", "Previous category page"), false, ignored -> openCategories(player, page - 1));
        if (page < pages) container.bind(bottom + 7, displayStack("minecraft:arrow", "Next category page"), false, ignored -> openCategories(player, page + 1));
        container.bind(bottom + 4, displayStack("minecraft:paper", "Balance: " + money(player), "Left-click buys | Right-click sells"), false,
            ignored -> ShopMessages.info(player, "Balance: " + money(player)));
        container.endRender(); openMenu(player, container, Component.literal(GuiShop.CONFIG.shopTitle), rows);
    }
    public static void openCategory(ServerPlayer player, String categoryId, ShopGui.Mode ignoredMode, int requestedPage) {
        var category = GuiShop.CONFIG.category(categoryId);
        if (category == null || !ShopPermissions.category(player, categoryId)) { ShopMessages.error(player, "That shop category is unavailable."); return; }
        if (!allowed(player)) return;
        List<ShopConfig.ShopItem> items = availableItems(player, category);
        var session = new PageSession(player, Component.literal(category.name), (screen, requested) -> {
            int pages = pageCount(items.size()), page = clampPage(requested, pages), start = (page - 1) * PAGE_SIZE;
            screen.begin();
            for (int slot = 0; slot < PAGE_SIZE && start + slot < items.size(); slot++) {
                var entry = items.get(start + slot);
                var icon = ShopIcons.listing(entry.createStack(player.registryAccess()), entry.name, entry.description,
                    entry.buy, entry.sell, GuiShop.CONFIG, true);
                screen.trade(slot, icon, (side, quantity) -> { if (ShopService.trade(player, entry, side, quantity)) screen.show(page); });
            }
            screen.bind(BACK_SLOT, displayStack("minecraft:barrier", "Back"), false, ignored -> openCategories(player, 1));
            screen.pageControls(page, pages, "Page " + page + "/" + pages + " | Balance " + money(player), screen::show);
            screen.finish();
        });
        session.open(requestedPage);
    }
    public static void openEnchantments(ServerPlayer player, int page) { openEnchantments(player, ShopGui.Mode.BUY, page); }
    public static void openEnchantments(ServerPlayer player, ShopGui.Mode ignoredMode, int requestedPage) {
        if (!allowed(player) || !ShopPermissions.user(player, "guishop.enchant")) {
            ShopMessages.warning(player, "The enchanted book shop is unavailable."); return;
        }
        var session = new PageSession(player, Component.literal("Enchanted Books"), (screen, requested) -> {
            var values = EnchantmentShopService.availableEnchantments(player, ShopGui.Mode.BUY);
            int pages = pageCount(values.size()), page = clampPage(requested, pages), start = (page - 1) * PAGE_SIZE;
            screen.begin();
            for (int slot = 0; slot < PAGE_SIZE && start + slot < values.size(); slot++) {
                var offer = values.get(start + slot);
                var icon = ShopIcons.display(EnchantmentShopService.createBook(offer.holder(), 1), offer.displayName(), "Levels 1-" + offer.maxLevel() + " | Click to choose a level");
                icon.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
                    Component.literal("Buy: " + GuiShop.CONFIG.money(offer.firstLevelCost()) + " - " + GuiShop.CONFIG.money(offer.maximumLevelCost())).withStyle(net.minecraft.ChatFormatting.GOLD),
                    Component.literal("Sell: " + GuiShop.CONFIG.money(EnchantmentShopService.cost(player, offer.enchantmentId(), 1, ShopGui.Mode.SELL)) + " - " + GuiShop.CONFIG.money(EnchantmentShopService.cost(player, offer.enchantmentId(), offer.maxLevel(), ShopGui.Mode.SELL))).withStyle(net.minecraft.ChatFormatting.GREEN),
                    Component.literal("Click to choose a level").withStyle(net.minecraft.ChatFormatting.GRAY))));
                screen.bind(slot, icon, false, ignored -> openEnchantmentLevels(player, offer.enchantmentId(), 1, page));
            }
            screen.bind(BACK_SLOT, displayStack("minecraft:barrier", "Back to Shop"), false, ignored -> openCategories(player, 1));
            screen.pageControls(page, pages, "Page " + page + "/" + pages + " | Balance " + money(player), screen::show); screen.finish();
        }); session.open(requestedPage);
    }
    private static void openEnchantmentLevels(ServerPlayer player, String id, int requestedPage, int parentPage) {
        var initial = EnchantmentShopService.availableLevels(player, id, ShopGui.Mode.BUY);
        if (initial.isEmpty()) { openEnchantments(player, ShopGui.Mode.BUY, parentPage); return; }
        var session = new PageSession(player, Component.literal(initial.getFirst().displayName() + " Levels"), (screen, requested) -> {
            var offers = EnchantmentShopService.availableLevels(player, id, ShopGui.Mode.BUY);
            int pages = pageCount(offers.size()), page = clampPage(requested, pages), start = (page - 1) * PAGE_SIZE;
            var configured = GuiShop.CONFIG.enchantmentOffer(id);
            screen.begin();
            for (int slot = 0; slot < PAGE_SIZE && start + slot < offers.size(); slot++) {
                var offer = offers.get(start + slot);
                double sell = EnchantmentShopService.cost(player, id, offer.targetLevel(), ShopGui.Mode.SELL);
                var icon = ShopIcons.listing(EnchantmentShopService.createBook(offer.holder(), offer.targetLevel()),
                    offer.displayName() + " " + EnchantmentShopService.roman(offer.targetLevel()), "", offer.cost(), sell, GuiShop.CONFIG, false);
                screen.trade(slot, icon, (side, quantity) -> {
                    if (false) { ShopMessages.warning(player, "The shop was updated. Reopen /shop before trading."); return; }
                    boolean success = side == ShopGui.Mode.BUY ? EnchantmentShopService.purchase(player, id, offer.targetLevel())
                        : EnchantmentShopService.sell(player, id, offer.targetLevel(), quantity);
                    if (success) screen.show(page);
                });
            }
            screen.bind(BACK_SLOT, displayStack("minecraft:barrier", "Back to Enchantments"), false, ignored -> openEnchantments(player, ShopGui.Mode.BUY, parentPage));
            screen.pageControls(page, pages, "Page " + page + "/" + pages + " | Balance " + money(player), screen::show); screen.finish();
        }); session.open(requestedPage);
    }
    private static List<ShopConfig.ShopItem> availableItems(ServerPlayer player, ShopConfig.Category category) {
        return category.items.stream().filter(item -> (item.buy > 0 && ShopService.canUseMode(player, ShopGui.Mode.BUY)
            || item.sell > 0 && ShopService.canUseMode(player, ShopGui.Mode.SELL)) && VersionedListingStore.isRuntimeAvailable(item, player.registryAccess())).toList();
    }
    private static String money(ServerPlayer player) { return GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID())); }
    private static int pageCount(int size) { return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE); }
    private static int clampPage(int page, int pages) { return Math.max(1, Math.min(page, pages)); }
    private static ItemStack displayStack(String id, String name) { return displayStack(id, name, ""); }
    private static ItemStack displayStack(String id, String name, String detail) {
        return ShopIcons.display(ItemStackData.decode(null, id, net.minecraft.core.RegistryAccess.EMPTY), name, detail);
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
    private interface PageAction {
        void open(int page);
    }

    @FunctionalInterface
    private interface PageRenderer {
        void render(PageSession screen, int requestedPage);
    }

    private record MenuButton(ItemStack icon, Runnable action) {}
    private record Binding(GuiAction action, TradeAction transaction, ShopConfig revision) {}
    @FunctionalInterface private interface TradeAction { void run(ShopGui.Mode side, int quantity); }

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

        private void trade(int slot, ItemStack stack, TradeAction action) {
            container.trade(slot, stack, action);
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
            pendingBindings.put(slot, new Binding(action, null, null));
        }

        private void trade(int slot, ItemStack stack, TradeAction action) {
            pending[slot] = stack.copy();
            pendingBindings.put(slot, new Binding(null, action, GuiShop.CONFIG));
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

        private void activate(int slot, int button, boolean shift) {
            Binding binding = bindings.get(slot);
            if (binding == null || processing || button < 0 || button > 1) return;
            processing = true;
            try {
                if (binding.transaction == null) binding.action.run(1);
                else if (binding.revision != GuiShop.CONFIG) ShopMessages.warning(owner, "The shop was updated. Reopen /shop before trading.");
                else {
                    ShopGui.Mode side = button == 1 ? ShopGui.Mode.SELL : ShopGui.Mode.BUY;
                    binding.transaction.run(side, shift ? (side == ShopGui.Mode.SELL ? ShopService.SELL_ALL : 64) : 1);
                }
            } finally { processing = false; }
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
                    shop.activate(slotIndex, buttonNum, false);
                } else if (input == ContainerInput.QUICK_MOVE) {
                    shop.activate(slotIndex, buttonNum, true);
                }
                SYNC.put(shop.owner.getUUID(), shop.owner);
                return;
            }
            super.clicked(slotIndex, buttonNum, input, player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            if (index >= 0 && index < shop.getContainerSize()) {
                shop.activate(index, 0, true);
                SYNC.put(shop.owner.getUUID(), shop.owner);
            }
            return ItemStack.EMPTY;
        }
    }
}
