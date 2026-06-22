package com.zycu.guishop;

import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Set;

public final class BalancedPricing {
    public static final int MODEL_VERSION = 2;

    private static final Map<String, Double> EXACT = Map.ofEntries(
        Map.entry("minecraft:dragon_egg", 50000.0),
        Map.entry("minecraft:elytra", 15000.0),
        Map.entry("minecraft:nether_star", 10000.0),
        Map.entry("minecraft:beacon", 12500.0),
        Map.entry("minecraft:mace", 6500.0),
        Map.entry("minecraft:heavy_core", 3200.0),
        Map.entry("minecraft:enchanted_golden_apple", 5000.0),
        Map.entry("minecraft:totem_of_undying", 2800.0),
        Map.entry("minecraft:trident", 2200.0),
        Map.entry("minecraft:heart_of_the_sea", 1600.0),
        Map.entry("minecraft:conduit", 2600.0),
        Map.entry("minecraft:recovery_compass", 950.0),
        Map.entry("minecraft:echo_shard", 260.0),
        Map.entry("minecraft:ominous_trial_key", 700.0),
        Map.entry("minecraft:trial_key", 300.0),
        Map.entry("minecraft:breeze_rod", 140.0),
        Map.entry("minecraft:wind_charge", 20.0),
        Map.entry("minecraft:netherite_ingot", 3200.0),
        Map.entry("minecraft:netherite_scrap", 720.0),
        Map.entry("minecraft:ancient_debris", 650.0),
        Map.entry("minecraft:netherite_block", 28800.0),
        Map.entry("minecraft:diamond", 260.0),
        Map.entry("minecraft:diamond_block", 2340.0),
        Map.entry("minecraft:emerald", 180.0),
        Map.entry("minecraft:emerald_block", 1620.0),
        Map.entry("minecraft:gold_ingot", 42.0),
        Map.entry("minecraft:gold_block", 378.0),
        Map.entry("minecraft:iron_ingot", 24.0),
        Map.entry("minecraft:iron_block", 216.0),
        Map.entry("minecraft:copper_ingot", 7.0),
        Map.entry("minecraft:copper_block", 63.0),
        Map.entry("minecraft:coal", 8.0),
        Map.entry("minecraft:charcoal", 10.0),
        Map.entry("minecraft:coal_block", 72.0),
        Map.entry("minecraft:redstone", 5.0),
        Map.entry("minecraft:redstone_block", 45.0),
        Map.entry("minecraft:lapis_lazuli", 7.0),
        Map.entry("minecraft:lapis_block", 63.0),
        Map.entry("minecraft:quartz", 10.0),
        Map.entry("minecraft:amethyst_shard", 8.0),
        Map.entry("minecraft:prismarine_shard", 9.0),
        Map.entry("minecraft:prismarine_crystals", 14.0),
        Map.entry("minecraft:blaze_rod", 35.0),
        Map.entry("minecraft:ender_pearl", 30.0),
        Map.entry("minecraft:ender_eye", 75.0),
        Map.entry("minecraft:ghast_tear", 90.0),
        Map.entry("minecraft:phantom_membrane", 65.0),
        Map.entry("minecraft:shulker_shell", 150.0),
        Map.entry("minecraft:shulker_box", 330.0),
        Map.entry("minecraft:wither_skeleton_skull", 1100.0),
        Map.entry("minecraft:skeleton_skull", 450.0),
        Map.entry("minecraft:zombie_head", 450.0),
        Map.entry("minecraft:creeper_head", 650.0),
        Map.entry("minecraft:piglin_head", 650.0),
        Map.entry("minecraft:dragon_head", 900.0),
        Map.entry("minecraft:nautilus_shell", 85.0),
        Map.entry("minecraft:experience_bottle", 35.0),
        Map.entry("minecraft:name_tag", 180.0),
        Map.entry("minecraft:saddle", 160.0),
        Map.entry("minecraft:lead", 28.0),
        Map.entry("minecraft:spyglass", 35.0),
        Map.entry("minecraft:clock", 175.0),
        Map.entry("minecraft:compass", 115.0),
        Map.entry("minecraft:lodestone", 3600.0),
        Map.entry("minecraft:respawn_anchor", 600.0),
        Map.entry("minecraft:crying_obsidian", 70.0),
        Map.entry("minecraft:obsidian", 45.0),
        Map.entry("minecraft:end_crystal", 280.0),
        Map.entry("minecraft:golden_apple", 420.0),
        Map.entry("minecraft:golden_carrot", 65.0),
        Map.entry("minecraft:glistering_melon_slice", 35.0),
        Map.entry("minecraft:carrot_on_a_stick", 45.0),
        Map.entry("minecraft:warped_fungus_on_a_stick", 50.0),
        Map.entry("minecraft:brush", 32.0),
        Map.entry("minecraft:bundle", 55.0),
        Map.entry("minecraft:decorated_pot", 28.0),
        Map.entry("minecraft:flower_pot", 5.0),
        Map.entry("minecraft:armor_stand", 40.0),
        Map.entry("minecraft:item_frame", 14.0),
        Map.entry("minecraft:glow_item_frame", 28.0),
        Map.entry("minecraft:painting", 18.0),
        Map.entry("minecraft:bell", 260.0),
        Map.entry("minecraft:brewing_stand", 75.0),
        Map.entry("minecraft:enchanting_table", 700.0),
        Map.entry("minecraft:anvil", 760.0),
        Map.entry("minecraft:chipped_anvil", 500.0),
        Map.entry("minecraft:damaged_anvil", 250.0),
        Map.entry("minecraft:ender_chest", 380.0),
        Map.entry("minecraft:jukebox", 280.0),
        Map.entry("minecraft:note_block", 35.0),
        Map.entry("minecraft:sculk_catalyst", 220.0),
        Map.entry("minecraft:sculk_shrieker", 260.0),
        Map.entry("minecraft:sculk_sensor", 120.0),
        Map.entry("minecraft:calibrated_sculk_sensor", 175.0),
        Map.entry("minecraft:spore_blossom", 75.0),
        Map.entry("minecraft:small_dripleaf", 35.0),
        Map.entry("minecraft:big_dripleaf", 20.0),
        Map.entry("minecraft:sniffer_egg", 600.0),
        Map.entry("minecraft:turtle_egg", 90.0),
        Map.entry("minecraft:armadillo_scute", 20.0),
        Map.entry("minecraft:turtle_scute", 35.0),
        Map.entry("minecraft:goat_horn", 180.0),
        Map.entry("minecraft:disc_fragment_5", 100.0)
    );

    private static final Map<String, Double> MATERIAL = Map.ofEntries(
        Map.entry("wooden", 1.0),
        Map.entry("stone", 2.0),
        Map.entry("copper", 7.0),
        Map.entry("golden", 42.0),
        Map.entry("iron", 24.0),
        Map.entry("diamond", 260.0),
        Map.entry("netherite", 3200.0),
        Map.entry("leather", 12.0),
        Map.entry("chainmail", 30.0)
    );

    private static final Set<String> VERY_RARE_SUFFIXES = Set.of(
        "_smithing_template", "_banner_pattern", "_pottery_sherd", "_armor_trim_smithing_template"
    );

    private static final Set<String> FARMABLE_HINTS = Set.of(
        "wheat", "carrot", "potato", "beetroot", "melon", "pumpkin", "bamboo", "cactus", "sugar_cane",
        "kelp", "mushroom", "sapling", "seeds", "egg", "feather", "leather", "wool", "honey", "slime",
        "string", "rotten_flesh", "bone", "gunpowder", "spider_eye", "cocoa", "nether_wart"
    );

    private BalancedPricing() {}

    public static PriceQuote quote(ItemStack stack, String categoryId, ShopConfig config) {
        String id = ShopService.itemId(stack);
        String path = id.substring(id.indexOf(':') + 1);
        String display = stack.getHoverName().getString().toLowerCase(LocaleHolder.ROOT);

        double buy = EXACT.getOrDefault(id, Double.NaN);
        if (!Double.isFinite(buy)) buy = calculate(path, display, categoryId);
        if (!Double.isFinite(buy) || buy <= 0) buy = categoryFloor(categoryId);

        buy = Math.max(config.minimumBuyPrice, nice(buy));
        double ratio = config.catalogSellRatio;
        if (isFarmable(path)) ratio *= 0.62;
        if (isRare(path, id)) ratio = Math.min(0.55, ratio * 1.25);
        double sell = nice(Math.max(0.01, buy * ratio));
        return new PriceQuote(buy, sell);
    }

    private static double calculate(String path, String display, String categoryId) {
        Double equipment = equipmentPrice(path);
        if (equipment != null) return equipment;

        if (path.endsWith("_music_disc") || path.startsWith("music_disc_")) return 650.0;
        if (path.endsWith("_smithing_template")) return smithingTemplatePrice(path);
        if (path.endsWith("_banner_pattern")) return 180.0;
        if (path.endsWith("_pottery_sherd")) return 140.0;
        if (path.endsWith("_spawn_egg")) return Double.NaN;

        if (path.startsWith("raw_")) {
            if (path.contains("gold")) return 34.0;
            if (path.contains("iron")) return 19.0;
            if (path.contains("copper")) return 5.0;
        }

        if (path.endsWith("_ore")) {
            if (path.contains("diamond")) return 300.0;
            if (path.contains("emerald")) return 260.0;
            if (path.contains("gold")) return 48.0;
            if (path.contains("iron")) return 30.0;
            if (path.contains("copper")) return 9.0;
            if (path.contains("coal")) return 10.0;
            if (path.contains("redstone")) return 16.0;
            if (path.contains("lapis")) return 18.0;
            if (path.contains("quartz")) return 14.0;
            return 8.0;
        }

        if (path.endsWith("_log") || path.endsWith("_stem") || path.endsWith("_hyphae") || path.endsWith("_wood")) return 2.5;
        if (path.contains("stripped_") && (path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem") || path.endsWith("_hyphae"))) return 3.0;
        if (path.endsWith("_planks")) return 0.75;
        if (path.endsWith("_leaves")) return 0.7;
        if (path.endsWith("_sapling") || path.endsWith("_propagule")) return 4.0;
        if (path.endsWith("_boat") || path.endsWith("_raft")) return 12.0;
        if (path.endsWith("_chest_boat") || path.endsWith("_chest_raft")) return 24.0;
        if (path.endsWith("_sign")) return 3.0;
        if (path.endsWith("_hanging_sign")) return 5.0;
        if (path.endsWith("_door")) return 3.5;
        if (path.endsWith("_trapdoor")) return 2.5;
        if (path.endsWith("_fence_gate")) return 2.5;
        if (path.endsWith("_fence")) return 1.5;
        if (path.endsWith("_pressure_plate")) return 1.5;
        if (path.endsWith("_button")) return 0.5;

        if (path.endsWith("_slab")) return baseBlockPrice(path) * 0.55;
        if (path.endsWith("_stairs")) return baseBlockPrice(path) * 1.55;
        if (path.endsWith("_wall")) return baseBlockPrice(path) * 1.05;

        if (path.endsWith("_wool")) return 5.0;
        if (path.endsWith("_carpet")) return 2.0;
        if (path.endsWith("_bed")) return 18.0;
        if (path.endsWith("_banner")) return 12.0;
        if (path.endsWith("_candle")) return 8.0;
        if (path.endsWith("_dye")) return 4.0;
        if (path.contains("concrete_powder")) return 2.0;
        if (path.contains("concrete")) return 2.5;
        if (path.contains("terracotta")) return path.contains("glazed") ? 4.0 : 2.5;
        if (path.contains("stained_glass_pane")) return 1.0;
        if (path.contains("stained_glass")) return 2.5;

        if (path.equals("cobblestone") || path.equals("cobbled_deepslate")) return 0.5;
        if (path.equals("stone") || path.equals("deepslate") || path.equals("netherrack")) return 1.0;
        if (path.contains("blackstone") || path.contains("basalt") || path.contains("tuff")) return 1.5;
        if (path.contains("sandstone") || path.contains("prismarine")) return 3.0;
        if (path.contains("bricks") || path.endsWith("_bricks")) return 4.0;
        if (path.contains("purpur")) return 5.0;
        if (path.contains("end_stone")) return 3.0;
        if (path.contains("moss")) return 3.0;
        if (path.contains("mud")) return 1.5;
        if (path.contains("ice")) return path.contains("blue") ? 18.0 : path.contains("packed") ? 8.0 : 3.0;

        if (path.endsWith("_bucket")) return bucketPrice(path);
        if (path.equals("bucket")) return 72.0;
        if (path.equals("glass_bottle")) return 2.0;
        if (path.equals("potion") || path.equals("splash_potion") || path.equals("lingering_potion")) return potionPrice(path, display);
        if (path.equals("tipped_arrow")) return 12.0 + potionTier(display) * 18.0;
        if (path.equals("suspicious_stew")) return 18.0 + potionTier(display) * 8.0;

        if (path.endsWith("_horse_armor")) return horseArmorPrice(path);
        if (path.endsWith("_minecart")) return path.equals("minecart") ? 125.0 : 175.0;
        if (path.endsWith("_rail") || path.equals("rail")) return railPrice(path);
        if (path.contains("firework_rocket")) return 10.0;
        if (path.contains("firework_star")) return 16.0;

        if (path.contains("cooked_") || path.startsWith("cooked_")) return 12.0;
        if (path.contains("raw_") || path.startsWith("raw_")) return 7.0;
        if (path.contains("bread")) return 9.0;
        if (path.contains("cake")) return 35.0;
        if (path.contains("cookie")) return 3.0;
        if (path.contains("stew") || path.contains("soup")) return 14.0;
        if (path.contains("pie")) return 18.0;
        if (path.contains("apple")) return 12.0;
        if (path.contains("berry")) return 4.0;
        if (path.contains("fish") || path.contains("salmon") || path.contains("cod")) return 8.0;

        if (path.endsWith("_flower") || path.contains("tulip") || path.equals("allium") || path.equals("azure_bluet") || path.equals("poppy") || path.equals("dandelion")) return 3.0;
        if (path.contains("coral")) return 9.0;
        if (path.contains("sculk")) return 16.0;

        if (categoryId.equals("combat")) return 45.0;
        if (categoryId.equals("tools")) return 35.0;
        if (categoryId.equals("redstone")) return 18.0;
        if (categoryId.equals("foodstuffs")) return 8.0;
        if (categoryId.equals("materials")) return 12.0;
        if (categoryId.equals("functional_blocks")) return 20.0;
        if (categoryId.equals("colored_blocks")) return 3.0;
        if (categoryId.equals("natural_blocks")) return 2.0;
        return 1.5;
    }

    private static Double equipmentPrice(String path) {
        for (Map.Entry<String, Double> material : MATERIAL.entrySet()) {
            String prefix = material.getKey() + "_";
            if (!path.startsWith(prefix)) continue;
            double unit = material.getValue();
            if (path.endsWith("_sword")) return unit * 2.0 + 8.0;
            if (path.endsWith("_pickaxe")) return unit * 3.0 + 8.0;
            if (path.endsWith("_axe")) return unit * 3.0 + 10.0;
            if (path.endsWith("_shovel")) return unit + 5.0;
            if (path.endsWith("_hoe")) return unit * 2.0 + 5.0;
            if (path.endsWith("_helmet")) return unit * 5.0 + 12.0;
            if (path.endsWith("_chestplate")) return unit * 8.0 + 18.0;
            if (path.endsWith("_leggings")) return unit * 7.0 + 16.0;
            if (path.endsWith("_boots")) return unit * 4.0 + 10.0;
        }
        if (path.equals("bow")) return 28.0;
        if (path.equals("crossbow")) return 80.0;
        if (path.equals("shield")) return 50.0;
        if (path.equals("fishing_rod")) return 22.0;
        if (path.equals("shears")) return 50.0;
        if (path.equals("flint_and_steel")) return 30.0;
        return null;
    }

    private static double smithingTemplatePrice(String path) {
        if (path.contains("netherite_upgrade")) return 1800.0;
        if (path.contains("silence")) return 2200.0;
        if (path.contains("spire") || path.contains("ward")) return 1200.0;
        if (path.contains("rib") || path.contains("snout") || path.contains("eye")) return 850.0;
        return 550.0;
    }

    private static double baseBlockPrice(String path) {
        String base = path
            .replace("_slab", "")
            .replace("_stairs", "")
            .replace("_wall", "");
        if (base.contains("quartz")) return 12.0;
        if (base.contains("prismarine")) return 7.0;
        if (base.contains("deepslate")) return 2.0;
        if (base.contains("blackstone")) return 2.0;
        if (base.contains("brick")) return 4.0;
        if (base.contains("sandstone")) return 3.0;
        if (base.contains("purpur")) return 5.0;
        return 1.5;
    }

    private static double bucketPrice(String path) {
        if (path.equals("water_bucket")) return 78.0;
        if (path.equals("lava_bucket")) return 95.0;
        if (path.equals("milk_bucket")) return 85.0;
        if (path.equals("powder_snow_bucket")) return 110.0;
        if (path.contains("axolotl")) return 230.0;
        if (path.contains("tadpole")) return 160.0;
        if (path.contains("pufferfish")) return 150.0;
        if (path.contains("salmon") || path.contains("cod")) return 120.0;
        if (path.contains("tropical_fish")) return 175.0;
        return 90.0;
    }

    private static double potionPrice(String path, String display) {
        double base = path.equals("lingering_potion") ? 110.0 : path.equals("splash_potion") ? 75.0 : 50.0;
        return base + potionTier(display) * 35.0;
    }

    private static int potionTier(String display) {
        if (display.contains("invisibility") || display.contains("slow falling") || display.contains("turtle master")) return 4;
        if (display.contains("strength") || display.contains("regeneration") || display.contains("healing") || display.contains("harming")) return 3;
        if (display.contains("swiftness") || display.contains("fire resistance") || display.contains("night vision") || display.contains("poison")) return 2;
        if (display.contains("water breathing") || display.contains("leaping") || display.contains("weakness") || display.contains("slowness")) return 2;
        return 1;
    }

    private static double horseArmorPrice(String path) {
        if (path.startsWith("diamond")) return 900.0;
        if (path.startsWith("golden")) return 250.0;
        if (path.startsWith("iron")) return 180.0;
        if (path.startsWith("leather")) return 70.0;
        return 120.0;
    }

    private static double railPrice(String path) {
        if (path.equals("powered_rail")) return 18.0;
        if (path.equals("detector_rail")) return 14.0;
        if (path.equals("activator_rail")) return 14.0;
        return 4.0;
    }

    private static double categoryFloor(String categoryId) {
        return switch (categoryId) {
            case "combat" -> 30.0;
            case "tools" -> 24.0;
            case "redstone" -> 10.0;
            case "functional_blocks" -> 12.0;
            case "materials" -> 6.0;
            case "foodstuffs" -> 4.0;
            case "colored_blocks" -> 1.5;
            case "natural_blocks" -> 1.0;
            default -> 0.75;
        };
    }

    private static boolean isFarmable(String path) {
        for (String hint : FARMABLE_HINTS) if (path.contains(hint)) return true;
        return path.endsWith("_log") || path.endsWith("_planks") || path.endsWith("_leaves");
    }

    private static boolean isRare(String path, String id) {
        if (EXACT.getOrDefault(id, 0.0) >= 500.0) return true;
        for (String suffix : VERY_RARE_SUFFIXES) if (path.endsWith(suffix)) return true;
        return path.startsWith("music_disc_") || path.endsWith("_head") || path.endsWith("_skull");
    }

    private static double nice(double value) {
        if (value < 1.0) return Math.round(value * 4.0) / 4.0;
        if (value < 10.0) return Math.round(value * 2.0) / 2.0;
        if (value < 100.0) return Math.round(value);
        if (value < 1000.0) return Math.round(value / 5.0) * 5.0;
        return Math.round(value / 25.0) * 25.0;
    }

    public record PriceQuote(double buy, double sell) {}

    private static final class LocaleHolder {
        private static final java.util.Locale ROOT = java.util.Locale.ROOT;
    }
}
