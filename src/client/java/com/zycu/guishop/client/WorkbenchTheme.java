package com.zycu.guishop.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** Shared editor presentation. Its font and colors never replace the player's vanilla UI. */
final class WorkbenchTheme {
    static final int TEXT = 0xFFE5EEEC, MUTED = 0xFFA2B6B5, TEAL = 0xFF71D8C2, GOLD = 0xFFD8B878;
    static final int SURFACE = 0xEB141F25, PANEL = 0xED1A272D, PREVIEW = 0xFF24323B;
    static final int BORDER = 0xFF3C515A, INPUT = 0xFF142027, SELECTED = 0xFF28484A, ROW = 0x441A272D;
    static final int DANGER = 0xFFEBA4A0;
    private static Font editorFont;
    static Font font() {
        if (editorFont == null) {
            var original = ((com.zycu.guishop.client.mixin.EditorFontAccess) Minecraft.getInstance().font).guishop$provider();
            var ui = new FontDescription.Resource(Identifier.fromNamespaceAndPath("guishop", "ui"));
            editorFont = new Font(new Font.Provider() {
                @Override public net.minecraft.client.gui.GlyphSource glyphs(FontDescription description) {
                    return original.glyphs(description.equals(FontDescription.DEFAULT) ? ui : description);
                }
                @Override public net.minecraft.client.gui.font.glyphs.EffectGlyph effect() { return original.effect(); }
            });
        }
        return editorFont;
    }
    static void rounded(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) return;
        int r = Math.min(radius, Math.min(width, height) / 2);
        if (r <= 0) { graphics.fill(x, y, x + width, y + height, color); return; }
        graphics.fill(x, y + r, x + width, y + height - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            int inset = (int) Math.ceil(r - Math.sqrt(r * r - dy * dy));
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            graphics.fill(x + inset, y + height - row - 1, x + width - inset, y + height - row, color);
        }
    }
    static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        rounded(graphics, x, y + 2, width, height, 6, 0x24000000);
        rounded(graphics, x, y, width, height, 6, 0x88445C63);
        rounded(graphics, x + 1, y + 1, width - 2, height - 2, 5, color);
    }
}
