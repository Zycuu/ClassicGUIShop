package com.zycu.guishop;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public final class ShopPermissions {
    private ShopPermissions() {}

    public static boolean check(CommandSourceStack source, String node, int fallbackLevel) {
        int level = GuiShop.CONFIG == null ? fallbackLevel : GuiShop.CONFIG.permissionLevel(node, fallbackLevel);
        return Permissions.check(source, node, level);
    }

    public static boolean check(ServerPlayer player, String node, int fallbackLevel) {
        int level = GuiShop.CONFIG == null ? fallbackLevel : GuiShop.CONFIG.permissionLevel(node, fallbackLevel);
        return Permissions.check(player, node, level);
    }

    public static boolean user(CommandSourceStack source, String node) {
        return check(source, node, 0);
    }

    public static boolean user(ServerPlayer player, String node) {
        return check(player, node, 0);
    }

    public static boolean admin(CommandSourceStack source, String child) {
        return check(source, "guishop.admin", 2) || check(source, "guishop.admin." + child, 2);
    }

    public static boolean admin(ServerPlayer player, String child) {
        return check(player, "guishop.admin", 2) || check(player, "guishop.admin." + child, 2);
    }

    public static boolean category(ServerPlayer player, String categoryId) {
        return check(player, "guishop.category." + sanitize(categoryId), 0);
    }

    private static String sanitize(String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }
}
