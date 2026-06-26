package com.zycu.guishop;

import com.mojang.brigadier.CommandDispatcher;
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
        ShopMessages.admin(source, "/adminshop advanced <item|category|enchant|economy|multiplier>", false);
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
        HybridShopGui.openCategories(player, mode);
        return 1;
    }

    private static int openEnchantments(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        HybridShopGui.openEnchantments(player, 1);
        return 1;
    }
}
