package com.zycu.guishop;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class GuiShop implements ModInitializer {
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
        FOLDERS = ShopFolderStore.load();
        FOLDERS.sync(CONFIG);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ShopCommands.register(dispatcher);
            EconomyCommands.register(dispatcher);
            IntegrationCommands.register(dispatcher);
            AdminEditorCommands.register(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CONFIG.ensureEnchantmentDefaults(server);
            VanillaCatalog.SyncResult catalog = VanillaCatalog.sync(CONFIG, server);
            EconomyExploitScanner.FixReport economy = EconomyExploitScanner.fixGeneratedExploits(server);
            FOLDERS.sync(CONFIG);
            IntegrationImportService.logStartupWarning(server);
            System.out.println("[ClassicGUIShop] Vanilla catalog synchronized. Added " + catalog.added()
                + ", removed " + catalog.removed() + ", repriced " + catalog.repriced() + ".");
            System.out.println("[ClassicGUIShop] Economy audit checked " + economy.after().recipesChecked()
                + " recipes, corrected " + economy.changedListings() + " generated sell prices, and found "
                + economy.after().exploits().size() + " remaining pricing issue(s).");
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PLAYERS.remember(handler.player);
            ECONOMY.balance(handler.player.getUUID());
            if (ShopPermissions.admin(handler.player, "root")) {
                IntegrationImportService.IntegrationScan scan = IntegrationImportService.scan(server);
                if (scan.hasExternalContent()) {
                    ShopMessages.warning(handler.player, "External mod or data-pack content is installed. Imported items are not automatically priced.");
                    ShopMessages.info(handler.player, "Run /adminshop edit to browse every imported listing, including hidden items.");
                }
            }
        });

        System.out.println("[ClassicGUIShop] Initialized with " + CONFIG.categories.size() + " categories.");
    }
}
