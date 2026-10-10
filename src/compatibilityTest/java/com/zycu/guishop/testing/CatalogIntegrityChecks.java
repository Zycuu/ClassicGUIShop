package com.zycu.guishop.testing;

import com.zycu.guishop.*;
import com.zycu.guishop.admin.DetectedContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import java.util.List;

/** Exhaustive component and actual trade audit of the catalog loaded in a disposable world. */
public final class CatalogIntegrityChecks {
    private CatalogIntegrityChecks() {}
    public static void run(ServerPlayer player, List<DetectedContent.Entry> detected) {
        var original = GuiShop.CONFIG;
        double originalBalance = GuiShop.ECONOMY.balance(player.getUUID());
        int trades = 0, contextual = 0, generatedMaps = 0;
        var unavailableMaps = new java.util.ArrayList<String>();
        var placeholders = new java.util.ArrayList<String>();
        var auditedTypes = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Item, Boolean>());
        try {
            var config = new ShopConfig(); config.categories.clear(); config.priceMultiplier = 1;
            var category = new ShopConfig.Category(); category.id = "catalog_audit"; category.name = "Catalog Audit";
            config.categories.add(category); GuiShop.CONFIG = config;
            GuiShop.ECONOMY.setBalance(player.getUUID(), detected.size() * 20.0 + 1000);
            for (var entry : detected) {
                var listing = com.zycu.guishop.admin.AdminDocument.JSON.fromJson(com.zycu.guishop.admin.AdminDocument.JSON.toJson(entry.listing()), ShopConfig.ShopItem.class);
                listing.buy = 10; listing.sell = 2; category.items.clear(); category.items.add(listing);
                var source = listing.createStack(player.registryAccess());
                check(!source.isEmpty(), "Detected source decodes: " + listing.listingId);
                auditedTypes.add(source.getItem());
                check(com.zycu.guishop.SaleValuation.quote(player,source).unit()==2,"Exact command valuation preserves catalog variant: "+listing.listingId);
                var before = ItemStackData.encode(source, player.registryAccess());
                var identity = ItemStackData.listingId(source, player.registryAccess());
                var icon = ShopIcons.listing(source, listing.name, "Audit shop description", 10, 2, config, true);
                var categoryIcon = ShopIcons.display(source, "Audit category", "Category description");
                check(before.equals(ItemStackData.encode(source, player.registryAccess())), "Menu construction never mutates source: " + identity);
                check(identity.equals(ItemStackData.listingId(source, player.registryAccess())), "Identity unchanged: " + identity);
                check(ItemStackData.same(source, ItemStackData.decode(before, listing.item, player.registryAccess())), "Codec preserves every component: " + identity);
                check(icon.get(DataComponents.ITEM_MODEL).equals(source.get(DataComponents.ITEM_MODEL)), "Listing uses native item model: " + identity);
                check(categoryIcon.get(DataComponents.ITEM_MODEL).equals(source.get(DataComponents.ITEM_MODEL)), "Category uses native item model: " + identity);
                var lines = icon.getTooltipLines(Item.TooltipContext.of(player.level()), player, TooltipFlag.NORMAL).stream().map(net.minecraft.network.chat.Component::getString).toList();
                check(lines.size() == 6 && lines.get(1).startsWith("Buy: ") && lines.get(2).startsWith("Sell: ") && lines.getLast().equals("Audit shop description"), "Only shop name, prices, controls and description appear: " + identity + " " + lines);
                player.getInventory().clearContent();
                if (source.getItem() instanceof MapItem) {
                    contextual++;
                    if (!source.has(DataComponents.MAP_ID)) placeholders.add(listing.item);
                    double balance = GuiShop.ECONOMY.balance(player.getUUID());
                    boolean bought = ShopService.trade(player, listing, ShopGui.Mode.BUY, 1);
                    if (bought) {
                        generatedMaps++;
                        var delivered = player.getInventory().getItem(0);
                        check(delivered.getItem() == source.getItem() && delivered.has(DataComponents.MAP_ID) && MapItem.getSavedData(delivered, player.level()) != null, "Playable map data and original item type: " + identity);
                        if (!source.is(Items.FILLED_MAP)) check(!delivered.get(DataComponents.MAP_DECORATIONS).decorations().isEmpty(), "Explorer map has a destination: " + identity);
                        check(ShopMaps.matches(source, delivered), "Original components survive generated world data: " + identity);
                        check(ShopService.trade(player, listing, ShopGui.Mode.SELL, 1), "Generated map sells: " + identity);
                    } else {
                        unavailableMaps.add(listing.item);
                        check(player.getInventory().isEmpty() && GuiShop.ECONOMY.balance(player.getUUID()) == balance, "Unavailable contextual item is never delivered or charged: " + identity);
                    }
                    continue;
                }
                check(ShopService.trade(player, listing, ShopGui.Mode.BUY, 1), "Actual purchase succeeds: " + identity);
                var delivered = player.getInventory().getItem(0);
                check(ItemStackData.same(delivered, source) && before.equals(ItemStackData.encode(delivered, player.registryAccess())), "Purchase retains ALL original components: " + identity);
                check(ShopService.trade(player, listing, ShopGui.Mode.SELL, 1) && player.getInventory().isEmpty(), "Exact purchased stack sells: " + identity);
                check(before.equals(ItemStackData.encode(listing.createStack(player.registryAccess()), player.registryAccess())), "Trade never changes configured template: " + identity);
                trades++;
            }
            long registered = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).count();
            check(detected.size() >= registered, "Every registered non-air item included");
            for (var item : BuiltInRegistries.ITEM) if (item != Items.AIR) check(auditedTypes.contains(item), "Registered item type was audited: " + BuiltInRegistries.ITEM.getKey(item));
            var corrupt = new ShopConfig.ShopItem(); corrupt.item = "minecraft:diamond"; corrupt.listingId = "minecraft:diamond#invalid_fixture";
            corrupt.stack = com.google.gson.JsonParser.parseString("{\"id\":\"minecraft:diamond\",\"components\":{\"missing_mod:audit_component\":{}}}"); corrupt.buy = 10; corrupt.sell = 2;
            category.items.clear(); category.items.add(corrupt); player.getInventory().clearContent();
            double unchangedBalance = GuiShop.ECONOMY.balance(player.getUUID());
            check(!ShopService.trade(player, corrupt, ShopGui.Mode.BUY, 1) && player.getInventory().isEmpty() && GuiShop.ECONOMY.balance(player.getUUID()) == unchangedBalance, "Unknown mod component fails closed instead of delivering a stripped base item");
            System.out.println("[Catalog integrity test] PASS: " + registered + " registered items; " + detected.size() + " detected base items and exact variants; all display, codec and original component checks; " + trades + " actual purchase/sale pairs; " + contextual + " world-dependent maps checked separately");
            System.out.println("[Catalog integrity test] MAP RESULTS: " + generatedMaps + " functional maps; " + unavailableMaps.size() + " no-charge refusals without nearby destinations: " + unavailableMaps);
            System.out.println("[Catalog integrity test] MAP PLACEHOLDERS: " + placeholders);
        } finally {
            player.getInventory().clearContent(); GuiShop.CONFIG = original;
            GuiShop.ECONOMY.setBalance(player.getUUID(), originalBalance);
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
