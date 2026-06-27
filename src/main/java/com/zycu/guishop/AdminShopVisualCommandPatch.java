package com.zycu.guishop;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.lang.reflect.Field;

public final class AdminShopVisualCommandPatch {
    private AdminShopVisualCommandPatch() {}

    public static void apply(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandNode<CommandSourceStack> admin = dispatcher.getRoot().getChild("adminshop");
        if (admin == null) {
            System.err.println("[ClassicGUIShop] Could not find /adminshop after command registration.");
            return;
        }

        try {
            Field commandField = CommandNode.class.getDeclaredField("command");
            if (!commandField.trySetAccessible()) {
                throw new IllegalAccessException("Could not access Brigadier command field.");
            }
            Command<CommandSourceStack> openEditor = AdminEditorCommands::openEditor;
            commandField.set(admin, openEditor);

            admin.addChild(Commands.literal("edit")
                .requires(source -> ShopPermissions.admin(source, "editor"))
                .executes(AdminEditorCommands::openEditor)
                .build());

            admin.addChild(Commands.literal("help")
                .requires(source -> ShopPermissions.admin(source, "root"))
                .executes(AdminEditorCommands::advancedHelp)
                .build());

            System.out.println("[ClassicGUIShop] /adminshop opens the visual editor; existing admin subcommands preserved.");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            System.err.println("[ClassicGUIShop] Could not patch /adminshop to open the visual editor.");
            exception.printStackTrace();
        }
    }
}
