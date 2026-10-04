package com.zycu.guishop.testing;

import com.zycu.guishop.GuiShop;
import com.zycu.guishop.ItemStackData;
import com.zycu.guishop.ShopConfig;
import com.zycu.guishop.ShopGui;
import com.zycu.guishop.ShopService;
import com.zycu.guishop.admin.AdminDocument;
import com.zycu.guishop.admin.AdminEditorService;
import com.zycu.guishop.admin.DetectedContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Real loaded resources, codecs and trades, exclusively in disposable worlds. */
public final class DetectedContentChecks {
    private DetectedContentChecks() {}
    public static boolean ready(MinecraftServer server, ServerPlayer player) {
        server.getPlayerList().getOps().add(new ServerOpListEntry(player.nameAndId(), LevelBasedPermissionSet.GAMEMASTER, false));
        return DetectedContent.list(player, new DetectedContent.Query("", "", 0, false, false)).ready();
    }
    public static void run(MinecraftServer server, ServerPlayer player) throws Exception {
        server.getPlayerList().getOps().remove(player.nameAndId());
        try { DetectedContent.list(player, new DetectedContent.Query("", "", 0, false, false)); throw new AssertionError("Non-OP received detected content"); }
        catch (SecurityException expected) {}
        server.getPlayerList().getOps().add(new ServerOpListEntry(player.nameAndId(), LevelBasedPermissionSet.GAMEMASTER, false));
        var original = AdminEditorService.snapshot(player);
        List<DetectedContent.Entry> detected = new ArrayList<>();
        for (int offset = 0;;) {
            var page = DetectedContent.list(player, new DetectedContent.Query("", "", offset, false, false));
            check(page.ready(), "Scan completed"); detected.addAll(page.entries());
            if (!page.more()) break;
            offset += page.entries().size();
        }
        check(AdminEditorService.snapshot(player).revision().equals(original.revision()), "Detection does not alter the shop");
        if (server.getResourceManager().getResource(Identifier.parse("guishop_detection_test:loot_table/items/static.json")).isPresent()) {
            check(detected.stream().anyMatch(entry -> entry.origin().contains("items/static.json")), "Static variant with literal nbt metadata is detected");
            check(detected.stream().noneMatch(entry -> entry.origin().contains("items/unsupported.json")), "Unsupported modifier never creates a partial variant");
        }
        for (var entry : detected) {
            check(entry.listing().buy == 0 && entry.listing().sell == 0 && Boolean.TRUE.equals(entry.listing().manualPrice), "Every detected item starts disabled");
        }
        var usefulIds = net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().stream().filter(id -> id.getNamespace().equals("usefulfood")).toList();
        for (var id : usefulIds) check(detected.stream().anyMatch(entry -> entry.source().equals("usefulfood") && entry.listing().item.equals(id.toString())), "Registered UsefulFood item detected: " + id);
        int matched = 0;
        var resources = server.getResourceManager().listResources("loot_table/items", id -> id.getNamespace().equals("moldomre") && id.getPath().endsWith(".json"));
        var params = new LootParams.Builder(server.overworld()).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
        for (var id : resources.keySet()) {
            String path = id.getPath().substring("loot_table/".length(), id.getPath().length() - 5);
            var key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(id.getNamespace(), path));
            var stacks = server.reloadableRegistries().getLootTable(key).getRandomItems(params, 1);
            check(stacks.size() == 1, "Pack item table produces exactly one item: " + id);
            ItemStack expected = stacks.getFirst();
            check(detected.stream().anyMatch(entry -> ItemStackData.same(entry.listing().createStack(player.registryAccess()), expected)), "Detected exact components match actual loot: " + id);
            matched++;
        }
        if (!resources.isEmpty()) check(matched == 189, "All 189 supplied pack definitions match actual items");
        if (!resources.isEmpty()) check(DetectedContent.list(player, new DetectedContent.Query("moldomre", "crab claw", 0, false, false)).entries().stream().anyMatch(entry -> entry.origin().contains("crab_claw")), "Search handles human words in pack definition names");

        var draft = original.document().copy(); draft.config.categories.clear();
        var first = new ShopConfig.Category(); first.id = "duplicate_first"; first.name = "First"; first.icon = "minecraft:diamond";
        var second = new ShopConfig.Category(); second.id = "duplicate_second"; second.name = "Second"; second.icon = "minecraft:diamond";
        var one = new ShopConfig.ShopItem(); one.item = "minecraft:diamond"; one.listingId = "minecraft:diamond"; one.name = "Diamond"; one.buy = 10; one.sell = 2; one.manualPrice = true;
        var existing = original.document().config.findItem(one.listingId); if (existing != null) one.stack = existing.item().stack;
        var two = AdminDocument.JSON.fromJson(AdminDocument.JSON.toJson(one), ShopConfig.ShopItem.class);
        two.listingId = ItemStackData.unusedListingId(one.listingId, java.util.Set.of(one.listingId)); two.buy = 0; two.sell = 0;
        first.items.add(two); second.items.add(one); draft.config.categories.add(first); draft.config.categories.add(second);
        AdminEditorService.validate(draft, player);
        two.buy = 1; two.sell = 0;
        try { AdminEditorService.validate(draft, player); throw new AssertionError("Cross-category buy/sell exploit accepted"); }
        catch (IllegalArgumentException expected) {}
        two.buy = 0;
        String validId = two.listingId; two.listingId = "fake-item-id";
        try { AdminEditorService.validate(draft, player); throw new AssertionError("Invalid listing identity accepted"); }
        catch (IllegalArgumentException expected) {}
        two.listingId = validId;
        draft.config.permissionDefaults.put("guishop.category.duplicate_first", 4);
        var saved = AdminEditorService.apply(player, original.revision(), draft);
        String quoted = com.mojang.brigadier.arguments.StringArgumentType.escapeIfRequired(two.listingId);
        check(server.getCommands().getDispatcher().execute("worth " + quoted + " 2", player.createCommandSourceStack()) == 1, "Worth accepts the exact duplicate listing ID and amount");
        var suggestions = server.getCommands().getDispatcher().getCompletionSuggestions(server.getCommands().getDispatcher().parse("worth \"minecraft:diamond@", player.createCommandSourceStack())).join();
        check(suggestions.getList().stream().anyMatch(value -> value.getText().equals(quoted)), "Duplicate listing suggestions include the required quotes");
        player.getInventory().clearContent(); player.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
        double balance = GuiShop.ECONOMY.balance(player.getUUID());
        check(ShopService.sellHand(player, 1, false), "Held sale finds enabled, accessible duplicate after a restricted disabled listing");
        check(GuiShop.ECONOMY.balance(player.getUUID()) == balance + 2, "Duplicate sale uses the correct price");
        List<DetectedContent.Entry> tradeCases = new ArrayList<>();
        if (!detected.isEmpty()) tradeCases.add(detected.getFirst());
        detected.stream().filter(entry -> entry.source().equals("usefulfood")).findFirst().ifPresent(tradeCases::add);
        for (var tradeCase : tradeCases) {
            var imported = saved.document().copy(); var category = imported.config.categories.getLast();
            var item = AdminDocument.JSON.fromJson(AdminDocument.JSON.toJson(tradeCase.listing()), ShopConfig.ShopItem.class);
            category.items.add(item);
            var disabled = AdminEditorService.apply(player, saved.revision(), imported);
            check(!ShopService.trade(player, GuiShop.CONFIG.findItem(item.listingId).item(), ShopGui.Mode.BUY, 1), "Zero-price detected item cannot be bought");
            var priced = disabled.document().copy(); var pricedItem = priced.config.findItem(item.listingId).item(); pricedItem.buy = 5; pricedItem.sell = 1;
            var pricedSaved = AdminEditorService.apply(player, disabled.revision(), priced);
            var live = GuiShop.CONFIG.findItem(item.listingId).item(); player.getInventory().clearContent();
            check(ShopService.trade(player, live, ShopGui.Mode.BUY, 1), "Admin-enabled custom item can be bought");
            check(ShopService.count(player, item.createStack(player.registryAccess())) == 1, "Purchased custom item preserves exact components");
            player.getInventory().setItem(0, new ItemStack(item.createStack(player.registryAccess()).getItem()));
            if (!ItemStackData.same(new ItemStack(item.createStack(player.registryAccess()).getItem()), item.createStack(player.registryAccess()))) {
                check(!ShopService.trade(player, live, ShopGui.Mode.SELL, 1), "Plain base item cannot be sold as a custom variant");
            }
            player.getInventory().clearContent(); player.getInventory().setItem(0, item.createStack(player.registryAccess()));
            check(ShopService.trade(player, live, ShopGui.Mode.SELL, 1), "Exact custom variant can be sold");
            saved = pricedSaved;
        }
        AdminEditorService.apply(player, saved.revision(), original.document());
        server.getPlayerList().getOps().remove(player.nameAndId());
        System.out.println("[Detected content test] PASS: read-only scan; zero prices; exact codec data; " + matched + " supplied pack variants match actual loot; " + usefulIds.size() + " UsefulFood items detected; independent duplicate listings; price exploit guard; permitted duplicate sale; exact custom trades");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
