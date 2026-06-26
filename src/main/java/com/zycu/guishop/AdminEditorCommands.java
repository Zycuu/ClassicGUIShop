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

public final class AdminEditorCommands {
    private AdminEditorCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandNode<CommandSourceStack> existingAdmin = dispatcher.getRoot().getChild("adminshop");

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

        dispatcher.register(Commands.literal("adminshop")
            .requires(source -> ShopPermissions.admin(source, "root"))
            .executes(AdminEditorCommands::openEditor)
            .then(Commands.literal("edit")
                .requires(source -> ShopPermissions.admin(source, "editor"))
                .executes(AdminEditorCommands::openEditor))
            .then(Commands.literal("help").executes(AdminEditorCommands::advancedHelp))
            .then(advanced)
            .then(Commands.literal("reload")
                .requires(source -> ShopPermissions.admin(source, "reload"))
                .executes(AdminEditorCommands::reload))
        );

        dispatcher.register(Commands.literal("shop")
            .requires(source -> ShopPermissions.user(source, "guishop.command.shop"))
            .executes(context -> openShop(context, ShopGui.Mode.BUY))
            .then(Commands.literal("buy").executes(context -> openShop(context, ShopGui.Mode.BUY)))
            .then(Commands.literal("sell").executes(context -> openShop(context, ShopGui.Mode.SELL)))
            .then(Commands.literal("enchant").executes(AdminEditorCommands::openEnchantments))
        );

        CommandTreeCleanup.prune(dispatcher);
    }

    private static void redirectExisting(
        LiteralArgumentBuilder<CommandSourceStack> advanced,
        CommandNode<CommandSourceStack> adminRoot,
        String childName
    ) {
        if (adminRoot == null) return;
        CommandNode<CommandSourceStack> target = adminRoot.getChild(childName);
        if (target != null) advanced.then(Commands.literal(childName).redirect(target));
    }

    private static int advancedHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ShopMessages.admin(source, "/adminshop | Open the classic visual editor", false);
        ShopMessages.admin(source, "/adminshop reload | Reload configuration and folders", false);
        ShopMessages.admin(source, "/adminshop import | Import external content", false);
        ShopMessages.admin(source,
            "/adminshop advanced <item|category|folder|enchant|economy|multiplier>", false);
        ShopMessages.admin(source,
            "/adminshop advanced folder create <category> <icon> <display name>", false);
        return 1;
    }

    private static int createFolder(CommandContext<CommandSourceStack> context) {
        String categoryId = StringArgumentType.getString(context, "category");
        String icon = StringArgumentType.getString(context, "icon");
        String name = StringArgumentType.getString(context, "name").trim();
        ShopConfig.Category category = GuiShop.CONFIG.category(categoryId);

        if (category == null) {
            ShopMessages.error(context.getSource(), "Unknown shop category: " + categoryId);
            return 0;
        }
        if (name.isBlank()) {
            ShopMessages.error(context.getSource(), "Folder name cannot be blank.");
            return 0;
        }
        if (!VersionedListingStore.isItemRegistered(icon)) {
            ShopMessages.error(context.getSource(), "Unknown folder icon item: " + icon);
            return 0;
        }

        ShopFolderStore.Folder folder = GuiShop.FOLDERS.createFolder(category.id, name, icon);
        if (folder == null) {
            ShopMessages.error(context.getSource(), "Could not create that folder.");
            return 0;
        }

        ShopMessages.admin(context.getSource(), "Created folder " + folder.name + " [" + folder.id
            + "] in " + category.name + ".", true);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        GuiShop.CONFIG = ShopConfig.load();
        GuiShop.CONFIG.permissionDefaults.putIfAbsent("guishop.command.ident", 0);
        GuiShop.CONFIG.permissionDefaults.putIfAbsent("guishop.admin.import", 2);
        GuiShop.CONFIG.permissionDefaults.putIfAbsent("guishop.admin.editor", 2);
        GuiShop.FOLDERS = SafeShopFolderLoader.load();

        VersionedListingStore.ReconcileResult versioned = VersionedListingStore.reconcile(
            GuiShop.CONFIG,
            GuiShop.FOLDERS,
            context.getSource().getServer().registryAccess()
        );
        GuiShop.CONFIG.ensureEnchantmentDefaults(context.getSource().getServer());
        VanillaCatalog.SyncResult result = VanillaCatalog.sync(GuiShop.CONFIG, context.getSource().getServer());
        GuiShop.ECONOMY.updateConfig(GuiShop.CONFIG);
        GuiShop.FOLDERS.sync(GuiShop.CONFIG);
        ShopMessages.admin(context.getSource(), "Configuration and folders reloaded. Catalog added "
            + result.added() + ", removed " + result.removed() + ", repriced " + result.repriced()
            + "; parked " + versioned.parked() + " and restored " + versioned.restored()
            + " version-specific listing(s).", true);
        return 1;
    }

    private static int openEditor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        AdminShopEditorGuiV2.open(player);
        return 1;
    }

    private static int openShop(CommandContext<CommandSourceStack> context, ShopGui.Mode mode) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        EnchantmentShopService.rememberMode(player, mode);
        HybridShopGui.openCategories(player, mode);
        return 1;
    }

    private static int openEnchantments(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        EnchantmentShopService.rememberMode(player, ShopGui.Mode.BUY);
        HybridShopGui.openEnchantments(player, 1);
        return 1;
    }
}
