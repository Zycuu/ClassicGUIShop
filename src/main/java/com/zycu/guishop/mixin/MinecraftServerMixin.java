package com.zycu.guishop.mixin;

import com.zycu.guishop.GuiShop;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "tickServer", at = @At("TAIL"))
    private void guishop$afterServerTick(BooleanSupplier timeLeft, CallbackInfo ci) {
        GuiShop.onServerTick((MinecraftServer) (Object) this);
    }
}
