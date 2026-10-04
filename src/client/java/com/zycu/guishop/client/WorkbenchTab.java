package com.zycu.guishop.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Raised pixel tabs with native item icons, keyboard activation, and button narration. */
final class WorkbenchTab extends Button {
    private final boolean selected;
    private final Item icon;
    WorkbenchTab(String label, int x, int width, Item icon, boolean selected, Runnable action) {
        super(x, selected ? 40 : 43, width, selected ? 29 : 24, Component.literal(label), ignored -> action.run(), DEFAULT_NARRATION);
        this.icon = icon; this.selected = selected;
    }
    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int color = selected ? 0xFFD0CEC3 : isHoveredOrFocused() ? 0xFFBAC7B8 : 0xFF9FAE9F;
        graphics.fill(x, y, x + w, y + h, 0xFF293D40);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - (selected ? 0 : 1), color);
        graphics.fill(x + 2, y + 1, x + w - 2, y + 3, 0xFFE5E8D9);
        if (selected) graphics.fill(x + 1, y + h - 3, x + w - 1, y + h, 0xFF80B6A3);
        graphics.item(new ItemStack(icon), x + 5, y + 4);
        var font = Minecraft.getInstance().font;
        graphics.text(font, font.plainSubstrByWidth(getMessage().getString(), w - 28), x + 25, y + 8, 0xFF203B35, false);
    }
}
