package com.zycu.guishop.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Explicit selection only. Browsing the registry does not add anything to the shop. */
final class IconPickerScreen extends WorkbenchScreen {
    private final Screen parent;
    private final Consumer<String> selected;
    private List<String> values = List.of();
    private int page;
    private EditBox search;
    IconPickerScreen(Screen parent, String title, Consumer<String> selected) {
        super(title); this.parent = parent; this.selected = selected;
    }
    @Override protected void init() {
        search = new EditBox(font, 80, 74, 500, 20, Component.literal("Search items"));
        search.setHint(Component.literal("Search item name or ID...")); search.setMaxLength(100);
        search.setResponder(this::filter); addRenderableWidget(search); filter(""); setInitialFocus(search);
        button("Previous", 80, 314, 100, () -> page = Math.max(0, page - 1));
        button("Next", 190, 314, 100, () -> page = Math.min(Math.max(0, (values.size() - 1) / 96), page + 1));
        button("Cancel", 480, 314, 100, this::onClose);
    }
    private void filter(String input) {
        String query = input.toLowerCase(Locale.ROOT);
        values = BuiltInRegistries.ITEM.keySet().stream().filter(id -> {
            var item = BuiltInRegistries.ITEM.getValue(id);
            return item != Items.AIR && (id.toString().contains(query) || new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT).contains(query));
        }).map(Object::toString).sorted().toList(); page = 0;
    }
    @Override protected void paint(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        panel(graphics, 62, 36, 536, 314, 0xFFD0CEC3);
        text(graphics, title.getString(), 80, 52, 0xFF214444);
        for (int slot = 0; slot < 96; slot++) {
            int x = 82 + slot % 16 * 30, y = 110 + slot / 16 * 30;
            panel(graphics, x, y, 24, 24, 0xFF858A84);
            int index = page * 96 + slot;
            if (index >= values.size()) continue;
            var item = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(values.get(index)));
            graphics.item(new ItemStack(item), x + 4, y + 4);
            if (mouseX >= x && mouseX < x + 24 && mouseY >= y && mouseY < y + 24) {
                graphics.outline(x, y, 24, 24, 0xFF67DDD0);
                text(graphics, font.plainSubstrByWidth(new ItemStack(item).getHoverName().getString() + " | " + values.get(index), 500), 80, 295, 0xFF303535);
            }
        }
        text(graphics, "Page " + (page + 1) + "/" + Math.max(1, (values.size() + 95) / 96), 320, 320, 0xFF303535);
    }
    @Override protected boolean click(double x, double y, int button) {
        if (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return false;
        for (int slot = 0; slot < 96; slot++) {
            int sx = 82 + slot % 16 * 30, sy = 110 + slot / 16 * 30;
            int index = page * 96 + slot;
            if (x >= sx && x < sx + 24 && y >= sy && y < sy + 24 && index < values.size()) {
                selected.accept(values.get(index)); minecraft.gui.setScreen(parent); return true;
            }
        }
        return false;
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
