package com.zycu.guishop.admin;

import com.zycu.guishop.ShopConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** Exact draft listings, never registry item IDs. Validate the whole selection before editing it. */
public final class BulkListings {
    private BulkListings() {}
    public record Entry(ShopConfig.Category category, ShopConfig.ShopItem item) {}

    public static List<Entry> entries(ShopConfig config) {
        var result = new ArrayList<Entry>();
        for (var category : config.categories) for (var item : category.items) result.add(new Entry(category, item));
        return List.copyOf(result);
    }

    private static List<Entry> current(ShopConfig config, Collection<Entry> selection) {
        var result = List.copyOf(new LinkedHashSet<>(selection));
        if (result.isEmpty()) throw new IllegalArgumentException("Select at least one listing first.");
        for (var entry : result) if (!config.categories.contains(entry.category()) || !entry.category().items.contains(entry.item()))
            throw new IllegalArgumentException("The shop draft changed. Reopen Bulk Adjust and select the listings again.");
        return result;
    }

    public static List<Entry> prices(ShopConfig config, Collection<Entry> selection, Double buy, Double sell) {
        if (buy == null && sell == null) throw new IllegalArgumentException("Enter a buy price, a sell price, or both.");
        for (var amount : new Double[]{buy, sell}) if (amount != null && (!Double.isFinite(amount) || amount < 0 || amount > 1e9))
            throw new IllegalArgumentException("Prices must be from 0 to 1 billion.");
        var entries = current(config, selection);
        for (var entry : entries) if (entry.item().unavailableImport &&
            ((buy == null ? entry.item().buy : buy) > 0 || (sell == null ? entry.item().sell : sell) > 0))
            throw new IllegalArgumentException("Unavailable listings cannot be enabled. Deselect them or set both prices to 0.");
        for (var entry : entries) {
            if (buy != null) entry.item().buy = buy;
            if (sell != null) entry.item().sell = sell;
            entry.item().manualPrice = true;
        }
        return entries;
    }

    public static List<Entry> remove(ShopConfig config, Collection<Entry> selection) {
        var entries = current(config, selection);
        for (var entry : entries) entry.category().items.remove(entry.item());
        return entries;
    }
}
