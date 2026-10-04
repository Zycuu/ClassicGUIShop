package com.zycu.guishop;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

public final class ShopSuggestions {
    public static final SuggestionProvider<CommandSourceStack> PLAYERS = (context, builder) -> {
        Set<String> values = new LinkedHashSet<>();
        values.addAll(context.getSource().getOnlinePlayerNames());
        if (GuiShop.PLAYERS != null) values.addAll(GuiShop.PLAYERS.knownNames());
        return SharedSuggestionProvider.suggest(values, builder);
    };

    public static final SuggestionProvider<CommandSourceStack> ITEMS = (context, builder) -> {
        String prefix = builder.getRemainingLowerCase().replaceFirst("^\"", "");
        // Namespaces, component hashes and duplicate suffixes need quoting in a vanilla string argument.
        itemAndListingIds().filter(id -> id.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)
            || id.substring(id.indexOf(':') + 1).toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
            .forEach(id -> builder.suggest(com.mojang.brigadier.arguments.StringArgumentType.escapeIfRequired(id)));
        return builder.buildFuture();
    };

    public static final SuggestionProvider<CommandSourceStack> PRICES = (context, builder) ->
        SharedSuggestionProvider.suggest(new String[]{"0", "0.25", "1", "5", "10", "25", "50", "100", "250", "500", "1000"}, builder);

    public static final SuggestionProvider<CommandSourceStack> AMOUNTS = (context, builder) ->
        SharedSuggestionProvider.suggest(new String[]{"1", "16", "32", "64", "128", "256", "512", "1000"}, builder);

    private ShopSuggestions() {}

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
