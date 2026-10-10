package com.zycu.guishop;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import java.util.*;

/** Commands share one conservative quote. Menu sales continue to use exact matching. */
public final class SaleValuation {
    private SaleValuation() {}
    public record Quote(ShopConfig.FoundItem listing, double base, double enchantments, double condition, double unit, String note, boolean exact) {}
    private static List<ShopConfig.FoundItem> listings(ServerPlayer player) {
        var result = new ArrayList<ShopConfig.FoundItem>();
        for (var category : GuiShop.CONFIG.categories) if (ShopPermissions.category(player, category.id))
            for (var item : category.items) if (VersionedListingStore.isRuntimeAvailable(item, player.registryAccess())
                && (!item.item.equals("minecraft:enchanted_book") || ShopPermissions.user(player, "guishop.enchant"))) result.add(new ShopConfig.FoundItem(category, item));
        return result;
    }
    public static Quote quote(ServerPlayer player, ItemStack held) {
        if (held.isEmpty() || !ShopService.canUseMode(player, ShopGui.Mode.SELL)) return new Quote(null, 0, 0, 1, 0, "No sell permission or empty item", false);
        var entries = listings(player);
        ShopConfig.FoundItem exact = null;
        for (var found : entries) if (ShopMaps.matches(found.item().createStack(player.registryAccess()), held)
            && (exact == null || found.item().sell > exact.item().sell)) exact = found;
        if (exact != null) return new Quote(exact, exact.item().sell, 0, 1, exact.item().sell, "Exact listing price", true);
        var enchants = held.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        boolean enchanted = !enchants.isEmpty(), damaged = held.isDamaged();
        if (damaged && !GuiShop.CONFIG.sellDamagedItems || enchanted && !GuiShop.CONFIG.sellEnchantedEquipment)
            return new Quote(null, 0, 0, 1, 0, damaged && !GuiShop.CONFIG.sellDamagedItems ? "Damaged item selling is disabled" : "Enchanted equipment selling is disabled", false);
        if (!damaged && !enchanted || enchanted && !held.isDamageableItem()) return new Quote(null, 0, 0, 1, 0, "No exact listing; custom components are preserved", false);
        var normalized = normalized(held);
        ShopConfig.FoundItem base = null;
        for (var found : entries) {
            var template = found.item().createStack(player.registryAccess());
            if (template.isDamaged() || !template.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()) continue;
            if (ItemStackData.same(normalized(template), normalized) && (base == null || found.item().sell > base.item().sell)) base = found;
        }
        if (base == null || base.item().sell <= 0) return new Quote(base, 0, 0, 1, 0, "Base item has no enabled sell price", false);
        double bonus = 0; var unpriced = new ArrayList<String>();
        for (var enchant : enchants.entrySet()) {
            var book = EnchantmentShopService.createBook(enchant.getKey(), enchant.getIntValue());
            double value = 0;
            for (var found : entries) if (found.item().sell > value && ItemStackData.same(book, found.item().createStack(player.registryAccess()))) value = found.item().sell;
            bonus += value;
            if (value == 0) unpriced.add(enchant.getKey().value().description().getString() + " " + enchant.getIntValue());
        }
        double condition = held.isDamageableItem() ? Math.max(0, (held.getMaxDamage() - held.getDamageValue()) / (double)held.getMaxDamage()) : 1;
        return new Quote(base, base.item().sell, bonus, condition, floor((base.item().sell + bonus) * condition), unpriced.isEmpty() ? "Base + priced enchantments, adjusted for durability" : "Unpriced enchantments add 0: " + String.join(", ", unpriced), false);
    }
    private static ItemStack normalized(ItemStack stack) {
        var result = stack.copyWithCount(1);
        if (result.isDamageableItem()) result.set(DataComponents.DAMAGE, 0);
        result.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        return result;
    }
    public static double floor(double value) { return Math.max(0, Math.floor((value + .00000001) * 100) / 100.0); }
}
