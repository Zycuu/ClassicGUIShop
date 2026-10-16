package com.zycu.guishop.client;

import com.zycu.guishop.ListingGroups;
import com.zycu.guishop.ShopConfig;
import com.zycu.guishop.ShopIcons;
import com.zycu.guishop.admin.BulkListings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Browse only existing draft listings. Picks survive tab/search changes and preserve exact variants. */
final class PriceAdjustScreen extends WorkbenchScreen {
    private static final int COLUMNS = 9, ROWS = 5, CELL = 32, VISIBLE = COLUMNS * ROWS;
    private static final int GRID_X = 84, GRID_Y = 140, SCROLL_X = 380, TRACK = ROWS * CELL, THUMB = 26;
    private record Pick(BulkListings.Entry entry, ItemStack icon, String name, String search) {}
    private record Tab(ShopConfig.Category category, ItemStack icon) {}
    private final AdminScreen parent;
    private final ShopConfig config;
    private final Set<BulkListings.Entry> selection = new LinkedHashSet<>();
    private final Map<BulkListings.Entry, Pick> catalog = new LinkedHashMap<>();
    private final List<Tab> tabs = new ArrayList<>();
    private final Map<String, String> searches = new LinkedHashMap<>();
    private List<Pick> tabValues = List.of(), values = List.of();
    private String categoryId, query = "", buy = "", sell = "", message = "", error = "";
    private int tabPage, topRow;
    private boolean initialized, scrollDragging;
    private Pick hovered;
    private Button setPrices, remove, clearPicks, selectAll;

    PriceAdjustScreen(AdminScreen parent, ShopConfig config, String categoryId) {
        super("Bulk Adjust"); this.parent = parent; this.config = config;
        this.categoryId = config.category(categoryId) == null ? "" : categoryId;
    }
    @Override protected void init() {
        if (!initialized) {
            tabs.add(new Tab(null, new ItemStack(Items.COMPASS)));
            for (var category : config.categories) tabs.add(new Tab(category, ShopIcons.category(category, minecraft.level.registryAccess(), category.name)));
            refreshCatalog();
            for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).category() != null && tabs.get(i).category().id.equals(categoryId)) tabPage = i / COLUMNS;
            initialized = true;
        }
        var search = new WorkbenchField(font, 80, 66, 422, Component.literal("Search shop listings"));
        search.setHint(Component.literal("Search listings in this tab...")); search.setMaxLength(128); search.setValue(query);
        search.setResponder(value -> { query = value; searches.put(categoryId, value); filter(true); }); addRenderableWidget(search);
        button("Clear", 508, 66, 72, () -> search.setValue(""));
        button("<", 62, 111, 18, () -> { tabPage--; rebuildWidgets(); }).active = tabPage > 0;
        button(">", 390, 111, 18, () -> { tabPage++; rebuildWidgets(); }).active = (tabPage + 1) * COLUMNS < tabs.size();
        for (int i = 0; i < COLUMNS && tabPage * COLUMNS + i < tabs.size(); i++) {
            var tab = tabs.get(tabPage * COLUMNS + i);
            addRenderableWidget(new CategoryTab(tab, GRID_X + i * CELL));
        }
        var buyField = new WorkbenchField(font, 432, 174, 136, Component.literal("New buy price"));
        buyField.setValue(buy); buyField.setResponder(value -> buy = value); addRenderableWidget(buyField);
        var sellField = new WorkbenchField(font, 432, 214, 136, Component.literal("New sell price"));
        sellField.setValue(sell); sellField.setResponder(value -> sell = value); addRenderableWidget(sellField);
        setPrices = button("Set prices", 432, 247, 136, this::setPrices);
        remove = button("Remove selected", 432, 279, 136, this::confirmRemove);
        selectAll = button("Select all", 80, 311, 156, () -> {
            boolean deselect = allSelected();
            for (var pick : tabValues) {
                if (deselect) selection.remove(pick.entry());
                else selection.add(pick.entry());
            }
            updateButtons();
        });
        clearPicks = button("Clear selection", 244, 311, 164, () -> { selection.clear(); updateButtons(); });
        button("Done", 472, 340, 108, this::onClose);
        filter(false); setInitialFocus(search);
    }
    private void refreshCatalog() {
        catalog.clear();
        for (var entry : BulkListings.entries(config)) {
            var item = entry.item();
            var stack = item.createStack(minecraft.level.registryAccess());
            String name = ListingGroups.displayName(item, minecraft.level.registryAccess());
            if (stack.isEmpty()) stack = new ItemStack(Items.BARRIER);
            catalog.put(entry, new Pick(entry, stack, name, searchable(name + " " + stack.getHoverName().getString() + " " + item.name + " " + item.item + " " + item.listingId + " " + item.description)));
        }
    }
    private static String searchable(String value) { return value.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' '); }
    private void filter(boolean reset) {
        String[] words = searchable(query).trim().split("\\s+");
        tabValues = catalog.values().stream().filter(pick -> categoryId.isBlank() || pick.entry().category().id.equals(categoryId)).toList();
        values = tabValues.stream().filter(pick -> { for (String word : words) if (!pick.search().contains(word)) return false; return true; }).toList();
        topRow = reset ? 0 : Math.min(topRow, maxRow()); hovered = null; updateButtons();
    }
    private void chooseTab(Tab tab) {
        categoryId = tab.category() == null ? "" : tab.category().id;
        query = searches.getOrDefault(categoryId, ""); topRow = 0; scrollDragging = false; error = "";
        filter(true); rebuildWidgets();
    }
    private int maxRow() { return Math.max(0, (values.size() + COLUMNS - 1) / COLUMNS - ROWS); }
    private boolean allSelected() { return !tabValues.isEmpty() && tabValues.stream().allMatch(pick -> selection.contains(pick.entry())); }
    private void updateButtons() {
        if (setPrices == null) return;
        setPrices.active = remove.active = clearPicks.active = !selection.isEmpty();
        selectAll.active = !tabValues.isEmpty();
        selectAll.setMessage(Component.literal(allSelected() ? "Deselect all" : "Select all"));
    }
    private static Double amount(String value) {
        if (value.isBlank()) return null;
        try { return Double.valueOf(value.trim()); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Enter prices from 0 to 1 billion."); }
    }
    private void setPrices() {
        try {
            var changed = BulkListings.prices(config, selection, amount(buy), amount(sell));
            parent.bulkUpdated(changed, false); error = "";
            message = "Updated " + changed.size() + " listings in your draft.";
        } catch (IllegalArgumentException invalid) { error = invalid.getMessage(); }
    }
    private void confirmRemove() {
        var picked = List.copyOf(selection);
        long categories = picked.stream().map(BulkListings.Entry::category).distinct().count();
        minecraft.gui.setScreen(new EditorDialog(this, "Remove selected listings?",
            "Remove " + picked.size() + " selected listings across " + categories + " categories from your draft? Categories stay in place. The server shop changes only when you Save changes.",
            null, "Remove " + picked.size() + " listings", "", ignored -> {
                var removed = BulkListings.remove(config, picked); parent.bulkUpdated(removed, true);
                selection.removeAll(removed); error = ""; message = "Removed " + removed.size() + " listings from your draft.";
                refreshCatalog(); filter(false);
            }));
    }
    @Override protected void paint(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        panel(graphics, 62, 26, 536, 346, WorkbenchTheme.SURFACE);
        text(graphics, "Bulk Adjust", 80, 41, WorkbenchTheme.TEXT);
        text(graphics, "Existing shop listings only", 338, 41, WorkbenchTheme.MUTED);
        var category = config.category(categoryId);
        String label = category == null ? "All listings" : category.name;
        text(graphics, font.plainSubstrByWidth(label + " | " + values.size() + " matches", 328), 80, 94, WorkbenchTheme.TEAL);
        panel(graphics, 420, 105, 160, 228, WorkbenchTheme.PANEL);
        text(graphics, "Selected: " + selection.size(), 432, 119, WorkbenchTheme.TEXT);
        long categories = selection.stream().map(BulkListings.Entry::category).distinct().count();
        text(graphics, "Across " + categories + " categories", 432, 137, WorkbenchTheme.MUTED);
        text(graphics, font.plainSubstrByWidth("Buy / item (" + config.currencySymbol + ")", 136), 432, 160, WorkbenchTheme.MUTED);
        text(graphics, font.plainSubstrByWidth("Sell / item (" + config.currencySymbol + ")", 136), 432, 200, WorkbenchTheme.MUTED);
        text(graphics, "Blank keeps price; 0 disables.", 432, 237, WorkbenchTheme.MUTED);
        hovered = null;
        panel(graphics, GRID_X - 4, GRID_Y - 3, 328, TRACK + 6, WorkbenchTheme.PREVIEW);
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
            graphics.item(pick.icon(), 0, 0); graphics.pose().popMatrix();
            if (selection.contains(pick.entry())) { graphics.outline(x, y, CELL, CELL, WorkbenchTheme.TEAL); text(graphics, "+", x + 23, y + 21, WorkbenchTheme.TEXT); }
            if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) {
                hovered = pick; graphics.outline(x, y, CELL, CELL, WorkbenchTheme.TEXT);
            }
        }
        graphics.fill(SCROLL_X, GRID_Y, SCROLL_X + 20, GRID_Y + TRACK, 0xFF8B8B8B);
        int thumbY = GRID_Y + (maxRow() == 0 ? 0 : Math.round((TRACK - THUMB) * topRow / (float) maxRow()));
        graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
            net.minecraft.resources.Identifier.withDefaultNamespace("container/creative_inventory/scroller" + (maxRow() == 0 ? "_disabled" : "")), SCROLL_X, thumbY, 20, THUMB);
        if (values.isEmpty()) {
            text(graphics, "No matching listings", GRID_X + 16, GRID_Y + 58, WorkbenchTheme.TEXT);
            text(graphics, "Clear search or choose another tab.", GRID_X + 16, GRID_Y + 72, WorkbenchTheme.TEXT);
        }
        String hint = error.isBlank() ? message.isBlank() ? "Pick listings across tabs. Save changes in the workbench." : message + " Save changes when ready." : error;
        int line = 0;
        for (var value : font.split(Component.literal(hint), 380)) graphics.text(font, value, 80, 340 + line++ * 12, error.isBlank() ? WorkbenchTheme.MUTED : WorkbenchTheme.DANGER, false);
    }
    @Override protected boolean click(double x, double y, int button) {
        if (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return false;
        if (x >= SCROLL_X && x < SCROLL_X + 20 && y >= GRID_Y && y < GRID_Y + TRACK && maxRow() > 0) {
            scrollDragging = true; scrollTo(y); return true;
        }
        int col = (int) ((x - GRID_X) / CELL), row = (int) ((y - GRID_Y) / CELL);
        if (x < GRID_X || x >= GRID_X + COLUMNS * CELL || y < GRID_Y || y >= GRID_Y + TRACK) return false;
        int index = topRow * COLUMNS + row * COLUMNS + col;
        if (index >= values.size()) return false;
        var entry = values.get(index).entry();
        if (!selection.remove(entry)) selection.add(entry);
        updateButtons(); return true;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        geometry(); int lx = logicalX(x), ly = logicalY(y);
        if (lx >= GRID_X && lx < SCROLL_X + 20 && ly >= GRID_Y && ly < GRID_Y + TRACK && vertical != 0) {
            topRow = Math.clamp(topRow - (int) Math.signum(vertical), 0, maxRow()); return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    private void scrollTo(double y) {
        double progress = Math.clamp((y - GRID_Y - THUMB / 2.0) / (TRACK - THUMB), 0, 1);
        topRow = (int) Math.round(progress * maxRow()); hovered = null;
    }
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (scrollDragging) { geometry(); scrollTo(logicalY(event.y())); return true; }
        return super.mouseDragged(event, dx, dy);
    }
    @Override protected boolean release(double x, double y, int button) {
        if (!scrollDragging) return false;
        scrollDragging = false; return true;
    }
    @Override protected void afterWidgets(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        int px = (int) (originX + x * scale), py = (int) (originY + y * scale);
        if (hovered != null) {
            var entry = hovered.entry(); var item = entry.item();
            var icon = hovered.icon().copy();
            icon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(hovered.name()));
            var lines = new ArrayList<Component>(icon.getOrDefault(net.minecraft.core.component.DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY).lines());
            lines.add(Component.literal(entry.category().name + " | " + item.listingId));
            lines.add(Component.literal("Buy: " + config.money(item.buy) + " | Sell: " + config.money(item.sell)));
            lines.add(Component.literal(selection.contains(entry) ? "Selected | Click to deselect" : "Click to select"));
            if (item.buy == 0 && item.sell == 0) lines.add(Component.literal("Disabled listing"));
            if (item.unavailableImport) lines.add(Component.literal("Content unavailable"));
            icon.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lines));
            graphics.setTooltipForNextFrame(font, icon, px, py);
        } else for (var child : children()) if (child instanceof CategoryTab tab && tab.isHovered())
            graphics.setTooltipForNextFrame(Component.literal(tab.getMessage().getString()), px, py);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }

    private final class CategoryTab extends Button {
        private final Tab tab;
        CategoryTab(Tab tab, int x) {
            super(x, 105, CELL, 28, Component.literal(tab.category() == null ? "All listings" : tab.category().name), ignored -> chooseTab(tab), DEFAULT_NARRATION);
            this.tab = tab;
        }
        @Override protected void extractContents(GuiGraphicsExtractor graphics, int x, int y, float delta) {
            boolean chosen = tab.category() == null ? categoryId.isBlank() : tab.category().id.equals(categoryId);
            WorkbenchTheme.rounded(graphics, getX(), getY(), getWidth(), getHeight(), 4,
                chosen ? WorkbenchTheme.SELECTED : isHoveredOrFocused() ? 0xFF344C54 : WorkbenchTheme.PANEL);
            graphics.item(tab.icon(), getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2);
            if (chosen) WorkbenchTheme.rounded(graphics, getX() + 5, getY() + 25, getWidth() - 10, 3, 1, WorkbenchTheme.TEAL);
        }
    }
}
