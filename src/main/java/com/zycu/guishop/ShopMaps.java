package com.zycu.guishop;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import java.util.LinkedHashMap;

/** Registry-only explorer maps are placeholders until vanilla supplies world data and a target. */
public final class ShopMaps {
    private ShopMaps() {}
    private record Target(String tag, net.minecraft.core.Holder<net.minecraft.world.level.saveddata.maps.MapDecorationType> decoration) {}
    private static final java.util.Map<String, Target> TARGETS = java.util.Map.ofEntries(
        target("abandoned_camp", "abandoned_camp", MapDecorationTypes.ABANDONED_CAMP),
        target("buried_ancient_city", "on_ancient_city_maps", MapDecorationTypes.ANCIENT_CITY),
        target("buried_mineshaft", "on_mineshaft_maps", MapDecorationTypes.MINESHAFT),
        target("buried_treasure", "on_treasure_maps", MapDecorationTypes.RED_X),
        target("buried_trial_chambers", "on_buried_trial_chambers_maps", MapDecorationTypes.TRIAL_CHAMBERS),
        target("desert_pyramid", "on_desert_pyramid_maps", MapDecorationTypes.DESERT_PYRAMID),
        target("desert_village", "on_desert_village_maps", MapDecorationTypes.DESERT_VILLAGE),
        target("jungle_pyramid", "on_jungle_pyramid_maps", MapDecorationTypes.JUNGLE_TEMPLE),
        target("ocean_monument", "on_ocean_monument_maps", MapDecorationTypes.OCEAN_MONUMENT),
        target("plains_village", "on_plains_village_maps", MapDecorationTypes.PLAINS_VILLAGE),
        target("savanna_village", "on_savanna_village_maps", MapDecorationTypes.SAVANNA_VILLAGE),
        target("snowy_village", "on_snowy_village_maps", MapDecorationTypes.SNOWY_VILLAGE),
        target("swamp_hut", "on_swamp_hut_maps", MapDecorationTypes.SWAMP_HUT),
        target("taiga_village", "on_taiga_village_maps", MapDecorationTypes.TAIGA_VILLAGE),
        target("warm_ocean_ruins", "on_ocean_ruin_warm_maps", MapDecorationTypes.OCEAN_RUIN_WARM),
        target("woodland_mansion", "on_woodland_mansion_maps", MapDecorationTypes.WOODLAND_MANSION));
    private static java.util.Map.Entry<String, Target> target(String item, String tag, net.minecraft.core.Holder<net.minecraft.world.level.saveddata.maps.MapDecorationType> decoration) {
        return java.util.Map.entry("minecraft:" + item + "_map", new Target(tag, decoration));
    }
    private record Key(net.minecraft.server.level.ServerLevel world, String identity, int regionX, int regionZ) {}
    private static final LinkedHashMap<Key, ItemStack> CACHE = new LinkedHashMap<>();
    public static void clear() { CACHE.clear(); }
    private static boolean nativeMap(ItemStack stack) {
        return stack.is(Items.FILLED_MAP) || TARGETS.containsKey(ShopService.itemId(stack));
    }
    public static boolean dynamic(ItemStack stack) { return nativeMap(stack) && !stack.has(DataComponents.MAP_ID); }

    public static ItemStack prepare(ServerPlayer player, ItemStack template) {
        // Mod subclasses may have their own initialization and identity rules. Preserve them verbatim.
        if (!nativeMap(template)) return template.copyWithCount(1);
        if (template.has(DataComponents.MAP_ID)) {
            if (MapItem.getSavedData(template, player.level()) == null) throw new IllegalArgumentException("This map's saved data is missing from this world. Import a working map with Add held item.");
            return template.copyWithCount(1);
        }
        var key = new Key(player.level(), ItemStackData.listingId(template, player.registryAccess()), player.blockPosition().getX() >> 8, player.blockPosition().getZ() >> 8);
        var cached = CACHE.get(key);
        if (cached != null && MapItem.getSavedData(cached, player.level()) != null) return cached.copyWithCount(1);
        var result = template.copyWithCount(1);
        if (result.is(Items.FILLED_MAP)) {
            MapItem.applyNewSavedData(player.level(), result, player.blockPosition().getX(), player.blockPosition().getZ(), (byte)0, true, false);
        } else if (TARGETS.containsKey(ShopService.itemId(result))) {
            var target = TARGETS.get(ShopService.itemId(result));
            var json = new com.google.gson.JsonObject();
            json.addProperty("type", "minecraft:exploration_map");
            json.addProperty("destination", "#minecraft:" + target.tag());
            json.addProperty("decoration", target.decoration().unwrapKey().orElseThrow().identifier().toString());
            json.addProperty("search_radius", 32); json.addProperty("skip_existing_chunks", false);
            net.minecraft.world.level.storage.loot.functions.LootItemFunction function;
            try { function = net.minecraft.world.level.storage.loot.functions.LootItemFunctions.DIRECT_CODEC.parse(player.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), json).getOrThrow(); }
            catch (RuntimeException unavailable) { throw new IllegalArgumentException("This world's data cannot generate that map. Import a working map with Add held item. No money was charged.", unavailable); }
            var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(player.level())
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, player.position())
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
            var context = new net.minecraft.world.level.storage.loot.LootContext.Builder(params).create(java.util.Optional.empty());
            result = function.apply(result, context);
            if (!result.has(DataComponents.MAP_ID)) throw new IllegalArgumentException("No destination was found nearby for this explorer map in this dimension. No money was charged.");
        } else throw new IllegalArgumentException("This explorer map needs a destination. Import a working map with Add held item.");
        if (CACHE.size() >= 256) CACHE.remove(CACHE.keySet().iterator().next());
        CACHE.put(key, result.copyWithCount(1));
        return result;
    }
    public static boolean matches(ItemStack template, ItemStack actual) {
        if (!dynamic(template)) return ItemStackData.same(template, actual);
        if (actual.isEmpty() || actual.getItem() != template.getItem()) return false;
        var normalized = actual.copyWithCount(1);
        normalized.remove(DataComponents.MAP_ID);
        normalized.set(DataComponents.MAP_DECORATIONS, template.getOrDefault(DataComponents.MAP_DECORATIONS, net.minecraft.world.item.component.MapDecorations.EMPTY));
        return ItemStackData.same(template, normalized);
    }
    /** Price guard treats maps differing only in generated world data as overlapping offers. */
    public static ItemStack economicIdentity(ItemStack stack) {
        if (!nativeMap(stack)) return stack;
        var normalized = stack.copyWithCount(1); normalized.remove(DataComponents.MAP_ID);
        normalized.set(DataComponents.MAP_DECORATIONS, net.minecraft.world.item.component.MapDecorations.EMPTY);
        return normalized;
    }
    /** Each extra vanilla cloned map consumes one empty map, regardless of that empty map's lore. */
    public static double cheapestEmptyMap(ShopConfig config, net.minecraft.core.RegistryAccess access) {
        double minimum = Double.POSITIVE_INFINITY;
        for (var category : config.categories) for (var listing : category.items) {
            if (listing.buy > 0 && listing.item.equals("minecraft:map") && VersionedListingStore.isRuntimeAvailable(listing, access)) minimum = Math.min(minimum, listing.buy);
        }
        return minimum;
    }
}
