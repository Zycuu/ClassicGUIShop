package com.zycu.guishop;

import net.minecraft.server.MinecraftServer;

/** Transient protection metadata. Vanilla offer data and its save codec stay intact. */
public interface ProtectedMerchantOffer {
    void guishop$protection(MinecraftServer server, String definition, int minimum);
    MinecraftServer guishop$owner();
    String guishop$definition();
    int guishop$minimum();
}
