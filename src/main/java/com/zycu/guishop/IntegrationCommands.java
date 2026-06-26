package com.zycu.guishop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class IntegrationCommands {
    private IntegrationCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ident")
            .requires(source -> ShopPermissions.user(source, "guishop.command.ident"))
            .executes(IntegrationCommands::identifyHeldItem));

        dispatcher.register(Commands.literal("adminshop")
            .requires(source -> ShopPermissions.admin(source, "root"))
            .then(Commands.literal("import")
                .requires(source -> ShopPermissions.admin(source, "import"))
                .executes(IntegrationCommands::importHelp)
                .then(Commands.literal("scan").executes(IntegrationCommands::scan))
                .then(Commands.literal("mod")
                    .then(Commands.argument("namespace", StringArgumentType.word())
                        .suggests(ShopSuggestions.EXTERNAL_ITEM_NAMESPACES)
                        .executes(context -> importNamespace(context, null))
                        .then(Commands.argument("category", StringArgumentType.word())
                            .suggests(ShopSuggestions.IMPORT_CATEGORIES)
                            .executes(context -> importNamespace(context, StringArgumentType.getString(context, "category"))))))
                .then(Commands.literal("namespace")
                    .then(Commands.argument("namespace", StringArgumentType.word())
                        .suggests(ShopSuggestions.EXTERNAL_ITEM_NAMESPACES)
                        .executes(context -> importNamespace(context, null))
                        .then(Commands.argument("category", StringArgumentType.word())
                            .suggests(ShopSuggestions.IMPORT_CATEGORIES)
                            .executes(context -> importNamespace(context, StringArgumentType.getString(context, "category"))))))
                .then(Commands.literal("datapack")
                    .then(Commands.argument("namespace", StringArgumentType.word())
                        .suggests(ShopSuggestions.RECIPE_NAMESPACES)
                        .executes(context -> importDataPack(context, null))
                        .then(Commands.argument("category", StringArgumentType.word())
                            .suggests(ShopSuggestions.IMPORT_CATEGORIES)
                            .executes(context -> importDataPack(context, StringArgumentType.getString(context, "category"))))))
                .then(Commands.literal("held")
                    .then(Commands.argument("category", StringArgumentType.word())
                        .suggests(ShopSuggestions.IMPORT_CATEGORIES)
                        .then(Commands.argument("buy", DoubleArgumentType.doubleArg(0))
                            .suggests(ShopSuggestions.PRICES)
                            .then(Commands.argument("sell", DoubleArgumentType.doubleArg(0))
                                .suggests(ShopSuggestions.PRICES)
                                .executes(IntegrationCommands::importHeld)))))
                .then(Commands.literal("resourcepack")
                    .then(Commands.argument("category", StringArgumentType.word())
                        .suggests(ShopSuggestions.IMPORT_CATEGORIES)
                        .then(Commands.argument("buy", DoubleArgumentType.doubleArg(0))
                            .suggests(ShopSuggestions.PRICES)
                            .then(Commands.argument("sell", DoubleArgumentType.doubleArg(0))
                                .suggests(ShopSuggestions.PRICES)
                                .executes(IntegrationCommands::importHeld)))))
                .then(Commands.literal("price")
                    .then(Commands.argument("category", StringArgumentType.word())
                        .suggests(ShopSuggestions.CATEGORIES)
                        .then(Commands.argument("buy", DoubleArgumentType.doubleArg(0))
                            .suggests(ShopSuggestions.PRICES)
                            .then(Commands.argument("sell", DoubleArgumentType.doubleArg(0))
                                .suggests(ShopSuggestions.PRICES)
                                .executes(IntegrationCommands::priceCategory)))))
            ));
    }

    private static int identifyHeldItem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            ShopMessages.warning(player, "You are not holding an item.");
            return 0;
        }

        ItemStack template = held.copyWithCount(1);
        String itemId = ShopService.itemId(template);
        String listingId = ItemStackData.listingId(template, player.registryAccess());
        String namespace = itemId.substring(0, itemId.indexOf(':'));

        ShopMessages.info(player, "Held item: " + held.getHoverName().getString());
        ShopMessages.info(player, "Item ID: " + itemId);
        ShopMessages.info(player, "Exact listing ID: " + listingId);
        ShopMessages.info(player, "Namespace: " + namespace + (listingId.contains("#") ? " | Custom components detected" : ""));
        return 1;
    }

    private static int importHelp(CommandContext<CommandSourceStack> context) {
        ShopMessages.admin(context.getSource(), "/adminshop import scan", false);
        ShopMessages.admin(context.getSource(), "/adminshop import mod <namespace> [category]", false);
        ShopMessages.admin(context.getSource(), "/adminshop import datapack <recipe-namespace> [category]", false);
        ShopMessages.admin(context.getSource(), "/adminshop import held <category> <buy> <sell>", false);
        ShopMessages.admin(context.getSource(), "/adminshop import resourcepack <category> <buy> <sell>", false);
        ShopMessages.admin(context.getSource(), "/adminshop import price <category> <buy> <sell>", false);
        ShopMessages.warning(context.getSource(), "Bulk imported mod and data-pack listings start hidden with buy 0 and sell 0. Open /adminshop to review them, then assign prices before players can see the category.");
        return 1;
    }

    private static int scan(CommandContext<CommandSourceStack> context) {
        IntegrationImportService.IntegrationScan scan = IntegrationImportService.scan(context.getSource().getServer());
        ShopMessages.admin(context.getSource(), "Detected external item namespaces: " + display(scan.itemNamespaces()), false);
        ShopMessages.admin(context.getSource(), "Detected recipe namespaces: " + display(scan.recipeNamespaces()), false);
        ShopMessages.admin(context.getSource(), "Enabled non-vanilla data packs: " + display(scan.dataPacks()), false);
        ShopMessages.admin(context.getSource(), "Installed content mods: " + display(scan.installedMods()), false);
        ShopMessages.warning(context.getSource(), "External content is never automatically priced. Review every imported listing before enabling buy or sell values.");
        ShopMessages.info(context.getSource(), "Resource packs cannot register new item IDs by themselves. Hold a resource-pack-backed custom item and use /adminshop import resourcepack.", false);
        return scan.hasExternalContent() ? 1 : 0;
    }

    private static int importNamespace(CommandContext<CommandSourceStack> context, String category) {
        String namespace = StringArgumentType.getString(context, "namespace");
        IntegrationImportService.ImportResult result = IntegrationImportService.importItemNamespace(
            context.getSource().getServer(), namespace, category
        );
        if (result.categoryId() == null) {
            ShopMessages.error(context.getSource(), "No registered items were found in namespace " + namespace + ".");
            return 0;
        }
        reportImport(context, result, "item(s)");
        return result.imported();
    }

    private static int importDataPack(CommandContext<CommandSourceStack> context, String category) {
        String namespace = StringArgumentType.getString(context, "namespace");
        IntegrationImportService.ImportResult result = IntegrationImportService.importRecipeNamespace(
            context.getSource().getServer(), namespace, category
        );
        if (result.categoryId() == null) {
            ShopMessages.error(context.getSource(), "No importable recipe outputs were found in namespace " + namespace + ".");
            return 0;
        }
        reportImport(context, result, "recipe output(s)");
        return result.imported();
    }

    private static void reportImport(
        CommandContext<CommandSourceStack> context,
        IntegrationImportService.ImportResult result,
        String noun
    ) {
        ShopMessages.admin(context.getSource(), "Imported " + result.imported() + " " + noun + " from "
            + result.source() + " into category " + result.categoryId() + "; " + result.existing()
            + " already existed and " + result.skipped() + " were skipped.", true);
        ShopMessages.warning(context.getSource(), "The category is hidden from the normal player shop because its imported listings currently have buy 0 and sell 0.");
        ShopMessages.info(context.getSource(), "Review imported listings with /adminshop, including hidden items.", false);
        ShopMessages.info(context.getSource(), "Enable all listings with /adminshop import price " + result.categoryId() + " <buy> <sell>, or price individual listing IDs.", false);
    }

    private static int importHeld(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            ShopMessages.error(context.getSource(), "Hold the exact modded, data-pack, or resource-pack-backed item you want to import.");
            return 0;
        }

        String categoryId = StringArgumentType.getString(context, "category");
        double buy = DoubleArgumentType.getDouble(context, "buy");
        double sell = DoubleArgumentType.getDouble(context, "sell");
        ShopConfig.Category category = GuiShop.CONFIG.category(categoryId);
        if (category == null) {
            category = GuiShop.CONFIG.createCategory(categoryId, categoryId, ShopService.itemId(held));
        }
        if (category == null) {
            ShopMessages.error(context.getSource(), "Could not create or find category " + categoryId + ".");
            return 0;
        }

        ShopConfig.ShopItem imported = GuiShop.CONFIG.addOrUpdateItem(
            category.id,
            held,
            held.getHoverName().getString(),
            buy,
            sell,
            player.registryAccess()
        );
        if (imported == null) {
            ShopMessages.error(context.getSource(), "The held item could not be imported.");
            return 0;
        }

        ShopMessages.admin(context.getSource(), "Imported exact listing " + imported.listingId + " into "
            + category.id + " with buy " + GuiShop.CONFIG.money(buy) + " and sell " + GuiShop.CONFIG.money(sell) + ".", true);
        if (buy <= 0 && sell <= 0) {
            ShopMessages.warning(context.getSource(), "This listing is hidden from normal player shops until either buy or sell is greater than 0.");
        }
        ShopMessages.warning(context.getSource(), "External and resource-pack-backed item prices are manual and are not automatically balanced.");
        return 1;
    }

    private static int priceCategory(CommandContext<CommandSourceStack> context) {
        String categoryId = StringArgumentType.getString(context, "category");
        double buy = DoubleArgumentType.getDouble(context, "buy");
        double sell = DoubleArgumentType.getDouble(context, "sell");
        ShopConfig.Category category = GuiShop.CONFIG.category(categoryId);
        if (category == null) {
            ShopMessages.error(context.getSource(), "Unknown category: " + categoryId);
            return 0;
        }

        int changed = 0;
        for (ShopConfig.ShopItem listing : category.items) {
            listing.buy = buy;
            listing.sell = sell;
            listing.manualPrice = true;
            changed++;
        }
        GuiShop.CONFIG.save();

        ShopMessages.admin(context.getSource(), "Set " + changed + " listing(s) in " + category.id
            + " to buy " + GuiShop.CONFIG.money(buy) + " and sell " + GuiShop.CONFIG.money(sell) + ".", true);
        if (buy > 0 || sell > 0) {
            ShopMessages.success(context.getSource(), "The category can now appear in the normal shop for the enabled mode(s).", false);
        } else {
            ShopMessages.warning(context.getSource(), "Both values are 0, so the category remains hidden from normal player shops.");
        }
        ShopMessages.warning(context.getSource(), "Bulk pricing applies one value to every listing. Review individual items and run the economy audit before publishing the shop.");
        return changed;
    }

    private static String display(java.util.List<String> values) {
        return values.isEmpty() ? "none" : String.join(", ", values);
    }
}
