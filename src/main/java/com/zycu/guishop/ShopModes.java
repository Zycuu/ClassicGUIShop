package com.zycu.guishop;

import com.zycu.guishop.admin.AccountOperations;
import com.zycu.guishop.admin.AdminDocument;
import com.zycu.guishop.admin.AdminEditorService;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import java.nio.file.*;
import java.util.*;

/** Server-only event handling. Disabled modes do not inspect or alter player wallets. */
public final class ShopModes {
    private ShopModes() {}
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("guishop/mode-progress.json");
    private static State state;
    private static int ticks;
    private static long lastError;
    private static final Map<UUID, Integer> sessionSeconds = new HashMap<>();
    private static final class Progress { int seconds; long sequence; double pendingAmount; }
    private static final class State { int version = 1; Map<String, Progress> passive = new HashMap<>(); }
    public record Offer(String id, String name, double suggested) {}
    public record Catalog(List<Offer> entities, List<Offer> advancements) {}
    public static void initialize() {
        ticks = 0; sessionSeconds.clear();
        GuiShop.CONFIG.modes.validate();
        try {
            state = Files.exists(FILE) ? AdminDocument.JSON.fromJson(Files.readString(FILE), State.class) : new State();
            if (state == null || state.version != 1 || state.passive == null) throw new IllegalStateException("Invalid mode progress file");
            for (var value : state.passive.values()) if (value == null || value.seconds < 0 || value.sequence < 0 || !Double.isFinite(value.pendingAmount) || value.pendingAmount < 0) throw new IllegalStateException("Invalid passive income progress");
        } catch (Exception error) { throw new IllegalStateException("Could not load Shop Modes progress. Restore its backup before enabling modes.", error); }
    }
    private static void save() throws Exception { AccountOperations.writeAtomic(FILE, AdminDocument.JSON.toJson(state)); }
    public static void checkpoint() {
        if (state == null || state.passive.isEmpty()) return;
        try { save(); } catch (Exception error) { report(error); }
    }
    public static void disconnected(ServerPlayer player) { sessionSeconds.remove(player.getUUID()); checkpoint(); }
    private static boolean eligible(ServerPlayer player) { return !player.isSpectator() && !player.isCreative(); }
    public static void tick(MinecraftServer server) {
        if (state == null || ++ticks % 20 != 0) return;
        var config = GuiShop.CONFIG.modes;
        var present = new HashSet<UUID>();
        for (var player : server.getPlayerList().getPlayers()) {
            present.add(player.getUUID());
            int seconds = sessionSeconds.getOrDefault(player.getUUID(), 0); sessionSeconds.put(player.getUUID(), Math.min(86400, seconds + 1));
            if (!eligible(player)) continue;
            try {
                var progress = state.passive.get(player.getUUID().toString());
                if (!config.passiveEnabled && (progress == null || progress.pendingAmount == 0)) continue;
                if (progress == null) { progress = new Progress(); state.passive.put(player.getUUID().toString(), progress); }
                if (progress.pendingAmount == 0 && config.passiveEnabled && config.passiveAmount >= .01) {
                    progress.seconds = Math.min(86400, progress.seconds + 1);
                    if (progress.seconds >= config.passiveSeconds) { progress.pendingAmount = Math.round(config.passiveAmount * 100) / 100.0; save(); }
                }
                if (progress.pendingAmount > 0) {
                    double amount = progress.pendingAmount;
                    boolean paid = AccountOperations.mode(player, "passive", player.getUUID() + ":" + progress.sequence, amount, "Online interval", null, 0, false, null, 0);
                    progress.seconds = 0; progress.sequence++; progress.pendingAmount = 0; save();
                    if (paid) message(player, "Passive Income", config.passiveColor, "Received " + GuiShop.CONFIG.money(amount) + ".");
                }
            } catch (Exception error) { report(error); }
        }
        sessionSeconds.keySet().retainAll(present);
        if (config.passiveEnabled && ticks % 1200 == 0) try { save(); } catch (Exception error) { report(error); }
    }
    public static void killed(LivingEntity victim) {
        killed(victim, victim.getKillCredit());
    }
    public static void killed(LivingEntity victim, LivingEntity credit) {
        if (state == null || GuiShop.CONFIG == null || !(credit instanceof ServerPlayer killer) || !eligible(killer)) return;
        var config = GuiShop.CONFIG.modes;
        try {
            if (victim instanceof ServerPlayer player) {
                if (!config.warriorEnabled || !eligible(player) || player.getUUID().equals(killer.getUUID())) return;
                if (sessionSeconds.getOrDefault(killer.getUUID(), 0) < config.warriorMinimumOnlineSeconds || sessionSeconds.getOrDefault(player.getUUID(), 0) < config.warriorMinimumOnlineSeconds) return;
                if (config.blockSameAddress && player.getIpAddress().equals(killer.getIpAddress())) return;
                if (config.warriorReward < .01) return;
                // Unordered pair prevents bypass by taking turns as killer and victim.
                String pair = java.util.stream.Stream.of(killer.getUUID().toString(), player.getUUID().toString()).sorted().reduce((a, b) -> a + ":" + b).orElseThrow();
                double before = GuiShop.ECONOMY.readBalance(player.getUUID());
                boolean paid = AccountOperations.mode(killer, "warrior", player.getUUID() + ":" + player.level().getGameTime(), config.warriorReward, "Player kill: " + player.getUUID(), player, config.warriorDeduction, config.allowNegativeBalance, "warrior:" + pair, config.warriorCooldownSeconds);
                if (paid) {
                    message(killer, "Warrior Mode", config.warriorColor, "Received " + GuiShop.CONFIG.money(config.warriorReward) + " for defeating " + player.getName().getString() + ".");
                    double deduction = before - GuiShop.ECONOMY.readBalance(player.getUUID());
                    if (deduction > 0) message(player, "Warrior Mode", config.warriorColor, "Lost " + GuiShop.CONFIG.money(deduction) + ".");
                }
            } else if (config.mobsEnabled) {
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();
                double amount = config.mobPrices.getOrDefault(id, config.mobDefault);
                if (amount < .01) return;
                // Quick farming is an explicit global bypass, including spawner farms.
                int cooldown = config.allowSpawnerFarming ? 0 : config.mobCooldownSeconds;
                if (AccountOperations.mode(killer, "mobs", victim.getUUID().toString(), amount, "Entity: " + id, null, 0, false, cooldown == 0 ? null : "mobs:" + killer.getUUID() + ":" + id, cooldown)) message(killer, "Cash Mobs", config.mobsColor, "Received " + GuiShop.CONFIG.money(amount) + " for " + victim.getType().getDescription().getString() + ".");
            }
        } catch (Exception error) { report(error); }
    }
    public static void advancement(ServerPlayer player, AdvancementHolder advancement) {
        if (state == null || GuiShop.CONFIG == null || !GuiShop.CONFIG.modes.advancementsEnabled || !eligible(player)) return;
        String id = advancement.id().toString();
        if (!player.getAdvancements().getOrStartProgress(advancement).isDone()) return;
        var config = GuiShop.CONFIG.modes;
        double amount = config.advancementPrices.getOrDefault(id, suggested(advancement));
        if (amount < .01) return;
        try {
            String reference = player.getUUID() + ":" + ItemStackData.shortHash(id);
            if (AccountOperations.mode(player, "advancement", reference, amount, "Advancement: " + id, null, 0, false, null, 0)) message(player, "Advancement Hunter", config.advancementsColor, "Received " + GuiShop.CONFIG.money(amount) + " for " + advancement.value().name().map(Component::getString).orElse(id) + ".");
        } catch (Exception error) { report(error); }
    }
    private static double suggested(AdvancementHolder value) {
        if (value.value().display().isEmpty()) return 0;
        if (!value.id().getNamespace().equals("minecraft")) return GuiShop.CONFIG.modes.advancementDefault;
        // Recipes and invisible roots have no vanilla payout; explicitly priced entries can opt in.
        return value.value().display().map(display -> switch (display.type()) { case TASK -> 25.0; case GOAL -> 100.0; case CHALLENGE -> 500.0; }).orElse(0.0);
    }
    public static Catalog catalog(ServerPlayer player) {
        if (!AdminEditorService.allowed(player)) throw new SecurityException(AdminEditorService.DENIED);
        var entities = BuiltInRegistries.ENTITY_TYPE.keySet().stream().filter(id -> BuiltInRegistries.ENTITY_TYPE.getValue(id).canSummon()).sorted(Comparator.comparing(Object::toString))
            .limit(10000).map(id -> new Offer(id.toString(), BuiltInRegistries.ENTITY_TYPE.getValue(id).getDescription().getString(), 0)).toList();
        var advancements = player.level().getServer().getAdvancements().getAllAdvancements().stream().sorted(Comparator.comparing(value -> value.id().toString())).limit(10000)
            .map(value -> new Offer(value.id().toString(), value.value().name().map(Component::getString).orElse(value.id().toString()), suggested(value))).toList();
        return new Catalog(entities, advancements);
    }
    private static void message(ServerPlayer player, String mode, String color, String text) {
        player.sendSystemMessage(Component.literal("[" + mode + "] ").withStyle(ChatFormatting.valueOf(color.toUpperCase(Locale.ROOT))).append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
    }
    private static void report(Exception error) {
        long now = System.currentTimeMillis(); if (now - lastError < 10000) return; lastError = now;
        System.err.println("[ClassicGUIShop Shop Modes] Payout refused: " + error.getMessage());
    }
}
