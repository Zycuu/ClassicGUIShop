package com.zycu.guishop;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

public final class AdminShopDialogs {
    private static final int BUTTON_WIDTH = 180;

    private AdminShopDialogs() {}

    public static void open(ServerPlayer player) {
        MultiActionDialog dialog = new MultiActionDialog(
            common(),
            List.of(
                run("Shop Editor", "Browse and price listings", "/adminshop edit"),
                run("Import Scan", "Detect installed content", "/adminshop import scan"),
                suggest("Import Tools", "Open the import command options", "/adminshop import "),
                suggest("Economy Tools", "Open balance commands", "/adminshop advanced economy "),
                suggest("Enchantment Tools", "Open enchantment commands", "/adminshop advanced enchant "),
                suggest("Global Pricing", "Set the price multiplier", "/adminshop advanced multiplier "),
                run("Catalog Sync", "Synchronize generated listings", "/adminshop advanced catalog sync"),
                run("Reload", "Reload configuration and folders", "/adminshop reload")
            ),
            Optional.of(closeButton()),
            2
        );
        player.openDialog(Holder.direct(dialog));
    }

    private static CommonDialogData common() {
        return new CommonDialogData(
            Component.literal("ClassicGUIShop Admin"),
            Optional.of(Component.literal("ClassicGUIShop Admin")),
            true,
            false,
            DialogAction.CLOSE,
            List.of(new PlainMessage(
                Component.literal("Choose a tool. Buttons either perform a safe action or place the correct command template into chat."),
                420
            )),
            List.of()
        );
    }

    private static ActionButton run(String label, String tooltip, String command) {
        return action(label, tooltip, new ClickEvent.RunCommand(command));
    }

    private static ActionButton suggest(String label, String tooltip, String command) {
        return action(label, tooltip, new ClickEvent.SuggestCommand(command));
    }

    private static ActionButton action(String label, String tooltip, ClickEvent event) {
        return new ActionButton(
            new CommonButtonData(
                Component.literal(label),
                Optional.of(Component.literal(tooltip)),
                BUTTON_WIDTH
            ),
            Optional.of(new StaticAction(event))
        );
    }

    private static ActionButton closeButton() {
        return new ActionButton(
            new CommonButtonData(Component.literal("Close"), BUTTON_WIDTH),
            Optional.empty()
        );
    }
}
