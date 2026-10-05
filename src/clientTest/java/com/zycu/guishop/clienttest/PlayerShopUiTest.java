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
                    Screenshot.grab(client.gameDirectory, "alpha5-client-without-shop-mod.png", client.gameRenderer.mainRenderTarget(), 1, ignored -> {});
                    slot(client, 3); next();
                }
                case 3 -> {
                    if (!(screen instanceof ContainerScreen)) return;
                    check(screen.getTitle().getString().equals("Materials - Buy"), "Category opens directly without folders");
                    check(client.player.containerMenu.getSlot(0).getItem().is(Items.DIAMOND), "Former folder item is visible in category");
                    check(client.player.containerMenu.getSlot(1).getItem().is(Items.STONE), "Moved category listing visible");
                    slot(client, 0); next();
                }
                case 4 -> { next(); }
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
                    check(screen.getTitle().getString().equals("Materials - Sell"), "Sell stays in category");
                    slot(client, 0); next();
                }
                case 7 -> {
                    check(client.player.getInventory().countItem(Items.DIAMOND) == 0, "Plain client sale removed matching diamond");
                    client.gui.setScreen(null); client.getConnection().sendCommand("shop enchant"); next();
                }
                case 8 -> {
                    if (!(screen instanceof ContainerScreen) || !screen.getTitle().getString().endsWith("Buy")) return;
                    slot(client, 0); next();
                }
                case 9 -> {
                    if (!(screen instanceof ContainerScreen) || !screen.getTitle().getString().equals("Unbreaking Levels - Buy")) return;
                    var holder = client.player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, net.minecraft.resources.Identifier.parse("minecraft:unbreaking")));
                    var icon = client.player.containerMenu.getSlot(4).getItem();
                    check(icon.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS).getLevel(holder) == 5, "Live player menu includes level V above vanilla limit");
                    Screenshot.grab(client.gameDirectory, "alpha5-player-enchantment-levels.png", client.gameRenderer.mainRenderTarget(), 1, ignored -> {});
                    slot(client, 4); next();
                }
                case 10 -> {
                    check(client.player.getInventory().countItem(Items.ENCHANTED_BOOK) == 1, "Plain client receives purchased level V book");
                    var holder = client.player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, net.minecraft.resources.Identifier.parse("minecraft:unbreaking")));
                    boolean exact = false;
                    for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
                        var item = client.player.getInventory().getItem(i);
                        if (item.is(Items.ENCHANTED_BOOK) && item.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS).getLevel(holder) == 5) exact = true;
                    }
                    check(exact, "Delivered book retains exact level V"); slot(client, 53); next();
                }
                case 11 -> {
                    if (!(screen instanceof ContainerScreen) || !screen.getTitle().getString().equals("Unbreaking Levels - Sell")) return;
                    slot(client, 4); next();
                }
                case 12 -> {
                    check(client.player.getInventory().countItem(Items.ENCHANTED_BOOK) == 0, "Plain client sale consumes exact level V book");
                    System.out.println("[Player UI test] PASS: client has no ClassicGUIShop; restart persistence; category-only vanilla menus; real purchase and sale; delivered identity preserved; live level V menu, exact purchase and sale"); next();
                }
                case 13 -> { done = true; client.stop(); }
            }
        } catch (Throwable failure) { done = true; System.err.println("[Player UI test] FAIL at phase " + phase); failure.printStackTrace(); client.stop(); }
    }
    private void next() { phase++; delay = 25; }
    private static void slot(Minecraft client, int slot) {
        client.gameMode.handleContainerInput(client.player.containerMenu.containerId, slot, 0, ContainerInput.PICKUP, client.player);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
