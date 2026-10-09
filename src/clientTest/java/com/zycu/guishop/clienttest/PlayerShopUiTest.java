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
    private int phase, delay = 80, age, subphase;
    private boolean done;
    private int initialDiamonds, initialBooks;
    @Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }
    private void tick(Minecraft client) {
        if (done) return;
        try {
            if (++age > 3000) throw new AssertionError("Player UI timeout at phase " + phase + "; screen=" + (client.gui.screen() == null ? "null" : client.gui.screen().getTitle().getString()) + "; menu=" + (client.player == null ? -1 : client.player.containerMenu.containerId));
            if (delay-- > 0) return;
            var screen = client.gui.screen();
            if (phase >= 13 && age % 100 == 0) System.out.println("[Player UI test] State: phase=" + phase + "; screen=" + (screen == null ? "null" : screen.getTitle().getString()) + "; menu=" + client.player.containerMenu.containerId);
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
                    initialDiamonds = client.player.getInventory().countItem(Items.DIAMOND);
                    initialBooks = client.player.getInventory().countItem(Items.ENCHANTED_BOOK);
                    client.gui.setScreen(null); client.getConnection().sendCommand("shop"); next();
                }
                case 2 -> {
                    if (!(screen instanceof ContainerScreen)) return;
                    if (subphase == 1) {
                        Screenshot.grab(client.gameDirectory, "alpha6-clean-backpack-tooltip.png", client.gameRenderer.mainRenderTarget(), 1, ignored -> {});
                        slot(client, 3); subphase = 0; next(); return;
                    }
                    check(screen.getTitle().getString().equals("Zycu's Shop"), "Saved shop title survives restart");
                    var categoryIcon = client.player.containerMenu.getSlot(3).getItem();
                    var iconLines = categoryIcon.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(client.level), client.player, net.minecraft.world.item.TooltipFlag.NORMAL).stream().map(net.minecraft.network.chat.Component::getString).toList();
                    check(iconLines.equals(java.util.List.of("Materials", "Shop category help")), "Native player category shows only shop text without default item lore");
                    if (FabricLoader.getInstance().isModLoaded("travelersbackpack")) {
                        check(categoryIcon.get(net.minecraft.core.component.DataComponents.ITEM_MODEL).toString().equals("travelersbackpack:standard"), "Native player menu retains the supplied backpack model");
                    }
                    var base = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class;
                    var left = base.getDeclaredField("leftPos"); left.setAccessible(true);
                    var top = base.getDeclaredField("topPos"); top.setAccessible(true);
                    var nativeSlot = client.player.containerMenu.getSlot(3);
                    var window = client.getWindow();
                    double x = (left.getInt(screen) + nativeSlot.x + 8) * window.getScreenWidth() / (double) window.getGuiScaledWidth();
                    double y = (top.getInt(screen) + nativeSlot.y + 8) * window.getScreenHeight() / (double) window.getGuiScaledHeight();
                    client.mouseHandler.onMove(window.handle(), x, y, 0, 0); client.mouseHandler.handleAccumulatedMovement();
                    subphase = 1; delay = 4;
                }
                case 3 -> {
                    if (!(screen instanceof ContainerScreen)) return;
                    check(screen.getTitle().getString().equals("Materials"), "Category opens directly without folders");
                    check(client.player.containerMenu.getSlot(0).getItem().getHoverName().getString().equals("Diamond"), "Former folder item is visible in category");
                    check(client.player.containerMenu.getSlot(1).getItem().getHoverName().getString().equals("Stone"), "Moved category listing visible");
                    slot(client, 0); next();
                }
                case 4 -> { next(); }
                case 5 -> {
                    check(client.player.getInventory().countItem(Items.DIAMOND) == initialDiamonds + 1, "Plain client purchase adds one diamond to its existing inventory");
                    // Configured descriptions decorate the menu copy, never the delivered item.
                    for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
                        var item = client.player.getInventory().getItem(i);
                        if (item.is(Items.DIAMOND)) {
                            var lore = item.get(net.minecraft.core.component.DataComponents.LORE);
                            check(lore == null || lore.lines().isEmpty(), "Purchase identity preserved");
                        }
                    }
                    next();
                }
                case 6 -> {
                    if (!(screen instanceof ContainerScreen) || !screen.getTitle().getString().equals("Materials")) return;
                    check(screen.getTitle().getString().equals("Materials"), "Sell stays in category");
                    slot(client, 0, 1); next();
                }
                case 7 -> {
                    check(client.player.getInventory().countItem(Items.DIAMOND) == initialDiamonds, "Plain client sale removes one matching diamond");
                    client.gui.setScreen(null); client.getConnection().sendCommand("shop enchant"); next();
                }
                case 8 -> {
                    if (!(screen instanceof ContainerScreen) || !screen.getTitle().getString().equals("Enchanted Books")) return;
                    var holder = client.player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, net.minecraft.resources.Identifier.parse("minecraft:unbreaking")));
                    int offerSlot = -1;
                    for (int i = 0; i < 45; i++) {
                        var enchantments = client.player.containerMenu.getSlot(i).getItem().get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
                        if (enchantments != null && enchantments.getLevel(holder) > 0) { offerSlot = i; break; }
                    }
                    check(offerSlot >= 0, "Unbreaking offer present alongside other configured offers");
                    slot(client, offerSlot); next();
                }
                case 9 -> {
                    if (!(screen instanceof ContainerScreen) || !screen.getTitle().getString().equals("Unbreaking Levels")) return;
                    var holder = client.player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, net.minecraft.resources.Identifier.parse("minecraft:unbreaking")));
                    var icon = client.player.containerMenu.getSlot(4).getItem();
                    check(icon.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS).getLevel(holder) == 5, "Live player menu includes level V above vanilla limit");
                    Screenshot.grab(client.gameDirectory, "alpha5-player-enchantment-levels.png", client.gameRenderer.mainRenderTarget(), 1, ignored -> {});
                    slot(client, 4); next();
                }
                case 10 -> {
                    check(client.player.getInventory().countItem(Items.ENCHANTED_BOOK) == initialBooks + 1, "Plain client receives one purchased level V book");
                    var holder = client.player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, net.minecraft.resources.Identifier.parse("minecraft:unbreaking")));
                    boolean exact = false;
                    for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
                        var item = client.player.getInventory().getItem(i);
                        if (item.is(Items.ENCHANTED_BOOK) && item.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS).getLevel(holder) == 5) exact = true;
                    }
                    check(exact, "Delivered book retains exact level V"); next();
                }
                case 11 -> {
                    if (!(screen instanceof ContainerScreen) || !screen.getTitle().getString().equals("Unbreaking Levels")) return;
                    slot(client, 4, 1); next();
                }
                case 12 -> {
                    check(client.player.getInventory().countItem(Items.ENCHANTED_BOOK) == initialBooks, "Plain client sale consumes one exact level V book");
                    client.player.closeContainer(); subphase = 0; next();
                }
                case 13 -> {
                    // Let the container close finish before sending an asynchronously executed command.
                    if (subphase == 0) { client.getConnection().sendCommand("shop sell"); subphase = 1; delay = 25; return; }
                    if (!(screen instanceof ContainerScreen) || !screen.getTitle().getString().equals("Zycu's Shop")) return;
                    slot(client, 3); subphase = 0; next();
                }
                case 14 -> {
                    check(screen instanceof ContainerScreen && screen.getTitle().getString().equals("Materials"), "Return to Materials after close and reopen; screen=" + (screen == null ? "null" : screen.getTitle().getString()) + "; menu=" + client.player.containerMenu.containerId);
                    check(client.player.containerMenu.getSlot(53).getItem().isEmpty(), "Legacy sell alias has no mode switch");
                    var lines = tooltip(client, client.player.containerMenu.getSlot(2).getItem());
                    check(lines.stream().anyMatch(line -> line.startsWith("Buy: ")) && lines.stream().anyMatch(line -> line.startsWith("Sell: ")), "Native shop displays both prices");
                    check(!lines.contains("Original map lore"), "Native shop hides source lore");
                    slot(client, 2); next();
                }
                case 15 -> {
                    var map = find(client, Items.MAP);
                    check(tooltip(client, map).contains("Original map lore"), "Purchased empty map immediately retains original lore");
                    pristine(map); slot(client, 2, 1); next();
                }
                case 16 -> { check(client.player.getInventory().countItem(Items.MAP) == 0, "Right-click sells exact empty map without changing screens"); slot(client, 2); next(); }
                case 17 -> {
                    var map = find(client, Items.MAP); pristine(map);
                    int index = -1;
                    for (int i = 0; i < 9; i++) if (client.player.getInventory().getItem(i) == map) index = i;
                    check(index >= 0, "Empty map delivered into test hotbar");
                    client.player.closeContainer(); client.player.getInventory().setSelectedSlot(index);
                    client.gameMode.useItem(client.player, net.minecraft.world.InteractionHand.MAIN_HAND); next();
                }
                case 18 -> {
                    var map = find(client, Items.FILLED_MAP);
                    check(map.has(net.minecraft.core.component.DataComponents.MAP_ID), "Purchased empty map works with native right-click use");
                    pristine(map); client.getConnection().sendCommand("shop buy"); next();
                }
                case 19 -> { if (!(screen instanceof ContainerScreen) || !screen.getTitle().getString().equals("Zycu's Shop")) return; slot(client, 3); next(); }
                case 20 -> { if (!(screen instanceof ContainerScreen) || !screen.getTitle().getString().equals("Materials")) return; slot(client, 3); next(); }
                case 21 -> {
                    var map = find(client, Items.ABANDONED_CAMP_MAP);
                    check(map.has(net.minecraft.core.component.DataComponents.MAP_ID) && !map.get(net.minecraft.core.component.DataComponents.MAP_DECORATIONS).decorations().isEmpty(), "Purchased camp map immediately has saved identity and destination");
                    pristine(map); slot(client, 3, 1); next();
                }
                case 22 -> {
                    check(client.player.getInventory().countItem(Items.ABANDONED_CAMP_MAP) == 0, "Right-click sells generated camp map");
                    if (!FabricLoader.getInstance().isModLoaded("travelersbackpack")) { phase = 25; delay = 1; return; }
                    slot(client, 4); next();
                }
                case 23 -> {
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("travelersbackpack:end"));
                    var backpack = find(client, item);
                    check(net.minecraft.world.item.ItemStack.isSameItemSameComponents(backpack, new net.minecraft.world.item.ItemStack(item)), "Backpack immediately has all original components without rejoining");
                    check(tooltip(client, backpack).stream().anyMatch(line -> line.contains("Backpack Tier:")), "Native backpack tier tooltip appears immediately");
                    pristine(backpack); slot(client, 4, 1); next();
                }
                case 24 -> {
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("travelersbackpack:end"));
                    check(client.player.getInventory().countItem(item) == 0, "Right-click sells exact purchased backpack"); next();
                }
                case 25 -> { System.out.println("[Player UI test] PASS: client has no ClassicGUIShop; restart persistence; unified left-buy/right-sell and dual-price lore; exact level V purchase/sale; original empty-map lore; native empty-map use; functional camp-map identity and destination; immediate native End backpack components and tier tooltip without rejoin; clean cursor"); next(); }
                case 26 -> { done = true; client.stop(); }
            }
        } catch (Throwable failure) { done = true; System.err.println("[Player UI test] FAIL at phase " + phase); failure.printStackTrace(); client.stop(); }
    }
    private void next() { System.out.println("[Player UI test] Phase " + phase + " complete"); phase++; delay = 25; }
    private static void slot(Minecraft client, int slot) { slot(client, slot, 0); }
    private static void slot(Minecraft client, int slot, int button) {
        client.gameMode.handleContainerInput(client.player.containerMenu.containerId, slot, button, ContainerInput.PICKUP, client.player);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static net.minecraft.world.item.ItemStack find(Minecraft client, net.minecraft.world.item.Item item) {
        check(client.player.containerMenu.getCarried().isEmpty(), "Menu display never remains on predicted cursor");
        for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
            var stack = client.player.getInventory().getItem(i); if (stack.is(item)) return stack;
        }
        throw new AssertionError("Purchased item missing: " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item));
    }
    private static java.util.List<String> tooltip(Minecraft client, net.minecraft.world.item.ItemStack stack) {
        return stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(client.level), client.player, net.minecraft.world.item.TooltipFlag.NORMAL).stream().map(net.minecraft.network.chat.Component::getString).toList();
    }
    private static void pristine(net.minecraft.world.item.ItemStack stack) {
        check(java.util.Objects.equals(stack.get(net.minecraft.core.component.DataComponents.TOOLTIP_DISPLAY), new net.minecraft.world.item.ItemStack(stack.getItem()).get(net.minecraft.core.component.DataComponents.TOOLTIP_DISPLAY)), "Purchase retains native tooltip visibility instead of shop mask");
        var lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
        check(lore == null || lore.lines().stream().map(net.minecraft.network.chat.Component::getString).noneMatch(line -> line.startsWith("Buy:") || line.startsWith("Sell:") || line.contains("Display only")), "Shop lore never delivered");
    }
}
