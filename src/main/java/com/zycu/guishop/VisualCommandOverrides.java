package com.zycu.guishop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class VisualCommandOverrides {
    private VisualCommandOverrides() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandNode<CommandSourceStack> existingAdmin = dispatcher.getRoot().getChild("adminshop");
        LiteralArgumentBuilder<CommandSourceStack> advanced = buildAdvancedAdminTree(existingAdmin);

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
            .then(advanced)
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAdvancedAdminTree(
        CommandNode<CommandSourceStack> existingAdmin
    ) {
        LiteralArgumentBuilder<CommandSourceStack> advanced = Commands.literal("advanced")
            .requires(source -> ShopPermissions.admin(source, "root"))
            .executes(AdminEditorCommands::advancedHelp);

        redirectExisting(advanced, existingAdmin, "item");
        redirectExisting(advanced, existingAdmin, "category");
        redirectExisting(advanced, existingAdmin, "enchant");
        redirectExisting(advanced, existingAdmin, "economy");
        redirectExisting(advanced, existingAdmin, "multiplier");

        advanced.then(Commands.literal("folder")
            .requires(source -> ShopPermissions.admin(source, "editor"))
            .then(Commands.literal("create")
                .then(Commands.argument("category", StringArgumentType.word())
                    .suggests(ShopSuggestions.CATEGORIES)
                    .then(Commands.argument("icon", StringArgumentType.word())
                        .suggests(ShopSuggestions.ITEM_IDS)
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                            .executes(AdminEditorCommands::createFolder))))));

        return advanced;
    }

    private static void redirectExisting(
        LiteralArgumentBuilder<CommandSourceStack> parent,
        CommandNode<CommandSourceStack> existingAdmin,
        String childName
    ) {
        if (existingAdmin == null) return;
        CommandNode<CommandSourceStack> target = existingAdmin.getChild(childName);
        if (target != null) parent.then(Commands.literal(childName).redirect(target));
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
        ShopMessages.admin(source, "/adminshop advanced folder create <category> <icon> <display name>", false);
        return 1;
    }
}
