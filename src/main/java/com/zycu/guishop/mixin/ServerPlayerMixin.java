package com.zycu.guishop.mixin;
import com.zycu.guishop.ShopModes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Unique private net.minecraft.world.entity.LivingEntity guishop$deathCredit;
    @Inject(method = "die", at = @At("HEAD"))
    private void guishop$captureDeathCredit(DamageSource source, CallbackInfo ci) { guishop$deathCredit = ((ServerPlayer)(Object)this).getKillCredit(); }
    @Inject(method = "die", at = @At("TAIL"))
    private void guishop$modeDeath(DamageSource source, CallbackInfo ci) { ShopModes.killed((ServerPlayer)(Object)this, guishop$deathCredit); guishop$deathCredit = null; ShopModes.died((ServerPlayer)(Object)this,source); }
}
