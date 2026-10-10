package com.zycu.guishop.client;

import com.zycu.guishop.admin.AdminPlayers;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Immediate server operations are explicitly confirmed outside the shop configuration draft. */
final class AccountDialog extends WorkbenchScreen {
    private final AdminScreen parent;
    private final AdminPlayers.Player player;
    private final boolean reset;
    private final double defaultBalance;
    private final String currency;
    private EditBox input;
    private String error = "";
    AccountDialog(AdminScreen parent, AdminPlayers.Player player, boolean reset, double defaultBalance, String currency) {
        super(reset ? "Reset player account" : "Set player balance");
        this.parent = parent; this.player = player; this.reset = reset; this.defaultBalance = defaultBalance; this.currency = currency;
    }
    @Override protected int contentOffsetX() { return 48; }
    @Override protected int canvasWidth() { return W + 48; }
    @Override protected Screen modalParent() { return parent; }
    @Override protected void init() {
        if (!reset) {
            input = new WorkbenchField(font, 144, 214, 372, Component.literal("New balance"));
            input.setMaxLength(24); input.setValue(Double.toString(player.balance())); addRenderableWidget(input); setInitialFocus(input);
        }
        button("Cancel", 222, 270, 100, this::onClose);
        button(reset ? "Reset" : "Set balance", 338, 270, 100, () -> {
            double value = defaultBalance;
            if (!reset) try {
                value = Double.parseDouble(input.getValue());
                if (!Double.isFinite(value) || value < 0 || value > 1_000_000_000_000.0) throw new NumberFormatException();
            } catch (NumberFormatException invalid) { error = "Enter a finite, non-negative balance up to 1 trillion."; return; }
            if (parent.submitAccount(player, value, reset, defaultBalance)) minecraft.gui.setScreen(parent);
            else error = "Wait for the current request, then try again.";
        });
    }
    @Override protected void paint(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        panel(graphics, 124, 108, 412, 204, WorkbenchTheme.SURFACE);
        text(graphics, title.getString(), 144, 125, WorkbenchTheme.TEXT);
        text(graphics, font.plainSubstrByWidth(player.name() + " | Current balance: " + parent.accountMoney(player.balance()), 372), 144, 149, WorkbenchTheme.MUTED);
        String message = reset ? "THIS WILL RESET " + player.name() + "'s BALANCE TO " + parent.accountMoney(defaultBalance) + " AND CLEAR THEIR TRANSACTION HISTORY." : "New balance (" + currency + "). This applies immediately on the server.";
        int line = 0;
        for (var text : font.split(Component.literal(message), 372)) graphics.text(font, text, 144, 171 + line++ * 12, reset ? WorkbenchTheme.DANGER : WorkbenchTheme.MUTED, false);
        if (!error.isBlank()) text(graphics, font.plainSubstrByWidth(error, 372), 144, 248, WorkbenchTheme.DANGER);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
