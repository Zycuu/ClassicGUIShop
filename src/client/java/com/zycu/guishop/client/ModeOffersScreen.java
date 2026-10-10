package com.zycu.guishop.client;

import com.zycu.guishop.ShopModes;
import com.zycu.guishop.ShopModesConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Server-detected IDs with editable draft payouts, including unavailable saved IDs. */
final class ModeOffersScreen extends WorkbenchScreen {
    private final AdminScreen parent;
    private final ShopModesConfig config;
    private final boolean advancements;
    private final List<ShopModes.Offer> catalog;
    private List<ShopModes.Offer> shown = List.of();
    private String query = "", error = "";
    private int page;
    ModeOffersScreen(AdminScreen parent, ShopModesConfig config, ShopModes.Catalog catalog, boolean advancements) {
        super(advancements ? "Advancement payouts" : "Entity payouts"); this.parent = parent; this.config = config; this.advancements = advancements;
        var entries = new LinkedHashMap<String, ShopModes.Offer>();
        for (var offer : advancements ? catalog.advancements() : catalog.entities()) entries.put(offer.id(), offer);
        for (var id : prices().keySet()) entries.putIfAbsent(id, new ShopModes.Offer(id, "Unavailable: " + id, 0));
        this.catalog = entries.values().stream().sorted(Comparator.comparing(ShopModes.Offer::id)).toList();
    }
    private Map<String, Double> prices() { return advancements ? config.advancementPrices : config.mobPrices; }
    @Override protected void init() {
        shown = catalog.stream().filter(value -> (value.id() + " " + value.name()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        page = Math.min(page, Math.max(0, (shown.size() - 1) / 9));
        var search = new WorkbenchField(font, 32, 58, 462, Component.literal("Search detected IDs")); search.setValue(query); search.setMaxLength(128); search.setResponder(value -> query = value); addRenderableWidget(search);
        button("Search", 508, 58, 120, () -> { page = 0; rebuildWidgets(); });
        for (int row = 0; row < 9 && page * 9 + row < shown.size(); row++) {
            var offer = shown.get(page * 9 + row); int y = 107 + row * 23;
            var field = new WorkbenchField(font, 496, y, 132, Component.literal("Payout " + offer.id()));
            double fallback = advancements ? !offer.id().startsWith("minecraft:") && offer.suggested() > 0 ? config.advancementDefault : offer.suggested() : config.mobDefault;
            field.setValue(Double.toString(prices().getOrDefault(offer.id(), fallback)));
            field.setMaxLength(18); field.setResponder(value -> {
                try { double amount = Double.parseDouble(value); if (!Double.isFinite(amount) || amount < 0 || amount > 1000000) throw new NumberFormatException(); prices().put(offer.id(), amount); error = ""; }
                catch (NumberFormatException invalid) { error = "Enter a finite payout from 0 to 1,000,000. Invalid text is not saved."; }
            }); addRenderableWidget(field);
        }
        button("Previous", 32, 338, 100, () -> { page = Math.max(0, page - 1); rebuildWidgets(); });
        button("Next", 140, 338, 100, () -> { page++; rebuildWidgets(); });
        button("Use defaults", 382, 338, 120, () -> minecraft.gui.setScreen(new EditorDialog(this, "Clear payout overrides?", "All overrides in this list will return to the mode defaults in your draft.", null, ignored -> { prices().clear(); rebuildWidgets(); })));
        button("Done", 514, 338, 114, this::onClose);
    }
    @Override protected void paint(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        panel(graphics, 16, 16, 628, 356, WorkbenchTheme.SURFACE); text(graphics, title.getString(), 32, 30, WorkbenchTheme.TEXT);
        text(graphics, "Detected ID / name", 32, 89, WorkbenchTheme.MUTED); text(graphics, "Payout (0 disables)", 496, 89, WorkbenchTheme.MUTED);
        for (int row = 0; row < 9 && page * 9 + row < shown.size(); row++) {
            var offer = shown.get(page * 9 + row); int y = 107 + row * 23;
            text(graphics, font.plainSubstrByWidth(offer.id(), 448), 32, y + 1, WorkbenchTheme.TEXT);
            text(graphics, font.plainSubstrByWidth(offer.name(), 448), 32, y + 11, WorkbenchTheme.MUTED);
        }
        text(graphics, error.isBlank() ? "Page " + (page + 1) + " | " + shown.size() + " entries | Draft changes require Apply." : error, 32, 320, error.isBlank() ? WorkbenchTheme.MUTED : WorkbenchTheme.DANGER);
    }
    @Override public void onClose() { parent.status("Mode payout edits remain in your draft until Apply."); minecraft.gui.setScreen(parent); }
}
