package com.zycu.guishop.admin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.zycu.guishop.ItemStackData;
import com.zycu.guishop.ShopConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Explicit, read-only discovery. Never runs loot tables, commands, random modifiers, or player effects. */
public final class DetectedContent {
    public static final int PAGE_SIZE = 24;
    private static final int MAX_ITEMS = 10000, MAX_FILES = 4000, MAX_FILE_BYTES = 256000, MAX_SCAN_BYTES = 16000000;
    private static final Set<String> STATIC_MODIFIERS = Set.of("minecraft:set_components", "minecraft:set_name", "minecraft:set_lore");
    public record Query(String source, String filter, int offset, boolean refresh, boolean importing, long generation) {
        public Query(String source, String filter, int offset, boolean refresh, boolean importing) { this(source, filter, offset, refresh, importing, 0); }
    }
    public record Source(String id, String name, int count) {}
    public record Entry(String source, String origin, ShopConfig.ShopItem listing) {}
    public record Page(List<Source> sources, List<Entry> entries, int offset, boolean more, boolean ready, String warning, int skipped) {}
    private record Input(Identifier id, JsonElement json, String pack) {}
    private record Inputs(List<Input> files, int skipped, String warning) {}
    private static Scan active;
    private DetectedContent() {}

    public static void register() { ServerTickEvents.END_SERVER_TICK.register(DetectedContent::tick); }
    public static void clear() { if (active != null) active.read.cancel(true); active = null; }

    public static Page list(ServerPlayer player, Query query) {
        if (!AdminEditorService.allowed(player)) throw new SecurityException(AdminEditorService.DENIED);
        String source = query.source() == null ? "" : query.source();
        String filter = query.filter() == null ? "" : query.filter().toLowerCase(Locale.ROOT);
        if (source.length() > 120 || filter.length() > 128 || query.offset() < 0 || query.offset() > MAX_ITEMS) throw new IllegalArgumentException("Invalid detection query");
        var server = player.createCommandSourceStack().getServer();
        if (active == null || active.server != server || active.manager != server.getResourceManager()
            || query.refresh() && active.ready && System.nanoTime() - active.started > 5_000_000_000L) {
            clear(); active = new Scan(server);
        }
        if (!active.ready) return new Page(List.of(), List.of(), 0, false, false, "Inspecting loaded content... " + (active.cursor + active.registryCursor) + " definitions checked.", active.skipped);
        var entries = active.entries.stream().filter(entry -> source.isEmpty() || entry.source().equals(source))
            .filter(entry -> searchMatches(entry, filter)).toList();
        int offset = Math.min(query.offset(), entries.size()), end = Math.min(entries.size(), offset + PAGE_SIZE);
        return new Page(active.sources, entries.subList(offset, end), offset, end < entries.size(), true, active.warning, active.skipped);
    }

    private static boolean searchMatches(Entry entry, String filter) {
        String text = (entry.origin() + " " + entry.listing().item + " " + entry.listing().name).toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        for (String word : filter.replace('_', ' ').replace('-', ' ').trim().split("\\s+")) if (!text.contains(word)) return false;
        return true;
    }

    private static final class Scan {
        final MinecraftServer server;
        final ResourceManager manager;
        final long started = System.nanoTime();
        final CompletableFuture<Inputs> read;
        final List<Identifier> registryItems;
        final List<Entry> entries = new ArrayList<>();
        final Map<Integer, List<ItemStack>> identities = new HashMap<>();
        final Map<String, String> sourceNames = new HashMap<>();
        List<Source> sources = List.of();
        List<Input> files;
        int cursor, registryCursor, skipped, catalogBytes;
        boolean ready, full;
        String warning = "";
        Scan(MinecraftServer server) {
            this.server = server; manager = server.getResourceManager();
            registryItems = BuiltInRegistries.ITEM.keySet().stream().filter(id -> !id.getNamespace().equals("minecraft"))
                .sorted(Comparator.comparing(Identifier::toString)).toList();
            read = CompletableFuture.supplyAsync(() -> readDefinitions(manager));
        }
        void add(String source, String origin, ItemStack stack) {
            if (full) return;
            if (entries.size() >= MAX_ITEMS) { full = true; warning = "Detection reached the 10,000 item limit. Use Add held item for additional content."; return; }
            if (stack.isEmpty()) return;
            stack = stack.copyWithCount(1);
            var bucket = identities.computeIfAbsent(ItemStack.hashItemAndComponents(stack), ignored -> new ArrayList<>());
            for (var existing : bucket) if (ItemStackData.same(existing, stack)) return;
            var data = ItemStackData.encode(stack, server.registryAccess());
            if (data.toString().length() > 32000) throw new IllegalArgumentException("Exact item data is too large");
            int bytes = data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (catalogBytes + bytes > MAX_SCAN_BYTES) { full = true; warning = "Detection reached the 16 MB catalog limit. Use Add held item for additional content."; return; }
            var listing = new ShopConfig.ShopItem();
            listing.item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            listing.listingId = ItemStackData.listingId(stack, server.registryAccess());
            listing.stack = data; listing.name = stack.getHoverName().getString(); listing.manualPrice = true;
            entries.add(new Entry(source, origin, listing)); bucket.add(stack); catalogBytes += bytes;
        }
        void finish() {
            entries.sort(Comparator.comparing(Entry::source).thenComparing(Entry::origin).thenComparing(entry -> entry.listing().listingId));
            var counts = new java.util.TreeMap<String, Integer>();
            for (var entry : entries) counts.merge(entry.source(), 1, Integer::sum);
            sources = counts.entrySet().stream().map(entry -> new Source(entry.getKey(),
                sourceNames.getOrDefault(entry.getKey(), FabricLoader.getInstance().getModContainer(entry.getKey()).map(mod -> mod.getMetadata().getName()).orElse(entry.getKey())), entry.getValue())).toList();
            if (skipped > 0) warning = (warning.isBlank() ? "" : warning + " ") + skipped + " unsupported or invalid definitions skipped. Use Add held item for those.";
            ready = true;
        }
    }

    private static Inputs readDefinitions(ResourceManager manager) {
        List<Input> files = new ArrayList<>(); int skipped = 0, bytes = 0, visited = 0; String warning = "";
        try {
            var resources = manager.listResources("loot_table", id -> id.getPath().endsWith(".json"));
            for (var entry : resources.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString))).toList()) {
                if (entry.getKey().getNamespace().equals("minecraft") && entry.getValue().sourcePackId().equals("vanilla")) continue;
                if (++visited > MAX_FILES || bytes >= MAX_SCAN_BYTES) { warning = "Definition scan limit reached; remaining files were not inspected."; break; }
                try (var stream = entry.getValue().open()) {
                    byte[] data = stream.readNBytes(MAX_FILE_BYTES + 1); bytes += data.length;
                    if (data.length > MAX_FILE_BYTES || bytes > MAX_SCAN_BYTES) { skipped++; continue; }
                    files.add(new Input(entry.getKey(), JsonParser.parseString(new String(data, java.nio.charset.StandardCharsets.UTF_8)), entry.getValue().sourcePackId()));
                } catch (Exception invalid) { skipped++; }
            }
        } catch (Exception invalid) { warning = "Some datapack resources could not be inspected. Use Add held item if content is missing."; }
        return new Inputs(files, skipped, warning);
    }

    private static void tick(MinecraftServer server) {
        Scan scan = active;
        if (scan == null || scan.ready || scan.server != server) return;
        if (scan.manager != server.getResourceManager()) { clear(); return; }
        long tickStarted = System.nanoTime();
        for (int checked = 0; checked < 64 && scan.registryCursor < scan.registryItems.size(); checked++) {
            var id = scan.registryItems.get(scan.registryCursor++);
            try { scan.add(id.getNamespace(), "Registered item: " + id, new ItemStack(BuiltInRegistries.ITEM.getValue(id))); }
            catch (RuntimeException invalid) { scan.skipped++; }
            if (System.nanoTime() - tickStarted > 5_000_000L) return;
        }
        if (scan.registryCursor < scan.registryItems.size()) return;
        if (scan.full) { scan.finish(); return; }
        if (scan.files == null) {
            if (!scan.read.isDone()) return;
            try { var input = scan.read.join(); scan.files = input.files(); scan.skipped += input.skipped(); if (!input.warning().isBlank()) scan.warning = input.warning(); }
            catch (RuntimeException failed) { scan.warning = "Datapack inspection failed. Try Rescan or use Add held item."; scan.finish(); return; }
        }
        // Codec work stays on the server thread and is spread across ticks.
        for (int checked = 0; checked < 8 && scan.cursor < scan.files.size(); checked++) {
            Input input = scan.files.get(scan.cursor++);
            try { inspect(scan, input); }
            catch (RuntimeException unsupported) { scan.skipped++; }
            if (scan.full || System.nanoTime() - tickStarted > 5_000_000L) break;
        }
        if (scan.full || scan.cursor == scan.files.size()) scan.finish();
    }

    private static void inspect(Scan scan, Input input) {
        JsonObject table = input.json().getAsJsonObject();
        List<ItemStack> candidates = new ArrayList<>();
        LootContext context = new LootContext.Builder(new LootParams.Builder(scan.server.overworld()).create(LootContextParamSets.EMPTY))
            .withOptionalRandomSource(net.minecraft.util.RandomSource.create(1)).create(Optional.empty());
        int[] budget = {256};
        if (!table.has("pools")) return;
        for (var rawPool : table.getAsJsonArray("pools")) {
            var pool = rawPool.getAsJsonObject();
            for (var entry : pool.getAsJsonArray("entries")) {
                List<ItemStack> stacks = new ArrayList<>(); extract(scan, entry.getAsJsonObject(), stacks, context, budget, 0);
                for (var stack : stacks) {
                    stack = modify(scan, stack, pool.get("modifier"), context);
                    stack = modify(scan, stack, table.get("modifier"), context);
                    if (!ItemStackData.same(stack, new ItemStack(stack.getItem()))) candidates.add(stack);
                }
            }
        }
        // Commit only after the entire definition passed. Never import a partially reconstructed stack.
        String source = input.id().getNamespace();
        if (source.equals("minecraft")) {
            source = "datapack_" + ItemStackData.shortHash(input.pack());
            scan.sourceNames.put(source, input.pack().replaceFirst("^file/", ""));
        }
        for (var stack : candidates) scan.add(source, input.id() + " | " + input.pack(), stack);
    }

    private static void extract(Scan scan, JsonObject entry, List<ItemStack> result, LootContext context, int[] budget, int depth) {
        if (--budget[0] < 0 || depth > 16) throw new IllegalArgumentException("Oversized loot definition");
        String type = Identifier.parse(entry.get("type").getAsString()).toString();
        List<ItemStack> stacks = new ArrayList<>();
        if (type.equals("minecraft:item")) {
            Identifier id = Identifier.parse(entry.get("name").getAsString());
            if (!BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ITEM.getValue(id) == Items.AIR) throw new IllegalArgumentException("Unknown item");
            stacks.add(new ItemStack(BuiltInRegistries.ITEM.getValue(id)));
        } else if (Set.of("minecraft:alternatives", "minecraft:group", "minecraft:sequence").contains(type)) {
            for (var child : entry.getAsJsonArray("children")) extract(scan, child.getAsJsonObject(), stacks, context, budget, depth + 1);
        } else if (!type.equals("minecraft:empty")) throw new IllegalArgumentException("Contextual or referenced loot entry");
        for (var stack : stacks) result.add(modify(scan, stack, entry.get("modifier"), context));
    }

    private static ItemStack modify(Scan scan, ItemStack stack, JsonElement data, LootContext context) {
        if (data == null || data.isJsonNull()) return stack;
        if (data.isJsonArray()) { for (var modifier : data.getAsJsonArray()) stack = modify(scan, stack, modifier, context); return stack; }
        var object = data.getAsJsonObject();
        String type = Identifier.parse(object.get("type").getAsString()).toString();
        if (!STATIC_MODIFIERS.contains(type) || object.has("condition") || object.has("conditions") || object.has("entity")) throw new IllegalArgumentException("Nonstatic item modifier");
        var function = LootItemFunctions.DIRECT_CODEC.parse(scan.server.registryAccess().createSerializationContext(JsonOps.INSTANCE), object).getOrThrow();
        boolean contextual = false;
        if (type.equals("minecraft:set_components")) {
            var components = object.getAsJsonObject("components");
            for (String key : List.of("minecraft:custom_name", "minecraft:item_name", "minecraft:lore", "custom_name", "item_name", "lore")) {
                if (components.has(key) && contextualText(components.get(key), 0)) contextual = true;
            }
        } else contextual = contextualText(object, 0);
        if (!function.getReferencedContextParams().isEmpty() || contextual) throw new IllegalArgumentException("Contextual text modifier");
        return function.apply(stack, context);
    }

    private static boolean contextualText(JsonElement data, int depth) {
        if (depth > 64) return true;
        if (data.isJsonObject()) {
            var object = data.getAsJsonObject();
            if (object.has("selector") || object.has("score") || object.has("nbt")) return true;
            for (var value : object.entrySet()) if (contextualText(value.getValue(), depth + 1)) return true;
        } else if (data.isJsonArray()) for (var value : data.getAsJsonArray()) if (contextualText(value, depth + 1)) return true;
        return false;
    }
}
