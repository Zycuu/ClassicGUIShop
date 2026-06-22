package com.zycu.guishop;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class EnchantmentShopService {
    private EnchantmentShopService() {}

    public static List<EnchantmentView> availableEnchantments(ServerPlayer player) {
        GuiShop.CONFIG.ensureEnchantmentDefaults(player.level().getServer());
        Registry<Enchantment> registry = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        List<EnchantmentView> enchantments = new ArrayList<>();

        for (Map.Entry<ResourceKey<Enchantment>, Enchantment> entry : registry.entrySet()) {
            String id = entry.getKey().identifier().toString();
            ShopConfig.EnchantmentOffer configured = GuiShop.CONFIG.enchantmentOffer(id);
            if (configured == null || !configured.enabled()) continue;

            Holder<Enchantment> holder = registry.getOrThrow(entry.getKey());
            int maxLevel = maximumLevel(holder.value(), configured);
            if (maxLevel < 1) continue;

            double firstLevelCost = price(configured, 1);
            double maximumLevelCost = price(configured, maxLevel);
            enchantments.add(new EnchantmentView(
                id,
                configured.name,
                holder,
                maxLevel,
                firstLevelCost,
                maximumLevelCost
            ));
        }

        enchantments.sort(Comparator.comparing(EnchantmentView::displayName, String.CASE_INSENSITIVE_ORDER));
        return enchantments;
    }

    public static List<OfferView> availableLevels(ServerPlayer player, String enchantmentId) {
        String id = ShopConfig.normalizeIdentifier(enchantmentId);
        ShopConfig.EnchantmentOffer configured = GuiShop.CONFIG.enchantmentOffer(id);
        if (configured == null || !configured.enabled()) return List.of();

        Registry<Enchantment> registry = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> holder;
        try {
            ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(id));
            holder = registry.getOrThrow(key);
        } catch (Exception exception) {
            return List.of();
        }

        int maxLevel = maximumLevel(holder.value(), configured);
        List<OfferView> offers = new ArrayList<>();
        for (int level = 1; level <= maxLevel; level++) {
            offers.add(new OfferView(id, configured.name, holder, level, price(configured, level)));
        }
        return offers;
    }

    public static ItemStack createBook(Holder<Enchantment> enchantment, int level) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.enchant(enchantment, level);
        return book;
    }

    public static boolean purchase(ServerPlayer player, String enchantmentId, int targetLevel) {
        if (!ShopPermissions.user(player, "guishop.enchant")) {
            ShopMessages.error(player, "You do not have permission to buy enchanted books.");
            return false;
        }
        if (!GuiShop.CONFIG.enchantmentsEnabled()) {
            ShopMessages.warning(player, "The enchanted book shop is disabled.");
            return false;
        }
        if (!GuiShop.CONFIG.creativeTransactionsAllowed()
            && player.getAbilities().instabuild
            && !ShopPermissions.check(player, "guishop.creative.bypass", 2)) {
            ShopMessages.warning(player, "Shop transactions are disabled while you are in creative mode.");
            return false;
        }

        String id = ShopConfig.normalizeIdentifier(enchantmentId);
        ShopConfig.EnchantmentOffer configured = GuiShop.CONFIG.enchantmentOffer(id);
        if (configured == null || !configured.enabled()) {
            ShopMessages.error(player, "That enchanted book is not for sale.");
            return false;
        }

        Registry<Enchantment> registry = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> holder;
        try {
            ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(id));
            holder = registry.getOrThrow(key);
        } catch (Exception exception) {
            ShopMessages.error(player, "Unknown enchantment: " + id);
            return false;
        }

        int maxLevel = maximumLevel(holder.value(), configured);
        if (targetLevel < 1 || targetLevel > maxLevel) {
            ShopMessages.error(player, "That enchantment level is unavailable.");
            return false;
        }

        double cost = price(configured, targetLevel);
        if (!GuiShop.ECONOMY.withdraw(player.getUUID(), cost)) {
            ShopMessages.error(player, "You cannot afford " + GuiShop.CONFIG.money(cost) + ".");
            return false;
        }

        ShopService.give(player, createBook(holder, targetLevel), 1);
        ShopMessages.success(player, "Purchased " + configured.name + " " + roman(targetLevel)
            + " for " + GuiShop.CONFIG.money(cost) + ". Balance: "
            + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID())));
        return true;
    }

    public static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> Integer.toString(value);
        };
    }

    private static int maximumLevel(Enchantment enchantment, ShopConfig.EnchantmentOffer configured) {
        int configuredMax = configured.maxLevel <= 0 ? enchantment.getMaxLevel() : configured.maxLevel;
        return Math.min(configuredMax, enchantment.getMaxLevel());
    }

    private static double price(ShopConfig.EnchantmentOffer configured, int level) {
        return round(configured.pricePerLevel * level * GuiShop.CONFIG.priceMultiplier);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record EnchantmentView(
        String enchantmentId,
        String displayName,
        Holder<Enchantment> holder,
        int maxLevel,
        double firstLevelCost,
        double maximumLevelCost
    ) {}

    public record OfferView(
        String enchantmentId,
        String displayName,
        Holder<Enchantment> holder,
        int targetLevel,
        double cost
    ) {}
}
