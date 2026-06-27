package com.zycu.guishop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class VisualCommandOverrides {
    private VisualCommandOverrides() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shop")
            .requires(source -> ShopPermissions.user(source, "guishop.command.shop"))
            .executes(context -> openShop(context, ShopGui.Mode.BUY))
            .then(Commands.literal("buy").executes(context -> openShop(context, ShopGui.Mode.BUY)))
            .then(Commands.literal("sell").executes(context -> openShop(context, ShopGui.Mode.SELL)))
            .then(Commands.literal("enchant").executes(VisualCommandOverrides::openEnchantments))
        );

        dispatcher.register(Commands.literal("adminshop")
            .requires(source -> ShopPermissions.admin(source, "root"))
            .executes(AdminEditorCommands::openEditor)
            .then(Commands.literal("edit")
                .requires(source -> ShopPermissions.admin(source, "editor"))
                .executes(AdminEditorCommands::openEditor))
            .then(Commands.literal("help").executes(VisualCommandOverrides::adminHelp))
            .then(Commands.literal("reload")
                .requires(source -> ShopPermissions.admin(source, "reload"))
                .executes(AdminEditorCommands::reload))
        );
    }

    private static int openShop(CommandContext<CommandSourceStack> context, ShopGui.Mode mode) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        GuiShop.PLAYERS.remember(player);
        GuiShop.ECONOMY.balance(player.getUUID());
        EnchantmentShopService.rememberMode(player, mode);
        HybridShopGui.openCategories(player, mode);
        return 1;
    }

    private static int openEnchantments(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        GuiShop.PLAYERS.remember(player);
        GuiShop.ECONOMY.balance(player.getUUID());
        EnchantmentShopService.rememberMode(player, ShopGui.Mode.BUY);
        HybridShopGui.openEnchantments(player, ShopGui.Mode.BUY, 1);
        return 1;
    }

    private static int adminHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ShopMessages.admin(source, "/adminshop | Open the visual shop editor", false);
        ShopMessages.admin(source, "/adminshop edit | Open the visual shop editor", false);
        ShopMessages.admin(source, "/adminshop reload | Reload configuration, folders, catalog, and versioned listings", false);
        ShopMessages.admin(source, "/adminshop item <add|remove|price|move|list>", false);
        ShopMessages.admin(source, "/adminshop category <add|remove|list>", false);
        ShopMessages.admin(source, "/adminshop enchant <set|remove|list|defaultprice|enabled>", false);
        ShopMessages.admin(source, "/adminshop economy <get|set|add|take>", false);
        ShopMessages.admin(source, "/adminshop import <scan|mod|namespace|datapack|held|price>", false);
        ShopMessages.admin(source, "/adminshop multiplier <value>", false);
        return 1;
    }
}
