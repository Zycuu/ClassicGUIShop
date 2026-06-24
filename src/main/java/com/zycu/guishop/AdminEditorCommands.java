package com.zycu.guishop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class AdminEditorCommands {
    private AdminEditorCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("adminshop")
            .requires(source -> ShopPermissions.admin(source, "root"))
            .then(Commands.literal("edit")
                .requires(source -> ShopPermissions.admin(source, "editor"))
                .executes(AdminEditorCommands::openEditor))
            .then(Commands.literal("gui-edit")
                .requires(source -> ShopPermissions.admin(source, "editor"))
                .executes(AdminEditorCommands::openEditor))
            .then(Commands.literal("editor")
                .requires(source -> ShopPermissions.admin(source, "editor"))
                .executes(AdminEditorCommands::openEditor))
        );

        // Registered after the original shop tree. Brigadier merges duplicate literal nodes
        // and replaces the executable handlers, giving the player shop folder support without
        // breaking existing subcommands such as /shop pay and /shop balance.
        dispatcher.register(Commands.literal("shop")
            .requires(source -> ShopPermissions.user(source, "guishop.command.shop"))
            .executes(context -> openShop(context, ShopGui.Mode.BUY))
            .then(Commands.literal("buy").executes(context -> openShop(context, ShopGui.Mode.BUY)))
            .then(Commands.literal("sell").executes(context -> openShop(context, ShopGui.Mode.SELL)))
            .then(Commands.literal("enchant").executes(AdminEditorCommands::openEnchantments))
        );
    }

    private static int openEditor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        AdminShopEditorGui.open(player);
        return 1;
    }

    private static int openShop(CommandContext<CommandSourceStack> context, ShopGui.Mode mode) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        FolderAwareShopGui.openCategories(player, mode);
        return 1;
    }

    private static int openEnchantments(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        FolderAwareShopGui.openEnchantments(player, 1);
        return 1;
    }
}
