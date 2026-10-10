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
    private String fixturePack;
    private String rewardReference;
    private String pickerVariant, pickerModItem;
    private int originalGuiScale, themeScale;
    @Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }
    private void tick(Minecraft client) {
        if (done) return;
        try {
            client.gui.toastManager().clear();
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
                    client.gui.setScreen(null); client.getConnection().sendCommand("adminshop"); next();
                }
                case 2 -> {
                    if (!(screen instanceof AdminNoticeScreen)) return;
                    check(get(screen, "message").equals(AdminEditorService.DENIED), "Exact permission popup message");
                    capture(client, "alpha9-permission-denied.png");
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
                        nativeHit(client, (WorkbenchScreen) screen, "detected:source", "minecraft"); nativeButton(client, true); nativeButton(client, false);
                        subphase = 7; delay = 10; return;
                    }
                    if (subphase == 7) {
                        var page = (com.zycu.guishop.admin.DetectedContent.Page) get(screen, "detectedData");
                        if (page.entries().isEmpty() || !page.entries().getFirst().source().equals("minecraft")) return;
                        set(screen, "Search detected items", "minecraft:diamond"); click(screen, "Search"); subphase = 8; delay = 10; return;
                    }
                    if (subphase == 8) {
                        var page = (com.zycu.guishop.admin.DetectedContent.Page) get(screen, "detectedData");
                        if (page.entries().isEmpty() || !page.entries().getFirst().listing().item.equals("minecraft:diamond")) return;
                        at((WorkbenchScreen) screen, 210, 152, false); click(screen, "Import item");
                        var vanilla = ((AdminDocument) get(screen, "draft")).config.categories.stream().filter(value -> "minecraft".equals(value.detectedSource)).findFirst().orElseThrow();
                        check(vanilla.items.size() == 1 && vanilla.items.getFirst().buy == 0 && vanilla.items.getFirst().sell == 0, "Vanilla detected item imports disabled");
                        capture(client, "alpha9-detected-vanilla.png");
                        fixturePack = page.sources().stream().filter(source -> source.name().contains("guishop-detection-fixture.zip")).findFirst().orElseThrow().id();
                        subphase = 84; delay = 4; return;
                    }
                    if (subphase == 84) {
                        nativeHit(client, (WorkbenchScreen) screen, "detected:source", fixturePack); nativeButton(client, true); nativeButton(client, false);
                        subphase = 9; delay = 10; return;
                    }
                    if (subphase == 9) {
                        var page = (com.zycu.guishop.admin.DetectedContent.Page) get(screen, "detectedData");
                        if (page.entries().size() != 2 || !page.entries().getFirst().packs().contains(fixturePack)) return;
                        set(screen, "Search detected items", "Second Namespace"); click(screen, "Search"); subphase = 10; delay = 10; return;
                    }
                    if (subphase == 10) {
                        var page = (com.zycu.guishop.admin.DetectedContent.Page) get(screen, "detectedData"); if (page.entries().size() != 1) return;
                        capture(client, "alpha9-import-pack.png"); click(screen, "Import pack"); subphase = 11; delay = 4; return;
                    }
                    if (subphase == 11) { click(screen, "Confirm"); subphase = 12; delay = 10; return; }
                    if (subphase == 12) {
                        if (get(screen, "importSource") != null) return;
                        var draft = (AdminDocument) get(screen, "draft");
                        var pack = draft.config.categories.stream().filter(value -> fixturePack.equals(value.detectedSource)).findFirst().orElseThrow();
                        check(pack.items.size() == 2 && draft.config.categories.size() == 2, "Multi-namespace pack imports to exactly one category, ignoring search");
                        check(pack.items.stream().allMatch(item -> item.buy == 0 && item.sell == 0), "Whole pack starts disabled");
                        pack.items.getFirst().buy = 5; pack.items.getFirst().sell = 1;
                        click(screen, "Import pack"); subphase = 13; delay = 4; return;
                    }
                    if (subphase == 13) { click(screen, "Confirm"); subphase = 14; delay = 10; return; }
                    if (subphase == 14) {
                        if (get(screen, "importSource") != null) return;
                        var draft = (AdminDocument) get(screen, "draft");
                        var pack = draft.config.categories.stream().filter(value -> fixturePack.equals(value.detectedSource)).findFirst().orElseThrow();
                        check(pack.items.size() == 2 && pack.items.getFirst().buy == 5 && pack.items.getFirst().sell == 1, "Repeated whole-pack import preserves category and prices");
                        nativeHit(client, (WorkbenchScreen) screen, "detected:source", ""); nativeButton(client, true); nativeButton(client, false);
                        subphase = 15; delay = 10; return;
                    }
                    if (subphase == 15) { click(screen, "Import all"); subphase = 16; delay = 4; return; }
                    if (subphase == 16) { click(screen, "Confirm"); subphase = 17; delay = 10; return; }
                    if (subphase == 17) {
                        if (get(screen, "importSource") != null) return;
                        var draft = (AdminDocument) get(screen, "draft");
                        var all = draft.config.categories.stream().filter(value -> "all_detected".equals(value.detectedSource)).findFirst().orElseThrow();
                        check(draft.config.categories.size() == 3 && all.items.stream().allMatch(item -> item.buy == 0 && item.sell == 0), "Import all uses one additional category with zero prices");
                        check(all.items.stream().anyMatch(item -> item.item.equals("minecraft:diamond")) && all.items.stream().anyMatch(item -> item.listingId.contains("#")), "Import all includes vanilla and exact pack variants across all pages");
                        check(all.items.stream().map(item -> item.stack.toString()).distinct().count() == all.items.size(), "Import all has no duplicate exact stacks");
                        capture(client, "alpha9-import-all.png"); click(screen, "Discard"); subphase = 18; delay = 4; return;
                    }
                    if (subphase == 18) { click(screen, "Confirm"); subphase = 19; delay = 12; return; }
                    if (subphase == 19) {
                        check(((AdminDocument) get(screen, "draft")).config.categories.isEmpty(), "Import test drafts discarded");
                        click(screen, "Close"); subphase = 191; delay = 4; return;
                    }
                    if (subphase == 191) { client.getConnection().sendCommand("adminshop"); subphase = 192; delay = 10; return; }
                    if (subphase == 192) {
                        if (!(screen instanceof AdminScreen)) return;
                        check(((AdminDocument) get(screen, "draft")).config.categories.isEmpty(), "OP command opens the client workbench with a fresh snapshot");
                        click(screen, "Shop Builder"); click(screen, "+ Category"); subphase = 30; delay = 4; return;
                    }
                    if (subphase == 30) { set(screen, "Name", "Picker Test"); click(screen, "Create"); subphase = 31; delay = 4; return; }
                    if (subphase == 31) { click(screen, "+ Select item"); subphase = 32; delay = 5; return; }
                    if (subphase == 32) {
                        var groups = (java.util.List<?>) get(screen, "groups");
                        check(groups.size() >= 9, "Vanilla creative inventory groups present");
                        String building = null;
                        for (Object group : groups) if (get(group, "id").equals("building_blocks")) building = (String) get(group, "name");
                        check(building != null, "Building Blocks creative tab present"); pickerTab(screen, building);
                        set(screen, "Search items", "minecraft:stone"); at((WorkbenchScreen) screen, 184, 180, false);
                        check(((java.util.Map<?, ?>) get(screen, "selection")).size() == 1, "Click selects instead of closing the listing picker");
                        String food = null;
                        for (Object group : groups) if (((String) get(group, "name")).contains("Food")) food = (String) get(group, "name");
                        pickerTab(screen, food); check(get(screen, "query").equals(""), "New tab starts with its own search state");
                        set(screen, "Search items", "minecraft:apple"); at((WorkbenchScreen) screen, 184, 180, false);
                        check(((java.util.Map<?, ?>) get(screen, "selection")).size() == 2, "Selection survives creative groups");
                        capture(client, "alpha9-creative-vanilla-picker.png"); click(screen, "Add selected (2)"); subphase = 33; delay = 4; return;
                    }
                    if (subphase == 33) {
                        var category = ((AdminDocument) get(screen, "draft")).config.category("picker_test");
                        check(category.items.size() == 2 && category.items.stream().allMatch(item -> item.buy == 0 && item.sell == 0 && item.stack != null), "Bulk native additions use zero prices and exact components");
                        at((WorkbenchScreen) screen, 70, 141, false); click(screen, "+ Select item"); subphase = 34; delay = 5; return;
                    }
                    if (subphase == 34) {
                        if ((boolean) get(screen, "bootstrap")) return;
                        pickerSource(screen, "usefulfood"); subphase = 35; delay = 10; return;
                    }
                    if (subphase == 35) {
                        var values = (java.util.List<?>) get(screen, "values"); if (values.isEmpty()) return;
                        var item = (com.zycu.guishop.ShopConfig.ShopItem) get(values.getFirst(), "listing"); pickerModItem = item.item;
                        at((WorkbenchScreen) screen, 184, 180, false); capture(client, "alpha9-creative-mod-picker.png");
                        String pack = null;
                        for (Object source : (java.util.List<?>) get(screen, "sources")) if (((String) get(source, "name")).contains("Zymods_Vanilla_Plus")) pack = (String) get(source, "id");
                        check(pack != null, "Actual datapack receives a picker tab"); pickerSource(screen, pack); subphase = 36; delay = 10; return;
                    }
                    if (subphase == 36) {
                        var values = (java.util.List<?>) get(screen, "values"); if (values.isEmpty()) return;
                        var item = (com.zycu.guishop.ShopConfig.ShopItem) get(values.getFirst(), "listing"); pickerVariant = item.listingId;
                        check(item.stack != null && pickerVariant.contains("#"), "Datapack picker retains exact variant data");
                        String localized = ((net.minecraft.world.item.ItemStack) get(values.getFirst(), "stack")).getHoverName().getString();
                        set(screen, "Search items", localized);
                        check(!((java.util.List<?>) get(screen, "values")).isEmpty(), "Detected picker searches client-localized names");
                        at((WorkbenchScreen) screen, 184, 180, false);
                        check(((java.util.Map<?, ?>) get(screen, "selection")).size() == 2, "Selection survives mod and pack tabs");
                        capture(client, "alpha9-creative-pack-picker.png"); click(screen, "Add selected (2)"); subphase = 37; delay = 4; return;
                    }
                    if (subphase == 37) {
                        var category = ((AdminDocument) get(screen, "draft")).config.category("picker_test");
                        check(category.items.size() == 4 && category.items.stream().anyMatch(item -> item.item.equals(pickerModItem)) && category.items.stream().anyMatch(item -> item.listingId.equals(pickerVariant)), "Mod and exact pack selections added to the chosen category");
                        check(category.items.stream().allMatch(item -> item.buy == 0 && item.sell == 0), "All picker additions start disabled");
                        at((WorkbenchScreen) screen, 70, 141, false); click(screen, "+ Select item"); subphase = 38; delay = 5; return;
                    }
                    if (subphase == 38) {
                        check((boolean) get(screen, "multiple"), "Listing picker remains in selection mode");
                        click(screen, "Select shown"); check(!((java.util.Map<?, ?>) get(screen, "selection")).isEmpty(), "Select visible rows works");
                        click(screen, "Clear picks"); check(((java.util.Map<?, ?>) get(screen, "selection")).isEmpty(), "Clear picks works");
                        var workbench = (WorkbenchScreen) screen; workbench.geometry();
                        screen.mouseScrolled(workbench.originX + 180 * workbench.scale, workbench.originY + 190 * workbench.scale, 0, -1);
                        check((int) get(screen, "topRow") == 1, "Mouse wheel advances one row like creative inventory");
                        nativeMove(client, workbench, 474, 177); nativeButton(client, true); subphase = 381; delay = 4; return;
                    }
                    if (subphase == 381) {
                        nativeMove(client, (WorkbenchScreen) screen, 474, 320); nativeButton(client, false);
                        int rows = (((java.util.List<?>) get(screen, "values")).size() + 8) / 9;
                        check((int) get(screen, "topRow") == rows - 5, "Native scrollbar drag reaches final five rows: row=" + get(screen, "topRow") + ", maximum=" + (rows - 5) + ", focused=" + client.isWindowActive());
                        subphase = 382; delay = 4; return;
                    }
                    if (subphase == 382) {
                        capture(client, "alpha9-creative-scrollbar.png");
                        set(screen, "Search items", "minecraft:stone"); check((int) get(screen, "topRow") == 0, "Search resets scroll and clamps short results");
                        click(screen, "Cancel"); subphase = 39; delay = 4; return;
                    }
                    if (subphase == 39) { click(screen, "Remove category"); subphase = 40; delay = 4; return; }
                    if (subphase == 40) { click(screen, "Confirm"); subphase = 41; delay = 4; return; }
                    if (subphase == 41) {
                        check(((AdminDocument) get(screen, "draft")).config.categories.isEmpty(), "Cancel adds nothing; temporary picker category removed from draft");
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
                case 8 -> {
                    if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("travelersbackpack")) { pickFirst(screen, "minecraft:emerald"); next(); return; }
                    if (subphase == 0) {
                        if ((boolean) get(screen, "bootstrap")) return;
                        pickerSource(screen, "travelersbackpack"); subphase = 1; delay = 10; return;
                    }
                    set(screen, "Search items", "travelersbackpack:standard");
                    if (((java.util.List<?>) get(screen, "values")).isEmpty()) return;
                    var source = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("travelersbackpack:standard")));
                    var context = net.minecraft.world.item.Item.TooltipContext.of(client.level);
                    var originalTooltip = source.getTooltipLines(context, client.player, net.minecraft.world.item.TooltipFlag.NORMAL);
                    check(originalTooltip.size() > 1, "Actual Traveler backpack injects default tooltip information");
                    var clean = com.zycu.guishop.ShopIcons.display(source, "Backpacks", "Shop category help");
                    check(clean.getTooltipLines(context, client.player, net.minecraft.world.item.TooltipFlag.NORMAL).stream().map(net.minecraft.network.chat.Component::getString).toList().equals(java.util.List.of("Backpacks", "Shop category help")), "Actual Traveler category carrier excludes tier and Shift help");
                    capture(client, "alpha9-travelers-icon-picker.png"); at((WorkbenchScreen) screen, 184, 180, false);
                    subphase = 0; next();
                }
                case 9 -> { set(screen, "Tooltip description", "Shop category help"); click(screen, "+ Add held item"); next(); }
                case 10 -> {
                    if (get(screen, "selectedItem") == null) return;
                    if (subphase == 0) { set(screen, "Buy price", "12.5"); set(screen, "Sell price", "3.1"); set(screen, "Tooltip description", "A little sparkle for your next adventure."); subphase = 1; delay = 4; return; }
                    if (subphase == 1) {
                        originalGuiScale = client.options.guiScale().get(); themeScale = 1;
                        client.options.guiScale().set(themeScale); client.resizeGui(); subphase = 2; delay = 4; return;
                    }
                    if (subphase == 2) {
                        var input = screen.children().stream().filter(widget -> widget instanceof net.minecraft.client.gui.components.EditBox field && field.getMessage().getString().equals("Display name")).map(widget -> (net.minecraft.client.gui.components.EditBox)widget).findFirst().orElseThrow();
                        input.setValue("Diamond");
                        // The left inset must focus and place the caret at the beginning, at every GUI scale.
                        at((WorkbenchScreen)screen, input.getX() + 2, input.getY() + 10, false);
                        check(input.isFocused(), "Rounded text field padding receives focus at scale " + themeScale);
                        screen.charTyped(new net.minecraft.client.input.CharacterEvent('X'));
                        check(input.getValue().equals("XDiamond"), "Typed text and caret match visual field inset at scale " + themeScale);
                        input.setValue("Diamond");
                        at((WorkbenchScreen)screen, input.getX() + input.getWidth() - 2, input.getY() + 10, false);
                        screen.charTyped(new net.minecraft.client.input.CharacterEvent('Y'));
                        check(input.getValue().equals("DiamondY"), "Right inset places caret at the end at scale " + themeScale);
                        input.setValue("Diamond");
                        check(screen.getFont() != client.font, "Editor font is scoped to the Adminshop, not the game font");
                        nativeMove(client, (WorkbenchScreen)screen, -19, 90);
                        subphase = 3; delay = 4; return;
                    }
                    if (subphase == 3) {
                        capture(client, "alpha9-editor-scale-" + themeScale + ".png");
                        if (themeScale < 4) { client.options.guiScale().set(++themeScale); client.resizeGui(); subphase = 2; delay = 4; return; }
                        client.options.guiScale().set(originalGuiScale); client.resizeGui(); subphase = 4; delay = 4; return;
                    }
                    subphase = 0; capture(client, "alpha9-shop-builder.png"); click(screen, "+ Category"); next();
                }
                case 11 -> { set(screen, "Name", "Building"); click(screen, "Create"); next(); }
                case 12 -> { click(screen, "+ Select item"); next(); }
                case 13 -> { pickFirst(screen, "minecraft:stone"); next(); }
                case 14 -> { set(screen, "Buy price", "2"); set(screen, "Sell price", "0.5"); at((WorkbenchScreen) screen, 70, 160, false); next(); }
                case 15 -> { click(screen, "+ Select item"); next(); }
                case 16 -> { pickFirst(screen, "minecraft:cobblestone"); next(); }
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
                    if (subphase == 10) { pickFirst(screen, "minecraft:stone"); subphase = 11; delay = 4; return; }
                    if (subphase == 11) {
                        AdminDocument draft = (AdminDocument) get(screen, "draft");
                        check(get(screen, "categoryId").equals("building"), "Adding existing item stays in chosen category");
                        var duplicate = draft.config.category("building").items.stream().filter(item -> item.item.equals("minecraft:stone")).findFirst().orElseThrow();
                        check(!duplicate.listingId.equals("minecraft:stone") && duplicate.buy == 0 && duplicate.sell == 0, "Separate duplicate listing starts disabled");
                        check(draft.config.findItem("minecraft:stone").category().id.equals("materials"), "Original listing remains in place");
                        capture(client, "alpha9-duplicate-listing.png"); click(screen, "Detected Content"); subphase = 12; delay = 15; return;
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
                        capture(client, "alpha9-detected-content.png");
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
                        capture(client, "alpha9-imported-pack-variants.png");
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
                        capture(client, "alpha9-detected-usefulfood.png"); click(screen, "Import source"); subphase = 21; delay = 4; return;
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
                        capture(client, "alpha9-imported-usefulfood.png"); startSettings(screen); return;
                    }
                    if (subphase == 1) { set(screen, "Name", "minecraft:unbreaking"); click(screen, "Create"); subphase = 2; delay = 4; return; }
                    AdminDocument draft = (AdminDocument) get(screen, "draft");
                    check(draft.config.enchantments.containsKey("minecraft:unbreaking"), "Explicit enchantment offer");
                    set(screen, "minecraft:unbreaking", "Unbreaking"); set(screen, "Price / level", "120"); set(screen, "Max level", "5"); subphase = 0;
                    addTradeFixtures(draft, client);
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
                    capture(client, "alpha9-shop-simulator.png"); subphase = 0; next();
                }
                case 22 -> {
                    if (subphase == 0) { nativeHit(client, (WorkbenchScreen) screen, "tile:enchant", ""); nativeButton(client, true); nativeButton(client, false); subphase = 1; delay = 4; return; }
                    if (subphase == 1) {
                        check((boolean) get(screen, "enchantPreview"), "Enchantment simulator");
                        nativeHit(client, (WorkbenchScreen) screen, "tile:offer", "minecraft:unbreaking"); nativeButton(client, true); nativeButton(client, false);
                        subphase = 2; delay = 4; return;
                    }
                    if (subphase >= 2 && subphase <= 4) {
                        check(get(screen, "enchantId").equals("minecraft:unbreaking"), "Enchantment selection survives arrange toggle");
                        check((boolean) get(screen, "enchantPreview") && hitCount(screen, "tile:level") == 5, "Levels above vanilla remain visible during arrange toggles");
                        if (subphase < 4) { click(screen, (boolean) get(screen, "arranging") ? "Exit arrange mode" : "Arrange mode"); subphase++; delay = 4; return; }
                        check(hitCount(screen, "preview:mode") == 0, "Simulator has no buy/sell mode switch");
                        click(screen, "Settings"); click(screen, "Economy"); subphase = 7; delay = 4; return;
                    }
                    capture(client, "alpha9-server-settings.png"); subphase = 0; next();
                }
                case 23 -> { click(screen, "Transactions"); click(screen, "Changes"); click(screen, "Transactions"); next(); }
                case 24 -> {
                    if (subphase == 0) {
                        if (!get(screen, "query").equals("ShopTest")) {
                            set(screen, "Search players", "ShopTest"); click(screen, "Refresh"); delay = 10; return;
                        }
                        var roster = (com.zycu.guishop.admin.AdminPlayers.Page) get(screen, "playerData");
                        if (roster.players().isEmpty()) return;
                        check(get(screen, "selectedPlayer") == null, "Transactions opens player selector");
                        int row = -1;
                        for (int i = 0; i < Math.min(10, roster.players().size()); i++) if (roster.players().get(i).name().equalsIgnoreCase("ShopTest")) row = i;
                        if (row < 0) { delay = 5; return; }
                        selectedHistoryUuid = roster.players().get(row).uuid();
                        check(roster.players().get(row).balance() >= 0, "Current balance provided by server");
                        capture(client, "alpha9-player-balances.png"); at((WorkbenchScreen) screen, 100, 164 + row * 14, false); subphase = 10; delay = 20; return;
                    }
                    if (subphase == 10) {
                        var page = (com.zycu.guishop.admin.TransactionJournal.Page) get(screen, "logData");
                        if (page.entries().isEmpty()) return;
                        check(page.entries().stream().allMatch(entry -> entry.uuid().equals(selectedHistoryUuid) && !entry.action().equals("CONFIG_APPLY")), "Only selected player's transactions shown");
                        check(((com.zycu.guishop.admin.AdminPlayers.Player) get(screen, "selectedPlayer")).uuid().equals(selectedHistoryUuid), "Selected player's balance stays attached to history");
                        capture(client, "alpha9-transaction-journal.png"); at((WorkbenchScreen) screen, 100, 164, false); subphase = 1; delay = 4; return;
                    }
                    if (subphase == 1) {
                        check(screen instanceof AdminScreen && hitCount(screen, "log") == 0, "Transaction rows are read-only with no detail window");
                        click(screen, "Set balance"); check(client.gui.screen() instanceof AccountDialog, "Set balance opens an overlay popup");
                        var popup = client.gui.screen(); check(((com.zycu.guishop.admin.AdminPlayers.Player) get(popup, "player")).uuid().equals(selectedHistoryUuid), "Popup targets selected UUID");
                        set(popup, "New balance", "777.25"); subphase = 35; delay = 4; return;
                    }
                    if (subphase == 35) { check(screen instanceof AccountDialog, "Set balance popup persists until confirmed"); capture(client, "alpha9-set-balance.png"); click(screen, "Set balance"); subphase = 30; delay = 20; return; }
                    if (subphase == 30) {
                        var selected = (com.zycu.guishop.admin.AdminPlayers.Player) get(screen, "selectedPlayer");
                        var page = (com.zycu.guishop.admin.TransactionJournal.Page) get(screen, "logData");
                        if ((boolean) get(screen, "busy") || selected.balance() != 777.25 || page.entries().stream().noneMatch(entry -> entry.action().equals("BALANCE_SET"))) return;
                        check(((AdminScreen) screen).accountMoney(777.25).equals("Coins 777.25"), "Amounts use live server currency");
                        click(screen, "Reset"); check(client.gui.screen() instanceof AccountDialog && (boolean) get(client.gui.screen(), "reset"), "Reset opens a separate confirmation");
                        subphase = 36; delay = 4; return;
                    }
                    if (subphase == 36) {
                        check(screen instanceof AccountDialog, "Reset popup persists until confirmed"); capture(client, "alpha9-reset-confirmation.png"); click(screen, "Cancel");
                        check(((com.zycu.guishop.admin.AdminPlayers.Player) get(client.gui.screen(), "selectedPlayer")).balance() == 777.25, "Cancel reset preserves balance");
                        click(client.gui.screen(), "Reset"); click(client.gui.screen(), "Reset"); subphase = 31; delay = 20; return;
                    }
                    if (subphase == 31) {
                        if ((boolean) get(screen, "busy")) return;
                        var selected = (com.zycu.guishop.admin.AdminPlayers.Player) get(screen, "selectedPlayer");
                        if (selected.balance() != 2000) return;
                        check(((com.zycu.guishop.admin.TransactionJournal.Page) get(screen, "logData")).entries().isEmpty(), "Reset clears selected player's active history and restores default");
                        rewardReference = "gui-" + java.util.UUID.randomUUID();
                        client.getConnection().sendCommand("shopreward " + selectedHistoryUuid + " 25 vote " + rewardReference + " GUI vote reward");
                        subphase = 32; delay = 20; return;
                    }
                    if (subphase == 32) {
                        var page = (com.zycu.guishop.admin.TransactionJournal.Page) get(screen, "logData");
                        if (page.entries().stream().noneMatch(entry -> entry.action().equals("REWARD") && entry.listing().contains(rewardReference))) { click(screen, "Refresh"); delay = 20; return; }
                        var entry = page.entries().getFirst();
                        check(((com.zycu.guishop.admin.AdminPlayers.Player) get(screen, "selectedPlayer")).balance() == 2025, "Console-style reward appears in current player balance");
                        capture(client, "alpha9-external-reward.png");
                        set(screen, "Filter transactions", com.zycu.guishop.admin.JournalDates.date(entry.time(), java.time.ZoneId.systemDefault())); click(screen, "Search"); subphase = 33; delay = 20; return;
                    }
                    if (subphase == 33) {
                        var page = (com.zycu.guishop.admin.TransactionJournal.Page) get(screen, "logData"); if (page.entries().isEmpty()) return;
                        check(page.entries().getFirst().action().equals("REWARD"), "Client local-date search finds reward");
                        set(screen, "Filter transactions", page.entries().getFirst().time().substring(0,10)); click(screen, "Search"); subphase = 34; delay = 20; return;
                    }
                    if (subphase == 34) {
                        var page = (com.zycu.guishop.admin.TransactionJournal.Page) get(screen, "logData"); if (page.entries().isEmpty()) return;
                        check(page.entries().getFirst().action().equals("REWARD"), "Client UTC-date search also finds reward");
                        capture(client, "alpha9-transaction-journal.png"); click(screen, "Changes"); subphase = 2; delay = 20; return;
                    }
                    if (subphase == 2) {
                        var page = (com.zycu.guishop.admin.TransactionJournal.Page) get(screen, "logData"); if (page.entries().isEmpty()) return;
                        capture(client, "alpha9-configuration-history.png"); at((WorkbenchScreen) screen, 100, 164, false); subphase = 3; delay = 4; return;
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
                    check(screen.getFont() == client.font, "Player shop retains the vanilla font even on an admin client");
                    capture(client, "alpha9-vanilla-player-shop.png"); client.gui.setScreen(null);
                    System.out.println("[Alpha UI test] REVOKE_READY"); next(); delay = 60;
                }
                case 26 -> { client.gui.setScreen(null); client.getConnection().sendCommand("adminshop"); next(); }
                case 27 -> {
                    if (!(screen instanceof AdminNoticeScreen)) return;
                    check(get(screen, "message").equals(AdminEditorService.DENIED), "Revoked OP receives exact popup");
                    System.out.println("[Alpha UI test] PASS: real keybind; permission popup; category creation with no folder controls; icon selection; exact held-item import; pricing; native slow mouse drag and category reorder; cross-category move; duplicate addition retains destination; real pack detection; individual and whole-source imports with zero prices; server Apply; presentation; arrange mode and unified shop with no mode switch; settings; player names and balances; exact player history; journal; vanilla player shop; revoked permissions");
                    next(); delay = 30;
                }
                case 28 -> { done = true; client.stop(); }
            }
        } catch (Throwable failure) {
            done = true; System.err.println("[Alpha UI test] FAIL at phase " + phase); failure.printStackTrace(); client.stop();
        }
    }
    private void next() { System.out.println("[Alpha UI test] Phase " + phase + " complete"); phase++; delay = 20; }
    private static void addTradeFixtures(AdminDocument draft, Minecraft client) {
        var map = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MAP);
        map.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(net.minecraft.network.chat.Component.literal("Original map lore"))));
        var stacks = new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
        stacks.add(map); stacks.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ABANDONED_CAMP_MAP));
        var backpack = net.minecraft.resources.Identifier.parse("travelersbackpack:end");
        if (net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(backpack)) stacks.add(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(backpack)));
        for (var stack : stacks) {
            var listing = new com.zycu.guishop.ShopConfig.ShopItem();
            listing.item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            listing.stack = com.zycu.guishop.ItemStackData.encode(stack, client.player.registryAccess());
            listing.listingId = com.zycu.guishop.ItemStackData.listingId(stack, client.player.registryAccess());
            listing.name = stack.getHoverName().getString(); listing.description = "Display only test description";
            listing.buy = 10; listing.sell = 2; listing.manualPrice = true;
            draft.config.category("materials").items.add(listing);
        }
    }
    private void startSettings(Screen screen) {
        click(screen, "Settings"); set(screen, "Shop title", "Zycu's Shop");
        // Fund fresh disposable player clients enough to buy the level V fixture on restart.
        set(screen, "Starting balance for new players", "2000");
        set(screen, "Currency prefix (display only)", "Coins ");
        click(screen, "Enchantments"); click(screen, "+ Offer"); subphase = 1; delay = 4;
    }
    private static void openKey() { KeyMapping.click(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F8)); }
    private static void pickFirst(Screen screen, String id) throws Exception {
        set(screen, "Search items", id); at((WorkbenchScreen) screen, 184, 180, false);
        if ((boolean) get(screen, "multiple")) click(screen, "Add selected (1)");
    }
    private static void pickerSource(Screen screen, String id) throws Exception {
        var tabs = (java.util.List<?>) get(screen, "tabs");
        for (int index = 0; index < tabs.size(); index++) if (get(tabs.get(index), "source").equals(id) && get(tabs.get(index), "group").equals("")) {
            int target = index / 9;
            while ((int) get(screen, "sourcePage") < target) click(screen, ">");
            while ((int) get(screen, "sourcePage") > target) click(screen, "<");
            click(screen, (String) get(tabs.get(index), "name")); return;
        }
        throw new AssertionError("Missing picker source " + id);
    }
    private static void pickerTab(Screen screen, String name) throws Exception {
        var tabs = (java.util.List<?>) get(screen, "tabs");
        for (int index = 0; index < tabs.size(); index++) if (get(tabs.get(index), "name").equals(name)) {
            int target = index / 9;
            while ((int) get(screen, "sourcePage") < target) click(screen, ">");
            while ((int) get(screen, "sourcePage") > target) click(screen, "<");
            click(screen, name); return;
        }
        throw new AssertionError("Missing picker tab " + name);
    }
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
        screen.geometry(); return new MouseButtonEvent(screen.originX + (x + screen.contentOffsetX()) * screen.scale, screen.originY + y * screen.scale, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
    }
    private static void at(WorkbenchScreen screen, double x, double y, boolean down) {
        var event = event(screen, x, y); screen.mouseClicked(event, false); if (!down) screen.mouseReleased(event);
    }
    private static void nativeMove(Minecraft client, WorkbenchScreen screen, double x, double y) throws Exception {
        screen.geometry(); var window = client.getWindow();
        double px = (screen.originX + (x + screen.contentOffsetX()) * screen.scale) * window.getScreenWidth() / window.getGuiScaledWidth();
        double py = (screen.originY + y * screen.scale) * window.getScreenHeight() / window.getGuiScaledHeight();
        // Synthetic input must exercise MouseHandler even when another desktop window has focus.
        // Restore the real focus flag immediately after dispatch, without raising or moving any window.
        Field focus = window.getClass().getDeclaredField("focused"); focus.setAccessible(true);
        boolean wasFocused = focus.getBoolean(window);
        try {
            focus.setBoolean(window, true);
            client.mouseHandler.onMove(window.handle(), px, py, 0, 0); client.mouseHandler.handleAccumulatedMovement();
        } finally { focus.setBoolean(window, wasFocused); }
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
