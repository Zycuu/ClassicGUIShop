package com.zycu.guishop.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
/** 1.20.1 keeps creative scrollbar sprites in its original texture atlas. */
final class LegacyCreativeScroll {
    static void draw(GuiGraphics g,ResourceLocation sprite,int x,int y,int width,int height){g.blit(new ResourceLocation("textures/gui/container/creative_inventory/tabs.png"),x,y,width,height,sprite.getPath().endsWith("_disabled")?244:232,0,12,15,256,256);}
}
