package com.zycu.guishop;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

public final class ShopSuggestions {
    private static final String[] CATEGORY_ID_EXAMPLES = {
        "custom_items",
        "resource_pack_items",
        "data_pack_items",
        "modded_items",
        "special_items"
    };

    public static final SuggestionProvider<CommandSourceStack> PLAYERS = (context, builder) -> {
        Set<String> values = new LinkedHashSet<>();
        values.addAll(context.getSource().getOnlinePlayerNames());
        if (GuiShop.PLAYERS != null) values.addAll(GuiShop.PLAYERS.knownNames());
        return SharedSuggestionProvider.suggest(values, builder);
    };

    public static final SuggestionProvider<CommandSourceStack> CATEGORIES = (context, builder) ->
        SharedSuggestionProvider.suggest(categoryIds(), builder);

    /**
     * Used by /adminshop category add. This intentionally includes examples,
     * detected namespaces, and existing IDs so the first argument never has an empty suggestion list.
     */
    public static final SuggestionProvider<CommandSourceStack> ITEM_NAMESPACES = (context, builder) -> {
        Set<String> values = new LinkedHashSet<>();
        for (String example : CATEGORY_ID_EXAMPLES) values.add(example);
        categoryIds().forEach(values::add);
        IntegrationImportService.itemNamespaces().forEach(values::add);
        IntegrationImportService.recipeNamespaces(context.getSource().getServer())
            .filter(namespace -> !namespace.equals("minecraft"))
            .forEach(values::add);
        return SharedSuggestionProvider.suggest(values, builder);
    };

    public static final SuggestionProvider<CommandSourceStack> EXTERNAL_ITEM_NAMESPACES = (context, builder) ->
        SharedSuggestionProvider.suggest(IntegrationImportService.itemNamespaces(), builder);

    public static final SuggestionProvider<CommandSourceStack> IMPORT_CATEGORIES = (context, builder) -> {
        Set<String> values = new LinkedHashSet<>();
        categoryIds().forEach(values::add);
        for (String argumentName : new String[]{"namespace", "pack"}) {
            try {
                String namespace = context.getArgument(argumentName, String.class);
                if (namespace != null && !namespace.isBlank()) {
                    String clean = namespace.toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
                    values.add(clean);
                    values.add(clean + "_items");
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        values.add("resource_pack_items");
        values.add("data_pack_items");
        values.add("modded_items");
        return SharedSuggestionProvider.suggest(values, builder);
    };

    public static final SuggestionProvider<CommandSourceStack> ITEMS = (context, builder) ->
        SharedSuggestionProvider.suggest(itemAndListingIds(), builder);

    public static final SuggestionProvider<CommandSourceStack> ITEM_IDS = (context, builder) ->
        SharedSuggestionProvider.suggest(BuiltInRegistries.ITEM.keySet().stream().map(Object::toString), builder);

    public static final SuggestionProvider<CommandSourceStack> ENCHANTMENTS = (context, builder) ->
        SharedSuggestionProvider.suggest(
            context.getSource().getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .entrySet().stream().map(entry -> entry.getKey().identifier().toString()),
            builder
        );

    public static final SuggestionProvider<CommandSourceStack> RECIPE_NAMESPACES = (context, builder) ->
        SharedSuggestionProvider.suggest(IntegrationImportService.recipeNamespaces(context.getSource().getServer()), builder);

    public static final SuggestionProvider<CommandSourceStack> DATA_PACKS = (context, builder) ->
        SharedSuggestionProvider.suggest(
            context.getSource().getServer().getPackRepository().getSelectedIds().stream(),
            builder
        );

    public static final SuggestionProvider<CommandSourceStack> PRICES = (context, builder) ->
        SharedSuggestionProvider.suggest(new String[]{"0", "0.25", "1", "5", "10", "25", "50", "100", "250", "500", "1000"}, builder);

    public static final SuggestionProvider<CommandSourceStack> AMOUNTS = (context, builder) ->
        SharedSuggestionProvider.suggest(new String[]{"1", "16", "32", "64", "128", "256", "512", "1000"}, builder);

    public static final SuggestionProvider<CommandSourceStack> PAGES = (context, builder) ->
        SharedSuggestionProvider.suggest(new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "10"}, builder);

    public static final SuggestionProvider<CommandSourceStack> ENCHANTMENT_LEVELS = (context, builder) ->
        SharedSuggestionProvider.suggest(new String[]{"1", "2", "3", "4", "5"}, builder);

    private ShopSuggestions() {}

    private static Stream<String> categoryIds() {
        if (GuiShop.CONFIG == null || GuiShop.CONFIG.categories == null) return Stream.empty();
        return GuiShop.CONFIG.categories.stream().map(category -> category.id).distinct().sorted();
    }

    private static Stream<String> itemAndListingIds() {
        Set<String> values = new LinkedHashSet<>();
        if (GuiShop.CONFIG != null && GuiShop.CONFIG.categories != null) {
            for (ShopConfig.Category category : GuiShop.CONFIG.categories) {
                for (ShopConfig.ShopItem item : category.items) {
                    if (item.listingId != null && !item.listingId.isBlank()) values.add(item.listingId);
                    if (item.item != null && !item.item.isBlank()) values.add(item.item);
                }
            }
        }
        BuiltInRegistries.ITEM.keySet().stream().map(Object::toString).forEach(values::add);
        return values.stream();
    }
}
