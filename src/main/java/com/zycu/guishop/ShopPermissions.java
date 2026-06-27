package com.zycu.guishop;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public final class ShopPermissions {
    private static final List<Method> PERMISSION_METHODS = findPermissionMethods();
    private static final List<Field> PERMISSION_LEVEL_FIELDS = findPermissionLevelFields();

    private ShopPermissions() {}

    public static void logPermissionAccessors() {
        if (!PERMISSION_METHODS.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (Method method : PERMISSION_METHODS) {
                if (!names.isEmpty()) names.append(", ");
                names.append(method.getName());
            }
            System.out.println("[ClassicGUIShop] Permission method candidate(s): " + names);
            return;
        }

        if (!PERMISSION_LEVEL_FIELDS.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (Field field : PERMISSION_LEVEL_FIELDS) {
                if (!names.isEmpty()) names.append(", ");
                names.append(field.getName());
            }
            System.out.println("[ClassicGUIShop] Permission level field candidate(s): " + names);
            return;
        }

        System.err.println("[ClassicGUIShop] Could not find a CommandSourceStack permission accessor. Admin commands may be hidden from players.");
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
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Try the next boolean(int) method candidate.
            }
        }

        for (Field field : PERMISSION_LEVEL_FIELDS) {
            try {
                if (field.getInt(source) >= level) return true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Try the next int field candidate.
            }
        }

        return false;
    }

    private static List<Method> findPermissionMethods() {
        List<Method> methods = new ArrayList<>();
        Class<?> type = CommandSourceStack.class;
        while (type != null && type != Object.class) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getReturnType() != boolean.class) continue;
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 1 || parameterTypes[0] != int.class) continue;
                method.setAccessible(true);
                methods.add(method);
            }
            type = type.getSuperclass();
        }
        return List.copyOf(methods);
    }

    private static List<Field> findPermissionLevelFields() {
        List<Field> fields = new ArrayList<>();
        Class<?> type = CommandSourceStack.class;
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != int.class) continue;
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                fields.add(field);
            }
            type = type.getSuperclass();
        }
        return List.copyOf(fields);
    }

    private static String sanitize(String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }
}
