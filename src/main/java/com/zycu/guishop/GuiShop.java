package com.zycu.guishop;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

public final class GuiShop implements ModInitializer {
    private static MinecraftServer initializedServer = null;

    public static ShopConfig CONFIG;
    public static EconomyStore ECONOMY;
    public static PlayerDirectory PLAYERS;

    @Override
    public void onInitialize() {
        com.zycu.guishop.admin.AdminNetworking.register();
        HybridShopGui.register();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            initializedServer = null;
            initializeStores();
            com.zycu.guishop.admin.AccountOperations.initialize();
            ShopModes.initialize();
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ShopModes.checkpoint();
            com.zycu.guishop.admin.AdminNetworking.clear(); ShopMaps.clear();
        });
        // Client installation is an editor, not a second local copy of a remote server's economy.
    }

    private static void initializeStores() {
        CONFIG = ShopConfig.load();
        ECONOMY = new EconomyStore(CONFIG);
        PLAYERS = new PlayerDirectory();

        System.out.println("[ShopGUI] Shop is now open!");
        System.out.println("[ClassicGUIShop] Initialized with " + CONFIG.categories.size() + " categories.");
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        ShopPermissions.logPermissionAccessors();
        ShopCommands.register(dispatcher);
        CommandIntegrityCheck.verify(dispatcher);
    }

    public static void onServerTick(MinecraftServer server) {
        if (initializedServer != server) {
            initializedServer = server;
            runStartupWork(server);
        }
        ShopModes.tick(server);
    }

    private static void runStartupWork(MinecraftServer server) {
        VersionedListingStore.ReconcileResult versioned = VersionedListingStore.reconcile(
            CONFIG,
            server.registryAccess()
        );
        EconomyExploitScanner.FixReport economy = EconomyExploitScanner.fixGeneratedExploits(server);
        com.zycu.guishop.admin.AdminEditorService.rememberDiskState();
        System.out.println("[ClassicGUIShop] Runtime listing filter parked " + versioned.parked()
            + ", restored " + versioned.restored() + ", and is preserving "
            + versioned.remainingParked() + " unavailable listing(s).");
        System.out.println("[ClassicGUIShop] Economy audit checked " + economy.after().recipesChecked()
            + " recipes, corrected " + economy.changedListings() + " generated sell prices, and found "
            + economy.after().exploits().size() + " remaining pricing issue(s).");
    }
}
