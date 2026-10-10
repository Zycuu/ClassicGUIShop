package com.zycu.guishop.testing;

import com.zycu.guishop.*;
import com.zycu.guishop.admin.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.entity.*;
import java.util.*;

public final class ModesPresetChecks {
    private ModesPresetChecks() {}
    public static void run(ServerPlayer viewer) throws Exception {
        var server = viewer.level().getServer(); var original = GuiShop.CONFIG; var draft = AdminDocument.JSON.fromJson(AdminDocument.JSON.toJson(original), ShopConfig.class);
        String before = AdminDocument.JSON.toJson(original);
        for (String preset : List.of("normal", "hard", "easy", "basics")) {
            var plan = ShopPresets.generate(viewer, new ShopPresets.Request(preset, .30));
            check(plan.items() > 50 && plan.recipes() > 100 && plan.categories().size() > 0, "Populated, audited " + preset);
            draft.categories = plan.categories(); draft.enchantments = plan.enchantments(); var doc = new AdminDocument(); doc.config = draft;
            AdminEditorService.validate(doc, viewer);
            check(EconomyExploitScanner.audit(server, draft).exploits().isEmpty(), "No current recipe profit in " + preset);
            check(draft.categories.stream().flatMap(c -> c.items.stream()).allMatch(item -> item.item.startsWith("minecraft:") && item.sell <= item.buy), "Vanilla-only coherent prices " + preset);
            check(plan.ratio() == (preset.equals("hard") ? .10 : preset.equals("easy") ? .95 : .30), "Requested target ratio " + preset);
            if (!preset.equals("basics")) {
                check(plan.enchantments().isEmpty() && plan.categories().stream().flatMap(c -> c.items.stream()).filter(i -> i.item.equals("minecraft:enchanted_book") && i.buy > 0).count() > 100, "Full preset includes vanilla enchantment offers");
                check(draft.findItem("minecraft:diamond") != null && draft.findItem("minecraft:map") != null, "Full catalog essentials");
                check(draft.findItem("minecraft:command_block").item().buy == 0 && draft.findItem("minecraft:pig_spawn_egg").item().sell == 0, "Administrative and spawn items locked");
            } else check(draft.findItem("minecraft:oak_log") != null && draft.findItem("minecraft:diamond_sword") == null && plan.enchantments().isEmpty(), "Basics membership");
        }
        check(AdminDocument.JSON.toJson(original).equals(before), "Preset previews do not mutate live config");
        var catalog = ShopModes.catalog(viewer);
        check(catalog.entities().stream().anyMatch(o -> o.id().equals("minecraft:zombie")) && catalog.advancements().stream().anyMatch(o -> o.id().equals("minecraft:story/mine_stone")), "Server entity and advancement discovery");
        var player = fake(viewer, "ModeTest"); var victim = fake(viewer, "ModeVictim");
        var players = server.getPlayerList().getPlayers(); players.add(player); players.add(victim);
        player.level().addNewPlayer(player); victim.level().addNewPlayer(victim);
        double playerBefore = GuiShop.ECONOMY.readBalance(player.getUUID()), victimBefore = GuiShop.ECONOMY.readBalance(victim.getUUID());
        GuiShop.CONFIG = draft; GuiShop.ECONOMY.updateConfig(draft); draft.modes = new ShopModesConfig();
        var m = draft.modes;
        try {
            GuiShop.ECONOMY.setBalance(player.getUUID(), 100); GuiShop.ECONOMY.setBalance(victim.getUUID(), 5);
            var zombie = EntityTypes.ZOMBIE.create(player.level(), EntitySpawnReason.COMMAND); zombie.setLastHurtByPlayer(player, 100); zombie.setLastHurtByMob(player);
            ShopModes.killed(zombie); check(GuiShop.ECONOMY.readBalance(player.getUUID()) == 100, "All modes disabled by default");
            m.mobsEnabled = true; m.mobPrices.put("minecraft:zombie", 7.0); m.mobCooldownSeconds = 60;
            zombie.die(player.damageSources().playerAttack(player)); check(GuiShop.ECONOMY.readBalance(player.getUUID()) == 107, "Real native mob death hook credits exact payout");
            ShopModes.killed(zombie); check(GuiShop.ECONOMY.readBalance(player.getUUID()) == 107, "Same death cannot pay twice");
            var second = EntityTypes.ZOMBIE.create(player.level(), EntitySpawnReason.SPAWNER); second.setLastHurtByPlayer(player, 100); second.setLastHurtByMob(player);
            ShopModes.killed(second); check(GuiShop.ECONOMY.readBalance(player.getUUID()) == 107, "Per-type cooldown blocks farm burst");
            AccountOperations.initialize(); ShopModes.killed(second); check(GuiShop.ECONOMY.readBalance(player.getUUID()) == 107, "Kill cooldown survives ledger reload");
            m.allowSpawnerFarming = true; // zero cooldown explicitly opts out of the existing window.
            var third = EntityTypes.ZOMBIE.create(player.level(), EntitySpawnReason.SPAWNER); third.setLastHurtByPlayer(player, 100); third.setLastHurtByMob(player);
            ShopModes.killed(third); check(GuiShop.ECONOMY.readBalance(player.getUUID()) == 114, "Explicit quick farming pays consecutive kills");
            m.advancementsEnabled = true; var advancement = server.getAdvancements().get(net.minecraft.resources.Identifier.parse("minecraft:story/mine_stone"));
            String criterion = advancement.value().criteria().keySet().iterator().next(); m.advancementPrices.put(advancement.id().toString(), 11.0);
            player.getAdvancements().revoke(advancement, criterion); player.getAdvancements().award(advancement, criterion);
            check(GuiShop.ECONOMY.readBalance(player.getUUID()) == 125, "Actual completed advancement hook pays configured price");
            m.advancementPrices.put(advancement.id().toString(), 19.0); player.getAdvancements().revoke(advancement, criterion); player.getAdvancements().award(advancement, criterion);
            check(GuiShop.ECONOMY.readBalance(player.getUUID()) == 125, "Regrant and price changes cannot repay an advancement");
            m.passiveEnabled = true; m.passiveAmount = 3; m.passiveSeconds = 2;
            for (int i = 0; i < 40; i++) ShopModes.tick(server);
            check(GuiShop.ECONOMY.readBalance(player.getUUID()) == 128, "Online interval pays configured amount");
            m.passiveEnabled = false; for (int i = 0; i < 40; i++) ShopModes.tick(server);
            check(GuiShop.ECONOMY.readBalance(player.getUUID()) == 128, "Disabled passive income does not accrue");
            m.advancementsEnabled = false; GuiShop.ECONOMY.setBalance(victim.getUUID(), 5);
            m.warriorEnabled = true; m.warriorMinimumOnlineSeconds = 0; m.blockSameAddress = false; m.warriorReward = 20; m.warriorDeduction = 10;
            victim.setLastHurtByPlayer(player, 100); victim.setLastHurtByMob(player);
            m.warriorMinimumOnlineSeconds = 600; ShopModes.killed(victim);
            check(GuiShop.ECONOMY.readBalance(player.getUUID()) == 128 && GuiShop.ECONOMY.readBalance(victim.getUUID()) == 5, "New sessions cannot farm PvP rewards");
            m.warriorMinimumOnlineSeconds = 0; m.blockSameAddress = true; ShopModes.killed(victim);
            check(GuiShop.ECONOMY.readBalance(player.getUUID()) == 128, "Same-address pair cannot earn a PvP reward");
            m.blockSameAddress = false; player.setGameMode(net.minecraft.world.level.GameType.CREATIVE); ShopModes.killed(victim);
            check(GuiShop.ECONOMY.readBalance(player.getUUID()) == 128, "Creative killer is excluded");
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); ShopModes.killed(player, player);
            check(GuiShop.ECONOMY.readBalance(player.getUUID()) == 128, "Self kill cannot earn");
            victim.die(player.damageSources().playerAttack(player));
            check(GuiShop.ECONOMY.readBalance(player.getUUID()) == 148 && GuiShop.ECONOMY.readBalance(victim.getUUID()) == 0, "Real PvP death credits winner and clamps deduction to zero: " + GuiShop.ECONOMY.readBalance(player.getUUID()) + "/" + GuiShop.ECONOMY.readBalance(victim.getUUID()));
            check(TransactionJournal.query("MODE_DEDUCTION", 0, false, victim.getUUID().toString()).entries().size() == 1, "Victim deduction is logged once");
            player.setLastHurtByPlayer(victim, 100); player.setLastHurtByMob(victim); ShopModes.killed(player);
            check(GuiShop.ECONOMY.readBalance(player.getUUID()) == 148 && GuiShop.ECONOMY.readBalance(victim.getUUID()) == 0, "Reversed pair cannot bypass cooldown");
            var directory = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("guishop/transactions");
            var journal = directory.resolve(java.time.LocalDate.now(java.time.ZoneOffset.UTC) + ".jsonl");
            var backup = journal.resolveSibling(journal.getFileName() + ".before-paired-failure-" + UUID.randomUUID());
            java.nio.file.Files.move(journal, backup); java.nio.file.Files.createDirectory(journal);
            try {
                try { AccountOperations.mode(player, "warrior", "paired-failure-" + UUID.randomUUID(), 1, "Paired recovery fixture", victim, 10, false, null, 0); throw new AssertionError("Failed paired journal was acknowledged"); } catch (IllegalStateException expected) {}
                check(GuiShop.ECONOMY.readBalance(player.getUUID()) == 149 && GuiShop.ECONOMY.readBalance(victim.getUUID()) == 0, "Paired balances persist atomically before journals");
            } finally { java.nio.file.Files.move(journal, journal.resolveSibling(journal.getFileName() + ".blocked-directory-" + UUID.randomUUID())); java.nio.file.Files.move(backup, journal); }
            AccountOperations.initialize(); AccountOperations.ensureReady();
            check(GuiShop.ECONOMY.readBalance(player.getUUID()) == 149 && TransactionJournal.query("Paired recovery fixture", 0, false, victim.getUUID().toString()).entries().size() == 1, "Paired operation recovers once after ledger reload");
            check(AccountOperations.mode(player, "warrior", "debt-" + UUID.randomUUID(), 1, "Debt fixture", victim, 10, true, null, 0) && GuiShop.ECONOMY.readBalance(victim.getUUID()) == -10, "Explicit debt persists a negative balance");
            var loaded = new EconomyStore(draft); check(loaded.readBalance(victim.getUUID()) == -10, "Debt survives balance-file reload");
            AccountOperations.reward(server.createCommandSourceStack(), victim.getUUID().toString(), 2, "vote", "debt-repay-" + UUID.randomUUID(), "Repay debt");
            check(GuiShop.ECONOMY.readBalance(victim.getUUID()) == -8, "External credit repays debt without erasing it");
            AccountOperations.change(viewer, new AccountOperations.Change(victim.getUUID().toString(), -8, 0, draft.startingBalance, true));
            check(GuiShop.ECONOMY.readBalance(victim.getUUID()) == draft.startingBalance && TransactionJournal.query("", 0, false, victim.getUUID().toString()).entries().isEmpty(), "Admin reset also handles a debt account");
            m.passiveSeconds = 0; try { m.validate(); throw new AssertionError("Invalid mode interval accepted"); } catch (IllegalArgumentException expected) {} m.passiveSeconds = 600;
            System.out.println("[Modes and presets test] PASS: four audited vanilla drafts; locks, ratios and Basics membership; no live mutation; server detection; actual mob, advancement and PvP hooks; duplicate and cooldown guards; persistent cooldowns; online income; paired debit; opt-in debt persistence and repayment; reset and validation");
        } finally {
            GuiShop.CONFIG = original; GuiShop.ECONOMY.updateConfig(original); GuiShop.ECONOMY.setBalance(player.getUUID(), Math.max(0, playerBefore)); GuiShop.ECONOMY.setBalance(victim.getUUID(), Math.max(0, victimBefore));
            players.remove(player); players.remove(victim); player.level().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED); victim.level().removePlayerImmediately(victim, Entity.RemovalReason.DISCARDED);
        }
    }
    private static ServerPlayer fake(ServerPlayer viewer, String name) {
        var server = viewer.level().getServer(); var player = new ServerPlayer(server, viewer.level(), new GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        try { var address = Connection.class.getDeclaredField("address"); address.setAccessible(true); address.set(connection, new java.net.InetSocketAddress("127.0.0.1", 25500)); }
        catch (ReflectiveOperationException error) { throw new AssertionError("Could not configure the same-address fixture", error); }
        player.connection = new ServerGamePacketListenerImpl(server, connection, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
        GuiShop.PLAYERS.remember(player); return player;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
