package com.zycu.guishop.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Compact navigation rail. Category and listing icons are rendered from their native stacks. */
final class WorkbenchTab extends Button {
    private final boolean selected;
    private final Item icon;
    WorkbenchTab(String label, int index, Item icon, boolean selected, Runnable action) {
        super(-34, 76 + index * 38, 30, 30, Component.literal(label), ignored -> action.run(), DEFAULT_NARRATION);
        this.icon = icon; this.selected = selected;
    }
    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int color = selected ? WorkbenchTheme.SELECTED : isHoveredOrFocused() ? 0xFF344C54 : 0x201A272D;
        WorkbenchTheme.rounded(graphics, x, y, w, h, 5, isFocused() ? WorkbenchTheme.TEAL : color);
        if (isFocused()) WorkbenchTheme.rounded(graphics, x + 1, y + 1, w - 2, h - 2, 4, color);
        graphics.item(new ItemStack(icon), x + (w - 16) / 2, y + (h - 16) / 2);
        if (selected) WorkbenchTheme.rounded(graphics, x + 5, y + h - 2, w - 10, 2, 1, WorkbenchTheme.TEAL);
    }
}
