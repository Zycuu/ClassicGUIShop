package com.zycu.guishop.client.mixin;

import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the existing font provider so only editor Font instances select guishop:ui. */
@Mixin(Font.class)
public interface EditorFontAccess {
    @Accessor("provider") Font.Provider guishop$provider();
}
