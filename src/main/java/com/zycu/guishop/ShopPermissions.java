package com.zycu.guishop;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

/** Minecraft 26.3 permission checks honor the configured level exactly. */
public final class ShopPermissions {
    private ShopPermissions() {}
    public static void logPermissionAccessors() {
        System.out.println("[ClassicGUIShop] Permission model detected: Minecraft PermissionSet with exact command levels.");
    }
    public static boolean check(CommandSourceStack source, String node, int fallbackLevel) {
        int level = GuiShop.CONFIG == null ? fallbackLevel : GuiShop.CONFIG.permissionLevel(node, fallbackLevel);
        if (level < 0 || level > 4) return false;
        return source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)));
    }
    public static boolean check(ServerPlayer player, String node, int fallbackLevel) {
        return check(player.createCommandSourceStack(), node, fallbackLevel);
    }
    public static boolean user(CommandSourceStack source, String node) { return check(source, node, 0); }
    public static boolean user(ServerPlayer player, String node) { return check(player, node, 0); }
    public static boolean category(ServerPlayer player, String categoryId) {
        return check(player, "guishop.category." + sanitize(categoryId), 0);
    }
    private static String sanitize(String categoryId) {
        return categoryId.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }
}
