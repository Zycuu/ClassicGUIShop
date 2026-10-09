package com.zycu.guishop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;

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
    // Preserve the metadata version on existing generated listings during the rebuild.
    public static final int GENERATED_PRICING_MODEL_VERSION = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("guishop");
    private static final Path LEGACY_FILE = DIRECTORY.resolve("shop.json");
    private static final Path SETTINGS_FILE = DIRECTORY.resolve("settings.json");
    private static final Path SHOPS_FILE = DIRECTORY.resolve("shops.json");
    private static final Path ENCHANTMENTS_FILE = DIRECTORY.resolve("enchantments.json");

    public String currencySymbol = "$";
    public String shopTitle = "ClassicGUIShop";
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
            saveChecked();
        } catch (Exception exception) {
            System.err.println("[ClassicGUIShop] Could not save configuration files.");
            exception.printStackTrace();
        }
    }

    /** Editor commits must propagate a write failure instead of reporting false success. */
    public synchronized void saveChecked() throws Exception {
        normalize();
        Files.createDirectories(DIRECTORY);
        atomicWrite(SETTINGS_FILE, GSON.toJson(toSettingsFile()));
        atomicWrite(SHOPS_FILE, GSON.toJson(toShopsFile()));
        atomicWrite(ENCHANTMENTS_FILE, GSON.toJson(toEnchantmentsFile()));
    }

    public Category category(String id) {
        if (id == null) return null;
        for (Category category : categories) {
            if (category.id.equalsIgnoreCase(id)) return category;
        }
        return null;
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
        for (Category category : categories) {
            for (ShopItem item : category.items) {
                if (ItemStackData.same(item.createStack(access), stack)) return new FoundItem(category, item);
            }
        }
        return null;
    }

    public EnchantmentOffer enchantmentOffer(String enchantmentId) {
        if (enchantmentId == null) return null;
        return enchantments.get(normalizeIdentifier(enchantmentId));
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
        if (shopTitle == null || shopTitle.isBlank()) shopTitle = "ClassicGUIShop";
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
            if (category.description == null) category.description = "";
            if (category.icon == null) category.icon = "minecraft:chest";
            if (category.items == null) category.items = new ArrayList<>();
            addPermissionDefault("guishop.category." + category.id, 0);
            for (ShopItem item : category.items) {
                if (item.item == null) item.item = "minecraft:barrier";
                item.item = normalizeIdentifier(item.item);
                if (item.listingId == null || item.listingId.isBlank()) item.listingId = item.item;
                if (item.name == null) item.name = item.item;
                if (item.description == null) item.description = "";
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
            shopTitle = file.general.shopTitle;
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
        file.general.shopTitle = shopTitle;
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
        file._notes = shopNotes();
        file.categories = categories;
        return file;
    }

    private EnchantmentsFile toEnchantmentsFile() {
        EnchantmentsFile file = new EnchantmentsFile();
        file._about = about();
        file._notes = enchantmentNotes();
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
            "It's been to long since we had a ShopGUI like the one from the bukkit era, hopefully this helps helps make your server economies awesome!"
        );
    }

    private static Map<String, String> settingsNotes() {
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("general.shopTitle", "Title displayed at the root of the vanilla player shop.");
        notes.put("general.priceMultiplier", "Multiplies every buy and sell price. 1.0 keeps listed prices unchanged, 2.0 doubles them, and 0.5 halves them.");
        notes.put("general.allowCreativeTransactions", "When true, players in Creative mode may buy and sell. When false, Creative transactions require the guishop.creative.bypass permission.");
        notes.put("general.allowOfflinePayments", "When true, /shop pay may send money to a player who is offline but has joined before. When false, the recipient must be online.");
        notes.put("economy.currencySymbol", "Text displayed before money values, such as $, €, or Coins: . This changes display only.");
        notes.put("economy.startingBalance", "Balance assigned when ClassicGUIShop first creates a balance record for a player. Changing it does not alter existing balances.");
        notes.put("catalog.autoPopulateVanillaCatalog", "Legacy catalog setting preserved for compatibility; automatic item detection and generated pricing have been removed.");
        notes.put("catalog.purgeUnobtainableOnSync", "Legacy catalog setting preserved for compatibility; automatic item detection and generated pricing have been removed.");
        notes.put("catalog.balancedPricingEnabled", "Legacy catalog setting preserved for compatibility; automatic item detection and generated pricing have been removed.");
        notes.put("catalog.rebalanceGeneratedItems", "Legacy catalog setting preserved for compatibility; automatic item detection and generated pricing have been removed.");
        notes.put("catalog.sellRatio", "Sell ratio retained for configured enchanted book offers. Automatic catalog pricing is removed.");
        notes.put("catalog.minimumBuyPrice", "Legacy catalog setting preserved for compatibility; automatic item detection and generated pricing have been removed.");
        notes.put("catalog.disabledDefaultCategories", "Legacy category exclusions retained; automatic catalog creation is removed.");
        notes.put("chat.prefix", "Text placed at the start of every ClassicGUIShop chat message.");
        notes.put("chat.prefixColor", "Minecraft color name used for the chat prefix, such as gold, aqua, green, yellow, red, or light_purple.");
        notes.put("chat.infoColor", "Color used for informational messages such as balances and worth checks.");
        notes.put("chat.successColor", "Color used for successful purchases, sales, payments, and other completed actions.");
        notes.put("chat.warningColor", "Color used for non-fatal warnings, unavailable listings, and disabled features.");
        notes.put("chat.errorColor", "Color used when an action fails or the player lacks permission or money.");
        notes.put("chat.adminColor", "Legacy administrator chat color retained for compatibility; Adminshop is a client GUI.");
        notes.put("permissionDefaults", "Each entry is an exact vanilla command level from 0 to 4. 0 allows everyone, 2 is gamemaster, and 4 is owner. External permission plugins are not integrated in this Alpha.");
        return notes;
    }

    private static Map<String, String> shopNotes() {
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("categories", "List of shop categories shown in /shop. Their order here controls their menu order.");
        notes.put("categories[].id", "Internal category ID used by commands and permissions. Keep it lowercase and unique.");
        notes.put("categories[].name", "Name players see in the shop menu.");
        notes.put("categories[].description", "Optional tooltip text on the category icon. This does not change purchased item components.");
        notes.put("categories[].items[].description", "Optional shop tooltip text. Purchased stacks keep their original components.");
        notes.put("categories[].icon", "Minecraft item ID used as the category icon, for example minecraft:bricks.");
        notes.put("categories[].items", "Listings contained in the category. The colored_blocks category is automatically divided into block-type submenus in the GUI.");
        notes.put("categories[].items[].item", "Base Minecraft item ID for the listing.");
        notes.put("categories[].items[].listingId", "Unique listing identifier. Component-rich variants include a #hash so multiple versions of the same base item can coexist.");
        notes.put("categories[].items[].stack", "Serialized exact ItemStack data. It preserves enchantments, names, lore, trims, potion contents, and other components. Preserve this data; use Add held item in Adminshop to add exact variants.");
        notes.put("categories[].items[].name", "Display name used in the shop GUI and chat messages.");
        notes.put("categories[].items[].buy", "Price for one item. Set to 0 to hide the listing from buy menus.");
        notes.put("categories[].items[].sell", "Amount paid for one matching item. Set to 0 to prevent players from selling it.");
        notes.put("categories[].items[].manualPrice", "When true, automatic economy balancing will not change buy or sell. Manual listings retain their configured prices.");
        notes.put("categories[].items[].pricingModelVersion", "Internal version of the automatic pricing model last applied. Normally this should not be edited.");
        return notes;
    }

    private static Map<String, String> enchantmentNotes() {
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("enabled", "Master switch for the enchanted-book shop. False hides it from players without deleting individual offers.");
        notes.put("defaultPricePerLevel", "Legacy default price retained for compatibility; automatic enchantment offer discovery has been removed.");
        notes.put("offers", "Map of enchantment IDs to the books offered by the shop.");
        notes.put("offers.<enchantment>.name", "Display name shown to players for that enchantment.");
        notes.put("offers.<enchantment>.pricePerLevel", "Base cost multiplied by the selected level. For example, 100.0 makes level III cost 300.0 before the global price multiplier.");
        notes.put("offers.<enchantment>.maxLevel", "Highest level the shop offers: 1-255, including above vanilla limits. 0 uses the enchantment's normal registered maximum. This does not change anvil rules.");
        notes.put("offers.<enchantment>.enabled", "When false, this enchantment is hidden from the shop. When true, players may select its available levels.");
        return notes;
    }

    public record FoundItem(Category category, ShopItem item) {}

    public static final class Category {
        public String id;
        public String name;
        public String icon;
        public JsonElement iconStack;
        public String description = "";
        public String detectedSource;
        public List<ShopItem> items = new ArrayList<>();
    }

    public static final class ShopItem {
        public String item;
        public String listingId;
        public JsonElement stack;
        public String name;
        public String description = "";
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
        Object _notes = new LinkedHashMap<String, String>();
        GeneralSection general = new GeneralSection();
        EconomySection economy = new EconomySection();
        CatalogSection catalog = new CatalogSection();
        ChatSection chat = new ChatSection();
        Map<String, Integer> permissionDefaults = new LinkedHashMap<>();
    }

    private static final class GeneralSection {
        String shopTitle = "ClassicGUIShop";
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
        Object _notes = new LinkedHashMap<String, String>();
        List<Category> categories = new ArrayList<>();
    }

    private static final class EnchantmentsFile {
        List<String> _about = new ArrayList<>();
        Object _notes = new LinkedHashMap<String, String>();
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
