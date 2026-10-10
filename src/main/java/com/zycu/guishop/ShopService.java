package com.zycu.guishop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
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
        try { com.zycu.guishop.admin.AccountOperations.ensureReady(); }
        catch (IllegalStateException pending) { ShopMessages.warning(player, "An account operation needs server recovery. Ask an administrator to check disk space and file permissions."); return false; }
        if (!canUseMode(player, mode)) {
            ShopMessages.error(player, "You do not have permission to " + mode.name().toLowerCase() + " items.");
            return false;
        }
        if (!GuiShop.CONFIG.creativeTransactionsAllowed()
            && player.getAbilities().instabuild
            && !ShopPermissions.check(player, "guishop.creative.bypass", 2)) {
            ShopMessages.warning(player, "Shop transactions are disabled while you are in creative mode.");
            return false;
        }
        return true;
    }

    public static boolean trade(ServerPlayer player, ShopConfig.ShopItem entry, ShopGui.Mode mode, int requestedQuantity) {
        if (!canTransact(player, mode)) return false;
        var current = GuiShop.CONFIG.findItem(entry.listingId);
        if (current == null || current.item() != entry) {
            ShopMessages.warning(player, "The shop was updated. Reopen /shop before trading.");
            return false;
        }
        if (!ShopPermissions.category(player, current.category().id)) {
            ShopMessages.error(player, "You do not have permission to use that category.");
            return false;
        }
        if (!VersionedListingStore.isRuntimeAvailable(entry, player.registryAccess())) {
            ShopMessages.warning(player, "That listing is unavailable in this Minecraft version.");
            return false;
        }

        if (entry.item.equals("minecraft:enchanted_book") && !ShopPermissions.user(player, "guishop.enchant")) { ShopMessages.error(player, "You do not have permission to trade enchanted books."); return false; }
        ItemStack template = entry.createStack(player.registryAccess());
        if (template.isEmpty()) {
            ShopMessages.error(player, "Invalid configured item: " + entry.item);
            return false;
        }

        double unitPrice = (mode == ShopGui.Mode.BUY ? entry.buy : entry.sell);
        if (unitPrice <= 0 || !Double.isFinite(unitPrice)) {
            ShopMessages.warning(player, "That item is not available for " + mode.name().toLowerCase() + ".");
            return false;
        }

        if (mode == ShopGui.Mode.BUY) {
            int quantity = Math.max(1, requestedQuantity);
            double total = round(unitPrice * quantity);
            if (!validTotal(player, total)) return false;
            if (GuiShop.ECONOMY.balance(player.getUUID()) < total) {
                ShopMessages.error(player, "You cannot afford " + quantity + "x " + entry.name + " for " + GuiShop.CONFIG.money(total) + ".");
                return false;
            }
            // Build playable world-dependent maps from the original template, never from menu lore.
            try { template = ShopMaps.prepare(player, template); }
            catch (IllegalArgumentException unavailable) { ShopMessages.warning(player, unavailable.getMessage()); return false; }
            if (!GuiShop.ECONOMY.withdraw(player.getUUID(), total)) {
                ShopMessages.error(player, "You cannot afford " + quantity + "x " + entry.name
                    + " for " + GuiShop.CONFIG.money(total) + ".");
                return false;
            }
            give(player, template, quantity);
            com.zycu.guishop.admin.TransactionJournal.record(player, "BUY", entry.listingId, entry.item, quantity, unitPrice, total, "");
            ShopMessages.success(player, "Purchased " + quantity + "x " + entry.name
                + " for " + GuiShop.CONFIG.money(total) + ". Balance: "
                + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID())));
            return true;
        }

        if (!safeMapSale(player, template, entry)) return false;
        int available = count(player, template);
        if (available <= 0) {
            ShopMessages.warning(player, "You do not have any matching " + entry.name + " to sell.");
            return false;
        }

        int quantity = requestedQuantity == SELL_ALL ? available : Math.min(Math.max(1, requestedQuantity), available);
        double total = round(unitPrice * quantity);
        if (!validTotal(player, total)) return false;
        remove(player, template, quantity);
        GuiShop.ECONOMY.deposit(player.getUUID(), total);
        com.zycu.guishop.admin.TransactionJournal.record(player, "SELL", entry.listingId, entry.item, quantity, unitPrice, total, "");
        ShopMessages.success(player, "Sold " + quantity + "x " + entry.name
            + " for " + GuiShop.CONFIG.money(total) + ". Balance: "
            + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID())));
        return true;
    }

    public static boolean sellHand(ServerPlayer player, int requestedQuantity, boolean allInventory) {
        if (!canTransact(player, ShopGui.Mode.SELL)) return false;

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            ShopMessages.warning(player, "You are not holding an item.");
            return false;
        }

        var quote = SaleValuation.quote(player, held);
        var found = quote.listing();
        if (found == null || quote.unit() <= 0) { ShopMessages.warning(player, quote.note()); return false; }

        ItemStack template = held.copyWithCount(1);
        if (!safeMapSale(player, template, found.item())) return false;
        int available = allInventory ? count(player, template) : held.getCount();
        int quantity = requestedQuantity == SELL_ALL ? available : Math.min(Math.max(1, requestedQuantity), available);
        if (quantity <= 0) return false;

        double unitPrice = quote.unit();
        double total = round(unitPrice * quantity);
        if (!validTotal(player, total)) return false;

        if (allInventory) {
            remove(player, template, quantity);
        } else {
            held.shrink(quantity);
            player.getInventory().setChanged();
        }

        GuiShop.ECONOMY.deposit(player.getUUID(), total);
        com.zycu.guishop.admin.TransactionJournal.recordStack(player, "SELL", found.item().listingId, found.item().item, quantity, unitPrice, total, allInventory ? "Inventory sale" : "Held-item sale", template);
        ShopMessages.success(player, "Sold " + quantity + "x " + found.item().name
            + " for " + GuiShop.CONFIG.money(total) + ". Balance: "
            + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID())));
        return true;
    }

    private static boolean safeMapSale(ServerPlayer player, ItemStack template, ShopConfig.ShopItem entry) {
        if (template.is(net.minecraft.tags.ItemTags.CLONABLE_MAPS)
            && entry.sell > ShopMaps.cheapestEmptyMap(GuiShop.CONFIG, player.registryAccess()) + 0.000001) {
            ShopMessages.warning(player, "This map's sell price would allow a map-cloning profit loop. Ask an administrator to adjust the map prices. No items were sold.");
            return false;
        }
        return true;
    }

    public static boolean showWorth(ServerPlayer player, boolean allInventory) {
        if (allInventory) {
            double total = 0; int count = 0, excluded = 0;
            var inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                var stack = inventory.getItem(slot); if (stack.isEmpty()) continue;
                var quote = SaleValuation.quote(player, stack);
                if (quote.unit() > 0 && safeMapSale(player, stack, quote.listing().item())) { total += round(quote.unit() * stack.getCount());count += stack.getCount(); }
                else excluded += stack.getCount();
            }
            ShopMessages.info(player, "Inventory sell value: " + GuiShop.CONFIG.money(total) + " | " + count + " eligible items | " + excluded + " excluded items"); return true;
        }
        var held = player.getMainHandItem();
        if (held.isEmpty()) { ShopMessages.warning(player, "You are not holding an item."); return false; }
        var quote = SaleValuation.quote(player, held);
        ShopMessages.info(player, held.getHoverName().getString() + " | Sell: " + GuiShop.CONFIG.money(quote.unit()) + " each / " + GuiShop.CONFIG.money(round(quote.unit()*held.getCount())) + " total");
        ShopMessages.info(player, "Base " + GuiShop.CONFIG.money(quote.base()) + " + enchantments " + GuiShop.CONFIG.money(quote.enchantments()) + " | Condition " + String.format(java.util.Locale.ROOT,"%.2f%%",quote.condition()*100));
        if(quote.exact())ShopMessages.info(player,"Buy: "+(quote.listing().item().buy>0?GuiShop.CONFIG.money(quote.listing().item().buy)+" each":"not purchasable"));
        ShopMessages.info(player,quote.note()); return true;
    }

    public static boolean showWorth(ServerPlayer player, String identifier, int amount) {
        ShopConfig.FoundItem found = GuiShop.CONFIG.findItem(identifier);
        if (found == null) {
            ShopMessages.warning(player, "No shop listing exists for " + identifier + ".");
            return false;
        }
        if (!VersionedListingStore.isRuntimeAvailable(found.item(), player.registryAccess())) {
            ShopMessages.warning(player, "That listing is unavailable in this Minecraft version.");
            return false;
        }
        if(!ShopPermissions.category(player,found.category().id)){ShopMessages.warning(player,"That listing is unavailable to you.");return false;}
        return showWorth(player, found, amount);
    }

    private static boolean showWorth(ServerPlayer player, ShopConfig.FoundItem found, int amount) {
        ShopConfig.ShopItem item = found.item();
        double buy = item.buy;
        double sell = item.sell;
        int quantity = Math.max(1, amount);
        String buyText = buy > 0 ? GuiShop.CONFIG.money(buy) + " each / " + GuiShop.CONFIG.money(round(buy * quantity)) + " total" : "not purchasable";
        String sellText = sell > 0 ? GuiShop.CONFIG.money(sell) + " each / " + GuiShop.CONFIG.money(round(sell * quantity)) + " total" : "not sellable";
        ShopMessages.info(player, item.name + " [" + item.listingId + "]");
        ShopMessages.info(player, "Buy: " + buyText);
        ShopMessages.info(player, "Sell: " + sellText);
        ShopMessages.info(player, "Category: " + found.category().name + " | Quantity checked: " + quantity);
        return true;
    }

    private static ShopConfig.FoundItem findSellable(ServerPlayer player, ItemStack held) {
        ShopConfig.FoundItem best = null;
        for (var category : GuiShop.CONFIG.categories) {
            if (!ShopPermissions.category(player, category.id)) continue;
            for (var item : category.items) {
                if (!(item.sell > 0) || !Double.isFinite(item.sell)
                    || !VersionedListingStore.isRuntimeAvailable(item, player.registryAccess())) continue;
                if (ShopMaps.matches(item.createStack(player.registryAccess()), held)
                    && (best == null || item.sell > best.item().sell)) best = new ShopConfig.FoundItem(category, item);
            }
        }
        return best;
    }

    public static int count(ServerPlayer player, ItemStack template) {
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ShopMaps.matches(template, stack)) count += stack.getCount();
        }
        return count;
    }

    public static boolean validTotal(ServerPlayer player, double total) {
        if (Double.isFinite(total) && total >= 0.01 && total <= 1_000_000_000_000.0) return true;
        ShopMessages.warning(player, "That transaction has a zero or invalid total. Ask an admin to check the price.");
        return false;
    }

    public static void remove(ServerPlayer player, ItemStack template, int quantity) {
        int remaining = quantity;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!ShopMaps.matches(template, stack)) continue;
            int removed = Math.min(stack.getCount(), remaining);
            stack.shrink(removed);
            remaining -= removed;
            if (stack.isEmpty()) inventory.setItem(slot, ItemStack.EMPTY);
        }
        inventory.setChanged();
    }

    public static void give(ServerPlayer player, ItemStack template, int quantity) {
        int remaining = quantity;
        while (remaining > 0) {
            int batch = Math.min(remaining, template.getMaxStackSize());
            ItemStack stack = template.copyWithCount(batch);
            remaining -= batch;
            if (!player.getInventory().add(stack) && !stack.isEmpty()) PlayerItemDrop.drop(player, stack);
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
