package com.zycu.guishop;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import java.util.*;

/** Presentation-only folders. Every variant remains an exact, independently priced listing. */
public final class ListingGroups {
    private ListingGroups() {}
    public static boolean potion(String item) { return Set.of("minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion").contains(item); }
    public static String key(ShopConfig.ShopItem item, RegistryAccess access) {
        String book = BookGroups.key(item, access);
        if (book != null) return "book:" + book;
        boolean arrow = item.item.equals("minecraft:tipped_arrow");
        if (!(potion(item.item) || arrow) || !VersionedListingStore.isRuntimeAvailable(item, access)) return null;
        String prefix = arrow ? "arrow:" : "potion:";
        var contents = item.createStack(access).get(DataComponents.POTION_CONTENTS);
        if (contents == null || contents.potion().isEmpty()) return prefix + "custom";
        var holder = contents.potion().get();
        var id = holder.unwrapKey().map(k -> k.identifier()).orElse(null);
        if (id == null) return prefix + "custom";
        // Only canonical duration/strength prefixes with matching effects join an existing family.
        // Unknown mod IDs stay independent, rather than guessing from names or translated labels.
        String path = id.getPath();
        if(arrow&&id.getNamespace().equals("minecraft")&&Set.of("mundane","thick","awkward").contains(path)
            &&holder.value().getEffects().isEmpty()&&contents.customEffects().isEmpty())return "arrow:no_effect";
        String base = path.startsWith("long_") ? path.substring(5) : path.startsWith("strong_") ? path.substring(7) : path;
        var registry = access.lookupOrThrow(Registries.POTION);
        var family = registry.get(Identifier.fromNamespaceAndPath(id.getNamespace(), base));
        if (family.isPresent() && (id.getNamespace().equals("minecraft")
            || !holder.value().getEffects().isEmpty() && effectTypes(holder.value()).equals(effectTypes(family.get().value())))) {
            id = Identifier.fromNamespaceAndPath(id.getNamespace(), base);
        }
        return prefix + id;
    }
    public static String displayName(ShopConfig.ShopItem item, RegistryAccess access) {
        if (!(potion(item.item) || item.item.equals("minecraft:tipped_arrow")) || !VersionedListingStore.isRuntimeAvailable(item, access)) return item.name;
        var contents = item.createStack(access).get(DataComponents.POTION_CONTENTS);
        if (contents == null) return item.name;
        String path = contents.potion().flatMap(holder -> holder.unwrapKey()).map(key -> key.identifier().getPath()).orElse("");
        if(item.item.equals("minecraft:tipped_arrow")&&Set.of("mundane","thick","awkward").contains(path))
            return item.name+" ("+Character.toUpperCase(path.charAt(0))+path.substring(1)+")";
        String suffix = path.startsWith("long_") ? " (Extended)" : path.startsWith("strong_") ? " (Strong)" : "";
        return suffix.isEmpty() || item.name.endsWith(suffix) ? item.name : item.name + suffix;
    }
    public static String name(String key, RegistryAccess access) {
        if (key.equals("arrows")) return "Tipped Arrows";
        if (key.equals("arrow:no_effect")) return "No Effect Tipped Arrows";
        if (key.startsWith("book:")) return BookGroups.name(key.substring(5), access);
        boolean arrow = key.startsWith("arrow:");
        if (!arrow && !key.startsWith("potion:")) return BookGroups.name(key, access);
        String id = key.substring(arrow ? 6 : 7);
        if (id.equals("custom")) return arrow ? "Custom Tipped Arrows" : "Custom Potions";
        return access.lookupOrThrow(Registries.POTION).get(Identifier.parse(id))
            .map(holder -> PotionContents.createItemStack(arrow ? Items.TIPPED_ARROW : Items.POTION, holder).getHoverName().getString()).orElse(id);
    }
    public static String parent(String group) { return group != null && group.startsWith("arrow:") ? "arrows" : null; }
    public static String description(String key, int count) {
        return count + (key.equals("arrows") ? " arrow variants | Choose an arrow type" : key.startsWith("arrow:") ? " arrow variants | Choose a variant" : key.startsWith("book:") ? " book variants | Choose a level" : " potion variants | Choose a variant");
    }
    private static String rootKey(String key) { return key != null && key.startsWith("arrow:") ? "arrows" : key; }
    private static List<String> effectTypes(net.minecraft.world.item.alchemy.Potion potion) {
        return potion.getEffects().stream().map(e -> e.getEffect().unwrapKey().map(k -> k.identifier().toString()).orElse("")).sorted().toList();
    }
    /** One shared display order for the real shop and its editor simulator. */
    public record Entry(String group, List<ShopConfig.ShopItem> items) {
        public boolean folder() { return group != null; }
        public ShopConfig.ShopItem item() { return items.getFirst(); }
    }
    public static List<Entry> entries(List<ShopConfig.ShopItem> items, RegistryAccess access, String parent) {
        var folders = groups(items, access, parent);
        var entries = new ArrayList<Entry>();
        var seen = new HashSet<String>();
        for (var item : items) {
            String key = key(item, access);
            String folder = parent == null ? rootKey(key) : "arrows".equals(parent) ? key : null;
            if (folder != null && folders.containsKey(folder)) {
                if (seen.add(folder)) entries.add(new Entry(folder, folders.get(folder)));
            } else if (!"arrows".equals(parent) && Objects.equals(key, parent)) {
                entries.add(new Entry(null, List.of(item)));
            }
        }
        if (parent == null) {
            var arrowFolder = entries.stream().filter(e -> "arrows".equals(e.group())).findFirst().orElse(null);
            if (arrowFolder != null) {
                entries.remove(arrowFolder);
                int position = -1;
                for (int i = 0; i < entries.size(); i++) if (!entries.get(i).folder() && entries.get(i).item().item.equals("minecraft:arrow")) position = i + 1;
                if (position < 0) for (int i = 0; i < entries.size(); i++) if (!entries.get(i).folder() && entries.get(i).item().item.equals("minecraft:spectral_arrow")) { position = i; break; }
                entries.add(position < 0 ? entries.size() : position, arrowFolder);
            }
        }
        return entries;
    }
    public static Map<String,List<ShopConfig.ShopItem>> groups(List<ShopConfig.ShopItem> items, RegistryAccess access) { return groups(items, access, null); }
    public static Map<String,List<ShopConfig.ShopItem>> groups(List<ShopConfig.ShopItem> items, RegistryAccess access, String parent) {
        var result = new LinkedHashMap<String,List<ShopConfig.ShopItem>>();
        if (parent != null && !parent.equals("arrows")) return result;
        for (var item : items) {
            String key = key(item, access);
            if (parent == null) key = rootKey(key);
            else if (key == null || !key.startsWith("arrow:")) continue;
            if (key != null) result.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        return result;
    }
    public static List<ShopConfig.ShopItem> leaves(List<ShopConfig.ShopItem> items, RegistryAccess access, String group) {
        if ("arrows".equals(group)) return List.of();
        return items.stream().filter(item -> Objects.equals(key(item, access), group)).toList();
    }
    public static void separateTippedArrows(ShopConfig config) {
        var combat = config.category("combat");
        if (combat == null) { combat = new ShopConfig.Category(); combat.id = "combat"; combat.name = "Combat"; combat.icon = "minecraft:diamond_sword"; }
        for (var category : config.categories) {
            if (category == combat) continue;
            var iterator = category.items.iterator();
            while (iterator.hasNext()) { var item = iterator.next(); if (item.item.equals("minecraft:tipped_arrow")) { combat.items.add(item); iterator.remove(); } }
        }
        if (!combat.items.isEmpty() && !config.categories.contains(combat)) config.categories.add(combat);
        config.categories.removeIf(category -> category.items.isEmpty());
    }
    public static void separatePotions(ShopConfig config) {
        var category = new ShopConfig.Category(); category.id = "potions"; category.name = "Potions"; category.icon = "minecraft:potion";
        for (var existing : config.categories) {
            var iterator = existing.items.iterator();
            while (iterator.hasNext()) { var item = iterator.next(); if (potion(item.item)) { category.items.add(item); iterator.remove(); } }
        }
        config.categories.removeIf(existing -> existing.items.isEmpty());
        if (!category.items.isEmpty()) config.categories.add(category);
    }
}
