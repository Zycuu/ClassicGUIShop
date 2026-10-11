package com.zycu.guishop;

import net.minecraft.world.item.trading.MerchantOffers;

/** Access existing offers without prematurely generating new villager trades. */
public interface VillagerOfferAccess {
    MerchantOffers guishop$loadedOffers();
}
