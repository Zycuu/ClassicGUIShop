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
            server.getPlayerList().getOps().remove(player.nameAndId());
            check(!AdminEditorService.allowed(player), "Non-OP must not access admin tools");
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
            var changed = original.document().copy();
            var category = new ShopConfig.Category(); category.id = "alpha_test"; category.name = "Alpha Test"; category.icon = "minecraft:diamond";
            var item = new ShopConfig.ShopItem(); item.item = "minecraft:diamond"; item.listingId = "minecraft:diamond";
            var existingDiamond = original.document().config.findItem(item.listingId);
            if (existingDiamond != null && existingDiamond.item().listingId.equals(item.listingId) && existingDiamond.item().stack != null) {
                item.stack = existingDiamond.item().stack.deepCopy();
            }
            item.name = "Test Diamond"; item.buy = 10; item.sell = 2; item.manualPrice = true; category.items.add(item);
            // A deterministic standalone fixture, never a real player's shop.
            changed.config.categories.clear(); changed.config.categories.add(category); changed.folders.clear();
            var folder = new com.zycu.guishop.ShopFolderStore.Folder(); folder.id = "gems"; folder.name = "Gems"; folder.icon = "minecraft:emerald";
            var layout = new AdminDocument.FolderLayout(); layout.folders.add(folder); layout.assignments.put(item.listingId, folder.id);
            changed.folders.put(category.id, layout);
            var invalid = changed.copy(); invalid.config.categories.getFirst().items.getFirst().buy = Double.NaN;
            try { AdminEditorService.validate(invalid, player); throw new AssertionError("NaN price accepted"); }
            catch (IllegalArgumentException expected) {}
            invalid = changed.copy(); invalid.folders.get(category.id).assignments.put("missing", folder.id);
            try { AdminEditorService.validate(invalid, player); throw new AssertionError("Dangling assignment accepted"); }
            catch (IllegalArgumentException expected) {}
            var saved = AdminEditorService.apply(player, original.revision(), changed);
            check(GuiShop.FOLDERS.folderFor(category.id, item.listingId).equals(folder.id), "Folder assignment saved");
            try { AdminEditorService.apply(player, original.revision(), changed); throw new AssertionError("Stale draft accepted"); }
            catch (IllegalStateException expected) {}
            check(Files.isDirectory(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("guishop/backups")), "Backup created");
            var changeRecords = TransactionJournal.query(player.getUUID().toString(), 0, true).entries();
            String backupId = changeRecords.getFirst().details().split(" ")[1];
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
            player.getInventory().clearContent();
            double beforeBalance = GuiShop.ECONOMY.balance(player.getUUID());
            var live = GuiShop.CONFIG.findItem("minecraft:diamond").item();
            check(ShopService.trade(player, live, com.zycu.guishop.ShopGui.Mode.BUY, 2), "Purchase executed");
            check(GuiShop.ECONOMY.balance(player.getUUID()) == beforeBalance - 20, "Purchase balance");
            check(ShopService.trade(player, live, com.zycu.guishop.ShopGui.Mode.SELL, 1), "Sale executed");
            check(GuiShop.ECONOMY.balance(player.getUUID()) == beforeBalance - 18, "Sale balance");
            var records = TransactionJournal.query(player.getUUID().toString(), 0, false).entries();
            check(records.stream().anyMatch(entry -> entry.action().equals("BUY") && entry.quantity() == 2 && entry.total() == 20), "Purchase journal");
            check(records.stream().anyMatch(entry -> entry.action().equals("SELL") && entry.quantity() == 1 && entry.total() == 2), "Sale journal");
            var restricted = saved.document().copy(); restricted.config.permissionDefaults.put("guishop.category.alpha_test", 4);
            var restrictedSaved = AdminEditorService.apply(player, saved.revision(), restricted);
            int beforeDeniedSale = ShopService.count(player, new ItemStack(Items.DIAMOND));
            check(!ShopService.sellHand(player, 1, false), "Held sale respects category permission");
            check(ShopService.count(player, new ItemStack(Items.DIAMOND)) == beforeDeniedSale, "Denied sale preserves inventory");
            var zero = restrictedSaved.document().copy(); zero.config.permissionDefaults.put("guishop.category.alpha_test", 0); zero.config.priceMultiplier = 0;
            var zeroSaved = AdminEditorService.apply(player, restrictedSaved.revision(), zero);
            int count = ShopService.count(player, new ItemStack(Items.DIAMOND));
            check(!ShopService.sellHand(player, 1, false), "Zero-value sale rejected");
            check(ShopService.count(player, new ItemStack(Items.DIAMOND)) == count, "Zero-value sale preserves inventory");
            check(!ShopService.trade(player, live, com.zycu.guishop.ShopGui.Mode.BUY, 1), "Old menu cannot transact after Apply");

            AdminEditorService.apply(player, zeroSaved.revision(), original.document());
            server.getPlayerList().getOps().remove(player.nameAndId());
            try { AdminEditorService.apply(player, "", changed); throw new AssertionError("Revoked OP applied a draft"); }
            catch (SecurityException expected) {}
            System.out.println("[ClassicGUIShop Alpha test] PASS: OP permissions, bounded Unicode chunks, invalid drafts, backups and restore, external edits, conflicts, folder persistence, purchases, sales, journal, zero-value guard, stale-menu guard, revoked permissions");
        } catch (Exception exception) { throw new AssertionError("Alpha server checks failed", exception); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
