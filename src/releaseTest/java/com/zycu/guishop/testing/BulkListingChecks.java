package com.zycu.guishop.testing;

import com.google.gson.JsonParser;
import com.zycu.guishop.ShopConfig;
import com.zycu.guishop.admin.AdminDocument;
import com.zycu.guishop.admin.BulkListings;
import java.util.List;

/** Multi-category variants, partial prices, invalid selection atomicity and detached-draft isolation. */
final class BulkListingChecks {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void rejected(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid bulk operation accepted");
    }
    private static ShopConfig.ShopItem item(String id) {
        var item = new ShopConfig.ShopItem(); item.item = "minecraft:suspicious_stew";
        item.listingId = id; item.name = id; item.buy = 10; item.sell = 2;
        item.stack = JsonParser.parseString("{\"variant\":\"" + id + "\"}"); return item;
    }
    static void verify() {
        var document = new AdminDocument(); document.config = new ShopConfig();
        for (String id : List.of("food", "custom")) {
            var category = new ShopConfig.Category(); category.id = category.name = id;
            category.items.add(item(id + "_one")); category.items.add(item(id + "_two"));
            document.config.categories.add(category);
        }
        var live = AdminDocument.JSON.toJson(document); var draft = document.copy(); var config = draft.config;
        var entries = BulkListings.entries(config); var first = entries.get(0); var other = entries.get(2);
        var original = first.item().stack.deepCopy();
        check(BulkListings.prices(config, List.of(first, other, first), 20.0, null).size() == 2, "Deduplicated exact selection");
        check(first.item().buy == 20 && other.item().buy == 20 && first.item().sell == 2 && entries.get(1).item().buy == 10, "Only selected listings repriced, blank side preserved");
        check(Boolean.TRUE.equals(first.item().manualPrice) && first.item().stack.equals(original), "Manual prices and exact item data retained");
        BulkListings.prices(config, List.of(first), null, 0.0);
        check(first.item().sell == 0 && first.item().buy == 20, "Zero disables only requested side");
        for (double price : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, 1e9 + 1}) rejected(() -> BulkListings.prices(config, List.of(first), price, 1.0));
        rejected(() -> BulkListings.prices(config, List.of(first), null, null));
        rejected(() -> BulkListings.remove(config, List.of()));
        other.item().unavailableImport = true; other.item().buy = other.item().sell = 0;
        rejected(() -> BulkListings.prices(config, List.of(first, other), 50.0, 5.0));
        check(first.item().buy == 20 && other.item().buy == 0, "Unavailable selection rejects all changes atomically");
        BulkListings.prices(config, List.of(other), 0.0, 0.0);
        var removed = BulkListings.remove(config, List.of(first, other));
        check(removed.size() == 2 && config.categories.size() == 2 && BulkListings.entries(config).size() == 2, "Removal spans categories and leaves sibling variants/categories");
        var remaining = BulkListings.entries(config).get(0);
        rejected(() -> BulkListings.remove(config, List.of(remaining, first)));
        check(remaining.category().items.contains(remaining.item()), "Stale removal rejects whole batch");
        check(AdminDocument.JSON.toJson(document).equals(live), "Live source unchanged by draft operations");
        var roundtrip = draft.copy(); check(BulkListings.entries(roundtrip.config).size() == 2, "Removed variants stay removed when draft is serialized");
    }
}
