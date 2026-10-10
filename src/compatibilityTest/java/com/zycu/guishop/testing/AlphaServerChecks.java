package com.zycu.guishop.testing;

import com.zycu.guishop.GuiShop;
import com.zycu.guishop.ShopConfig;
import com.zycu.guishop.ShopPermissions;
import com.zycu.guishop.ShopService;
import com.zycu.guishop.admin.AdminDocument;
import com.zycu.guishop.admin.AdminEditorService;
import com.zycu.guishop.admin.AdminPayloads;
import com.zycu.guishop.admin.TransactionJournal;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.nio.file.Files;
import java.util.List;

public final class AlphaServerChecks {
    private AlphaServerChecks() {}
    public static void run(MinecraftServer server, ServerPlayer player) {
        try {
            checkIcons(player);
            // This disposable account is reused across restarts; previous trade checks consume funds.
            double fixtureBalance = GuiShop.ECONOMY.balance(player.getUUID());
            if (fixtureBalance < 1000) GuiShop.ECONOMY.deposit(player.getUUID(), 1000 - fixtureBalance);
            server.getPlayerList().getOps().remove(player.nameAndId());
            check(!AdminEditorService.allowed(player), "Non-OP must not access admin tools");
            try { com.zycu.guishop.admin.AdminPlayers.list(player,player.getUUID().toString(),0); throw new AssertionError("Non-OP received player balances"); }
            catch (SecurityException expected) {}
            try { AdminEditorService.snapshot(player); throw new AssertionError("Non-OP received a snapshot"); }
            catch (SecurityException expected) { check(expected.getMessage().equals(AdminEditorService.DENIED), "Exact denial message"); }
            server.getPlayerList().getOps().add(new ServerOpListEntry(player.nameAndId(), LevelBasedPermissionSet.MODERATOR, false));
            check(!AdminEditorService.allowed(player), "OP level 1 must not edit the shop");
            server.getPlayerList().getOps().add(new ServerOpListEntry(player.nameAndId(), LevelBasedPermissionSet.GAMEMASTER, false));
            check(AdminEditorService.allowed(player), "OP level 2 can edit");
            check(!ShopPermissions.check(player, "test.owner", 4), "OP level 2 must not inherit owner level 4");

            String large = "x".repeat(17000) + "\uD83D\uDC8E".repeat(6000);
            var chunks = AdminPayloads.chunks("test", "apply", "revision", large);
            var assembly = new AdminPayloads.Assembly(chunks.getFirst());
            for (var chunk : chunks) assembly.add(chunk);
            check(assembly.body().equals(large), "Unicode chunk round trip");
            try { new AdminPayloads.Assembly(chunks.getFirst()).add(chunks.get(1)); throw new AssertionError("Out-of-order chunk accepted"); }
            catch (IllegalArgumentException expected) {}

            var original = AdminEditorService.snapshot(player);
            var changed=original.document().copy();changed.config.shopTitle="Alpha validation "+java.util.UUID.randomUUID();
            var category = new ShopConfig.Category(); category.id = "alpha_test"; category.name = "Alpha Test"; category.icon = "minecraft:diamond";
            var iconTemplate = new ItemStack(Items.DIAMOND);
            iconTemplate.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(net.minecraft.network.chat.Component.literal("Default icon lore"))));
            category.iconStack = com.zycu.guishop.ItemStackData.encode(iconTemplate, player.registryAccess()); category.description = "Our category help";
            var item = new ShopConfig.ShopItem(); item.item = "minecraft:diamond"; item.listingId = "minecraft:diamond";
            var existingDiamond = original.document().config.findItem(item.listingId);
            if (existingDiamond != null && existingDiamond.item().listingId.equals(item.listingId) && existingDiamond.item().stack != null) {
                item.stack = existingDiamond.item().stack.deepCopy();
            }
            item.name = "Test Diamond"; item.buy = 10; item.sell = 2; item.manualPrice = true; category.items.add(item);
            // A deterministic standalone fixture, never a real player's shop.
            changed.config.categories.clear(); changed.config.categories.add(category);
            var legacyPath = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("guishop/folders.json");
            // A missing legacy file must not have been generated at startup. A present file may even be malformed.
            if (!Files.exists(legacyPath)) Files.writeString(legacyPath, "Retired folder fixture: keep these bytes unchanged.\n");
            byte[] legacyBytes = Files.readAllBytes(legacyPath);
            check(AdminEditorService.snapshot(player).revision().equals(original.revision()), "Legacy data does not affect editor revisions");
            check(!AdminDocument.JSON.toJson(changed).contains("\"folders\""), "No folders in editor document");
            var invalid = changed.copy(); invalid.config.categories.getFirst().items.getFirst().buy = Double.NaN;
            try { AdminEditorService.validate(invalid, player); throw new AssertionError("NaN price accepted"); }
            catch (IllegalArgumentException expected) {}
            invalid = changed.copy(); invalid.config.categories.add(category);
            try { AdminEditorService.validate(invalid, player); throw new AssertionError("Duplicate category accepted"); }
            catch (IllegalArgumentException expected) {}
            invalid = changed.copy(); invalid.config.categories.getFirst().iconStack = com.zycu.guishop.ItemStackData.encode(new ItemStack(Items.STONE), player.registryAccess());
            try { AdminEditorService.validate(invalid, player); throw new AssertionError("Mismatched exact category icon accepted"); }
            catch (IllegalArgumentException expected) {}
            var saved = AdminEditorService.apply(player, original.revision(), changed);
            check(GuiShop.CONFIG.category(category.id).items.getFirst().listingId.equals(item.listingId), "Category listing saved");
            check(GuiShop.CONFIG.category(category.id).iconStack.equals(category.iconStack), "Exact icon components saved");
            check(java.util.Arrays.equals(Files.readAllBytes(legacyPath), legacyBytes), "Apply leaves legacy folder bytes untouched");
            try { AdminEditorService.apply(player, original.revision(), changed); throw new AssertionError("Stale draft accepted"); }
            catch (IllegalStateException expected) {}
            check(Files.isDirectory(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("guishop/backups")), "Backup created");
            var changeRecords = TransactionJournal.query(player.getUUID().toString(), 0, true).entries();
            String backupId = changeRecords.getFirst().details().split(" ")[1];
            var backup = legacyPath.getParent().resolve("backups").resolve(backupId);
            check(java.util.Arrays.equals(Files.readAllBytes(backup.resolve("folders.json")), legacyBytes), "Legacy data copied into backup unchanged");
            // Documents from Alpha 1 may still contain folder fields; they must restore categories safely.
            var oldDocument = AdminDocument.JSON.toJsonTree(original.document()).getAsJsonObject();
            oldDocument.add("folders", com.google.gson.JsonParser.parseString("{\"alpha_test\":{\"folders\":[{\"id\":\"gems\"}],\"assignments\":{\"minecraft:diamond\":\"gems\"}}}"));
            Files.writeString(backup.resolve("editor-before.json"), oldDocument.toString());
            var restored = AdminEditorService.restoreDraft(player, backupId);
            check(AdminDocument.JSON.toJson(restored.previous()).equals(AdminDocument.JSON.toJson(original.document())), "Backup restores previous draft");
            check(GuiShop.CONFIG.category("alpha_test") != null, "Loading a backup does not change the live shop");
            var settingsPath = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("guishop/settings.json");
            byte[] settingsBytes = Files.readAllBytes(settingsPath);
            try {
                Files.writeString(settingsPath, Files.readString(settingsPath) + "\n");
                try { AdminEditorService.snapshot(player); throw new AssertionError("External edits ignored"); }
                catch (IllegalStateException expected) {}
            } finally { Files.write(settingsPath, settingsBytes); }

            player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
            check(server.getCommands().getDispatcher().execute("adminshop", player.createCommandSourceStack()) == 0, "Admin without client gets installation response");
            check(server.getCommands().getDispatcher().execute("adminshop", server.createCommandSourceStack()) == 0, "Console receives in-game-only guidance");
            com.zycu.guishop.HybridShopGui.openCategories(player, com.zycu.guishop.ShopGui.Mode.BUY);
            var menuIcon = player.containerMenu.slots.stream().map(slot -> slot.getItem()).filter(stack -> "Alpha Test".equals(stack.getHoverName().getString())).findFirst().orElseThrow();
            var iconTooltip = menuIcon.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(server.overworld()), player, net.minecraft.world.item.TooltipFlag.NORMAL).stream().map(net.minecraft.network.chat.Component::getString).toList();
            check(iconTooltip.equals(List.of("Alpha Test", "Our category help")), "Actual shop category includes only shop tooltip, without prototype lore");
            player.closeContainer();
            player.getInventory().clearContent();
            double beforeBalance = GuiShop.ECONOMY.balance(player.getUUID());
            var live = GuiShop.CONFIG.findItem("minecraft:diamond").item();
            check(ShopService.trade(player, live, com.zycu.guishop.ShopGui.Mode.BUY, 2), "Purchase executed");
            check(Math.abs(GuiShop.ECONOMY.balance(player.getUUID()) - (beforeBalance - 20)) < 0.000001, "Purchase balance");
            check(ShopService.trade(player, live, com.zycu.guishop.ShopGui.Mode.SELL, 1), "Sale executed");
            check(Math.abs(GuiShop.ECONOMY.balance(player.getUUID()) - (beforeBalance - 18)) < 0.000001, "Sale balance");
            var records = TransactionJournal.query(player.getUUID().toString(), 0, false).entries();
            check(records.stream().anyMatch(entry -> entry.action().equals("BUY") && entry.quantity() == 2 && entry.total() == 20), "Purchase journal");
            check(records.stream().anyMatch(entry -> entry.action().equals("SELL") && entry.quantity() == 1 && entry.total() == 2), "Sale journal");
            var roster = com.zycu.guishop.admin.AdminPlayers.list(player,player.getUUID().toString(),0);
            var row = roster.players().stream().filter(value -> value.uuid().equals(player.getUUID().toString())).findFirst().orElseThrow();
            check(row.name().equalsIgnoreCase(player.getName().getString()), "Directory provides player names");
            check(row.balance() == GuiShop.ECONOMY.readBalance(player.getUUID()), "Roster uses current balance");
            int accounts = GuiShop.ECONOMY.knownPlayers().size();
            GuiShop.ECONOMY.readBalance(java.util.UUID.randomUUID());
            check(GuiShop.ECONOMY.knownPlayers().size() == accounts, "Balance browsing does not create accounts");
            // An unrelated actor can mention the selected UUID. Substring filtering must not leak their record.
            String isolationId = "uuid-probe-" + java.util.UUID.randomUUID();
            TransactionJournal.append(new TransactionJournal.Entry(java.time.Instant.now().toString(), "Lookalike", java.util.UUID.randomUUID().toString(), "BUY", isolationId, "", 1, 1, 1, 1, player.getUUID().toString()));
            TransactionJournal.record(player, "BUY", isolationId, "", 1, 1, 1, "Test history only; no trade");
            var isolated = TransactionJournal.query(isolationId, 0, false, player.getUUID().toString());
            check(isolated.entries().size() == 1 && isolated.entries().getFirst().uuid().equals(player.getUUID().toString()), "History filters exact actor UUID, not text mentions");
            check(com.zycu.guishop.admin.AdminPlayers.list(player, row.name(), 0).players().stream().anyMatch(value -> value.uuid().equals(row.uuid())), "Player name search");
            var restricted = saved.document().copy(); restricted.config.permissionDefaults.put("guishop.category.alpha_test", 4);
            var restrictedSaved = AdminEditorService.apply(player, saved.revision(), restricted);
            int beforeDeniedSale = ShopService.count(player, new ItemStack(Items.DIAMOND));
            check(!ShopService.sellHand(player, 1, false), "Held sale respects category permission");
            check(ShopService.count(player, new ItemStack(Items.DIAMOND)) == beforeDeniedSale, "Denied sale preserves inventory");
            var zero = restrictedSaved.document().copy(); zero.config.permissionDefaults.put("guishop.category.alpha_test", 0); zero.config.categories.getFirst().items.getFirst().sell = 0;
            var zeroSaved = AdminEditorService.apply(player, restrictedSaved.revision(), zero);
            int count = ShopService.count(player, new ItemStack(Items.DIAMOND));
            check(!ShopService.sellHand(player, 1, false), "Zero-value sale rejected");
            check(ShopService.count(player, new ItemStack(Items.DIAMOND)) == count, "Zero-value sale preserves inventory");
            check(!ShopService.trade(player, live, com.zycu.guishop.ShopGui.Mode.BUY, 1), "Old menu cannot transact after Apply");

            AdminEditorService.apply(player, zeroSaved.revision(), original.document());
            CombinedShopChecks.run(player);
            AccountChecks.run(player);
            ModesPresetChecks.run(player);
            BetaChecks.run(player);
            Beta2Checks.run(player);
            Beta4Checks.run(player);
            check(java.util.Arrays.equals(Files.readAllBytes(legacyPath), legacyBytes), "Legacy data survives repeated Apply and restore");
            server.getPlayerList().getOps().remove(player.nameAndId());
            try { AdminEditorService.apply(player, "", changed); throw new AssertionError("Revoked OP applied a draft"); }
            catch (SecurityException expected) {}
            System.out.println("[ClassicGUIShop Alpha test] PASS: OP permissions, bounded Unicode chunks, invalid drafts, backups and restore, external edits, conflicts, category persistence and legacy folder preservation, purchases, sales, journal, player names and current balances, exact UUID history isolation, zero-value guard, stale-menu guard, revoked permissions");
        } catch (Exception exception) { throw new AssertionError("Alpha server checks failed", exception); }
    }
    private static void checkIcons(ServerPlayer player) {
        var source = new ItemStack(Items.GOAT_HORN);
        source.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
            net.minecraft.network.chat.Component.literal("Backpack Tier: Leather"),
            net.minecraft.network.chat.Component.literal("Hold Shift for Special Ability Description"))));
        source.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, net.minecraft.resources.Identifier.withDefaultNamespace("diamond"));
        var components = source.getComponentsPatch();
        var context = net.minecraft.world.item.Item.TooltipContext.of(player.level());
        check(source.getTooltipLines(context, player, net.minecraft.world.item.TooltipFlag.NORMAL).stream().anyMatch(line -> line.getString().contains("Backpack Tier")), "Fixture actually carries item help text");
        var icon = com.zycu.guishop.ShopIcons.display(source, "Backpacks | Buy", "Shop description");
        check(icon.getTooltipLines(context, player, net.minecraft.world.item.TooltipFlag.NORMAL).stream().map(net.minecraft.network.chat.Component::getString).toList().equals(List.of("Backpacks | Buy", "Shop description")), "Icon excludes default lore and arbitrary item help hooks");
        check(icon.get(net.minecraft.core.component.DataComponents.ITEM_MODEL).equals(source.get(net.minecraft.core.component.DataComponents.ITEM_MODEL)), "Selected item model retained");
        check(icon.is(Items.PAPER), "Decorative carrier cannot call the source item's specialized tooltip hook");
        check(source.getComponentsPatch().equals(components), "Icon cleanup leaves original stack untouched");
        var blank = com.zycu.guishop.ShopIcons.display(source, "Backpacks | Sell", "");
        check(blank.getTooltipLines(context, player, net.minecraft.world.item.TooltipFlag.NORMAL).size() == 1, "Empty category description injects no default lore");
        System.out.println("[Shop icon test] PASS: native lore and item tooltip hooks suppressed; shop name and optional description retained; selected model retained; source stack unchanged");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
