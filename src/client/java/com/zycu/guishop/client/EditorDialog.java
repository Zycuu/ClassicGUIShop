package com.zycu.guishop.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

final class EditorDialog extends WorkbenchScreen {
    private final Screen parent;
    private final String description, initial, action, inputLabel;
    private String error="";
    private final Consumer<String> accepted;
    private EditBox input;
    private int top,descriptionHeight,inputY,buttonsY,bottom;
    EditorDialog(Screen parent, String title, String description, String initial, Consumer<String> accepted) {
        this(parent,title,description,initial,actionFor(title),"Name",accepted);
    }
    EditorDialog(Screen parent,String title,String description,String initial,String action,String inputLabel,Consumer<String> accepted){super(title);this.parent=parent;this.description=description;this.initial=initial;this.action=action;this.inputLabel=inputLabel;this.accepted=accepted;}
    private static String actionFor(String title){String value=title.toLowerCase(java.util.Locale.ROOT);if(value.contains("remove category"))return "Remove category";if(value.contains("remove listing"))return "Remove item";if(value.contains("discard"))return "Discard changes";if(value.contains("close"))return "Close without saving";if(value.contains("reload")||value.contains("refresh server"))return "Reload shop";if(value.contains("import into"))return "Import items";if(value.contains("export"))return "Export preset";if(value.contains("import preset"))return "Import preset";if(value.contains("previous configuration"))return "Load backup";if(value.contains("replace"))return "Load into editor";if(value.contains("default")||value.contains("override"))return "Use defaults";return "Create category";}
    @Override protected Screen modalParent(){return parent;}
    @Override protected void init() {
        descriptionHeight=font.split(Component.literal(description),348).size()*12;
        int height=64+descriptionHeight+(initial==null?0:30);top=Math.max(16,(H-height)/2);inputY=top+43+descriptionHeight;buttonsY=inputY+(initial==null?0:32);bottom=buttonsY+50;
        if (initial != null) {
            input = new WorkbenchField(font, 156, inputY, 348, Component.literal(inputLabel));
            input.setMaxLength(256); input.setValue(initial); addRenderableWidget(input); setInitialFocus(input);
        }
        button("Cancel", 156, buttonsY, 126, this::onClose);
        button(action, 294, buttonsY, 210, () -> {
            String value = input == null ? "" : input.getValue().trim();
            if(input!=null&&value.isBlank()){error="Enter "+inputLabel.toLowerCase(java.util.Locale.ROOT)+".";return;}
            try{accepted.accept(value);}catch(IllegalArgumentException invalid){error=invalid.getMessage();return;}
            if (minecraft.gui.screen() == this) minecraft.gui.setScreen(parent);
        });
    }
    @Override protected void paint(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        panel(graphics, 136, top, 388, bottom-top, WorkbenchTheme.SURFACE);
        if(!error.isBlank())text(graphics,font.plainSubstrByWidth(error,348),156,buttonsY+28,WorkbenchTheme.DANGER);
        text(graphics, title.getString(), 156, top+18, WorkbenchTheme.MUTED);
        int line = 0;
        for (var value : font.split(Component.literal(description), 348)) {
            graphics.text(font, value, 156, top+43 + line++ * 12, WorkbenchTheme.MUTED, false);
        }
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
