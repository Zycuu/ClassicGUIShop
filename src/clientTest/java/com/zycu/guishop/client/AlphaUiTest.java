package com.zycu.guishop.client;

import com.zycu.guishop.admin.AdminDocument;
import com.zycu.guishop.admin.AdminEditorService;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import java.lang.reflect.Field;

/** Drives the actual rendered client against a disposable loopback server. */
public final class AlphaUiTest implements ClientModInitializer {
    private int phase, delay = 80, age, subphase;
    private boolean done;
    @Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }
    private void tick(Minecraft client) {
        if (done) return;
        try {
            if (++age > 6000) throw new AssertionError("UI test timed out at phase " + phase);
            if (delay-- > 0) return;
            Screen screen = client.gui.screen();
            switch (phase) {
                case 0 -> {
                    if (client.gui.overlay() != null || screen == null) return;
                    client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                    client.gui.toastManager().clear();
                    ConnectScreen.startConnecting(screen, client, ServerAddress.parseString("127.0.0.1:25573"),
                        new ServerData("Alpha test", "127.0.0.1:25573", ServerData.Type.OTHER), false, null);
                    next();
                }
                case 1 -> {
                    if (client.player == null) return;
                    System.out.println("[Alpha UI test] PLAYER: " + client.player.getName().getString());
                    client.gui.setScreen(null); openKey(); next();
                }
                case 2 -> {
                    if (!(screen instanceof AdminNoticeScreen)) return;
                    check(get(screen, "message").equals(AdminEditorService.DENIED), "Exact permission popup message");
                    capture(client, "alpha2-permission-denied.png");
                    System.out.println("[Alpha UI test] DENIED_READY"); next(); delay = 60;
                }
                case 3 -> {
                    if (!(screen instanceof AdminScreen)) { client.gui.setScreen(null); openKey(); delay = 25; return; }
                    click(screen, "+ Category"); next();
                }
                case 4 -> { set(screen, "Name", "Materials"); click(screen, "Create"); next(); }
                case 5 -> {
                    check(screen.children().stream().noneMatch(widget -> widget instanceof Button button && button.getMessage().getString().contains("Folder")), "Folder controls removed");
                    check(((AdminDocument) get(screen, "draft")).config.category("materials") != null, "Category created"); next();
                }
                case 6 -> { check(hitCount(screen, "tile:item") == 0, "New category opens directly to listings"); next(); }
                case 7 -> { click(screen, "Choose icon..."); next(); }
                case 8 -> { set(screen, "Search items", "minecraft:emerald"); at((WorkbenchScreen) screen, 92, 120, false); next(); }
                case 9 -> { click(screen, "+ Add held item"); next(); }
                case 10 -> {
                    if (get(screen, "selectedItem") == null) return;
                    if (subphase == 0) { set(screen, "Buy price", "12.5"); set(screen, "Sell price", "3.1"); set(screen, "Tooltip description", "A little sparkle for your next adventure."); subphase = 1; delay = 4; return; }
                    subphase = 0; capture(client, "alpha2-shop-builder.png"); click(screen, "+ Category"); next();
                }
                case 11 -> { set(screen, "Name", "Building"); click(screen, "Create"); next(); }
                case 12 -> { click(screen, "+ Select item"); next(); }
                case 13 -> { set(screen, "Search items", "minecraft:stone"); at((WorkbenchScreen) screen, 92, 120, false); next(); }
                case 14 -> { set(screen, "Buy price", "2"); set(screen, "Sell price", "0.5"); at((WorkbenchScreen) screen, 70, 160, false); next(); }
                case 15 -> { click(screen, "+ Select item"); next(); }
                case 16 -> { set(screen, "Search items", "minecraft:cobblestone"); at((WorkbenchScreen) screen, 92, 120, false); next(); }
                case 17 -> {
                    if (subphase == 0) { set(screen, "Buy price", "1"); set(screen, "Sell price", "0.25"); at((WorkbenchScreen) screen, 249, 169, true); subphase = 1; delay = 4; return; }
                    var workbench = (WorkbenchScreen) screen;
                    workbench.mouseDragged(event(workbench, 231, 169), -18 * workbench.scale, 0);
                    workbench.mouseReleased(event(workbench, 231, 169)); subphase = 0; next();
                }
                case 18 -> {
                    AdminDocument draft = (AdminDocument) get(screen, "draft");
                    check(draft.config.category("building").items.getFirst().item.equals("minecraft:cobblestone"), "Drag reorder");
                    at((WorkbenchScreen) screen, 249, 169, false); click(screen, "Move listing..."); next();
                }
                case 19 -> { click(screen, "Materials"); next(); }
                case 20 -> {
                    if (subphase == 0) {
                        AdminDocument draft = (AdminDocument) get(screen, "draft");
                        check(draft.config.findItem("minecraft:stone").category().id.equals("materials"), "Move into category");
                        click(screen, "Settings"); set(screen, "Shop title", "Zycu's Shop"); click(screen, "Enchantments"); click(screen, "+ Offer"); subphase = 1; delay = 4; return;
                    }
                    if (subphase == 1) { set(screen, "Name", "minecraft:unbreaking"); click(screen, "Create"); subphase = 2; delay = 4; return; }
                    AdminDocument draft = (AdminDocument) get(screen, "draft");
                    check(draft.config.enchantments.containsKey("minecraft:unbreaking"), "Explicit enchantment offer");
                    set(screen, "minecraft:unbreaking", "Unbreaking"); set(screen, "Price / level", "120"); subphase = 0;
                    click(screen, "Apply"); next();
                }
                case 21 -> {
                    if ((boolean) get(screen, "busy")) return;
                    check(((String) get(screen, "status")).startsWith("Applied on the server"), "Server acknowledged Apply: " + get(screen, "status"));
                    if (subphase == 0) { click(screen, "Shop Builder"); click(screen, "Home"); subphase = 1; delay = 4; return; }
                    check(hitCount(screen, "tile:enchant") == 1, "Enchantment entry survives Build/Simulate toggle " + subphase);
                    if (subphase < 4) { click(screen, (boolean) get(screen, "simulate") ? "Simulate" : "Build"); subphase++; delay = 4; return; }
                    capture(client, "alpha2-shop-simulator.png"); subphase = 0; next();
                }
                case 22 -> {
                    if (subphase == 0) { at((WorkbenchScreen) screen, 321, 169, false); subphase = 1; delay = 4; return; }
                    if (subphase == 1) { check((boolean) get(screen, "enchantPreview"), "Enchantment simulator"); at((WorkbenchScreen) screen, 231, 169, false); subphase = 2; delay = 4; return; }
                    if (subphase >= 2 && subphase <= 4) {
                        check(get(screen, "enchantId").equals("minecraft:unbreaking"), "Enchantment selection survives toggle");
                        check((boolean) get(screen, "enchantPreview") && hitCount(screen, "tile:level") == 3, "Level preview remains visible in both modes");
                        if (subphase < 4) { click(screen, (boolean) get(screen, "simulate") ? "Simulate" : "Build"); subphase++; delay = 4; return; }
                        capture(client, "alpha2-enchantment-simulator.png"); click(screen, "Settings"); click(screen, "Economy"); subphase = 5; delay = 4; return;
                    }
                    check(((AdminDocument) get(screen, "draft")).config.shopTitle.equals("Zycu's Shop"), "Presentation persisted");
                    capture(client, "alpha2-server-settings.png"); subphase = 0; next();
                }
                case 23 -> { click(screen, "Transactions"); click(screen, "Changes"); click(screen, "Transactions"); next(); }
                case 24 -> {
                    if (subphase == 0) {
                        var page = (com.zycu.guishop.admin.TransactionJournal.Page) get(screen, "logData");
                        if (page.entries().isEmpty()) return;
                        check(page.entries().stream().noneMatch(entry -> entry.action().equals("CONFIG_APPLY")), "Fast tab switching keeps transaction data separate");
                        capture(client, "alpha2-transaction-journal.png"); at((WorkbenchScreen) screen, 100, 164, false); subphase = 1; delay = 4; return;
                    }
                    if (subphase == 1) { check(screen instanceof JournalEntryScreen, "Transaction detail window"); click(screen, "Back"); click(client.gui.screen(), "Changes"); subphase = 2; delay = 20; return; }
                    if (subphase == 2) {
                        var page = (com.zycu.guishop.admin.TransactionJournal.Page) get(screen, "logData"); if (page.entries().isEmpty()) return;
                        capture(client, "alpha2-configuration-history.png"); at((WorkbenchScreen) screen, 100, 164, false); subphase = 3; delay = 4; return;
                    }
                    if (subphase == 3) { click(screen, "Restore draft..."); subphase = 4; delay = 4; return; }
                    if (subphase == 4) { click(screen, "Confirm"); subphase = 5; delay = 20; return; }
                    if (subphase == 5) {
                        if (!(screen instanceof AdminScreen)) return;
                        check(((String) get(screen, "status")).startsWith("Previous configuration loaded"), "Restore loads a reviewable draft");
                        click(screen, "Discard"); subphase = 6; delay = 4; return;
                    }
                    if (subphase == 6) { click(screen, "Confirm"); subphase = 7; delay = 4; return; }
                    check(((AdminDocument) get(screen, "draft")).config.shopTitle.equals("Zycu's Shop"), "Discard restore preserves live configuration");
                    click(screen, "Close"); client.getConnection().sendCommand("shop"); subphase = 0; next();
                }
                case 25 -> {
                    if (!(screen instanceof ContainerScreen)) return;
                    capture(client, "alpha2-vanilla-player-shop.png"); client.gui.setScreen(null);
                    System.out.println("[Alpha UI test] REVOKE_READY"); next(); delay = 60;
                }
                case 26 -> { openKey(); next(); }
                case 27 -> {
                    if (!(screen instanceof AdminNoticeScreen)) return;
                    check(get(screen, "message").equals(AdminEditorService.DENIED), "Revoked OP receives exact popup");
                    System.out.println("[Alpha UI test] PASS: real keybind; permission popup; category creation with no folder controls; icon selection; exact held-item import; pricing; drag reorder; cross-category move; server Apply; presentation; item and enchantment simulator with repeated Build toggles; settings; journal; vanilla player shop; revoked permissions");
                    next(); delay = 30;
                }
                case 28 -> { done = true; client.stop(); }
            }
        } catch (Throwable failure) {
            done = true; System.err.println("[Alpha UI test] FAIL at phase " + phase); failure.printStackTrace(); client.stop();
        }
    }
    private void next() { System.out.println("[Alpha UI test] Phase " + phase + " complete"); phase++; delay = 20; }
    private static void openKey() { KeyMapping.click(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F8)); }
    private static Object get(Object object, String name) throws Exception { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
    private static void set(Screen screen, String label, String value) {
        for (var widget : screen.children()) if (widget instanceof EditBox input && input.getMessage().getString().equals(label)) { input.setValue(value); return; }
        throw new AssertionError("Missing field " + label + " on " + screen.getClass());
    }
    private static void click(Screen screen, String label) {
        for (var widget : screen.children()) if (widget instanceof Button button && button.getMessage().getString().replace("* ", "").equals(label)) {
            at((WorkbenchScreen) screen, button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0, false); return;
        }
        throw new AssertionError("Missing button " + label + " on " + screen.getClass());
    }
    private static MouseButtonEvent event(WorkbenchScreen screen, double x, double y) {
        screen.geometry(); return new MouseButtonEvent(screen.originX + x * screen.scale, screen.originY + y * screen.scale, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
    }
    private static void at(WorkbenchScreen screen, double x, double y, boolean down) {
        var event = event(screen, x, y); screen.mouseClicked(event, false); if (!down) screen.mouseReleased(event);
    }
    private static void drag(WorkbenchScreen screen, double x, double y, double tx, double ty) {
        at(screen, x, y, true); screen.mouseDragged(event(screen, tx, ty), (tx - x) * screen.scale, (ty - y) * screen.scale); screen.mouseReleased(event(screen, tx, ty));
    }
    private static void capture(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name, client.gameRenderer.mainRenderTarget(), 1,
            message -> System.out.println("[Alpha UI test] Screenshot " + name + ": " + message.getString()));
    }
    private static long hitCount(Screen screen, String kind) throws Exception {
        long count = 0;
        for (Object hit : (java.util.List<?>) get(screen, "hits")) if (get(hit, "kind").equals(kind)) count++;
        return count;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
