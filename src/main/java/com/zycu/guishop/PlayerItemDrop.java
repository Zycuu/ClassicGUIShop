package com.zycu.guishop;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Keeps inventory overflow drops compatible with the 26.3 prediction parameter. */
final class PlayerItemDrop {
    private static final MethodHandle DROP = resolveDrop();

    private PlayerItemDrop() {}

    private static MethodHandle resolveDrop() {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        try {
            MethodHandle legacy = lookup.findVirtual(ServerPlayer.class, "drop",
                MethodType.methodType(ItemEntity.class, ItemStack.class, boolean.class));
            return MethodHandles.insertArguments(legacy, 2, false);
        } catch (NoSuchMethodException missingLegacyMethod) {
            try {
                Class<?> prediction = Class.forName("net.minecraft.util.Prediction");
                Object serverOnly = prediction.getField("SERVER_ONLY").get(null);
                MethodHandle current = lookup.findVirtual(ServerPlayer.class, "drop",
                    MethodType.methodType(ItemEntity.class, ItemStack.class, boolean.class, prediction));
                return MethodHandles.insertArguments(current, 2, false, serverOnly);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Unsupported Minecraft player item-drop API", failure);
            }
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot access Minecraft player item-drop API", failure);
        }
    }

    static void drop(ServerPlayer player, ItemStack stack) {
        try {
            ItemEntity ignored = (ItemEntity) DROP.invokeExact(player, stack);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Cannot drop shop inventory overflow", failure);
        }
    }
}
