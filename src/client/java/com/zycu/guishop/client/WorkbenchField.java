package com.zycu.guishop.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Rounded background and six-pixel inset, shared by text drawing and caret input. */
final class WorkbenchField extends EditBox {
    WorkbenchField(Font font, int x, int y, int width, Component label) {
        super(font, x, y, width, 20, label);
        setBordered(false); setTextShadow(false);
        setTextColor(WorkbenchTheme.TEXT); setTextColorUneditable(WorkbenchTheme.MUTED);
    }
    @Override public int getInnerWidth() { return Math.max(1, getWidth() - 12); }
    @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        WorkbenchTheme.rounded(graphics, getX(), getY(), getWidth(), getHeight(), 4, isFocused() ? WorkbenchTheme.TEAL : WorkbenchTheme.BORDER);
        WorkbenchTheme.rounded(graphics, getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, 3, WorkbenchTheme.INPUT);
        graphics.pose().pushMatrix(); graphics.pose().translate(6, 6);
        super.extractWidgetRenderState(graphics, mouseX - 6, mouseY - 6, delta);
        graphics.pose().popMatrix();
    }
    private MouseButtonEvent inset(MouseButtonEvent event) {
        return new MouseButtonEvent(Math.max(getX() + 0.5, event.x() - 6), event.y(), event.buttonInfo());
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isMouseOver(event.x(), event.y())) return false;
        return super.mouseClicked(inset(event), doubleClick);
    }
    @Override protected void onDrag(MouseButtonEvent event, double dx, double dy) { super.onDrag(inset(event), dx, dy); }
}
