package com.zycu.guishop.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Rounded controls retain vanilla keyboard activation and narration. */
class WorkbenchButton extends Button {
    private final String label;
    WorkbenchButton(String label, int x, int y, int width, Runnable action) {
        super(x, y, width, 20, Component.literal(label), ignored -> action.run(), DEFAULT_NARRATION);
        this.label = label;
    }
    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean primary = label.equals("Apply"), danger = label.startsWith("Remove") || label.equals("Reset");
        boolean quiet = label.equals("Close") || label.equals("Reload") || label.equals("Home") || label.equals("Up");
        int fill = primary ? WorkbenchTheme.TEAL : quiet ? 0x181A272D : 0xEF34464F;
        int text = primary ? 0xFF142A28 : danger ? WorkbenchTheme.DANGER : WorkbenchTheme.TEXT;
        int edge = primary ? WorkbenchTheme.TEAL : danger ? 0x885F4246 : 0xFF465C65;
        if (!active) { fill = 0xB32A3940; text = 0xFF84999C; edge = 0x70465C65; }
        else if (isHoveredOrFocused()) { fill = primary ? 0xFF91E3D2 : 0xFF425962; edge = WorkbenchTheme.TEAL; }
        WorkbenchTheme.rounded(graphics, getX(), getY(), getWidth(), getHeight(), 5, edge);
        WorkbenchTheme.rounded(graphics, getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, 4, fill);
        var font = WorkbenchTheme.font();
        String caption = font.plainSubstrByWidth(getMessage().getString(), getWidth() - 10);
        graphics.text(font, caption, getX() + (getWidth() - font.width(caption)) / 2, getY() + 6, text, false);
    }
}
