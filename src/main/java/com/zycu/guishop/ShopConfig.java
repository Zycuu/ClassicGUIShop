package com.zycu.guishop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

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

    public Boolean autoPopulateVanillaCatalog = true;
    public double generatedVanillaBuyPrice = 1.0;
    public double generatedVanillaSellPrice = 0.0;
    public List<String> disabledDefaultCategories = new ArrayList<>();

    public Boolean enchantmentsEnabled = true;
    public double defaultEnchantmentPricePerLevel = 100.0;
    public Map<String, EnchantmentOffer> enchantments = new LinkedHashMap<>();
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

    public synchronized void ensureEnchantmentDefaults(MinecraftServer server) {
        Registry<Enchantment> registry = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        boolean changed = false;
        for (Map.Entry<net.minecraft.resources.ResourceKey<Enchantment>, Enchantment> entry : registry.entrySet()) {
            String id = entry.getKey().identifier().toString();
            if (!enchantments.containsKey(id)) {
                EnchantmentOffer offer = new EnchantmentOffer();
                offer.name = entry.getValue().description().getString();
                offer.pricePerLevel = defaultEnchantmentPricePerLevel;
                offer.maxLevel = entry.getValue().getMaxLevel();
                offer.enabled = true;
                enchantments.put(id, offer);
                changed = true;
            }
        }
        if (changed) save();
    }

    public boolean autoPopulateVanillaCatalog() {
        return !Boolean.FALSE.equals(autoPopulateVanillaCatalog);
    }

    public Category category(String id) {
        if (id == null) return null;
        for (Category category : categories) {
            if (category.id.equalsIgnoreCase(id)) return category;
        }
        return null;
    }

    public synchronized Category ensureDefaultCategory(String id, String displayName, String icon) {
        String cleanId = sanitizeId(id);
        if (disabledDefaultCategories.stream().anyMatch(disabled -> disabled.equalsIgnoreCase(cleanId))) return null;
        Category existing = category(cleanId);
        if (existing != null) return existing;

        Category category = new Category();
        category.id = cleanId;
        category.name = displayName;
        category.icon = icon;
        categories.add(category);
        addPermissionDefault("guishop.category." + cleanId, 0);
        return category;
    }

    public FoundItem findItem(String identifier) {
        if (identifier == null || identifier.isBlank()) return null;
        String raw = identifier.trim();
        for (Category category : categories) {
            for (ShopItem item : category.items) {
                if (item.listingId != null && item.listingId.equalsIgnoreCase(raw)) return new FoundItem(category, item);
            }
        }

        String itemId = raw.contains("#") ? raw : normalizeIdentifier(raw);
        for (Category category : categories) {
            for (ShopItem item : category.items) {
                if (item.item.equalsIgnoreCase(itemId)) return new FoundItem(category, item);
            }
        }
        return null;
    }

    public FoundItem findItem(ItemStack stack, RegistryAccess access) {
        if (stack == null || stack.isEmpty()) return null;
        String listingId = ItemStackData.listingId(stack.copyWithCount(1), access);
        for (Category category : categories) {
            for (ShopItem item : category.items) {
                if (listingId.equalsIgnoreCase(item.listingId)) return new FoundItem(category, item);
            }
        }
        for (Category category : categories) {
            for (ShopItem item : category.items) {
                if (ItemStackData.same(item.createStack(access), stack)) return new FoundItem(category, item);
            }
        }
        return null;
    }

    public List<FoundItem> findItems(String identifier) {
        List<FoundItem> exact = new ArrayList<>();
        if (identifier == null || identifier.isBlank()) return exact;
        String raw = identifier.trim();
        for (Category category : categories) {
            for (ShopItem item : category.items) {
                if (item.listingId != null && item.listingId.equalsIgnoreCase(raw)) exact.add(new FoundItem(category, item));
            }
        }
        if (!exact.isEmpty()) return exact;

        String itemId = raw.contains("#") ? raw : normalizeIdentifier(raw);
        List<FoundItem> found = new ArrayList<>();
        for (Category category : categories) {
            for (ShopItem item : category.items) {
                if (item.item.equalsIgnoreCase(itemId)) found.add(new FoundItem(category, item));
            }
        }
        return found;
    }

    public synchronized ShopItem addOrUpdateItem(
        String categoryId,
        ItemStack stack,
        String name,
        double buy,
        double sell,
        RegistryAccess access
    ) {
        Category target = category(categoryId);
        if (target == null || stack == null || stack.isEmpty()) return null;

        ItemStack template = stack.copyWithCount(1);
        String listingId = ItemStackData.listingId(template, access);
        ShopItem item = null;
        for (Category category : categories) {
            for (int index = category.items.size() - 1; index >= 0; index--) {
                ShopItem candidate = category.items.get(index);
                if (listingId.equalsIgnoreCase(candidate.listingId)
                    || ItemStackData.same(candidate.createStack(access), template)) {
                    if (item == null) item = candidate;
                    category.items.remove(index);
                }
            }
        }
        if (item == null) item = new ShopItem();

        item.item = ShopService.itemId(template);
        item.listingId = listingId;
        item.stack = ItemStackData.encode(template, access);
        item.name = name;
        item.buy = buy;
        item.sell = sell;
        target.items.add(item);
        save();
        return item;
    }

    public synchronized ShopItem addGeneratedItem(
        String categoryId,
        ItemStack stack,
        double buy,
        double sell,
        RegistryAccess access
    ) {
        Category target = category(categoryId);
        if (target == null || stack == null || stack.isEmpty()) return null;
        FoundItem existing = findItem(stack, access);
        if (existing != null) return existing.item;

        ItemStack template = stack.copyWithCount(1);
        ShopItem item = new ShopItem();
        item.item = ShopService.itemId(template);
        item.listingId = ItemStackData.listingId(template, access);
        item.stack = ItemStackData.encode(template, access);
        item.name = template.getHoverName().getString();
        item.buy = buy;
        item.sell = sell;
        target.items.add(item);
        return item;
    }

    public synchronized int removeItemEverywhere(ItemStack stack, RegistryAccess access) {
        if (stack == null || stack.isEmpty()) return 0;
        int removed = 0;
        String listingId = ItemStackData.listingId(stack.copyWithCount(1), access);
        for (Category category : categories) {
            int before = category.items.size();
            category.items.removeIf(item -> listingId.equalsIgnoreCase(item.listingId)
                || ItemStackData.same(item.createStack(access), stack));
            removed += before - category.items.size();
        }
        if (removed > 0) save();
        return removed;
    }

    public synchronized int removeItemEverywhere(String identifier) {
        List<FoundItem> found = findItems(identifier);
        for (FoundItem entry : found) entry.category.items.remove(entry.item);
        if (!found.isEmpty()) save();
        return found.size();
    }

    public synchronized boolean updatePrices(String identifier, double buy, double sell) {
        boolean changed = false;
        for (FoundItem found : findItems(identifier)) {
            found.item.buy = buy;
            found.item.sell = sell;
            changed = true;
        }
        if (changed) save();
        return changed;
    }

    public synchronized boolean moveItem(String identifier, String categoryId) {
        Category target = category(categoryId);
        List<FoundItem> foundItems = findItems(identifier);
        if (target == null || foundItems.isEmpty()) return false;
        for (FoundItem found : foundItems) {
            if (found.category != target) {
                found.category.items.remove(found.item);
                target.items.add(found.item);
            }
        }
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
        disabledDefaultCategories.removeIf(disabled -> disabled.equalsIgnoreCase(category.id));
        save();
        return category;
    }

    public synchronized RemovedCategory removeCategoryAndContents(String id) {
        Category category = category(id);
        if (category == null) return null;
        int removedListings = category.items.size();
        categories.remove(category);
        if (disabledDefaultCategories.stream().noneMatch(disabled -> disabled.equalsIgnoreCase(category.id))) {
            disabledDefaultCategories.add(category.id);
        }
        save();
        return new RemovedCategory(category.id, removedListings);
    }

    public EnchantmentOffer enchantmentOffer(String enchantmentId) {
        if (enchantmentId == null) return null;
        return enchantments.get(normalizeIdentifier(enchantmentId));
    }

    public synchronized EnchantmentOffer setEnchantmentOffer(String enchantmentId, String displayName, double pricePerLevel, int maxLevel) {
        String id = normalizeIdentifier(enchantmentId);
        EnchantmentOffer offer = enchantments.computeIfAbsent(id, ignored -> new EnchantmentOffer());
        if (displayName != null && !displayName.isBlank()) offer.name = displayName;
        offer.pricePerLevel = pricePerLevel;
        offer.maxLevel = maxLevel;
        offer.enabled = true;
        save();
        return offer;
    }

    public synchronized boolean disableEnchantment(String enchantmentId) {
        EnchantmentOffer offer = enchantmentOffer(enchantmentId);
        if (offer == null) return false;
        offer.enabled = false;
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

    public boolean enchantmentsEnabled() {
        return !Boolean.FALSE.equals(enchantmentsEnabled);
    }

    private void normalize() {
        if (currencySymbol == null) currencySymbol = "$";
        if (categories == null) categories = new ArrayList<>();
        if (permissionDefaults == null) permissionDefaults = new LinkedHashMap<>();
        if (enchantments == null) enchantments = new LinkedHashMap<>();
        if (disabledDefaultCategories == null) disabledDefaultCategories = new ArrayList<>();
        if (allowCreativeTransactions == null) allowCreativeTransactions = false;
        if (allowOfflinePayments == null) allowOfflinePayments = true;
        if (autoPopulateVanillaCatalog == null) autoPopulateVanillaCatalog = true;
        if (enchantmentsEnabled == null) enchantmentsEnabled = true;
        if (!Double.isFinite(startingBalance) || startingBalance < 0) startingBalance = 0;
        if (!Double.isFinite(priceMultiplier) || priceMultiplier < 0) priceMultiplier = 1;
        if (!Double.isFinite(generatedVanillaBuyPrice) || generatedVanillaBuyPrice < 0) generatedVanillaBuyPrice = 1;
        if (!Double.isFinite(generatedVanillaSellPrice) || generatedVanillaSellPrice < 0) generatedVanillaSellPrice = 0;
        if (!Double.isFinite(defaultEnchantmentPricePerLevel) || defaultEnchantmentPricePerLevel < 0) defaultEnchantmentPricePerLevel = 100;

        addPermissionDefault("guishop.command.shop", 0);
        addPermissionDefault("guishop.command.balance", 0);
        addPermissionDefault("guishop.command.pay", 0);
        addPermissionDefault("guishop.command.sellhand", 0);
        addPermissionDefault("guishop.command.worth", 0);
        addPermissionDefault("guishop.buy", 0);
        addPermissionDefault("guishop.sell", 0);
        addPermissionDefault("guishop.enchant", 0);
        addPermissionDefault("guishop.creative.bypass", 2);
        addPermissionDefault("guishop.admin", 2);
        addPermissionDefault("guishop.admin.item.add", 2);
        addPermissionDefault("guishop.admin.item.remove", 2);
        addPermissionDefault("guishop.admin.item.list", 2);
        addPermissionDefault("guishop.admin.item.price", 2);
        addPermissionDefault("guishop.admin.item.category", 2);
        addPermissionDefault("guishop.admin.category", 2);
        addPermissionDefault("guishop.admin.enchant", 2);
        addPermissionDefault("guishop.admin.reload", 2);
        addPermissionDefault("guishop.admin.multiplier", 2);
        addPermissionDefault("guishop.admin.balance", 2);

        for (Map.Entry<String, EnchantmentOffer> entry : new ArrayList<>(enchantments.entrySet())) {
            String normalizedId = normalizeIdentifier(entry.getKey());
            EnchantmentOffer offer = entry.getValue();
            if (offer == null) offer = new EnchantmentOffer();
            if (offer.name == null || offer.name.isBlank()) offer.name = normalizedId;
            if (!Double.isFinite(offer.pricePerLevel) || offer.pricePerLevel < 0) offer.pricePerLevel = defaultEnchantmentPricePerLevel;
            if (offer.maxLevel < 0) offer.maxLevel = 0;
            if (offer.enabled == null) offer.enabled = true;
            if (!normalizedId.equals(entry.getKey())) {
                enchantments.remove(entry.getKey());
                enchantments.put(normalizedId, offer);
            } else {
                enchantments.put(normalizedId, offer);
            }
        }

        for (Category category : categories) {
            if (category.id == null || category.id.isBlank()) category.id = "category";
            category.id = sanitizeId(category.id);
            if (category.name == null) category.name = category.id;
            if (category.icon == null) category.icon = "minecraft:chest";
            if (category.items == null) category.items = new ArrayList<>();
            addPermissionDefault("guishop.category." + category.id, 0);
            for (ShopItem item : category.items) {
                if (item.item == null) item.item = "minecraft:barrier";
                item.item = normalizeIdentifier(item.item);
                if (item.listingId == null || item.listingId.isBlank()) item.listingId = item.item;
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

    public static String normalizeIdentifier(String input) {
        String value = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        return value.contains(":") ? value : "minecraft:" + value;
    }

    public record FoundItem(Category category, ShopItem item) {}
    public record RemovedCategory(String id, int removedListings) {}

    public static final class Category {
        public String id;
        public String name;
        public String icon;
        public List<ShopItem> items = new ArrayList<>();
    }

    public static final class ShopItem {
        public String item;
        public String listingId;
        public JsonElement stack;
        public String name;
        public double buy;
        public double sell;

        public ItemStack createStack(RegistryAccess access) {
            return ItemStackData.decode(stack, item, access);
        }
    }

    public static final class EnchantmentOffer {
        public String name;
        public double pricePerLevel = 100.0;
        public int maxLevel = 0;
        public Boolean enabled = true;

        public boolean enabled() {
            return !Boolean.FALSE.equals(enabled) && pricePerLevel > 0;
        }
    }
}
