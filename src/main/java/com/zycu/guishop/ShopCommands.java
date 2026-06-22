package com.zycu.guishop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class ShopCommands {
    private static final int LIST_PAGE_SIZE = 10;

    private ShopCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shop")
            .requires(source -> ShopPermissions.user(source, "guishop.command.shop"))
            .executes(context -> openShop(context, ShopGui.Mode.BUY))
            .then(Commands.literal("buy").executes(context -> openShop(context, ShopGui.Mode.BUY)))
            .then(Commands.literal("sell").executes(context -> openShop(context, ShopGui.Mode.SELL)))
        );

        dispatcher.register(Commands.literal("balance")
            .requires(source -> ShopPermissions.user(source, "guishop.command.balance"))
            .executes(ShopCommands::balance)
        );

        dispatcher.register(Commands.literal("pay")
            .requires(source -> ShopPermissions.user(source, "guishop.command.pay"))
            .then(Commands.argument("player", StringArgumentType.word())
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                    .executes(ShopCommands::pay)))
        );

        dispatcher.register(Commands.literal("sellhand")
            .requires(source -> ShopPermissions.user(source, "guishop.command.sellhand"))
            .executes(context -> sellHand(context, ShopService.SELL_ALL, false))
            .then(Commands.literal("all").executes(context -> sellHand(context, ShopService.SELL_ALL, true)))
            .then(Commands.argument("amount", IntegerArgumentType.integer(1, 6400))
                .executes(context -> sellHand(context, IntegerArgumentType.getInteger(context, "amount"), false)))
        );

        dispatcher.register(Commands.literal("worth")
            .requires(source -> ShopPermissions.user(source, "guishop.command.worth"))
            .executes(context -> worthHeld(context, false))
            .then(Commands.literal("all").executes(context -> worthHeld(context, true)))
            .then(Commands.argument("item", StringArgumentType.word())
                .executes(context -> worthItem(context, 1))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 6400))
                    .executes(context -> worthItem(context, IntegerArgumentType.getInteger(context, "amount")))))
        );

        dispatcher.register(Commands.literal("addopitem")
            .requires(source -> ShopPermissions.admin(source, "item.add"))
            .then(Commands.argument("category", StringArgumentType.word())
                .then(Commands.argument("buy", DoubleArgumentType.doubleArg(0))
                    .then(Commands.argument("sell", DoubleArgumentType.doubleArg(0))
                        .executes(ShopCommands::addHeldItem))))
        );

        dispatcher.register(Commands.literal("removeopitem")
            .requires(source -> ShopPermissions.admin(source, "item.remove"))
            .executes(ShopCommands::removeHeldItem)
        );

        dispatcher.register(Commands.literal("opitemlist")
            .requires(source -> ShopPermissions.admin(source, "item.list"))
            .executes(ShopCommands::listCategories)
            .then(Commands.argument("category", StringArgumentType.word())
                .executes(context -> listItems(context, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(context -> listItems(context, IntegerArgumentType.getInteger(context, "page")))))
        );

        dispatcher.register(Commands.literal("shopadmin")
            .requires(source -> ShopPermissions.admin(source, "root"))
            .then(Commands.literal("reload")
                .requires(source -> ShopPermissions.admin(source, "reload"))
                .executes(ShopCommands::reload))
            .then(Commands.literal("multiplier")
                .requires(source -> ShopPermissions.admin(source, "multiplier"))
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                    .executes(ShopCommands::setMultiplier)))
            .then(Commands.literal("setprice")
                .requires(source -> ShopPermissions.admin(source, "item.price"))
                .then(Commands.argument("item", StringArgumentType.word())
                    .then(Commands.argument("buy", DoubleArgumentType.doubleArg(0))
                        .then(Commands.argument("sell", DoubleArgumentType.doubleArg(0))
                            .executes(ShopCommands::setPrice)))))
            .then(Commands.literal("setcategory")
                .requires(source -> ShopPermissions.admin(source, "item.category"))
                .then(Commands.argument("item", StringArgumentType.word())
                    .then(Commands.argument("category", StringArgumentType.word())
                        .executes(ShopCommands::setCategory))))
            .then(Commands.literal("addcategory")
                .requires(source -> ShopPermissions.admin(source, "category"))
                .then(Commands.argument("id", StringArgumentType.word())
                    .then(Commands.argument("icon", StringArgumentType.word())
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                            .executes(ShopCommands::addCategory)))))
            .then(Commands.literal("removecategory")
                .requires(source -> ShopPermissions.admin(source, "category"))
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(ShopCommands::removeCategory)))
            .then(Commands.literal("balance")
                .requires(source -> ShopPermissions.admin(source, "balance"))
                .then(Commands.literal("get")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ShopCommands::adminBalanceGet)))
                .then(Commands.literal("set")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                            .executes(ShopCommands::adminBalanceSet))))
                .then(Commands.literal("add")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                            .executes(ShopCommands::adminBalanceAdd))))
                .then(Commands.literal("take")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                            .executes(ShopCommands::adminBalanceTake))))
            )
        );
    }

    private static int openShop(CommandContext<CommandSourceStack> context, ShopGui.Mode mode) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        remember(player);
        ShopGui.openCategories(player, mode);
        return 1;
    }

    private static int balance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        remember(player);
        player.sendSystemMessage(Component.literal("Balance: " + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID()))));
        return 1;
    }

    private static int pay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer sender = context.getSource().getPlayerOrException();
        remember(sender);
        String targetInput = StringArgumentType.getString(context, "player");
        double amount = DoubleArgumentType.getDouble(context, "amount");
        PlayerDirectory.ResolvedPlayer target = GuiShop.PLAYERS.resolve(context.getSource().getServer(), targetInput);

        if (target == null) {
            sender.sendSystemMessage(Component.literal("That player is unknown. They must have joined the server at least once."));
            return 0;
        }
        if (target.onlinePlayer() == null && !GuiShop.CONFIG.offlinePaymentsAllowed()) {
            sender.sendSystemMessage(Component.literal("Offline payments are disabled."));
            return 0;
        }
        if (target.uuid().equals(sender.getUUID())) {
            sender.sendSystemMessage(Component.literal("You cannot pay yourself."));
            return 0;
        }
        if (!GuiShop.ECONOMY.transfer(sender.getUUID(), target.uuid(), amount)) {
            sender.sendSystemMessage(Component.literal("You cannot afford " + GuiShop.CONFIG.money(amount) + "."));
            return 0;
        }

        sender.sendSystemMessage(Component.literal("Paid " + target.name() + " " + GuiShop.CONFIG.money(amount) + "."));
        if (target.onlinePlayer() != null) {
            target.onlinePlayer().sendSystemMessage(Component.literal(sender.getName().getString() + " paid you " + GuiShop.CONFIG.money(amount) + "."));
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
        String item = normalizeItemId(StringArgumentType.getString(context, "item"));
        return ShopService.showWorth(player, item, amount) ? 1 : 0;
    }

    private static int addHeldItem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Hold the item you want to add."));
            return 0;
        }

        String category = StringArgumentType.getString(context, "category");
        double buy = DoubleArgumentType.getDouble(context, "buy");
        double sell = DoubleArgumentType.getDouble(context, "sell");
        String itemId = ShopService.itemId(held);
        String displayName = held.getHoverName().getString();
        ShopConfig.ShopItem item = GuiShop.CONFIG.addOrUpdateItem(category, itemId, displayName, buy, sell);
        if (item == null) {
            context.getSource().sendFailure(Component.literal("Unknown category: " + category));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Added/updated " + itemId + " in " + category
            + " with buy " + GuiShop.CONFIG.money(buy) + " and sell " + GuiShop.CONFIG.money(sell) + "."), true);
        return 1;
    }

    private static int removeHeldItem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Hold the item you want to remove."));
            return 0;
        }
        String itemId = ShopService.itemId(held);
        int removed = GuiShop.CONFIG.removeItemEverywhere(itemId);
        if (removed == 0) {
            context.getSource().sendFailure(Component.literal(itemId + " is not listed."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Removed " + itemId + " from " + removed + " shop listing(s)."), true);
        return 1;
    }

    private static int listCategories(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("ClassicGUIShop categories:"), false);
        for (ShopConfig.Category category : GuiShop.CONFIG.categories) {
            context.getSource().sendSuccess(() -> Component.literal("- " + category.id + " (" + category.name + "): " + category.items.size() + " items"), false);
        }
        return 1;
    }

    private static int listItems(CommandContext<CommandSourceStack> context, int requestedPage) {
        String categoryId = StringArgumentType.getString(context, "category");
        ShopConfig.Category category = GuiShop.CONFIG.category(categoryId);
        if (category == null) {
            context.getSource().sendFailure(Component.literal("Unknown category: " + categoryId));
            return 0;
        }
        int pages = Math.max(1, (category.items.size() + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE);
        int page = Math.max(1, Math.min(requestedPage, pages));
        int start = (page - 1) * LIST_PAGE_SIZE;
        int end = Math.min(start + LIST_PAGE_SIZE, category.items.size());
        context.getSource().sendSuccess(() -> Component.literal(category.name + " items, page " + page + "/" + pages + ":"), false);
        for (int i = start; i < end; i++) {
            ShopConfig.ShopItem item = category.items.get(i);
            context.getSource().sendSuccess(() -> Component.literal("- " + item.item + " | buy "
                + GuiShop.CONFIG.money(item.buy) + " | sell " + GuiShop.CONFIG.money(item.sell)), false);
        }
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        GuiShop.CONFIG = ShopConfig.load();
        GuiShop.ECONOMY.updateConfig(GuiShop.CONFIG);
        context.getSource().sendSuccess(() -> Component.literal("ClassicGUIShop configuration reloaded."), true);
        return 1;
    }

    private static int setMultiplier(CommandContext<CommandSourceStack> context) {
        double value = DoubleArgumentType.getDouble(context, "value");
        GuiShop.CONFIG.priceMultiplier = value;
        GuiShop.CONFIG.save();
        context.getSource().sendSuccess(() -> Component.literal("Price multiplier set to " + value + "."), true);
        return 1;
    }

    private static int setPrice(CommandContext<CommandSourceStack> context) {
        String itemId = normalizeItemId(StringArgumentType.getString(context, "item"));
        double buy = DoubleArgumentType.getDouble(context, "buy");
        double sell = DoubleArgumentType.getDouble(context, "sell");
        if (!GuiShop.CONFIG.updatePrices(itemId, buy, sell)) {
            context.getSource().sendFailure(Component.literal("No listing exists for " + itemId + "."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Updated " + itemId + " to buy "
            + GuiShop.CONFIG.money(buy) + " and sell " + GuiShop.CONFIG.money(sell) + "."), true);
        return 1;
    }

    private static int setCategory(CommandContext<CommandSourceStack> context) {
        String itemId = normalizeItemId(StringArgumentType.getString(context, "item"));
        String category = StringArgumentType.getString(context, "category");
        if (!GuiShop.CONFIG.moveItem(itemId, category)) {
            context.getSource().sendFailure(Component.literal("Could not move item. Check the item ID and category."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Moved " + itemId + " to " + category + "."), true);
        return 1;
    }

    private static int addCategory(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        String icon = normalizeItemId(StringArgumentType.getString(context, "icon"));
        String name = StringArgumentType.getString(context, "name");
        ShopConfig.Category category = GuiShop.CONFIG.createCategory(id, name, icon);
        if (category == null) {
            context.getSource().sendFailure(Component.literal("That category already exists or the ID is invalid."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Created category " + category.id + " (" + category.name + ")."), true);
        return 1;
    }

    private static int removeCategory(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        if (!GuiShop.CONFIG.removeEmptyCategory(id)) {
            context.getSource().sendFailure(Component.literal("Category not found or it still contains items. Move/remove its items first."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Removed empty category " + id + "."), true);
        return 1;
    }

    private static int adminBalanceGet(CommandContext<CommandSourceStack> context) {
        PlayerDirectory.ResolvedPlayer target = resolveTarget(context);
        if (target == null) return 0;
        double balance = GuiShop.ECONOMY.balance(target.uuid());
        context.getSource().sendSuccess(() -> Component.literal(target.name() + " balance: " + GuiShop.CONFIG.money(balance)), false);
        return 1;
    }

    private static int adminBalanceSet(CommandContext<CommandSourceStack> context) {
        PlayerDirectory.ResolvedPlayer target = resolveTarget(context);
        if (target == null) return 0;
        double amount = DoubleArgumentType.getDouble(context, "amount");
        GuiShop.ECONOMY.setBalance(target.uuid(), amount);
        context.getSource().sendSuccess(() -> Component.literal("Set " + target.name() + " balance to " + GuiShop.CONFIG.money(amount) + "."), true);
        notifyBalanceChange(target, "Your balance was set to " + GuiShop.CONFIG.money(amount) + ".");
        return 1;
    }

    private static int adminBalanceAdd(CommandContext<CommandSourceStack> context) {
        PlayerDirectory.ResolvedPlayer target = resolveTarget(context);
        if (target == null) return 0;
        double amount = DoubleArgumentType.getDouble(context, "amount");
        GuiShop.ECONOMY.deposit(target.uuid(), amount);
        context.getSource().sendSuccess(() -> Component.literal("Added " + GuiShop.CONFIG.money(amount) + " to " + target.name() + "."), true);
        notifyBalanceChange(target, GuiShop.CONFIG.money(amount) + " was added to your balance.");
        return 1;
    }

    private static int adminBalanceTake(CommandContext<CommandSourceStack> context) {
        PlayerDirectory.ResolvedPlayer target = resolveTarget(context);
        if (target == null) return 0;
        double amount = DoubleArgumentType.getDouble(context, "amount");
        if (!GuiShop.ECONOMY.withdraw(target.uuid(), amount)) {
            context.getSource().sendFailure(Component.literal(target.name() + " does not have enough money."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Removed " + GuiShop.CONFIG.money(amount) + " from " + target.name() + "."), true);
        notifyBalanceChange(target, GuiShop.CONFIG.money(amount) + " was removed from your balance.");
        return 1;
    }

    private static PlayerDirectory.ResolvedPlayer resolveTarget(CommandContext<CommandSourceStack> context) {
        String input = StringArgumentType.getString(context, "player");
        PlayerDirectory.ResolvedPlayer target = GuiShop.PLAYERS.resolve(context.getSource().getServer(), input);
        if (target == null) context.getSource().sendFailure(Component.literal("Unknown player: " + input));
        return target;
    }

    private static void notifyBalanceChange(PlayerDirectory.ResolvedPlayer target, String message) {
        if (target.onlinePlayer() != null) target.onlinePlayer().sendSystemMessage(Component.literal(message));
    }

    private static void remember(ServerPlayer player) {
        GuiShop.PLAYERS.remember(player);
        GuiShop.ECONOMY.balance(player.getUUID());
    }

    private static String normalizeItemId(String input) {
        return input.contains(":") ? input : "minecraft:" + input;
    }
}
