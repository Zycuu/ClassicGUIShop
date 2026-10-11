package com.zycu.guishop.mixin;

import com.zycu.guishop.ProtectedMerchantOffer;
import com.zycu.guishop.TradeLoopProtection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;

@Mixin(MerchantOffer.class)
public abstract class MerchantOfferMixin implements ProtectedMerchantOffer {
    @Shadow @Final private ItemCost baseCostA;
    @Shadow @Final private Optional<ItemCost> costB;
    @Shadow private int demand;
    @Shadow private int specialPriceDiff;
    @Shadow @Final private float priceMultiplier;
    @Unique private MinecraftServer guishop$owner;
    @Override public void guishop$protection(MinecraftServer server){guishop$owner=server;}
    @Override public MinecraftServer guishop$owner(){return guishop$owner;}
    @Override public ItemCost guishop$originalA(){return baseCostA;}
    @Override public Optional<ItemCost> guishop$originalB(){return costB;}
    @Unique private TradeLoopProtection.Payment guishop$payment(){return TradeLoopProtection.payment((MerchantOffer)(Object)this);}
    @Unique private int guishop$amount(TradeLoopProtection.Payment p){int base=p.first().count();int extra=Math.max(0,Mth.floor((float)(base*demand)*priceMultiplier));return Mth.clamp(base+extra+specialPriceDiff,p.minimum(),p.first().itemStack().getMaxStackSize());}
    @Inject(method="getItemCostA",at=@At("RETURN"),cancellable=true)
    private void guishop$first(CallbackInfoReturnable<ItemCost> callback){var p=guishop$payment();if(p!=null)callback.setReturnValue(p.first());}
    @Inject(method="getBaseCostA",at=@At("RETURN"),cancellable=true)
    private void guishop$base(CallbackInfoReturnable<ItemStack> callback){var p=guishop$payment();if(p!=null)callback.setReturnValue(p.first().itemStack());}
    @Inject(method="getCostA",at=@At("RETURN"),cancellable=true)
    private void guishop$cost(CallbackInfoReturnable<ItemStack> callback){var p=guishop$payment();if(p!=null)callback.setReturnValue(p.first().itemStack().copyWithCount(guishop$amount(p)));}
    @Inject(method="getItemCostB",at=@At("RETURN"),cancellable=true)
    private void guishop$second(CallbackInfoReturnable<Optional<ItemCost>> callback){var p=guishop$payment();if(p!=null)callback.setReturnValue(p.second());}
    @Inject(method="getCostB",at=@At("RETURN"),cancellable=true)
    private void guishop$secondStack(CallbackInfoReturnable<ItemStack> callback){var p=guishop$payment();if(p!=null)callback.setReturnValue(p.second().map(ItemCost::itemStack).orElse(ItemStack.EMPTY));}
    @Inject(method="satisfiedBy",at=@At("HEAD"),cancellable=true)
    private void guishop$validate(ItemStack a,ItemStack b,CallbackInfoReturnable<Boolean> callback){var p=guishop$payment();if(p!=null)callback.setReturnValue(!p.unavailable()&&p.first().test(a)&&a.getCount()>=guishop$amount(p)&&(p.second().isPresent()?p.second().get().test(b)&&b.getCount()>=p.second().get().count():b.isEmpty()));}
    @Inject(method="isOutOfStock",at=@At("RETURN"),cancellable=true)
    private void guishop$capacity(CallbackInfoReturnable<Boolean> callback){var p=guishop$payment();if(p!=null&&p.unavailable())callback.setReturnValue(true);}
    @Inject(method="getSpecialPriceDiff",at=@At("RETURN"),cancellable=true)
    private void guishop$preview(CallbackInfoReturnable<Integer> callback){var p=guishop$payment();if(p!=null){int base=p.first().count();int extra=Math.max(0,Mth.floor((float)(base*demand)*priceMultiplier));callback.setReturnValue(Math.max(specialPriceDiff,p.minimum()-base-extra));}}
    @Inject(method="copy",at=@At("RETURN"))
    private void guishop$copy(CallbackInfoReturnable<MerchantOffer> callback){((ProtectedMerchantOffer)callback.getReturnValue()).guishop$protection(guishop$owner);}
}
