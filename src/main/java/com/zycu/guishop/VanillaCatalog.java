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

import java.util.List;
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

    public static int sync(ShopConfig config, MinecraftServer server) {
        if (!config.autoPopulateVanillaCatalog()) return 0;

        Registry<CreativeModeTab> tabs = server.registryAccess().lookupOrThrow(Registries.CREATIVE_MODE_TAB);
        CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(
            FeatureFlags.DEFAULT_FLAGS,
            false,
            server.registryAccess()
        );

        int added = 0;
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
                if (config.findItem(stack, server.registryAccess()) != null) continue;

                String itemId = ShopService.itemId(stack);
                ShopConfig.FoundItem similar = config.findItem(itemId);
                double buy = similar == null ? config.generatedVanillaBuyPrice : similar.item().buy;
                double sell = similar == null ? config.generatedVanillaSellPrice : similar.item().sell;
                config.addGeneratedItem(category.id, stack, buy, sell, server.registryAccess());
                added++;
            }
        }

        if (added > 0) config.save();
        return added;
    }

    private static boolean isCatalogItem(ItemStack stack) {
        if (stack.isEmpty() || stack.is(Items.ENCHANTED_BOOK)) return false;
        String id = ShopService.itemId(stack);
        if (UNOBTAINABLE_ITEMS.contains(id)) return false;
        return !id.startsWith("minecraft:infested_") && !id.endsWith("_spawn_egg");
    }

    private record TabCategory(String tabId, String categoryId, String displayName, String icon) {}
}
