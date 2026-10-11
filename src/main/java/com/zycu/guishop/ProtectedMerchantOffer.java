package com.zycu.guishop;

import net.minecraft.server.MinecraftServer;

/** The native cost fields remain original, including in vanilla saved data. */
public interface ProtectedMerchantOffer {
    void guishop$protection(MinecraftServer server);
    MinecraftServer guishop$owner();
    net.minecraft.world.item.trading.ItemCost guishop$originalA();
    java.util.Optional<net.minecraft.world.item.trading.ItemCost> guishop$originalB();
}
