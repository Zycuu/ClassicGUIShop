package com.zycu.guishop.mixin;

import com.zycu.guishop.TradeLoopProtection;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerTrade.class)
public abstract class VillagerTradeMixin {
    @Inject(method="getOffer",at=@At("RETURN"))
    private void guishop$offer(LootContext context,CallbackInfoReturnable<MerchantOffer> callback){TradeLoopProtection.generated((VillagerTrade)(Object)this,context,callback.getReturnValue());}
}
