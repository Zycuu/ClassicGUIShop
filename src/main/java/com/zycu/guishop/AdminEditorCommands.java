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
            .executes(AdminEditorCommands::adminHelp)
            .then(Commands.literal("edit")
                .requires(source -> ShopPermissions.admin(source, "editor"))
                .executes(AdminEditorCommands::openEditor))
            .then(Commands.literal("gui-edit")
                .requires(source -> ShopPermissions.admin(source, "editor"))
                .executes(AdminEditorCommands::openEditor))
            .then(Commands.literal("editor")
                .requires(source -> ShopPermissions.admin(source, "editor"))
                .executes(AdminEditorCommands::openEditor))
            .then(Commands.literal("reload")
                .requires(source -> ShopPermissions.admin(source, "reload"))
                .executes(AdminEditorCommands::reload))
        );

        // Registered after the original shop tree. Brigadier merges duplicate literal nodes
        // and replaces the executable handlers without removing existing child commands.
        dispatcher.register(Commands.literal("shop")
            .requires(source -> ShopPermissions.user(source, "guishop.command.shop"))
            .executes(context -> openShop(context, ShopGui.Mode.BUY))
            .then(Commands.literal("buy").executes(context -> openShop(context, ShopGui.Mode.BUY)))
            .then(Commands.literal("sell").executes(context -> openShop(context, ShopGui.Mode.SELL)))
            .then(Commands.literal("enchant").executes(AdminEditorCommands::openEnchantments))
        );
    }

    private static int adminHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ShopMessages.admin(source, "/adminshop edit | Open the visual shop editor", false);
        ShopMessages.admin(source, "/adminshop item <add|remove|price|move|list>", false);
        ShopMessages.admin(source, "/adminshop category <add|remove|list>", false);
        ShopMessages.admin(source, "/adminshop enchant <set|remove|list|defaultprice|enabled>", false);
        ShopMessages.admin(source, "/adminshop economy <get|set|add|take>", false);
        ShopMessages.admin(source, "/adminshop import <scan|mod|datapack|held|resourcepack|preview|price>", false);
        ShopMessages.admin(source, "/adminshop catalog sync | /adminshop multiplier <value> | /adminshop reload", false);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        GuiShop.CONFIG = ShopConfig.load();
        GuiShop.CONFIG.permissionDefaults.putIfAbsent("guishop.command.ident", 0);
        GuiShop.CONFIG.permissionDefaults.putIfAbsent("guishop.admin.import", 2);
        GuiShop.CONFIG.permissionDefaults.putIfAbsent("guishop.admin.editor", 2);
        GuiShop.CONFIG.ensureEnchantmentDefaults(context.getSource().getServer());
        VanillaCatalog.SyncResult result = VanillaCatalog.sync(GuiShop.CONFIG, context.getSource().getServer());
        GuiShop.ECONOMY.updateConfig(GuiShop.CONFIG);
        GuiShop.FOLDERS = SafeShopFolderLoader.load();
        GuiShop.FOLDERS.sync(GuiShop.CONFIG);
        ShopMessages.admin(context.getSource(), "Configuration and folders reloaded. Catalog added "
            + result.added() + ", removed " + result.removed() + ", repriced " + result.repriced() + ".", true);
        return 1;
    }

    private static int openEditor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        AdminShopEditorGuiV2.open(player);
        return 1;
    }

    private static int openShop(CommandContext<CommandSourceStack> context, ShopGui.Mode mode) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CursorStableShopGui.openCategories(player, mode);
        return 1;
    }

    private static int openEnchantments(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CursorStableShopGui.openEnchantments(player, 1);
        return 1;
    }
}
