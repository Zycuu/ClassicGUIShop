package com.zycu.guishop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ShopCommands {
    private static final int LIST_PAGE_SIZE = 10;

    private ShopCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
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
            .then(Commands.argument("item", StringArgumentType.word())
                .suggests(ShopSuggestions.ITEMS)
                .executes(context -> worthItem(context, 1))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 6400))
                    .suggests(ShopSuggestions.AMOUNTS)
                    .executes(context -> worthItem(context, IntegerArgumentType.getInteger(context, "amount")))))
        );

        dispatcher.register(Commands.literal("adminshop")
            .requires(source -> ShopPermissions.admin(source, "root"))
            .executes(ShopCommands::adminHelp)
            .then(Commands.literal("reload")
                .requires(source -> ShopPermissions.admin(source, "reload"))
                .executes(ShopCommands::reload))
            .then(Commands.literal("multiplier")
                .requires(source -> ShopPermissions.admin(source, "multiplier"))
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                    .suggests(ShopSuggestions.PRICES)
                    .executes(ShopCommands::setMultiplier)))
            .then(Commands.literal("catalog")
                .requires(source -> ShopPermissions.admin(source, "reload"))
                .then(Commands.literal("sync").executes(ShopCommands::syncCatalog)))
            .then(Commands.literal("item")
                .then(Commands.literal("add")
                    .requires(source -> ShopPermissions.admin(source, "item.add"))
                    .then(Commands.argument("category", StringArgumentType.word())
                        .suggests(ShopSuggestions.CATEGORIES)
                        .then(Commands.argument("buy", DoubleArgumentType.doubleArg(0))
                            .suggests(ShopSuggestions.PRICES)
                            .then(Commands.argument("sell", DoubleArgumentType.doubleArg(0))
                                .suggests(ShopSuggestions.PRICES)
                                .executes(ShopCommands::addHeldItem)))))
                .then(Commands.literal("remove")
                    .requires(source -> ShopPermissions.admin(source, "item.remove"))
                    .executes(ShopCommands::removeHeldItem))
                .then(Commands.literal("price")
                    .requires(source -> ShopPermissions.admin(source, "item.price"))
                    .then(Commands.argument("item", StringArgumentType.word())
                        .suggests(ShopSuggestions.ITEMS)
                        .then(Commands.argument("buy", DoubleArgumentType.doubleArg(0))
                            .suggests(ShopSuggestions.PRICES)
                            .then(Commands.argument("sell", DoubleArgumentType.doubleArg(0))
                                .suggests(ShopSuggestions.PRICES)
                                .executes(ShopCommands::setPrice)))))
                .then(Commands.literal("move")
                    .requires(source -> ShopPermissions.admin(source, "item.category"))
                    .then(Commands.argument("item", StringArgumentType.word())
                        .suggests(ShopSuggestions.ITEMS)
                        .then(Commands.argument("category", StringArgumentType.word())
                            .suggests(ShopSuggestions.CATEGORIES)
                            .executes(ShopCommands::setCategory))))
                .then(Commands.literal("list")
                    .requires(source -> ShopPermissions.admin(source, "item.list"))
                    .executes(ShopCommands::listCategories)
                    .then(Commands.argument("category", StringArgumentType.word())
                        .suggests(ShopSuggestions.CATEGORIES)
                        .executes(context -> listItems(context, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                            .suggests(ShopSuggestions.PAGES)
                            .executes(context -> listItems(context, IntegerArgumentType.getInteger(context, "page"))))))
            )
            .then(Commands.literal("category")
                .requires(source -> ShopPermissions.admin(source, "category"))
                .then(Commands.literal("list").executes(ShopCommands::listCategories))
                .then(Commands.literal("add")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(ShopSuggestions.ITEM_NAMESPACES)
                        .then(Commands.argument("icon", StringArgumentType.word())
                            .suggests(ShopSuggestions.ITEM_IDS)
                            .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ShopCommands::addCategory)))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(ShopSuggestions.CATEGORIES)
                        .executes(ShopCommands::removeCategory)))
            )
            .then(Commands.literal("enchant")
                .requires(source -> ShopPermissions.admin(source, "enchant"))
                .then(Commands.literal("list")
                    .executes(context -> listEnchantments(context, 1))
                    .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .suggests(ShopSuggestions.PAGES)
                        .executes(context -> listEnchantments(context, IntegerArgumentType.getInteger(context, "page")))))
                .then(Commands.literal("set")
                    .then(Commands.argument("enchantment", StringArgumentType.word())
                        .suggests(ShopSuggestions.ENCHANTMENTS)
                        .then(Commands.argument("pricePerLevel", DoubleArgumentType.doubleArg(0.01))
                            .suggests(ShopSuggestions.PRICES)
                            .executes(context -> setEnchantment(context, 0))
                            .then(Commands.argument("maxLevel", IntegerArgumentType.integer(1, 255))
                                .suggests(ShopSuggestions.ENCHANTMENT_LEVELS)
                                .executes(context -> setEnchantment(context, IntegerArgumentType.getInteger(context, "maxLevel")))))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("enchantment", StringArgumentType.word())
                        .suggests(ShopSuggestions.ENCHANTMENTS)
                        .executes(ShopCommands::removeEnchantment)))
                .then(Commands.literal("defaultprice")
                    .then(Commands.argument("pricePerLevel", DoubleArgumentType.doubleArg(0.01))
                        .suggests(ShopSuggestions.PRICES)
                        .executes(ShopCommands::setDefaultEnchantmentPrice)))
                .then(Commands.literal("enabled")
                    .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ShopCommands::setEnchantmentsEnabled)))
            )
            .then(Commands.literal("economy")
                .requires(source -> ShopPermissions.admin(source, "balance"))
                .then(Commands.literal("get")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(ShopSuggestions.PLAYERS)
                        .executes(ShopCommands::adminBalanceGet)))
                .then(Commands.literal("set")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(ShopSuggestions.PLAYERS)
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                            .suggests(ShopSuggestions.PRICES)
                            .executes(ShopCommands::adminBalanceSet))))
                .then(Commands.literal("add")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(ShopSuggestions.PLAYERS)
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                            .suggests(ShopSuggestions.PRICES)
                            .executes(ShopCommands::adminBalanceAdd))))
                .then(Commands.literal("take")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(ShopSuggestions.PLAYERS)
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                            .suggests(ShopSuggestions.PRICES)
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

    private static int openEnchantments(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        remember(player);
        ShopGui.openEnchantments(player, 1);
        return 1;
    }

    private static int balance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        remember(player);
        ShopMessages.info(player, "Balance: " + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID())));
        return 1;
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

    private static int adminHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ShopMessages.admin(source, "/adminshop item <add|remove|price|move|list>", false);
        ShopMessages.admin(source, "/adminshop category <add|remove|list>", false);
        ShopMessages.admin(source, "/adminshop enchant <set|remove|list|defaultprice|enabled>", false);
        ShopMessages.admin(source, "/adminshop economy <get|set|add|take>", false);
        ShopMessages.admin(source, "/adminshop import <scan|mod|datapack|held|resourcepack>", false);
        ShopMessages.admin(source, "/adminshop catalog sync | /adminshop multiplier <value> | /adminshop reload", false);
        return 1;
    }

    private static int addHeldItem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            ShopMessages.error(context.getSource(), "Hold the exact item you want to add.");
            return 0;
        }

        String category = StringArgumentType.getString(context, "category");
        double buy = DoubleArgumentType.getDouble(context, "buy");
        double sell = DoubleArgumentType.getDouble(context, "sell");
        ShopConfig.ShopItem item = GuiShop.CONFIG.addOrUpdateItem(
            category,
            held,
            held.getHoverName().getString(),
            buy,
            sell,
            player.registryAccess()
        );
        if (item == null) {
            ShopMessages.error(context.getSource(), "Unknown category: " + category);
            return 0;
        }

        ShopMessages.admin(context.getSource(), "Added/updated " + item.listingId + " in " + category
            + " with buy " + GuiShop.CONFIG.money(buy) + " and sell " + GuiShop.CONFIG.money(sell) + ".", true);
        return 1;
    }

    private static int removeHeldItem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            ShopMessages.error(context.getSource(), "Hold the exact item you want to remove.");
            return 0;
        }
        String listingId = ItemStackData.listingId(held.copyWithCount(1), player.registryAccess());
        int removed = GuiShop.CONFIG.removeItemEverywhere(held, player.registryAccess());
        if (removed == 0) {
            ShopMessages.error(context.getSource(), listingId + " is not listed.");
            return 0;
        }
        ShopMessages.admin(context.getSource(), "Removed " + listingId + " from " + removed + " listing(s).", true);
        return 1;
    }

    private static int listCategories(CommandContext<CommandSourceStack> context) {
        ShopMessages.admin(context.getSource(), "ClassicGUIShop categories:", false);
        for (ShopConfig.Category category : GuiShop.CONFIG.categories) {
            ShopMessages.admin(context.getSource(), "- " + category.id + " (" + category.name + "): " + category.items.size() + " items", false);
        }
        return 1;
    }

    private static int listItems(CommandContext<CommandSourceStack> context, int requestedPage) {
        String categoryId = StringArgumentType.getString(context, "category");
        ShopConfig.Category category = GuiShop.CONFIG.category(categoryId);
        if (category == null) {
            ShopMessages.error(context.getSource(), "Unknown category: " + categoryId);
            return 0;
        }
        int pages = Math.max(1, (category.items.size() + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE);
        int page = Math.max(1, Math.min(requestedPage, pages));
        int start = (page - 1) * LIST_PAGE_SIZE;
        int end = Math.min(start + LIST_PAGE_SIZE, category.items.size());
        ShopMessages.admin(context.getSource(), category.name + " items, page " + page + "/" + pages + ":", false);
        for (int i = start; i < end; i++) {
            ShopConfig.ShopItem item = category.items.get(i);
            ShopMessages.admin(context.getSource(), "- " + item.listingId + " | " + item.name + " | buy "
                + GuiShop.CONFIG.money(item.buy) + " | sell " + GuiShop.CONFIG.money(item.sell)
                + (Boolean.TRUE.equals(item.manualPrice) ? " | manual" : " | balanced"), false);
        }
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        GuiShop.CONFIG = ShopConfig.load();
        GuiShop.CONFIG.ensureEnchantmentDefaults(context.getSource().getServer());
        VanillaCatalog.SyncResult result = VanillaCatalog.sync(GuiShop.CONFIG, context.getSource().getServer());
        GuiShop.ECONOMY.updateConfig(GuiShop.CONFIG);
        ShopMessages.admin(context.getSource(), "Configuration reloaded. Catalog added " + result.added()
            + ", removed " + result.removed() + ", repriced " + result.repriced() + ".", true);
        return 1;
    }

    private static int syncCatalog(CommandContext<CommandSourceStack> context) {
        VanillaCatalog.SyncResult result = VanillaCatalog.sync(GuiShop.CONFIG, context.getSource().getServer());
        ShopMessages.admin(context.getSource(), "Catalog synchronized. Added " + result.added()
            + ", removed " + result.removed() + ", repriced " + result.repriced() + ".", true);
        return 1;
    }

    private static int setMultiplier(CommandContext<CommandSourceStack> context) {
        double value = DoubleArgumentType.getDouble(context, "value");
        GuiShop.CONFIG.priceMultiplier = value;
        GuiShop.CONFIG.save();
        ShopMessages.admin(context.getSource(), "Price multiplier set to " + value + ".", true);
        return 1;
    }

    private static int setPrice(CommandContext<CommandSourceStack> context) {
        String identifier = StringArgumentType.getString(context, "item");
        double buy = DoubleArgumentType.getDouble(context, "buy");
        double sell = DoubleArgumentType.getDouble(context, "sell");
        if (!GuiShop.CONFIG.updatePrices(identifier, buy, sell)) {
            ShopMessages.error(context.getSource(), "No listing exists for " + identifier + ".");
            return 0;
        }
        ShopMessages.admin(context.getSource(), "Updated " + identifier + " to buy "
            + GuiShop.CONFIG.money(buy) + " and sell " + GuiShop.CONFIG.money(sell) + ".", true);
        return 1;
    }

    private static int setCategory(CommandContext<CommandSourceStack> context) {
        String identifier = StringArgumentType.getString(context, "item");
        String category = StringArgumentType.getString(context, "category");
        if (!GuiShop.CONFIG.moveItem(identifier, category)) {
            ShopMessages.error(context.getSource(), "Could not move item. Check the listing ID/item ID and category.");
            return 0;
        }
        ShopMessages.admin(context.getSource(), "Moved " + identifier + " to " + category + ".", true);
        return 1;
    }

    private static int addCategory(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        String icon = ShopConfig.normalizeIdentifier(StringArgumentType.getString(context, "icon"));
        String name = StringArgumentType.getString(context, "name");
        ShopConfig.Category category = GuiShop.CONFIG.createCategory(id, name, icon);
        if (category == null) {
            ShopMessages.error(context.getSource(), "That category already exists or the ID is invalid.");
            return 0;
        }
        ShopMessages.admin(context.getSource(), "Created category " + category.id + " (" + category.name + ").", true);
        return 1;
    }

    private static int removeCategory(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        ShopConfig.RemovedCategory removed = GuiShop.CONFIG.removeCategoryAndContents(id);
        if (removed == null) {
            ShopMessages.error(context.getSource(), "Category not found: " + id);
            return 0;
        }
        ShopMessages.admin(context.getSource(), "Removed category " + removed.id() + " and all "
            + removed.removedListings() + " contained listings.", true);
        return 1;
    }

    private static int listEnchantments(CommandContext<CommandSourceStack> context, int requestedPage) {
        GuiShop.CONFIG.ensureEnchantmentDefaults(context.getSource().getServer());
        List<Map.Entry<String, ShopConfig.EnchantmentOffer>> entries = new ArrayList<>(GuiShop.CONFIG.enchantments.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        int pages = Math.max(1, (entries.size() + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE);
        int page = Math.max(1, Math.min(requestedPage, pages));
        int start = (page - 1) * LIST_PAGE_SIZE;
        int end = Math.min(start + LIST_PAGE_SIZE, entries.size());
        ShopMessages.admin(context.getSource(), "Enchanted books, page " + page + "/" + pages + ":", false);
        for (int i = start; i < end; i++) {
            Map.Entry<String, ShopConfig.EnchantmentOffer> entry = entries.get(i);
            ShopConfig.EnchantmentOffer offer = entry.getValue();
            String status = offer.enabled()
                ? GuiShop.CONFIG.money(offer.pricePerLevel) + "/level | max " + offer.maxLevel
                : "disabled";
            ShopMessages.admin(context.getSource(), "- " + entry.getKey() + " | " + status, false);
        }
        return 1;
    }

    private static int setEnchantment(CommandContext<CommandSourceStack> context, int requestedMaxLevel) {
        String id = ShopConfig.normalizeIdentifier(StringArgumentType.getString(context, "enchantment"));
        double price = DoubleArgumentType.getDouble(context, "pricePerLevel");
        Registry<Enchantment> registry = context.getSource().getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> holder;
        try {
            ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(id));
            holder = registry.getOrThrow(key);
        } catch (Exception exception) {
            ShopMessages.error(context.getSource(), "Unknown enchantment: " + id);
            return 0;
        }

        int registryMax = holder.value().getMaxLevel();
        int maxLevel = requestedMaxLevel <= 0 ? registryMax : Math.min(requestedMaxLevel, registryMax);
        String displayName = holder.value().description().getString();
        GuiShop.CONFIG.setEnchantmentOffer(id, displayName, price, maxLevel);
        ShopMessages.admin(context.getSource(), "Enabled enchanted books for " + id + " at "
            + GuiShop.CONFIG.money(price) + " per level, maximum level " + maxLevel + ".", true);
        return 1;
    }

    private static int removeEnchantment(CommandContext<CommandSourceStack> context) {
        String id = ShopConfig.normalizeIdentifier(StringArgumentType.getString(context, "enchantment"));
        if (!GuiShop.CONFIG.disableEnchantment(id)) {
            ShopMessages.error(context.getSource(), "Unknown enchantment listing: " + id);
            return 0;
        }
        ShopMessages.admin(context.getSource(), "Disabled enchanted books for " + id + ".", true);
        return 1;
    }

    private static int setDefaultEnchantmentPrice(CommandContext<CommandSourceStack> context) {
        double price = DoubleArgumentType.getDouble(context, "pricePerLevel");
        GuiShop.CONFIG.defaultEnchantmentPricePerLevel = price;
        GuiShop.CONFIG.save();
        ShopMessages.admin(context.getSource(), "Default enchanted book price set to "
            + GuiShop.CONFIG.money(price) + " per level for newly discovered enchantments.", true);
        return 1;
    }

    private static int setEnchantmentsEnabled(CommandContext<CommandSourceStack> context) {
        boolean enabled = BoolArgumentType.getBool(context, "value");
        GuiShop.CONFIG.enchantmentsEnabled = enabled;
        GuiShop.CONFIG.save();
        ShopMessages.admin(context.getSource(), "Enchanted book shop " + (enabled ? "enabled" : "disabled") + ".", true);
        return 1;
    }

    private static int adminBalanceGet(CommandContext<CommandSourceStack> context) {
        PlayerDirectory.ResolvedPlayer target = resolveTarget(context);
        if (target == null) return 0;
        double balance = GuiShop.ECONOMY.balance(target.uuid());
        ShopMessages.admin(context.getSource(), target.name() + " balance: " + GuiShop.CONFIG.money(balance), false);
        return 1;
    }

    private static int adminBalanceSet(CommandContext<CommandSourceStack> context) {
        PlayerDirectory.ResolvedPlayer target = resolveTarget(context);
        if (target == null) return 0;
        double amount = DoubleArgumentType.getDouble(context, "amount");
        GuiShop.ECONOMY.setBalance(target.uuid(), amount);
        ShopMessages.admin(context.getSource(), "Set " + target.name() + " balance to " + GuiShop.CONFIG.money(amount) + ".", true);
        notifyBalanceChange(target, "Your balance was set to " + GuiShop.CONFIG.money(amount) + ".");
        return 1;
    }

    private static int adminBalanceAdd(CommandContext<CommandSourceStack> context) {
        PlayerDirectory.ResolvedPlayer target = resolveTarget(context);
        if (target == null) return 0;
        double amount = DoubleArgumentType.getDouble(context, "amount");
        GuiShop.ECONOMY.deposit(target.uuid(), amount);
        ShopMessages.admin(context.getSource(), "Added " + GuiShop.CONFIG.money(amount) + " to " + target.name() + ".", true);
        notifyBalanceChange(target, GuiShop.CONFIG.money(amount) + " was added to your balance.");
        return 1;
    }

    private static int adminBalanceTake(CommandContext<CommandSourceStack> context) {
        PlayerDirectory.ResolvedPlayer target = resolveTarget(context);
        if (target == null) return 0;
        double amount = DoubleArgumentType.getDouble(context, "amount");
        if (!GuiShop.ECONOMY.withdraw(target.uuid(), amount)) {
            ShopMessages.error(context.getSource(), target.name() + " does not have enough money.");
            return 0;
        }
        ShopMessages.admin(context.getSource(), "Removed " + GuiShop.CONFIG.money(amount) + " from " + target.name() + ".", true);
        notifyBalanceChange(target, GuiShop.CONFIG.money(amount) + " was removed from your balance.");
        return 1;
    }

    private static PlayerDirectory.ResolvedPlayer resolveTarget(CommandContext<CommandSourceStack> context) {
        String input = StringArgumentType.getString(context, "player");
        PlayerDirectory.ResolvedPlayer target = GuiShop.PLAYERS.resolve(context.getSource().getServer(), input);
        if (target == null) ShopMessages.error(context.getSource(), "Unknown player: " + input);
        return target;
    }

    private static void notifyBalanceChange(PlayerDirectory.ResolvedPlayer target, String message) {
        if (target.onlinePlayer() != null) ShopMessages.info(target.onlinePlayer(), message);
    }

    private static void remember(ServerPlayer player) {
        GuiShop.PLAYERS.remember(player);
        GuiShop.ECONOMY.balance(player.getUUID());
    }
}
