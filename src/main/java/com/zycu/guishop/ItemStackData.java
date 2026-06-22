package com.zycu.guishop;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class ItemStackData {
    private ItemStackData() {}

    public static JsonElement encode(ItemStack stack, RegistryAccess access) {
        DynamicOps<JsonElement> ops = access.createSerializationContext(JsonOps.INSTANCE);
        return ItemStack.CODEC.encodeStart(ops, stack.copyWithCount(1)).getOrThrow();
    }

    public static ItemStack decode(JsonElement data, String fallbackItemId, RegistryAccess access) {
        if (data != null && !data.isJsonNull()) {
            try {
                DynamicOps<JsonElement> ops = access.createSerializationContext(JsonOps.INSTANCE);
                ItemStack decoded = ItemStack.CODEC.parse(ops, data).getOrThrow();
                if (!decoded.isEmpty()) return decoded.copyWithCount(1);
            } catch (Exception exception) {
                System.err.println("[ClassicGUIShop] Could not decode configured item stack " + fallbackItemId + ": " + exception.getMessage());
            }
        }

        try {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(fallbackItemId));
            if (item != null && item != Items.AIR) return new ItemStack(item);
        } catch (Exception ignored) {
        }
        return ItemStack.EMPTY;
    }

    public static boolean same(ItemStack first, ItemStack second) {
        if (first == null || second == null || first.isEmpty() || second.isEmpty()) return false;
        return ItemStack.isSameItemSameComponents(first, second);
    }

    public static String listingId(ItemStack stack, RegistryAccess access) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (stack.getComponentsPatch().isEmpty()) return itemId;
        return itemId + "#" + shortHash(encode(stack, access).toString());
    }

    public static String shortHash(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < 6; index++) builder.append(String.format("%02x", hash[index]));
            return builder.toString();
        } catch (Exception exception) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
