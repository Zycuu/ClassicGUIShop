package com.zycu.guishop.mixin;

import com.zycu.guishop.TradeLoopProtection;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerMixin implements com.zycu.guishop.VillagerOfferAccess {
    @Shadow protected MerchantOffers offers;
    @Override public MerchantOffers guishop$loadedOffers(){return offers;}
    @Inject(method="getOffers",at=@At("RETURN"))
    private void guishop$offers(CallbackInfoReturnable<MerchantOffers> callback){TradeLoopProtection.prepare((AbstractVillager)(Object)this,callback.getReturnValue());}
    @Inject(method="readAdditionalSaveData",at=@At("TAIL"))
    private void guishop$savedOffers(ValueInput input,CallbackInfo callback){TradeLoopProtection.prepare((AbstractVillager)(Object)this,offers);}
}
