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
    private static final Path LEGACY_FILE = DIRECTORY.resolve("shop.json");
    private static final Path SETTINGS_FILE = DIRECTORY.resolve("settings.json");
    private static final Path SHOPS_FILE = DIRECTORY.resolve("shops.json");
    private static final Path ENCHANTMENTS_FILE = DIRECTORY.resolve("enchantments.json");

    public String currencySymbol = "$";
    public double startingBalance = 500.0;
    public double priceMultiplier = 1.0;
    public Boolean allowCreativeTransactions = false;
    public Boolean allowOfflinePayments = true;

    public Boolean autoPopulateVanillaCatalog = true;
    public Boolean purgeUnobtainableOnSync = true;
    public Boolean balancedPricingEnabled = true;
    public Boolean rebalanceGeneratedItems = true;
    public double catalogSellRatio = 0.32;
    public double minimumBuyPrice = 0.25;
    public List<String> disabledDefaultCategories = new ArrayList<>();

    public String chatPrefix = "[ShopGUI]";
    public String chatPrefixColor = "gold";
    public String chatInfoColor = "aqua";
    public String chatSuccessColor = "green";
    public String chatWarningColor = "yellow";
    public String chatErrorColor = "red";
    public String chatAdminColor = "light_purple";

    public Boolean enchantmentsEnabled = true;
    public double defaultEnchantmentPricePerLevel = 100.0;
    public Map<String, EnchantmentOffer> enchantments = new LinkedHashMap<>();
    public Map<String, Integer> permissionDefaults = new LinkedHashMap<>();
    public List<Category> categories = new ArrayList<>();

    public static ShopConfig load() {
        try {
            Files.createDirectories(DIRECTORY);
            ShopConfig config;

            boolean hasNewFiles = Files.exists(SETTINGS_FILE) || Files.exists(SHOPS_FILE) || Files.exists(ENCHANTMENTS_FILE);
            if (!hasNewFiles && Files.exists(LEGACY_FILE)) {
                LegacyConfig legacy = GSON.fromJson(Files.readString(LEGACY_FILE, StandardCharsets.UTF_8), LegacyConfig.class);
                config = fromLegacy(legacy);
                config.normalize();
                config.save();
                Path backup = DIRECTORY.resolve("shop.json.migrated-backup");
                Files.move(LEGACY_FILE, backup, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[ClassicGUIShop] Migrated shop.json into settings.json, shops.json, and enchantments.json.");
                return config;
            }

            config = new ShopConfig();
            if (Files.exists(SETTINGS_FILE)) config.applySettings(read(SETTINGS_FILE, SettingsFile.class));
            if (Files.exists(SHOPS_FILE)) config.applyShops(read(SHOPS_FILE, ShopsFile.class));
            if (Files.exists(ENCHANTMENTS_FILE)) config.applyEnchantments(read(ENCHANTMENTS_FILE, EnchantmentsFile.class));
            config.normalize();
            config.save();
            return config;
        } catch (Exception exception) {
            System.err.println("[ClassicGUIShop] Could not load configuration files. Using safe in-memory defaults.");
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
            atomicWrite(SETTINGS_FILE, GSON.toJson(toSettingsFile()));
            atomicWrite(SHOPS_FILE, GSON.toJson(toShopsFile()));
            atomicWrite(ENCHANTMENTS_FILE, GSON.toJson(toEnchantmentsFile()));
        } catch (Exception exception) {
            System.err.println("[ClassicGUIShop] Could not save configuration files.");
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

    public boolean purgeUnobtainableOnSync() {
        return !Boolean.FALSE.equals(purgeUnobtainableOnSync);
    }

    public boolean balancedPricingEnabled() {
        return !Boolean.FALSE.equals(balancedPricingEnabled);
    }

    public boolean rebalanceGeneratedItems() {
        return !Boolean.FALSE.equals(rebalanceGeneratedItems);
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
        item.manualPrice = true;
        item.pricingModelVersion = BalancedPricing.MODEL_VERSION;
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
        item.manualPrice = false;
        item.pricingModelVersion = BalancedPricing.MODEL_VERSION;
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
            found.item.manualPrice = true;
            found.item.pricingModelVersion = BalancedPricing.MODEL_VERSION;
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
        if (purgeUnobtainableOnSync == null) purgeUnobtainableOnSync = true;
        if (balancedPricingEnabled == null) balancedPricingEnabled = true;
        if (rebalanceGeneratedItems == null) rebalanceGeneratedItems = true;
        if (enchantmentsEnabled == null) enchantmentsEnabled = true;
        if (!Double.isFinite(startingBalance) || startingBalance < 0) startingBalance = 0;
        if (!Double.isFinite(priceMultiplier) || priceMultiplier < 0) priceMultiplier = 1;
        if (!Double.isFinite(catalogSellRatio) || catalogSellRatio < 0 || catalogSellRatio > 1) catalogSellRatio = 0.32;
        if (!Double.isFinite(minimumBuyPrice) || minimumBuyPrice < 0) minimumBuyPrice = 0.25;
        if (!Double.isFinite(defaultEnchantmentPricePerLevel) || defaultEnchantmentPricePerLevel < 0) defaultEnchantmentPricePerLevel = 100;

        if (chatPrefix == null || chatPrefix.isBlank()) chatPrefix = "[ShopGUI]";
        if (chatPrefixColor == null) chatPrefixColor = "gold";
        if (chatInfoColor == null) chatInfoColor = "aqua";
        if (chatSuccessColor == null) chatSuccessColor = "green";
        if (chatWarningColor == null) chatWarningColor = "yellow";
        if (chatErrorColor == null) chatErrorColor = "red";
        if (chatAdminColor == null) chatAdminColor = "light_purple";

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
                if (item.manualPrice == null) item.manualPrice = item.listingId.contains("#");
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

    private static <T> T read(Path path, Class<T> type) throws Exception {
        return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
    }

    private static void atomicWrite(Path path, String content) throws Exception {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static ShopConfig fromLegacy(LegacyConfig legacy) {
        ShopConfig config = new ShopConfig();
        if (legacy == null) return config;
        if (legacy.currencySymbol != null) config.currencySymbol = legacy.currencySymbol;
        config.startingBalance = legacy.startingBalance;
        config.priceMultiplier = legacy.priceMultiplier;
        config.allowCreativeTransactions = legacy.allowCreativeTransactions;
        config.allowOfflinePayments = legacy.allowOfflinePayments;
        config.autoPopulateVanillaCatalog = legacy.autoPopulateVanillaCatalog;
        config.disabledDefaultCategories = legacy.disabledDefaultCategories == null ? new ArrayList<>() : legacy.disabledDefaultCategories;
        config.enchantmentsEnabled = legacy.enchantmentsEnabled;
        config.defaultEnchantmentPricePerLevel = legacy.defaultEnchantmentPricePerLevel;
        config.enchantments = legacy.enchantments == null ? new LinkedHashMap<>() : legacy.enchantments;
        config.permissionDefaults = legacy.permissionDefaults == null ? new LinkedHashMap<>() : legacy.permissionDefaults;
        config.categories = legacy.categories == null ? new ArrayList<>() : legacy.categories;
        return config;
    }

    private void applySettings(SettingsFile file) {
        if (file == null) return;
        if (file.general != null) {
            priceMultiplier = file.general.priceMultiplier;
            allowCreativeTransactions = file.general.allowCreativeTransactions;
            allowOfflinePayments = file.general.allowOfflinePayments;
        }
        if (file.economy != null) {
            currencySymbol = file.economy.currencySymbol;
            startingBalance = file.economy.startingBalance;
        }
        if (file.catalog != null) {
            autoPopulateVanillaCatalog = file.catalog.autoPopulateVanillaCatalog;
            purgeUnobtainableOnSync = file.catalog.purgeUnobtainableOnSync;
            balancedPricingEnabled = file.catalog.balancedPricingEnabled;
            rebalanceGeneratedItems = file.catalog.rebalanceGeneratedItems;
            catalogSellRatio = file.catalog.sellRatio;
            minimumBuyPrice = file.catalog.minimumBuyPrice;
            disabledDefaultCategories = file.catalog.disabledDefaultCategories;
        }
        if (file.chat != null) {
            chatPrefix = file.chat.prefix;
            chatPrefixColor = file.chat.prefixColor;
            chatInfoColor = file.chat.infoColor;
            chatSuccessColor = file.chat.successColor;
            chatWarningColor = file.chat.warningColor;
            chatErrorColor = file.chat.errorColor;
            chatAdminColor = file.chat.adminColor;
        }
        if (file.permissionDefaults != null) permissionDefaults = file.permissionDefaults;
    }

    private void applyShops(ShopsFile file) {
        if (file != null && file.categories != null) categories = file.categories;
    }

    private void applyEnchantments(EnchantmentsFile file) {
        if (file == null) return;
        enchantmentsEnabled = file.enabled;
        defaultEnchantmentPricePerLevel = file.defaultPricePerLevel;
        if (file.offers != null) enchantments = file.offers;
    }

    private SettingsFile toSettingsFile() {
        SettingsFile file = new SettingsFile();
        file._about = about();
        file._notes = settingsNotes();
        file.general.priceMultiplier = priceMultiplier;
        file.general.allowCreativeTransactions = allowCreativeTransactions;
        file.general.allowOfflinePayments = allowOfflinePayments;
        file.economy.currencySymbol = currencySymbol;
        file.economy.startingBalance = startingBalance;
        file.catalog.autoPopulateVanillaCatalog = autoPopulateVanillaCatalog;
        file.catalog.purgeUnobtainableOnSync = purgeUnobtainableOnSync;
        file.catalog.balancedPricingEnabled = balancedPricingEnabled;
        file.catalog.rebalanceGeneratedItems = rebalanceGeneratedItems;
        file.catalog.sellRatio = catalogSellRatio;
        file.catalog.minimumBuyPrice = minimumBuyPrice;
        file.catalog.disabledDefaultCategories = disabledDefaultCategories;
        file.chat.prefix = chatPrefix;
        file.chat.prefixColor = chatPrefixColor;
        file.chat.infoColor = chatInfoColor;
        file.chat.successColor = chatSuccessColor;
        file.chat.warningColor = chatWarningColor;
        file.chat.errorColor = chatErrorColor;
        file.chat.adminColor = chatAdminColor;
        file.permissionDefaults = permissionDefaults;
        return file;
    }

    private ShopsFile toShopsFile() {
        ShopsFile file = new ShopsFile();
        file._about = about();
        file._notes = List.of(
            "Each category controls one menu in /shop.",
            "Items with manualPrice=true are never changed by automatic economy balancing.",
            "Component-rich items use stack data and a #hash listing ID so variants can have separate prices.",
            "Use /adminshop commands whenever possible instead of editing large stack objects by hand."
        );
        file.categories = categories;
        return file;
    }

    private EnchantmentsFile toEnchantmentsFile() {
        EnchantmentsFile file = new EnchantmentsFile();
        file._about = about();
        file._notes = List.of(
            "Players buy enchanted books. Enchantments are never applied directly to equipment.",
            "pricePerLevel is multiplied by the selected enchantment level.",
            "maxLevel=0 means the normal vanilla maximum level.",
            "Set enabled=false to hide an enchantment from the shop."
        );
        file.enabled = enchantmentsEnabled;
        file.defaultPricePerLevel = defaultEnchantmentPricePerLevel;
        file.offers = enchantments;
        return file;
    }

    private static List<String> about() {
        return List.of(
            "Thank you for using ClassicGUIShop!",
            "ClassicGUIShop is maintained by Zycu.",
            "Credit to the original Bukkit GUIShop creators, _Waffles_ and AlreadyCoded.",
            "Need help? Send a Discord friend request to: zycu",
            "Keys beginning with _ are documentation for humans and are ignored by the mod."
        );
    }

    private static Map<String, String> settingsNotes() {
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("general", "Global behavior such as the all-price multiplier and creative-mode transactions.");
        notes.put("economy", "Currency display and the balance assigned to a player the first time they are seen.");
        notes.put("catalog", "Controls vanilla item synchronization, unobtainable-item cleanup, and automatic balanced prices.");
        notes.put("chat", "Minecraft ChatFormatting color names are supported, such as gold, aqua, green, yellow, red, and light_purple.");
        notes.put("permissionDefaults", "Values are fallback OP levels. 0 means everyone; 2 means normal server operators.");
        return notes;
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
        public Boolean manualPrice;
        public int pricingModelVersion;

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

    private static final class SettingsFile {
        List<String> _about = new ArrayList<>();
        Map<String, String> _notes = new LinkedHashMap<>();
        GeneralSection general = new GeneralSection();
        EconomySection economy = new EconomySection();
        CatalogSection catalog = new CatalogSection();
        ChatSection chat = new ChatSection();
        Map<String, Integer> permissionDefaults = new LinkedHashMap<>();
    }

    private static final class GeneralSection {
        double priceMultiplier = 1.0;
        Boolean allowCreativeTransactions = false;
        Boolean allowOfflinePayments = true;
    }

    private static final class EconomySection {
        String currencySymbol = "$";
        double startingBalance = 500.0;
    }

    private static final class CatalogSection {
        Boolean autoPopulateVanillaCatalog = true;
        Boolean purgeUnobtainableOnSync = true;
        Boolean balancedPricingEnabled = true;
        Boolean rebalanceGeneratedItems = true;
        double sellRatio = 0.32;
        double minimumBuyPrice = 0.25;
        List<String> disabledDefaultCategories = new ArrayList<>();
    }

    private static final class ChatSection {
        String prefix = "[ShopGUI]";
        String prefixColor = "gold";
        String infoColor = "aqua";
        String successColor = "green";
        String warningColor = "yellow";
        String errorColor = "red";
        String adminColor = "light_purple";
    }

    private static final class ShopsFile {
        List<String> _about = new ArrayList<>();
        List<String> _notes = new ArrayList<>();
        List<Category> categories = new ArrayList<>();
    }

    private static final class EnchantmentsFile {
        List<String> _about = new ArrayList<>();
        List<String> _notes = new ArrayList<>();
        Boolean enabled = true;
        double defaultPricePerLevel = 100.0;
        Map<String, EnchantmentOffer> offers = new LinkedHashMap<>();
    }

    private static final class LegacyConfig {
        String currencySymbol = "$";
        double startingBalance = 500.0;
        double priceMultiplier = 1.0;
        Boolean allowCreativeTransactions = false;
        Boolean allowOfflinePayments = true;
        Boolean autoPopulateVanillaCatalog = true;
        List<String> disabledDefaultCategories = new ArrayList<>();
        Boolean enchantmentsEnabled = true;
        double defaultEnchantmentPricePerLevel = 100.0;
        Map<String, EnchantmentOffer> enchantments = new LinkedHashMap<>();
        Map<String, Integer> permissionDefaults = new LinkedHashMap<>();
        List<Category> categories = new ArrayList<>();
    }
}
