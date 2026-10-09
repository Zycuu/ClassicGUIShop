package com.zycu.guishop.client;

import com.zycu.guishop.ItemStackData;
import com.zycu.guishop.ShopConfig;
import com.zycu.guishop.admin.AdminDocument;
import com.zycu.guishop.admin.DetectedContent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Creative groups and server-detected sources. Selections only enter the detached draft. */
final class IconPickerScreen extends WorkbenchScreen {
    private static final int COLUMNS = 9, ROWS = 5, CELL = 32, VISIBLE = COLUMNS * ROWS, GRID_X = 168, GRID_Y = 164;
    private static final int SCROLL_X = GRID_X + COLUMNS * CELL + 8, TRACK_HEIGHT = ROWS * CELL, THUMB_HEIGHT = 26;
    private record Pick(ShopConfig.ShopItem listing, ItemStack stack, String search) {}
    private record Group(String id, String name, ItemStack icon, List<Pick> items) {}
    private record Source(String id, String name, ItemStack icon) {}
    private record Tab(String name, ItemStack icon, String source, String group) {}
    private final Screen parent;
    private final boolean multiple;
    private final Consumer<List<ShopConfig.ShopItem>> accepted;
    private final List<Group> groups = new ArrayList<>();
    private final List<Source> sources = new ArrayList<>();
    private final List<Tab> tabs = new ArrayList<>();
    private final Map<String, String> searches = new HashMap<>();
    private final Map<String, List<Pick>> catalogs = new HashMap<>();
    private final Map<String, Integer> cursors = new HashMap<>();
    private final Set<String> complete = new HashSet<>();
    private final Map<String, ShopConfig.ShopItem> selection = new LinkedHashMap<>();
    private List<Pick> values = List.of();
    private String sourceId = "minecraft", groupId = "", query = "", status = "Loading detected tabs...";
    private int topRow, sourcePage, offset, retry = 1;
    private long generation = 1;
    private boolean initialized, bootstrap = true, failed;
    private boolean scrollDragging;
    private Pick hovered;
    private EditBox search;
    private Button add;

    IconPickerScreen(Screen parent, String title, boolean multiple, Consumer<List<ShopConfig.ShopItem>> accepted) {
        super(title); this.parent = parent; this.multiple = multiple; this.accepted = accepted;
    }
    @Override protected void init() {
        if (!initialized) { nativeCatalog(); initialized = true; }
        tabs.clear();
        tabs.add(new Tab("Vanilla", new ItemStack(Items.GRASS_BLOCK), "minecraft", ""));
        tabs.add(new Tab("All items", new ItemStack(Items.COMPASS), "", ""));
        for (var group : groups) if (!group.id().isBlank()) tabs.add(new Tab(group.name(), group.icon(), "minecraft", group.id()));
        for (var source : sources) if (!source.id().isBlank() && !source.id().equals("minecraft")) tabs.add(new Tab(source.name(), source.icon(), source.id(), ""));
        sourcePage = Math.min(sourcePage, Math.max(0, (tabs.size() - 1) / 9));
        button("<", GRID_X - 24, 137, 18, () -> { sourcePage = Math.max(0, sourcePage - 1); rebuildWidgets(); }).active = sourcePage > 0;
        button(">", SCROLL_X + 2, 137, 18, () -> { sourcePage++; rebuildWidgets(); }).active = (sourcePage + 1) * 9 < tabs.size();
        for (int i = 0; i < 9 && sourcePage * 9 + i < tabs.size(); i++) {
            var tab = tabs.get(sourcePage * 9 + i);
            addRenderableWidget(new PickerTab(tab.name(), tab.icon(), GRID_X + i * CELL, 133, CELL,
                tab.source().equals(sourceId) && tab.group().equals(groupId), () -> chooseSource(tab.source(), tab.group())));
        }
        search = new WorkbenchField(font, 80, 104, 422, Component.literal("Search items"));
        search.setHint(Component.literal("Search names or IDs in this tab...")); search.setMaxLength(128); search.setValue(query);
        search.setResponder(value -> { query = value; searches.put(sourceId + "|" + groupId, value); filter(true); }); addRenderableWidget(search);
        button("Clear", 508, 104, 72, () -> search.setValue(""));
        if (multiple) {
            button("Select shown", 80, 354, 110, () -> {
                for (var pick : values.subList(topRow * COLUMNS, Math.min(values.size(), topRow * COLUMNS + VISIBLE))) selection.put(pick.listing().listingId, pick.listing());
                updateButtons();
            });
            button("Clear picks", 196, 354, 84, () -> { selection.clear(); updateButtons(); });
            add = button("Add selected", 324, 354, 170, this::submit);
        }
        button("Cancel", 506, 354, 74, this::onClose);
        filter(false); setInitialFocus(search);
    }
    private void nativeCatalog() {
        sources.add(new Source("minecraft", "Vanilla", new ItemStack(Items.GRASS_BLOCK)));
        sources.add(new Source("", "All items", new ItemStack(Items.COMPASS)));
        var unique = new LinkedHashMap<String, Pick>();
        for (var id : BuiltInRegistries.ITEM.keySet().stream().filter(id -> id.getNamespace().equals("minecraft")).sorted(Comparator.comparing(Object::toString)).toList()) {
            var stack = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
            if (!stack.isEmpty()) { var pick = pick(stack, id.toString()); unique.put(pick.listing().listingId, pick); }
        }
        CreativeModeTabs.tryRebuildTabContents(minecraft.level.enabledFeatures(), true, minecraft.level.registryAccess());
        var registry = minecraft.level.registryAccess().lookupOrThrow(Registries.CREATIVE_MODE_TAB);
        groups.add(new Group("", "All vanilla items", new ItemStack(Items.COMPASS), List.of()));
        for (var entry : registry.entrySet().stream().sorted(Comparator.comparingInt(entry -> entry.getValue().row().ordinal() * 10 + entry.getValue().column())).toList()) {
            var tab = entry.getValue();
            if (!entry.getKey().identifier().getNamespace().equals("minecraft") || tab.getType() != CreativeModeTab.Type.CATEGORY || !tab.hasAnyItems()) continue;
            List<Pick> items = new ArrayList<>();
            for (var stack : tab.getDisplayItems()) if (BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft")) {
                var pick = pick(stack, entry.getKey().identifier().toString()); items.add(pick); unique.putIfAbsent(pick.listing().listingId, pick);
            }
            if (!items.isEmpty()) groups.add(new Group(entry.getKey().identifier().getPath(), tab.getDisplayName().getString(), tab.getIconItem(), List.copyOf(items)));
        }
        var all = new ArrayList<>(unique.values()); all.sort(Comparator.comparing(pick -> pick.stack().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER));
        catalogs.put("minecraft", all); complete.add("minecraft");
        groups.set(0, new Group("", "All vanilla items", new ItemStack(Items.COMPASS), all));
    }
    private Pick pick(ItemStack source, String origin) {
        var stack = source.copyWithCount(1); var listing = new ShopConfig.ShopItem();
        listing.item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); listing.stack = ItemStackData.encode(stack, minecraft.level.registryAccess());
        listing.listingId = stack.getComponentsPatch().isEmpty() ? listing.item : listing.item + "#" + ItemStackData.shortHash(listing.stack.toString());
        listing.name = stack.getHoverName().getString(); listing.manualPrice = true;
        return new Pick(listing, stack, searchable(listing.name + " " + listing.item + " " + origin));
    }
    private static String searchable(String value) { return value.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' '); }
    private void filter(boolean reset) {
        var items = sourceId.equals("minecraft") ? groups.stream().filter(group -> group.id().equals(groupId)).findFirst().orElse(groups.getFirst()).items() : catalogs.getOrDefault(sourceId, List.of());
        String[] words = searchable(query).trim().split("\\s+");
        var matches = items.stream().filter(pick -> { for (String word : words) if (!pick.search().contains(word)) return false; return true; });
        if (!query.isBlank()) matches = matches.sorted(Comparator.comparingInt(pick ->
            pick.listing().item.equalsIgnoreCase(query.trim()) || pick.stack().getHoverName().getString().equalsIgnoreCase(query.trim()) ? 0 : 1));
        values = matches.toList();
        topRow = reset ? 0 : Math.min(topRow, maxRow()); updateButtons();
    }
    private int maxRow() { return Math.max(0, (values.size() + COLUMNS - 1) / COLUMNS - ROWS); }
    private void updateButtons() {
        if (add != null) { add.active = !selection.isEmpty(); add.setMessage(Component.literal("Add selected (" + selection.size() + ")")); }
    }
    private void chooseSource(String id, String group) {
        AdminShopClient.cancelDetection(this);
        sourceId = id; groupId = group; query = searches.getOrDefault(id + "|" + group, ""); topRow = 0; scrollDragging = false; failed = false;
        if (!bootstrap) {
            generation++; offset = cursors.getOrDefault(id, 0); retry = complete.contains(id) ? 0 : 1;
            status = complete.contains(id) ? "" : "Loading this tab... You can browse while it fills.";
        }
        filter(true); rebuildWidgets();
    }
    void tickNetworking() {
        if (minecraft.gui.screen() != this || failed || retry <= 0 || --retry > 0) return;
        var request = new DetectedContent.Query(bootstrap ? "" : sourceId, "", bootstrap ? 0 : offset, false, false, generation);
        retry = AdminShopClient.detect(this, request) ? 0 : 6;
    }
    void detected(DetectedContent.Page data, DetectedContent.Query request) {
        if (minecraft.gui.screen() != this || request.generation() != generation) return;
        if (!data.ready()) { status = "Inspecting loaded items... Vanilla tabs are ready."; retry = 10; return; }
        if (bootstrap) {
            for (var source : data.sources()) if (!source.id().equals("minecraft")) {
                var icon = data.entries().stream().filter(entry -> entry.namespaces().contains(source.id()) || entry.packs().contains(source.id())).findFirst()
                    .map(entry -> entry.listing().createStack(minecraft.level.registryAccess())).orElse(new ItemStack(source.id().startsWith("datapack_") ? Items.SPYGLASS : Items.CHEST));
                sources.add(new Source(source.id(), source.name(), icon));
            }
            bootstrap = false; generation++; offset = 0;
            retry = sourceId.equals("minecraft") ? 0 : 1; status = ""; rebuildWidgets(); return;
        }
        if (!request.source().equals(sourceId) || request.offset() != offset) return;
        var items = catalogs.computeIfAbsent(sourceId, ignored -> new ArrayList<>());
        for (var entry : data.entries()) {
            var stack = entry.listing().createStack(minecraft.level.registryAccess());
            if (stack.isEmpty()) continue;
            var listing = AdminDocument.JSON.fromJson(AdminDocument.JSON.toJson(entry.listing()), ShopConfig.ShopItem.class);
            listing.name = stack.getHoverName().getString();
            items.add(new Pick(listing, stack, searchable(listing.name + " " + listing.item + " " + entry.origin())));
        }
        offset = data.offset() + data.entries().size();
        cursors.put(sourceId, offset);
        if (data.more()) { retry = 6; status = "Loading this tab... " + offset + " items received."; }
        else { complete.add(sourceId); status = ""; }
        filter(false);
    }
    void failure(String message) { if (minecraft.gui.screen() == this) { status = message; retry = 0; failed = true; } }
    private void submit() {
        try {
            var copies = selection.values().stream().map(item -> AdminDocument.JSON.fromJson(AdminDocument.JSON.toJson(item), ShopConfig.ShopItem.class)).toList();
            for (var item : copies) {
                item.name = item.name.replaceAll("[\\p{Cntrl}]", " ");
                if (item.name.length() > 120) item.name = item.name.substring(0, 120);
            }
            accepted.accept(copies); minecraft.gui.setScreen(parent);
        } catch (IllegalArgumentException invalid) { status = invalid.getMessage(); }
    }
    @Override protected void paint(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        panel(graphics, 62, 68, 536, 314, WorkbenchTheme.SURFACE);
        text(graphics, title.getString(), 80, 82, WorkbenchTheme.TEXT);
        String label = sources.stream().filter(source -> source.id().equals(sourceId)).findFirst().map(Source::name).orElse("Vanilla");
        if (sourceId.equals("minecraft")) label = groups.stream().filter(group -> group.id().equals(groupId)).findFirst().orElse(groups.getFirst()).name();
        text(graphics, font.plainSubstrByWidth(label, 312), 248, 82, WorkbenchTheme.TEXT);
        String hint = multiple ? "Click to select several items; Add selected puts them in your category." : "Click an item to use its exact appearance as the category icon.";
        hovered = null;
        panel(graphics, GRID_X - 8, GRID_Y - 3, COLUMNS * CELL + 44, TRACK_HEIGHT + 6, WorkbenchTheme.PREVIEW);
        for (int slot = 0; slot < VISIBLE; slot++) {
            int x = GRID_X + slot % COLUMNS * CELL, y = GRID_Y + slot / COLUMNS * CELL;
            graphics.fill(x, y, x + CELL, y + CELL, 0xFF373737);
            graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0xFF8B8B8B);
            graphics.fill(x + 1, y + CELL - 1, x + CELL, y + CELL, 0xFFFFFFFF);
            graphics.fill(x + CELL - 1, y + 1, x + CELL, y + CELL, 0xFFFFFFFF);
            int index = topRow * COLUMNS + slot;
            if (index >= values.size()) continue;
            var pick = values.get(index);
            graphics.pose().pushMatrix(); graphics.pose().translate(x + 2, y + 2); graphics.pose().scale(1.75f, 1.75f);
            graphics.item(pick.stack(), 0, 0); graphics.pose().popMatrix();
            if (selection.containsKey(pick.listing().listingId)) { graphics.outline(x, y, CELL, CELL, 0xFF67DDD0); text(graphics, "+", x + 23, y + 21, 0xFFECFFF0); }
            if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) {
                graphics.outline(x, y, CELL, CELL, WorkbenchTheme.TEXT); hint = pick.stack().getHoverName().getString() + " | " + pick.listing().item; hovered = pick;
            }
        }
        graphics.fill(SCROLL_X, GRID_Y, SCROLL_X + 20, GRID_Y + TRACK_HEIGHT, 0xFF8B8B8B);
        int thumbY = GRID_Y + (maxRow() == 0 ? 0 : Math.round((TRACK_HEIGHT - THUMB_HEIGHT) * topRow / (float) maxRow()));
        graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
            net.minecraft.resources.Identifier.withDefaultNamespace("container/creative_inventory/scroller" + (maxRow() == 0 ? "_disabled" : "")), SCROLL_X, thumbY, 20, THUMB_HEIGHT);
        if (values.isEmpty()) {
            text(graphics, "No matching items", GRID_X + 16, GRID_Y + 58, WorkbenchTheme.TEXT);
            text(graphics, "Clear search or choose another tab.", GRID_X + 16, GRID_Y + 72, WorkbenchTheme.TEXT);
        }
        for (var widget : children()) if (widget instanceof PickerTab tab && tab.isHovered()) hint = tab.getMessage().getString();
        text(graphics, font.plainSubstrByWidth(hint, 500), 80, 328, WorkbenchTheme.MUTED);
        String range = values.isEmpty() ? "0" : (topRow * COLUMNS + 1) + "-" + Math.min(values.size(), topRow * COLUMNS + VISIBLE);
        text(graphics, status.isBlank() ? "Showing " + range + " of " + values.size() + " matches" : font.plainSubstrByWidth(status, 500), 80, 340, status.isBlank() ? WorkbenchTheme.MUTED : WorkbenchTheme.GOLD);
    }
    @Override protected boolean click(double x, double y, int button) {
        if (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return false;
        if (x >= SCROLL_X && x < SCROLL_X + 20 && y >= GRID_Y && y < GRID_Y + TRACK_HEIGHT && maxRow() > 0) {
            scrollDragging = true; scrollTo(y); return true;
        }
        for (int slot = 0; slot < VISIBLE; slot++) {
            int sx = GRID_X + slot % COLUMNS * CELL, sy = GRID_Y + slot / COLUMNS * CELL, index = topRow * COLUMNS + slot;
            if (x < sx || x >= sx + CELL || y < sy || y >= sy + CELL || index >= values.size()) continue;
            var item = values.get(index).listing();
            if (!multiple) {
                try { accepted.accept(List.of(AdminDocument.JSON.fromJson(AdminDocument.JSON.toJson(item), ShopConfig.ShopItem.class))); minecraft.gui.setScreen(parent); }
                catch (IllegalArgumentException invalid) { status = invalid.getMessage(); }
            } else { if (selection.remove(item.listingId) == null) selection.put(item.listingId, item); updateButtons(); }
            return true;
        }
        return false;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        geometry(); int lx = logicalX(x), ly = logicalY(y);
        if (lx >= GRID_X && lx < SCROLL_X + 20 && ly >= GRID_Y && ly < GRID_Y + TRACK_HEIGHT && vertical != 0) {
            topRow = Math.clamp(topRow - (int) Math.signum(vertical), 0, maxRow()); updateButtons(); return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    private void scrollTo(double y) {
        double progress = Math.clamp((y - GRID_Y - THUMB_HEIGHT / 2.0) / (TRACK_HEIGHT - THUMB_HEIGHT), 0, 1);
        topRow = (int) Math.round(progress * maxRow());
    }
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (scrollDragging) { geometry(); scrollTo(logicalY(event.y())); return true; }
        return super.mouseDragged(event, dx, dy);
    }
    @Override protected boolean release(double x, double y, int button) {
        if (scrollDragging) { scrollDragging = false; return true; } return false;
    }
    @Override protected void afterWidgets(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        int px = (int) (originX + x * scale), py = (int) (originY + y * scale);
        if (hovered != null) graphics.setTooltipForNextFrame(font, hovered.stack(), px, py);
        else for (var widget : children()) if (widget instanceof PickerTab tab && tab.isHovered()) graphics.setTooltipForNextFrame(Component.literal(tab.getMessage().getString()), px, py);
    }
    @Override public void onClose() { retry = 0; generation++; AdminShopClient.cancelDetection(this); minecraft.gui.setScreen(parent); }

    private final class PickerTab extends Button {
        private final ItemStack icon;
        private final boolean chosen;
        PickerTab(String name, ItemStack icon, int x, int y, int width, boolean chosen, Runnable action) {
            super(x, y, width, 28, Component.literal(name), ignored -> action.run(), DEFAULT_NARRATION);
            this.icon = icon; this.chosen = chosen;
        }
        @Override protected void extractContents(GuiGraphicsExtractor graphics, int x, int y, float delta) {
            WorkbenchTheme.rounded(graphics, getX(), getY(), getWidth(), getHeight(), 4, chosen ? WorkbenchTheme.SELECTED : isHoveredOrFocused() ? 0xFF344C54 : WorkbenchTheme.PANEL);
            graphics.item(icon, getX() + (getWidth() <= 40 ? (getWidth() - 16) / 2 : 4), getY() + (getHeight() - 16) / 2);
            if (getWidth() > 40) text(graphics, font.plainSubstrByWidth(getMessage().getString().replaceFirst("^Pack: ", ""), getWidth() - 26), getX() + 24, getY() + 10, WorkbenchTheme.TEXT);
            if (chosen) WorkbenchTheme.rounded(graphics, getX() + 5, getY() + 25, getWidth() - 10, 3, 1, WorkbenchTheme.TEAL);
        }
    }
}
