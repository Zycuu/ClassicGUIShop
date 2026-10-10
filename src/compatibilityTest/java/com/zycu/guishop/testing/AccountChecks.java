package com.zycu.guishop.testing;

import com.zycu.guishop.*;
import com.zycu.guishop.admin.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.ServerOpListEntry;
import java.nio.file.*;
import java.time.ZoneId;
import java.util.UUID;

public final class AccountChecks {
    private AccountChecks() {}
    public static void run(ServerPlayer viewer) throws Exception {
        var server = viewer.level().getServer(); var source = viewer.createCommandSourceStack();
        double original = GuiShop.ECONOMY.balance(viewer.getUUID());
        UUID other = UUID.nameUUIDFromBytes("ClassicGUIShop account isolation fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        GuiShop.ECONOMY.setBalance(other, 123);
        String probe = "account-check-" + UUID.randomUUID();
        var directory = FabricLoader.getInstance().getConfigDir().resolve("guishop");
        try {
            check(JournalDates.date("2026-01-01T01:30:00Z", ZoneId.of("America/Los_Angeles")).equals("31/Dec/25"), "Local conversion crosses the UTC date boundary");
            check(JournalDates.display("2026-01-01T01:30:00Z", ZoneId.of("America/Los_Angeles")).equals("05:30:00 PM (31/Dec/25)"), "Exact requested timestamp layout");
            check(JournalDates.display("2026-01-01T08:00:00Z", ZoneId.of("America/Los_Angeles")).equals("12:00:00 AM (01/Jan/26)"), "12-hour midnight layout");
            check(JournalDates.display("2026-01-01T20:00:00Z", ZoneId.of("America/Los_Angeles")).equals("12:00:00 PM (01/Jan/26)"), "12-hour noon layout");
            check(JournalDates.full("2026-07-01T12:00:00Z", ZoneId.of("America/New_York")).endsWith("EDT") && JournalDates.full("2026-01-01T12:00:00Z", ZoneId.of("America/New_York")).endsWith("EST"), "Historical daylight saving labels");
            TransactionJournal.append(new TransactionJournal.Entry("2026-01-01T01:30:00Z", "ShopTest", viewer.getUUID().toString(), "BUY", probe, "minecraft:apple", 1, 1, 1, original, "Date boundary fixture"));
            TransactionJournal.append(new TransactionJournal.Entry("2026-01-01T01:30:00Z", "Unrelated", other.toString(), "BUY", probe, "minecraft:apple", 1, 1, 1, 123, "Other player"));
            check(TransactionJournal.query("31/Dec/25", 0, false, viewer.getUUID().toString(), "America/Los_Angeles").entries().stream().anyMatch(entry -> probe.equals(entry.listing())), "Local date search finds UTC-stored record");
            check(TransactionJournal.query("01/Jan/26", 0, false, viewer.getUUID().toString(), "America/Los_Angeles").entries().stream().anyMatch(entry -> probe.equals(entry.listing())), "UTC formatted date search works alongside local date");
            check(TransactionJournal.query("2026-01-01", 0, false, viewer.getUUID().toString(), "America/Los_Angeles").entries().stream().anyMatch(entry -> probe.equals(entry.listing())), "Original ISO UTC search remains supported");
            check(TransactionJournal.query("05:30:00 PM (31/Dec/25)", 0, false, viewer.getUUID().toString(), "America/Los_Angeles").entries().stream().anyMatch(entry -> probe.equals(entry.listing())), "Full 12-hour local timestamp is searchable");
            check(TransactionJournal.query("01:30:00 AM (01/Jan/26)", 0, false, viewer.getUUID().toString(), "America/Los_Angeles").entries().stream().anyMatch(entry -> probe.equals(entry.listing())), "Full 12-hour UTC timestamp is searchable");
            var request = new AccountOperations.Change(viewer.getUUID().toString(), original, 100.50, GuiShop.CONFIG.startingBalance, false);
            AccountOperations.change(viewer, request); check(GuiShop.ECONOMY.balance(viewer.getUUID()) == 100.50, "Set balance applies and persists");
            try { AccountOperations.change(viewer, request); throw new AssertionError("Stale balance overwrite accepted"); } catch (IllegalStateException expected) {}
            try { AccountOperations.change(viewer, new AccountOperations.Change(viewer.getUUID().toString(), 100.50, -1, 0, false)); throw new AssertionError("Negative balance accepted"); } catch (IllegalArgumentException expected) {}
            try { AccountOperations.change(viewer, new AccountOperations.Change(viewer.getUUID().toString(), 100.50, Double.NaN, 0, false)); throw new AssertionError("NaN balance accepted"); } catch (IllegalArgumentException expected) {}
            String reference = "test-" + UUID.randomUUID();
            String command = "shopreward " + viewer.getName().getString() + " 1.10 vote " + reference + " Test vote reward";
            check(server.getCommands().getDispatcher().execute(command, source) == 1 && GuiShop.ECONOMY.balance(viewer.getUUID()) == 101.60, "Actual reward command credits and labels a vote");
            check(server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack()) == 1 && GuiShop.ECONOMY.balance(viewer.getUUID()) == 101.60, "Console retry does not pay twice");
            check(server.getCommands().getDispatcher().execute(command.replace("1.10", "2.10"), source) == 0 && GuiShop.ECONOMY.balance(viewer.getUUID()) == 101.60, "Conflicting reference is rejected");
            check(TransactionJournal.query(reference, 0, false, viewer.getUUID().toString()).entries().size() == 1, "Reward logs exactly once");
            var offlineReward = AccountOperations.reward(source, other.toString(), 5, "donation", "offline-" + UUID.randomUUID(), "Test store credit");
            check(offlineReward.credited() && GuiShop.ECONOMY.readBalance(other) == 128, "Known offline UUID can receive external credit");
            var journal = directory.resolve("transactions/2026-01-01.jsonl"); byte[] archive = Files.readAllBytes(journal);
            long generation = AccountOperations.historyGeneration(viewer.getUUID().toString());
            try { AccountOperations.change(viewer, new AccountOperations.Change(viewer.getUUID().toString(), 101.60, 0, GuiShop.CONFIG.startingBalance + 1, true)); throw new AssertionError("Stale reset default accepted"); } catch (IllegalStateException expected) {}
            AccountOperations.change(viewer, new AccountOperations.Change(viewer.getUUID().toString(), 101.60, 0, GuiShop.CONFIG.startingBalance, true));
            check(GuiShop.ECONOMY.balance(viewer.getUUID()) == GuiShop.CONFIG.startingBalance && TransactionJournal.query("", 0, false, viewer.getUUID().toString()).entries().isEmpty(), "Reset uses current server default and empties active history");
            check(AccountOperations.historyGeneration(viewer.getUUID().toString()) == generation + 1 && java.util.Arrays.equals(archive, Files.readAllBytes(journal)), "Reset preserves original journals as recovery archive");
            check(TransactionJournal.query(probe, 0, false, other.toString()).entries().size() == 1 && GuiShop.ECONOMY.readBalance(other) == 128, "Unrelated history and balance remain untouched");
            AccountOperations.initialize();
            check(server.getCommands().getDispatcher().execute(command, source) == 1 && GuiShop.ECONOMY.balance(viewer.getUUID()) == GuiShop.CONFIG.startingBalance, "Reward replay protection survives ledger reload and history reset");
            check(TransactionJournal.query("", 0, false, viewer.getUUID().toString()).entries().isEmpty(), "Cleared history remains cleared after ledger reload");
            String quoted = "shopreward " + viewer.getName().getString() + " 0.10 \"vote:test\" \"checkout:" + UUID.randomUUID() + "\" Quoted reference";
            check(server.getCommands().getDispatcher().execute(quoted, source) == 1 && server.getCommands().getDispatcher().execute(quoted, source) == 1
                && GuiShop.ECONOMY.balance(viewer.getUUID()) == GuiShop.CONFIG.startingBalance + 0.10, "Quoted colon tokens parse and pay once");
            GuiShop.ECONOMY.setBalance(viewer.getUUID(), GuiShop.CONFIG.startingBalance);
            // Force an audit write failure after balance persistence; pending operations must block trades and recover once.
            var audit = directory.resolve("account-audit.jsonl"); var backup = audit.resolveSibling("account-audit-before-failure-" + UUID.randomUUID() + ".jsonl");
            Files.move(audit, backup); Files.createDirectory(audit);
            try {
                try { AccountOperations.change(viewer, new AccountOperations.Change(viewer.getUUID().toString(), GuiShop.CONFIG.startingBalance, 0, GuiShop.CONFIG.startingBalance, true)); throw new AssertionError("Failed audit was acknowledged"); } catch (IllegalStateException expected) {}
                check(!ShopService.canTransact(viewer, ShopGui.Mode.BUY), "Pending account recovery blocks transactions before inventory mutation");
            } finally {
                Files.move(audit, audit.resolveSibling("account-audit-blocked-directory-" + UUID.randomUUID())); Files.move(backup, audit);
            }
            AccountOperations.ensureReady(); AccountOperations.ensureReady();
            check(TransactionJournal.query("", 0, false, viewer.getUUID().toString()).entries().isEmpty(), "Recovered reset still has an empty active history");
            check(com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("account-operations.json"))).getAsJsonObject().get("pending") == null, "Durable pending operation completed");
            server.getPlayerList().getOps().remove(viewer.nameAndId());
            try { AccountOperations.change(viewer, new AccountOperations.Change(viewer.getUUID().toString(), GuiShop.CONFIG.startingBalance, 5, 0, false)); throw new AssertionError("Non-OP changed balance"); } catch (SecurityException expected) {}
            try { server.getCommands().getDispatcher().execute(command, viewer.createCommandSourceStack()); throw new AssertionError("Non-OP ran reward command"); } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {}
            System.out.println("[Account test] PASS: local and UTC search; DST labels; set balance; stale and invalid requests; console and offline rewards; durable replay protection; UUID-isolated reset and archived journals; empty active history; audit-write failure and recovery; OP checks");
        } finally {
            server.getPlayerList().getOps().add(new ServerOpListEntry(viewer.nameAndId(), LevelBasedPermissionSet.GAMEMASTER, false));
            AccountOperations.ensureReady(); GuiShop.ECONOMY.setBalance(viewer.getUUID(), original);
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
