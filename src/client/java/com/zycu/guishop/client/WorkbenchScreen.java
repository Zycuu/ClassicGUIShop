package com.zycu.guishop.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** One logical canvas keeps controls and pointer hitboxes aligned at every GUI scale. */
public abstract class WorkbenchScreen extends Screen {
    protected static final int W = 660, H = 386;
    protected float scale = 1, originX, originY;
    protected WorkbenchScreen(String title) { super(Component.literal(title)); }
    protected void geometry() {
        scale = Math.min(1.5f, Math.min((width - 12f) / W, (height - 12f) / H));
        originX = (width - W * scale) / 2; originY = (height - H * scale) / 2;
    }
    protected int logicalX(double x) { return (int) ((x - originX) / scale); }
    protected int logicalY(double y) { return (int) ((y - originY) / scale); }
    private MouseButtonEvent logical(MouseButtonEvent event) {
        return new MouseButtonEvent((event.x() - originX) / scale, (event.y() - originY) / scale, event.buttonInfo());
    }
    @Override public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        geometry();
        graphics.fill(0, 0, width, height, 0xC0202528);
        graphics.pose().pushMatrix(); graphics.pose().translate(originX, originY); graphics.pose().scale(scale, scale);
        int x = logicalX(mouseX), y = logicalY(mouseY);
        paint(graphics, x, y, delta);
        super.extractRenderState(graphics, x, y, delta);
        afterWidgets(graphics, x, y, delta);
        graphics.pose().popMatrix();
    }
    protected abstract void paint(GuiGraphicsExtractor graphics, int x, int y, float delta);
    protected void afterWidgets(GuiGraphicsExtractor graphics, int x, int y, float delta) {}
    protected boolean click(double x, double y, int button) { return false; }
    protected boolean release(double x, double y, int button) { return false; }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        geometry(); var local = logical(event);
        return click(local.x(), local.y(), local.button()) || super.mouseClicked(local, doubleClick);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        var local = logical(event);
        return release(local.x(), local.y(), local.button()) || super.mouseReleased(local);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return super.mouseDragged(logical(event), dx / scale, dy / scale);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return super.mouseScrolled(logicalX(x), logicalY(y), horizontal, vertical);
    }
    @Override public void mouseMoved(double x, double y) { super.mouseMoved(logicalX(x), logicalY(y)); }
    @Override public boolean isPauseScreen() { return false; }
    protected Button button(String text, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(text), ignored -> action.run()).bounds(x, y, width, 20).build());
    }
    protected void text(GuiGraphicsExtractor graphics, String value, int x, int y, int color) {
        graphics.text(font, value, x, y, color, false);
    }
    protected void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, 0xFF1A2327);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, color);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFFDFE4D8);
        graphics.fill(x + 1, y + 1, x + 2, y + height - 1, 0xFFDFE4D8);
    }
}
