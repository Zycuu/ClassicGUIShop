package com.zycu.guishop;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class VanillaCatalog {
    private static final List<TabCategory> DEFAULT_TABS = List.of(
        new TabCategory("building_blocks", "building_blocks", "Building Blocks", "minecraft:bricks"),
        new TabCategory("colored_blocks", "colored_blocks", "Colored Blocks", "minecraft:cyan_wool"),
        new TabCategory("natural_blocks", "natural_blocks", "Natural Blocks", "minecraft:grass_block"),
        new TabCategory("functional_blocks", "functional_blocks", "Functional Blocks", "minecraft:crafting_table"),
        new TabCategory("redstone_blocks", "redstone", "Redstone", "minecraft:redstone"),
        new TabCategory("tools_and_utilities", "tools", "Tools and Utilities", "minecraft:diamond_pickaxe"),
        new TabCategory("combat", "combat", "Combat", "minecraft:diamond_sword"),
        new TabCategory("food_and_drinks", "foodstuffs", "Food and Drinks", "minecraft:golden_apple"),
        new TabCategory("ingredients", "materials", "Ingredients", "minecraft:iron_ingot")
    );

    private static final Set<String> UNOBTAINABLE_ITEMS = Set.of(
        "minecraft:air",
        "minecraft:barrier",
        "minecraft:bedrock",
        "minecraft:budding_amethyst",
        "minecraft:chain_command_block",
        "minecraft:command_block",
        "minecraft:command_block_minecart",
        "minecraft:debug_stick",
        "minecraft:end_portal_frame",
        "minecraft:enchanted_book",
        "minecraft:farmland",
        "minecraft:frogspawn",
        "minecraft:jigsaw",
        "minecraft:knowledge_book",
        "minecraft:light",
        "minecraft:player_head",
        "minecraft:reinforced_deepslate",
        "minecraft:repeating_command_block",
        "minecraft:spawner",
        "minecraft:structure_block",
        "minecraft:structure_void",
        "minecraft:suspicious_gravel",
        "minecraft:suspicious_sand",
        "minecraft:trial_spawner",
        "minecraft:vault"
    );

    private VanillaCatalog() {}

    public static SyncResult sync(ShopConfig config, MinecraftServer server) {
        if (!config.autoPopulateVanillaCatalog()) return new SyncResult(0, 0, 0);

        Registry<CreativeModeTab> tabs = server.registryAccess().lookupOrThrow(Registries.CREATIVE_MODE_TAB);
        CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(
            FeatureFlags.DEFAULT_FLAGS,
            false,
            server.registryAccess()
        );

        Map<String, CatalogEntry> entries = new LinkedHashMap<>();
        Set<String> allowedBaseItemIds = new HashSet<>();

        for (TabCategory definition : DEFAULT_TABS) {
            ShopConfig.Category category = config.ensureDefaultCategory(
                definition.categoryId(),
                definition.displayName(),
                definition.icon()
            );
            if (category == null) continue;

            ResourceKey<CreativeModeTab> key = ResourceKey.create(
                Registries.CREATIVE_MODE_TAB,
                Identifier.withDefaultNamespace(definition.tabId())
            );

            Holder<CreativeModeTab> holder;
            try {
                holder = tabs.getOrThrow(key);
            } catch (Exception exception) {
                System.err.println("[ClassicGUIShop] Missing creative tab " + definition.tabId());
                continue;
            }

            CreativeModeTab tab = holder.value();
            tab.buildContents(parameters);
            for (ItemStack displayed : tab.getDisplayItems()) {
                ItemStack stack = displayed.copyWithCount(1);
                if (!isCatalogItem(stack)) continue;

                String itemId = ShopService.itemId(stack);
                allowedBaseItemIds.add(itemId);
                String listingId = ItemStackData.listingId(stack, server.registryAccess());
                entries.putIfAbsent(listingId, new CatalogEntry(category.id, stack));
            }
        }

        int removed = config.purgeUnobtainableOnSync()
            ? purgeUnobtainable(config, allowedBaseItemIds)
            : 0;

        int added = 0;
        int repriced = 0;
        for (CatalogEntry entry : entries.values()) {
            ShopConfig.Category targetCategory = config.category(entry.categoryId());
            if (targetCategory == null) continue;

            BalancedPricing.PriceQuote quote = BalancedPricing.quote(entry.stack(), entry.categoryId(), config);
            ShopConfig.FoundItem found = config.findItem(entry.stack(), server.registryAccess());
            if (found == null) {
                config.addGeneratedItem(
                    entry.categoryId(),
                    entry.stack(),
                    quote.buy(),
                    quote.sell(),
                    server.registryAccess()
                );
                added++;
                continue;
            }

            ShopConfig.ShopItem item = found.item();
            boolean generated = !Boolean.TRUE.equals(item.manualPrice);
            if (generated && found.category() != targetCategory) {
                found.category().items.remove(item);
                targetCategory.items.add(item);
            }

            if (generated && config.balancedPricingEnabled() && config.rebalanceGeneratedItems()) {
                if (Double.compare(item.buy, quote.buy()) != 0
                    || Double.compare(item.sell, quote.sell()) != 0
                    || item.pricingModelVersion != BalancedPricing.MODEL_VERSION) {
                    item.buy = quote.buy();
                    item.sell = quote.sell();
                    item.pricingModelVersion = BalancedPricing.MODEL_VERSION;
                    repriced++;
                }
            }
        }

        if (added > 0 || removed > 0 || repriced > 0) config.save();
        return new SyncResult(added, removed, repriced);
    }

    private static int purgeUnobtainable(ShopConfig config, Set<String> allowedBaseItemIds) {
        int removed = 0;
        for (ShopConfig.Category category : config.categories) {
            List<ShopConfig.ShopItem> retained = new ArrayList<>();
            for (ShopConfig.ShopItem item : category.items) {
                String id = item.item == null ? "minecraft:air" : item.item;
                boolean vanilla = id.startsWith("minecraft:");
                boolean allowed = !vanilla || (allowedBaseItemIds.contains(id) && !UNOBTAINABLE_ITEMS.contains(id));
                if (allowed) retained.add(item);
                else removed++;
            }
            category.items = retained;
        }
        return removed;
    }

    private static boolean isCatalogItem(ItemStack stack) {
        if (stack.isEmpty() || stack.is(Items.ENCHANTED_BOOK)) return false;
        String id = ShopService.itemId(stack);
        if (UNOBTAINABLE_ITEMS.contains(id)) return false;
        return !id.startsWith("minecraft:infested_") && !id.endsWith("_spawn_egg");
    }

    public record SyncResult(int added, int removed, int repriced) {}
    private record CatalogEntry(String categoryId, ItemStack stack) {}
    private record TabCategory(String tabId, String categoryId, String displayName, String icon) {}
}
