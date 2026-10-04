package com.zycu.guishop.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** Permission denial is deliberately a separate screen, never an editor with disabled controls. */
public final class AdminNoticeScreen extends WorkbenchScreen {
    private final String message;
    private final Screen previous;
    public AdminNoticeScreen(String message, Screen previous) { super("Adminshop"); this.message = message; this.previous = previous; }
    @Override protected void init() {
        button("OK", 280, 236, 100, this::onClose);
    }
    @Override protected void paint(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        panel(graphics, 120, 125, 420, 140, 0xFFD0CEC3);
        text(graphics, "Adminshop", 140, 143, 0xFF263D3D);
        int row = 0;
        for (var line : font.split(net.minecraft.network.chat.Component.literal(message), 380)) {
            graphics.text(font, line, 140, 178 + row++ * 12, 0xFF303535, false);
        }
    }
    @Override public void onClose() { minecraft.gui.setScreen(previous); }
}
