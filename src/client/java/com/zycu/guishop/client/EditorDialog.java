package com.zycu.guishop.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

final class EditorDialog extends WorkbenchScreen {
    private final Screen parent;
    private final String description, initial;
    private final Consumer<String> accepted;
    private EditBox input;
    EditorDialog(Screen parent, String title, String description, String initial, Consumer<String> accepted) {
        super(title); this.parent = parent; this.description = description; this.initial = initial; this.accepted = accepted;
    }
    @Override protected void init() {
        if (initial != null) {
            input = new WorkbenchField(font, 156, 196, 348, Component.literal("Name"));
            input.setMaxLength(80); input.setValue(initial); addRenderableWidget(input); setInitialFocus(input);
        }
        button("Cancel", 222, 238, 100, this::onClose);
        button(initial == null ? "Confirm" : "Create", 338, 238, 100, () -> {
            String value = input == null ? "" : input.getValue().trim();
            if (input != null && value.isBlank()) return;
            accepted.accept(value);
            if (minecraft.gui.screen() == this) minecraft.gui.setScreen(parent);
        });
    }
    @Override protected void paint(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        panel(graphics, 136, 118, 388, 156, WorkbenchTheme.SURFACE);
        text(graphics, title.getString(), 156, 136, WorkbenchTheme.MUTED);
        int line = 0;
        for (var value : font.split(Component.literal(description), 348)) {
            graphics.text(font, value, 156, 161 + line++ * 12, WorkbenchTheme.MUTED, false);
        }
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
