package com.zycu.guishop.mixin;
import com.zycu.guishop.ShopModes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "die", at = @At("TAIL"))
    private void guishop$modeDeath(DamageSource source, CallbackInfo ci) { ShopModes.killed((LivingEntity)(Object)this); }
}
