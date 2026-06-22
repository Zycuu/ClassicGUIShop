package com.zycu.guishop;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EnchantmentShopService {
    private EnchantmentShopService() {}

    public static List<OfferView> availableOffers(ServerPlayer player) {
        GuiShop.CONFIG.ensureEnchantmentDefaults(player.getServer());
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return List.of();

        Registry<Enchantment> registry = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        List<OfferView> offers = new ArrayList<>();

        for (Map.Entry<ResourceKey<Enchantment>, Enchantment> entry : registry.entrySet()) {
            String id = entry.getKey().identifier().toString();
            ShopConfig.EnchantmentOffer configured = GuiShop.CONFIG.enchantmentOffer(id);
            if (configured == null || !configured.enabled()) continue;

            Holder<Enchantment> holder = registry.getOrThrow(entry.getKey());
            Enchantment enchantment = holder.value();
            if (!enchantment.canEnchant(held)) continue;

            int currentLevel = held.getEnchantments().getLevel(holder);
            Set<Holder<Enchantment>> existing = new HashSet<>(EnchantmentHelper.getEnchantmentsForCrafting(held).keySet());
            existing.remove(holder);
            if (!EnchantmentHelper.isEnchantmentCompatible(existing, holder)) continue;

            int configuredMax = configured.maxLevel <= 0 ? enchantment.getMaxLevel() : configured.maxLevel;
            int maxLevel = Math.min(configuredMax, enchantment.getMaxLevel());
            for (int level = currentLevel + 1; level <= maxLevel; level++) {
                double cost = round(configured.pricePerLevel * (level - currentLevel) * GuiShop.CONFIG.priceMultiplier);
                offers.add(new OfferView(id, configured.name, holder, level, currentLevel, cost));
            }
        }

        offers.sort(Comparator.comparing(OfferView::displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(OfferView::targetLevel));
        return offers;
    }

    public static boolean purchase(ServerPlayer player, String enchantmentId, int targetLevel) {
        if (!ShopPermissions.user(player, "guishop.enchant")) {
            player.sendSystemMessage(Component.literal("You do not have permission to buy enchantments."));
            return false;
        }
        if (!GuiShop.CONFIG.enchantmentsEnabled()) {
            player.sendSystemMessage(Component.literal("The enchantment shop is disabled."));
            return false;
        }
        if (!GuiShop.CONFIG.creativeTransactionsAllowed()
            && player.getAbilities().instabuild
            && !ShopPermissions.check(player, "guishop.creative.bypass", 2)) {
            player.sendSystemMessage(Component.literal("Shop transactions are disabled while you are in creative mode."));
            return false;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold the item you want to enchant."));
            return false;
        }
        if (held.getCount() != 1) {
            player.sendSystemMessage(Component.literal("Hold exactly one item when purchasing an enchantment."));
            return false;
        }

        String id = ShopConfig.normalizeIdentifier(enchantmentId);
        ShopConfig.EnchantmentOffer configured = GuiShop.CONFIG.enchantmentOffer(id);
        if (configured == null || !configured.enabled()) {
            player.sendSystemMessage(Component.literal("That enchantment is not for sale."));
            return false;
        }

        Registry<Enchantment> registry = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ResourceKey<Enchantment> key;
        try {
            key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(id));
        } catch (Exception exception) {
            player.sendSystemMessage(Component.literal("Unknown enchantment: " + id));
            return false;
        }

        Holder<Enchantment> holder;
        try {
            holder = registry.getOrThrow(key);
        } catch (Exception exception) {
            player.sendSystemMessage(Component.literal("Unknown enchantment: " + id));
            return false;
        }

        Enchantment enchantment = holder.value();
        int currentLevel = held.getEnchantments().getLevel(holder);
        int configuredMax = configured.maxLevel <= 0 ? enchantment.getMaxLevel() : configured.maxLevel;
        int maxLevel = Math.min(configuredMax, enchantment.getMaxLevel());

        if (targetLevel <= currentLevel || targetLevel > maxLevel) {
            player.sendSystemMessage(Component.literal("That enchantment level is unavailable for this item."));
            return false;
        }
        if (!enchantment.canEnchant(held)) {
            player.sendSystemMessage(Component.literal(configured.name + " cannot be applied to that item."));
            return false;
        }

        Set<Holder<Enchantment>> existing = new HashSet<>(EnchantmentHelper.getEnchantmentsForCrafting(held).keySet());
        existing.remove(holder);
        if (!EnchantmentHelper.isEnchantmentCompatible(existing, holder)) {
            player.sendSystemMessage(Component.literal(configured.name + " conflicts with an enchantment already on that item."));
            return false;
        }

        double cost = round(configured.pricePerLevel * (targetLevel - currentLevel) * GuiShop.CONFIG.priceMultiplier);
        if (!GuiShop.ECONOMY.withdraw(player.getUUID(), cost)) {
            player.sendSystemMessage(Component.literal("You cannot afford " + GuiShop.CONFIG.money(cost) + "."));
            return false;
        }

        held.enchant(holder, targetLevel);
        player.getInventory().setChanged();
        player.sendSystemMessage(Component.literal("Purchased " + configured.name + " " + roman(targetLevel)
            + " for " + GuiShop.CONFIG.money(cost) + ". Balance: "
            + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))));
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

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record OfferView(
        String enchantmentId,
        String displayName,
        Holder<Enchantment> holder,
        int targetLevel,
        int currentLevel,
        double cost
    ) {}
}
