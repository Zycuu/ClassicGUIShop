package com.zycu.guishop;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ColoredBlockGroups {
    private static final List<Group> GROUPS = List.of(
        new Group("wool", "Wool", "minecraft:white_wool"),
        new Group("carpet", "Carpet", "minecraft:white_carpet"),
        new Group("concrete", "Concrete", "minecraft:white_concrete"),
        new Group("concrete_powder", "Concrete Powder", "minecraft:white_concrete_powder"),
        new Group("terracotta", "Terracotta", "minecraft:white_terracotta"),
        new Group("glazed_terracotta", "Glazed Terracotta", "minecraft:white_glazed_terracotta"),
        new Group("stained_glass", "Stained Glass", "minecraft:white_stained_glass"),
        new Group("stained_glass_pane", "Stained Glass Panes", "minecraft:white_stained_glass_pane"),
        new Group("beds", "Beds", "minecraft:white_bed"),
        new Group("candles", "Candles", "minecraft:white_candle"),
        new Group("banners", "Banners", "minecraft:white_banner"),
        new Group("shulker_boxes", "Shulker Boxes", "minecraft:white_shulker_box"),
        new Group("other", "Other Colored Blocks", "minecraft:cyan_wool")
    );

    private ColoredBlockGroups() {}

    public static List<Group> definitions() {
        return GROUPS;
    }

    public static Group groupFor(ShopConfig.ShopItem item) {
        String identifier = item == null || item.item == null ? "" : item.item.toLowerCase(Locale.ROOT);
        String path = identifier.contains(":") ? identifier.substring(identifier.indexOf(':') + 1) : identifier;

        if (path.endsWith("_stained_glass_pane")) return byId("stained_glass_pane");
        if (path.endsWith("_stained_glass")) return byId("stained_glass");
        if (path.endsWith("_concrete_powder")) return byId("concrete_powder");
        if (path.endsWith("_glazed_terracotta")) return byId("glazed_terracotta");
        if (path.endsWith("_terracotta")) return byId("terracotta");
        if (path.endsWith("_concrete")) return byId("concrete");
        if (path.endsWith("_wool")) return byId("wool");
        if (path.endsWith("_carpet")) return byId("carpet");
        if (path.endsWith("_bed")) return byId("beds");
        if (path.endsWith("_candle")) return byId("candles");
        if (path.endsWith("_banner")) return byId("banners");
        if (path.endsWith("_shulker_box")) return byId("shulker_boxes");
        return byId("other");
    }

    public static Group byId(String id) {
        for (Group group : GROUPS) {
            if (group.id().equalsIgnoreCase(id)) return group;
        }
        return GROUPS.get(GROUPS.size() - 1);
    }

    public static Map<Group, List<ShopConfig.ShopItem>> group(List<ShopConfig.ShopItem> items) {
        Map<Group, List<ShopConfig.ShopItem>> grouped = new LinkedHashMap<>();
        for (Group definition : GROUPS) grouped.put(definition, new java.util.ArrayList<>());
        for (ShopConfig.ShopItem item : items) grouped.get(groupFor(item)).add(item);
        return grouped;
    }

    public record Group(String id, String name, String icon) {}
}
