package com.zycu.guishop.admin;

import com.zycu.guishop.GuiShop;
import com.zycu.guishop.ItemStackData;
import com.zycu.guishop.ShopConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AdminEditorService {
    public static final String DENIED = "Ask the Server Owner for permission to use the Adminshop tools";
    private static final List<String> FILES = List.of("settings.json", "shops.json", "enchantments.json");
    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("guishop");
    private static String expectedDisk;
    private AdminEditorService() {}
    public record Snapshot(String revision, AdminDocument document) {}
    public record RestoreDraft(String revision, AdminDocument live, AdminDocument previous) {}

    public static void rememberDiskState() {
        try { expectedDisk = diskRevision(); }
        catch (Exception exception) { expectedDisk = "unreadable"; System.err.println("[ClassicGUIShop] Editor writes blocked: could not read configuration state. " + exception.getMessage()); }
    }
    private static String diskRevision() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String name : FILES) {
            digest.update(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Path file = DIRECTORY.resolve(name);
            if (Files.exists(file)) digest.update(Files.readAllBytes(file));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static boolean allowed(ServerPlayer player) {
        // Use Minecraft's actual permission set, never the old broad operator reflection fallback.
        return player.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS));
    }
    private static void authorize(ServerPlayer player) {
        if (!allowed(player)) throw new SecurityException(DENIED);
    }
    public static Snapshot snapshot(ServerPlayer player) throws Exception {
        authorize(player); return current();
    }
    private static Snapshot current() throws Exception {
        String disk = diskRevision();
        if (expectedDisk != null && !expectedDisk.equals(disk)) throw new IllegalStateException("Configuration files changed outside the editor. Restart the server to load those changes before editing.");
        AdminDocument document = new AdminDocument();
        document.config = GuiShop.CONFIG;
        document = document.copy();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(AdminDocument.JSON.toJson(document).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update(disk.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new Snapshot(HexFormat.of().formatHex(digest.digest()), document);
    }

    public static ShopConfig.ShopItem heldListing(ServerPlayer player) {
        authorize(player);
        var held = player.getMainHandItem();
        if (held.isEmpty()) throw new IllegalArgumentException("Hold the exact item you want to add, then click Add held item.");
        ShopConfig.ShopItem item = new ShopConfig.ShopItem();
        item.item = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        item.listingId = ItemStackData.listingId(held, player.registryAccess());
        item.stack = ItemStackData.encode(held, player.registryAccess());
        item.name = held.getHoverName().getString(); item.manualPrice = true;
        return item;
    }

    public static RestoreDraft restoreDraft(ServerPlayer player, String backupId) throws Exception {
        authorize(player); Snapshot live = current();
        if (backupId == null || !backupId.matches("[0-9TZ.+-]{20,60}-[0-9a-f]{8}")) fail("Invalid backup identifier");
        Path backups = DIRECTORY.resolve("backups").toAbsolutePath().normalize();
        Path file = backups.resolve(backupId).resolve("editor-before.json").normalize();
        if (!file.startsWith(backups) || !Files.isRegularFile(file) || Files.size(file) > 8_000_000) fail("Backup is missing or too large");
        var previous = AdminDocument.JSON.fromJson(Files.readString(file), AdminDocument.class);
        return new RestoreDraft(live.revision(), live.document(), previous);
    }

    public static synchronized Snapshot apply(ServerPlayer player, String revision, AdminDocument requested) throws Exception {
        authorize(player);
        Snapshot before = current();
        if (!before.revision().equals(revision)) throw new IllegalStateException("The shop changed since this draft opened. Your draft is preserved. Review it, then reload before applying.");
        AdminDocument draft = requested.copy();
        validate(draft, player);
        // Preserve existing component identity; new variants must pass codec and identity validation.
        Map<String, ShopConfig.ShopItem> originals = new LinkedHashMap<>();
        for (var category : before.document().config.categories) for (var item : category.items) originals.put(item.listingId.toLowerCase(java.util.Locale.ROOT), item);
        for (var category : draft.config.categories) for (var item : category.items) {
            var old = originals.get(item.listingId.toLowerCase(java.util.Locale.ROOT));
            if (old != null && (!old.item.equals(item.item) || !java.util.Objects.equals(old.stack, item.stack))) {
                throw new IllegalArgumentException("Existing exact item data cannot be changed. Add a new held-item listing instead.");
            }
            if (old == null || old.buy != item.buy || old.sell != item.sell) item.manualPrice = true;
        }
        String backupId = Instant.now().toString().replace(':', '-') + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path backup = DIRECTORY.resolve("backups").resolve(backupId);
        Files.createDirectories(backup);
        for (String name : FILES) {
            Path original = DIRECTORY.resolve(name);
            if (Files.exists(original)) Files.copy(original, backup.resolve(name));
        }
        // Legacy folder data is ignored, but retained byte-for-byte in each backup.
        Path legacy = DIRECTORY.resolve("folders.json");
        if (Files.exists(legacy)) Files.copy(legacy, backup.resolve(legacy.getFileName()));
        // Preserve unavailable listing metadata in every backup as well.
        Path parked = DIRECTORY.resolve("versioned-listings.json");
        if (Files.exists(parked)) Files.copy(parked, backup.resolve(parked.getFileName()));
        Files.writeString(backup.resolve("editor-before.json"), AdminDocument.JSON.toJson(before.document()));
        Files.writeString(backup.resolve("editor-after.json"), AdminDocument.JSON.toJson(draft));
        String changes = describeChanges(before.document(), draft);
        Files.writeString(backup.resolve("changes.txt"), changes);
        try {
            draft.config.saveChecked();
            expectedDisk = diskRevision();
        } catch (Exception failure) {
            for (String name : FILES) {
                Path saved = backup.resolve(name);
                if (Files.exists(saved)) {
                    try { Files.copy(saved, DIRECTORY.resolve(name), StandardCopyOption.REPLACE_EXISTING); }
                    catch (Exception rollback) { failure.addSuppressed(rollback); }
                }
            }
            throw new IllegalStateException("Save failed. Previous configuration was restored where possible; backup: " + backupId, failure);
        }
        GuiShop.CONFIG = draft.config;
        GuiShop.ECONOMY.updateConfig(draft.config);
        TransactionJournal.record(player, "CONFIG_APPLY", "", "", 0, 0, 0, "Backup " + backupId + " | " + changes.substring(0, Math.min(600, changes.length())));
        return current();
    }

    public static void validate(AdminDocument draft, ServerPlayer player) {
        if (draft == null || draft.config == null) fail("Incomplete editor draft");
        ShopConfig config = draft.config;
        text(config.currencySymbol, 32, "Currency symbol");
        text(config.shopTitle, 64, "Shop title");
        if (config.shopTitle.isBlank()) fail("Shop title cannot be empty");
        amount(config.startingBalance, "Starting balance"); amount(config.priceMultiplier, "Price multiplier");
        if (config.priceMultiplier > 1000) fail("Price multiplier must be at most 1000");
        if (config.catalogSellRatio < 0 || config.catalogSellRatio > 1 || !Double.isFinite(config.catalogSellRatio)) fail("Enchantment sell ratio must be between 0 and 1");
        amount(config.defaultEnchantmentPricePerLevel, "Default enchantment price");
        text(config.chatPrefix, 64, "Chat prefix");
        for (String color : List.of(nonNull(config.chatPrefixColor), nonNull(config.chatInfoColor), nonNull(config.chatSuccessColor), nonNull(config.chatWarningColor), nonNull(config.chatErrorColor))) {
            if (!Set.of("black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white").contains(color)) fail("Choose a valid Minecraft chat color");
        }
        if (config.permissionDefaults == null || config.permissionDefaults.size() > 2000) fail("Invalid permission settings");
        for (var entry : config.permissionDefaults.entrySet()) {
            text(entry.getKey(), 160, "Permission name");
            if (entry.getValue() == null || entry.getValue() < 0 || entry.getValue() > 4) fail("Permission levels must be between 0 and 4");
        }
        if (config.categories == null || config.categories.size() > 250) fail("At most 250 categories are supported");
        Set<String> categoryIds = new HashSet<>(), listingIds = new HashSet<>();
        int listings = 0;
        Map<Integer, java.util.List<PriceGroup>> identities = new LinkedHashMap<>();
        for (var category : config.categories) {
            if (category == null) fail("Empty category");
            id(category.id, "Category ID");
            if (!categoryIds.add(category.id)) fail("Category IDs must be unique");
            text(category.name, 80, "Category name"); icon(category.icon);
            if (category.iconStack != null) {
                if (AdminDocument.JSON.toJson(category.iconStack).length() > 32000) fail("Exact icon data is too large");
                var oldCategory = GuiShop.CONFIG.category(category.id);
                boolean preserved = oldCategory != null && category.icon.equals(oldCategory.icon)
                    && category.iconStack.equals(oldCategory.iconStack);
                if (!preserved) {
                    var template = net.minecraft.world.item.ItemStack.CODEC.parse(player.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), category.iconStack).getOrThrow();
                    if (template.isEmpty() || !BuiltInRegistries.ITEM.getKey(template.getItem()).toString().equals(category.icon)) fail("Category icon does not match its exact item identity");
                }
            }
            text(category.description, 160, "Category description");
            if (category.detectedSource != null) text(category.detectedSource, 120, "Detected source");
            if (category.items == null) fail("Missing category listings");
            for (var item : category.items) {
                if (++listings > 10000 || item == null) fail("Invalid or oversized listing collection");
                text(item.listingId, 200, "Listing ID"); text(item.item, 200, "Item ID"); text(item.name, 120, "Item name");
                text(item.description, 160, "Listing description");
                if (!listingIds.add(item.listingId.toLowerCase(java.util.Locale.ROOT))) fail("Listing IDs must be unique across the shop");
                amount(item.buy, "Buy price"); amount(item.sell, "Sell price");
                if (item.buy > 0 && item.sell > item.buy) fail("Sell price cannot exceed the same listing's buy price");
                // Existing unavailable items remain editable and preserved. New items must decode successfully.
                ShopConfig.ShopItem old = null;
                for (var existingCategory : GuiShop.CONFIG.categories) for (var existing : existingCategory.items) {
                    if (existing.listingId.equalsIgnoreCase(item.listingId)) old = existing;
                }
                if (old == null) {
                    var stack = item.createStack(player.registryAccess());
                    if (item.stack != null) {
                        stack = net.minecraft.world.item.ItemStack.CODEC.parse(player.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), item.stack).getOrThrow();
                    }
                    if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(item.item)
                        || !ItemStackData.validListingId(ItemStackData.listingId(stack, player.registryAccess()), item.listingId)) fail("New listing does not match its exact item identity: " + item.listingId);
                }
                if (item.stack != null && AdminDocument.JSON.toJson(item.stack).length() > 32000) fail("Exact item data is too large");
                if (com.zycu.guishop.VersionedListingStore.isRuntimeAvailable(item, player.registryAccess())) {
                    var stack = item.createStack(player.registryAccess());
                    var bucket = identities.computeIfAbsent(net.minecraft.world.item.ItemStack.hashItemAndComponents(stack), ignored -> new java.util.ArrayList<>());
                    var group = bucket.stream().filter(value -> ItemStackData.same(value.stack, stack)).findFirst().orElse(null);
                    if (group == null) { group = new PriceGroup(stack); bucket.add(group); }
                    if (item.buy > 0) group.minimumBuy = Math.min(group.minimumBuy, item.buy);
                    group.maximumSell = Math.max(group.maximumSell, item.sell);
                    if (group.maximumSell > group.minimumBuy) fail("Identical item listings cannot sell for more than their cheapest enabled buy price: " + item.name);
                }
            }
        }
        if (config.enchantments == null || config.enchantments.size() > 1000) fail("Invalid enchantment offers");
        for (var entry : config.enchantments.entrySet()) {
            text(entry.getKey(), 200, "Enchantment ID");
            if (!GuiShop.CONFIG.enchantments.containsKey(entry.getKey())) {
                try {
                    var registry = player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                    if (!registry.containsKey(Identifier.parse(entry.getKey()))) fail("Unknown enchantment: " + entry.getKey());
                } catch (RuntimeException invalid) { fail("Unknown enchantment: " + entry.getKey()); }
            }
            var offer = entry.getValue(); if (offer == null) fail("Empty enchantment offer");
            text(offer.name, 120, "Enchantment name"); amount(offer.pricePerLevel, "Enchantment price");
            if (offer.maxLevel < 0 || offer.maxLevel > 255) fail("Invalid maximum enchantment level");
        }
    }

    private static final class PriceGroup {
        final net.minecraft.world.item.ItemStack stack;
        double minimumBuy = Double.POSITIVE_INFINITY, maximumSell;
        PriceGroup(net.minecraft.world.item.ItemStack stack) { this.stack = stack; }
    }

    private static String describeChanges(AdminDocument before, AdminDocument after) {
        java.util.List<String> changes = new java.util.ArrayList<>();
        var oldSettings = AdminDocument.JSON.toJsonTree(before.config).getAsJsonObject();
        var newSettings = AdminDocument.JSON.toJsonTree(after.config).getAsJsonObject();
        for (var field : newSettings.entrySet()) {
            if (field.getKey().equals("categories")) continue;
            if (!java.util.Objects.equals(field.getValue(), oldSettings.get(field.getKey()))) changes.add("Changed " + field.getKey());
        }
        Map<String, ShopConfig.FoundItem> oldItems = new LinkedHashMap<>(), newItems = new LinkedHashMap<>();
        for (var category : before.config.categories) for (var item : category.items) oldItems.put(item.listingId, new ShopConfig.FoundItem(category, item));
        for (var category : after.config.categories) for (var item : category.items) newItems.put(item.listingId, new ShopConfig.FoundItem(category, item));
        for (var entry : newItems.entrySet()) {
            var old = oldItems.get(entry.getKey()); var now = entry.getValue();
            if (old == null) changes.add("Added " + entry.getKey());
            else {
                if (old.item().buy != now.item().buy || old.item().sell != now.item().sell) changes.add(entry.getKey() + " prices " + old.item().buy + "/" + old.item().sell + " -> " + now.item().buy + "/" + now.item().sell);
                if (!old.category().id.equals(now.category().id)) changes.add("Moved " + entry.getKey() + " to " + now.category().id);
            }
        }
        for (String id : oldItems.keySet()) if (!newItems.containsKey(id)) changes.add("Removed " + id);
        if (!AdminDocument.JSON.toJson(before.config.categories).equals(AdminDocument.JSON.toJson(after.config.categories))) changes.add("Category layout or listing details changed");
        return changes.isEmpty() ? "No content changes" : String.join("; ", changes);
    }
    private static String nonNull(String text) { return text == null ? "" : text; }
    private static void text(String value, int maximum, String label) {
        if (value == null || value.length() > maximum || value.codePoints().anyMatch(c -> c < 32)) fail(label + " is missing, too long, or contains control characters");
    }
    private static void id(String value, String label) {
        if (value == null || !value.matches("[a-z0-9_.-]{1,80}")) fail(label + " must use lowercase letters, numbers, underscores, dots, or dashes");
    }
    private static void icon(String value) {
        text(value, 200, "Category icon");
        try {
            var id = Identifier.parse(value);
            // Absent mod icons are harmless presentation metadata and must survive backup restoration.
            if (id.getNamespace().equals("minecraft") && !BuiltInRegistries.ITEM.containsKey(id)) fail("Unknown icon: " + value);
        }
        catch (RuntimeException exception) { fail("Unknown icon: " + value); }
    }
    private static void amount(double value, String label) {
        if (!Double.isFinite(value) || value < 0 || value > 1_000_000_000) fail(label + " must be finite and between 0 and 1,000,000,000");
    }
    private static void fail(String message) { throw new IllegalArgumentException(message); }
}
