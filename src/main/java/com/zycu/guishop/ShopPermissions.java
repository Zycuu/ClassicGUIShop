package com.zycu.guishop;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class ShopPermissions {
    private static final List<Method> LEGACY_PERMISSION_METHODS = findLegacyPermissionMethods();
    private static final List<Field> LEGACY_PERMISSION_LEVEL_FIELDS = findLegacyPermissionLevelFields();
    private static final Object ALL_PERMISSIONS = findStaticFieldValue(
        "net.minecraft.server.permissions.PermissionSet",
        "ALL_PERMISSIONS"
    );

    private ShopPermissions() {}

    public static void logPermissionAccessors() {
        if (ALL_PERMISSIONS != null) {
            System.out.println("[ClassicGUIShop] Permission model detected: Minecraft PermissionSet.ALL_PERMISSIONS.");
            return;
        }
        if (!LEGACY_PERMISSION_METHODS.isEmpty()) {
            System.out.println("[ClassicGUIShop] Permission model detected: legacy CommandSourceStack boolean(int) method.");
            return;
        }
        if (!LEGACY_PERMISSION_LEVEL_FIELDS.isEmpty()) {
            System.out.println("[ClassicGUIShop] Permission model detected: legacy CommandSourceStack int permission field.");
            return;
        }
        System.err.println("[ClassicGUIShop] Could not detect a permission model. Admin commands may be hidden from players.");
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

        if (hasAllPermissions(source)) return true;

        ServerPlayer player = null;
        try {
            player = source.getPlayer();
        } catch (RuntimeException ignored) {
            // Older mappings may throw for non-player sources. Non-player command sources are handled below.
        }

        if (player != null && hasAllPermissions(player)) return true;
        if (player == null) return true;

        if (hasLegacyPermissionLevel(source, level)) return true;
        return hasLevelObject(source, level);
    }

    private static boolean hasAllPermissions(Object target) {
        if (ALL_PERMISSIONS == null || target == null) return false;
        Object permissions = invokeNoArg(target, "permissions");
        return permissions == ALL_PERMISSIONS || ALL_PERMISSIONS.equals(permissions);
    }

    private static boolean hasLegacyPermissionLevel(CommandSourceStack source, int level) {
        for (Method method : LEGACY_PERMISSION_METHODS) {
            try {
                Object result = method.invoke(source, level);
                if (result instanceof Boolean value && value) return true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Try the next boolean(int) method candidate.
            }
        }

        for (Field field : LEGACY_PERMISSION_LEVEL_FIELDS) {
            try {
                if (field.getInt(source) >= level) return true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Try the next int field candidate.
            }
        }
        return false;
    }

    private static boolean hasLevelObject(CommandSourceStack source, int level) {
        Object value = invokeNoArg(source, "levels");
        if (!(value instanceof Collection<?> collection)) return false;

        for (Object entry : collection) {
            Integer parsed = parsePermissionLevel(entry);
            if (parsed != null && parsed >= level) return true;
        }
        return false;
    }

    private static Integer parsePermissionLevel(Object entry) {
        if (entry == null) return null;
        if (entry instanceof Number number) return number.intValue();

        String text = String.valueOf(entry).toLowerCase(Locale.ROOT);
        int best = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '4') best = Math.max(best, c - '0');
        }
        return best >= 0 ? best : null;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }
        return null;
    }

    private static List<Method> findLegacyPermissionMethods() {
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

    private static List<Field> findLegacyPermissionLevelFields() {
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

    private static Object findStaticFieldValue(String className, String fieldName) {
        try {
            Class<?> type = Class.forName(className);
            Field field = type.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static String sanitize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }
}
