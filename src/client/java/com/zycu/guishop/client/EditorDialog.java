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
    private int top,descriptionHeight,inputY,buttonsY,bottom;
    EditorDialog(Screen parent, String title, String description, String initial, Consumer<String> accepted) {
        super(title); this.parent = parent; this.description = description; this.initial = initial; this.accepted = accepted;
    }
    @Override protected void init() {
        descriptionHeight=font.split(Component.literal(description),348).size()*12;
        int height=64+descriptionHeight+(initial==null?0:30);top=Math.max(16,(H-height)/2);inputY=top+43+descriptionHeight;buttonsY=inputY+(initial==null?0:32);bottom=buttonsY+36;
        if (initial != null) {
            input = new WorkbenchField(font, 156, inputY, 348, Component.literal("Name"));
            input.setMaxLength(256); input.setValue(initial); addRenderableWidget(input); setInitialFocus(input);
        }
        button("Cancel", 222, buttonsY, 100, this::onClose);
        button(initial == null ? "Confirm" : "Create", 338, buttonsY, 100, () -> {
            String value = input == null ? "" : input.getValue().trim();
            if (input != null && value.isBlank()) return;
            accepted.accept(value);
            if (minecraft.gui.screen() == this) minecraft.gui.setScreen(parent);
        });
    }
    @Override protected void paint(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        panel(graphics, 136, top, 388, bottom-top, WorkbenchTheme.SURFACE);
        text(graphics, title.getString(), 156, top+18, WorkbenchTheme.MUTED);
        int line = 0;
        for (var value : font.split(Component.literal(description), 348)) {
            graphics.text(font, value, 156, top+43 + line++ * 12, WorkbenchTheme.MUTED, false);
        }
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
