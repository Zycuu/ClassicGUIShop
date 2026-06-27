package com.zycu.guishop;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

public final class GuiShop implements ModInitializer {
    private static final long IMPORT_REMINDER_INTERVAL_MS = 60L * 60L * 1000L;
    private static long nextImportReminderAtMillis = 0L;
    private static MinecraftServer initializedServer = null;

    public static ShopConfig CONFIG;
    public static EconomyStore ECONOMY;
    public static PlayerDirectory PLAYERS;
    public static ShopFolderStore FOLDERS;

    @Override
    public void onInitialize() {
        CONFIG = ShopConfig.load();
        CONFIG.permissionDefaults.putIfAbsent("guishop.command.ident", 0);
        CONFIG.permissionDefaults.putIfAbsent("guishop.admin.import", 2);
        CONFIG.permissionDefaults.putIfAbsent("guishop.admin.editor", 2);
        CONFIG.save();
        ECONOMY = new EconomyStore(CONFIG);
        PLAYERS = new PlayerDirectory();
        FOLDERS = SafeShopFolderLoader.load();
        FOLDERS.sync(CONFIG);

        System.out.println("[ShopGUI] Shop is now open!");
        System.out.println("[ClassicGUIShop] Initialized with " + CONFIG.categories.size() + " categories.");
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        ShopPermissions.logPermissionAccessors();
        ShopCommands.register(dispatcher);
        EconomyCommands.register(dispatcher);
        IntegrationCommands.register(dispatcher);
        VisualCommandOverrides.register(dispatcher);
        CommandIntegrityCheck.verify(dispatcher);
    }

    public static void onServerTick(MinecraftServer server) {
        if (initializedServer != server) {
            initializedServer = server;
            runStartupWork(server);
        }

        long now = System.currentTimeMillis();
        if (now < nextImportReminderAtMillis) return;
        nextImportReminderAtMillis = now + IMPORT_REMINDER_INTERVAL_MS;
        IntegrationImportService.logImportReminder(server);
    }

    private static void runStartupWork(MinecraftServer server) {
        VersionedListingStore.ReconcileResult versioned = VersionedListingStore.reconcile(
            CONFIG,
            FOLDERS,
            server.registryAccess()
        );
        CONFIG.ensureEnchantmentDefaults(server);
        VanillaCatalog.SyncResult catalog = VanillaCatalog.sync(CONFIG, server);
        EconomyExploitScanner.FixReport economy = EconomyExploitScanner.fixGeneratedExploits(server);
        FOLDERS.sync(CONFIG);
        IntegrationImportService.logImportReminder(server);
        nextImportReminderAtMillis = System.currentTimeMillis() + IMPORT_REMINDER_INTERVAL_MS;
        System.out.println("[ClassicGUIShop] Runtime listing filter parked " + versioned.parked()
            + ", restored " + versioned.restored() + ", and is preserving "
            + versioned.remainingParked() + " unavailable listing(s).");
        System.out.println("[ClassicGUIShop] Vanilla catalog synchronized. Added " + catalog.added()
            + ", removed " + catalog.removed() + ", repriced " + catalog.repriced() + ".");
        System.out.println("[ClassicGUIShop] Economy audit checked " + economy.after().recipesChecked()
            + " recipes, corrected " + economy.changedListings() + " generated sell prices, and found "
            + economy.after().exploits().size() + " remaining pricing issue(s).");
    }
}
