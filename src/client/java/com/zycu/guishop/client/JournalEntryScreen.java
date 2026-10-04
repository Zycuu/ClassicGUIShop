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
    JournalEntryScreen(Screen parent, TransactionJournal.Entry entry) { super("Server record"); this.parent = parent; this.entry = entry; }
    @Override protected void init() {
        button("Back", 80, 322, 120, this::onClose);
        button("<", 210, 322, 36, () -> { page = Math.max(0, page - 1); });
        button(">", 252, 322, 36, () -> page++);
        if (entry.action().equals("CONFIG_APPLY") && entry.details().startsWith("Backup ")) {
            String backup = entry.details().substring(7).split(" ")[0];
            button("Restore draft...", 400, 322, 180, () -> minecraft.gui.setScreen(new EditorDialog(this,
                "Load previous configuration?", "This replaces your local draft. Review the restored draft before applying it to the server.", null,
                ignored -> AdminShopClient.request("restore", "", backup))));
        }
    }
    @Override protected void paint(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        panel(graphics, 62, 36, 536, 322, 0xFFD0CEC3); text(graphics, "Server record", 80, 52, 0xFF214444);
        var lines = new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (String line : List.of("Time (UTC): " + entry.time(), "Player: " + entry.player(), "UUID: " + entry.uuid(), "Action: " + entry.action(),
            "Listing: " + entry.listing(), "Item: " + entry.item(), "Quantity: " + entry.quantity(), "Unit price: " + entry.unitPrice(),
            "Total: " + entry.total(), "Balance after: " + entry.balance(), "Details: " + entry.details())) {
            lines.addAll(font.split(Component.literal(line), 500));
        }
        int pages = Math.max(1, (lines.size() + 13) / 14); page = Math.min(page, pages - 1);
        for (int row = 0; row < 14 && page * 14 + row < lines.size(); row++) graphics.text(font, lines.get(page * 14 + row), 80, 82 + row * 16, 0xFF303E36, false);
        text(graphics, (page + 1) + "/" + pages, 300, 328, 0xFF303E36);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
