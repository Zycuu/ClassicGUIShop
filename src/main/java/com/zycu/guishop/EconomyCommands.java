package com.zycu.guishop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class EconomyCommands {
    private static final int MAX_EXPLOITS_SHOWN = 20;

    private EconomyCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bal")
            .requires(source -> ShopPermissions.user(source, "guishop.command.balance"))
            .executes(EconomyCommands::balance));

        // Registered after ShopCommands so catalog sync and reload always finish with a pricing safety pass.
        dispatcher.register(Commands.literal("adminshop")
            .requires(source -> ShopPermissions.admin(source, "root"))
            .then(Commands.literal("reload")
                .requires(source -> ShopPermissions.admin(source, "reload"))
                .executes(EconomyCommands::reload))
            .then(Commands.literal("catalog")
                .requires(source -> ShopPermissions.admin(source, "reload"))
                .then(Commands.literal("sync").executes(EconomyCommands::syncCatalog)))
            .then(Commands.literal("economy")
                .requires(source -> ShopPermissions.admin(source, "balance"))
                .executes(EconomyCommands::economyHelp)
                .then(Commands.literal("audit").executes(EconomyCommands::audit))
                .then(Commands.literal("check").executes(EconomyCommands::audit))
                .then(Commands.literal("fix").executes(EconomyCommands::fix))
            ));
    }

    private static int balance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        GuiShop.PLAYERS.remember(player);
        ShopMessages.info(player, "Balance: " + GuiShop.CONFIG.money(GuiShop.ECONOMY.balance(player.getUUID())));
        return 1;
    }

    private static int economyHelp(CommandContext<CommandSourceStack> context) {
        ShopMessages.admin(context.getSource(), "/adminshop economy <get|set|add|take|audit|check|fix>", false);
        ShopMessages.admin(context.getSource(), "audit/check scans loaded crafting, cooking, stonecutting, smithing, datapack, and mod recipes.", false);
        ShopMessages.admin(context.getSource(), "fix lowers only automatically generated sell prices; manual admin prices are reported but preserved.", false);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        GuiShop.CONFIG = ShopConfig.load();
        GuiShop.CONFIG.ensureEnchantmentDefaults(context.getSource().getServer());
        VanillaCatalog.SyncResult catalog = VanillaCatalog.sync(GuiShop.CONFIG, context.getSource().getServer());
        EconomyExploitScanner.FixReport economy = EconomyExploitScanner.fixGeneratedExploits(context.getSource().getServer());
        GuiShop.ECONOMY.updateConfig(GuiShop.CONFIG);
        ShopMessages.admin(context.getSource(), "Configuration reloaded. Catalog added " + catalog.added()
            + ", removed " + catalog.removed() + ", repriced " + catalog.repriced()
            + "; pricing protection corrected " + economy.changedListings() + " generated sell price(s).", true);
        return 1;
    }

    private static int syncCatalog(CommandContext<CommandSourceStack> context) {
        VanillaCatalog.SyncResult catalog = VanillaCatalog.sync(GuiShop.CONFIG, context.getSource().getServer());
        EconomyExploitScanner.FixReport economy = EconomyExploitScanner.fixGeneratedExploits(context.getSource().getServer());
        ShopMessages.admin(context.getSource(), "Catalog synchronized. Added " + catalog.added()
            + ", removed " + catalog.removed() + ", repriced " + catalog.repriced()
            + "; pricing protection corrected " + economy.changedListings() + " generated sell price(s).", true);
        return 1;
    }

    private static int audit(CommandContext<CommandSourceStack> context) {
        EconomyExploitScanner.AuditReport report = EconomyExploitScanner.audit(context.getSource().getServer());
        if (report.exploits().isEmpty()) {
            ShopMessages.admin(context.getSource(), "Economy audit checked " + report.recipesChecked()
                + " recipes. No buy-craft-sell pricing issues were detected.", false);
            return 1;
        }

        ShopMessages.error(context.getSource(), "Economy audit found " + report.exploits().size()
            + " pricing issue(s): " + report.generatedExploitCount() + " generated and "
            + report.manualExploitCount() + " manual.");
        showExploits(context.getSource(), report);
        return report.exploits().size();
    }

    private static int fix(CommandContext<CommandSourceStack> context) {
        EconomyExploitScanner.FixReport report = EconomyExploitScanner.fixGeneratedExploits(context.getSource().getServer());
        ShopMessages.admin(context.getSource(), "Economy protection lowered " + report.changedListings()
            + " generated sell price(s).", true);

        if (report.after().exploits().isEmpty()) {
            ShopMessages.admin(context.getSource(), "No recipe-based pricing issues remain.", false);
            return Math.max(1, report.changedListings());
        }

        ShopMessages.error(context.getSource(), report.after().exploits().size()
            + " pricing issue(s) remain. These normally involve manual prices, which are never changed automatically.");
        showExploits(context.getSource(), report.after());
        return report.changedListings();
    }

    private static void showExploits(CommandSourceStack source, EconomyExploitScanner.AuditReport report) {
        int shown = Math.min(MAX_EXPLOITS_SHOWN, report.exploits().size());
        for (int index = 0; index < shown; index++) {
            EconomyExploitScanner.Exploit exploit = report.exploits().get(index);
            String manual = exploit.manualPrice() ? " [manual price]" : " [generated price]";
            ShopMessages.error(source, EconomyExploitScanner.describe(exploit) + manual);
        }
        int hidden = report.exploits().size() - shown;
        if (hidden > 0) {
            ShopMessages.warning(source, hidden + " additional pricing issue(s) were not printed to avoid flooding chat.");
        }
    }
}
