package com.zycu.guishop;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

/** Explicit provider rules win; unassigned nodes use the server's exact vanilla permission set. */
public final class ShopPermissions {
    public static final String ADMIN = "guishop.admin";
    private static final boolean API = FabricLoader.getInstance().isModLoaded("fabric-permissions-api-v0");
    private static boolean reportedFailure;
    private ShopPermissions() {}
    public record Info(boolean api, String name) {}
    public static Info info() {
        return new Info(API, API ? "Fabric Permissions API compatibility" : "Vanilla OP levels");
    }
    public static void logPermissionAccessors() {
        System.out.println("[ClassicGUIShop] Permissions: " + info().name() + ". Admin node: " + ADMIN + " (fallback OP 2).");
    }
    public static boolean check(CommandSourceStack source, String node, int fallbackLevel) {
        int level = GuiShop.CONFIG == null ? fallbackLevel : GuiShop.CONFIG.permissionLevel(node, fallbackLevel);
        return resolve(source, node, level);
    }
    public static boolean admin(CommandSourceStack source) {
        // A legacy config entry must never lower the administrative fallback.
        return resolve(source, ADMIN, 2);
    }
    private static boolean resolve(CommandSourceStack source, String node, int level) {
        if (level < 0 || level > 4) return false;
        if (!API) return source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)));
        try { return Provider.check(source, node, level); }
        catch (RuntimeException | LinkageError failure) {
            // Provider failure is not an unassigned rule. Deny rather than silently bypass the provider.
            if (!reportedFailure) { reportedFailure = true; System.err.println("[ClassicGUIShop] Permission provider failed; access denied. " + failure); }
            return false;
        }
    }
    private static final class Provider {
        static boolean check(CommandSourceStack source, String node, int level) {
            return me.lucko.fabric.api.permissions.v0.Permissions.check(source, node, PermissionLevel.byId(level));
        }
    }
    public static boolean check(ServerPlayer player, String node, int fallbackLevel) { return check(player.createCommandSourceStack(), node, fallbackLevel); }
    public static boolean user(CommandSourceStack source, String node) { return check(source, node, 0); }
    public static boolean user(ServerPlayer player, String node) { return check(player, node, 0); }
    public static boolean category(ServerPlayer player, String categoryId) { return check(player, "guishop.category." + sanitize(categoryId), 0); }
    private static String sanitize(String value) { return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"); }
}
