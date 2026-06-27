package com.zycu.guishop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;

import java.lang.reflect.Field;
import java.util.Map;

public final class CommandTreeCleanup {
    private static final String[] CHILD_MAP_FIELDS = {"children", "literals", "arguments"};

    private CommandTreeCleanup() {}

    public static void prune(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandNode<CommandSourceStack> admin = dispatcher.getRoot().getChild("adminshop");
        if (admin == null) return;

        CommandNode<CommandSourceStack> advanced = admin.getChild("advanced");
        CommandNode<CommandSourceStack> imports = admin.getChild("import");

        removeChild(admin, "catalog");
        removeChild(advanced, "catalog");
        removeChild(imports, "preview");
    }

    public static void removeRoot(CommandDispatcher<CommandSourceStack> dispatcher, String childName) {
        removeChild(dispatcher.getRoot(), childName);
    }

    private static void removeChild(CommandNode<CommandSourceStack> parent, String childName) {
        if (parent == null) return;

        try {
            for (String fieldName : CHILD_MAP_FIELDS) {
                Field field = CommandNode.class.getDeclaredField(fieldName);
                if (!field.trySetAccessible()) {
                    throw new IllegalAccessException("Could not access Brigadier field " + fieldName);
                }
                Object value = field.get(parent);
                if (value instanceof Map<?, ?> map) map.remove(childName);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                "ClassicGUIShop could not remove unused command /" + childName,
                exception
            );
        }
    }
}
