package com.zycu.guishop;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ShopPermissions {
    private static final List<Method> PERMISSION_METHODS = findPermissionMethods();

    private ShopPermissions() {}

    public static void logPermissionAccessors() {
        if (PERMISSION_METHODS.isEmpty()) {
            System.err.println("[ClassicGUIShop] Could not find a CommandSourceStack boolean(int) permission accessor. Admin commands may be hidden from players.");
            return;
        }
        StringBuilder names = new StringBuilder();
        for (Method method : PERMISSION_METHODS) {
            if (!names.isEmpty()) names.append(", ");
            names.append(method.getName());
        }
        System.out.println("[ClassicGUIShop] Permission accessor candidate(s): " + names);
    }

    public static boolean check(CommandSourceStack source, String node, int fallbackLevel) {
        int level = GuiShop.CONFIG == null ? fallbackLevel : GuiShop.CONFIG.permissionLevel(node, fallbackLevel);
        return hasPermissionLevel(source, level);
    }

    public static boolean check(ServerPlayer player, String node, int fallbackLevel) {
        return check(player.createCommandSourceStack(), node, fallbackLevel);
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

    private static boolean hasPermissionLevel(CommandSourceStack source, int level) {
        if (level <= 0) return true;
        for (Method method : PERMISSION_METHODS) {
            try {
                Object result = method.invoke(source, level);
                if (result instanceof Boolean value && value) return true;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Try the next boolean(int) method candidate.
            }
        }
        return false;
    }

    private static List<Method> findPermissionMethods() {
        List<Method> methods = new ArrayList<>();
        for (Method method : CommandSourceStack.class.getMethods()) {
            if (method.getReturnType() != boolean.class) continue;
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != 1 || parameterTypes[0] != int.class) continue;
            method.setAccessible(true);
            methods.add(method);
        }
        return List.copyOf(methods);
    }

    private static String sanitize(String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }
}
