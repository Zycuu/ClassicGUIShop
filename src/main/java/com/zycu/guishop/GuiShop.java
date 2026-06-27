package com.zycu.guishop;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class GuiShop implements ModInitializer {
    private static final long IMPORT_REMINDER_INTERVAL_MS = 60L * 60L * 1000L;
    private static long nextImportReminderAtMillis = 0L;

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

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ShopCommands.register(dispatcher);
            EconomyCommands.register(dispatcher);
            IntegrationCommands.register(dispatcher);
            AdminEditorCommands.register(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
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
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();
            if (now < nextImportReminderAtMillis) return;
            nextImportReminderAtMillis = now + IMPORT_REMINDER_INTERVAL_MS;
            IntegrationImportService.logImportReminder(server);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PLAYERS.remember(handler.player);
            ECONOMY.balance(handler.player.getUUID());
        });

        System.out.println("[ShopGUI] Shop is now open!");
    }
}
