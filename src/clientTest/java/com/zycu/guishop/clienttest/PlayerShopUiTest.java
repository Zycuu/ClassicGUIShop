package com.zycu.guishop.clienttest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

/** Only automation hooks are installed. No ClassicGUIShop classes or channels are loaded. */
public final class PlayerShopUiTest implements ClientModInitializer {
    private int phase, delay = 80, age;
    private boolean done;
    @Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }
    private void tick(Minecraft client) {
        if (done) return;
        try {
            if (++age > 3000) throw new AssertionError("Player UI timeout at phase " + phase);
            if (delay-- > 0) return;
            var screen = client.gui.screen();
            switch (phase) {
                case 0 -> {
                    if (client.gui.overlay() != null || screen == null) return;
                    check(!FabricLoader.getInstance().isModLoaded("guishop"), "ClassicGUIShop must be absent from the client");
                    client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                    ConnectScreen.startConnecting(screen, client, ServerAddress.parseString("127.0.0.1:25573"),
                        new ServerData("Player shop test", "127.0.0.1:25573", ServerData.Type.OTHER), false, null);
                    next();
                }
                case 1 -> {
                    if (client.player == null) return;
                    client.gui.setScreen(null); client.getConnection().sendCommand("shop"); next();
                }
                case 2 -> {
                    if (!(screen instanceof ContainerScreen)) return;
                    check(screen.getTitle().getString().equals("Zycu's Shop - Buy"), "Saved shop title survives restart");
                    Screenshot.grab(client.gameDirectory, "alpha-client-without-shop-mod.png", client.gameRenderer.mainRenderTarget(), 1, ignored -> {});
                    slot(client, 3); next();
                }
                case 3 -> {
                    if (!(screen instanceof ContainerScreen) || !screen.getTitle().getString().contains("Folders")) return;
                    slot(client, 0); next();
                }
                case 4 -> {
                    if (!(screen instanceof ContainerScreen) || !screen.getTitle().getString().contains("Gems")) return;
                    slot(client, 0); next();
                }
                case 5 -> {
                    check(client.player.getInventory().countItem(Items.DIAMOND) == 1, "Plain client purchase delivered one diamond");
                    // Configured descriptions decorate the menu copy, never the delivered item.
                    for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
                        var item = client.player.getInventory().getItem(i);
                        if (item.is(Items.DIAMOND)) {
                            var lore = item.get(net.minecraft.core.component.DataComponents.LORE);
                            check(lore == null || lore.lines().isEmpty(), "Purchase identity preserved");
                        }
                    }
                    slot(client, 53); next();
                }
                case 6 -> {
                    if (!(screen instanceof ContainerScreen) || !screen.getTitle().getString().endsWith("Sell")) return;
                    if (screen.getTitle().getString().contains("Folders")) { slot(client, 0); delay = 25; return; }
                    slot(client, 0); next();
                }
                case 7 -> {
                    check(client.player.getInventory().countItem(Items.DIAMOND) == 0, "Plain client sale removed matching diamond");
                    System.out.println("[Player UI test] PASS: client has no ClassicGUIShop; restart persistence; vanilla menus; real purchase and sale; delivered identity preserved"); next();
                }
                case 8 -> { done = true; client.stop(); }
            }
        } catch (Throwable failure) { done = true; System.err.println("[Player UI test] FAIL at phase " + phase); failure.printStackTrace(); client.stop(); }
    }
    private void next() { phase++; delay = 25; }
    private static void slot(Minecraft client, int slot) {
        client.gameMode.handleContainerInput(client.player.containerMenu.containerId, slot, 0, ContainerInput.PICKUP, client.player);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
