package com.zycu.guishop.mixin;

import com.zycu.guishop.ProtectedMerchantOffer;
import com.zycu.guishop.TradeLoopProtection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MerchantOffer.class)
public abstract class MerchantOfferMixin implements ProtectedMerchantOffer {
    @Shadow private int demand;
    @Shadow private int specialPriceDiff;
    @Shadow @Final private float priceMultiplier;
    @Shadow public abstract net.minecraft.world.item.ItemStack getBaseCostA();
    @Unique private MinecraftServer guishop$owner;
    @Unique private String guishop$definition;
    @Unique private int guishop$minimum;
    @Override public void guishop$protection(MinecraftServer server,String definition,int minimum){guishop$owner=server;guishop$definition=definition;guishop$minimum=minimum;}
    @Override public MinecraftServer guishop$owner(){return guishop$owner;}
    @Override public String guishop$definition(){return guishop$definition;}
    @Override public int guishop$minimum(){return guishop$minimum;}
    @Inject(method="getModifiedCostCount",at=@At("RETURN"),cancellable=true)
    private void guishop$minimum(ItemCost cost,CallbackInfoReturnable<Integer> callback){
        int minimum=TradeLoopProtection.floor((MerchantOffer)(Object)this);
        if(minimum>callback.getReturnValue())callback.setReturnValue(Math.min(minimum,cost.itemStack().getMaxStackSize()));
    }
    @Inject(method="getSpecialPriceDiff",at=@At("RETURN"),cancellable=true)
    private void guishop$vanillaPreview(CallbackInfoReturnable<Integer> callback){
        int minimum=TradeLoopProtection.floor((MerchantOffer)(Object)this);if(minimum==0)return;
        int base=getBaseCostA().getCount();int surcharge=Math.max(0,Mth.floor((float)(base*demand)*priceMultiplier));
        callback.setReturnValue(Math.max(specialPriceDiff,minimum-base-surcharge));
    }
    @Inject(method="copy",at=@At("RETURN"))
    private void guishop$copy(CallbackInfoReturnable<MerchantOffer> callback){
        ((ProtectedMerchantOffer)callback.getReturnValue()).guishop$protection(guishop$owner,guishop$definition,guishop$minimum);
    }
}
