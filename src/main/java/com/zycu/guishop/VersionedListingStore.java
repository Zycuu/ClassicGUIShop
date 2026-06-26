package com.zycu.guishop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class VersionedListingStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("guishop");
    private static final Path FILE = DIRECTORY.resolve("versioned-listings.json");
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter
        .ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneOffset.UTC);

    private VersionedListingStore() {}

    public static ReconcileResult reconcile(
        ShopConfig config,
        ShopFolderStore folders,
        RegistryAccess registryAccess
    ) {
        ParkedFile parkedFile = load();
        Map<String, ParkedListing> parkedById = new LinkedHashMap<>();
        for (ParkedListing parked : parkedFile.listings) {
            if (parked == null || parked.listing == null) continue;
            parkedById.put(key(parked.listing), parked);
        }

        int restored = 0;
        int parked = 0;
        boolean changed = parkedById.size() != parkedFile.listings.size();

        Map<String, Boolean> activeIds = new LinkedHashMap<>();
        for (ShopConfig.Category category : config.categories) {
            for (ShopConfig.ShopItem listing : category.items) {
                activeIds.put(key(listing), true);
            }
        }

        Iterator<Map.Entry<String, ParkedListing>> parkedIterator = parkedById.entrySet().iterator();
        while (parkedIterator.hasNext()) {
            Map.Entry<String, ParkedListing> entry = parkedIterator.next();
            ParkedListing stored = entry.getValue();

            if (activeIds.containsKey(entry.getKey())) {
                parkedIterator.remove();
                changed = true;
                continue;
            }

            ShopConfig.Category category = config.category(stored.categoryId);
            if (category == null || !isRuntimeAvailable(stored.listing, registryAccess)) continue;

            category.items.add(stored.listing);
            activeIds.put(entry.getKey(), true);
            parkedIterator.remove();
            restored++;
            changed = true;

            if (folders != null && stored.folderId != null && !stored.folderId.isBlank()) {
                folders.assign(category.id, stored.listing.listingId, stored.folderId);
            }
        }

        for (ShopConfig.Category category : config.categories) {
            Iterator<ShopConfig.ShopItem> listingIterator = category.items.iterator();
            while (listingIterator.hasNext()) {
                ShopConfig.ShopItem listing = listingIterator.next();
                if (isRuntimeAvailable(listing, registryAccess)) continue;

                String folderId = folders == null
                    ? ShopFolderStore.UNSORTED
                    : folders.folderFor(category.id, listing.listingId);
                ParkedListing stored = new ParkedListing();
                stored.categoryId = category.id;
                stored.folderId = folderId;
                stored.listing = listing;
                parkedById.put(key(listing), stored);
                listingIterator.remove();
                parked++;
                changed = true;
            }
        }

        if (changed) {
            config.save();
            parkedFile.listings = new ArrayList<>(parkedById.values());
            save(parkedFile);
        }

        return new ReconcileResult(parked, restored, parkedById.size());
    }

    public static boolean isRuntimeAvailable(ShopConfig.ShopItem listing, RegistryAccess registryAccess) {
        if (listing == null || !isItemRegistered(listing.item)) return false;
        if (listing.stack == null || listing.stack.isJsonNull()) return true;

        try {
            DynamicOps<JsonElement> ops = registryAccess.createSerializationContext(JsonOps.INSTANCE);
            ItemStack decoded = ItemStack.CODEC.parse(ops, listing.stack).getOrThrow();
            if (decoded.isEmpty()) return false;
            Identifier expected = Identifier.parse(listing.item);
            return expected.equals(BuiltInRegistries.ITEM.getKey(decoded.getItem()));
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isItemRegistered(String itemId) {
        if (itemId == null || itemId.isBlank()) return false;
        try {
            Identifier identifier = Identifier.parse(itemId);
            Item item = BuiltInRegistries.ITEM.getValue(identifier);
            return item != null
                && item != Items.AIR
                && identifier.equals(BuiltInRegistries.ITEM.getKey(item));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static ParkedFile load() {
        try {
            Files.createDirectories(DIRECTORY);
            if (!Files.exists(FILE)) return new ParkedFile();
            ParkedFile loaded = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), ParkedFile.class);
            if (loaded == null) loaded = new ParkedFile();
            if (loaded.listings == null) loaded.listings = new ArrayList<>();
            return loaded;
        } catch (Exception exception) {
            preserveMalformedFile();
            return new ParkedFile();
        }
    }

    private static void save(ParkedFile parkedFile) {
        try {
            Files.createDirectories(DIRECTORY);
            Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(parkedFile), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            System.err.println("[ClassicGUIShop] Could not save versioned-listings.json.");
            exception.printStackTrace();
        }
    }

    private static void preserveMalformedFile() {
        if (!Files.exists(FILE)) return;
        try {
            String timestamp = BACKUP_TIME.format(Instant.now());
            Path backup = DIRECTORY.resolve("versioned-listings.json.corrupt-" + timestamp + ".backup");
            Files.move(FILE, backup, StandardCopyOption.REPLACE_EXISTING);
            System.err.println("[ClassicGUIShop] Malformed versioned-listings.json was preserved as "
                + backup.getFileName() + ".");
        } catch (Exception backupException) {
            throw new IllegalStateException("Could not safely load versioned-listings.json", backupException);
        }
    }

    private static String key(ShopConfig.ShopItem listing) {
        String listingId = listing == null ? null : listing.listingId;
        if (listingId == null || listingId.isBlank()) listingId = listing == null ? "" : listing.item;
        return listingId == null ? "" : listingId.toLowerCase(Locale.ROOT);
    }

    public record ReconcileResult(int parked, int restored, int remainingParked) {}

    private static final class ParkedFile {
        List<String> _about = List.of(
            "Listings unavailable in the current Minecraft runtime are stored here instead of being deleted.",
            "ClassicGUIShop automatically restores them when their item and components are available again."
        );
        List<ParkedListing> listings = new ArrayList<>();
    }

    private static final class ParkedListing {
        String categoryId;
        String folderId;
        ShopConfig.ShopItem listing;
    }
}
