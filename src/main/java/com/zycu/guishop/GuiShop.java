package com.zycu.guishop;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class GuiShop implements ModInitializer {
    public static ShopConfig CONFIG;
    public static EconomyStore ECONOMY;
    public static PlayerDirectory PLAYERS;

    @Override
    public void onInitialize() {
        CONFIG = ShopConfig.load();
        ECONOMY = new EconomyStore(CONFIG);
        PLAYERS = new PlayerDirectory();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ShopCommands.register(dispatcher));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PLAYERS.remember(handler.player);
            ECONOMY.balance(handler.player.getUUID());
        });

        System.out.println("[ClassicGUIShop] Initialized with " + CONFIG.categories.size() + " categories.");
    }
}
