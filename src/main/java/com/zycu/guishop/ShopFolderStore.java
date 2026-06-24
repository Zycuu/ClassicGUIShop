package com.zycu.guishop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ShopFolderStore {
    public static final String UNSORTED = "";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("guishop");
    private static final Path FILE = DIRECTORY.resolve("folders.json");

    private List<String> _about = List.of(
        "ClassicGUIShop folder layout used by the player shop and administrator GUI editor.",
        "Folders only organize listings. Prices and exact item data remain in shops.json."
    );
    private Map<String, String> _notes = notes();
    private Map<String, CategoryFolders> categories = new LinkedHashMap<>();

    public static ShopFolderStore load() {
        try {
            Files.createDirectories(DIRECTORY);
            ShopFolderStore store;
            if (Files.exists(FILE)) {
                store = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), ShopFolderStore.class);
                if (store == null) store = new ShopFolderStore();
            } else {
                store = new ShopFolderStore();
            }
            store.normalize();
            store.save();
            return store;
        } catch (Exception exception) {
            System.err.println("[ClassicGUIShop] Could not load folders.json. Using an empty folder layout.");
            exception.printStackTrace();
            return new ShopFolderStore();
        }
    }

    public synchronized void save() {
        try {
            normalize();
            Files.createDirectories(DIRECTORY);
            Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(this), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            System.err.println("[ClassicGUIShop] Could not save folders.json.");
            exception.printStackTrace();
        }
    }

    public synchronized void sync(ShopConfig config) {
        if (config == null || config.categories == null) return;

        Map<String, Boolean> existingCategories = new LinkedHashMap<>();
        for (ShopConfig.Category category : config.categories) {
            existingCategories.put(category.id.toLowerCase(Locale.ROOT), true);
            CategoryFolders layout = categories.get(category.id.toLowerCase(Locale.ROOT));
            if (layout == null) continue;

            Map<String, Boolean> listings = new LinkedHashMap<>();
            for (ShopConfig.ShopItem item : category.items) {
                if (item.listingId != null) listings.put(item.listingId.toLowerCase(Locale.ROOT), true);
            }
            layout.assignments.entrySet().removeIf(entry -> !listings.containsKey(entry.getKey().toLowerCase(Locale.ROOT)));
            layout.assignments.entrySet().removeIf(entry -> folder(category.id, entry.getValue()) == null);
        }
        categories.entrySet().removeIf(entry -> !existingCategories.containsKey(entry.getKey().toLowerCase(Locale.ROOT)));
        save();
    }

    public synchronized List<Folder> folders(String categoryId) {
        CategoryFolders layout = layout(categoryId, false);
        if (layout == null) return List.of();
        return List.copyOf(layout.folders);
    }

    public synchronized boolean hasFolders(String categoryId) {
        CategoryFolders layout = layout(categoryId, false);
        return layout != null && !layout.folders.isEmpty();
    }

    public synchronized Folder folder(String categoryId, String folderId) {
        if (folderId == null || folderId.isBlank()) return null;
        CategoryFolders layout = layout(categoryId, false);
        if (layout == null) return null;
        for (Folder folder : layout.folders) {
            if (folder.id.equalsIgnoreCase(folderId)) return folder;
        }
        return null;
    }

    public synchronized String folderFor(String categoryId, String listingId) {
        if (listingId == null) return UNSORTED;
        CategoryFolders layout = layout(categoryId, false);
        if (layout == null) return UNSORTED;
        String assigned = layout.assignments.get(listingId.toLowerCase(Locale.ROOT));
        return assigned == null || folder(categoryId, assigned) == null ? UNSORTED : assigned;
    }

    public synchronized Folder createFolder(String categoryId, String displayName, String icon) {
        if (categoryId == null || categoryId.isBlank()) return null;
        String name = displayName == null || displayName.isBlank() ? "New Folder" : displayName.trim();
        CategoryFolders layout = layout(categoryId, true);
        String baseId = sanitize(name);
        String id = baseId;
        int suffix = 2;
        while (folder(categoryId, id) != null) id = baseId + "_" + suffix++;

        Folder folder = new Folder();
        folder.id = id;
        folder.name = name;
        folder.icon = icon == null || icon.isBlank() ? "minecraft:chest" : icon;
        layout.folders.add(folder);
        save();
        return folder;
    }

    public synchronized boolean renameFolder(String categoryId, String folderId, String displayName) {
        Folder folder = folder(categoryId, folderId);
        if (folder == null || displayName == null || displayName.isBlank()) return false;
        folder.name = displayName.trim();
        save();
        return true;
    }

    public synchronized boolean deleteFolder(String categoryId, String folderId) {
        CategoryFolders layout = layout(categoryId, false);
        Folder folder = folder(categoryId, folderId);
        if (layout == null || folder == null) return false;
        layout.folders.remove(folder);
        layout.assignments.entrySet().removeIf(entry -> folderId.equalsIgnoreCase(entry.getValue()));
        save();
        return true;
    }

    public synchronized boolean assign(String categoryId, String listingId, String folderId) {
        if (categoryId == null || listingId == null) return false;
        CategoryFolders layout = layout(categoryId, true);
        String key = listingId.toLowerCase(Locale.ROOT);
        if (folderId == null || folderId.isBlank()) {
            layout.assignments.remove(key);
        } else {
            Folder folder = folder(categoryId, folderId);
            if (folder == null) return false;
            layout.assignments.put(key, folder.id);
        }
        save();
        return true;
    }

    public synchronized int assignedCount(String categoryId, String folderId) {
        CategoryFolders layout = layout(categoryId, false);
        if (layout == null) return 0;
        if (folderId == null || folderId.isBlank()) {
            int assigned = layout.assignments.size();
            ShopConfig.Category category = GuiShop.CONFIG == null ? null : GuiShop.CONFIG.category(categoryId);
            return category == null ? 0 : Math.max(0, category.items.size() - assigned);
        }
        int count = 0;
        for (String assigned : layout.assignments.values()) {
            if (folderId.equalsIgnoreCase(assigned)) count++;
        }
        return count;
    }

    private CategoryFolders layout(String categoryId, boolean create) {
        if (categoryId == null || categoryId.isBlank()) return null;
        String key = categoryId.toLowerCase(Locale.ROOT);
        CategoryFolders layout = categories.get(key);
        if (layout == null && create) {
            layout = new CategoryFolders();
            categories.put(key, layout);
        }
        return layout;
    }

    private void normalize() {
        if (_about == null) _about = new ArrayList<>();
        if (_notes == null) _notes = notes();
        if (categories == null) categories = new LinkedHashMap<>();
        for (CategoryFolders layout : categories.values()) {
            if (layout.folders == null) layout.folders = new ArrayList<>();
            if (layout.assignments == null) layout.assignments = new LinkedHashMap<>();
            for (Folder folder : layout.folders) {
                if (folder.id == null || folder.id.isBlank()) folder.id = "folder";
                folder.id = sanitize(folder.id);
                if (folder.name == null || folder.name.isBlank()) folder.name = folder.id;
                if (folder.icon == null || folder.icon.isBlank()) folder.icon = "minecraft:chest";
            }
        }
    }

    private static String sanitize(String value) {
        String cleaned = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        cleaned = cleaned.replaceAll("_+", "_");
        cleaned = cleaned.replaceAll("^_+|_+$", "");
        return cleaned.isBlank() ? "folder" : cleaned;
    }

    private static Map<String, String> notes() {
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("categories.<category>.folders", "Folders shown inside a shop category. Their order here controls their GUI order.");
        notes.put("folders[].id", "Internal folder ID used to assign listings. Keep it unique inside the category.");
        notes.put("folders[].name", "Folder name players see in the shop GUI.");
        notes.put("folders[].icon", "Minecraft item ID used as the folder icon.");
        notes.put("categories.<category>.assignments", "Maps exact listing IDs from shops.json to folder IDs. Missing entries appear under Unsorted.");
        return notes;
    }

    private static final class CategoryFolders {
        List<Folder> folders = new ArrayList<>();
        Map<String, String> assignments = new LinkedHashMap<>();
    }

    public static final class Folder {
        public String id;
        public String name;
        public String icon;
    }
}
