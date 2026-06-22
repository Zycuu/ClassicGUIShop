package com.zycu.guishop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ShopConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("guishop");
    private static final Path FILE = DIRECTORY.resolve("shop.json");

    public String currencySymbol = "$";
    public double startingBalance = 500.0;
    public double priceMultiplier = 1.0;
    public Boolean allowCreativeTransactions = false;
    public Boolean allowOfflinePayments = true;
    public Map<String, Integer> permissionDefaults = new LinkedHashMap<>();
    public List<Category> categories = new ArrayList<>();

    public static ShopConfig load() {
        try {
            Files.createDirectories(DIRECTORY);
            if (Files.notExists(FILE)) {
                try (InputStream stream = ShopConfig.class.getResourceAsStream("/default_shop.json")) {
                    if (stream == null) throw new IOException("Missing bundled default_shop.json");
                    Files.copy(stream, FILE, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            ShopConfig config = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), ShopConfig.class);
            if (config == null) throw new IOException("shop.json was empty");
            config.normalize();
            config.save();
            return config;
        } catch (Exception exception) {
            System.err.println("[ClassicGUIShop] Could not load shop.json. Using a minimal in-memory configuration.");
            exception.printStackTrace();
            ShopConfig fallback = new ShopConfig();
            fallback.normalize();
            return fallback;
        }
    }

    public synchronized void save() {
        try {
            normalize();
            Files.createDirectories(DIRECTORY);
            Path temporary = DIRECTORY.resolve("shop.json.tmp");
            Files.writeString(temporary, GSON.toJson(this), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            System.err.println("[ClassicGUIShop] Could not save shop.json");
            exception.printStackTrace();
        }
    }

    public Category category(String id) {
        if (id == null) return null;
        for (Category category : categories) {
            if (category.id.equalsIgnoreCase(id)) return category;
        }
        return null;
    }

    public FoundItem findItem(String itemId) {
        if (itemId == null) return null;
        for (Category category : categories) {
            for (ShopItem item : category.items) {
                if (item.item.equalsIgnoreCase(itemId)) return new FoundItem(category, item);
            }
        }
        return null;
    }

    public List<FoundItem> findItems(String itemId) {
        List<FoundItem> found = new ArrayList<>();
        if (itemId == null) return found;
        for (Category category : categories) {
            for (ShopItem item : category.items) {
                if (item.item.equalsIgnoreCase(itemId)) found.add(new FoundItem(category, item));
            }
        }
        return found;
    }

    public synchronized ShopItem addOrUpdateItem(String categoryId, String itemId, String name, double buy, double sell) {
        Category target = category(categoryId);
        if (target == null) return null;

        ShopItem item = null;
        for (FoundItem found : findItems(itemId)) {
            if (item == null) item = found.item;
            found.category.items.remove(found.item);
        }
        if (item == null) item = new ShopItem();

        item.item = itemId;
        item.name = name;
        item.buy = buy;
        item.sell = sell;
        target.items.add(item);
        save();
        return item;
    }

    public synchronized int removeItemEverywhere(String itemId) {
        int removed = 0;
        for (Category category : categories) {
            int before = category.items.size();
            category.items.removeIf(item -> item.item.equalsIgnoreCase(itemId));
            removed += before - category.items.size();
        }
        if (removed > 0) save();
        return removed;
    }

    public synchronized boolean updatePrices(String itemId, double buy, double sell) {
        boolean changed = false;
        for (FoundItem found : findItems(itemId)) {
            found.item.buy = buy;
            found.item.sell = sell;
            changed = true;
        }
        if (changed) save();
        return changed;
    }

    public synchronized boolean moveItem(String itemId, String categoryId) {
        Category target = category(categoryId);
        FoundItem found = findItem(itemId);
        if (target == null || found == null) return false;
        if (found.category == target) return true;
        found.category.items.remove(found.item);
        target.items.add(found.item);
        save();
        return true;
    }

    public synchronized Category createCategory(String id, String displayName, String icon) {
        if (id == null || id.isBlank() || category(id) != null) return null;
        Category category = new Category();
        category.id = sanitizeId(id);
        category.name = displayName == null || displayName.isBlank() ? category.id : displayName;
        category.icon = icon == null || icon.isBlank() ? "minecraft:chest" : icon;
        categories.add(category);
        save();
        return category;
    }

    public synchronized boolean removeEmptyCategory(String id) {
        Category category = category(id);
        if (category == null || !category.items.isEmpty()) return false;
        categories.remove(category);
        save();
        return true;
    }

    public int permissionLevel(String node, int fallback) {
        if (permissionDefaults == null) return fallback;
        return permissionDefaults.getOrDefault(node, fallback);
    }

    public String money(double amount) {
        return currencySymbol + String.format(Locale.US, "%,.2f", amount);
    }

    public boolean creativeTransactionsAllowed() {
        return Boolean.TRUE.equals(allowCreativeTransactions);
    }

    public boolean offlinePaymentsAllowed() {
        return !Boolean.FALSE.equals(allowOfflinePayments);
    }

    private void normalize() {
        if (currencySymbol == null) currencySymbol = "$";
        if (categories == null) categories = new ArrayList<>();
        if (permissionDefaults == null) permissionDefaults = new LinkedHashMap<>();
        if (allowCreativeTransactions == null) allowCreativeTransactions = false;
        if (allowOfflinePayments == null) allowOfflinePayments = true;
        if (!Double.isFinite(startingBalance) || startingBalance < 0) startingBalance = 0;
        if (!Double.isFinite(priceMultiplier) || priceMultiplier < 0) priceMultiplier = 1;

        addPermissionDefault("guishop.command.shop", 0);
        addPermissionDefault("guishop.command.balance", 0);
        addPermissionDefault("guishop.command.pay", 0);
        addPermissionDefault("guishop.command.sellhand", 0);
        addPermissionDefault("guishop.command.worth", 0);
        addPermissionDefault("guishop.buy", 0);
        addPermissionDefault("guishop.sell", 0);
        addPermissionDefault("guishop.creative.bypass", 2);
        addPermissionDefault("guishop.admin", 2);
        addPermissionDefault("guishop.admin.item.add", 2);
        addPermissionDefault("guishop.admin.item.remove", 2);
        addPermissionDefault("guishop.admin.item.list", 2);
        addPermissionDefault("guishop.admin.item.price", 2);
        addPermissionDefault("guishop.admin.item.category", 2);
        addPermissionDefault("guishop.admin.category", 2);
        addPermissionDefault("guishop.admin.reload", 2);
        addPermissionDefault("guishop.admin.multiplier", 2);
        addPermissionDefault("guishop.admin.balance", 2);

        for (Category category : categories) {
            if (category.id == null || category.id.isBlank()) category.id = "category";
            category.id = sanitizeId(category.id);
            if (category.name == null) category.name = category.id;
            if (category.icon == null) category.icon = "minecraft:chest";
            if (category.items == null) category.items = new ArrayList<>();
            addPermissionDefault("guishop.category." + category.id, 0);
            for (ShopItem item : category.items) {
                if (item.item == null) item.item = "minecraft:barrier";
                if (item.name == null) item.name = item.item;
                if (!Double.isFinite(item.buy) || item.buy < 0) item.buy = 0;
                if (!Double.isFinite(item.sell) || item.sell < 0) item.sell = 0;
            }
        }
    }

    private void addPermissionDefault(String node, int level) {
        permissionDefaults.putIfAbsent(node, level);
    }

    private static String sanitizeId(String id) {
        String value = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return value.isBlank() ? "category" : value;
    }

    public record FoundItem(Category category, ShopItem item) {}

    public static final class Category {
        public String id;
        public String name;
        public String icon;
        public List<ShopItem> items = new ArrayList<>();
    }

    public static final class ShopItem {
        public String item;
        public String name;
        public double buy;
        public double sell;
    }
}
