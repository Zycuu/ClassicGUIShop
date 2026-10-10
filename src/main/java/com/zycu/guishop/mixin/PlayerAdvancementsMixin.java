package com.zycu.guishop.mixin;
import com.zycu.guishop.ShopModes;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.advancements.AdvancementHolder;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Shadow private ServerPlayer player;
    @Inject(method = "award", at = @At("RETURN"))
    private void guishop$modeAdvancement(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValue() && player != null) ShopModes.advancement(player, advancement);
    }
}
