package com.zycu.guishop.admin;

import com.zycu.guishop.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import java.util.*;

/** Planning for explicit editor previews and first-install Vanilla Normal setup. A plan changes no live state. */
public final class ShopPresets {
    private ShopPresets() {}
    public record Request(String preset,double normalRatio,Double bookRatio,Double potionRatio) {
        public Request(String preset,double normalRatio){this(preset,normalRatio,.30,.30);}
    }
    public record Plan(String preset, List<ShopConfig.Category> categories, Map<String, ShopConfig.EnchantmentOffer> enchantments, double ratio, int items, int locked, int capped, int recipes, String warning) {}
    private record Group(String tab, String id, String name, String icon) {}
    private static final List<Group> GROUPS = List.of(
        new Group("building_blocks", "building_blocks", "Building Blocks", "minecraft:bricks"), new Group("colored_blocks", "colored_blocks", "Colored Blocks", "minecraft:cyan_wool"),
        new Group("natural_blocks", "natural_blocks", "Natural Blocks", "minecraft:grass_block"), new Group("functional_blocks", "functional_blocks", "Functional Blocks", "minecraft:crafting_table"),
        new Group("redstone_blocks", "redstone", "Redstone", "minecraft:redstone"), new Group("tools_and_utilities", "tools", "Tools and Utilities", "minecraft:diamond_pickaxe"),
        new Group("combat", "combat", "Combat", "minecraft:diamond_sword"), new Group("food_and_drinks", "foodstuffs", "Food and Drinks", "minecraft:golden_apple"), new Group("ingredients", "materials", "Ingredients", "minecraft:iron_ingot"));
    private static final Set<String> LOCKED = Set.of("air", "barrier", "bedrock", "budding_amethyst", "chain_command_block", "command_block", "command_block_minecart", "debug_stick", "end_portal_frame", "jigsaw", "knowledge_book", "light", "reinforced_deepslate", "repeating_command_block", "spawner", "structure_block", "structure_void", "trial_spawner", "vault", "enchanted_book");
    public static Plan generate(ServerPlayer player, Request request) {
        if (!AdminEditorService.allowed(player)) throw new SecurityException(AdminEditorService.DENIED);
        return generate(player.level().getServer(),request);
    }
    public static Plan starter(net.minecraft.server.MinecraftServer server){return generate(server,new Request("normal",.30,.30,.30));}
    private static Plan generate(net.minecraft.server.MinecraftServer server,Request request){
        if (request == null || !Set.of("normal","hard","easy","basics","blank").contains(request.preset()) || !Double.isFinite(request.normalRatio()) || request.normalRatio() < 0 || request.normalRatio() > 1) throw new IllegalArgumentException("Invalid preset");
        for(Double value:new Double[]{request.bookRatio(),request.potionRatio()})if(value!=null&&(!Double.isFinite(value)||value<0||value>1))throw new IllegalArgumentException("Invalid default variant return");
        double bookRatio=request.bookRatio()==null?GuiShop.CONFIG.bookSellRatio:request.bookRatio(), potionRatio=request.potionRatio()==null?GuiShop.CONFIG.potionSellRatio:request.potionRatio();
        if(request.preset().equals("blank"))return new Plan("blank",List.of(),Map.of(),0,0,0,0,0,"Start with an empty shop and create your own categories and listings.");
        var access = server.registryAccess(); var config = new ShopConfig();
        double ratio = switch (request.preset()) { case "hard" -> .10; case "easy" -> .95; default -> request.normalRatio(); };
        if(request.preset().equals("hard")||request.preset().equals("easy")){bookRatio=ratio;potionRatio=ratio;}
        double buyFactor = switch (request.preset()) { case "hard" -> 3; case "easy" -> .75; default -> 1; };
        var used = new HashSet<String>(); for (var category : GuiShop.CONFIG.categories) for (var item : category.items) used.add(item.listingId.toLowerCase(Locale.ROOT));
        var identities = new HashSet<String>(); int locked = 0;
        var tabs = access.lookupOrThrow(Registries.CREATIVE_MODE_TAB);
        var parameters = new CreativeModeTab.ItemDisplayParameters(server.getWorldData().enabledFeatures(), false, access);
        for (var group : GROUPS) {
            var category = new ShopConfig.Category(); category.id = group.id(); category.name = group.name(); category.icon = group.icon();
            var holder = tabs.get(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace(group.tab())));
            if (holder.isEmpty()) continue;
            holder.get().value().buildContents(parameters);
            for (var stack : holder.get().value().getDisplayItems()) {
                var potion = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
                if (potion != null && potion.potion().flatMap(h -> h.unwrapKey()).map(k -> !k.identifier().getNamespace().equals("minecraft")).orElse(false)) continue;
                String id = ShopService.itemId(stack), identity = ItemStackData.listingId(stack, access);
                if (!id.startsWith("minecraft:") || !identities.add(identity)) continue;
                if (request.preset().equals("basics") && !basic(id.substring(10), group.id())) continue;
                var item = listing(stack, category.id, ratio, buyFactor, used, access); if (item.buy == 0) locked++;
                category.items.add(item);
            }
            if (!category.items.isEmpty()) config.categories.add(category);
        }
        // Keep every vanilla registry item inspectable, including locked administrative items.
        if (!request.preset().equals("basics")) {
            var other = new ShopConfig.Category(); other.id = "other_vanilla"; other.name = "Other Vanilla"; other.icon = "minecraft:chest";
            for (var id : BuiltInRegistries.ITEM.keySet().stream().filter(id -> id.getNamespace().equals("minecraft")).sorted(Comparator.comparing(Object::toString)).toList()) {
                var stack = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
                if (stack.isEmpty() || !identities.add(ItemStackData.listingId(stack, access))) continue;
                var item = listing(stack, other.id, ratio, buyFactor, used, access);
                // Outside survival creative tabs, require explicit administrator pricing.
                item.buy = 0; item.sell = 0; locked++; other.items.add(item);
            }
            if (!other.items.isEmpty()) config.categories.add(other);
        }
        int count = config.categories.stream().mapToInt(category -> category.items.size()).sum();
        if (!request.preset().equals("basics")) for (var holder : access.lookupOrThrow(Registries.ENCHANTMENT).listElements().toList()) {
            String id = holder.key().identifier().toString(); if (VanillaPriceSafety.maximumLevel(id)==0) continue;
            var offer = new ShopConfig.EnchantmentOffer(); offer.name = holder.value().description().getString(); offer.pricePerLevel = 100 * buyFactor; offer.maxLevel = Math.min(holder.value().getMaxLevel(),VanillaPriceSafety.maximumLevel(id)); config.enchantments.put(id, offer);
        }
        PriceMigration.flattenBooks(config, access, 1, bookRatio);
        VanillaPriceSafety.pricePotions(config, access);
        for(var category:config.categories)for(var item:category.items)if(ListingGroups.potion(item.item)&&item.buy>0)item.sell=floor(item.buy*potionRatio);
        int capped=VanillaPriceSafety.apply(config,access);
        ListingGroups.separatePotions(config);
        ListingGroups.separateTippedArrows(config);
        count = config.categories.stream().mapToInt(category -> category.items.size()).sum();
        return new Plan(request.preset(), config.categories, Map.of(), ratio, count, locked, capped, 2042, "Vanilla recipes, instant interactions and discounted merchants were audited. Unavoidable loop outputs are buy-only. Modded content and altered datapack mechanics are the admin's responsibility.");
    }
    private static ShopConfig.ShopItem listing(ItemStack stack, String category, double ratio, double factor, Set<String> used, net.minecraft.core.RegistryAccess access) {
        var item = new ShopConfig.ShopItem(); item.item = ShopService.itemId(stack); item.stack = ItemStackData.encode(stack, access); item.name = stack.getHoverName().getString();
        item.listingId = ItemStackData.unusedListingId(ItemStackData.listingId(stack, access), used); used.add(item.listingId.toLowerCase(Locale.ROOT)); item.manualPrice = true;
        if (ListingGroups.potion(item.item) || item.item.equals("minecraft:tipped_arrow")) {
            var contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
            if (contents != null) contents.potion().flatMap(h -> h.unwrapKey()).ifPresent(key -> {
                String potion = key.identifier().getPath();
                if (potion.startsWith("long_")) item.name += " (Extended)";
                else if (potion.startsWith("strong_")) item.name += " (Strong)";
            });
        }
        String path = item.item.substring(10);
        if (!LOCKED.contains(path) && !path.endsWith("_spawn_egg")) { item.buy = Math.round(PresetPricing.buy(stack, category) * factor * 100) / 100.0; item.sell = stack.getComponentsPatch().isEmpty() ? floor(item.buy * ratio) : 0; }
        return item;
    }
    private static boolean basic(String path, String group) {
        return group.equals("materials") && !path.contains("smithing_template") && !path.contains("banner_pattern") && !path.contains("pottery_sherd") && !path.startsWith("music_disc")
            || !path.startsWith("stripped_") && (path.endsWith("_log") || path.endsWith("_stem") || path.endsWith("_ore"))
            || Set.of("ancient_debris", "obsidian", "sand", "gravel", "clay", "dirt", "cobblestone", "bamboo", "sugar_cane", "cactus", "nether_wart", "wheat", "carrot", "potato", "beetroot", "kelp").contains(path);
    }
    private static double floor(double value) { return Math.max(0, Math.floor((value + .000001) * 100) / 100.0); }
}
