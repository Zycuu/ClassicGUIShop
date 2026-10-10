package com.zycu.guishop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class ShopCommands {
    private ShopCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        RewardCommand.register(dispatcher);
        dispatcher.register(Commands.literal("adminshop").executes(context ->
            com.zycu.guishop.admin.AdminNetworking.openEditor(context.getSource())));
        dispatcher.register(Commands.literal("shop")
            .requires(source -> ShopPermissions.user(source, "guishop.command.shop"))
            .executes(context -> openShop(context, ShopGui.Mode.BUY))
            .then(Commands.literal("buy").executes(context -> openShop(context, ShopGui.Mode.BUY)))
            .then(Commands.literal("sell").executes(context -> openShop(context, ShopGui.Mode.SELL)))
            .then(Commands.literal("enchant").executes(ShopCommands::openEnchantments))
            .then(Commands.literal("balance")
                .requires(source -> ShopPermissions.user(source, "guishop.command.balance"))
                .executes(ShopCommands::balance))
            .then(Commands.literal("pay")
                .requires(source -> ShopPermissions.user(source, "guishop.command.pay"))
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests(ShopSuggestions.PLAYERS)
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .suggests(ShopSuggestions.PRICES)
                        .executes(ShopCommands::pay))))
        );

        dispatcher.register(Commands.literal("sellhand")
            .requires(source -> ShopPermissions.user(source, "guishop.command.sellhand"))
            .executes(context -> sellHand(context, ShopService.SELL_ALL, false))
            .then(Commands.literal("all").executes(context -> sellHand(context, ShopService.SELL_ALL, true)))
            .then(Commands.argument("amount", IntegerArgumentType.integer(1, 6400))
                .suggests(ShopSuggestions.AMOUNTS)
                .executes(context -> sellHand(context, IntegerArgumentType.getInteger(context, "amount"), false)))
        );

        dispatcher.register(Commands.literal("worth")
            .requires(source -> ShopPermissions.user(source, "guishop.command.worth"))
            .executes(context -> worthHeld(context, false))
            .then(Commands.literal("all").executes(context -> worthHeld(context, true)))
            .then(Commands.argument("item", StringArgumentType.string())
                .suggests(ShopSuggestions.ITEMS)
                .executes(context -> worthItem(context, 1))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 6400))
                    .suggests(ShopSuggestions.AMOUNTS)
                    .executes(context -> worthItem(context, IntegerArgumentType.getInteger(context, "amount")))))
        );

        dispatcher.register(Commands.literal("bal")
            .requires(source -> ShopPermissions.user(source, "guishop.command.balance"))
            .executes(ShopCommands::balance)
            .then(Commands.argument("player", StringArgumentType.word()).suggests(ShopSuggestions.PLAYERS).executes(ShopCommands::otherBalance)));
        dispatcher.register(Commands.literal("baltop").requires(source -> ShopPermissions.user(source, "guishop.command.balance")).executes(context -> {
            int rank = 0; for (var entry : PublicBalances.top(context.getSource().getServer())) { String message = (++rank) + ". " + entry.name() + " | " + GuiShop.CONFIG.money(entry.amount()); context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(message), false); } return 1;
        }));

    }

    private static int openShop(CommandContext<CommandSourceStack> context, ShopGui.Mode mode) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        remember(player);
        HybridShopGui.openCategories(player, mode);
        return 1;
    }

    private static int openEnchantments(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        remember(player);
        HybridShopGui.openEnchantments(player, ShopGui.Mode.BUY, 1);
        return 1;
    }

    private static int balance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        remember(player);
        ShopMessages.info(player, "Balance: " + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID())));
        return 1;
    }

    private static int otherBalance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var viewer = context.getSource().getPlayerOrException();
        var target = GuiShop.PLAYERS.resolve(context.getSource().getServer(), StringArgumentType.getString(context,"player"));
        if (target == null || !target.uuid().equals(viewer.getUUID()) && !PublicBalances.visible(context.getSource().getServer(),target.uuid())) { ShopMessages.warning(viewer,"That player's balance is unavailable."); return 0; }
        ShopMessages.info(viewer,target.name() + " | Balance: " + GuiShop.CONFIG.money(GuiShop.ECONOMY.readBalance(target.uuid()))); return 1;
    }
    private static int pay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer sender = context.getSource().getPlayerOrException();
        remember(sender);
        String targetInput = StringArgumentType.getString(context, "player");
        double amount = DoubleArgumentType.getDouble(context, "amount");
        PlayerDirectory.ResolvedPlayer target = GuiShop.PLAYERS.resolve(context.getSource().getServer(), targetInput);

        if (target == null) {
            ShopMessages.error(sender, "That player is unknown. They must have joined the server at least once.");
            return 0;
        }
        if (target.onlinePlayer() == null && !GuiShop.CONFIG.offlinePaymentsAllowed()) {
            ShopMessages.warning(sender, "Offline payments are disabled.");
            return 0;
        }
        if (target.uuid().equals(sender.getUUID())) {
            ShopMessages.warning(sender, "You cannot pay yourself.");
            return 0;
        }
        if (!GuiShop.ECONOMY.transfer(sender.getUUID(), target.uuid(), amount)) {
            ShopMessages.error(sender, "You cannot afford " + GuiShop.CONFIG.money(amount) + ".");
            return 0;
        }

        ShopMessages.success(sender, "Paid " + target.name() + " " + GuiShop.CONFIG.money(amount) + ".");
        com.zycu.guishop.admin.TransactionJournal.record(sender, "PAY", "", "", 0, 0, amount, "Recipient " + target.name() + " (" + target.uuid() + ")");
        if (target.onlinePlayer() != null) {
            ShopMessages.success(target.onlinePlayer(), sender.getName().getString() + " paid you " + GuiShop.CONFIG.money(amount) + ".");
        }
        return 1;
    }

    private static int sellHand(CommandContext<CommandSourceStack> context, int amount, boolean allInventory) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        remember(player);
        return ShopService.sellHand(player, amount, allInventory) ? 1 : 0;
    }

    private static int worthHeld(CommandContext<CommandSourceStack> context, boolean allInventory) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        remember(player);
        return ShopService.showWorth(player, allInventory) ? 1 : 0;
    }

    private static int worthItem(CommandContext<CommandSourceStack> context, int amount) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        remember(player);
        String item = StringArgumentType.getString(context, "item");
        return ShopService.showWorth(player, item, amount) ? 1 : 0;
    }

    private static void remember(ServerPlayer player) {
        GuiShop.PLAYERS.remember(player);
        GuiShop.ECONOMY.balance(player.getUUID());
    }
}
