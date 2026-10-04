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
    public static ShopFolderStore FOLDERS;

    @Override
    public void onInitialize() {
        CONFIG = ShopConfig.load();
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
        CommandIntegrityCheck.verify(dispatcher);
    }

    public static void onServerTick(MinecraftServer server) {
        if (initializedServer != server) {
            initializedServer = server;
            runStartupWork(server);
        }
    }

    private static void runStartupWork(MinecraftServer server) {
        VersionedListingStore.ReconcileResult versioned = VersionedListingStore.reconcile(
            CONFIG,
            FOLDERS,
            server.registryAccess()
        );
        EconomyExploitScanner.FixReport economy = EconomyExploitScanner.fixGeneratedExploits(server);
        FOLDERS.sync(CONFIG);
        System.out.println("[ClassicGUIShop] Runtime listing filter parked " + versioned.parked()
            + ", restored " + versioned.restored() + ", and is preserving "
            + versioned.remainingParked() + " unavailable listing(s).");
        System.out.println("[ClassicGUIShop] Economy audit checked " + economy.after().recipesChecked()
            + " recipes, corrected " + economy.changedListings() + " generated sell prices, and found "
            + economy.after().exploits().size() + " remaining pricing issue(s).");
    }
}
