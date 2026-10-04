package com.zycu.guishop.client;

import com.zycu.guishop.admin.AdminDocument;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

final class DestinationScreen extends WorkbenchScreen {
    private record Destination(String category, String folder, String label) {}
    private final Screen parent;
    private final BiConsumer<String, String> selected;
    private final List<Destination> destinations = new ArrayList<>();
    private int page;
    DestinationScreen(Screen parent, AdminDocument document, BiConsumer<String, String> selected) {
        super("Move listing"); this.parent = parent; this.selected = selected;
        for (var category : document.config.categories) {
            destinations.add(new Destination(category.id, "", category.name + " / Unsorted"));
            var layout = document.folders.get(category.id);
            if (layout != null) for (var folder : layout.folders) destinations.add(new Destination(category.id, folder.id, category.name + " / " + folder.name));
        }
    }
    @Override protected void init() {
        for (int row = 0; row < 10 && page * 10 + row < destinations.size(); row++) {
            var value = destinations.get(page * 10 + row);
            button(font.plainSubstrByWidth(value.label, 476), 80, 74 + row * 24, 500, () -> {
                selected.accept(value.category, value.folder); minecraft.gui.setScreen(parent);
            });
        }
        button("Previous", 80, 322, 110, () -> { page = Math.max(0, page - 1); rebuildWidgets(); });
        button("Next", 200, 322, 110, () -> { page = Math.min(Math.max(0, (destinations.size() - 1) / 10), page + 1); rebuildWidgets(); });
        button("Cancel", 470, 322, 110, this::onClose);
    }
    @Override protected void paint(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        panel(graphics, 62, 36, 536, 322, 0xFFD0CEC3); text(graphics, "Choose a destination", 80, 52, 0xFF214444);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
