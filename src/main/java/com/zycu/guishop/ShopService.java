package com.zycu.guishop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ShopService {
    public static final int SELL_ALL = -1;

    private ShopService() {}

    public static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static boolean canUseMode(ServerPlayer player, ShopGui.Mode mode) {
        String node = mode == ShopGui.Mode.BUY ? "guishop.buy" : "guishop.sell";
        return ShopPermissions.user(player, node);
    }

    public static boolean canTransact(ServerPlayer player, ShopGui.Mode mode) {
        if (!canUseMode(player, mode)) {
            player.sendSystemMessage(Component.literal("You do not have permission to " + mode.name().toLowerCase() + " items."));
            return false;
        }
        if (!GuiShop.CONFIG.creativeTransactionsAllowed()
            && player.getAbilities().instabuild
            && !ShopPermissions.check(player, "guishop.creative.bypass", 2)) {
            player.sendSystemMessage(Component.literal("Shop transactions are disabled while you are in creative mode."));
            return false;
        }
        return true;
    }

    public static boolean trade(ServerPlayer player, ShopConfig.ShopItem entry, ShopGui.Mode mode, int requestedQuantity) {
        if (!canTransact(player, mode)) return false;

        Item item = resolveItem(entry.item);
        if (item == null) {
            player.sendSystemMessage(Component.literal("Invalid configured item: " + entry.item));
            return false;
        }

        double unitPrice = (mode == ShopGui.Mode.BUY ? entry.buy : entry.sell) * GuiShop.CONFIG.priceMultiplier;
        if (unitPrice <= 0 || !Double.isFinite(unitPrice)) {
            player.sendSystemMessage(Component.literal("That item is not available for " + mode.name().toLowerCase() + "."));
            return false;
        }

        if (mode == ShopGui.Mode.BUY) {
            int quantity = Math.max(1, requestedQuantity);
            double total = round(unitPrice * quantity);
            if (!GuiShop.ECONOMY.withdraw(player.getUUID(), total)) {
                player.sendSystemMessage(Component.literal("You cannot afford " + quantity + "x " + entry.name
                    + " for " + GuiShop.CONFIG.money(total) + "."));
                return false;
            }
            give(player, item, quantity);
            player.sendSystemMessage(Component.literal("Purchased " + quantity + "x " + entry.name
                + " for " + GuiShop.CONFIG.money(total) + ". Balance: "
                + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))));
            return true;
        }

        int available = count(player, item);
        if (available <= 0) {
            player.sendSystemMessage(Component.literal("You do not have any " + entry.name + " to sell."));
            return false;
        }

        int quantity = requestedQuantity == SELL_ALL ? available : Math.min(Math.max(1, requestedQuantity), available);
        double total = round(unitPrice * quantity);
        remove(player, item, quantity);
        GuiShop.ECONOMY.deposit(player.getUUID(), total);
        player.sendSystemMessage(Component.literal("Sold " + quantity + "x " + entry.name
            + " for " + GuiShop.CONFIG.money(total) + ". Balance: "
            + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))));
        return true;
    }

    public static boolean sellHand(ServerPlayer player, int requestedQuantity, boolean allInventory) {
        if (!canTransact(player, ShopGui.Mode.SELL)) return false;

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.sendSystemMessage(Component.literal("You are not holding an item."));
            return false;
        }

        String itemId = itemId(held);
        ShopConfig.FoundItem found = GuiShop.CONFIG.findItem(itemId);
        if (found == null || found.item().sell <= 0) {
            player.sendSystemMessage(Component.literal("That item does not have a sell price."));
            return false;
        }

        int available = allInventory ? count(player, held.getItem()) : held.getCount();
        int quantity = requestedQuantity == SELL_ALL ? available : Math.min(Math.max(1, requestedQuantity), available);
        if (quantity <= 0) return false;

        double unitPrice = found.item().sell * GuiShop.CONFIG.priceMultiplier;
        double total = round(unitPrice * quantity);

        if (allInventory) {
            remove(player, held.getItem(), quantity);
        } else {
            held.shrink(quantity);
            player.getInventory().setChanged();
        }

        GuiShop.ECONOMY.deposit(player.getUUID(), total);
        player.sendSystemMessage(Component.literal("Sold " + quantity + "x " + found.item().name
            + " for " + GuiShop.CONFIG.money(total) + ". Balance: "
            + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))));
        return true;
    }

    public static boolean showWorth(ServerPlayer player, boolean allInventory) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.sendSystemMessage(Component.literal("You are not holding an item."));
            return false;
        }
        return showWorth(player, itemId(held), allInventory ? count(player, held.getItem()) : held.getCount());
    }

    public static boolean showWorth(ServerPlayer player, String itemId, int amount) {
        ShopConfig.FoundItem found = GuiShop.CONFIG.findItem(itemId);
        if (found == null) {
            player.sendSystemMessage(Component.literal("No shop listing exists for " + itemId + "."));
            return false;
        }

        ShopConfig.ShopItem item = found.item();
        double buy = item.buy * GuiShop.CONFIG.priceMultiplier;
        double sell = item.sell * GuiShop.CONFIG.priceMultiplier;
        int quantity = Math.max(1, amount);
        String buyText = buy > 0 ? GuiShop.CONFIG.money(buy) + " each / " + GuiShop.CONFIG.money(round(buy * quantity)) + " total" : "not purchasable";
        String sellText = sell > 0 ? GuiShop.CONFIG.money(sell) + " each / " + GuiShop.CONFIG.money(round(sell * quantity)) + " total" : "not sellable";
        player.sendSystemMessage(Component.literal(item.name + " [" + item.item + "]"));
        player.sendSystemMessage(Component.literal("Buy: " + buyText));
        player.sendSystemMessage(Component.literal("Sell: " + sellText));
        player.sendSystemMessage(Component.literal("Category: " + found.category().name + " | Quantity checked: " + quantity));
        return true;
    }

    public static int count(ServerPlayer player, Item item) {
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    public static void remove(ServerPlayer player, Item item, int quantity) {
        int remaining = quantity;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.is(item)) continue;
            int removed = Math.min(stack.getCount(), remaining);
            stack.shrink(removed);
            remaining -= removed;
            if (stack.isEmpty()) inventory.setItem(slot, ItemStack.EMPTY);
        }
        inventory.setChanged();
    }

    public static void give(ServerPlayer player, Item item, int quantity) {
        int remaining = quantity;
        while (remaining > 0) {
            ItemStack probe = new ItemStack(item);
            int batch = Math.min(remaining, probe.getMaxStackSize());
            ItemStack stack = new ItemStack(item, batch);
            remaining -= batch;
            if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
        }
    }

    private static Item resolveItem(String itemId) {
        try {
            Item item = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(itemId));
            return item == null || BuiltInRegistries.ITEM.getKey(item).toString().equals("minecraft:air") ? null : item;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
