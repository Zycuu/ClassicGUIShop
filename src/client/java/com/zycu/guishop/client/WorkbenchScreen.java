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
    protected WorkbenchScreen(String title) { super(net.minecraft.client.Minecraft.getInstance(), WorkbenchTheme.font(), Component.literal(title)); }
    protected int contentOffsetX() { return 0; }
    protected int canvasWidth() { return W; }
    protected void geometry() {
        scale = Math.min(1.5f, Math.min((width - 12f) / canvasWidth(), (height - 12f) / H));
        originX = (width - canvasWidth() * scale) / 2; originY = (height - H * scale) / 2;
    }
    protected int logicalX(double x) { return (int) ((x - originX) / scale - contentOffsetX()); }
    protected int logicalY(double y) { return (int) ((y - originY) / scale); }
    private MouseButtonEvent logical(MouseButtonEvent event) {
        return new MouseButtonEvent((event.x() - originX) / scale - contentOffsetX(), (event.y() - originY) / scale, event.buttonInfo());
    }
    @Override public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        var parent = modalParent(); if (parent != null) parent.extractRenderState(graphics, -100, -100, delta);
        geometry();
        graphics.fill(0, 0, width, height, 0x70202528);
        graphics.pose().pushMatrix(); graphics.pose().translate(originX + contentOffsetX() * scale, originY); graphics.pose().scale(scale, scale);
        int x = logicalX(mouseX), y = logicalY(mouseY);
        paint(graphics, x, y, delta);
        super.extractRenderState(graphics, x, y, delta);
        afterWidgets(graphics,x,y,delta);
        for(var child:children())if(child instanceof Button button&&button.isHovered()&&font.width(button.getMessage())>button.getWidth()-10)graphics.setTooltipForNextFrame(font,button.getMessage(),(int)(originX+(x+contentOffsetX())*scale),(int)(originY+y*scale));
        graphics.pose().popMatrix();
    }
    protected abstract void paint(GuiGraphicsExtractor graphics, int x, int y, float delta);
    protected Screen modalParent() { return null; }
    protected WorkbenchScroll scroller(){return null;}
    protected boolean submitSearch(){return false;}
    protected void rebuildKeepingFocus(){
        var focused=getFocused();String label=focused instanceof net.minecraft.client.gui.components.EditBox field?field.getMessage().getString():null;
        int cursor=focused instanceof net.minecraft.client.gui.components.EditBox field?field.getCursorPosition():0;
        rebuildWidgets();if(label!=null)for(var child:children())if(child instanceof net.minecraft.client.gui.components.EditBox field&&field.getMessage().getString().equals(label)){setFocused(field);field.setCursorPosition(Math.min(cursor,field.getValue().length()));break;}
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){
        if((event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_RETURN||event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER)&&getFocused() instanceof net.minecraft.client.gui.components.EditBox field&&field.getMessage().getString().startsWith("Search")&&submitSearch())return true;
        return super.keyPressed(event);
    }
    protected void afterWidgets(GuiGraphicsExtractor graphics, int x, int y, float delta) {}
    protected boolean click(double x, double y, int button) { return false; }
    protected boolean release(double x, double y, int button) { return false; }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        geometry(); var local = logical(event);
        return scroller()!=null&&scroller().click(local.x(),local.y(),local.button()) || click(local.x(), local.y(), local.button()) || super.mouseClicked(local, doubleClick);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        var local = logical(event);
        if(scroller()!=null&&scroller().dragging){scroller().dragging=false;return true;}
        return release(local.x(), local.y(), local.button()) || super.mouseReleased(local);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if(scroller()!=null&&scroller().dragging){scroller().drag(logicalY(event.y()));return true;}
        return super.mouseDragged(logical(event), dx / scale, dy / scale);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        geometry();if(scroller()!=null&&scroller().wheel(logicalX(x),logicalY(y),vertical))return true;
        return super.mouseScrolled(logicalX(x), logicalY(y), horizontal, vertical);
    }
    @Override public void mouseMoved(double x, double y) { super.mouseMoved(logicalX(x), logicalY(y)); }
    @Override public boolean isPauseScreen() { return false; }
    protected Button button(String text, int x, int y, int width, Runnable action) {
        return addRenderableWidget(new WorkbenchButton(text, x, y, width, action));
    }
    protected void text(GuiGraphicsExtractor graphics, String value, int x, int y, int color) {
        graphics.text(font, value, x, y, color, false);
    }
    protected void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        WorkbenchTheme.panel(graphics, x, y, width, height, color);
    }
}
