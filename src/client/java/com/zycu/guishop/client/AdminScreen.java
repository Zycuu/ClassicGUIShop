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
    private enum Tab { BUILDER, SETTINGS, ACCESS, DETECTED, TRANSACTIONS, HISTORY, PRESETS, MODES }
    private record Hit(int x, int y, int w, int h, String kind, String id, String category, ShopConfig.ShopItem item) {
        boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }
    private record Tile(String kind, String id, String name, String icon, ShopConfig.ShopItem item, ItemStack preview, String detail) {
        Tile(String kind, String id, String name, String icon, ShopConfig.ShopItem item) { this(kind, id, name, icon, item, null, ""); }
    }
    private AdminDocument draft;
    private String baseline, revision, status = "Choose a category to edit. Save changes when ready.";
    private com.zycu.guishop.ShopModes.Catalog modeCatalog;
    private com.zycu.guishop.admin.VariantSources.Catalog variantSources;
    public void variantSources(com.zycu.guishop.admin.VariantSources.Catalog value){variantSources=value;}
    private String modeSection = "Passive Income", presetChoice = "normal";
    private com.zycu.guishop.admin.ShopPresets.Plan presetPlan;
    private Tab tab = Tab.BUILDER;
    private String categoryId = "", query = "", selectedItem, settingsSection = "General";
    private final java.util.ArrayDeque<Integer> logPrevious = new java.util.ArrayDeque<>();
    private int logNext;
    private int browserPage, previewPage, accessPage, logPage, logOffset;
    private boolean arranging, busy;
    private net.minecraft.client.gui.components.Button saveButton,discardButton,presetLoadButton;
    private int searchDebounce;
    private AdminDocument importBefore;
    private final WorkbenchScroll detectedScroll=new WorkbenchScroll(628,141,138,6,()->{});
    private final WorkbenchScroll permissionScroll=new WorkbenchScroll(614,149,156,6,this::rebuildKeepingFocus);
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
    private String accountCurrency;
    private double accountDefault;
    private long historyRequestGeneration;
    private final java.time.ZoneId localZone = java.time.ZoneId.systemDefault();
    private TransactionJournal.Entry hoveredRecord;
    private boolean hoveredDate;
    private DetectedContent.Page detectedData = new DetectedContent.Page(List.of(), List.of(), 0, false, false, "", 0);
    private String detectedSource = "", importSource;
    private int sourcePage, detectedOffset, detectionRetry, importOffset, importAdded;
    private boolean detectionRefresh;

    private long detectionGeneration;

    public AdminScreen(AdminDocument document, String revision) {
        super("ClassicGUIShop Adminshop"); load(document, revision, false);
    }
    @Override protected int contentOffsetX() { return 144; }
    @Override protected int canvasWidth() { return W + 144; }
    public void load(AdminDocument document, String revision, boolean saved) {
        draft = document.copy(); baseline = AdminDocument.JSON.toJson(draft); this.revision = revision;
        accountCurrency = document.config.currencySymbol; accountDefault = document.config.startingBalance;
        cancelDrag(); importSource = null; detectionRetry = 0; busy = false; errors.clear(); invalidInputs.clear();
        if (!categoryId.isEmpty() && draft.config.category(categoryId) == null) categoryId = "";
        selectedItem = null;
        enchantPreview = false; enchantId = null;
        status = saved ? "Saved on the server. Previous configuration backed up." : "Choose a category to edit. Save changes when ready.";
        if (width > 0) rebuildWidgets();
    }
    public void status(String value){status=value;}
    void bulkUpdated(ShopConfig.Category category){for(var item:category.items){String prefix=item.listingId+":";errors.keySet().removeIf(v->v.startsWith(prefix));invalidInputs.keySet().removeIf(v->v.startsWith(prefix));}status="Updated "+category.items.size()+" items in "+category.name+". Save changes when ready.";}
    private String statusText(){return errors.isEmpty()?status:errors.values().iterator().next();}
    public void restore(com.zycu.guishop.admin.AdminEditorService.RestoreDraft restored) {
        baseline = AdminDocument.JSON.toJson(restored.live()); draft = restored.previous().copy(); draft.permissions=restored.live().permissions; revision = restored.revision();
        cancelDrag(); importSource = null; detectionRetry = 0; categoryId = ""; selectedItem = null; enchantPreview = false; enchantId = null;
        previewPage = 0; browserPage = 0; busy = false; errors.clear(); invalidInputs.clear(); tab = Tab.BUILDER;
        status = "Previous configuration loaded into a draft. Review it, then Save changes to restore."; rebuildWidgets();
    }
    public void failure(String value) { if(importSource!=null&&importBefore!=null)draft=importBefore;importBefore=null;importSource = null; detectionRetry = 0; busy = false; status = value; if (width > 0) rebuildWidgets(); }
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
        if (!matches(request) || request.generation() != historyRequestGeneration) return;
        logData = page; logOffset = page.offset(); logPage = 0; logNext=0; logPrevious.clear();
        status = page.warning().isBlank() ? "Dates shown in " + com.zycu.guishop.admin.JournalDates.zoneLabel(localZone) + ". Search local or UTC dates." : page.warning();rebuildKeepingFocus();
    }
    public void players(AdminPlayers.Page page, String filter) {
        if (tab != Tab.TRANSACTIONS || selectedPlayer != null || !query.equals(filter)) return;
        playerData = page; logOffset = page.offset(); logPage = 0;
        accountCurrency = page.currencySymbol(); accountDefault = page.defaultBalance();
        status="Select a player to view their transactions. Balances are current at refresh.";rebuildKeepingFocus();
    }
    public void playerHistory(AdminPlayers.History history, AdminNetworking.Query request) {
        if (!matches(request) || !history.player().uuid().equals(request.playerUuid())) return;
        if (request.generation() != historyRequestGeneration) return;
        accountCurrency = history.currencySymbol(); accountDefault = history.defaultBalance();
        selectedPlayer = history.player(); logs(history.history(), request);
    }
    String accountMoney(double amount) {
        var format = java.text.NumberFormat.getNumberInstance(Locale.US); format.setMinimumFractionDigits(2); format.setMaximumFractionDigits(2);
        return (accountCurrency == null ? "$" : accountCurrency) + format.format(amount);
    }
    boolean submitAccount(AdminPlayers.Player player, double balance, boolean reset, double expectedDefault) {
        if (busy) return false;
        var change = new com.zycu.guishop.admin.AccountOperations.Change(player.uuid(), player.balance(), balance, expectedDefault, reset);
        if (!AdminShopClient.request("account", "", AdminDocument.JSON.toJson(change))) return false;
        historyRequestGeneration++; busy = true; status = "Updating player account on the server..."; rebuildWidgets(); return true;
    }
    public void accountResult(com.zycu.guishop.admin.AccountOperations.Result result) {
        busy = false; status = result.message();
        if (selectedPlayer != null && selectedPlayer.uuid().equals(result.player().uuid())) {
            selectedPlayer = result.player(); query = ""; logOffset = 0; logPage = 0; requestRecords(0);
        }
        rebuildWidgets();
    }
    public void detected(DetectedContent.Page page, DetectedContent.Query request) {
        if (tab != Tab.DETECTED || request == null || request.generation() != detectionGeneration) return;
        if (request.importing()) {
            if (importSource == null || !importSource.equals(request.source()) || request.offset() != importOffset) return;
            if (!page.ready()) { detectionRetry = 10; return; }
            String name = sourceName(page, importSource);
            for (var entry : page.entries()) if (importDetected(entry, importSource.isBlank() ? "all_detected" : importSource, name)) importAdded++;
            if (page.more()) { importOffset = page.offset() + page.entries().size(); detectionRetry = 6; }
            else { importSource = null;importBefore=null; status = "Imported " + importAdded + " disabled listings into the category. Enable and price items in Shop Builder, then Save changes."; }
            rebuildWidgets(); return;
        }
        if (importSource != null || !request.source().equals(detectedSource) || !request.filter().equals(query) || request.offset() != detectedOffset) return;
        detectedData = page; detectedOffset=page.offset();detectedScroll.row=0;detectedScroll.count((page.entries().size()+1)/2);
        if (!page.ready()) { detectionRetry = 10; status = page.warning(); }
        else status=page.warning().isBlank()?"Loaded content detected. Imports stay in the draft with Buy 0 / Sell 0.":page.warning();
        rebuildKeepingFocus();
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
        searchDebounce=0;
        if (importSource == null) detectionGeneration++;
        detectedOffset = offset; detectionRefresh=refresh;
        var request = importSource == null ? new DetectedContent.Query(detectedSource, query, offset, refresh, false, detectionGeneration)
            : new DetectedContent.Query(importSource, "", importOffset, false, true, detectionGeneration);
        if (AdminShopClient.request("detected", "", AdminDocument.JSON.toJson(request))) {
            detectionRetry = 0; detectionRefresh = false;
            status = importSource == null ? "Loading detected content..." : "Importing into the draft... " + importAdded + " listings added, all prices 0.";
        } else detectionRetry = 6;
    }
    public void tickNetworking() {
        if(searchDebounce>0&&--searchDebounce==0){if(tab==Tab.DETECTED&&importSource==null)requestDetected(0,false);else if(tab==Tab.TRANSACTIONS||tab==Tab.HISTORY)requestRecords(0);}
        if (detectionRetry > 0 && --detectionRetry == 0 && tab == Tab.DETECTED) requestDetected(detectedOffset, detectionRefresh);
        if (logRetryTicks > 0 && --logRetryTicks == 0 && (tab == Tab.HISTORY || tab == Tab.TRANSACTIONS)) requestRecords(retryOffset);
    }
    @Override protected boolean submitSearch(){searchDebounce=0;if(tab==Tab.DETECTED&&importSource==null){requestDetected(0,false);return true;}if(tab==Tab.TRANSACTIONS||tab==Tab.HISTORY){requestRecords(0);return true;}return tab==Tab.BUILDER;}
    private boolean dirty() { return !errors.isEmpty() || !baseline.equals(AdminDocument.JSON.toJson(draft)); }
    private ShopConfig.Category category() { return draft.config.category(categoryId); }
    private ShopConfig.ShopItem selected() {
        if (category() == null || selectedItem == null) return null;
        return category().items.stream().filter(item -> item.listingId.equals(selectedItem)).findFirst().orElse(null);
    }
    @Override protected void init() {
        fields.clear(); hits.clear();
        String[] names = {"Shop Builder", "Settings", "Permissions", "Detected Content", "Transactions", "Change History", "Presets", "Shop Modes"};
        net.minecraft.world.item.Item[] icons = {Items.CRAFTING_TABLE, Items.COMPARATOR, Items.IRON_DOOR, Items.SPYGLASS, Items.GOLD_INGOT, Items.WRITABLE_BOOK, Items.CHEST, Items.EXPERIENCE_BOTTLE};
        for (int index = 0; index < names.length; index++) {
            Tab target = Tab.values()[index];
            var button = addRenderableWidget(new WorkbenchTab(names[index], index, icons[index], target == tab, () -> {
                if (busy) return;
                cancelDrag(); importSource = null; detectionRetry = 0; tab = target;searchDebounce=0; query = ""; selectedPlayer = null; logPage = 0; logOffset = 0;
                logData = new TransactionJournal.Page(List.of(), 0, false, ""); playerData = new AdminPlayers.Page(List.of(), 0, false); errors.entrySet().removeIf(entry -> entry.getKey().startsWith("view:"));
                rebuildWidgets();
                if (target == Tab.TRANSACTIONS || target == Tab.HISTORY) requestRecords(0);
                if (target == Tab.DETECTED) requestDetected(0, false);
                if (target == Tab.MODES && modeCatalog == null) { if (AdminShopClient.request("mode_catalog", "", "")) { busy = true; rebuildWidgets(); } }
            })); button.active = !busy&&importSource==null;
        }
        saveButton=button("Save changes",426,352,104,this::apply);
        discardButton=button("Discard changes",538,352,106, () -> {
            if (busy) return;
            confirm("Discard changes?", "Your unapplied edits will be discarded. The live server shop stays unchanged.", () -> {
                importSource = null; detectionRetry = 0;
                draft = AdminDocument.JSON.fromJson(baseline, AdminDocument.class); errors.clear(); invalidInputs.clear(); selectedItem = null; status = "Unsaved changes discarded.";
                categoryId = ""; enchantPreview = false; enchantId = null; previewPage = 0; browserPage = 0;
            });
        });discardButton.active=!busy;
        button("Close", 16, 352, 70, this::onClose).active = !busy;
        button("Reload shop",92,352,82, () -> {
            if(importSource!=null){status="Finish or cancel the import before refreshing.";return;}
            if (dirty()) confirm("Reload shop?", "Reloading discards this local draft and fetches the current server configuration.", () -> AdminShopClient.request("open", "", ""));
            else AdminShopClient.request("open", "", "");
        }).active = !busy;
        if (tab == Tab.BUILDER) builder();
        else if (tab == Tab.SETTINGS) settings();
        else if (tab == Tab.ACCESS) access();
        else if (tab == Tab.DETECTED) detection();
        else if (tab == Tab.PRESETS) presets();
        else if (tab == Tab.MODES) modes();
        else history();
        if (busy) for (var field : fields) field.setEditable(false);
    }

    public void modeCatalog(com.zycu.guishop.ShopModes.Catalog catalog) {
        modeCatalog = catalog; busy = false; status = "Detected server entities and advancements. Mode edits require Save changes."; rebuildWidgets();
    }
    public void presetResult(com.zycu.guishop.admin.ShopPresets.Plan plan) {
        busy = false; presetPlan = plan; status = "Preset ready for review. The live shop is unchanged."; rebuildWidgets();
    }
    private void presets() {
        button("Import / export presets",28,301,190,()->minecraft.gui.setScreen(new PresetLibraryScreen(this,draft)));
        String[] ids={"normal","hard","easy","basics","blank"},names={"Full Vanilla Normal","Full Vanilla Hard","Full Vanilla Easy","The Basics","Blank shop"};
        for(int i=0;i<ids.length;i++){String id=ids[i];button((presetChoice.equals(id)?"> ":"")+names[i],28,82+i*39,190,()->{presetChoice=id;presetPlan=null;rebuildWidgets();});}
        if(presetChoice.equals("normal")||presetChoice.equals("basics")){
            number("Item sell return %",250,184,110,draft.config.presetSellRatio*100,v->{draft.config.presetSellRatio=v/100;presetPlan=null;});
            number("Book sell return %",380,184,110,draft.config.bookSellRatio*100,v->{draft.config.bookSellRatio=v/100;presetPlan=null;});
            number("Potion sell return %",510,184,110,draft.config.potionSellRatio*100,v->{draft.config.potionSellRatio=v/100;presetPlan=null;});
        }
        button("Preview preset",250,269,174,()->{if(AdminShopClient.request("preset","",AdminDocument.JSON.toJson(new com.zycu.guishop.admin.ShopPresets.Request(presetChoice,draft.config.presetSellRatio,draft.config.bookSellRatio,draft.config.potionSellRatio,draft.config.villagerTradeLoopFix)))){busy=true;status="Building and auditing the preset on the server...";rebuildWidgets();}}).active=!busy&&errors.isEmpty();
        presetLoadButton=button("Load into editor",434,269,186,()->{if(presetPlan==null)return;confirm("Replace draft shop?","This replaces all editor categories and listings, including modded content. Review the result, then Save changes. Accounts and modes are preserved.",()->{draft.config.categories=new ArrayList<>(presetPlan.categories());if(presetPlan.preset().equals("blank"))draft.config.enchantments.clear();categoryId="";selectedItem=null;browserPage=previewPage=0;tab=Tab.BUILDER;status="Preset loaded into the editor. Review it, then Save changes.";});});presetLoadButton.active=!busy&&presetPlan!=null&&errors.isEmpty();
    }
    private void paintPresets(GuiGraphicsExtractor g){
        panel(g,16,69,214,270,WorkbenchTheme.PANEL);panel(g,236,69,408,270,WorkbenchTheme.PANEL);
        String description=switch(presetChoice){case "hard"->"Full Vanilla Hard: 3x base buy prices. Fixed 10% sell return for items, books and potions.";case "easy"->"Full Vanilla Easy: 75% base buy prices. Fixed 95% sell return for items, books and potions.";case "blank"->"Blank shop: no categories or listings. Build your own shop from scratch.";case "basics"->"The Basics: ores, logs, ingredients and collectible resources.";default->"Full Vanilla Normal: all vanilla items at base buy prices.";};
        multiline(g,description,250,84,370,WorkbenchTheme.TEXT);multiline(g,presetChoice.equals("blank")?"Load the blank shop into the editor. Create categories and add items in Shop Builder, then Save changes.":"Defaults only set newly generated prices. Existing listing prices stay as entered. Recipe safety may lower sell returns.",250,123,370,WorkbenchTheme.MUTED);
        if(presetChoice.equals("normal")||presetChoice.equals("basics")){text(g,"Item return %",250,170,WorkbenchTheme.MUTED);text(g,"Book return %",380,170,WorkbenchTheme.MUTED);text(g,"Potion return %",510,170,WorkbenchTheme.MUTED);}
        if(presetPlan!=null&&presetChoice.equals("blank"))text(g,"Empty shop ready to load.",250,218,WorkbenchTheme.TEAL);
        if(presetPlan!=null&&!presetChoice.equals("blank")){text(g,presetPlan.items()+" listings | "+presetPlan.categories().size()+" categories",250,218,WorkbenchTheme.TEXT);text(g,presetPlan.locked()+" disabled | "+presetPlan.capped()+" safety caps | "+presetPlan.recipes()+" recipes checked",250,233,WorkbenchTheme.TEAL);}
        multiline(g,"Preview preset > Load into editor > Save changes. Loading a preset replaces all editor listings.",250,303,370,WorkbenchTheme.MUTED);if(presetLoadButton!=null)presetLoadButton.active=!busy&&presetPlan!=null&&errors.isEmpty();
    }
    private void modes() {
        var m = draft.config.modes;
        String[] names = {"Passive Income", "Cash Mobs", "Advancement Hunter", "Warrior Mode", "Mishap Charge"};
        for (int i = 0; i < names.length; i++) { String name = names[i]; button((modeSection.equals(name) ? "> " : "") + name, 26, 80 + i * 35, 170, () -> { modeSection = name; rebuildWidgets(); }); }
        button("Refresh content", 26, 301, 170, () -> { if (AdminShopClient.request("mode_catalog", "", "")) { busy = true; rebuildWidgets(); } });
        if (modeSection.equals("Passive Income")) {
            toggle("Passive Income", 218, 81, 182, m.passiveEnabled, value -> m.passiveEnabled = value);
            number("Income amount", 218, 125, 174, m.passiveAmount, value -> m.passiveAmount = value);
            number("Interval minutes", 418, 125, 194, m.passiveMinutes, value -> m.passiveMinutes = value);
            modeColor("Prefix color", 218, 181, m.passiveColor, value -> m.passiveColor = value);
        } else if (modeSection.equals("Cash Mobs")) {
            toggle("Cash Mobs", 218, 81, 182, m.mobsEnabled, value -> m.mobsEnabled = value);
            toggle("Quick farming", 418, 81, 194, m.allowSpawnerFarming, value -> m.allowSpawnerFarming = value);
            number("Default reward override", 218, 125, 174, m.mobDefault, value -> m.mobDefault = value);
            number("Kill cooldown minutes", 418, 125, 194, m.mobCooldownMinutes, value -> m.mobCooldownMinutes = value);
            modeColor("Prefix color", 218, 181, m.mobsColor, value -> m.mobsColor = value);
            button("Edit mob rewards", 418, 181, 194, () -> minecraft.gui.setScreen(new ModeOffersScreen(this, m, modeCatalog, false))).active = modeCatalog != null;
        } else if (modeSection.equals("Advancement Hunter")) {
            toggle("Advancement Hunter", 218, 81, 200, m.advancementsEnabled, value -> m.advancementsEnabled = value);
            number("Default mod advancement reward", 218, 125, 394, m.advancementDefault, value -> m.advancementDefault = value);
            modeColor("Prefix color", 218, 181, m.advancementsColor, value -> m.advancementsColor = value);
            button("Edit advancement rewards", 418, 181, 194, () -> minecraft.gui.setScreen(new ModeOffersScreen(this, m, modeCatalog, true))).active = modeCatalog != null;
        } else if(modeSection.equals("Mishap Charge")){
            toggle("Mishap Charge",218,81,182,m.mishapEnabled,value->m.mishapEnabled=value);
            toggle("Allow debt",418,81,194,m.mishapAllowDebt,value->m.mishapAllowDebt=value);
            number("Death charge",218,125,174,m.mishapAmount,value->m.mishapAmount=value);
            modeColor("Prefix color",218,181,m.mishapColor,value->m.mishapColor=value);
        } else {
            toggle("Warrior Mode", 218, 81, 182, m.warriorEnabled, value -> m.warriorEnabled = value);
            toggle("Allow debt", 418, 81, 194, m.allowNegativeBalance, value -> m.allowNegativeBalance = value);
            number("Kill reward", 218, 125, 174, m.warriorReward, value -> m.warriorReward = value);
            number("Victim deduction", 418, 125, 194, m.warriorDeduction, value -> m.warriorDeduction = value);
            number("Repeat kill cooldown (min)", 218, 172, 174, m.warriorCooldownMinutes, value -> m.warriorCooldownMinutes = value);
            number("Online time required (min)", 418, 172, 194, m.warriorMinimumOnlineMinutes, value -> m.warriorMinimumOnlineMinutes = value);
            toggle("Shared-IP reward block", 218, 208, 182, m.blockSameAddress, value -> m.blockSameAddress = value);
            modeColor("Prefix color", 418, 208, m.warriorColor, value -> m.warriorColor = value);
        }
        String prefix = m.prefix(modeSection.equals("Passive Income")?"passive":modeSection.equals("Cash Mobs")?"mobs":modeSection.equals("Advancement Hunter")?"advancement":modeSection.equals("Mishap Charge")?"mishap":"warrior");
        field("Chat prefix name",218,243,394,prefix,value->{switch(modeSection){case "Passive Income"->m.passivePrefix=value;case "Cash Mobs"->m.mobsPrefix=value;case "Advancement Hunter"->m.advancementsPrefix=value;case "Mishap Charge"->m.mishapPrefix=value;default->m.warriorPrefix=value;}}).setMaxLength(32);
    }
    private void modeColor(String label,int x,int y,String color,Consumer<String> changed){colorChoice(label,x,y,174,color,changed);}
    private void colorChoice(String label,int x,int y,int width,String color,Consumer<String> changed){button(ChoiceDialog.label(color)+"...",x,y,width,()->minecraft.gui.setScreen(new ChoiceDialog(this,label,color,ChoiceDialog.colors(),value->{changed.accept(value);rebuildWidgets();})));}
    private void paintModes(GuiGraphicsExtractor graphics) {
        panel(graphics, 16, 69, 188, 270, WorkbenchTheme.PANEL); panel(graphics, 208, 69, 436, 270, WorkbenchTheme.PANEL);
        String[] labels = switch (modeSection) { case "Passive Income" -> new String[]{"Income amount", "Interval minutes"}; case "Cash Mobs" -> new String[]{"Default reward override", "Kill cooldown minutes"}; case "Advancement Hunter" -> new String[]{"Default mod advancement reward", ""}; case "Mishap Charge" -> new String[]{"Death charge", ""}; default -> new String[]{"Kill reward", "Victim deduction"}; };
        int y = 112;
        text(graphics, labels[0], 218, y, WorkbenchTheme.MUTED); text(graphics, labels[1], 418, y, WorkbenchTheme.MUTED);
        if (modeSection.equals("Warrior Mode")) { text(graphics, "Repeat kill cooldown (min)", 218, 159, WorkbenchTheme.MUTED); text(graphics, "Online time required (min)", 418, 159, WorkbenchTheme.MUTED); text(graphics, "Prefix color", 418, 195, WorkbenchTheme.MUTED); }
        else text(graphics, "Prefix color", 218, 168, WorkbenchTheme.MUTED);
        String help = switch (modeSection) {
            case "Passive Income" -> "Survival/adventure play earns income. AFK counts; offline time and disabled periods do not.";
            case "Cash Mobs" -> "Living mobs only. 0 uses vanilla rewards of 2 to 50. Added mobs use your default override. Quick farming bypasses cooldowns.";
            case "Advancement Hunter" -> "One reward per advancement. Vanilla: Task 25, Goal 100, Challenge 500. Hidden entries pay 0.";
            case "Mishap Charge" -> "Each survival/adventure death is charged once. Debt is optional. Warrior deductions apply separately.";
            default -> "Repeat pairs, self kills and creative/spectator players do not pay. Same-address blocking includes shared connections.";
        };
        text(graphics,"Chat prefix name",218,230,WorkbenchTheme.MUTED);
        multiline(graphics, help, 218, 277, 406, WorkbenchTheme.MUTED);
        text(graphics, "Modes can run together. Save changes when ready.", 218, 325, WorkbenchTheme.TEAL);
    }

    private void builder() {
        button("+ Category", 22, 76, 124, () -> createCategory());
        button("<",22,316,38,()->{browserPage=Math.max(0,browserPage-1);rebuildWidgets();}).active=browserPage>0;
        button(">",108,316,38,()->{browserPage++;rebuildWidgets();}).active=(browserPage+1)*9<draft.config.categories.size();
        button("Categories",166,76,80,this::home);
        button(arranging ? "Exit arrange mode" : "Arrange mode", 252,76,137, () -> {
            cancelDrag(); arranging = !arranging; query = ""; previewPage = 0; selectedItem = null;
            status=arranging?"Arrange mode: drag listings or categories.":"Click any listing to edit it, including disabled imports.";
            rebuildWidgets();
        });
        button("Back",397,76,55,this::up).active=category()!=null;
        button("Bulk Adjust",344,107,108,()->minecraft.gui.setScreen(new PriceAdjustScreen(this,draft.config,categoryId,selectedItem))).active=category()!=null&&!category().items.isEmpty();
        search = field("Search listings", 166, 107, 172, query, value -> query = value);
        search.setHint(Component.literal("Search by name or item ID..."));
        search.setResponder(value -> { query = value; previewPage = 0; });
        search.setEditable(true);
        if(draft.config.categories.isEmpty()){button("Create category",176,205,126,this::createCategory);button("Choose a preset",312,205,132,()->{tab=Tab.PRESETS;rebuildWidgets();});}
        button("<",166,316,40,()->{previewPage=Math.max(0,previewPage-1);rebuildWidgets();}).active=previewPage>0;
        button(">",412,316,40,()->{previewPage++;rebuildWidgets();}).active=(previewPage+1)*(category()==null?CategoryLayout.PAGE_SIZE:45)<tiles().size();
        inspector();
    }
    private void home() {
        cancelDrag(); categoryId = ""; selectedItem = null; enchantPreview = false; enchantId = null; previewPage = 0; rebuildWidgets();
    }
    private void up() {
        if (enchantPreview) { if (enchantId != null) enchantId = null; else enchantPreview = false; }
        else if(enchantId!=null)enchantId=com.zycu.guishop.ListingGroups.parent(enchantId);else categoryId = "";
        selectedItem = null; previewPage = 0; rebuildWidgets();
    }
    private void inspector() {
        var item = selected(); var category = category();
        if (item != null) {
            field("Display name", 472, 107, 164, item.name, value -> item.name = value);
            number("Buy price", 472, 147, 164, item.buy, value -> { item.buy = value; item.manualPrice = true; });
            number("Sell price", 472, 187, 164, item.sell, value -> { item.sell = value; item.manualPrice = true; });
            button("Move listing...", 472, 219, 164, () -> minecraft.gui.setScreen(new DestinationScreen(this, draft, cat -> move(item, cat))));
            button("Remove listing", 472, 245, 164, () -> confirm("Remove listing?", item.name + " will be removed when you Save changes. The previous configuration will be backed up.", () -> {
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
            button("+ Add items", 472, 181, 164, () -> minecraft.gui.setScreen(new IconPickerScreen(this, "Add item listings", true, items -> {
                if (draft.config.categories.stream().mapToInt(value -> value.items.size()).sum() + items.size() > 10000) throw new IllegalArgumentException("Listing limit reached. Select fewer items.");
                categoryId = category.id;
                for (var itemToAdd : items) addListing(itemToAdd);
            })));
            button("+ Add held item", 472, 207, 164, () -> {
                if (AdminShopClient.request("held", "", "")) { heldCategory = categoryId; }
            });
            button("Remove category", 472, 245, 164, () -> confirm("Remove category?", "This also removes its " + category.items.size() + " listings when you Save changes.", () -> {
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
        category().items.add(item); selectedItem = item.listingId;
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
        for (String section : List.of("General","Economy", "Messages", "Enchantments/Potions")) {
            String name = section; button((name.equals(settingsSection) ? "* " : "") + name, x, 78, 148, () -> { settingsSection = name; rebuildWidgets(); }); x += 154;
        }
        var config = draft.config;
        if (settingsSection.equals("General")) {
            field("Shop title",32,142,594,config.shopTitle,value->config.shopTitle=value).setMaxLength(64);
            toggle("Creative transactions",32,196,276,config.creativeTransactionsAllowed(),value->config.allowCreativeTransactions=value);
            toggle("Offline payments",350,196,276,config.offlinePaymentsAllowed(),value->config.allowOfflinePayments=value);
        } else if (settingsSection.equals("Economy")) {
            field("Currency prefix (display only)", 32, 142, 276, config.currencySymbol, value -> config.currencySymbol = value);
            number("Starting balance for new players", 350, 142, 276, config.startingBalance, value -> config.startingBalance = value);
            toggle("Damaged item selling",32,196,276,config.sellDamagedItems,value->config.sellDamagedItems=value);
            toggle("Enchanted equipment selling",350,196,276,config.sellEnchantedEquipment,value->config.sellEnchantedEquipment=value);
            toggle("Show operator balances publicly",32,235,276,config.showOperatorBalances,value->config.showOperatorBalances=value);
            button("Preset price defaults",350,235,276,()->{tab=Tab.PRESETS;rebuildWidgets();});
            toggle("Villager Trade Loop Fix",32,279,594,config.villagerTradeLoopFix,value->{config.villagerTradeLoopFix=value;presetPlan=null;status=value?"Save changes to refresh existing villager prices from this shop.":"Save changes restores original payments. Shop prices stay; native trade loops are unprotected.";});
        } else if (settingsSection.equals("Messages")) {
            field("Chat prefix", 32, 142, 276, config.chatPrefix, value -> config.chatPrefix = value);
            colorChoice("Prefix color", 350, 142, 276, config.chatPrefixColor, value -> config.chatPrefixColor = value);
            colorChoice("Information color", 32, 196, 276, config.chatInfoColor, value -> config.chatInfoColor = value);
            colorChoice("Success color", 350, 196, 276, config.chatSuccessColor, value -> config.chatSuccessColor = value);
            colorChoice("Warning color", 32, 250, 276, config.chatWarningColor, value -> config.chatWarningColor = value);
            colorChoice("Error color", 350, 250, 276, config.chatErrorColor, value -> config.chatErrorColor = value);
        } else {
            if(variantSources==null)AdminShopClient.request("variant_sources","","");
            button("Enchantments",32,123,276,()->minecraft.gui.setScreen(new VariantScreen(this,config,true)));
            button("Potions",350,123,276,()->minecraft.gui.setScreen(new VariantScreen(this,config,false)));
        }
    }
    private List<Map.Entry<String,Integer>> permissionEntries(){return draft.config.permissionDefaults.entrySet().stream().filter(e->!e.getKey().contains("admin")&&!e.getKey().contains("ident")).sorted(Comparator.comparing(e->permissionName(e.getKey()))).toList();}
    private String permissionName(String key){if(key.startsWith("guishop.category.")){String id=key.substring(17);var cat=draft.config.category(id);return "Category: "+(cat==null?ChoiceDialog.label(id):cat.name);}return switch(key){case "guishop.buy"->"Buy items";case "guishop.sell"->"Sell items";case "guishop.enchant"->"Trade enchanted books";case "guishop.creative.bypass"->"Trade while in creative mode";case "guishop.command.shop"->"Open the shop";case "guishop.command.balance"->"View balances";case "guishop.command.pay"->"Send money";case "guishop.command.sellhand"->"Sell held items";case "guishop.command.worth"->"Check item value";default->ChoiceDialog.label(key.replace("guishop.","").replace('.',' '));};}
    @Override protected WorkbenchScroll scroller(){return tab==Tab.ACCESS?permissionScroll:tab==Tab.DETECTED?detectedScroll:null;}
    private void access(){
        var entries=permissionEntries();permissionScroll.count(entries.size());
        for(int row=0;row<6&&permissionScroll.row+row<entries.size();row++){
            var e=entries.get(permissionScroll.row+row);String role=ChoiceDialog.roles().get(e.getValue()).label();
            button(role,430,150+row*26,174,()->minecraft.gui.setScreen(new ChoiceDialog(this,permissionName(e.getKey())+" default",Integer.toString(e.getValue()),ChoiceDialog.roles(),v->{draft.config.permissionDefaults.put(e.getKey(),Integer.parseInt(v));rebuildWidgets();})));
        }
    }
    private int importCount(){return detectedData.sourceCount();}
    private String destinationName(){String key=detectedSource.isBlank()?"all_detected":detectedSource;var cat=draft.config.categories.stream().filter(v->key.equals(v.detectedSource)).findFirst().orElse(null);return cat==null?sourceName(detectedData,detectedSource):cat.name;}
    private void detection() {
        search = field("Search detected items", 184, 110, 346, query, value -> query = value); search.setMaxLength(128);
        search.setHint(Component.literal("Search item name, ID, or source path...")); search.setEditable(importSource==null);search.setResponder(v->{query=v;searchDebounce=6;});
        button("Search", 536, 110, 94, () -> requestDetected(0, false)).active = importSource == null;
        button("Rescan", 536, 76, 94, () -> requestDetected(0, true)).active = importSource == null;
        button("<", 24, 313, 40, () -> { sourcePage = Math.max(0, sourcePage - 1); rebuildWidgets(); });
        button(">", 118, 313, 40, () -> { sourcePage++; rebuildWidgets(); });
        button("Previous",184,279,76, () -> requestDetected(Math.max(0, detectedOffset - DetectedContent.PAGE_SIZE), false)).active = importSource == null && detectedOffset > 0;
        button("Next",268,279,60, () -> requestDetected(detectedOffset + DetectedContent.PAGE_SIZE, false)).active = importSource == null && detectedData.more();
        String action=detectedSource.isBlank()?"Choose a source":detectedSource.startsWith("datapack_")?"Import entire pack":"Import entire source";
        button(importSource == null ? action : "Cancel import", 184,313,446, () -> {
            if(importSource!=null){importSource=null;detectionRetry=0;detectionGeneration++;if(importBefore!=null)draft=importBefore;importBefore=null;status="Import cancelled. The editor has been restored.";rebuildWidgets();}
            else confirm("Import into one category?", importCount()+" detected items from "+sourceName(detectedData,detectedSource)+" will go into category "+destinationName()+". Includes every page and ignores search. New prices are 0; existing prices stay unchanged.", () -> {
                importBefore=draft.copy();detectionGeneration++; importSource = detectedSource; importOffset = 0; importAdded = 0; requestDetected(0, false);
            });
        }).active=importSource!=null||detectedData.ready()&&!detectedSource.isBlank();
    }
    private void paintDetected(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        panel(graphics, 16, 69, 148, 270, WorkbenchTheme.PANEL); panel(graphics, 172, 69, 472, 270, WorkbenchTheme.PANEL);
        text(graphics, "CONTENT SOURCES", 24, 82, WorkbenchTheme.MUTED);
        text(graphics, "Imports start hidden: Buy 0 / Sell 0", 184, 82, WorkbenchTheme.MUTED);
        text(graphics, "Choose a source, then import it into one category.", 184, 96, WorkbenchTheme.MUTED);
        var sources = new ArrayList<>(detectedData.sources());
        sourcePage = Math.min(sourcePage, Math.max(0, (sources.size() - 1) / 9));
        graphics.fill(22, 109, 158, 127, detectedSource.isBlank() ? WorkbenchTheme.SELECTED : WorkbenchTheme.ROW);
        text(graphics, "All sources", 28, 114, WorkbenchTheme.TEXT); hits.add(new Hit(22, 109, 136, 18, "detected:source", "", "", null));
        for (int row = 0; row < 9 && sourcePage * 9 + row < sources.size(); row++) {
            var source = sources.get(sourcePage * 9 + row); int y = 133 + row * 19;
            graphics.fill(22, y, 158, y + 18, source.id().equals(detectedSource) ? WorkbenchTheme.SELECTED : WorkbenchTheme.ROW);
            text(graphics, font.plainSubstrByWidth(source.name() + " (" + source.count() + ")", 126), 28, y + 5, WorkbenchTheme.TEXT);
            hits.add(new Hit(22, y, 136, 18, "detected:source", source.id(), "", null));
        }
        text(graphics, Integer.toString(sourcePage + 1), 85, 322, WorkbenchTheme.MUTED);
        text(graphics,"Page "+(detectedOffset/DetectedContent.PAGE_SIZE+1)+" | "+detectedData.sourceCount()+" detected in source",340,284,WorkbenchTheme.MUTED);
        text(graphics, detectedData.ready() ? font.plainSubstrByWidth(detectedSource.isBlank()?"Select a source to import it into its own category.":"Category: "+destinationName(), 440) : "Missing a variant? Use Add held item in Shop Builder.",184,302,WorkbenchTheme.MUTED);
        detectedScroll.paint(graphics);
        for(int index=detectedScroll.row*2;index<Math.min(detectedData.entries().size(),(detectedScroll.row+6)*2);index++){
            var entry = detectedData.entries().get(index); int x=184+index%2*218,y=141+(index/2-detectedScroll.row)*23;
            ItemStack item = entry.listing().createStack(minecraft.level.registryAccess());
            boolean selected=x<=mouseX&&mouseX<x+212&&y<=mouseY&&mouseY<y+22;
            graphics.fill(x, y, x + 212, y + 22, selected ? WorkbenchTheme.SELECTED : WorkbenchTheme.ROW);
            graphics.item(item, x + 2, y + 3);
            text(graphics, font.plainSubstrByWidth(item.getHoverName().getString(),190), x + 21, y + 7, WorkbenchTheme.TEXT);

            if (x <= mouseX && mouseX < x + 212 && y <= mouseY && mouseY < y + 22) {
                graphics.setTooltipForNextFrame(font,font.split(Component.literal(item.getHoverName().getString()+"\n"+entry.listing().item+"\n"+entry.origin()),350),(int)(originX+(mouseX+contentOffsetX())*scale),(int)(originY+mouseY*scale));

            }
        }
        if (detectedData.entries().isEmpty()) text(graphics, detectedData.ready() ? "No matching detected items. Held-item import is also available." : "Inspecting server content...", 184, 169, WorkbenchTheme.MUTED);
    }
    private void history() {
        boolean roster = tab == Tab.TRANSACTIONS && selectedPlayer == null;
        search = field(roster ? "Search players" : "Search records", 32, 110, 450, query, value -> query = value);
        search.setMaxLength(128);search.setResponder(v->{query=v;searchDebounce=6;});
        search.setHint(Component.literal(roster ? "Search player name or UUID..." : "Search item, source, action, or local / UTC date..."));
        if (selectedPlayer != null) {
            button("Set balance", 320, 76, 100, () -> minecraft.gui.setScreen(new AccountDialog(this, selectedPlayer, false, accountDefault, accountCurrency)));
            button("Reset", 428, 76, 84, () -> minecraft.gui.setScreen(new AccountDialog(this, selectedPlayer, true, accountDefault, accountCurrency)));
            button("Back to players",522,76,106, () -> {
            selectedPlayer = null; query = ""; logPage = 0; requestRecords(0); rebuildWidgets();
            });
        }
        if(tab==Tab.TRANSACTIONS)button("Trending",selectedPlayer==null?494:250,selectedPlayer==null?76:313,selectedPlayer==null?130:118,()->minecraft.gui.setScreen(new TrendingScreen(this,accountCurrency)));
        button("Search", 494, 110, 130, () -> requestRecords(0));
        button("Previous", 32, 313, 100, () -> { if(!roster && !logPrevious.isEmpty())logPage=logPrevious.pop(); else if (roster && logPage > 0) logPage--; else if (logOffset > 0) requestRecords(Math.max(0, logOffset - 100)); });
        button("Next", 140, 313, 100, () -> {
            int count = roster ? playerData.players().size() : logData.entries().size();
            boolean more = roster ? playerData.more() : logData.more();
            if(!roster && logNext < count){logPrevious.push(logPage);logPage=logNext;}
            else if (roster && (logPage + 1) * 10 < count) logPage++;
            else if (more) requestRecords(logOffset + 100);
        });
        button("Refresh", 494, 313, 130, () -> requestRecords(logOffset));
    }
    private void requestRecords(int offset) {
        searchDebounce=0;
        historyRequestGeneration++;
        retryOffset = offset; logPage = 0; logNext=0; logPrevious.clear();
        String action, body;
        if (tab == Tab.TRANSACTIONS && selectedPlayer == null) {
            playerData = new AdminPlayers.Page(List.of(), offset, false);
            action = "players"; body = AdminDocument.JSON.toJson(new AdminNetworking.PlayersQuery(query, offset));
        } else {
            logData = new TransactionJournal.Page(List.of(), offset, false, "");
            action = "logs"; body = AdminDocument.JSON.toJson(new AdminNetworking.Query(query, offset, tab == Tab.HISTORY, selectedPlayer == null ? null : selectedPlayer.uuid(), localZone.getId(), historyRequestGeneration));
        }
        if (AdminShopClient.request(action, "", body)) { status = "Loading server records..."; logRetryTicks = 0; }
        else logRetryTicks = 6;
    }
    private EditBox field(String label, int x, int y, int width, String value, Consumer<String> changed) {
        var field = new WorkbenchField(font, x, y, width, Component.literal(label));
        field.setMaxLength(200); field.setValue(value == null ? "" : value);
        field.setResponder(changed); fields.add(field); addRenderableWidget(field); return field;
    }
    private EditBox number(String label, int x, int y, int width, double value, DoubleConsumer changed) {
        String context = tab == Tab.MODES ? modeSection : tab == Tab.BUILDER ? selectedItem : tab == Tab.SETTINGS && settingsSection.equals("Enchantments") ? "offer:" + accessPage + ":" + y : tab.name();
        String key = context + ":" + label;
        var field = field(label, x, y, width, invalidInputs.getOrDefault(key, number(value)), ignored -> {});
        field.setResponder(input -> {
            try {
                double parsed = Double.parseDouble(input);
                if (!Double.isFinite(parsed) || parsed < 0) throw new NumberFormatException();
                if ((label.equals("Max level") || label.startsWith("guishop.")) && parsed != Math.rint(parsed)) throw new NumberFormatException();
                if (label.startsWith("guishop.") && parsed > 4 || label.equals("Max level") && parsed > 255) throw new NumberFormatException();
                if((label.contains("return")||label.contains("%"))&&parsed>100)throw new NumberFormatException();
                errors.remove(key);invalidInputs.remove(key);field.setTextColor(WorkbenchTheme.TEXT);changed.accept(parsed);
            } catch (NumberFormatException exception) {
                field.setTextColor(WorkbenchTheme.DANGER);invalidInputs.put(key, input);
                errors.put(key, label + " has an invalid number. Correct it or discard the draft.");
            }
        });
        if(invalidInputs.containsKey(key))field.setTextColor(WorkbenchTheme.DANGER);return field;
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
        if(importSource!=null){status="Finish or cancel the import before closing.";return;}
        if (busy) { status = "Wait for the server to finish applying this draft."; return; }
        if (dirty()) confirm("Close without saving?", "Unsaved edits will be discarded. The live server shop stays unchanged.", () -> { importSource = null; detectionRetry = 0; minecraft.gui.setScreen(null); });
        else { importSource = null; detectionRetry = 0; minecraft.gui.setScreen(null); }
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (busy) return true;
        return super.keyPressed(event);
    }

    @Override protected void paint(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        hoveredRecord = null;
        hits.clear(); panel(graphics, -144, 0, W + 144, H, WorkbenchTheme.SURFACE);
        graphics.text(font, Component.literal("ClassicGUIShop").withStyle(style -> style.withBold(true)), -124, 12, WorkbenchTheme.TEXT, false);
        text(graphics, "Admin Workbench", -124, 25, WorkbenchTheme.MUTED);
        String version=net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("guishop").orElseThrow().getMetadata().getVersion().getFriendlyString().split("[+]",2)[0].replace("-beta."," BETA ").replace("-alpha."," ALPHA ");
        text(graphics,version,636-font.width(version),17,WorkbenchTheme.MUTED);
        graphics.fill(-128, 41, W - 16, 42, 0x553C515A);
        panel(graphics, -136, 69, 132, 270, 0xAA142027);
        String heading = switch (tab) { case BUILDER -> "Shop Builder"; case SETTINGS -> "Settings"; case ACCESS -> "Permissions"; case DETECTED -> "Detected Content"; case TRANSACTIONS -> "Transactions"; case HISTORY -> "Change History"; case PRESETS -> "Presets"; case MODES -> "Shop Modes"; };
        text(graphics, heading, 20, 52, WorkbenchTheme.MUTED);
        if (tab == Tab.BUILDER) paintBuilder(graphics, mouseX, mouseY);
        else if (tab == Tab.SETTINGS) paintSettings(graphics);
        else if (tab == Tab.ACCESS) paintAccess(graphics);
        else if (tab == Tab.DETECTED) paintDetected(graphics, mouseX, mouseY);
        else if (tab == Tab.PRESETS) paintPresets(graphics);
        else if (tab == Tab.MODES) paintModes(graphics);
        else paintHistory(graphics, mouseX, mouseY);
        for(var child:children())if(child instanceof net.minecraft.client.gui.components.Button button){String label=button.getMessage().getString();
            if(tab==Tab.BUILDER&&(label.equals("<")||label.equals(">"))){boolean previous=label.equals("<");button.active=button.getX()<160?(previous?browserPage>0:(browserPage+1)*9<draft.config.categories.size()):(previous?previewPage>0:(previewPage+1)*(category()==null?CategoryLayout.PAGE_SIZE:45)<tiles().size());}
            else if(tab==Tab.DETECTED&&(label.equals("<")||label.equals(">")))button.active=importSource==null&&(label.equals("<")?sourcePage>0:(sourcePage+1)*9<detectedData.sources().size());
            else if((tab==Tab.TRANSACTIONS||tab==Tab.HISTORY)&&(label.equals("Previous")||label.equals("Next"))){boolean roster=tab==Tab.TRANSACTIONS&&selectedPlayer==null;button.active=label.equals("Previous")?logOffset>0||(roster?logPage>0:!logPrevious.isEmpty()):(roster?(logPage+1)*10<playerData.players().size()||playerData.more():logNext<logData.entries().size()||logData.more());}
            else if(label.equals("Preview preset"))button.active=!busy&&errors.isEmpty();
        }
        graphics.fill(-128, 343, W - 16, 344, 0x553C515A);
        text(graphics, font.plainSubstrByWidth(statusText(),620), 20, 375, busy ? WorkbenchTheme.GOLD : WorkbenchTheme.MUTED);
        saveButton.active=!busy&&importSource==null&&dirty()&&errors.isEmpty();discardButton.active=!busy&&importSource==null&&dirty();
        WorkbenchTheme.rounded(graphics, 183, 360, 3, 3, 1, dirty() ? WorkbenchTheme.GOLD : WorkbenchTheme.TEAL);
        text(graphics, dirty() ? "Unsaved changes" : "All changes saved", 193, 358, dirty() ? WorkbenchTheme.GOLD : WorkbenchTheme.MUTED);
    }
    @Override protected void afterWidgets(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        if (hoveredRecord != null) {
            String text = hoveredDate ? com.zycu.guishop.admin.JournalDates.full(hoveredRecord.time(), localZone) + " (" + localZone.getId() + ")\nUTC: " + com.zycu.guishop.admin.JournalDates.full(hoveredRecord.time(), java.time.ZoneId.of("UTC"))
                : tab==Tab.HISTORY ? hoveredRecord.player()+"\n"+TransactionJournal.activity(hoveredRecord)+"\nClick to view the backup" : com.zycu.guishop.admin.TransactionJournal.action(hoveredRecord) + "\n" + com.zycu.guishop.admin.TransactionJournal.activity(hoveredRecord) + "\n" + hoveredRecord.listing() + "\nQuantity: " + hoveredRecord.quantity() + " | Total: " + accountMoney(hoveredRecord.total()) + "\n" + hoveredRecord.details();
            graphics.setTooltipForNextFrame(font, font.split(Component.literal(text), 280), (int)(originX + (x + contentOffsetX()) * scale), (int)(originY + y * scale));
        }
        for(Hit hit:hits)if(hit.kind.equals("permission")&&hit.contains(x,y))graphics.setTooltipForNextFrame(font,font.split(Component.literal(hit.id+"\n"+(hit.id.equals(com.zycu.guishop.ShopPermissions.ADMIN)?"Full Adminshop access. Unassigned: OP 2+. Grant only to trusted administrators.":"An explicit provider grant or deny overrides the default role.")),320),(int)(originX+(x+contentOffsetX())*scale),(int)(originY+y*scale));
        for(Hit hit:hits)if(hit.kind.equals("detected:source")&&hit.contains(x,y))graphics.setTooltipForNextFrame(font,font.split(Component.literal(sourceName(detectedData,hit.id)+"\n"+detectedData.sources().stream().filter(v->v.id().equals(hit.id)).map(v->v.count()+" detected items").findFirst().orElse("All detected items")),360),(int)(originX+(x+contentOffsetX())*scale),(int)(originY+y*scale));
        for (Hit hit : hits) if (hit.item != null && hit.contains(x, y) && hit.kind.equals("tile:item")) {
            var item = hit.item;
            var icon = com.zycu.guishop.ShopIcons.editor(item.createStack(minecraft.level.registryAccess()),com.zycu.guishop.ListingGroups.displayName(item,minecraft.level.registryAccess()),item.description,item.buy,item.sell,draft.config);
            graphics.setTooltipForNextFrame(font, icon, (int)(originX + (x + contentOffsetX()) * scale), (int)(originY + y * scale));
        }
        for (Hit hit : hits) if (hit.contains(x, y) && (hit.kind.equals("tile:level") || hit.kind.equals("tile:offer"))) {
            var tile = tiles().stream().filter(value -> value.id.equals(hit.id)).findFirst().orElse(null);
            if (tile != null && tile.preview != null) graphics.setTooltipForNextFrame(font, tile.preview,
                (int)(originX + (x + contentOffsetX()) * scale), (int)(originY + y * scale));
        }
        for (var widget : children()) if (widget instanceof WorkbenchTab rail && (rail.isHovered() || rail.isFocused())) {
            graphics.setTooltipForNextFrame(font, rail.getMessage(), (int)(originX + (x + contentOffsetX()) * scale), (int)(originY + y * scale));
        }
        if (y >= 373 && y < H && x >= 20 && x < W - 20) graphics.setTooltipForNextFrame(font,
            Component.literal(statusText()), (int)(originX + (x + contentOffsetX()) * scale), (int)(originY + y * scale));
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
            text(graphics, "Applying changes on the server...", 210, 190, WorkbenchTheme.TEXT);
        }
    }
    private void paintBuilder(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        panel(graphics, 16, 69, 136, 270, WorkbenchTheme.PANEL);
        panel(graphics, 466, 69, 178, 270, WorkbenchTheme.PANEL);
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
            WorkbenchTheme.rounded(graphics, 21, y, 126, 18, 4, selected ? WorkbenchTheme.SELECTED : WorkbenchTheme.ROW);
            if (selected) WorkbenchTheme.rounded(graphics, 21, y + 3, 2, 12, 1, WorkbenchTheme.TEAL);
            graphics.item(com.zycu.guishop.ShopIcons.category(category, minecraft.level.registryAccess(), label), 24, y + 1); text(graphics, font.plainSubstrByWidth(label, 101), 43, y + 5, WorkbenchTheme.TEXT);
            hits.add(new Hit(21, y, 126, 18, entry.kind, entry.id, entry.category, null));
        }
        text(graphics, Integer.toString(browserPage + 1), 77, 322, WorkbenchTheme.MUTED);
        text(graphics, "Categories", 24, 114, WorkbenchTheme.MUTED);
        String count = Integer.toString(draft.config.categories.size());
        text(graphics, count, 143 - font.width(count), 114, WorkbenchTheme.MUTED);
        paintPreview(graphics, mouseX, mouseY);
        var selected = selected(); var category = category();
        text(graphics, selected != null ? "LISTING" : "CATEGORY", 474, 80, WorkbenchTheme.MUTED);
        if (selected != null) {
            labels(graphics, new String[]{"Display name", "Buy / item", "Sell / item"}, 474, new int[]{96, 136, 176});
            text(graphics, "Tooltip description", 474, 302, WorkbenchTheme.MUTED);
        } else if (category != null) {
            text(graphics, "Display name", 474, 96, WorkbenchTheme.MUTED);
            graphics.item(com.zycu.guishop.ShopIcons.category(category, minecraft.level.registryAccess(), category.name), 545, 164);
            text(graphics, "Tooltip description", 474, 302, WorkbenchTheme.MUTED);
        } else {
            multiline(graphics, "Create or select a category to edit it. Drag categories to reorder them.", 477, 112, 151, WorkbenchTheme.MUTED);
            multiline(graphics, "Click a listing to edit its prices. Arrange mode lets you drag listings and categories.", 477, 207, 151, WorkbenchTheme.MUTED);
        }
    }
    private boolean visible(ShopConfig.ShopItem item) {
        return (item.sell > 0 || item.buy > 0) && (minecraft.level == null || VersionedListingStore.isRuntimeAvailable(item, minecraft.level.registryAccess()));
    }
    private List<Tile> tiles() {
        var category=category();List<Tile> tiles=new ArrayList<>();String search=query.toLowerCase(Locale.ROOT);

        if (category == null) {
            for(var value:draft.config.categories){if(!search.isBlank()&&!(value.name+" "+value.id).toLowerCase(Locale.ROOT).contains(search)&&value.items.stream().noneMatch(item->(item.name+" "+item.item+" "+item.listingId).toLowerCase(Locale.ROOT).contains(search)))continue;tiles.add(new Tile("category", value.id, value.name, value.icon, null,
                com.zycu.guishop.ShopIcons.category(value, minecraft.level.registryAccess(), value.name), value.description));}

            return tiles;
        }
        for(var node:com.zycu.guishop.ListingGroups.entries(category.items,minecraft.level.registryAccess(),enchantId)){
            if(node.folder()){
                String name=com.zycu.guishop.ListingGroups.name(node.group(),minecraft.level.registryAccess());
                if(!search.isBlank()&&!name.toLowerCase(Locale.ROOT).contains(search)&&node.items().stream().noneMatch(item->(item.name+" "+item.item+" "+item.listingId).toLowerCase(Locale.ROOT).contains(search)))continue;
                String detail=com.zycu.guishop.ListingGroups.description(node.group(),node.items().size());
                var icon=com.zycu.guishop.ShopIcons.display(node.item().createStack(minecraft.level.registryAccess()),name,detail);
                tiles.add(new Tile("bookgroup",node.group(),name,node.item().item,null,icon,detail));
            }else{
                var item=node.item();
                if(!search.isBlank()&&!(item.name+" "+item.item+" "+item.listingId).toLowerCase(Locale.ROOT).contains(search))continue;
                tiles.add(new Tile("item",item.listingId,com.zycu.guishop.ListingGroups.displayName(item,minecraft.level.registryAccess()),item.item,item));
            }
        }
        return tiles;
    }
    private void paintPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        String breadcrumb="Shop"+(category()==null?"":" > "+category().name)+(enchantId==null?"":" > "+(enchantId.startsWith("arrow:")?"Tipped Arrows > ":"")+com.zycu.guishop.ListingGroups.name(enchantId,minecraft.level.registryAccess()));
        text(graphics,font.plainSubstrByWidth(breadcrumb,278),166,132,WorkbenchTheme.MUTED);
        if(draft.config.categories.isEmpty()){panel(graphics,160,145,298,160,WorkbenchTheme.PANEL);text(graphics,"Build your first shop",176,166,WorkbenchTheme.TEXT);text(graphics,"Start with a category or a vanilla preset.",176,183,WorkbenchTheme.MUTED);return;}
        List<Tile> tiles = tiles(); boolean root = category() == null && !enchantPreview;
        int perPage = root ? CategoryLayout.PAGE_SIZE : 45;
        int pages = Math.max(1, (tiles.size() + perPage - 1) / perPage); previewPage = Math.min(previewPage, pages - 1);
        int start = previewPage * perPage, shown = Math.min(perPage, tiles.size() - start);
        int rows = root ? CategoryLayout.rows(shown) : 6;
        int left = 222, top = 160;
        panel(graphics, left - 8, top - 17, 178, rows * 18 + 40, WorkbenchTheme.PREVIEW);
        var previewOffer = draft.config.enchantments.get(enchantId);
        String title=enchantId!=null&&!enchantPreview?com.zycu.guishop.ListingGroups.name(enchantId,minecraft.level.registryAccess()):root?draft.config.shopTitle:category().name;
        text(graphics, font.plainSubstrByWidth(title, 156), left, top - 11, WorkbenchTheme.TEXT);
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
            graphics.item(stack,x+1,y+1);
            if(tile.item!=null&&!visible(tile.item))graphics.fill(x,y,x+18,y+18,0x60303840);
            if (tile.id.equals(selectedItem)) graphics.outline(x, y, 18, 18, 0xFF65D5C1);
            if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                graphics.fill(x, y, x + 18, y + 18, 0x44FFFFFF);
                text(graphics, font.plainSubstrByWidth(tile.name, 278), 169, 297, WorkbenchTheme.MUTED);
                if (tile.item != null) text(graphics, "Buy " + number(tile.item.buy) + " | Sell " + number(tile.item.sell)+(!visible(tile.item)?" | Hidden from players":""), 169, 308, WorkbenchTheme.MUTED);
                else if (!tile.detail.isBlank()) text(graphics, font.plainSubstrByWidth(tile.detail, 278), 169, 308, WorkbenchTheme.MUTED);
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
        if (!root) footer(graphics, left, footer, Items.BARRIER, "back");
        if (previewPage > 0) footer(graphics, left + (root ? 0 : 3 * 18), footer, Items.ARROW, "previous");
        if (previewPage + 1 < pages) footer(graphics, left + (root ? 7 : 5) * 18, footer, Items.ARROW, "next");
        if (shown == 0) text(graphics, root?"No matching categories":"No matching listings", 248, 194, WorkbenchTheme.TEXT);
        for (Hit hit : hits) if (hit.kind.startsWith("preview:") && hit.contains(mouseX, mouseY)) {
            String hint = switch (hit.kind) {
                case "preview:back" -> "Back";
                case "preview:previous" -> "Previous page";
                case "preview:next" -> "Next page";
                default -> "Preview only. No items or money change.";
            };
            text(graphics, hint, 169, 297, WorkbenchTheme.MUTED);
        }
        text(graphics, "Page " + (previewPage + 1) + "/" + pages + (arranging ? " | Arrange mode" : " | Click to edit"), 214, 322, WorkbenchTheme.MUTED);
    }
    private void footer(GuiGraphicsExtractor graphics, int x, int y, net.minecraft.world.item.Item icon, String action) {
        graphics.item(new ItemStack(icon), x + 1, y + 1);
        hits.add(new Hit(x, y, 18, 18, "preview:" + action, "", "", null));
    }
    private void paintSettings(GuiGraphicsExtractor graphics) {
        panel(graphics,16,69,628,270,WorkbenchTheme.PANEL);
        if(settingsSection.equals("General")) {
            text(graphics,"Shop title",32,129,WorkbenchTheme.MUTED);
            multiline(graphics,"The player shop uses vanilla menus. Admin editing requires the matching client mod and server permission.",32,247,580,WorkbenchTheme.MUTED);
        } else if(settingsSection.equals("Economy")) {
            text(graphics,"Currency prefix (display only)",32,129,WorkbenchTheme.MUTED);text(graphics,"Starting balance for new players",350,129,WorkbenchTheme.MUTED);
            text(graphics,"Uses shop values. Saving refreshes existing villagers and wandering traders.",32,308,WorkbenchTheme.MUTED);
            text(graphics,"Off restores original payments. Expensive offers can use emerald blocks.",32,322,WorkbenchTheme.TEAL);
        } else if(settingsSection.equals("Messages")) {
            labels(graphics,new String[]{"Chat prefix","Information color","Warning color"},32,new int[]{129,183,237});labels(graphics,new String[]{"Prefix color","Success color","Error color"},350,new int[]{129,183,237});
            text(graphics,"Choose a color to preview it, then Update and Save changes.",32,296,WorkbenchTheme.MUTED);
        } else if(minecraft.level!=null) {
            VariantScreen.paintSummary(graphics,font,draft.config,minecraft.level.registryAccess(),true,32,165,variantSources);
            VariantScreen.paintSummary(graphics,font,draft.config,minecraft.level.registryAccess(),false,350,165,variantSources);
            multiline(graphics,"Browse registered enchantments and potions here. Set new preset defaults in Presets; edit listing prices in Shop Builder.",32,294,584,WorkbenchTheme.MUTED);
        }
    }
    private void paintAccess(GuiGraphicsExtractor g){
        panel(g,16,69,628,270,WorkbenchTheme.PANEL);
        var info=draft.permissions;
        text(g,"Permissions: "+(info==null?"Vanilla OP levels":info.name()),32,82,WorkbenchTheme.TEAL);
        text(g,"Adminshop: guishop.admin | Default: OP 2+",32,98,WorkbenchTheme.TEXT);
        text(g,info!=null&&info.api()?"Provider rules override defaults. Configure nodes in your permission manager.":"Choose who can use each action. Installing the client grants no permissions.",32,114,WorkbenchTheme.MUTED);
        text(g,"ACTION / PERMISSION NODE",36,135,WorkbenchTheme.MUTED);text(g,"DEFAULT ROLE",430,135,WorkbenchTheme.MUTED);
        var entries=permissionEntries();permissionScroll.count(entries.size());
        for(int row=0;row<6&&permissionScroll.row+row<entries.size();row++){
            var e=entries.get(permissionScroll.row+row);int y=150+row*26;
            g.fill(30,y,420,y+24,WorkbenchTheme.ROW);
            text(g,font.plainSubstrByWidth(permissionName(e.getKey()),375),36,y+2,WorkbenchTheme.TEXT);
            text(g,font.plainSubstrByWidth(e.getKey(),375),36,y+13,WorkbenchTheme.MUTED);
            hits.add(new Hit(30,y,390,24,"permission",e.getKey(),"",null));
        }
        hits.add(new Hit(30,96,574,14,"permission",com.zycu.guishop.ShopPermissions.ADMIN,"",null));
        permissionScroll.paint(g);text(g,"Save changes applies defaults. Hover a row to see its full node.",32,316,WorkbenchTheme.MUTED);
    }
    private void paintHistory(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        hoveredRecord = null;
        panel(graphics, 16, 69, 628, 270, WorkbenchTheme.PANEL);
        if (tab == Tab.TRANSACTIONS && selectedPlayer == null) { paintPlayers(graphics); return; }
        String heading = tab == Tab.HISTORY ? "Configuration updates | Click a change to view its backup" : selectedPlayer.name() + " | Balance: " + accountMoney(selectedPlayer.balance());
        text(graphics, font.plainSubstrByWidth(heading, selectedPlayer == null ? 590 : 278), 32, 82, WorkbenchTheme.MUTED);
        graphics.fill(30,139,629,155,0xFF304850);
        text(graphics,"Time ("+com.zycu.guishop.admin.JournalDates.zoneLabel(localZone)+")",36,143,WorkbenchTheme.TEXT);
        text(graphics,tab==Tab.HISTORY?"Changed by":"Action",180,143,WorkbenchTheme.TEXT);text(graphics,tab==Tab.HISTORY?"Summary":"Activity / item",310,143,WorkbenchTheme.TEXT);if(tab!=Tab.HISTORY)text(graphics,"Total",550,143,WorkbenchTheme.TEXT);
        int y=158,row=0;logNext=logPage;
        for(int index=logPage;index<logData.entries().size();index++){
            var entry=logData.entries().get(index);
            String activity=com.zycu.guishop.admin.TransactionJournal.activity(entry);

            var date=font.split(Component.literal(com.zycu.guishop.admin.JournalDates.display(entry.time(),localZone)),136);
            var action=font.split(Component.literal(tab==Tab.HISTORY?entry.player():com.zycu.guishop.admin.TransactionJournal.action(entry)),120);
            var description=font.split(Component.literal(activity==null?"":activity),tab==Tab.HISTORY?307:230);
            var total=font.split(Component.literal(tab==Tab.HISTORY?"":accountMoney(com.zycu.guishop.admin.TransactionJournal.signedTotal(entry))),76);
            int height=Math.min(132,6+12*Math.max(Math.max(date.size(),action.size()),Math.max(description.size(),total.size())));
            if(y+height>295 && row>0)break;
            graphics.fill(30,y,629,y+height,row++%2==0?0x30283D45:WorkbenchTheme.ROW);
            paintCell(graphics,date,36,y+3,height);paintCell(graphics,action,180,y+3,height);paintCell(graphics,description,310,y+3,height);paintCell(graphics,total,550,y+3,height);
            if(tab==Tab.HISTORY)hits.add(new Hit(30,y,599,height,"log",Integer.toString(index),"",null));
            if(mouseX>=30&&mouseX<629&&mouseY>=y&&mouseY<y+height){hoveredRecord=entry;hoveredDate=mouseX<174;}
            y+=height;logNext=index+1;
        }
        if (logData.entries().isEmpty()) text(graphics, "No matching records in the recent history window.", 32, 181, WorkbenchTheme.MUTED);
        text(graphics, font.plainSubstrByWidth("Local: " + com.zycu.guishop.admin.JournalDates.zoneLabel(localZone) + " (" + localZone.getId() + "). Search local or UTC dates.", 590), 32, 299, WorkbenchTheme.MUTED);
    }
    private void paintCell(GuiGraphicsExtractor graphics,java.util.List<net.minecraft.util.FormattedCharSequence> lines,int x,int y,int height){
        int count=Math.max(1,(height-6)/12);for(int i=0;i<lines.size()&&i<count;i++)graphics.text(font,lines.get(i),x,y+i*12,WorkbenchTheme.TEXT,false);
        if(lines.size()>count)text(graphics,"...",x,y+(count-1)*12,WorkbenchTheme.MUTED);
    }
    private void paintPlayers(GuiGraphicsExtractor graphics) {
        text(graphics, "Select a player to view their transactions", 32, 82, WorkbenchTheme.MUTED);
        graphics.fill(30, 139, 629, 155, 0xFF304850);
        text(graphics, "Player", 36, 143, WorkbenchTheme.TEXT); text(graphics, "Current balance", 390, 143, WorkbenchTheme.TEXT); text(graphics, "Status", 550, 143, WorkbenchTheme.TEXT);
        for (int row = 0; row < 10 && logPage * 10 + row < playerData.players().size(); row++) {
            var player = playerData.players().get(logPage * 10 + row); int y = 158 + row * 14;
            graphics.fill(30, y, 629, y + 14, row % 2 == 0 ? 0x30283D45 : WorkbenchTheme.ROW);
            text(graphics, font.plainSubstrByWidth(player.name(), 330), 36, y + 3, WorkbenchTheme.TEXT);
            text(graphics, font.plainSubstrByWidth(accountMoney(player.balance()), 150), 390, y + 3, WorkbenchTheme.TEXT);
            text(graphics, player.online() ? "Online" : "Offline", 550, y + 3, WorkbenchTheme.TEXT);
            hits.add(new Hit(30, y, 599, 14, "player", Integer.toString(logPage * 10 + row), "", null));
        }
        if (playerData.players().isEmpty()) text(graphics, "No matching players. Refresh or clear the search.", 32, 181, WorkbenchTheme.MUTED);
        text(graphics, "Page " + (logOffset / 10 + logPage + 1) + " | Current balances at refresh. History includes offline players.", 32, 299, WorkbenchTheme.MUTED);
    }
    private void labels(GuiGraphicsExtractor graphics, String[] labels, int x, int[] ys) {
        for (int index = 0; index < labels.length; index++) text(graphics, labels[index], x, ys[index], WorkbenchTheme.MUTED);
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
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
            for (Hit hit : hits) if (hit.contains(x, y) && (hit.kind.equals("tile:item") || hit.kind.equals("tile:level"))) {
                status = "Click a listing to edit it. Arrange mode lets you drag it. Purchases happen in /shop."; return true;
            }
            return false;
        }
        if (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return false;
        for (Hit hit : hits) if (hit.contains(x, y)) {
            if (hit.kind.equals("detected:source")) {
                if (importSource == null) { detectedSource = hit.id; query = ""; requestDetected(0, false); rebuildWidgets(); }
                return true;
            }
            if (hit.kind.startsWith("preview:")) {
                switch (hit.kind) {
                    case "preview:back" -> up();
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
        else if(hit.kind.equals("tile:bookgroup")){enchantId=hit.id;selectedItem=null;previewPage=0;}
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
