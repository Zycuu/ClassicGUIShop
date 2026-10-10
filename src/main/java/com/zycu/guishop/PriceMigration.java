package com.zycu.guishop;

import com.zycu.guishop.admin.AdminDocument;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import java.nio.file.*;
import java.util.*;

/** A single backed-up conversion. Runtime prices never use legacy multipliers. */
public final class PriceMigration {
    private PriceMigration() {}
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("guishop");
    public static void migrate(ShopConfig config, RegistryAccess access) throws Exception {
        config.modes.migrateMinutes(); config.modes.validate();
        boolean initial = config.pricingSchema < 1;
        boolean returning = config.enchantments.keySet().stream().anyMatch(id -> access.lookupOrThrow(Registries.ENCHANTMENT).containsKey(net.minecraft.resources.Identifier.parse(id)));
        if(!initial && !returning) return;
        Path backup = DIR.resolve("backups/beta-price-migration-" + java.time.Instant.now().toString().replace(':', '-') + "-" + UUID.randomUUID());
        Files.createDirectories(backup);
        for (String name : List.of("settings.json", "shops.json", "enchantments.json", "versioned-listings.json", "folders.json", "shop.json"))
            if (Files.exists(DIR.resolve(name))) Files.copy(DIR.resolve(name), backup.resolve(name));
        Files.writeString(backup.resolve("editor-before.json"), "{\"config\":" + AdminDocument.JSON.toJson(config) + "}");
        Files.writeString(backup.resolve("migration.txt"), "One-time price conversion. Restore these files together with Alpha 10 to roll back; balances and journals are untouched.");
        try {
        double multiplier = initial ? config.priceMultiplier : 1;
        for (var category : config.categories) for (var item : category.items) { item.buy *= multiplier; item.sell *= multiplier; item.manualPrice = true; }
        Path parked = DIR.resolve("versioned-listings.json");
        if (Files.exists(parked)) {
            var data = com.google.gson.JsonParser.parseString(Files.readString(parked)).getAsJsonObject();
            if (data.has("listings")) for (var element : data.getAsJsonArray("listings")) {
                var item = element.getAsJsonObject().getAsJsonObject("listing");
                for (String field : List.of("buy", "sell")) if (item.has(field)) item.addProperty(field, item.get(field).getAsDouble() * multiplier);
            }
            com.zycu.guishop.admin.AccountOperations.writeAtomic(parked, AdminDocument.JSON.toJson(data));
        }
        flattenBooks(config, access, multiplier, config.catalogSellRatio);
        if(initial) for(var offer:config.enchantments.values()) offer.pricePerLevel *= multiplier;
        config.priceMultiplier = 1; config.pricingSchema = 1;
        config.saveChecked();
        }
        catch (Exception failure) {
            for (String name : List.of("settings.json", "shops.json", "enchantments.json", "versioned-listings.json"))
                if (Files.exists(backup.resolve(name))) Files.copy(backup.resolve(name), DIR.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            throw failure;
        }
        Files.writeString(backup.resolve("migration.txt"), "Converted effective listing prices once; legacy enchanted offers became exact book listings. Restore all backed-up files together with Alpha 10 to roll back. Player balances and journals were not changed.");
        System.out.println("[ClassicGUIShop Beta] Price migration backed up to " + backup);
    }
    public static void flattenBooks(ShopConfig config, RegistryAccess access, double multiplier, double ratio) {
        if (config.enchantments.isEmpty()) return;
        var category = new ShopConfig.Category(); category.id = "enchanted_books";
        int suffix = 2; while (config.category(category.id) != null) category.id = "enchanted_books_" + suffix++;
        category.name = "Enchanted Books"; category.icon = "minecraft:enchanted_book";
        config.permissionDefaults.put(categoryPermission(category.id), config.permissionLevel("guishop.enchant", 0));
        var registry = access.lookupOrThrow(Registries.ENCHANTMENT);
        var used = new HashSet<String>(); for (var cat : config.categories) for (var item : cat.items) used.add(item.listingId.toLowerCase(Locale.ROOT));
        var consumed = new ArrayList<String>();
        for (var entry : config.enchantments.entrySet()) {
            var holder = registry.get(net.minecraft.resources.Identifier.parse(entry.getKey()));
            // Unavailable legacy definitions stay in the archive until their registry is installed.
            if (holder.isEmpty()) continue;
            var offer = entry.getValue();
            for (int level = 1; level <= EnchantmentShopService.maximumLevel(holder.get().value(), offer); level++) {
                var stack = EnchantmentShopService.createBook(holder.get(), level);
                var item = new ShopConfig.ShopItem(); item.item = "minecraft:enchanted_book"; item.stack = ItemStackData.encode(stack, access);
                item.listingId = ItemStackData.unusedListingId(ItemStackData.listingId(stack, access), used); used.add(item.listingId.toLowerCase(Locale.ROOT));
                item.name = offer.name + " " + EnchantmentShopService.roman(level); item.manualPrice = true;
                if (config.enchantmentsEnabled() && offer.enabled()) { item.buy = Math.round(offer.pricePerLevel * level * multiplier * 100) / 100.0; item.sell = Math.round(item.buy * ratio * 100) / 100.0; }
                category.items.add(item);
            }
            consumed.add(entry.getKey());
        }
        // Keep legacy source definitions, but they are no longer an active pricing system.
        for(String id : consumed) config.enchantments.remove(id);
        if (!category.items.isEmpty()) config.categories.add(category);
    }
    private static String categoryPermission(String id) { return "guishop.category." + id; }
}
