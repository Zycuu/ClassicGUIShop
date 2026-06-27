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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EnchantmentShopService {
    private static final Map<UUID, ShopGui.Mode> PLAYER_MODES = new ConcurrentHashMap<>();

    private EnchantmentShopService() {}

    public static void rememberMode(ServerPlayer player, ShopGui.Mode mode) {
        if (player != null && mode != null) PLAYER_MODES.put(player.getUUID(), mode);
    }

    public static List<EnchantmentView> availableEnchantments(ServerPlayer player) {
        return availableEnchantments(player, rememberedMode(player));
    }

    public static List<EnchantmentView> availableEnchantments(ServerPlayer player, ShopGui.Mode mode) {
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

            double firstLevelCost = price(configured, 1, mode);
            double maximumLevelCost = price(configured, maxLevel, mode);
            if (firstLevelCost <= 0 && maximumLevelCost <= 0) continue;
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
        return availableLevels(player, enchantmentId, rememberedMode(player));
    }

    public static List<OfferView> availableLevels(ServerPlayer player, String enchantmentId, ShopGui.Mode mode) {
        ResolvedOffer resolved = resolve(player, enchantmentId);
        if (resolved == null) return List.of();

        int maxLevel = maximumLevel(resolved.holder().value(), resolved.configured());
        List<OfferView> offers = new ArrayList<>();
        for (int level = 1; level <= maxLevel; level++) {
            double value = price(resolved.configured(), level, mode);
            if (value > 0) offers.add(new OfferView(
                resolved.enchantmentId(),
                resolved.configured().name,
                resolved.holder(),
                level,
                value
            ));
        }
        return offers;
    }

    public static ItemStack createBook(Holder<Enchantment> enchantment, int level) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.enchant(enchantment, level);
        return book;
    }

    public static boolean purchase(ServerPlayer player, String enchantmentId, int targetLevel) {
        if (rememberedMode(player) == ShopGui.Mode.SELL) {
            return sell(player, enchantmentId, targetLevel, 1);
        }
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

        ResolvedOffer resolved = resolve(player, enchantmentId);
        if (resolved == null) {
            ShopMessages.error(player, "That enchanted book is not for sale.");
            return false;
        }

        int maxLevel = maximumLevel(resolved.holder().value(), resolved.configured());
        if (targetLevel < 1 || targetLevel > maxLevel) {
            ShopMessages.error(player, "That enchantment level is unavailable.");
            return false;
        }

        double cost = buyPrice(resolved.configured(), targetLevel);
        if (!GuiShop.ECONOMY.withdraw(player.getUUID(), cost)) {
            ShopMessages.error(player, "You cannot afford " + GuiShop.CONFIG.money(cost) + ".");
            return false;
        }

        ShopService.give(player, createBook(resolved.holder(), targetLevel), 1);
        ShopMessages.success(player, "Purchased " + resolved.configured().name + " " + roman(targetLevel)
            + " for " + GuiShop.CONFIG.money(cost) + ". Balance: "
            + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID())));
        return true;
    }

    public static boolean sell(ServerPlayer player, String enchantmentId, int targetLevel, int requestedQuantity) {
        if (!ShopPermissions.user(player, "guishop.enchant")) {
            ShopMessages.error(player, "You do not have permission to sell enchanted books.");
            return false;
        }
        if (!GuiShop.CONFIG.enchantmentsEnabled()) {
            ShopMessages.warning(player, "The enchanted book shop is disabled.");
            return false;
        }
        if (!ShopService.canTransact(player, ShopGui.Mode.SELL)) return false;

        ResolvedOffer resolved = resolve(player, enchantmentId);
        if (resolved == null) {
            ShopMessages.error(player, "That enchanted book is not accepted by the shop.");
            return false;
        }

        int maxLevel = maximumLevel(resolved.holder().value(), resolved.configured());
        if (targetLevel < 1 || targetLevel > maxLevel) {
            ShopMessages.error(player, "That enchantment level is unavailable.");
            return false;
        }

        ItemStack template = createBook(resolved.holder(), targetLevel);
        int available = ShopService.count(player, template);
        if (available <= 0) {
            ShopMessages.warning(player, "You do not have any matching " + resolved.configured().name + " "
                + roman(targetLevel) + " books to sell.");
            return false;
        }

        int quantity = requestedQuantity == ShopService.SELL_ALL
            ? available
            : Math.min(Math.max(1, requestedQuantity), available);
        double unitPrice = sellPrice(resolved.configured(), targetLevel);
        double total = round(unitPrice * quantity);
        ShopService.remove(player, template, quantity);
        GuiShop.ECONOMY.deposit(player.getUUID(), total);
        ShopMessages.success(player, "Sold " + quantity + "x " + resolved.configured().name + " "
            + roman(targetLevel) + " for " + GuiShop.CONFIG.money(total) + ". Balance: "
            + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID())));
        return true;
    }

    public static boolean sellHeld(ServerPlayer player, ItemStack held, int requestedQuantity, boolean allInventory) {
        if (held == null || held.isEmpty()) return false;
        ResolvedHeldBook resolved = resolveHeldBook(player, held);
        if (resolved == null) return false;

        int available = allInventory ? ShopService.count(player, resolved.template()) : held.getCount();
        int quantity = requestedQuantity == ShopService.SELL_ALL
            ? available
            : Math.min(Math.max(1, requestedQuantity), available);
        if (quantity <= 0) return false;

        double unitPrice = sellPrice(resolved.offer().configured(), resolved.level());
        double total = round(unitPrice * quantity);
        if (allInventory) {
            ShopService.remove(player, resolved.template(), quantity);
        } else {
            held.shrink(quantity);
            player.getInventory().setChanged();
        }

        GuiShop.ECONOMY.deposit(player.getUUID(), total);
        ShopMessages.success(player, "Sold " + quantity + "x " + resolved.offer().configured().name + " "
            + roman(resolved.level()) + " for " + GuiShop.CONFIG.money(total) + ". Balance: "
            + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID())));
        return true;
    }

    public static boolean showWorth(ServerPlayer player, ItemStack stack, int amount) {
        ResolvedHeldBook resolved = resolveHeldBook(player, stack);
        if (resolved == null) return false;
        int quantity = Math.max(1, amount);
        double buy = buyPrice(resolved.offer().configured(), resolved.level());
        double sell = sellPrice(resolved.offer().configured(), resolved.level());
        ShopMessages.info(player, resolved.offer().configured().name + " " + roman(resolved.level()) + " [enchanted book]");
        ShopMessages.info(player, "Buy: " + GuiShop.CONFIG.money(buy) + " each / "
            + GuiShop.CONFIG.money(round(buy * quantity)) + " total");
        ShopMessages.info(player, "Sell: " + GuiShop.CONFIG.money(sell) + " each / "
            + GuiShop.CONFIG.money(round(sell * quantity)) + " total");
        ShopMessages.info(player, "Category: Enchanted Books | Quantity checked: " + quantity);
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

    private static ShopGui.Mode rememberedMode(ServerPlayer player) {
        if (player == null) return ShopGui.Mode.BUY;
        ShopGui.Mode visible = visibleShopMode(player);
        if (visible != null) {
            PLAYER_MODES.put(player.getUUID(), visible);
            return visible;
        }
        ShopGui.Mode remembered = PLAYER_MODES.get(player.getUUID());
        if (remembered != null) return remembered;
        if (ShopService.canUseMode(player, ShopGui.Mode.SELL) && !ShopService.canUseMode(player, ShopGui.Mode.BUY)) {
            return ShopGui.Mode.SELL;
        }
        return ShopGui.Mode.BUY;
    }

    private static ShopGui.Mode visibleShopMode(ServerPlayer player) {
        String info = slotName(player, 49);
        String toggle = slotName(player, 53);
        if (info.contains("| Sell |") || toggle.contains("Switch to Buy")) return ShopGui.Mode.SELL;
        if (info.contains("| Buy |") || toggle.contains("Switch to Sell")) return ShopGui.Mode.BUY;
        return null;
    }

    private static String slotName(ServerPlayer player, int slot) {
        try {
            if (player == null || player.containerMenu == null) return "";
            ItemStack stack = player.containerMenu.getSlot(slot).getItem();
            if (stack == null || stack.isEmpty()) return "";
            return stack.getHoverName().getString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static ResolvedOffer resolve(ServerPlayer player, String enchantmentId) {
        String id = ShopConfig.normalizeIdentifier(enchantmentId);
        ShopConfig.EnchantmentOffer configured = GuiShop.CONFIG.enchantmentOffer(id);
        if (configured == null || !configured.enabled()) return null;

        Registry<Enchantment> registry = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        try {
            ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(id));
            Holder<Enchantment> holder = registry.getOrThrow(key);
            return new ResolvedOffer(id, configured, holder);
        } catch (Exception exception) {
            return null;
        }
    }

    private static ResolvedHeldBook resolveHeldBook(ServerPlayer player, ItemStack stack) {
        if (!GuiShop.CONFIG.enchantmentsEnabled() || !ShopPermissions.user(player, "guishop.enchant")) return null;
        for (EnchantmentView enchantment : availableEnchantments(player, ShopGui.Mode.SELL)) {
            for (OfferView offer : availableLevels(player, enchantment.enchantmentId(), ShopGui.Mode.SELL)) {
                ItemStack template = createBook(offer.holder(), offer.targetLevel());
                if (ItemStackData.same(template, stack)) {
                    ResolvedOffer resolved = resolve(player, offer.enchantmentId());
                    if (resolved != null) return new ResolvedHeldBook(resolved, offer.targetLevel(), template);
                }
            }
        }
        return null;
    }

    private static int maximumLevel(Enchantment enchantment, ShopConfig.EnchantmentOffer configured) {
        int configuredMax = configured.maxLevel <= 0 ? enchantment.getMaxLevel() : configured.maxLevel;
        return Math.min(configuredMax, enchantment.getMaxLevel());
    }

    private static double price(ShopConfig.EnchantmentOffer configured, int level, ShopGui.Mode mode) {
        return mode == ShopGui.Mode.BUY ? buyPrice(configured, level) : sellPrice(configured, level);
    }

    private static double buyPrice(ShopConfig.EnchantmentOffer configured, int level) {
        return round(configured.pricePerLevel * level * GuiShop.CONFIG.priceMultiplier);
    }

    private static double sellPrice(ShopConfig.EnchantmentOffer configured, int level) {
        return round(buyPrice(configured, level) * Math.max(0.0, GuiShop.CONFIG.catalogSellRatio));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record ResolvedOffer(
        String enchantmentId,
        ShopConfig.EnchantmentOffer configured,
        Holder<Enchantment> holder
    ) {}

    private record ResolvedHeldBook(
        ResolvedOffer offer,
        int level,
        ItemStack template
    ) {}

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
