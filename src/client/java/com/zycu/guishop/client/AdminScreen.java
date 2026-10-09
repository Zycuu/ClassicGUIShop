package com.zycu.guishop.client;

import com.zycu.guishop.CategoryLayout;
import com.zycu.guishop.EnchantmentShopService;
import com.zycu.guishop.ShopConfig;
import com.zycu.guishop.VersionedListingStore;
import com.zycu.guishop.admin.AdminDocument;
import com.zycu.guishop.admin.DetectedContent;
import com.zycu.guishop.admin.AdminNetworking;
import com.zycu.guishop.admin.AdminPlayers;
import com.zycu.guishop.admin.TransactionJournal;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/** All edits affect a detached draft until the server acknowledges Apply. */
public final class AdminScreen extends WorkbenchScreen {
    private enum Tab { BUILDER, SETTINGS, ACCESS, DETECTED, TRANSACTIONS, HISTORY }
    private record Hit(int x, int y, int w, int h, String kind, String id, String category, ShopConfig.ShopItem item) {
        boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }
    private record Tile(String kind, String id, String name, String icon, ShopConfig.ShopItem item, ItemStack preview, String detail) {
        Tile(String kind, String id, String name, String icon, ShopConfig.ShopItem item) { this(kind, id, name, icon, item, null, ""); }
    }
    private AdminDocument draft;
    private String baseline, revision, status = "Draft ready. Changes stay local until Apply.";
    private Tab tab = Tab.BUILDER;
    private String categoryId = "", query = "", selectedItem, settingsSection = "Economy";
    private int browserPage, previewPage, accessPage, logPage, logOffset;
    private boolean arranging, sellMode, busy;
    private boolean enchantPreview;
    private String enchantId;
    private TransactionJournal.Page logData = new TransactionJournal.Page(List.of(), 0, false, "");
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, String> errors = new HashMap<>();
    private final Map<String, String> invalidInputs = new HashMap<>();
    private final List<EditBox> fields = new ArrayList<>();
    private Hit dragging;
    private boolean dragged;
    private EditBox search;
    private String heldCategory;
    private int logRetryTicks, retryOffset;
    private double pressedX, pressedY;
    private AdminPlayers.Page playerData = new AdminPlayers.Page(List.of(), 0, false);
    private AdminPlayers.Player selectedPlayer;
    private DetectedContent.Page detectedData = new DetectedContent.Page(List.of(), List.of(), 0, false, false, "", 0);
    private String detectedSource = "", importSource;
    private int sourcePage, detectedOffset, detectionRetry, importOffset, importAdded;
    private boolean detectionRefresh;
    private DetectedContent.Entry detectedSelection;
    private long detectionGeneration;

    public AdminScreen(AdminDocument document, String revision) {
        super("ClassicGUIShop Adminshop"); load(document, revision, false);
    }
    public void load(AdminDocument document, String revision, boolean saved) {
        draft = document.copy(); baseline = AdminDocument.JSON.toJson(draft); this.revision = revision;
        cancelDrag(); importSource = null; detectionRetry = 0; busy = false; errors.clear(); invalidInputs.clear();
        if (!categoryId.isEmpty() && draft.config.category(categoryId) == null) categoryId = "";
        selectedItem = null;
        enchantPreview = false; enchantId = null;
        status = saved ? "Applied on the server. Previous configuration backed up." : "Draft ready. Changes stay local until Apply.";
        if (width > 0) rebuildWidgets();
    }
    public void status(String value) { status = value; }
    public void restore(com.zycu.guishop.admin.AdminEditorService.RestoreDraft restored) {
        baseline = AdminDocument.JSON.toJson(restored.live()); draft = restored.previous().copy(); revision = restored.revision();
        cancelDrag(); importSource = null; detectionRetry = 0; categoryId = ""; selectedItem = null; enchantPreview = false; enchantId = null;
        previewPage = 0; browserPage = 0; busy = false; errors.clear(); invalidInputs.clear(); tab = Tab.BUILDER;
        status = "Previous configuration loaded into a draft. Review it, then Apply to restore."; rebuildWidgets();
    }
    public void failure(String value) { importSource = null; detectionRetry = 0; busy = false; status = value; if (width > 0) rebuildWidgets(); }
    public void addHeld(ShopConfig.ShopItem item) {
        if (heldCategory != null) {
            String destination = heldCategory; heldCategory = null;
            if (draft.config.category(destination) == null) { status = "The destination was removed. Select a category and add the held item again."; return; }
            categoryId = destination;
        }
        if (category() == null) { status = "Select a category before adding an item."; return; }
        addListing(item); rebuildWidgets();
    }
    private boolean matches(AdminNetworking.Query request) {
        return request != null && (tab == Tab.HISTORY || tab == Tab.TRANSACTIONS)
            && request.changes() == (tab == Tab.HISTORY)
            && java.util.Objects.equals(request.playerUuid(), selectedPlayer == null ? null : selectedPlayer.uuid())
            && java.util.Objects.equals(request.filter(), query);
    }
    public void logs(TransactionJournal.Page page, AdminNetworking.Query request) {
        if (!matches(request)) return;
        logData = page; logOffset = page.offset(); logPage = 0;
        status = page.warning().isBlank() ? "Recent server records loaded. Times are UTC." : page.warning(); rebuildWidgets();
    }
    public void players(AdminPlayers.Page page, String filter) {
        if (tab != Tab.TRANSACTIONS || selectedPlayer != null || !query.equals(filter)) return;
        playerData = page; logOffset = page.offset(); logPage = 0;
        status = "Select a player to view their transactions. Balances are current at refresh."; rebuildWidgets();
    }
    public void playerHistory(AdminPlayers.History history, AdminNetworking.Query request) {
        if (!matches(request) || !history.player().uuid().equals(request.playerUuid())) return;
        selectedPlayer = history.player(); logs(history.history(), request);
    }
    public void detected(DetectedContent.Page page, DetectedContent.Query request) {
        if (tab != Tab.DETECTED || request == null || request.generation() != detectionGeneration) return;
        if (request.importing()) {
            if (importSource == null || !importSource.equals(request.source()) || request.offset() != importOffset) return;
            if (!page.ready()) { detectionRetry = 10; return; }
            String name = sourceName(page, importSource);
            for (var entry : page.entries()) if (importDetected(entry, importSource.isBlank() ? "all_detected" : importSource, name)) importAdded++;
            if (page.more()) { importOffset = page.offset() + page.entries().size(); detectionRetry = 6; }
            else { importSource = null; status = "Imported " + importAdded + " new listings into the draft. Buy 0 / Sell 0. Review in Shop Builder, then Apply."; }
            rebuildWidgets(); return;
        }
        if (importSource != null || !request.source().equals(detectedSource) || !request.filter().equals(query) || request.offset() != detectedOffset) return;
        detectedData = page; detectedOffset = page.offset(); detectedSelection = null;
        if (!page.ready()) { detectionRetry = 10; status = page.warning(); }
        else status = page.warning().isBlank() ? "Loaded content detected. Imports stay in the draft with Buy 0 / Sell 0." : page.warning();
        rebuildWidgets();
    }
    private static String sourceName(DetectedContent.Page page, String source) {
        return source.isBlank() ? "All detected content" : page.sources().stream().filter(value -> value.id().equals(source)).map(DetectedContent.Source::name).findFirst().orElse(source);
    }
    private boolean importDetected(DetectedContent.Entry entry, String destination, String sourceName) {
        var category = draft.config.categories.stream().filter(value -> destination.equals(value.detectedSource)).findFirst().orElse(null);
        if (category == null) {
            if (draft.config.categories.size() >= 250) throw new IllegalArgumentException("Category limit reached. Remove an unused category before importing.");
            category = new ShopConfig.Category(); category.id = uniqueId("detected_" + destination, draft.config.categories.stream().map(value -> value.id).toList());
            String name = sourceName.replaceAll("[\\p{Cntrl}]", " ");
            category.name = name.length() > 80 ? name.substring(0, 80) : name;
            category.icon = entry.listing().item; category.iconStack = entry.listing().stack.deepCopy(); category.detectedSource = destination; draft.config.categories.add(category);
        }
        var access = minecraft.level.registryAccess(); var template = entry.listing().createStack(access);
        if (category.items.stream().anyMatch(item -> com.zycu.guishop.ItemStackData.same(item.createStack(access), template))) return false;
        if (draft.config.categories.stream().mapToInt(value -> value.items.size()).sum() >= 10000) throw new IllegalArgumentException("Listing limit reached. Review this partial import before applying.");
        var listing = AdminDocument.JSON.fromJson(AdminDocument.JSON.toJson(entry.listing()), ShopConfig.ShopItem.class);
        java.util.Set<String> used = new java.util.HashSet<>();
        for (var existing : draft.config.categories) for (var item : existing.items) used.add(item.listingId.toLowerCase(Locale.ROOT));
        listing.listingId = com.zycu.guishop.ItemStackData.unusedListingId(listing.listingId, used);
        listing.name = template.getHoverName().getString().replaceAll("[\\p{Cntrl}]", " ");
        if (listing.name.length() > 120) listing.name = listing.name.substring(0, 120);
        listing.buy = 0; listing.sell = 0; listing.manualPrice = true;
        category.items.add(listing); return true;
    }
    private void requestDetected(int offset, boolean refresh) {
        if (importSource == null) detectionGeneration++;
        detectedOffset = offset; detectionRefresh = refresh; detectedSelection = null;
        var request = importSource == null ? new DetectedContent.Query(detectedSource, query, offset, refresh, false, detectionGeneration)
            : new DetectedContent.Query(importSource, "", importOffset, false, true, detectionGeneration);
        if (AdminShopClient.request("detected", "", AdminDocument.JSON.toJson(request))) {
            detectionRetry = 0; detectionRefresh = false;
            status = importSource == null ? "Loading detected content..." : "Importing into the draft... " + importAdded + " listings added, all prices 0.";
        } else detectionRetry = 6;
    }
    public void tickNetworking() {
        if (detectionRetry > 0 && --detectionRetry == 0 && tab == Tab.DETECTED) requestDetected(detectedOffset, detectionRefresh);
        if (logRetryTicks > 0 && --logRetryTicks == 0 && (tab == Tab.HISTORY || tab == Tab.TRANSACTIONS)) requestRecords(retryOffset);
    }
    private boolean dirty() { return !errors.isEmpty() || !baseline.equals(AdminDocument.JSON.toJson(draft)); }
    private ShopConfig.Category category() { return draft.config.category(categoryId); }
    private ShopConfig.ShopItem selected() {
        if (category() == null || selectedItem == null) return null;
        return category().items.stream().filter(item -> item.listingId.equals(selectedItem)).findFirst().orElse(null);
    }
    @Override protected void init() {
        fields.clear(); hits.clear();
        String[] names = {"Shop Builder", "Settings", "Access", "Detected Content", "Transactions", "Changes"};
        net.minecraft.world.item.Item[] icons = {Items.CRAFTING_TABLE, Items.COMPARATOR, Items.IRON_DOOR, Items.SPYGLASS, Items.GOLD_INGOT, Items.WRITABLE_BOOK};
        int[] xs = {16, 133, 225, 305, 439, 557}, ws = {111, 86, 74, 128, 112, 87};
        for (int index = 0; index < names.length; index++) {
            Tab target = Tab.values()[index];
            var button = addRenderableWidget(new WorkbenchTab(names[index], xs[index], ws[index], icons[index], target == tab, () -> {
                if (busy) return;
                cancelDrag(); importSource = null; detectionRetry = 0; tab = target; query = ""; selectedPlayer = null; logPage = 0; logOffset = 0;
                logData = new TransactionJournal.Page(List.of(), 0, false, ""); playerData = new AdminPlayers.Page(List.of(), 0, false); errors.entrySet().removeIf(entry -> entry.getKey().startsWith("view:"));
                rebuildWidgets();
                if (target == Tab.TRANSACTIONS || target == Tab.HISTORY) requestRecords(0);
                if (target == Tab.DETECTED) requestDetected(0, false);
            })); button.active = !busy;
        }
        button("Apply", 464, 352, 88, this::apply).active = !busy && importSource == null;
        button("Discard", 558, 352, 86, () -> {
            if (busy) return;
            confirm("Discard draft?", "Your unapplied edits will be discarded. The live server shop stays unchanged.", () -> {
                importSource = null; detectionRetry = 0;
                draft = AdminDocument.JSON.fromJson(baseline, AdminDocument.class); errors.clear(); invalidInputs.clear(); selectedItem = null; status = "Draft discarded.";
                categoryId = ""; enchantPreview = false; enchantId = null; previewPage = 0; browserPage = 0;
            });
        }).active = !busy;
        button("Close", 16, 352, 70, this::onClose).active = !busy;
        button("Reload", 92, 352, 76, () -> {
            if (dirty()) confirm("Reload server shop?", "Reloading discards this local draft and fetches the current server configuration.", () -> AdminShopClient.request("open", "", ""));
            else AdminShopClient.request("open", "", "");
        }).active = !busy;
        if (tab == Tab.BUILDER) builder();
        else if (tab == Tab.SETTINGS) settings();
        else if (tab == Tab.ACCESS) access();
        else if (tab == Tab.DETECTED) detection();
        else history();
        if (busy) for (var field : fields) field.setEditable(false);
    }

    private void builder() {
        button("+ Category", 22, 76, 124, () -> createCategory());
        button("<", 22, 316, 38, () -> { browserPage = Math.max(0, browserPage - 1); rebuildWidgets(); });
        button(">", 108, 316, 38, () -> { browserPage++; rebuildWidgets(); });
        button("Home", 166, 76, 62, this::home);
        button(arranging ? "Exit arrange mode" : "Arrange mode", 234, 76, 157, () -> {
            cancelDrag(); arranging = !arranging; query = ""; previewPage = 0; selectedItem = null;
            status = arranging ? "Arrange mode: drag listings or categories. Disabled listings are shown too." : "Shop preview. Use the shop's gold or emerald button to switch Buy/Sell.";
            rebuildWidgets();
        });
        button("Up", 397, 76, 55, this::up);
        search = field("Search listings", 166, 107, 286, query, value -> query = value);
        search.setHint(Component.literal("Search by name or item ID..."));
        search.setResponder(value -> { query = value; previewPage = 0; });
        search.setEditable(arranging);
        button("<", 166, 316, 40, () -> { previewPage = Math.max(0, previewPage - 1); });
        button(">", 412, 316, 40, () -> { previewPage++; });
        inspector();
    }
    private void home() {
        cancelDrag(); categoryId = ""; selectedItem = null; enchantPreview = false; enchantId = null; previewPage = 0; rebuildWidgets();
    }
    private void up() {
        if (enchantPreview) { if (enchantId != null) enchantId = null; else enchantPreview = false; }
        else categoryId = "";
        selectedItem = null; previewPage = 0; rebuildWidgets();
    }
    private void switchMode() {
        sellMode = !sellMode; previewPage = 0;
        if (!arranging && !enchantPreview) { selectedItem = null; }
        rebuildWidgets();
    }
    private void inspector() {
        var item = selected(); var category = category();
        if (item != null) {
            field("Display name", 472, 107, 164, item.name, value -> item.name = value);
            number("Buy price", 472, 147, 164, item.buy, value -> { item.buy = value; item.manualPrice = true; });
            number("Sell price", 472, 187, 164, item.sell, value -> { item.sell = value; item.manualPrice = true; });
            button("Move listing...", 472, 219, 164, () -> minecraft.gui.setScreen(new DestinationScreen(this, draft, cat -> move(item, cat))));
            button("Remove listing", 472, 245, 164, () -> confirm("Remove listing?", item.name + " will be removed when you Apply. The previous configuration will be backed up.", () -> {
                category.items.remove(item); selectedItem = null;
            }));
            button("Move earlier", 472, 276, 79, () -> reorder(category.items, item, -1));
            button("Move later", 557, 276, 79, () -> reorder(category.items, item, 1));
            field("Tooltip description", 472, 313, 164, item.description, value -> item.description = value).setMaxLength(160);
        } else if (category != null) {
            field("Category name", 472, 107, 164, category.name, value -> category.name = value);
            button("Choose icon...", 472, 141, 164, () -> minecraft.gui.setScreen(new IconPickerScreen(this, "Choose category icon", false, icons -> {
                var icon = icons.getFirst(); category.icon = icon.item; category.iconStack = icon.stack.deepCopy();
            })));
            button("+ Select item", 472, 181, 164, () -> minecraft.gui.setScreen(new IconPickerScreen(this, "Add item listings", true, items -> {
                if (draft.config.categories.stream().mapToInt(value -> value.items.size()).sum() + items.size() > 10000) throw new IllegalArgumentException("Listing limit reached. Select fewer items.");
                categoryId = category.id;
                for (var itemToAdd : items) addListing(itemToAdd);
            })));
            button("+ Add held item", 472, 207, 164, () -> {
                if (AdminShopClient.request("held", "", "")) { heldCategory = categoryId; }
            });
            button("Remove category", 472, 245, 164, () -> confirm("Remove category?", "This also removes its " + category.items.size() + " listings when you Apply.", () -> {
                draft.config.categories.remove(category); categoryId = ""; selectedItem = null;
            }));
            button("Move earlier", 472, 276, 79, () -> reorder(draft.config.categories, category, -1));
            button("Move later", 557, 276, 79, () -> reorder(draft.config.categories, category, 1));
            field("Tooltip description", 472, 313, 164, category.description, value -> category.description = value).setMaxLength(160);
        }
    }
    private void createCategory() {
        minecraft.gui.setScreen(new EditorDialog(this, "Create category", "Choose the name players will see in /shop.", "New Category", name -> {
            var category = new ShopConfig.Category(); category.id = uniqueId(name, draft.config.categories.stream().map(value -> value.id).toList());
            category.name = name; category.icon = "minecraft:chest"; draft.config.categories.add(category);
            categoryId = category.id; selectedItem = null; previewPage = 0;
        }));
    }
    private static String uniqueId(String name, List<String> used) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_").replaceAll("_+", "_");
        if (base.length() > 64) base = base.substring(0, 64); if (base.isBlank()) base = "category";
        String id = base; for (int suffix = 2; used.contains(id); suffix++) id = base + "_" + suffix; return id;
    }
    private void addListing(ShopConfig.ShopItem item) {
        java.util.Set<String> used = new java.util.HashSet<>();
        for (var category : draft.config.categories) for (var listing : category.items) used.add(listing.listingId.toLowerCase(Locale.ROOT));
        item.listingId = com.zycu.guishop.ItemStackData.unusedListingId(item.listingId, used);
        item.buy = 0; item.sell = 0; item.manualPrice = true;
        category().items.add(item); selectedItem = item.listingId; arranging = true;
        status = "Listing added to draft with prices disabled. Set buy and sell prices before applying.";
    }
    private void move(ShopConfig.ShopItem item, String destination) {
        var old = draft.config.findItem(item.listingId); var target = draft.config.category(destination);
        if (old == null || target == null) return;
        old.category().items.remove(item);
        target.items.add(item);
        categoryId = destination; selectedItem = item.listingId; previewPage = 0;
        status = "Listing moved in the draft."; rebuildWidgets();
    }
    private <T> void reorder(List<T> list, T item, int direction) {
        int from = list.indexOf(item), to = from + direction;
        if (from >= 0 && to >= 0 && to < list.size()) { list.remove(from); list.add(to, item); rebuildWidgets(); }
    }
    private <T> void placeAt(List<T> list, T item, T target) {
        int from = list.indexOf(item), destination = list.indexOf(target);
        if (from < 0 || destination < 0 || from == destination) return;
        // Capture the destination before removal, so dragging forward reaches that position too.
        list.remove(from); list.add(destination, item); rebuildWidgets();
    }

    private void settings() {
        int x = 24;
        for (String section : List.of("Economy", "Messages", "Enchantments")) {
            String name = section; button((name.equals(settingsSection) ? "* " : "") + name, x, 78, 190, () -> { settingsSection = name; rebuildWidgets(); }); x += 208;
        }
        var config = draft.config;
        if (settingsSection.equals("Economy")) {
            field("Currency prefix (display only)", 32, 142, 276, config.currencySymbol, value -> config.currencySymbol = value);
            number("Starting balance for new players", 350, 142, 276, config.startingBalance, value -> config.startingBalance = value);
            number("Global buy and sell multiplier", 32, 196, 276, config.priceMultiplier, value -> config.priceMultiplier = value);
            number("Enchanted book sell ratio (0 to 1)", 350, 196, 276, config.catalogSellRatio, value -> config.catalogSellRatio = value);
            toggle("Creative transactions", 32, 240, 276, config.creativeTransactionsAllowed(), value -> config.allowCreativeTransactions = value);
            toggle("Offline payments", 350, 240, 276, config.offlinePaymentsAllowed(), value -> config.allowOfflinePayments = value);
            toggle("Enchanted book shop", 32, 274, 276, config.enchantmentsEnabled(), value -> config.enchantmentsEnabled = value);
            field("Shop title", 350, 274, 276, config.shopTitle, value -> config.shopTitle = value).setMaxLength(64);
        } else if (settingsSection.equals("Messages")) {
            field("Chat prefix", 32, 142, 276, config.chatPrefix, value -> config.chatPrefix = value);
            field("Prefix color", 350, 142, 276, config.chatPrefixColor, value -> config.chatPrefixColor = value);
            field("Information color", 32, 196, 276, config.chatInfoColor, value -> config.chatInfoColor = value);
            field("Success color", 350, 196, 276, config.chatSuccessColor, value -> config.chatSuccessColor = value);
            field("Warning color", 32, 250, 276, config.chatWarningColor, value -> config.chatWarningColor = value);
            field("Error color", 350, 250, 276, config.chatErrorColor, value -> config.chatErrorColor = value);
        } else {
            List<Map.Entry<String, ShopConfig.EnchantmentOffer>> offers = new ArrayList<>(config.enchantments.entrySet());
            int pages = Math.max(1, (offers.size() + 4) / 5); accessPage = Math.min(accessPage, pages - 1);
            for (int row = 0; row < 5 && accessPage * 5 + row < offers.size(); row++) {
                var entry = offers.get(accessPage * 5 + row); var offer = entry.getValue(); int y = 133 + row * 35;
                field(entry.getKey(), 32, y, 245, offer.name, value -> offer.name = value);
                number("Price / level", 291, y, 104, offer.pricePerLevel, value -> offer.pricePerLevel = value);
                number("Max level", 409, y, 88, offer.maxLevel, value -> offer.maxLevel = (int) value);
                toggle("Enabled", 511, y, 116, offer.enabled(), value -> offer.enabled = value);
            }
            button("Previous", 32, 313, 110, () -> { accessPage = Math.max(0, accessPage - 1); rebuildWidgets(); });
            button("Next", 150, 313, 110, () -> { accessPage = Math.min(pages - 1, accessPage + 1); rebuildWidgets(); });
            button("+ Offer", 506, 313, 120, () -> minecraft.gui.setScreen(new EditorDialog(this, "Add enchantment offer", "Enter a registered enchantment ID, for example minecraft:unbreaking.", "minecraft:unbreaking", id -> {
                String key = ShopConfig.normalizeIdentifier(id);
                if (!config.enchantments.containsKey(key)) { var offer = new ShopConfig.EnchantmentOffer(); offer.name = key; offer.pricePerLevel = 0; config.enchantments.put(key, offer); }
            })));
        }
    }
    private void access() {
        List<Map.Entry<String, Integer>> entries = draft.config.permissionDefaults.entrySet().stream()
            .filter(entry -> !entry.getKey().contains("admin") && !entry.getKey().contains("ident")).sorted(Map.Entry.comparingByKey()).toList();
        int pages = Math.max(1, (entries.size() + 6) / 7); accessPage = Math.min(accessPage, pages - 1);
        for (int index = 0; index < 7 && accessPage * 7 + index < entries.size(); index++) {
            var entry = entries.get(accessPage * 7 + index); int y = 127 + index * 26;
            number(entry.getKey(), 450, y, 160, entry.getValue(), value -> draft.config.permissionDefaults.put(entry.getKey(), (int) value));
        }
        button("Previous", 32, 313, 110, () -> { accessPage = Math.max(0, accessPage - 1); rebuildWidgets(); });
        button("Next", 150, 313, 110, () -> { accessPage = Math.min(pages - 1, accessPage + 1); rebuildWidgets(); });
    }
    private void detection() {
        search = field("Search detected items", 184, 110, 346, query, value -> query = value); search.setMaxLength(128);
        search.setHint(Component.literal("Search item name, ID, or source path...")); search.setEditable(importSource == null);
        button("Search", 536, 110, 94, () -> requestDetected(0, false)).active = importSource == null;
        button("Rescan", 536, 76, 94, () -> requestDetected(0, true)).active = importSource == null;
        button("<", 24, 313, 40, () -> { sourcePage = Math.max(0, sourcePage - 1); rebuildWidgets(); });
        button(">", 118, 313, 40, () -> { sourcePage++; rebuildWidgets(); });
        button("Previous", 184, 313, 84, () -> requestDetected(Math.max(0, detectedOffset - DetectedContent.PAGE_SIZE), false)).active = importSource == null && detectedOffset > 0;
        button("Next", 274, 313, 68, () -> requestDetected(detectedOffset + DetectedContent.PAGE_SIZE, false)).active = importSource == null && detectedData.more();
        button("Import item", 350, 313, 102, () -> {
            if (detectedSelection == null) return;
            String destination = detectedSource.isBlank() ? detectedSelection.source() : detectedSource;
            String name = sourceName(detectedData, destination);
            try {
                boolean added = importDetected(detectedSelection, destination, name);
                status = added ? "Imported to its source category in the draft. Buy 0 / Sell 0. Review, then Apply." : "That exact variant is already in its source category. Existing prices were preserved.";
            } catch (IllegalArgumentException invalid) { status = invalid.getMessage(); }
            rebuildWidgets();
        }).active = importSource == null && detectedSelection != null;
        String action = detectedSource.isBlank() ? "Import all" : detectedSource.startsWith("datapack_") ? "Import pack" : "Import source";
        button(importSource == null ? action : "Cancel import", 460, 313, 170, () -> {
            if (importSource != null) { importSource = null; detectionRetry = 0; status = "Import stopped. Items already added remain in the draft with prices 0."; rebuildWidgets(); }
            else confirm("Import into one category?", "Add ALL items across ALL pages to one category. Search is ignored. New prices stay 0; existing prices are preserved. Apply saves later.", () -> {
                detectionGeneration++; importSource = detectedSource; importOffset = 0; importAdded = 0; requestDetected(0, false);
            });
        }).active = importSource != null || detectedData.ready();
    }
    private void paintDetected(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        panel(graphics, 16, 69, 148, 270, 0xFFB7B9AB); panel(graphics, 172, 69, 472, 270, 0xFFC6C8BB);
        text(graphics, "CONTENT SOURCES", 24, 82, 0xFF234747);
        text(graphics, "New imports: Buy 0 / Sell 0", 184, 82, 0xFF234747);
        text(graphics, "Select a Pack: row, then Import pack for one category.", 184, 96, 0xFF3B4940);
        var sources = new ArrayList<>(detectedData.sources());
        sourcePage = Math.min(sourcePage, Math.max(0, (sources.size() - 1) / 9));
        graphics.fill(22, 109, 158, 127, detectedSource.isBlank() ? 0xFF78978B : 0xFFA6AC9D);
        text(graphics, "All sources", 28, 114, 0xFF202F2D); hits.add(new Hit(22, 109, 136, 18, "detected:source", "", "", null));
        for (int row = 0; row < 9 && sourcePage * 9 + row < sources.size(); row++) {
            var source = sources.get(sourcePage * 9 + row); int y = 133 + row * 19;
            graphics.fill(22, y, 158, y + 18, source.id().equals(detectedSource) ? 0xFF78978B : 0xFFA6AC9D);
            text(graphics, font.plainSubstrByWidth(source.name() + " (" + source.count() + ")", 126), 28, y + 5, 0xFF202F2D);
            hits.add(new Hit(22, y, 136, 18, "detected:source", source.id(), "", null));
        }
        text(graphics, Integer.toString(sourcePage + 1), 85, 322, 0xFF33443B);
        text(graphics, "Page " + (detectedOffset / DetectedContent.PAGE_SIZE + 1) + " | Unsupported definitions: " + detectedData.skipped(), 184, 283, 0xFF303E36);
        text(graphics, detectedData.ready() ? font.plainSubstrByWidth(sourceName(detectedData, detectedSource) + " | Whole imports include every page.", 440) : "Missing a variant? Use Add held item in Shop Builder.", 184, 296, 0xFF303E36);
        for (int index = 0; index < detectedData.entries().size(); index++) {
            var entry = detectedData.entries().get(index); int x = 184 + index % 4 * 112, y = 141 + index / 4 * 23;
            ItemStack item = entry.listing().createStack(minecraft.level.registryAccess());
            boolean selected = entry.equals(detectedSelection);
            graphics.fill(x, y, x + 108, y + 22, selected ? 0xFF78978B : 0xFFB8BFAE);
            graphics.item(item, x + 2, y + 3);
            text(graphics, font.plainSubstrByWidth(item.getHoverName().getString(), 86), x + 21, y + 7, 0xFF202F2D);
            hits.add(new Hit(x, y, 108, 22, "detected:item", Integer.toString(index), "", null));
            if (x <= mouseX && mouseX < x + 108 && y <= mouseY && mouseY < y + 22) {
                graphics.fill(182, 281, 634, 310, 0xFFC6C8BB);
                text(graphics, font.plainSubstrByWidth(item.getHoverName().getString() + " | " + entry.listing().item, 440), 184, 283, 0xFF303E36);
                text(graphics, font.plainSubstrByWidth(entry.origin(), 440), 184, 296, 0xFF303E36);
            }
        }
        if (detectedData.entries().isEmpty()) text(graphics, detectedData.ready() ? "No matching detected items. Held-item import is also available." : "Inspecting server content...", 184, 169, 0xFF3B4940);
    }
    private void history() {
        boolean roster = tab == Tab.TRANSACTIONS && selectedPlayer == null;
        search = field(roster ? "Search players" : "Filter transactions", 32, 110, 450, query, value -> query = value);
        search.setMaxLength(128);
        search.setHint(Component.literal(roster ? "Search player name or UUID..." : "Search item, action, or UTC date..."));
        if (selectedPlayer != null) button("Players", 494, 76, 130, () -> {
            selectedPlayer = null; query = ""; logPage = 0; requestRecords(0); rebuildWidgets();
        });
        button("Search", 494, 110, 130, () -> requestRecords(0));
        button("Previous", 32, 313, 100, () -> { if (logPage > 0) logPage--; else if (logOffset > 0) requestRecords(Math.max(0, logOffset - 100)); });
        button("Next", 140, 313, 100, () -> {
            int count = roster ? playerData.players().size() : logData.entries().size();
            boolean more = roster ? playerData.more() : logData.more();
            if ((logPage + 1) * 10 < count) logPage++;
            else if (more) requestRecords(logOffset + 100);
        });
        button("Refresh", 494, 313, 130, () -> requestRecords(logOffset));
    }
    private void requestRecords(int offset) {
        retryOffset = offset; logPage = 0;
        String action, body;
        if (tab == Tab.TRANSACTIONS && selectedPlayer == null) {
            playerData = new AdminPlayers.Page(List.of(), offset, false);
            action = "players"; body = AdminDocument.JSON.toJson(new AdminNetworking.PlayersQuery(query, offset));
        } else {
            logData = new TransactionJournal.Page(List.of(), offset, false, "");
            action = "logs"; body = AdminDocument.JSON.toJson(new AdminNetworking.Query(query, offset, tab == Tab.HISTORY, selectedPlayer == null ? null : selectedPlayer.uuid()));
        }
        if (AdminShopClient.request(action, "", body)) { status = "Loading server records..."; logRetryTicks = 0; }
        else logRetryTicks = 6;
    }
    private EditBox field(String label, int x, int y, int width, String value, Consumer<String> changed) {
        var field = new EditBox(font, x, y, width, 20, Component.literal(label));
        field.setMaxLength(200); field.setValue(value == null ? "" : value);
        field.setResponder(changed); fields.add(field); addRenderableWidget(field); return field;
    }
    private void number(String label, int x, int y, int width, double value, DoubleConsumer changed) {
        String context = tab == Tab.BUILDER ? selectedItem : tab == Tab.SETTINGS && settingsSection.equals("Enchantments") ? "offer:" + accessPage + ":" + y : tab.name();
        String key = context + ":" + label;
        var field = field(label, x, y, width, invalidInputs.getOrDefault(key, number(value)), ignored -> {});
        field.setResponder(input -> {
            try {
                double parsed = Double.parseDouble(input);
                if (!Double.isFinite(parsed) || parsed < 0) throw new NumberFormatException();
                if ((label.equals("Max level") || label.startsWith("guishop.")) && parsed != Math.rint(parsed)) throw new NumberFormatException();
                if (label.startsWith("guishop.") && parsed > 4 || label.equals("Max level") && parsed > 255) throw new NumberFormatException();
                errors.remove(key); invalidInputs.remove(key); changed.accept(parsed);
            } catch (NumberFormatException exception) {
                invalidInputs.put(key, input);
                errors.put(key, label + " has an invalid number. Correct it or discard the draft.");
            }
        });
    }
    private void toggle(String label, int x, int y, int width, boolean value, Consumer<Boolean> changed) {
        button(label + ": " + (value ? "On" : "Off"), x, y, width, () -> { changed.accept(!value); rebuildWidgets(); });
    }
    private static String number(double value) { return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value); }
    private void apply() {
        if (busy) return;
        if (importSource != null) { status = "Wait for the import to finish, or cancel it before applying."; return; }
        if (!errors.isEmpty()) { status = errors.values().iterator().next(); return; }
        if (!dirty()) { status = "No changes to apply."; return; }
        if (AdminShopClient.request("apply", revision, AdminDocument.JSON.toJson(draft))) {
            busy = true; status = "Applying draft on the server..."; rebuildWidgets();
        }
    }
    private void confirm(String title, String message, Runnable accepted) {
        minecraft.gui.setScreen(new EditorDialog(this, title, message, null, ignored -> accepted.run()));
    }
    @Override public void onClose() {
        if (busy) { status = "Wait for the server to finish applying this draft."; return; }
        if (dirty()) confirm("Close without applying?", "Unapplied edits will be discarded. The live server shop stays unchanged.", () -> { importSource = null; detectionRetry = 0; minecraft.gui.setScreen(null); });
        else { importSource = null; detectionRetry = 0; minecraft.gui.setScreen(null); }
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (busy) return true;
        return super.keyPressed(event);
    }

    @Override protected void paint(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        hits.clear(); panel(graphics, 0, 0, W, H, 0xFFD0CEC3);
        graphics.fill(2, 2, W - 2, 37, 0xFF293D40);
        text(graphics, "CLASSIC GUI SHOP", 16, 12, 0xFFF0EBCE);
        text(graphics, "Admin Workbench", 194, 12, 0xFF81CDBD);
        text(graphics, "1.4.0 ALPHA", 556, 12, 0xFFC5CCBD);
        graphics.fill(2, 35, W - 2, 38, 0xFF81B7A5);
        if (tab == Tab.BUILDER) paintBuilder(graphics, mouseX, mouseY);
        else if (tab == Tab.SETTINGS) paintSettings(graphics);
        else if (tab == Tab.ACCESS) paintAccess(graphics);
        else if (tab == Tab.DETECTED) paintDetected(graphics, mouseX, mouseY);
        else paintHistory(graphics, mouseX, mouseY);
        text(graphics, font.plainSubstrByWidth(status, 620), 20, 339, busy ? 0xFF75591B : 0xFF344442);
        text(graphics, dirty() ? "Unapplied changes" : "Matches server snapshot", 183, 358, dirty() ? 0xFF75591B : 0xFF365D51);
    }
    @Override protected void afterWidgets(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        if (arranging && dragged && dragging != null) {
            var category = draft.config.category(dragging.id);
            ItemStack ghost = dragging.item != null && minecraft.level != null ? dragging.item.createStack(minecraft.level.registryAccess())
                : category != null ? com.zycu.guishop.ShopIcons.category(category, minecraft.level.registryAccess(), category.name) : ItemStack.EMPTY;
            if (!ghost.isEmpty()) graphics.item(ghost, x - 8, y - 8);
            for (Hit target : hits) if (target.contains(x, y) && (target.item != null || target.kind.endsWith("category") || target.kind.equals("tile:empty"))) {
                graphics.outline(target.x, target.y, target.w, target.h, 0xFF65D5C1);
            }
        }
        if (busy) {
            graphics.fill(10, 68, W - 10, 337, 0xC0293D40);
            text(graphics, "Applying changes on the server...", 210, 190, 0xFFF0EBCE);
        }
    }
    private void paintBuilder(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        panel(graphics, 16, 69, 136, 270, 0xFFB7B9AB);
        panel(graphics, 160, 69, 298, 270, 0xFFC6C8BB);
        panel(graphics, 466, 69, 178, 270, 0xFFB7B9AB);
        List<Hit> browser = new ArrayList<>();
        for (var category : draft.config.categories) {
            browser.add(new Hit(0, 0, 0, 0, "category", category.id, category.id, null));
        }
        browserPage = Math.min(browserPage, Math.max(0, (browser.size() - 1) / 9));
        for (int row = 0; row < 9 && browserPage * 9 + row < browser.size(); row++) {
            var entry = browser.get(browserPage * 9 + row); int y = 133 + row * 19;
            var category = draft.config.category(entry.category);
            String label = category.name;
            boolean selected = entry.category.equals(categoryId);
            graphics.fill(21, y, 147, y + 18, selected ? 0xFF78978B : 0xFFA6AC9D);
            graphics.item(com.zycu.guishop.ShopIcons.category(category, minecraft.level.registryAccess(), label), 24, y + 1); text(graphics, font.plainSubstrByWidth(label, 101), 43, y + 5, 0xFF202F2D);
            hits.add(new Hit(21, y, 126, 18, entry.kind, entry.id, entry.category, null));
        }
        text(graphics, Integer.toString(browserPage + 1), 77, 322, 0xFF33443B);
        paintPreview(graphics, mouseX, mouseY);
        var selected = selected(); var category = category();
        text(graphics, selected != null ? "LISTING" : "CATEGORY", 474, 80, 0xFF234747);
        if (selected != null) {
            labels(graphics, new String[]{"Display name", "Buy / item", "Sell / item"}, 474, new int[]{96, 136, 176});
            text(graphics, "Tooltip description", 474, 302, 0xFF455449);
        } else if (category != null) {
            text(graphics, "Display name", 474, 96, 0xFF3B4940);
            graphics.item(com.zycu.guishop.ShopIcons.category(category, minecraft.level.registryAccess(), category.name), 545, 164);
            text(graphics, "Tooltip description", 474, 302, 0xFF455449);
        } else {
            multiline(graphics, "Create or select a category to edit it. Drag categories to reorder them.", 477, 112, 151, 0xFF3B4940);
            multiline(graphics, "Arrange mode lets you drag categories and listings. Buy/Sell is switched inside the shop preview.", 477, 207, 151, 0xFF3B4940);
        }
    }
    private boolean visible(ShopConfig.ShopItem item) {
        return (sellMode ? item.sell : item.buy) > 0 && (minecraft.level == null || VersionedListingStore.isRuntimeAvailable(item, minecraft.level.registryAccess()));
    }
    private List<Tile> tiles() {
        var category = category(); List<Tile> tiles = new ArrayList<>();
        if (enchantPreview) return enchantTiles();
        if (category == null) {
            for (var value : draft.config.categories) if (arranging || value.items.stream().anyMatch(this::visible)) tiles.add(new Tile("category", value.id, value.name, value.icon, null,
                com.zycu.guishop.ShopIcons.category(value, minecraft.level.registryAccess(), value.name), value.description));
            if (draft.config.enchantmentsEnabled()) tiles.add(new Tile("enchant", "", "Enchanted Books", "minecraft:enchanted_book", null));
            return tiles;
        }
        String search = query.toLowerCase(Locale.ROOT);
        for (var item : category.items) {
            if (!arranging && !visible(item)) continue;
            if (arranging && !search.isBlank() && !item.name.toLowerCase(Locale.ROOT).contains(search) && !item.item.contains(search) && !item.listingId.contains(search)) continue;
            tiles.add(new Tile("item", item.listingId, item.name, item.item, item));
        }
        return tiles;
    }
    private List<Tile> enchantTiles() {
        List<Tile> tiles = new ArrayList<>();
        if (!draft.config.enchantmentsEnabled() || minecraft.level == null) return tiles;
        var registry = minecraft.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        for (var entry : draft.config.enchantments.entrySet()) {
            var offer = entry.getValue(); if (!offer.enabled() || enchantId != null && !entry.getKey().equals(enchantId)) continue;
            try {
                var holder = registry.getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, Identifier.parse(entry.getKey())));
                int max = EnchantmentShopService.maximumLevel(holder.value(), offer);
                double first = enchantPrice(offer, 1), last = enchantPrice(offer, max);
                if (enchantId == null && max > 0 && last > 0) {
                    tiles.add(new Tile("offer", entry.getKey(), offer.name, "minecraft:enchanted_book", null,
                        EnchantmentShopService.createBook(holder, 1), "Levels 1-" + max + " | " + draft.config.money(first) + " - " + draft.config.money(last)));
                } else for (int level = 1; level <= max; level++) {
                    double price = enchantPrice(offer, level); if (price <= 0) continue;
                    tiles.add(new Tile("level", entry.getKey() + ":" + level, offer.name + " " + EnchantmentShopService.roman(level), "minecraft:enchanted_book", null,
                        EnchantmentShopService.createBook(holder, level), (sellMode ? "Sell " : "Buy ") + draft.config.money(price) + " | Preview only"));
                }
            } catch (RuntimeException unavailable) { /* Keep unavailable offers in configuration, outside the preview. */ }
        }
        if (enchantId == null) tiles.sort(Comparator.comparing(Tile::name, String.CASE_INSENSITIVE_ORDER));
        return tiles;
    }
    private double enchantPrice(ShopConfig.EnchantmentOffer offer, int level) {
        double buy = Math.round(offer.pricePerLevel * level * draft.config.priceMultiplier * 100.0) / 100.0;
        return sellMode ? Math.round(buy * draft.config.catalogSellRatio * 100.0) / 100.0 : buy;
    }
    private void paintPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<Tile> tiles = tiles(); boolean root = category() == null && !enchantPreview;
        int perPage = root ? CategoryLayout.PAGE_SIZE : 45;
        int pages = Math.max(1, (tiles.size() + perPage - 1) / perPage); previewPage = Math.min(previewPage, pages - 1);
        int start = previewPage * perPage, shown = Math.min(perPage, tiles.size() - start);
        int rows = root ? CategoryLayout.rows(shown) : 6;
        int left = 222, top = 160;
        panel(graphics, left - 8, top - 17, 178, rows * 18 + 40, 0xFFC6C6C6);
        var previewOffer = draft.config.enchantments.get(enchantId);
        String title = enchantPreview ? previewOffer == null ? "Enchanted Books" : previewOffer.name + " Levels" : root ? draft.config.shopTitle : category().name;
        title += sellMode ? " - Sell" : " - Buy";
        text(graphics, font.plainSubstrByWidth(title, 156), left, top - 11, 0xFF404040);
        List<Integer> slots = root ? CategoryLayout.slots(shown) : java.util.stream.IntStream.range(0, shown).boxed().toList();
        for (int slot = 0; slot < rows * 9; slot++) {
            int x = left + slot % 9 * 18, y = top + slot / 9 * 18;
            graphics.fill(x, y, x + 18, y + 18, 0xFF373737);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
            graphics.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
            graphics.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        }
        for (int index = 0; index < shown; index++) {
            Tile tile = tiles.get(start + index); int slot = slots.get(index), x = left + slot % 9 * 18, y = top + slot / 9 * 18;
            var stack = tile.preview != null ? tile.preview : tile.item != null && minecraft.level != null ? tile.item.createStack(minecraft.level.registryAccess()) : stack(tile.icon);
            if (stack.isEmpty()) stack = new ItemStack(Items.BARRIER);
            graphics.item(stack, x + 1, y + 1);
            if (tile.id.equals(selectedItem)) graphics.outline(x, y, 18, 18, 0xFF65D5C1);
            if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                graphics.fill(x, y, x + 18, y + 18, 0x44FFFFFF);
                text(graphics, font.plainSubstrByWidth(tile.name, 278), 169, 297, 0xFF33443B);
                if (tile.item != null) text(graphics, "Buy " + number(tile.item.buy * draft.config.priceMultiplier) + " | Sell " + number(tile.item.sell * draft.config.priceMultiplier), 169, 308, 0xFF33443B);
                else if (!tile.detail.isBlank()) text(graphics, font.plainSubstrByWidth(tile.detail, 278), 169, 308, 0xFF33443B);
            }
            hits.add(new Hit(x, y, 18, 18, "tile:" + tile.kind, tile.id, categoryId, tile.item));
        }
        // Blank content slots accept a drop at the end; footer slots always keep their navigation action.
        if (arranging) for (int slot = 0; slot < (rows - 1) * 9; slot++) {
            if (slots.contains(slot)) continue;
            hits.add(new Hit(left + slot % 9 * 18, top + slot / 9 * 18, 18, 18, "tile:empty", "", categoryId, null));
        }
        int footer = top + (rows - 1) * 18;
        footer(graphics, left + 4 * 18, footer, Items.PAPER, "info");
        footer(graphics, left + 8 * 18, footer, sellMode ? Items.EMERALD : Items.GOLD_INGOT, "mode");
        if (!root) footer(graphics, left, footer, Items.BARRIER, "back");
        if (previewPage > 0) footer(graphics, left + (root ? 0 : 3 * 18), footer, Items.ARROW, "previous");
        if (previewPage + 1 < pages) footer(graphics, left + (root ? 7 : 5) * 18, footer, Items.ARROW, "next");
        if (shown == 0) text(graphics, arranging ? "No listings yet" : "No enabled listings", 248, 194, 0xFF404040);
        for (Hit hit : hits) if (hit.kind.startsWith("preview:") && hit.contains(mouseX, mouseY)) {
            String hint = switch (hit.kind) {
                case "preview:mode" -> "Switch to " + (sellMode ? "Buy" : "Sell");
                case "preview:back" -> "Back";
                case "preview:previous" -> "Previous page";
                case "preview:next" -> "Next page";
                default -> "Preview only. No items or money change.";
            };
            text(graphics, hint, 169, 297, 0xFF33443B);
        }
        text(graphics, "Page " + (previewPage + 1) + "/" + pages + (arranging ? " | Arrange mode" : " | Preview only"), 214, 322, 0xFF33443B);
    }
    private void footer(GuiGraphicsExtractor graphics, int x, int y, net.minecraft.world.item.Item icon, String action) {
        graphics.item(new ItemStack(icon), x + 1, y + 1);
        hits.add(new Hit(x, y, 18, 18, "preview:" + action, "", "", null));
    }
    private void paintSettings(GuiGraphicsExtractor graphics) {
        panel(graphics, 16, 69, 628, 270, 0xFFBDC1B2);
        if (settingsSection.equals("Economy")) {
            labels(graphics, new String[]{"Currency prefix (display only)", "Global buy and sell multiplier"}, 32, new int[]{129, 183});
            labels(graphics, new String[]{"Starting balance for new players", "Enchanted book sell ratio (0 to 1)"}, 350, new int[]{129, 183});
            text(graphics, "Existing player balances are preserved.", 32, 307, 0xFF3B4940);
            text(graphics, "Shop title", 350, 263, 0xFF3B4940);
        } else if (settingsSection.equals("Messages")) {
            labels(graphics, new String[]{"Chat prefix", "Information color", "Warning color"}, 32, new int[]{129, 183, 237});
            labels(graphics, new String[]{"Prefix color", "Success color", "Error color"}, 350, new int[]{129, 183, 237});
            text(graphics, "Use Minecraft color names such as gold, aqua, green, or light_purple.", 32, 296, 0xFF3B4940);
        } else {
            var offers = new ArrayList<>(draft.config.enchantments.entrySet());
            for (int row = 0; row < 5 && accessPage * 5 + row < offers.size(); row++) {
                text(graphics, font.plainSubstrByWidth(offers.get(accessPage * 5 + row).getKey(), 245), 32, 121 + row * 35, 0xFF3B4940);
                text(graphics, "Price / level", 291, 121 + row * 35, 0xFF3B4940);
                text(graphics, "Max level", 409, 121 + row * 35, 0xFF3B4940);
            }
            if (offers.isEmpty()) text(graphics, "No offers yet. Add an offer explicitly to configure it.", 32, 167, 0xFF3B4940);
        }
    }
    private void paintAccess(GuiGraphicsExtractor graphics) {
        panel(graphics, 16, 69, 628, 270, 0xFFBDC1B2);
        text(graphics, "Admin tools require OP level 2 or higher. Installing the client grants no permissions.", 32, 82, 0xFF234747);
        text(graphics, "Player permissions: 0 everyone, 1 moderator, 2 gamemaster, 3 admin, 4 owner.", 32, 98, 0xFF3B4940);
        var entries = draft.config.permissionDefaults.entrySet().stream().filter(entry -> !entry.getKey().contains("admin") && !entry.getKey().contains("ident")).sorted(Map.Entry.comparingByKey()).toList();
        for (int index = 0; index < 7 && accessPage * 7 + index < entries.size(); index++) text(graphics, entries.get(accessPage * 7 + index).getKey(), 32, 133 + index * 26, 0xFF3B4940);
    }
    private void paintHistory(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        panel(graphics, 16, 69, 628, 270, 0xFFBDC1B2);
        if (tab == Tab.TRANSACTIONS && selectedPlayer == null) { paintPlayers(graphics); return; }
        String heading = tab == Tab.HISTORY ? "Server configuration changes" : selectedPlayer.name() + " | Balance: " + draft.config.money(selectedPlayer.balance());
        text(graphics, font.plainSubstrByWidth(heading, selectedPlayer == null ? 590 : 450), 32, 82, 0xFF234747);
        graphics.fill(30, 139, 629, 155, 0xFF566E65);
        text(graphics, "UTC time", 36, 143, 0xFFF0EBCE); text(graphics, "Player", 172, 143, 0xFFF0EBCE);
        text(graphics, "Action", 272, 143, 0xFFF0EBCE); text(graphics, "Listing / change", 373, 143, 0xFFF0EBCE); text(graphics, "Total", 570, 143, 0xFFF0EBCE);
        for (int row = 0; row < 10 && logPage * 10 + row < logData.entries().size(); row++) {
            var entry = logData.entries().get(logPage * 10 + row); int y = 158 + row * 14;
            graphics.fill(30, y, 629, y + 14, row % 2 == 0 ? 0xFFCED1C1 : 0xFFB8BFAE);
            text(graphics, entry.time().substring(0, Math.min(19, entry.time().length())).replace('T', ' '), 36, y + 3, 0xFF303E36);
            text(graphics, font.plainSubstrByWidth(entry.player(), 92), 172, y + 3, 0xFF303E36);
            text(graphics, entry.action(), 272, y + 3, 0xFF303E36);
            text(graphics, font.plainSubstrByWidth(entry.action().equals("CONFIG_APPLY") ? entry.details() : entry.listing(), 184), 373, y + 3, 0xFF303E36);
            text(graphics, font.plainSubstrByWidth(number(entry.total()), 53), 570, y + 3, 0xFF303E36);
            hits.add(new Hit(30, y, 599, 14, "log", Integer.toString(logPage * 10 + row), "", null));
        }
        if (logData.entries().isEmpty()) text(graphics, "No matching records in the recent history window.", 32, 181, 0xFF3B4940);
        text(graphics, "Recent view: 30 daily files, last 2 MB each. Full journals stay on the server.", 32, 299, 0xFF3B4940);
    }
    private void paintPlayers(GuiGraphicsExtractor graphics) {
        text(graphics, "Select a player to view their transactions", 32, 82, 0xFF234747);
        graphics.fill(30, 139, 629, 155, 0xFF566E65);
        text(graphics, "Player", 36, 143, 0xFFF0EBCE); text(graphics, "Current balance", 390, 143, 0xFFF0EBCE); text(graphics, "Status", 550, 143, 0xFFF0EBCE);
        for (int row = 0; row < 10 && logPage * 10 + row < playerData.players().size(); row++) {
            var player = playerData.players().get(logPage * 10 + row); int y = 158 + row * 14;
            graphics.fill(30, y, 629, y + 14, row % 2 == 0 ? 0xFFCED1C1 : 0xFFB8BFAE);
            text(graphics, font.plainSubstrByWidth(player.name(), 330), 36, y + 3, 0xFF303E36);
            text(graphics, font.plainSubstrByWidth(draft.config.money(player.balance()), 150), 390, y + 3, 0xFF303E36);
            text(graphics, player.online() ? "Online" : "Offline", 550, y + 3, 0xFF303E36);
            hits.add(new Hit(30, y, 599, 14, "player", Integer.toString(logPage * 10 + row), "", null));
        }
        if (playerData.players().isEmpty()) text(graphics, "No matching players. Refresh or clear the search.", 32, 181, 0xFF3B4940);
        text(graphics, "Page " + (logOffset / 10 + logPage + 1) + " | Current balances at refresh. History includes offline players.", 32, 299, 0xFF3B4940);
    }
    private void labels(GuiGraphicsExtractor graphics, String[] labels, int x, int[] ys) {
        for (int index = 0; index < labels.length; index++) text(graphics, labels[index], x, ys[index], 0xFF3B4940);
    }
    private void multiline(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int color) {
        int line = 0; for (var value : font.split(Component.literal(text), width)) graphics.text(font, value, x, y + line++ * 12, color, false);
    }
    private ItemStack stack(String id) {
        try { var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id)); return new ItemStack(item == null || item == Items.AIR ? Items.BARRIER : item); }
        catch (RuntimeException ignored) { return new ItemStack(Items.BARRIER); }
    }
    @Override protected boolean click(double x, double y, int button) {
        if (busy) return true;
        if (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return false;
        for (Hit hit : hits) if (hit.contains(x, y)) {
            if (hit.kind.equals("detected:source")) {
                if (importSource == null) { detectedSource = hit.id; query = ""; requestDetected(0, false); rebuildWidgets(); }
                return true;
            }
            if (hit.kind.equals("detected:item")) {
                if (importSource == null) { detectedSelection = detectedData.entries().get(Integer.parseInt(hit.id)); rebuildWidgets(); }
                return true;
            }
            if (hit.kind.startsWith("preview:")) {
                switch (hit.kind) {
                    case "preview:back" -> up();
                    case "preview:mode" -> switchMode();
                    case "preview:previous" -> previewPage = Math.max(0, previewPage - 1);
                    case "preview:next" -> previewPage++;
                    default -> status = "Simulator only. No items or money change here.";
                }
                return true;
            }
            if (hit.kind.equals("log")) {
                var entry = logData.entries().get(Integer.parseInt(hit.id));
                minecraft.gui.setScreen(new JournalEntryScreen(this, entry)); return true;
            }
            if (hit.kind.equals("player")) {
                selectedPlayer = playerData.players().get(Integer.parseInt(hit.id)); query = ""; requestRecords(0); rebuildWidgets(); return true;
            }
            // Mouse-down must not navigate or rebuild the widgets underneath a pending drag.
            dragging = hit; dragged = false; pressedX = x; pressedY = y; return true;
        }
        return false;
    }
    private void cancelDrag() { dragging = null; dragged = false; }
    private void activate(Hit hit) {
        if (hit.kind.equals("category") || hit.kind.equals("tile:category")) {
            enchantPreview = false; enchantId = null; categoryId = hit.id; selectedItem = null; previewPage = 0;
        } else if (hit.item != null) selectedItem = hit.item.listingId;
        else if (hit.kind.equals("tile:enchant")) { enchantPreview = true; enchantId = null; selectedItem = null; previewPage = 0; }
        else if (hit.kind.equals("tile:offer")) { enchantId = hit.id; previewPage = 0; }
        else if (hit.kind.equals("tile:level")) status = "Simulator only. No items or money change here.";
        rebuildWidgets();
    }
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            double distance = Math.hypot(logicalX(event.x()) - pressedX, logicalY(event.y()) - pressedY);
            if (distance >= 3) dragged = true;
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }
    @Override protected boolean release(double x, double y, int button) {
        if (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return false;
        Hit source = dragging; boolean moved = dragged || Math.hypot(x - pressedX, y - pressedY) >= 3; cancelDrag();
        if (source == null) return false;
        if (!moved) { if (source.contains(x, y)) activate(source); return true; }
        if (!arranging || busy) { if (!busy) status = "Enable Arrange mode to drag listings and categories."; return true; }
        for (Hit target : hits) if (target.contains(x, y)) {
            if (source.item != null) {
                if (target.kind.endsWith("category")) move(source.item, target.id);
                else if (target.item != null && !source.item.equals(target.item)) {
                    var found = draft.config.findItem(source.item.listingId);
                    if (found == null || !found.category().items.contains(target.item)) return true;
                    var list = found.category().items;
                    selectedItem = source.item.listingId; placeAt(list, source.item, target.item);
                } else if (target.kind.equals("tile:empty") && !enchantPreview && source.category.equals(categoryId)) {
                    var list = category().items; list.remove(source.item); list.add(source.item); selectedItem = source.item.listingId; rebuildWidgets();
                }
            } else if (source.kind.endsWith("category") && target.kind.endsWith("category") && !source.id.equals(target.id)) {
                var category = draft.config.category(source.id); var destination = draft.config.category(target.id);
                placeAt(draft.config.categories, category, destination);
            } else if (source.kind.endsWith("category") && target.kind.equals("tile:empty") && categoryId.isEmpty() && !enchantPreview) {
                var category = draft.config.category(source.id); draft.config.categories.remove(category); draft.config.categories.add(category); rebuildWidgets();
            }
            return true;
        }
        status = "Drop onto another listing to reorder, a category to move, or an empty slot to place last."; return true;
    }

}
