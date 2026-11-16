package com.zycu.guishop;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;

final class PlayerItemDrop {
    private PlayerItemDrop() {}

    static void drop(ServerPlayer player, ItemStack stack) {
        player.drop(stack, false, Prediction.SERVER_ONLY);
    }
}
