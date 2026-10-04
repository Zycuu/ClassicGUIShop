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
    private String selectedHistoryUuid;
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
                    capture(client, "alpha4-permission-denied.png");
                    System.out.println("[Alpha UI test] DENIED_READY"); next(); delay = 60;
                }
                case 3 -> {
                    if (subphase == 0 && !(screen instanceof AdminScreen)) { client.gui.setScreen(null); openKey(); delay = 25; return; }
                    if (subphase == 0) { click(screen, "Detected Content"); subphase = 1; delay = 10; return; }
                    if (subphase == 1) {
                        var page = (com.zycu.guishop.admin.DetectedContent.Page) get(screen, "detectedData"); if (!page.ready()) return;
                        nativeHit(client, (WorkbenchScreen) screen, "detected:source", "moldomre"); nativeButton(client, true); nativeButton(client, false);
                        subphase = 2; delay = 10; return;
                    }
                    if (subphase == 2) {
                        var page = (com.zycu.guishop.admin.DetectedContent.Page) get(screen, "detectedData");
                        if (page.entries().isEmpty() || !page.entries().getFirst().source().equals("moldomre")) return;
                        click(screen, "Import source"); subphase = 3; delay = 4; return;
                    }
                    if (subphase == 3) { click(screen, "Confirm"); subphase = 4; delay = 0; return; }
                    if (subphase == 4) {
                        if ((int) get(screen, "importAdded") == 0) return;
                        check(get(screen, "importSource") != null, "Bulk import is still running during cancellation check");
                        click(screen, "Cancel import"); click(screen, "Discard"); subphase = 5; delay = 4; return;
                    }
                    if (subphase == 5) { click(screen, "Confirm"); subphase = 6; delay = 12; return; }
                    if (subphase == 6) {
                        check(((AdminDocument) get(screen, "draft")).config.categories.isEmpty(), "Discarded import remains empty after pending responses arrive");
                        check(get(screen, "importSource") == null && (int) get(screen, "detectionRetry") == 0, "Cancellation stops subsequent import batches");
                        click(screen, "Shop Builder"); subphase = 7; delay = 4; return;
                    }
                    click(screen, "+ Category"); subphase = 0; next();
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
                    subphase = 0; capture(client, "alpha4-shop-builder.png"); click(screen, "+ Category"); next();
                }
                case 11 -> { set(screen, "Name", "Building"); click(screen, "Create"); next(); }
                case 12 -> { click(screen, "+ Select item"); next(); }
                case 13 -> { set(screen, "Search items", "minecraft:stone"); at((WorkbenchScreen) screen, 92, 120, false); next(); }
                case 14 -> { set(screen, "Buy price", "2"); set(screen, "Sell price", "0.5"); at((WorkbenchScreen) screen, 70, 160, false); next(); }
                case 15 -> { click(screen, "+ Select item"); next(); }
                case 16 -> { set(screen, "Search items", "minecraft:cobblestone"); at((WorkbenchScreen) screen, 92, 120, false); next(); }
                case 17 -> {
                    if (subphase == 0) {
                        set(screen, "Buy price", "1"); set(screen, "Sell price", "0.25");
                        check((boolean) get(screen, "arranging"), "Adding disabled listings enables arrange mode");
                        nativeMove(client, (WorkbenchScreen) screen, 249, 169); nativeButton(client, true);
                        check(get(screen, "selectedItem").equals("minecraft:cobblestone"), "Mouse-down does not select the underlying destination");
                        subphase = 1; delay = 1; return;
                    }
                    // Slow, cumulative one-pixel movement through Minecraft's actual MouseHandler.
                    if (subphase <= 18) { nativeMove(client, (WorkbenchScreen) screen, 249 - subphase, 169); subphase++; delay = 1; return; }
                    nativeButton(client, false); subphase = 0; next();
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
                        at((WorkbenchScreen) screen, 70, 160, false); click(screen, "+ Select item"); subphase = 10; delay = 4; return;
                    }
                    if (subphase == 10) { set(screen, "Search items", "minecraft:stone"); at((WorkbenchScreen) screen, 92, 120, false); subphase = 11; delay = 4; return; }
                    if (subphase == 11) {
                        AdminDocument draft = (AdminDocument) get(screen, "draft");
                        check(get(screen, "categoryId").equals("building"), "Adding existing item stays in chosen category");
                        var duplicate = draft.config.category("building").items.stream().filter(item -> item.item.equals("minecraft:stone")).findFirst().orElseThrow();
                        check(!duplicate.listingId.equals("minecraft:stone") && duplicate.buy == 0 && duplicate.sell == 0, "Separate duplicate listing starts disabled");
                        check(draft.config.findItem("minecraft:stone").category().id.equals("materials"), "Original listing remains in place");
                        capture(client, "alpha4-duplicate-listing.png"); click(screen, "Detected Content"); subphase = 12; delay = 15; return;
                    }
                    if (subphase == 12) {
                        var page = (com.zycu.guishop.admin.DetectedContent.Page) get(screen, "detectedData"); if (!page.ready()) return;
                        check(page.sources().stream().anyMatch(source -> source.id().equals("moldomre") && source.count() >= 189), "Actual pack variants detected");
                        nativeHit(client, (WorkbenchScreen) screen, "detected:source", "moldomre"); nativeButton(client, true); nativeButton(client, false);
                        subphase = 13; delay = 15; return;
                    }
                    if (subphase == 13) {
                        var page = (com.zycu.guishop.admin.DetectedContent.Page) get(screen, "detectedData");
                        if (page.entries().isEmpty() || !page.entries().getFirst().source().equals("moldomre")) return;
                        capture(client, "alpha4-detected-content.png");
                        at((WorkbenchScreen) screen, 210, 152, false); click(screen, "Import item"); subphase = 14; delay = 4; return;
                    }
                    if (subphase == 14) {
                        AdminDocument draft = (AdminDocument) get(screen, "draft");
                        var category = draft.config.categories.stream().filter(value -> "moldomre".equals(value.detectedSource)).findFirst().orElseThrow();
                        check(category.items.size() == 1 && category.items.getFirst().buy == 0 && category.items.getFirst().sell == 0, "Single detected item imported disabled into own category");
                        click(screen, "Import item"); check(category.items.size() == 1, "Repeated exact import does not duplicate source-category listing");
                        click(screen, "Import source"); subphase = 15; delay = 4; return;
                    }
                    if (subphase == 15) { click(screen, "Confirm"); subphase = 16; delay = 20; return; }
                    if (subphase == 16) {
                        if (get(screen, "importSource") != null) return;
                        AdminDocument draft = (AdminDocument) get(screen, "draft");
                        var category = draft.config.categories.stream().filter(value -> "moldomre".equals(value.detectedSource)).findFirst().orElseThrow();
                        check(category.items.size() >= 189 && category.items.stream().allMatch(item -> item.buy == 0 && item.sell == 0 && Boolean.TRUE.equals(item.manualPrice) && item.stack != null), "Whole-source import retains variants with manual zero prices");
                        click(screen, "Shop Builder");
                        subphase = 17; delay = 4; return;
                    }
                    if (subphase == 17) {
                        AdminDocument draft = (AdminDocument) get(screen, "draft");
                        var category = draft.config.categories.stream().filter(value -> "moldomre".equals(value.detectedSource)).findFirst().orElseThrow();
                        nativeHit(client, (WorkbenchScreen) screen, "category", category.id); nativeButton(client, true); nativeButton(client, false);
                        subphase = 18; delay = 4; return;
                    }
                    if (subphase == 18) {
                        check(hitCount(screen, "tile:item") > 0, "Disabled imported variants are visible in arrange mode");
                        capture(client, "alpha4-imported-pack-variants.png");
                        if (net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().stream().anyMatch(id -> id.getNamespace().equals("usefulfood"))) {
                            click(screen, "Detected Content"); subphase = 19; delay = 10; return;
                        }
                        startSettings(screen); return;
                    }
                    if (subphase == 19) {
                        var page = (com.zycu.guishop.admin.DetectedContent.Page) get(screen, "detectedData"); if (!page.ready()) return;
                        check(page.sources().stream().anyMatch(source -> source.id().equals("usefulfood") && source.name().equals("UsefulFood Reborn")), "UsefulFood named source detected");
                        nativeHit(client, (WorkbenchScreen) screen, "detected:source", "usefulfood"); nativeButton(client, true); nativeButton(client, false);
                        subphase = 20; delay = 10; return;
                    }
                    if (subphase == 20) {
                        var page = (com.zycu.guishop.admin.DetectedContent.Page) get(screen, "detectedData");
                        if (page.entries().isEmpty() || !page.entries().getFirst().source().equals("usefulfood")) return;
                        capture(client, "alpha4-detected-usefulfood.png"); click(screen, "Import source"); subphase = 21; delay = 4; return;
                    }
                    if (subphase == 21) { click(screen, "Confirm"); subphase = 22; delay = 15; return; }
                    if (subphase == 22) {
                        if (get(screen, "importSource") != null) return;
                        AdminDocument draft = (AdminDocument) get(screen, "draft");
                        var category = draft.config.categories.stream().filter(value -> "usefulfood".equals(value.detectedSource)).findFirst().orElseThrow();
                        long expected = net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().stream().filter(id -> id.getNamespace().equals("usefulfood")).count();
                        check(category.items.size() == expected && category.items.stream().allMatch(item -> item.buy == 0 && item.sell == 0 && Boolean.TRUE.equals(item.manualPrice)), "All UsefulFood registry items imported with zero prices");
                        click(screen, "Shop Builder"); subphase = 23; delay = 4; return;
                    }
                    if (subphase == 23) {
                        AdminDocument draft = (AdminDocument) get(screen, "draft");
                        var category = draft.config.categories.stream().filter(value -> "usefulfood".equals(value.detectedSource)).findFirst().orElseThrow();
                        nativeHit(client, (WorkbenchScreen) screen, "category", category.id); nativeButton(client, true); nativeButton(client, false);
                        subphase = 24; delay = 4; return;
                    }
                    if (subphase == 24) {
                        capture(client, "alpha4-imported-usefulfood.png"); startSettings(screen); return;
                    }
                    if (subphase == 1) { set(screen, "Name", "minecraft:unbreaking"); click(screen, "Create"); subphase = 2; delay = 4; return; }
                    AdminDocument draft = (AdminDocument) get(screen, "draft");
                    check(draft.config.enchantments.containsKey("minecraft:unbreaking"), "Explicit enchantment offer");
                    set(screen, "minecraft:unbreaking", "Unbreaking"); set(screen, "Price / level", "120"); subphase = 0;
                    click(screen, "Apply"); next();
                }
                case 21 -> {
                    if ((boolean) get(screen, "busy")) return;
                    if (subphase == 0) {
                        check(((String) get(screen, "status")).startsWith("Applied on the server"), "Server acknowledged Apply");
                        click(screen, "Shop Builder"); click(screen, "Home"); subphase = 1; delay = 4; return;
                    }
                    check(hitCount(screen, "tile:enchant") == 1, "Enchantment entry stays visible during arrange toggles");
                    check(screen.children().stream().noneMatch(widget -> widget instanceof Button button && java.util.List.of("Build", "Simulate", "Buy", "Sell").contains(button.getMessage().getString())), "Obsolete mode controls removed");
                    if (subphase == 1) {
                        nativeHit(client, (WorkbenchScreen) screen, "tile:category", "building"); nativeButton(client, true);
                        check(get(screen, "categoryId").equals(""), "Category mouse-down defers navigation"); subphase = 2; delay = 4; return;
                    }
                    if (subphase == 2) { nativeHit(client, (WorkbenchScreen) screen, "tile:category", "materials"); nativeButton(client, false); subphase = 3; delay = 4; return; }
                    if (subphase == 3) {
                        check(((AdminDocument) get(screen, "draft")).config.categories.getFirst().id.equals("building"), "Category drag reorders without entering it");
                        check(get(screen, "categoryId").equals(""), "Category drag leaves root open");
                        nativeHit(client, (WorkbenchScreen) screen, "tile:category", "building"); nativeButton(client, true); subphase = 4; delay = 4; return;
                    }
                    if (subphase == 4) { nativeHit(client, (WorkbenchScreen) screen, "tile:category", "materials"); nativeButton(client, false); subphase = 5; delay = 4; return; }
                    if (subphase == 5) {
                        check(((AdminDocument) get(screen, "draft")).config.categories.getFirst().id.equals("materials"), "Category order restored by drag");
                        click(screen, "Exit arrange mode"); subphase = 6; delay = 4; return;
                    }
                    capture(client, "alpha4-shop-simulator.png"); subphase = 0; next();
                }
                case 22 -> {
                    if (subphase == 0) { nativeHit(client, (WorkbenchScreen) screen, "tile:enchant", ""); nativeButton(client, true); nativeButton(client, false); subphase = 1; delay = 4; return; }
                    if (subphase == 1) { check((boolean) get(screen, "enchantPreview"), "Enchantment simulator"); at((WorkbenchScreen) screen, 231, 169, false); subphase = 2; delay = 4; return; }
                    if (subphase >= 2 && subphase <= 4) {
                        check(get(screen, "enchantId").equals("minecraft:unbreaking"), "Enchantment selection survives arrange toggle");
                        check((boolean) get(screen, "enchantPreview") && hitCount(screen, "tile:level") == 3, "Level preview remains visible");
                        if (subphase < 4) { click(screen, (boolean) get(screen, "arranging") ? "Exit arrange mode" : "Arrange mode"); subphase++; delay = 4; return; }
                        // Buy/Sell is controlled exclusively by the vanilla-style footer inside the simulator.
                        at((WorkbenchScreen) screen, 375, 259, false); subphase = 5; delay = 4; return;
                    }
                    if (subphase == 5) {
                        check((boolean) get(screen, "sellMode"), "In-shop footer switches to Sell");
                        check(get(screen, "enchantId").equals("minecraft:unbreaking"), "Sell keeps enchantment selection");
                        capture(client, "alpha4-enchantment-simulator.png"); at((WorkbenchScreen) screen, 375, 259, false); subphase = 6; delay = 4; return;
                    }
                    if (subphase == 6) { check(!(boolean) get(screen, "sellMode"), "In-shop footer switches back to Buy"); click(screen, "Settings"); click(screen, "Economy"); subphase = 7; delay = 4; return; }
                    capture(client, "alpha4-server-settings.png"); subphase = 0; next();
                }
                case 23 -> { click(screen, "Transactions"); click(screen, "Changes"); click(screen, "Transactions"); next(); }
                case 24 -> {
                    if (subphase == 0) {
                        var roster = (com.zycu.guishop.admin.AdminPlayers.Page) get(screen, "playerData");
                        if (roster.players().isEmpty()) return;
                        check(get(screen, "selectedPlayer") == null, "Transactions opens player selector");
                        int row = -1;
                        for (int i = 0; i < Math.min(10, roster.players().size()); i++) if (roster.players().get(i).name().equalsIgnoreCase("ShopTest")) row = i;
                        check(row >= 0, "Known offline player appears by name");
                        selectedHistoryUuid = roster.players().get(row).uuid();
                        check(roster.players().get(row).balance() >= 0, "Current balance provided by server");
                        capture(client, "alpha4-player-balances.png"); at((WorkbenchScreen) screen, 100, 164 + row * 14, false); subphase = 10; delay = 20; return;
                    }
                    if (subphase == 10) {
                        var page = (com.zycu.guishop.admin.TransactionJournal.Page) get(screen, "logData");
                        if (page.entries().isEmpty()) return;
                        check(page.entries().stream().allMatch(entry -> entry.uuid().equals(selectedHistoryUuid) && !entry.action().equals("CONFIG_APPLY")), "Only selected player's transactions shown");
                        check(((com.zycu.guishop.admin.AdminPlayers.Player) get(screen, "selectedPlayer")).uuid().equals(selectedHistoryUuid), "Selected player's balance stays attached to history");
                        capture(client, "alpha4-transaction-journal.png"); at((WorkbenchScreen) screen, 100, 164, false); subphase = 1; delay = 4; return;
                    }
                    if (subphase == 1) { check(screen instanceof JournalEntryScreen, "Transaction detail window"); click(screen, "Back"); click(client.gui.screen(), "Changes"); subphase = 2; delay = 20; return; }
                    if (subphase == 2) {
                        var page = (com.zycu.guishop.admin.TransactionJournal.Page) get(screen, "logData"); if (page.entries().isEmpty()) return;
                        capture(client, "alpha4-configuration-history.png"); at((WorkbenchScreen) screen, 100, 164, false); subphase = 3; delay = 4; return;
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
                    capture(client, "alpha4-vanilla-player-shop.png"); client.gui.setScreen(null);
                    System.out.println("[Alpha UI test] REVOKE_READY"); next(); delay = 60;
                }
                case 26 -> { openKey(); next(); }
                case 27 -> {
                    if (!(screen instanceof AdminNoticeScreen)) return;
                    check(get(screen, "message").equals(AdminEditorService.DENIED), "Revoked OP receives exact popup");
                    System.out.println("[Alpha UI test] PASS: real keybind; permission popup; category creation with no folder controls; icon selection; exact held-item import; pricing; native slow mouse drag and category reorder; cross-category move; duplicate addition retains destination; real pack detection; individual and whole-source imports with zero prices; server Apply; presentation; arrange mode and in-shop Buy/Sell footer; settings; player names and balances; exact player history; journal; vanilla player shop; revoked permissions");
                    next(); delay = 30;
                }
                case 28 -> { done = true; client.stop(); }
            }
        } catch (Throwable failure) {
            done = true; System.err.println("[Alpha UI test] FAIL at phase " + phase); failure.printStackTrace(); client.stop();
        }
    }
    private void next() { System.out.println("[Alpha UI test] Phase " + phase + " complete"); phase++; delay = 20; }
    private void startSettings(Screen screen) {
        click(screen, "Settings"); set(screen, "Shop title", "Zycu's Shop"); click(screen, "Enchantments"); click(screen, "+ Offer"); subphase = 1; delay = 4;
    }
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
    private static void nativeMove(Minecraft client, WorkbenchScreen screen, double x, double y) {
        screen.geometry(); var window = client.getWindow();
        double px = (screen.originX + x * screen.scale) * window.getScreenWidth() / window.getGuiScaledWidth();
        double py = (screen.originY + y * screen.scale) * window.getScreenHeight() / window.getGuiScaledHeight();
        client.mouseHandler.onMove(window.handle(), px, py, 0, 0); client.mouseHandler.handleAccumulatedMovement();
    }
    private static void nativeHit(Minecraft client, WorkbenchScreen screen, String kind, String id) throws Exception {
        for (Object hit : (java.util.List<?>) get(screen, "hits")) if (get(hit, "kind").equals(kind) && get(hit, "id").equals(id)) {
            nativeMove(client, screen, (int) get(hit, "x") + (int) get(hit, "w") / 2.0, (int) get(hit, "y") + (int) get(hit, "h") / 2.0); return;
        }
        throw new AssertionError("Missing hit " + kind + " / " + id);
    }
    private static void nativeButton(Minecraft client, boolean down) {
        client.mouseHandler.onButton(client.getWindow().handle(), new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0), down ? 1 : 0);
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
