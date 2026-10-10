package com.zycu.guishop.client;

import com.zycu.guishop.admin.TransactionJournal;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

final class JournalEntryScreen extends WorkbenchScreen {
    private final Screen parent;
    private final TransactionJournal.Entry entry;
    private int page;
    JournalEntryScreen(Screen parent, TransactionJournal.Entry entry) { super("Configuration change"); this.parent = parent; this.entry = entry; }
    @Override protected void init() {
        button("Back", 80, 322, 120, this::onClose);
        button("<", 210, 322, 36, () -> { page = Math.max(0, page - 1); });
        button(">", 252, 322, 36, () -> page++);
        if (entry.action().equals("CONFIG_APPLY") && entry.details().startsWith("Backup ")) {
            String backup = entry.details().substring(7).split(" ")[0];
            button("Load backup", 400, 322, 180, () -> minecraft.gui.setScreen(new EditorDialog(this,
                "Load previous configuration?", "This replaces your local draft. Review the restored draft before choosing Save changes.", null,
                ignored -> AdminShopClient.request("restore", "", backup))));
        }
    }
    @Override protected void paint(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        panel(graphics, 62, 36, 536, 322, WorkbenchTheme.SURFACE); text(graphics, "Configuration change", 80, 52, WorkbenchTheme.TEXT);
        var lines = new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
        for(String line:List.of("When: "+com.zycu.guishop.admin.JournalDates.full(entry.time(),java.time.ZoneId.systemDefault()),"Changed by: "+entry.player(),"Summary: "+TransactionJournal.activity(entry),"Backup: "+(entry.details().startsWith("Backup ")?entry.details().substring(7).split(" ")[0]:"Unavailable"),"Load backup puts the previous configuration into the editor. Review it, then Save changes.")) {
            lines.addAll(font.split(Component.literal(line), 500));
        }
        int pages = Math.max(1, (lines.size() + 13) / 14); page = Math.min(page, pages - 1);
        for (int row = 0; row < 14 && page * 14 + row < lines.size(); row++) graphics.text(font, lines.get(page * 14 + row), 80, 82 + row * 16, WorkbenchTheme.TEXT, false);
        for(var child:children())if(child instanceof net.minecraft.client.gui.components.Button button){if(button.getMessage().getString().equals("<"))button.active=page>0;else if(button.getMessage().getString().equals(">"))button.active=page+1<pages;}
        text(graphics, (page + 1) + "/" + pages, 300, 328, WorkbenchTheme.TEXT);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
